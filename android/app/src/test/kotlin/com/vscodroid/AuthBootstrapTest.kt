package com.vscodroid

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthBootstrapTest {
    @Test
    fun `only the explicit successful completion for this document is accepted`() {
        assertTrue(authBootstrapSucceeded("[7,true]", 7L))
        for (marker in listOf(null, "null", "[]", "[7]", "[7,false]", "[6,true]", "[7,true,true]", "[7,1]", "[7.5,true]", "[\"7\",true]", "not json")) {
            assertFalse(authBootstrapSucceeded(marker, 7L), marker)
        }
    }
}
