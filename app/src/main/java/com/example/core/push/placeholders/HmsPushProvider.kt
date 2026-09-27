package com.example.core.push.placeholders

import com.example.core.push.PushProvider

/**
 * Placeholder for Huawei Mobile Services (HMS) Push Kit integration.
 */
class HmsPushProvider : PushProvider {
    override val providerName: String = "hms"

    override suspend fun registerDevice(userId: String): Result<String> {
        // Ready for HmsMessaging.getInstance().turnOnPush()
        return Result.success("hms_placeholder_token")
    }

    override suspend fun unregisterDevice(userId: String): Result<Unit> {
        return Result.success(Unit)
    }
}
