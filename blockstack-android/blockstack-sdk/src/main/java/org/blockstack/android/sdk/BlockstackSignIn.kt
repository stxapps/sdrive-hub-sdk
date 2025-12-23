package org.blockstack.android.sdk

import android.content.Context
import org.blockstack.android.sdk.model.BlockstackConfig

class BlockstackSignIn(private val sessionStore: ISessionStore,
                       private val appConfig: BlockstackConfig) {

    suspend fun redirectUserToSignIn(context: Context, sendToSignIn: Boolean = false) {
        throw Error("Not implemented")
    }
}
