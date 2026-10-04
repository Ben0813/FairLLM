package be.itspace.fairllm

import org.junit.Assert.*
import org.junit.Test

class AgentProfileTest {
    private val profile = AgentProfile(id = "writer", name = "Rédacteur", instructions = "Écris des textes courts.",
        modelSha = "a".repeat(64), temperature = 0.4f, topP = 0.9f, maxTokens = 256, antiHallucination = false)

    @Test fun allAgentParametersSurviveSerialization() {
        assertEquals(profile, AgentProfile.fromJson(profile.toJson()))
    }

    @Test fun instructionsAndFactualCautionAreBothSent() {
        assertEquals("Écris des textes courts.", profile.systemPrompt())
        val cautious = profile.copy(antiHallucination = true).systemPrompt()
        assertTrue(cautious.startsWith(profile.instructions))
        assertTrue(cautious.contains(ANTI_HALLUCINATION_PROMPT))
    }

    @Test fun rejectsEmptyNameAndInvalidSamplingParameters() {
        listOf(profile.copy(name = " "), profile.copy(temperature = Float.NaN), profile.copy(topP = 0f),
            profile.copy(maxTokens = -1), profile.copy(modelSha = "invalid")).forEach {
            assertThrows(IllegalArgumentException::class.java) { it.validate() }
        }
    }
}
