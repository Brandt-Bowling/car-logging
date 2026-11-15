package com.example.car_logger

import com.example.car_logger.ui.screens.home.tabs.DeveloperDashboardUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class DeveloperDashboardUrlsTest {

    @Test
    fun testDeveloperDashboardUrlsAreValidHttpsUris() {
        val urls = listOf(
            DeveloperDashboardUrls.GEMINI,
            DeveloperDashboardUrls.AUTO_DEV,
            DeveloperDashboardUrls.SMARTCAR,
            DeveloperDashboardUrls.TESLA
        )

        for (urlString in urls) {
            val uri = URI.create(urlString)
            assertEquals("https", uri.scheme)
            assertTrue("Host should not be blank: $urlString", !uri.host.isNullOrBlank())
        }

        assertEquals("https://aistudio.google.com/apikey", DeveloperDashboardUrls.GEMINI)
        assertEquals("https://auto.dev/dashboard", DeveloperDashboardUrls.AUTO_DEV)
        assertEquals("https://dashboard.smartcar.com/apps", DeveloperDashboardUrls.SMARTCAR)
        assertEquals("https://developer.tesla.com/dashboard", DeveloperDashboardUrls.TESLA)
    }
}
