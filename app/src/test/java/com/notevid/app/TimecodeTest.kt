package com.notevid.app

import org.junit.Assert.*
import org.junit.Test

class TimecodeTest {
    @Test fun hundredths() {
        assertEquals(132450L, Timecode.parse("00:02:12.45"))
        assertEquals("00:02:12.45", Timecode.format(132450L))
        assertEquals(132456L, Timecode.parse("2:12.456"))
        assertEquals(12500L, Timecode.parse("12.5"))
    }

    @Test fun invalidInput() {
        assertNull(Timecode.parse("01:88:12"))
        assertNull(Timecode.parse("a"))
        assertNull(Timecode.parse("00:12:00.1234"))
    }

    @Test fun compareVersions() {
        assertTrue(VersionCheck.isNewer("v1.0.1", "1.0.0"))
        assertTrue(VersionCheck.isNewer("v2.0.0", "1.99.99"))
        assertFalse(VersionCheck.isNewer("v1.0.0", "1.0.0"))
        assertFalse(VersionCheck.isNewer("release-x", "1.0.0"))
    }
}
