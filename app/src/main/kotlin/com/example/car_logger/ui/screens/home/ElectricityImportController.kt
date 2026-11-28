package com.example.car_logger.ui.screens.home

import android.net.Uri
import com.example.car_logger.data.insights.ChargingVenue
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.UriContentReader
import com.example.car_logger.data.local.dao.ChargingSessionDao
import com.example.car_logger.data.local.dao.UtilityBillDao
import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.GeminiAiService
import com.example.car_logger.services.ParsedChargerImportResult
import com.example.car_logger.services.ParsedUtilityBillDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

internal class ElectricityImportController(
    private val scope: CoroutineScope,
    private val utilityBillDao: UtilityBillDao,
    private val chargingSessionDao: ChargingSessionDao,
    private val preferencesManager: PreferencesManager,
    private val geminiAiService: GeminiAiService,
    private val uriContentReader: UriContentReader,
    private val emitToast: suspend (String) -> Unit
) {
    private val _insightsChargingVenue = MutableStateFlow(ChargingVenue.HOME)
    val insightsChargingVenue: StateFlow<ChargingVenue> = _insightsChargingVenue.asStateFlow()

    private val _isParsingElectricityFile = MutableStateFlow(false)
    val isParsingElectricityFile: StateFlow<Boolean> = _isParsingElectricityFile.asStateFlow()

    private val _pendingUtilityBillDraft = MutableStateFlow<ParsedUtilityBillDraft?>(null)
    val pendingUtilityBillDraft: StateFlow<ParsedUtilityBillDraft?> = _pendingUtilityBillDraft.asStateFlow()

    private val _showUtilityBillSheet = MutableStateFlow(false)
    val showUtilityBillSheet: StateFlow<Boolean> = _showUtilityBillSheet.asStateFlow()

    private val _pendingChargerImport = MutableStateFlow<ParsedChargerImportResult?>(null)
    val pendingChargerImport: StateFlow<ParsedChargerImportResult?> = _pendingChargerImport.asStateFlow()

    private val _showChargerUsageSheet = MutableStateFlow(false)
    val showChargerUsageSheet: StateFlow<Boolean> = _showChargerUsageSheet.asStateFlow()

    fun setInsightsChargingVenue(venue: ChargingVenue) {
        _insightsChargingVenue.value = venue
    }

    fun openManualUtilityBillSheet() {
        _pendingUtilityBillDraft.value = null
        _showUtilityBillSheet.value = true
    }

    fun dismissUtilityBillSheet() {
        _showUtilityBillSheet.value = false
        _pendingUtilityBillDraft.value = null
    }

    fun openManualChargerUsageSheet() {
        _pendingChargerImport.value = null
        _showChargerUsageSheet.value = true
    }

    fun dismissChargerUsageSheet() {
        _showChargerUsageSheet.value = false
        _pendingChargerImport.value = null
    }

    fun parseUtilityBillUri(uri: Uri) {
        scope.launch {
            _isParsingElectricityFile.value = true
            val result = withContext(Dispatchers.IO) { readAndParseUtilityBill(uri) }
            _isParsingElectricityFile.value = false
            _pendingUtilityBillDraft.value = result
            _showUtilityBillSheet.value = true
        }
    }

    private suspend fun readAndParseUtilityBill(uri: Uri): ParsedUtilityBillDraft {
        val fileName = uriContentReader.resolveFileName(uri) ?: "utility_bill"
        val mimeType = uriContentReader.resolveMimeType(uri, fileName)
        val bytes = uriContentReader.readBytes(uri)
            ?: return ParsedUtilityBillDraft(
                providerName = "",
                startDateIso = "",
                endDateIso = "",
                totalKwh = 0.0,
                totalAmount = 0.0,
                fixedCharges = 0.0,
                source = "manual",
                fileName = fileName,
                error = "Couldn't open $fileName. Enter the bill details manually."
            )

        return geminiAiService.parseUtilityBill(fileName = fileName, mimeType = mimeType, fileBytes = bytes)
    }

    fun parseChargerUsageUri(uri: Uri) {
        scope.launch {
            _isParsingElectricityFile.value = true
            val result = withContext(Dispatchers.IO) { readAndParseChargerUsage(uri) }
            _isParsingElectricityFile.value = false
            _pendingChargerImport.value = result
            _showChargerUsageSheet.value = true
        }
    }

    private suspend fun readAndParseChargerUsage(uri: Uri): ParsedChargerImportResult {
        val fileName = uriContentReader.resolveFileName(uri) ?: "charger_export"
        val mimeType = uriContentReader.resolveMimeType(uri, fileName)
        val bytes = uriContentReader.readBytes(uri)
            ?: return ParsedChargerImportResult(
                sessions = emptyList(),
                fileName = fileName,
                source = "manual",
                error = "Couldn't open $fileName. Enter sessions or monthly totals manually."
            )

        val isCsv = fileName.endsWith(".csv", ignoreCase = true) ||
            mimeType.contains("csv", ignoreCase = true) ||
            mimeType == "text/plain" ||
            mimeType == "text/comma-separated-values"

        if (isCsv) {
            val csvText = String(bytes, Charsets.UTF_8)
            val parsed = ElectricityImportParser.parseChargerCsv(fileName, csvText)
            return if (parsed.isNotEmpty()) {
                ParsedChargerImportResult(sessions = parsed, fileName = fileName, source = "csv")
            } else {
                ParsedChargerImportResult(
                    sessions = emptyList(),
                    fileName = fileName,
                    source = "csv",
                    error = "Couldn't detect date and kWh columns in $fileName. Try a screenshot or PDF, or enter it manually."
                )
            }
        }

        return geminiAiService.parseChargerUsage(fileName = fileName, mimeType = mimeType, fileBytes = bytes)
    }

    fun saveUtilityBill(
        providerName: String,
        startDateMs: Long,
        endDateMs: Long,
        totalKwh: Double,
        totalAmount: Double,
        fixedCharges: Double,
        overrideRate: Double?,
        replaceExistingId: String?
    ) {
        scope.launch {
            val calculatedRate = ElectricityImportParser.calculateVariableBlendedRate(
                totalAmount = totalAmount,
                fixedCharges = fixedCharges,
                totalKwh = totalKwh
            )
            val effectiveRate = overrideRate ?: calculatedRate
            withContext(Dispatchers.IO) {
                if (replaceExistingId != null) {
                    utilityBillDao.deleteBillById(replaceExistingId)
                }
                utilityBillDao.insertBill(
                    UtilityBillEntity(
                        id = "bill_${UUID.randomUUID()}",
                        providerName = providerName,
                        startDateMs = startDateMs,
                        endDateMs = endDateMs,
                        totalKwh = totalKwh,
                        totalAmount = totalAmount,
                        fixedCharges = fixedCharges,
                        calculatedRate = calculatedRate,
                        overrideRate = overrideRate,
                        effectiveRate = effectiveRate,
                        uploadedAtMs = System.currentTimeMillis()
                    )
                )
            }
            preferencesManager.setDefaultElectricityRate(effectiveRate.toFloat())
            preferencesManager.setUseCustomElectricityRate(false)
            dismissUtilityBillSheet()
            emitToast("Saved electric bill ($${String.format(Locale.US, "%.3f", effectiveRate)}/kWh)")
        }
    }

    fun saveChargingSessions(sessions: List<ChargingSessionEntity>, skippedFromUi: Int) {
        scope.launch {
            var insertedCount = 0
            var totalSkipped = skippedFromUi
            val batchId = "batch_${UUID.randomUUID()}"
            val uploadedAtMs = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                val existingFingerprints = chargingSessionDao.getAllFingerprints().toMutableSet()
                val uniqueNew = mutableListOf<ChargingSessionEntity>()
                for (session in sessions) {
                    if (existingFingerprints.add(session.fingerprint)) {
                        uniqueNew.add(session.copy(uploadBatchId = batchId, uploadedAtMs = uploadedAtMs))
                    } else {
                        totalSkipped++
                    }
                }
                if (uniqueNew.isNotEmpty()) {
                    chargingSessionDao.insertSessions(uniqueNew)
                }
                insertedCount = uniqueNew.size
            }
            dismissChargerUsageSheet()
            val msg = when {
                insertedCount > 0 && totalSkipped > 0 ->
                    "Added $insertedCount charging ${if (insertedCount == 1) "session" else "sessions"} ($totalSkipped ${if (totalSkipped == 1) "duplicate" else "duplicates"} skipped)"
                insertedCount > 0 ->
                    "Added $insertedCount charging ${if (insertedCount == 1) "session" else "sessions"}"
                totalSkipped > 0 ->
                    "All $totalSkipped sessions were already saved"
                else -> "No charging sessions saved"
            }
            emitToast(msg)
        }
    }

    fun undoLastChargingUpload(reopenForRetag: Boolean = false) {
        scope.launch {
            val lastBatch = withContext(Dispatchers.IO) {
                val all = chargingSessionDao.getAllSessionsList()
                val batch = ElectricityImportParser.resolveLastUploadBatch(all)
                if (batch.isNotEmpty()) {
                    batch.map { it.id }.chunked(500).forEach { chunk ->
                        chargingSessionDao.deleteSessionsByIds(chunk)
                    }
                }
                batch
            }
            if (lastBatch.isEmpty()) {
                emitToast("No charging import to undo")
                return@launch
            }
            if (reopenForRetag) {
                _pendingChargerImport.value = ElectricityImportParser.sessionsToImportResult(
                    sessions = lastBatch,
                    fileName = "last import"
                )
                _showChargerUsageSheet.value = true
                emitToast(
                    "Removed ${lastBatch.size} ${if (lastBatch.size == 1) "session" else "sessions"} — choose a vehicle to save again"
                )
            } else {
                emitToast("Removed ${lastBatch.size} charging ${if (lastBatch.size == 1) "session" else "sessions"}")
            }
        }
    }

    fun deleteUtilityBill(billId: String) {
        scope.launch {
            withContext(Dispatchers.IO) { utilityBillDao.deleteBillById(billId) }
            emitToast("Deleted electric bill")
        }
    }

    fun deleteChargingSession(sessionId: String) {
        scope.launch {
            withContext(Dispatchers.IO) { chargingSessionDao.deleteSessionById(sessionId) }
            emitToast("Deleted charging session")
        }
    }

    fun updateDefaultElectricityRate(rate: Float) {
        scope.launch {
            preferencesManager.setCustomElectricityRate(rate)
            emitToast("Home rate set to $${String.format(Locale.US, "%.3f", rate)}/kWh")
        }
    }

    fun useSavedBillRate() {
        scope.launch {
            preferencesManager.setUseCustomElectricityRate(false)
            emitToast("Using rates from saved bills")
        }
    }

    fun updateChargingSessionDirectCost(sessionId: String, newCost: Double) {
        scope.launch {
            val sanitizedCost = newCost.coerceAtLeast(0.0)
            val updated = withContext(Dispatchers.IO) {
                val existing = chargingSessionDao.getAllSessionsList().firstOrNull { it.id == sessionId }
                if (existing != null) {
                    chargingSessionDao.insertSession(existing.copy(directCost = sanitizedCost))
                    true
                } else {
                    false
                }
            }
            if (updated) {
                emitToast("Updated session cost to $${String.format(Locale.US, "%.2f", sanitizedCost)}")
            }
        }
    }
}
