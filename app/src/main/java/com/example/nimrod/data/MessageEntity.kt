package com.example.nimrod.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single turn of conversation.
 *
 * [topicId] links the message to the [TopicEntity] it belongs to so memory can be
 * recalled per subject instead of replaying the whole history.
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("topicId"), Index("timestamp")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    val role: String,
    val timestamp: Long,
    val topicId: Int? = null
)
