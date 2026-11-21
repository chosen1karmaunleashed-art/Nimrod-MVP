package com.example.nimrod.memory

import com.example.nimrod.data.AppDatabase
import com.example.nimrod.data.MessageEntity
import com.example.nimrod.topic.TopicManager
import java.util.concurrent.TimeUnit

/**
 * Owns the "what Nimrod remembers" problem.
 *
 * Every message is tagged with a topic on the way in. When building context for a
 * request we combine:
 *   1. the most recent turns overall (short-term continuity), and
 *   2. the most recent turns from the current topic (subject recall),
 * de-duplicated and ordered oldest to newest.
 */
class MemoryManager(private val db: AppDatabase) {

    private val messageDao = db.messageDao()
    private val topicDao = db.topicDao()

    /** Stores a message, resolving or creating its topic. Returns the stored row. */
    suspend fun remember(text: String, role: String, topicName: String = TopicManager.GENERAL): MessageEntity {
        val now = System.currentTimeMillis()
        val topicId = resolveTopic(topicName, now)

        val entity = MessageEntity(
            content = text,
            role = role,
            timestamp = now,
            topicId = topicId
        )
        val id = messageDao.insert(entity)
        return entity.copy(id = id.toInt())
    }

    private suspend fun resolveTopic(name: String, now: Long): Int? {
        val existing = topicDao.findByName(name)
        if (existing != null) {
            topicDao.touch(existing.id, now)
            return existing.id
        }
        val inserted = topicDao.insert(com.example.nimrod.data.TopicEntity(name = name, lastActive = now))
        return if (inserted > 0) inserted.toInt() else topicDao.findByName(name)?.id
    }

    /**
     * Builds the memory block injected into the system prompt.
     * [recentLimit] caps the short-term window; [topicLimit] caps per-topic recall.
     */
    suspend fun buildContext(
        currentTopic: String,
        recentLimit: Int = 12,
        topicLimit: Int = 12
    ): String {
        val recent = messageDao.getRecent(recentLimit)

        val topicMessages = topicDao.findByName(currentTopic)?.let { topic ->
            messageDao.getByTopic(topic.id, topicLimit)
        } ?: emptyList()

        val merged = (recent + topicMessages)
            .distinctBy { it.id }
            .sortedBy { it.timestamp }

        if (merged.isEmpty()) return "(no prior conversation)"

        return merged.joinToString(separator = "\n") { message ->
            val speaker = when (message.role) {
                "user" -> "User"
                "nimrod" -> "Nimrod"
                else -> message.role
            }
            "$speaker: ${message.content}"
        }
    }

    /** Which topic was talked about most recently, or general if the log is empty. */
    suspend fun lastTopic(): String = topicDao.getRecent(1).firstOrNull()?.name ?: TopicManager.GENERAL

    /** Trims history so the database does not grow without bound. */
    suspend fun prune(olderThanDays: Int = 30): Int {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(olderThanDays.toLong())
        return messageDao.deleteOlderThan(cutoff)
    }

    suspend fun messageCount(): Int = messageDao.count()
}
