package com.sunnychung.application.multiplatform.giantlogviewer

import com.sunnychung.application.multiplatform.giantlogviewer.ux.ToastManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToastManagerTest {

    @Test
    fun normalToastReplacesPersistentToastAndResetsPersistence() {
        val toastManager = ToastManager()
        toastManager.showToast("Copying selection...", isPersistent = true)

        assertTrue(toastManager.isMessagePersistent.value)

        toastManager.showToast("Copied 5,242,880 bytes.")

        assertEquals("Copied 5,242,880 bytes.", toastManager.message.value)
        assertFalse(toastManager.isMessagePersistent.value)
    }
}
