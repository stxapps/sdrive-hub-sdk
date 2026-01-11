import ExpoModulesCore
import Blockstack

public class RNBlockstackSDKModule: Module {

    private var config: [String: Any?]?

    // Each module class must implement the definition function. The definition consists of components
    // that describes the module's functionality and behavior.
    // See https://docs.expo.dev/modules/module-api for more details about available components.
    public func definition() -> ModuleDefinition {
        // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
        // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
        // The module will be accessible from `requireNativeModule('RNBlockstackSDK')` in JavaScript.
        Name("RNBlockstackSDK")

        // Defines a JavaScript function that always returns a Promise and whose native code
        // is by default dispatched on the different thread than the JavaScript runtime runs on.
        AsyncFunction("hasSession") {
            return ["hasSession": self.config != nil]
        }

        AsyncFunction("createSession") { (config: [String: Any?]?) in
            self.config = config

            // blockstack-ios uses Google Promises that by default, resolve and reject are dispatched to run in main queue.
            // If long running tasks i.e. decrypt, UI will hault. So change to global queue instead.
            // https://github.com/google/promises/blob/master/g3doc/index.md#default-dispatch-queue
            //
            // DispatchQueue is global variable, affect every native modules that use Google Promises.
            // Should be fine but might be better to set this in AppDelegate.m.
            DispatchQueue.promises = .global()

            return ["loaded": true]
        }

        AsyncFunction("isUserSignedIn") {
            return ["signedIn": Blockstack.shared.isUserSignedIn()]
        }

        AsyncFunction("signUserOut") {
            Blockstack.shared.signUserOut()
            return ["signedOut": true]
        }

        AsyncFunction("updateUserData") { (dict: [String: Any?]) in
            var mDict: [String: Any] = dict.compactMapValues { $0 }

            if let decentralizedID = dict["decentralizedID"] as? String {
                mDict["iss"] = decentralizedID
            } else {
                mDict.removeValue(forKey: "iss")
            }
            if let appPrivateKey = dict["appPrivateKey"] as? String {
                mDict["private_key"] = appPrivateKey
            } else {
                mDict.removeValue(forKey: "private_key")
            }
            if let identityAddress = dict["identityAddress"] as? String {
                mDict["public_keys"] = [identityAddress]
            } else {
                mDict.removeValue(forKey: "public_keys")
            }

            let jsonDecoder = JSONDecoder()
            let data = try JSONSerialization.data(withJSONObject: mDict)
            let userData = try jsonDecoder.decode(UserData.self, from: data)

            Blockstack.shared.updateUserData(userData: userData)
            return ["updated": true]
        }

        AsyncFunction("loadUserData") {
            guard let userData = Blockstack.shared.loadUserData() else {
                throw Exception(name: "ERR_LOAD_USER_DATA", description: "loadUserData returns nil")
            }
            return userData.dictionary ?? [:]
        }

        AsyncFunction("putFile") { (fileName: String, content: String, options: [String: Any?]?, promise: Promise) in
            let encrypt = options?["encrypt"] as? Bool ?? true
            let dir = options?["dir"] as? String ?? ""

            Blockstack.shared.putFile(to: fileName, text: content, encrypt: encrypt, dir: dir) { result, error in
                if let error = error {
                    promise.reject("ERR_PUT_FILE", "putFile Error: \(error.localizedDescription)")
                    return
                }

                guard let fileUrl = result else {
                    promise.reject("ERR_PUT_FILE", "putFile Error: result is nil")
                    return
                }

                promise.resolve(["fileUrl": fileUrl])
            }
        }

        AsyncFunction("getFile") { (path: String, options: [String: Any?]?, promise: Promise) in
            let decrypt = options?["decrypt"] as? Bool ?? true
            let dir = options?["dir"] as? String ?? ""

            Blockstack.shared.getFile(at: path, decrypt: decrypt, dir: dir) { value, error in
                if let error = error {
                    promise.reject("ERR_GET_FILE", "getFile Error: \(error.localizedDescription)")
                    return
                }

                if decrypt {
                    guard let decryptedValue = value as? DecryptedValue else {
                        promise.reject("ERR_GET_FILE", "getFile Error: options decrypt is true but value is not DecryptedValue.")
                        return
                    }

                    if decryptedValue.isString {
                        promise.resolve(["fileContents": decryptedValue.plainText])
                    } else {
                        promise.resolve(["fileContentsEncoded": decryptedValue.bytes?.toBase64()])
                    }
                    return
                }

                promise.resolve(["fileContents": value])
            }
        }

        AsyncFunction("deleteFile") { (path: String, options: [String: Any?]?, promise: Promise) in
            let wasSigned = options?["wasSigned"] as? Bool ?? false

            Blockstack.shared.deleteFile(at: path, wasSigned: wasSigned) { error in
                if let error = error {
                    promise.reject("ERR_DELETE_FILE", "deleteFile Error: \(error.localizedDescription)")
                    return
                }

                promise.resolve(["deleted": true])
            }
        }

        AsyncFunction("performFiles") { (pfData: String, dir: String, promise: Promise) in
            Blockstack.shared.performFiles(pfData: pfData, dir: dir) { result, error in
                if let error = error {
                    promise.reject("ERR_PERFORM_FILES", "performFiles Error: \(error.localizedDescription)")
                    return
                }

                guard let unResult = result else {
                    promise.reject("ERR_PERFORM_FILES", "performFiles Error: result is nil")
                    return
                }

                promise.resolve(unResult)
            }
        }

        AsyncFunction("listFiles") { (promise: Promise) in
            var files = [String]()
            Blockstack.shared.listFiles(callback: {
                files.append($0)
                return true
            }, completion: { fileCount, error in
                if let error = error {
                    promise.reject("ERR_LIST_FILES", "listFiles Error: \(error.localizedDescription)")
                    return
                }

                promise.resolve(["files": files, "fileCount": fileCount])
            })
        }

        AsyncFunction("signECDSA") { (privateKey: String, content: String) in
            // @stacks/encryption uses noble-secp256k1
            //   and in noble-secp256k1/index.ts#L1148, default canonical is true
            guard let sigObj = Blockstack.signECDSA(privateKey: privateKey, content: content, canonical: true) else {
                throw Exception(name: "ERR_SIGN_ECDSA", description: "signECDSA returns nil")
            }
            return sigObj.dictionary ?? [:]
        }
    }
}

extension Encodable {
    var dictionary: [String: Any?]? {
        guard let data = try? JSONEncoder().encode(self) else { return nil }
        return (try? JSONSerialization.jsonObject(with: data, options: .allowFragments)).flatMap { $0 as? [String: Any?] }
    }
}
