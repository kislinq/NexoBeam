package com.example.core.push.placeholders

import com.example.core.push.PushProvider

/**
 * Placeholder for UnifiedPush, WebPush, or custom self-hosted push transports.
 */
class FuturePushProvider(override val providerName: String = "custom_push") : PushProvider {
    override suspend fun registerDevice(userId: String): Result<String> {
        return Result.success("custom_provider_token")
    }

    override suspend fun unregisterDevice(userId: String): Result<Unit> {
        return Result.success(Unit)
    }
}
