package com.example.nimrod.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MessageDao {

    @Insert
    suspend fun insert(message: MessageEntity): Long

    /** Newest first - callers that need chronological order should reverse. */
    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE topicId = :topicId ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getByTopic(topicId: Int, limit: Int = 30): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAll(): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int

    @Query("DELETE FROM messages WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM messages")
    suspend fun clear()
}
