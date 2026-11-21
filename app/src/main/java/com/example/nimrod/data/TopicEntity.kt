package com.example.nimrod.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A conversation topic, e.g. "legal", "finance", "development", "general".
 * [lastActive] drives which topics get pulled into context.
 */
@Entity(
    tableName = "topics",
    indices = [Index(value = ["name"], unique = true)]
)
data class TopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val lastActive: Long = System.currentTimeMillis()
)
