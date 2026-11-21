package com.example.nimrod.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TopicDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(topic: TopicEntity): Long

    @Query("SELECT * FROM topics WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TopicEntity?

    @Query("UPDATE topics SET lastActive = :timestamp WHERE id = :id")
    suspend fun touch(id: Int, timestamp: Long)

    @Query("SELECT * FROM topics ORDER BY lastActive DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 5): List<TopicEntity>

    @Query("DELETE FROM topics")
    suspend fun clear()
}
