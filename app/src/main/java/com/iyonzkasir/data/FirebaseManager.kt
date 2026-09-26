package com.iyonzkasir.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestoreSettings
import com.google.firebase.firestore.ktx.persistentCacheSettings
import kotlinx.coroutines.tasks.await

/**
 * Singleton untuk Firebase Auth + Firestore.
 * Fokus: owner login + sync data.
 */
object FirebaseManager {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = firestoreSettings {
                setLocalCacheSettings(persistentCacheSettings {
                    // Cache offline otomatis (untuk sync tanpa internet)
                })
            }
        }
    }

    val currentUser: FirebaseUser? get() = auth.currentUser
    val isLoggedIn: Boolean get() = auth.currentUser != null
    val userEmail: String? get() = auth.currentUser?.email
    val userId: String? get() = auth.currentUser?.uid

    fun db(): FirebaseFirestore = firestore
    fun authInstance(): FirebaseAuth = auth

    /**
     * Login owner pakai email + password Firebase.
     */
    suspend fun loginOwner(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) Result.success(user)
            else Result.failure(Exception("User kosong setelah login"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Logout owner.
     */
    fun logout() {
        auth.signOut()
    }

    /**
     * Cek apakah user sekarang owner (login Firebase).
     */
    fun isOwnerLoggedIn(): Boolean = isLoggedIn
}
