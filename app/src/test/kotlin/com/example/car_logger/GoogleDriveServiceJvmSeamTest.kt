package com.example.car_logger

import com.example.car_logger.services.DriveFileItem
import com.example.car_logger.services.GoogleDriveService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GoogleDriveServiceJvmSeamTest {

    @Test
    fun internalConstructorInstantiatesOnPureJvmWithoutPlayServices() {
        val service = GoogleDriveService()
        assertNull(service.currentUser.value)
    }

    @Test
    fun handleSignInResultUpdatesCurrentUserSafelyWithoutContext() {
        val service = GoogleDriveService()
        service.handleSignInResult(null)
        assertNull(service.currentUser.value)
    }

    @Test
    fun signOutClearsCurrentUserSafelyWithoutContext() = runTest {
        val service = GoogleDriveService()
        service.signOut()
        assertNull(service.currentUser.value)
    }

    @Test
    fun checkSilentSignInReturnsNullSafelyWithoutContext() = runTest {
        val service = GoogleDriveService()
        assertNull(service.checkSilentSignIn())
        assertNull(service.currentUser.value)
    }

    @Test
    fun listFoldersListFilesAndDownloadFileBytesReturnSafeDefaultsWithoutContext() = runTest {
        val service = GoogleDriveService()
        assertTrue(service.listFolders().isEmpty())
        assertTrue(service.listFolders(parentId = "folder-1", searchName = "Receipts").isEmpty())
        assertTrue(service.listFiles("folder-1").isEmpty())
        assertNull(service.downloadFileBytes("file-1"))
    }

    @Test
    fun getSignInIntentThrowsClearIllegalStateExceptionWithoutContext() {
        val service = GoogleDriveService()
        try {
            service.getSignInIntent()
            fail("Expected IllegalStateException when Android Context is absent")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("Context", ignoreCase = true))
        }
    }

    @Test
    fun openClassSeamSupportsPureJvmFakeSubclass() = runTest {
        val expectedFolder = DriveFileItem(
            id = "folder-42",
            name = "Blazer EV Receipts",
            mimeType = "application/vnd.google-apps.folder",
            isFolder = true
        )
        val expectedFile = DriveFileItem(
            id = "file-99",
            name = "2026-03-10_Tire_Rotation.pdf",
            mimeType = "application/pdf",
            size = 1024L,
            isFolder = false
        )
        val expectedBytes = byteArrayOf(1, 2, 3, 4)

        var signOutCalled = false
        val fakeService: GoogleDriveService = object : GoogleDriveService() {
            override suspend fun signOut() {
                signOutCalled = true
                super.signOut()
            }

            override suspend fun listFolders(parentId: String, searchName: String?): List<DriveFileItem> =
                listOf(expectedFolder)

            override suspend fun listFiles(folderId: String): List<DriveFileItem> =
                if (folderId == expectedFolder.id) listOf(expectedFile) else emptyList()

            override suspend fun downloadFileBytes(fileId: String): ByteArray? =
                if (fileId == expectedFile.id) expectedBytes else null
        }

        assertEquals(listOf(expectedFolder), fakeService.listFolders())
        assertEquals(listOf(expectedFile), fakeService.listFiles("folder-42"))
        assertArrayEquals(expectedBytes, fakeService.downloadFileBytes("file-99"))
        fakeService.signOut()
        assertTrue(signOutCalled)
    }
}
