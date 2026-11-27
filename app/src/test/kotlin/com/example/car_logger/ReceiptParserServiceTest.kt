package com.example.car_logger

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.services.GeminiAiService
import com.example.car_logger.services.ai.ReceiptParserService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class ReceiptParserServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createPrefs(apiKey: String?): Pair<PreferencesManager, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefsFile = File(tempFolder.newFolder(), "receipt_test.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { prefsFile }
        val prefs = PreferencesManager(dataStore)
        if (apiKey != null) {
            runBlocking { prefs.setGeminiApiKey(apiKey) }
        }
        return prefs to scope
    }

    private fun geminiCandidateResponseJson(innerText: String): String {
        val escaped = Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(innerText))
        return """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": $escaped }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
    }

    private fun mockClient(
        code: Int,
        responseBody: String,
        onRequest: ((url: String, requestBody: String) -> Unit)? = null
    ): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val buffer = Buffer()
            req.body?.writeTo(buffer)
            val reqBody = buffer.readUtf8()
            onRequest?.invoke(req.url.toString(), reqBody)
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("OK")
                .body(responseBody.toResponseBody("application/json".toMediaType()))
                .build()
        }
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    @Test
    fun `parseReceipt sends JPEG inlineData via REST and parses JSON response`() {
        val (prefs, scope) = createPrefs("test-gemini-key")
        try {
            val sampleBytes = "fake-jpeg-receipt-bytes".toByteArray()
            var capturedUrl = ""
            var capturedBody = ""
            val innerJson = """
                {
                  "title": "Brake Pad Replacement",
                  "date": "2026-04-12",
                  "odometer": 48250,
                  "cost": 349.95,
                  "description": "Replaced front pads and resurfaced rotors."
                }
            """.trimIndent()
            val client = mockClient(200, geminiCandidateResponseJson(innerJson)) { url, body ->
                capturedUrl = url
                capturedBody = body
            }
            val service = GeminiAiService(preferencesManager = prefs, client = client)

            val result = runBlocking {
                service.parseReceipt("receipt.jpg", "image/jpeg", sampleBytes)
            }

            assertTrue(capturedUrl.contains("gemini-2.5-flash:generateContent?key=test-gemini-key"))
            val requestRoot = Json.parseToJsonElement(capturedBody).jsonObject
            val parts = requestRoot["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
            val inlineData = parts[1].jsonObject["inlineData"]!!.jsonObject
            assertEquals("image/jpeg", inlineData["mimeType"]!!.jsonPrimitive.content)
            assertEquals(Base64.getEncoder().encodeToString(sampleBytes), inlineData["data"]!!.jsonPrimitive.content)

            assertEquals("gemini", result.source)
            assertEquals("Brake Pad Replacement", result.title)
            assertEquals("2026-04-12", result.date)
            assertEquals(48250, result.odometer)
            assertEquals(349.95, result.cost ?: 0.0, 0.001)
            assertEquals("Replaced front pads and resurfaced rotors.", result.description)
            assertNull(result.error)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `parseReceipt sends PDF inlineData directly over REST`() {
        val (prefs, scope) = createPrefs("pdf-key")
        try {
            val pdfBytes = "%PDF-1.4 sample receipt".toByteArray()
            var capturedMime = ""
            var capturedData = ""
            val innerJson = """
                {
                  "title": "Tire Rotation",
                  "date": "2026-05-01",
                  "odometer": 51000,
                  "cost": 40.00,
                  "description": "Four-wheel tire rotation."
                }
            """.trimIndent()
            val client = mockClient(200, geminiCandidateResponseJson(innerJson)) { _, body ->
                val requestRoot = Json.parseToJsonElement(body).jsonObject
                val parts = requestRoot["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
                val inlineData = parts[1].jsonObject["inlineData"]!!.jsonObject
                capturedMime = inlineData["mimeType"]!!.jsonPrimitive.content
                capturedData = inlineData["data"]!!.jsonPrimitive.content
            }
            val service = GeminiAiService(preferencesManager = prefs, client = client)

            val result = runBlocking {
                service.parseReceipt("invoice.pdf", "application/pdf", pdfBytes)
            }

            assertEquals("application/pdf", capturedMime)
            assertEquals(Base64.getEncoder().encodeToString(pdfBytes), capturedData)
            assertEquals("gemini", result.source)
            assertEquals("Tire Rotation", result.title)
            assertEquals(51000, result.odometer)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `parseReceiptJsonResponse strips markdown code fences`() {
        val fenced = """
            ```json
            {
              "title": "State Inspection",
              "date": "2026-03-10",
              "odometer": null,
              "cost": 25.50,
              "description": "Annual emissions and safety inspection."
            }
            ```
        """.trimIndent()

        val parsed = ReceiptParserService.parseReceiptJsonResponse(fenced)
        assertEquals("State Inspection", parsed.title)
        assertEquals("2026-03-10", parsed.date)
        assertEquals(0, parsed.odometer)
        assertEquals(25.50, parsed.cost ?: 0.0, 0.001)
        assertEquals("Annual emissions and safety inspection.", parsed.description)
    }

    @Test
    fun `parseReceipt falls back to filename on HTTP error or blank API key`() {
        val (prefsWithKey, scope1) = createPrefs("bad-key")
        val (prefsNoKey, scope2) = createPrefs(null)
        try {
            val failingClient = mockClient(
                code = 500,
                responseBody = """{"error":{"message":"Upstream failure"}}"""
            )
            val serviceWithError = GeminiAiService(preferencesManager = prefsWithKey, client = failingClient)
            val errResult = runBlocking {
                serviceWithError.parseReceipt(
                    fileName = "2025-11-04_Oil_Change_62000mi_$78.pdf",
                    mimeType = "application/pdf",
                    fileBytes = "bytes".toByteArray()
                )
            }
            assertEquals("filename", errResult.source)
            assertEquals("Oil & Filter Change", errResult.title)
            assertEquals("2025-11-04", errResult.date)
            assertEquals(62000, errResult.odometer)
            assertEquals(78.0, errResult.cost ?: 0.0, 0.001)
            assertTrue(!errResult.error.isNullOrBlank())

            val serviceWithoutKey = GeminiAiService(preferencesManager = prefsNoKey, client = failingClient)
            val noKeyResult = runBlocking {
                serviceWithoutKey.parseReceipt(
                    fileName = "2026-01-15_Brake_Service_71000mi.jpg",
                    mimeType = "image/jpeg",
                    fileBytes = "bytes".toByteArray()
                )
            }
            assertEquals("filename", noKeyResult.source)
            assertEquals("Brake Service", noKeyResult.title)
            assertEquals(71000, noKeyResult.odometer)
            assertNull(noKeyResult.error)
        } finally {
            scope1.cancel()
            scope2.cancel()
        }
    }
}
