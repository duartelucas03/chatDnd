package com.example.easychat.data.repository

import com.example.easychat.model.UserModel
import com.example.easychat.utils.SupabaseClientProvider

class UserRepository {

    private val db = SupabaseClientProvider.db

    suspend fun getCurrentUser(): UserModel? = try {
        db.from("users").select {
            filter { eq("id", SupabaseClientProvider.currentUserId()) }
        }.decodeSingleOrNull()
    } catch (e: Exception) { null }

    suspend fun saveUser(user: UserModel) {
        db.from("users").upsert(user)
    }

    suspend fun updateFcmToken(token: String) {
        db.from("users").update({ set("fcm_token", token) }) {
            filter { eq("id", SupabaseClientProvider.currentUserId()) }
        }
    }

    suspend fun updateStatus(status: String) {
        db.from("users").update({
            set("status", status)
            set("last_seen", java.time.Instant.now().toString())
        }) {
            filter { eq("id", SupabaseClientProvider.currentUserId()) }
        }
    }

    suspend fun searchUsers(term: String): List<UserModel> {
        if (term.isBlank()) return emptyList()
        return try {
            db.from("users").select {
                filter { ilike("username", "%$term%") }
            }.decodeList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getUserById(userId: String): UserModel? = try {
        db.from("users").select {
            filter { eq("id", userId) }
        }.decodeSingleOrNull()
    } catch (e: Exception) { null }

    suspend fun getUsersByIds(userIds: List<String>): List<UserModel> {
        if (userIds.isEmpty()) return emptyList()
        return try {
            db.from("users").select {
                filter { isIn("id", userIds) }
            }.decodeList()
        } catch (e: Exception) { emptyList() }
    }

    /** Cruza lista de números de telefone com usuários cadastrados */
    suspend fun getUsersByPhones(phones: List<String>): List<UserModel> {
        if (phones.isEmpty()) return emptyList()
        return try {
            db.from("users").select {
                filter { isIn("phone", phones) }
            }.decodeList()
        } catch (e: Exception) { emptyList() }
    }
}