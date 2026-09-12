package app.keeply.domain

import kotlin.test.Test
import kotlin.test.assertTrue

class SmokeTest {
    @Test
    fun toolchainWorks() {
        assertTrue(Smoke.ok())
    }
}
