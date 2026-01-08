package org.blockstack.android.sdk

import android.util.Base64
import com.colendi.ecies.EncryptedResultForm
import com.colendi.ecies.Encryption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import org.blockstack.android.sdk.model.CipherObject
import org.blockstack.android.sdk.model.CryptoOptions
import org.json.JSONObject
import org.kethereum.crypto.getCompressedPublicKey
import org.kethereum.model.ECKeyPair
import org.komputing.kbase58.encodeToBase58String
import org.komputing.khash.ripemd160.extensions.digestRipemd160
import org.komputing.khash.sha256.extensions.sha256
import org.komputing.khex.extensions.toNoPrefixHexString
import org.komputing.khex.extensions.hexToByteArray
import org.komputing.khex.model.HexString
import java.net.URI

class Blockstack(private val callFactory: Call.Factory = OkHttpClient(),
                 val dispatcher: CoroutineDispatcher = Dispatchers.IO) {

    /**
     * Encrypt content
     *
     * @plainContent can be a String or ByteArray
     * @options defines how to encrypt
     * @return result object with `CipherObject` or error if encryption failed
     */
    fun encryptContent(plainContent: Any, options: CryptoOptions): Result<CipherObject> {
        val valid = plainContent is String || plainContent is ByteArray
        if (!valid) {
            throw IllegalArgumentException("encrypt content only supports String or ByteArray")
        }

        val isBinary = plainContent is ByteArray

        val contentString = if (isBinary) {
            Base64.encodeToString(plainContent as ByteArray, Base64.NO_WRAP)
        } else {
            plainContent as String
        }

        val result = Encryption().encryptWithPublicKey(contentString.toByteArray(), options.publicKey)

        if (result.iv.isNotEmpty()) {
            return Result(CipherObject(result.iv, result.ephemPublicKey, result.ciphertext, result.mac, !isBinary))
        } else {
            return Result(null, ResultError(ErrorCode.UnknownError, "failed to encrypt"))
        }
    }


    /**
     * Decrypt content
     * @cipherObject can be a String or ByteArray representing the cipherObject returned by  @see encryptContent
     * @binary flag indicating whether a ByteArray or String was encrypted
     * @options defines how to decrypt the cipherObject
     * @return result object with plain content as String or ByteArray depending on the given binary flag or error
     */
    fun decryptContent(cipherObject: Any, binary: Boolean, options: CryptoOptions): Result<Any> {

        val valid = cipherObject is String || cipherObject is ByteArray
        if (!valid) {
            throw IllegalArgumentException("decrypt content only supports (json) String or ByteArray not " + cipherObject::class.java)
        }

        val isByteArray = cipherObject is ByteArray
        val cipherObjectString = if (isByteArray) {
            Base64.encodeToString(cipherObject as ByteArray, Base64.NO_WRAP)
        } else {
            cipherObject as String
        }
        val cipher = CipherObject(JSONObject(cipherObjectString))
        val plainContent = Encryption().decryptWithPrivateKey(EncryptedResultForm(cipher.ephemeralPK, cipher.iv, cipher.mac, cipher.cipherText, options.privateKey))

        if (plainContent != null) {
            if (!binary) {
                return Result(String(plainContent))
            } else {
                return Result(Base64.decode(plainContent, Base64.DEFAULT))
            }
        } else {
            return Result(null, ResultError(ErrorCode.FailedDecryptionError, "failed to decrypt"))
        }
    }

    /**
     * Fetch the public read URL of a user file for the specified app.
     *
     *@param path the path to the file to read
     *@param username The Blockstack ID of the user to look up
     *@param appOrigin The app origin
     *@param zoneFileLookupURL The URL to use for zonefile lookup. If false, this will use the blockstack.js's getNameInfo function instead.
     *@result the public read URL of the file or null on error
     */
    suspend fun getUserAppFileUrl(path: String, username: String, appOrigin: String, zoneFileLookupURL: String?): String = withContext(dispatcher){
        //val profile = lookupProfile(username, zoneFileLookupURL?.let { URL(it) })
        var bucketUrl = NO_URL
        /*if (profile.json.has("apps")) {
            val apps = profile.json.getJSONObject("apps")
            if (apps.has(appOrigin)) {
                val url = apps.getString(appOrigin)
                val bucket = url.replace(Regex("/+(\\?|#|$)"), "/$1")
                bucketUrl = "${bucket}${path}"
            }
        }*/
        return@withContext bucketUrl
    }

    companion object {
        const val NO_URL = "NO_URL"
        val TAG = Blockstack::class.java.simpleName
    }
}

fun String.toBtcAddress(): String {
    val sha256 = HexString(this).hexToByteArray().sha256()
    val hash160 = sha256.digestRipemd160()
    val extended = "00${hash160.toNoPrefixHexString()}"
    val checksum = checksum(extended)
    val address = HexString(extended + checksum).hexToByteArray().encodeToBase58String()
    return address
}

private fun checksum(extended: String): String {
    val checksum = HexString(extended).hexToByteArray().sha256().sha256()
    val shortPrefix = checksum.slice(0..3)
    return shortPrefix.toNoPrefixHexString()
}


fun ECKeyPair.toHexPublicKey64(): String {
    return this.getCompressedPublicKey().toNoPrefixHexString()
}

fun ECKeyPair.toBtcAddress(): String {
    val publicKey = toHexPublicKey64()
    return publicKey.toBtcAddress()
}


fun URI.getOrigin(): String {
    return if (this.port != -1) {
        "${this.scheme}://${this.host}:${this.port}"
    } else {
        "${this.scheme}://${this.host}"
    }
}

