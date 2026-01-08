import ExpoModulesCore
import Blockstack

public class RNBlockstackSDKModule: Module {
    
    private var config: [String: Any]?

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

        AsyncFunction("createSession") { (config: [String: Any]?) in
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

        
    }
}
