package com.example.roadguard.model

/**
 * User role constants.
 * Stored as strings in Firestore for flexibility.
 */
object UserRole {
    const val CITIZEN = "user"        // Regular citizen user
    const val OPERATOR = "operator"   // Road maintenance operator
    const val ADMIN = "admin"         // System administrator
}

data class User(
    val uid: String = "",
    val email: String = "",
    val role: String = UserRole.CITIZEN
) {
    fun isOperator(): Boolean = role == UserRole.OPERATOR || role == UserRole.ADMIN
    fun isAdmin(): Boolean = role == UserRole.ADMIN
}

