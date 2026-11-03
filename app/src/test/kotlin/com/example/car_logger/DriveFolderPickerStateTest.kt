package com.example.car_logger

import com.example.car_logger.ui.screens.dialogs.DriveFolderBreadcrumb
import com.example.car_logger.ui.screens.dialogs.DriveFolderPickerNavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveFolderPickerStateTest {

    @Test
    fun `initial state is at root My Drive`() {
        val state = DriveFolderPickerNavigationState()

        assertEquals("root", state.currentFolder.id)
        assertEquals("My Drive", state.currentFolder.name)
        assertTrue(state.isAtRoot)
        assertFalse(state.canNavigateBack)
        assertEquals("My Drive", state.breadcrumbPath)
        assertEquals(1, state.backStack.size)
    }

    @Test
    fun `openFolder pushes subfolder to navigation stack`() {
        val state = DriveFolderPickerNavigationState()

        state.openFolder("folder_1", "Cars")

        assertEquals("folder_1", state.currentFolder.id)
        assertEquals("Cars", state.currentFolder.name)
        assertFalse(state.isAtRoot)
        assertTrue(state.canNavigateBack)
        assertEquals("My Drive > Cars", state.breadcrumbPath)
        assertEquals(2, state.backStack.size)
    }

    @Test
    fun `openFolder multiple levels deep tracks hierarchy accurately`() {
        val state = DriveFolderPickerNavigationState()

        state.openFolder("cars_id", "Cars")
        state.openFolder("miata_id", "1994 Mazda Miata")
        state.openFolder("receipts_id", "Receipts")

        assertEquals("receipts_id", state.currentFolder.id)
        assertEquals("Receipts", state.currentFolder.name)
        assertFalse(state.isAtRoot)
        assertTrue(state.canNavigateBack)
        assertEquals("My Drive > Cars > 1994 Mazda Miata > Receipts", state.breadcrumbPath)
        assertEquals(4, state.backStack.size)
    }

    @Test
    fun `navigateBack pops one level up until root`() {
        val state = DriveFolderPickerNavigationState()

        state.openFolder("cars_id", "Cars")
        state.openFolder("miata_id", "1994 Mazda Miata")

        // Pop once
        val poppedOnce = state.navigateBack()
        assertTrue(poppedOnce)
        assertEquals("cars_id", state.currentFolder.id)
        assertEquals("Cars", state.currentFolder.name)
        assertTrue(state.canNavigateBack)

        // Pop to root
        val poppedTwice = state.navigateBack()
        assertTrue(poppedTwice)
        assertEquals("root", state.currentFolder.id)
        assertEquals("My Drive", state.currentFolder.name)
        assertFalse(state.canNavigateBack)
        assertTrue(state.isAtRoot)

        // Attempt pop at root should return false and not crash
        val poppedAtRoot = state.navigateBack()
        assertFalse(poppedAtRoot)
        assertEquals("root", state.currentFolder.id)
    }

    @Test
    fun `navigateToBreadcrumb jumps directly to selected parent level`() {
        val state = DriveFolderPickerNavigationState()

        state.openFolder("level_1", "One")
        state.openFolder("level_2", "Two")
        state.openFolder("level_3", "Three")
        state.openFolder("level_4", "Four")

        assertEquals(5, state.backStack.size)

        // Jump back to "One" (index 1: root is 0, One is 1)
        val jumped = state.navigateToBreadcrumb(1)
        assertTrue(jumped)
        assertEquals("level_1", state.currentFolder.id)
        assertEquals("One", state.currentFolder.name)
        assertEquals(2, state.backStack.size)

        // Jump to root (index 0)
        val jumpedToRoot = state.navigateToBreadcrumb(0)
        assertTrue(jumpedToRoot)
        assertEquals("root", state.currentFolder.id)
        assertEquals(1, state.backStack.size)
        assertTrue(state.isAtRoot)
    }

    @Test
    fun `navigateToBreadcrumb invalid index returns false`() {
        val state = DriveFolderPickerNavigationState()
        state.openFolder("level_1", "One")

        assertFalse(state.navigateToBreadcrumb(-1))
        assertFalse(state.navigateToBreadcrumb(1)) // current index, nothing to jump back to
        assertFalse(state.navigateToBreadcrumb(5))
    }

    @Test
    fun `resetToRoot clears navigation stack back to root`() {
        val state = DriveFolderPickerNavigationState()
        state.openFolder("a", "A")
        state.openFolder("b", "B")

        state.resetToRoot()

        assertEquals("root", state.currentFolder.id)
        assertEquals(1, state.backStack.size)
        assertTrue(state.isAtRoot)
    }
}
