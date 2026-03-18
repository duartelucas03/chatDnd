package com.example.easychat.data.local

import androidx.room.*

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE chatroomId = :chatroomId ORDER BY createdAt DESC")
    suspend fun getMessages(chatroomId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE messages SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("SELECT * FROM messages WHERE isSynced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsyncedMessages(): List<MessageEntity>

    @Query("DELETE FROM messages WHERE chatroomId = :chatroomId")
    suspend fun deleteForChatroom(chatroomId: String)
}
