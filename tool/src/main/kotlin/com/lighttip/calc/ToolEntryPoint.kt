package com.lighttip.calc

import android.util.Log
import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.sdk.shared.LightServerData
import kotlinx.coroutines.flow.StateFlow

@EntryPoint
object ToolEntryPoint : LightEntryPoint {
    override suspend fun onToolCreate(serverData: StateFlow<LightServerData?>) {
        Log.d("LightTip", "LightTip initialized")
    }

    override suspend fun onPushNotification(data: ByteArray) {
        Log.d("LightTip", "Ignoring unsupported push payload")
    }
}
