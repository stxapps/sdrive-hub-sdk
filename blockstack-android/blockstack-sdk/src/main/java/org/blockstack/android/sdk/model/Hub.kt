package org.blockstack.android.sdk.model

import android.util.Log
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import org.blockstack.android.sdk.BlockstackSession
import org.blockstack.android.sdk.toBtcAddress
import org.blockstack.android.sdk.toHexPublicKey64
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.json.JSONObject
import org.kethereum.crypto.SecureRandomUtils
import org.kethereum.crypto.toECKeyPair
import org.kethereum.model.PrivateKey
import org.komputing.khex.extensions.toNoPrefixHexString
import org.komputing.khex.model.HexString
import java.security.KeyFactory
import java.security.Security

data class AuthScope(val scope: String, val domain: String) {
    companion object {
        val COLLECTION_AUTH_SCOPE = AuthScope("putFileArchivalPrefix", "collection")
    }
}

const val GAIA_HUB_COLLECTION_KEY_FILE_NAME = ".collections.keys"

class Hub(val callFactory: Call.Factory = OkHttpClient()) {

    fun getFullReadUrl(filename: String, hubConfig: GaiaHubConfig): String {
        return "${hubConfig.urlPrefix}${hubConfig.address}/${filename}"
    }

    suspend fun connectToGaia(gaiaHubUrl: String,
                              challengeSignerHex: String,
                              associationToken: String?,
                              scopes: Array<AuthScope> = emptyArray()): GaiaHubConfig {

        val builder = Request.Builder()
                .url("${gaiaHubUrl}/hub_info")
        builder.addHeader("Referrer-Policy", "no-referrer")

        val response = callFactory.newCall(builder.build()).execute()

        val hubInfo = JSONObject(response.body!!.string())

        val readURL = hubInfo.getString("read_url_prefix")
        val token = makeV1GaiaAuthToken(hubInfo, challengeSignerHex, gaiaHubUrl, associationToken, scopes)
        val address = PrivateKey(HexString(challengeSignerHex)).toECKeyPair().toBtcAddress()

        return GaiaHubConfig(readURL, address, token, gaiaHubUrl)

    }


    /**
     *
     * @param hubInfo
     * @param signerKeyHex
     * @param hubUrl
     * @param associationToken
     *
     * @ignore
     */
    suspend fun makeV1GaiaAuthToken(hubInfo: JSONObject,
                                    signerKeyHex: String,
                                    hubUrl: String,
                                    associationToken: String?,
                                    scopes: Array<AuthScope>): String {

        val challengeText = hubInfo.getString("challenge_text")
        val handlesV1Auth = hubInfo.optString("latest_auth_version").substring(1).let {
            Integer.parseInt(it, 10) >= 1
        }

        val kthPrivKey = PrivateKey(HexString(signerKeyHex))
        val iss = kthPrivKey.toECKeyPair().toHexPublicKey64()

        if (!handlesV1Auth) {
            throw NotImplementedError("only v1 auth supported, please upgrade your gaia server")
        }

        val header = JWSHeader.Builder(JWSAlgorithm.ES256K).build()

        val saltArray = ByteArray(16) { 0 }
        SecureRandomUtils.secureRandom().nextBytes(saltArray)
        val salt = saltArray.toNoPrefixHexString()
        // {"gaiaChallenge":"[\"gaiahub\",\"0\",\"storage2.blockstack.org\",\"blockstack_storage_please_sign\"]","hubUrl":"https://hub.blockstack.org","iss":"024634ee1d4ff57f2e0ec7a847e1705ec562949f84a83d1f5fdb5956220a9775e0","salt":"c3b9b4aefad343f204bd95c2fa1ae0a4"}
        val payload = mapOf("gaiaChallenge" to challengeText,
                "hubUrl" to hubUrl,
                "iss" to iss,
                "salt" to salt,
                "associationToken" to associationToken,
                "scopes" to scopes.map { s -> mapOf("scope" to s.scope, "domain" to s.domain) })
        val claimsBuilder = JWTClaimsSet.Builder()
        payload.forEach { (k, v) -> claimsBuilder.claim(k, v) }
        val claimsSet = claimsBuilder.build()
        Log.d(BlockstackSession.TAG, "$header, $claimsSet")

        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        val privateKeyBI = kthPrivKey.key
        val paramSpec = ECNamedCurveTable.getParameterSpec("secp256k1")
        val privateKeySpec = ECPrivateKeySpec(privateKeyBI, paramSpec)
        val keyFactory = KeyFactory.getInstance("ECDSA", "BC")
        val privateKey = keyFactory.generatePrivate(privateKeySpec)
        val signer = ECDSASigner(privateKey, Curve.SECP256K1)
        val signedJWT = SignedJWT(header, claimsSet)
        signedJWT.sign(signer)

        val token = signedJWT.serialize()
        Log.d(BlockstackSession.TAG, token)
        return "v1:${token}"
    }

    suspend fun uploadToGaiaHub(filename: String, content: ByteString, gaiaHubConfig: GaiaHubConfig, contentType: String = "application/octet-stream"): Response {
        val putRequest = buildPutRequest(filename, content, contentType, gaiaHubConfig)

        return withContext(Dispatchers.IO) {
            callFactory.newCall(putRequest).execute()
        }
    }


    private fun buildPutRequest(path: String, content: ByteString, contentType: String, hubConfig: GaiaHubConfig): Request {
        val url = "${hubConfig.server}/store/${hubConfig.address}/${path}"

        val builder = Request.Builder()
                .url(url)
        builder.method("POST",  content.toRequestBody(contentType.toMediaType()))
        builder.addHeader("Content-Type", contentType)
        builder.addHeader("Authorization", "bearer ${hubConfig.token}")
        builder.addHeader("Referrer-Policy", "no-referrer")
        return builder.build()
    }

    suspend fun getFromGaiaHub(url: String): Response {
        val getRequest = buildGetRequest(url)
        return withContext(Dispatchers.IO) {
            callFactory.newCall(getRequest).execute()
        }
    }

    fun buildGetRequest(url: String): Request {
        val builder = Request.Builder()
                .url(url)
        builder.addHeader("Referrer-Policy", "no-referrer")
        return builder.build()
    }


    suspend fun deleteFromGaiaHub(filename: String, gaiaHubConfig: GaiaHubConfig): Response? {
        val deleteRequest = buildDeleteRequest(filename, gaiaHubConfig)

        return withContext(Dispatchers.IO) {
            return@withContext callFactory.newCall(deleteRequest).execute()
        }
    }

    private fun buildDeleteRequest(filename: String, hubConfig: GaiaHubConfig): Request {
        val url = "${hubConfig.server}/delete/${hubConfig.address}/${filename}"
        val builder = Request.Builder()
                .url(url)
        builder.method("DELETE", null)
        builder.header("Authorization", "bearer ${hubConfig.token}")
        return builder.build()
    }

}
