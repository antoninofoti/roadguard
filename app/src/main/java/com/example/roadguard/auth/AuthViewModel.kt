package com.example.roadguard.auth

import androidx.lifecycle.ViewModel
import com.example.roadguard.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthViewModel : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    init {
        _isLoggedIn.value = auth.currentUser != null
    }

    fun login(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    _isLoggedIn.value = true
                    onSuccess()
                } else {
                    onError(it.exception?.message ?: "Login failed")
                }
            }
    }

    fun signUp(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = task.result?.user
                    if (firebaseUser != null) {
                        val user = User(
                            uid = firebaseUser.uid,
                            email = firebaseUser.email ?: ""
                        )
                        db.collection("users").document(firebaseUser.uid).set(user)
                            .addOnSuccessListener {
                                _isLoggedIn.value = true
                                onSuccess()
                            }
                            .addOnFailureListener { e ->
                                onError(e.message ?: "Failed to save user data")
                            }
                    } else {
                        onError("Failed to create user")
                    }
                } else {
                    onError(task.exception?.message ?: "Sign up failed")
                }
            }
    }

    fun logout() {
        auth.signOut()
        _isLoggedIn.value = false
    }
}
