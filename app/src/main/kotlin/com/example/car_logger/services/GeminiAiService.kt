package com.example.car_logger.services

import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.di.AppContainer
import com.example.car_logger.services.ai.ElectricityAiParserService
import com.example.car_logger.services.ai.GeminiHttpClient
import com.example.car_logger.services.ai.ReceiptParserService
import com.example.car_logger.services.ai.VehicleImageGeneratorService
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import java.time.LocalDate

@Serializable
data class ParsedReceiptResult(
    val title: String,
    val date: String,
    val odometer: Int,
    val cost: Double? = null,
    val description: String? = null,
    val source: String = "filename",
    val error: String? = null
)

class GeminiAiService(
    preferencesManager: PreferencesManager? = null,
    internal val client: OkHttpClient = AppContainer.newSharedHttpClient(
        connectTimeoutSeconds = AppContainer.GEMINI_CONNECT_TIMEOUT_SECONDS,
        readTimeoutSeconds = AppContainer.READ_TIMEOUT_SECONDS
    )
) {
    private val httpClient = GeminiHttpClient(client)
    private val receiptParserService = ReceiptParserService(preferencesManager, httpClient)
    private val vehicleImageGeneratorService = VehicleImageGeneratorService(preferencesManager, httpClient)
    private val electricityAiParserService = ElectricityAiParserService(preferencesManager, httpClient)

    companion object {
        fun parseImageGenerationError(statusCode: Int, rawBody: String): String =
            GeminiHttpClient.parseImageGenerationError(statusCode, rawBody)

        internal fun parseChargerUsageJsonResponse(
            rawJson: String,
            fileName: String,
            fallbackDateIso: String = LocalDate.now().toString()
        ): List<ParsedChargingSessionDraft> =
            ElectricityAiParserService.parseChargerUsageJsonResponse(rawJson, fileName, fallbackDateIso)

        internal fun parseUtilityBillJsonResponse(
            rawJson: String,
            fileName: String
        ): ParsedUtilityBillDraft =
            ElectricityAiParserService.parseUtilityBillJsonResponse(rawJson, fileName)

        internal fun parseReceiptJsonResponse(rawJson: String): ParsedReceiptResult =
            ReceiptParserService.parseReceiptJsonResponse(rawJson)

        fun parseFromFileName(fileName: String): ParsedReceiptResult =
            ReceiptParserService.parseFromFileName(fileName)
    }

    suspend fun parseReceipt(
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray?
    ): ParsedReceiptResult =
        receiptParserService.parseReceipt(fileName, mimeType, fileBytes)

    fun buildVehiclePrompt(
        year: Int,
        make: String,
        model: String,
        color: String,
        trim: String? = null,
        environment: String = "clean paved driveway"
    ): String =
        vehicleImageGeneratorService.buildVehiclePrompt(year, make, model, color, trim, environment)

    suspend fun generateVehicleImage(
        prompt: String,
        aspectRatio: String = "16:9"
    ): Result<ByteArray> =
        vehicleImageGeneratorService.generateVehicleImage(prompt, aspectRatio)

    suspend fun parseUtilityBill(
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray?
    ): ParsedUtilityBillDraft =
        electricityAiParserService.parseUtilityBill(fileName, mimeType, fileBytes)

    suspend fun parseChargerUsage(
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray?
    ): ParsedChargerImportResult =
        electricityAiParserService.parseChargerUsage(fileName, mimeType, fileBytes)
}
