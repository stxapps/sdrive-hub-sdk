package org.blockstack.android.sdk

import android.net.Uri
import android.util.Log
import com.colendi.ecies.EncryptedResultForm
import com.colendi.ecies.Encryption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.blockstack.android.sdk.ecies.signContent
import org.blockstack.android.sdk.ecies.signEncryptedContent
import org.blockstack.android.sdk.ecies.verify
import org.blockstack.android.sdk.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.kethereum.crypto.toECKeyPair
import org.kethereum.model.ECKeyPair
import org.kethereum.model.PrivateKey
import org.kethereum.model.PublicKey
import org.komputing.khash.sha256.extensions.sha256
import org.komputing.khex.model.HexString
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.InvalidParameterException
import java.util.*
import java.util.concurrent.TimeUnit

const val SIGNATURE_FILE_EXTENSION = ".sig"
const val FILE_PREFIX = "file://"

class BlockstackSession(private val sessionStore: ISessionStore, private val appConfig: BlockstackConfig? = null,
                        private val callFactory: Call.Factory = OkHttpClient(),
                        val blockstack: Blockstack = Blockstack(),
                        val hub: Hub = Hub(callFactory),
                        val dispatcher: CoroutineDispatcher = Dispatchers.IO) {

    private var appPrivateKey: String?
    var gaiaHubConfig: GaiaHubConfig? = null

    init {
        val appPrivateKey = sessionStore.sessionData.json.optJSONObject("userData")?.optString("appPrivateKey")
        this.appPrivateKey = appPrivateKey
    }



    companion object {
        val TAG = BlockstackSession::class.java.simpleName
        val CONTENT_TYPE_JSON = "application/json"
    }
}

fun JSONObject.optStringOrNull(name: String): String? {
    if (isNull(name)) {
        return null
    } else {
        return optString(name)
    }
}
