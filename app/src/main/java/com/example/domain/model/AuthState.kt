package com.example.domain.model

sealed class AuthState {
    data object Loading : AuthState()
    data object Unauthenticated : AuthState()
    data class RequiresProfileSetup(val userId: String, val email: String) : AuthState()
    data class Authenticated(val userId: String, val email: String) : AuthState()
}
