package org.blockstack.reactnative

import android.util.Base64
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.blockstack.android.sdk.BlockstackSession
import org.blockstack.android.sdk.Result
import org.blockstack.android.sdk.Scope
import org.blockstack.android.sdk.SessionStore
import org.blockstack.android.sdk.ecies.signContent
import org.blockstack.android.sdk.getBlockstackSharedPreferences
import org.blockstack.android.sdk.model.BlockstackConfig
import org.blockstack.android.sdk.model.DeleteFileOptions
import org.blockstack.android.sdk.model.GetFileOptions
import org.blockstack.android.sdk.model.PutFileOptions
import org.blockstack.android.sdk.model.UserData
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

class RNBlockstackSDKModule : Module() {

    private var session: BlockstackSession? = null

    // Each module class must implement the definition function. The definition consists of components
    // that describes the module's functionality and behavior.
    // See https://docs.expo.dev/modules/module-api for more details about available components.
    override fun definition() = ModuleDefinition {
        // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
        // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
        // The module will be accessible from `requireNativeModule('RNBlockstackSDK')` in JavaScript.
        Name("RNBlockstackSDK")

        // Defines a JavaScript function that always returns a Promise and whose native code
        // is by default dispatched on the different thread than the JavaScript runtime runs on.
        AsyncFunction("hasSession") {
            mapOf("hasSession" to (session != null))
        }

        AsyncFunction("createSession") { configArg: Map<String, Any> ->
            val currentActivity = appContext.currentActivity
            if (currentActivity == null) {
                throw IllegalStateException("must be called from an Activity")
            }

            val sessionStore = SessionStore(currentActivity.getBlockstackSharedPreferences())

            val scopesList = configArg["scopes"] as? List<String>
            val scopes = scopesList?.map {
                Scope.fromJSName(it).scope
            }?.toTypedArray()

            if (!configArg.containsKey("appDomain")) {
                throw IllegalArgumentException("appDomain needed in config object")
            }

            val appDomain = configArg["appDomain"] as String
            val manifestPath = configArg["manifestUrl"] as? String ?: "/manifest.json"
            val redirectPath = configArg["redirectUrl"] as? String ?: "/redirect"
            val config = BlockstackConfig(URI(appDomain), redirectPath, manifestPath, scopes!!)

            session = BlockstackSession(sessionStore, config)
            mapOf("loaded" to true)
        }

        AsyncFunction("isUserSignedIn") {
            if (session == null) {
                throw IllegalStateException("Session not loaded")
            }
            mapOf("signedIn" to session!!.isUserSignedIn())
        }

        AsyncFunction("signUserOut") {
            if (session == null) {
                throw IllegalStateException("Session not loaded")
            }
            session!!.signUserOut()
            mapOf("signedOut" to true)
        }

        AsyncFunction("updateUserData") { userData: Map<String, Any>, promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (session == null) {
                        promise.reject("ERR_UPDATE_USER_DATA", "Session not loaded", null)
                        return@launch
                    }

                    session!!.updateUserData(UserData(mapToJson(userData)))
                    promise.resolve(mapOf("updated" to true))
                } catch (e: Exception) {
                    promise.reject("ERR_UPDATE_USER_DATA", e.message, e)
                }
            }
        }

        AsyncFunction("loadUserData") {
            if (session == null) {
                throw IllegalStateException("Session not loaded")
            }
            val userData = session!!.loadUserData()
            jsonToMap(userData.json)
        }

        AsyncFunction("putFile") { path: String, content: String, optionsArg: Map<String, Any>, promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (session == null) {
                        promise.reject("ERR_PUT_FILE", "Session not loaded", null)
                        return@launch
                    }

                    val encrypt = optionsArg["encrypt"] as? Boolean ?: true
                    val options = if (optionsArg.containsKey("dir")) {
                        PutFileOptions(encrypt, dir = optionsArg["dir"] as String)
                    } else PutFileOptions(encrypt)

                    val res = session!!.putFile(path, content, options)
                    if (res.hasValue) {
                        promise.resolve(mapOf("fileUrl" to res.value))
                    } else {
                        promise.reject("ERR_PUT_FILE", res.error?.toString(), null)
                    }
                } catch (e: Exception) {
                    promise.reject("ERR_PUT_FILE", e.message, e)
                }
            }
        }

        AsyncFunction("getFile") { path: String, optionsArg: Map<String, Any>, promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (session == null) {
                        promise.reject("ERR_GET_FILE", "Session not loaded", null)
                        return@launch
                    }

                    val decrypt = optionsArg["decrypt"] as? Boolean ?: true
                    val options = if (optionsArg.containsKey("dir")) {
                        GetFileOptions(decrypt, dir = optionsArg["dir"] as String)
                    } else GetFileOptions(decrypt)

                    val res = session!!.getFile(path, options)
                    if (res.hasValue) {
                        val result = mutableMapOf<String, Any>()
                        if (res.value is String) {
                            result["fileContents"] = res.value as String
                        } else {
                            result["fileContentsEncoded"] = Base64.encodeToString(res.value as ByteArray, Base64.NO_WRAP)
                        }
                        promise.resolve(result)
                    } else {
                        promise.reject("ERR_GET_FILE", res.error?.toString(), null)
                    }
                } catch (e: Exception) {
                    promise.reject("ERR_GET_FILE", e.message, e)
                }
            }
        }

        AsyncFunction("deleteFile") { path: String, optionsArg: Map<String, Any>, promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (session == null) {
                        promise.reject("ERR_DELETE_FILE", "Session not loaded", null)
                        return@launch
                    }

                    val wasSigned = optionsArg["wasSigned"] as? Boolean ?: false
                    val options = DeleteFileOptions(wasSigned)
                    val res = session!!.deleteFile(path, options)
                    if (res.hasErrors) {
                        promise.reject("ERR_DELETE_FILE", res.error?.toString(), null)
                    } else {
                        promise.resolve(mapOf("deleted" to true))
                    }
                } catch (e: Exception) {
                    promise.reject("ERR_DELETE_FILE", e.message, e)
                }
            }
        }

        AsyncFunction("performFiles") { pfData: String, dir: String, promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (session == null) {
                        promise.reject("ERR_PERFORM_FILES", "Session not loaded", null)
                        return@launch
                    }

                    val res = session!!.performFiles(pfData, dir)
                    if (res.hasValue) {
                        promise.resolve(res.value)
                    } else {
                        promise.reject("ERR_PERFORM_FILES", res.error?.toString(), null)
                    }
                } catch (e: Exception) {
                    promise.reject("ERR_PERFORM_FILES", e.message, e)
                }
            }
        }

        AsyncFunction("listFiles") { promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (session == null) {
                        promise.reject("ERR_LIST_FILES", "Session not loaded", null)
                        return@launch
                    }

                    // list all files and return to JS just once
                    val files = ArrayList<String>()
                    val res = session!!.listFiles { result: Result<String> ->
                        result.value?.let { files.add(it) }
                        true
                    }
                    if (res.hasValue) {
                        val map = mutableMapOf<String, Any>()
                        map["files"] = files
                        map["fileCount"] = res.value!!
                        promise.resolve(map)
                    } else {
                        promise.reject("ERR_LIST_FILES", res.error?.toString(), null)
                    }
                } catch (e: Exception) {
                    promise.reject("ERR_LIST_FILES", e.message, e)
                }
            }
        }

        AsyncFunction("signECDSA") { privateKey: String, content: String, promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // @stacks/encryption uses noble-secp256k1
                    //   and in noble-secp256k1/index.ts#L1148, default canonical is true.
                    val res = signContent(content, privateKey, true)

                    val map = mutableMapOf<String, Any>()
                    map["publicKey"] = res.publicKey
                    map["signature"] = res.signature
                    promise.resolve(map)
                } catch (e: Exception) {
                    promise.reject("ERR_SIGN_ECDSA", e.message, e)
                }
            }
        }
    }

    private fun jsonToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            if (value == JSONObject.NULL) {
                map[key] = null
            } else if (value is JSONObject) {
                map[key] = jsonToMap(value)
            } else if (value is JSONArray) {
                map[key] = jsonArrayToList(value)
            } else {
                map[key] = value
            }
        }
        return map
    }

    private fun jsonArrayToList(jsonArray: JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until jsonArray.length()) {
            val value = jsonArray.get(i)
            if (value == JSONObject.NULL) {
                list.add(null)
            } else if (value is JSONObject) {
                list.add(jsonToMap(value))
            } else if (value is JSONArray) {
                list.add(jsonArrayToList(value))
            } else {
                list.add(value)
            }
        }
        return list
    }

    private fun mapToJson(map: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        for ((key, value) in map) {
            when (value) {
                is Map<*, *> -> json.put(key, mapToJson(value as Map<String, Any?>))
                is List<*> -> json.put(key, listToJson(value as List<Any?>))
                else -> json.put(key, value)
            }
        }
        return json
    }

    private fun listToJson(list: List<Any?>): JSONArray {
        val jsonArray = JSONArray()
        for (value in list) {
            when (value) {
                is Map<*, *> -> jsonArray.put(mapToJson(value as Map<String, Any?>))
                is List<*> -> jsonArray.put(listToJson(value as List<Any?>))
                else -> jsonArray.put(value)
            }
        }
        return jsonArray
    }
}
