package com.rout.arixy

import com.rout.arixy.core.JealousyLevel
import com.rout.arixy.core.MemoryManager
import com.rout.arixy.core.MemoryEntry
import com.rout.arixy.core.PersonalityEngine
import com.rout.arixy.core.PersonalityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalityEngineTest {

    @Test
    fun stopCommandsMatchExactly() {
        assertTrue(PersonalityEngine.isStopCommand("OK OK, ruko"))
        assertTrue(PersonalityEngine.isStopCommand("Arixy, stop"))
        assertTrue(PersonalityEngine.isStopCommand("bas, band karo"))
        assertTrue(PersonalityEngine.isStopCommand("Cancel"))
        assertTrue(PersonalityEngine.isStopCommand("ruk jao"))
        assertTrue(PersonalityEngine.isStopCommand("stop speaking"))
        assertTrue(PersonalityEngine.isStopCommand("stop the task"))
        assertTrue(PersonalityEngine.isStopCommand("stop"))
    }

    @Test
    fun normalSpeechIsNotStop() {
        assertFalse(PersonalityEngine.isStopCommand("YouTube kholo"))
        assertFalse(PersonalityEngine.isStopCommand("mujhe stopwatch lagao"))
        assertFalse(PersonalityEngine.isStopCommand("kya tum ruko matlab samajhte ho"))
        assertFalse(PersonalityEngine.isStopCommand(""))
    }

    @Test
    fun jealousyDisabledForProfessionalAndSerious() {
        assertFalse(PersonalityEngine.jealousyAllowed(PersonalityMode.PROFESSIONAL))
        assertFalse(PersonalityEngine.jealousyAllowed(PersonalityMode.SERIOUS_SUPPORT))
        assertTrue(PersonalityEngine.jealousyAllowed(PersonalityMode.FRIENDLY))
        assertTrue(PersonalityEngine.jealousyAllowed(PersonalityMode.EXPRESSIVE_COMPANION))
    }

    @Test
    fun promptInjectsNameOnlyWhenSaved() {
        val withName = PersonalityEngine.buildSystemPrompt(
            PersonalityMode.FRIENDLY, JealousyLevel.OFF, "Rout", emptyList())
        assertTrue(withName.contains("\"Rout\""))
        val noName = PersonalityEngine.buildSystemPrompt(
            PersonalityMode.FRIENDLY, JealousyLevel.OFF, "", emptyList())
        assertFalse(noName.contains("\"Rout\""))
        assertTrue(noName.contains("No name is saved"))
    }

    @Test
    fun promptCarriesMemoriesAndPersonality() {
        val p = PersonalityEngine.buildSystemPrompt(
            PersonalityMode.SWEET, JealousyLevel.PLAYFUL, "Rout",
            listOf("Mango pasand hai", "Gym subah karta hai"))
        assertTrue(p.contains("PERSONALITY: SWEET"))
        assertTrue(p.contains("PLAYFUL JEALOUSY"))
        assertTrue(p.contains("Mango pasand hai"))
        assertTrue(p.contains("Gym subah karta hai"))
    }

    @Test
    fun jealousyNeverInPromptForProfessional() {
        val p = PersonalityEngine.buildSystemPrompt(
            PersonalityMode.PROFESSIONAL, JealousyLevel.EXTRA_DRAMATIC, "Rout", emptyList())
        assertFalse(p.contains("PLAYFUL JEALOUSY"))
        assertTrue(p.contains("PERSONALITY: PROFESSIONAL"))
    }

    @Test
    fun promptKeepsActionArsenalAndJsonSchema() {
        val p = PersonalityEngine.buildSystemPrompt(
            PersonalityMode.FRIENDLY, JealousyLevel.OFF, "", emptyList())
        assertTrue(p.contains("OPEN_APP"))
        assertTrue(p.contains("MAKE_CALL"))
        assertTrue(p.contains("STRICT JSON OUTPUT"))
        assertTrue(p.contains("OPEN_WEBPAGE"))
    }
}

class MemoryStoreTest {

    @Test
    fun encodeDecodeRoundTrip() {
        val entries = listOf(
            MemoryEntry(1L, "Mango pasand hai", 1000L),
            MemoryEntry(2L, "Naam Rout hai", 2000L)
        )
        val decoded = MemoryManager.decode(MemoryManager.encode(entries))
        assertEquals(2, decoded.size)
        assertEquals("Mango pasand hai", decoded[0].text)
        assertEquals(1L, decoded[0].id)
        assertEquals("Naam Rout hai", decoded[1].text)
    }

    @Test
    fun decodeToleratesGarbage() {
        assertTrue(MemoryManager.decode("not json").isEmpty())
        assertTrue(MemoryManager.decode("").isEmpty())
        assertTrue(MemoryManager.decode("[{\"text\":\"\"}]").isEmpty())
    }
}
