package com.example.nimrod.topic

import org.junit.Assert.assertEquals
import org.junit.Test

class TopicManagerTest {

    @Test
    fun `legal phrasing routes to legal`() {
        assertEquals(
            TopicManager.LEGAL,
            TopicManager.classify("Can you review the contract clause my attorney sent?")
        )
    }

    @Test
    fun `money phrasing routes to finance`() {
        assertEquals(
            TopicManager.FINANCE,
            TopicManager.classify("Help me build a budget so I can invest more each month")
        )
    }

    @Test
    fun `code phrasing routes to development`() {
        assertEquals(
            TopicManager.DEVELOPMENT,
            TopicManager.classify("Why does the gradle build fail when I compile the android module?")
        )
    }

    @Test
    fun `unrelated phrasing falls back to general`() {
        assertEquals(
            TopicManager.GENERAL,
            TopicManager.classify("What is the weather like tomorrow")
        )
    }

    @Test
    fun `blank input is general`() {
        assertEquals(TopicManager.GENERAL, TopicManager.classify("   "))
    }
}
