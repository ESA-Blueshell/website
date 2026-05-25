package net.blueshell.api.platform.integration.sync.target.contact

import net.blueshell.api.platform.integration.contact.adapter.ContactData
import net.blueshell.api.platform.integration.contact.adapter.listmonk.ListmonkContactAdapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ListmonkContactSyncTargetTest {

    private val adapter: ListmonkContactAdapter = mock()
    private val target = ListmonkContactSyncTarget(adapter)
    private val data = ContactData("a@b.c", "A", "B", null, false, false)

    @Test
    fun `null data and null id is a no-op`() {
        assertNull(target.push(1L, null, null))
        verify(adapter, never()).createContact(any())
        verify(adapter, never()).updateContact(any(), any())
        verify(adapter, never()).deleteContact(any())
    }

    @Test
    fun `null data with existing id deletes and returns null`() {
        assertNull(target.push(1L, null, "42"))
        verify(adapter).deleteContact(42L)
    }

    @Test
    fun `data with no existing id creates and returns the new id`() {
        whenever(adapter.createContact(data)).thenReturn(99L)
        assertEquals("99", target.push(1L, data, null))
        verify(adapter).createContact(data)
    }

    @Test
    fun `data with existing id updates and keeps the same id`() {
        assertEquals("42", target.push(1L, data, "42"))
        verify(adapter).updateContact(eq(42L), eq(data))
    }
}
