package com.example.core.push

interface PushProvider {
    val providerName: String
    suspend fun registerDevice(userId: String): Result<String>
    suspend fun unregisterDevice(userId: String): Result<Unit>
}
