package com.docscan

import com.docscan.data.model.IdCardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdCardModeTest {
    @Test
    fun `all six reference card options are available in the expected order`() {
        assertEquals(
            listOf("General", "ID Card", "Driver License", "Passport", "Bank Card", "Certificate"),
            IdCardType.entries.map { it.title }
        )
    }

    @Test
    fun `front and back only required for actual two-sided cards`() {
        assertTrue(IdCardType.ID_CARD.isTwoSided)
        assertTrue(IdCardType.DRIVER_LICENSE.isTwoSided)
        assertTrue(IdCardType.BANK_CARD.isTwoSided)
        assertFalse(IdCardType.GENERAL.isTwoSided)
        assertFalse(IdCardType.PASSPORT.isTwoSided)
        assertFalse(IdCardType.CERTIFICATE.isTwoSided)
    }

    @Test
    fun `gallery selection enforces two sides but certificates have no image limit`() {
        for (type in listOf(IdCardType.ID_CARD, IdCardType.DRIVER_LICENSE, IdCardType.BANK_CARD)) {
            assertEquals(2, type.maximumPages)
            assertEquals(2, type.remainingPages(0))
            assertEquals(1, type.remainingPages(1))
            assertEquals(0, type.remainingPages(2))
        }
        assertEquals(1, IdCardType.PASSPORT.maximumPages)
        assertEquals(1, IdCardType.GENERAL.maximumPages)
        assertEquals(null, IdCardType.CERTIFICATE.maximumPages)
        assertEquals(null, IdCardType.CERTIFICATE.remainingPages(500))
    }
}
