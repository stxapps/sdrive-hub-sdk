import { NativeModule, requireNativeModule } from 'expo';

import { RNBlockstackSDKModuleEvents } from './RNBlockstackSDK.types';

declare class RNBlockstackSDKModule extends NativeModule<RNBlockstackSDKModuleEvents> {
  setValueAsync(value: string): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<RNBlockstackSDKModule>('RNBlockstackSDK');
