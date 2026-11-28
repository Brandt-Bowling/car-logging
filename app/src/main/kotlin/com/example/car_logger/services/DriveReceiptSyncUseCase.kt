package com.example.car_logger.services

import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.dao.MaintenanceRecordDao
import com.example.car_logger.data.local.dao.OdometerLogDao
import com.example.car_logger.data.local.entity.MaintenanceRecordEntity
import com.example.car_logger.data.local.entity.OdometerLogEntity
import com.example.car_logger.data.model.MaintenanceRecord
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class DriveReceiptSyncUseCase(
    private val googleDriveService: GoogleDriveService,
    private val preferencesManager: PreferencesManager,
    private val geminiAiService: GeminiAiService,
    private val recordDao: MaintenanceRecordDao,
    private val carDao: CarDao,
    private val odometerLogDao: OdometerLogDao
) {
    suspend fun syncFolder(
        carId: String,
        folderId: String,
        fallbackOdometer: Int?,
        existingRecords: List<MaintenanceRecord>
    ) {
        val files = googleDriveService.listFiles(folderId)
        val importedIds = preferencesManager.getImportedFileIds()
        val existingDriveFileIds = existingRecords.mapNotNull { it.driveFileId }.toSet()

        for (file in files) {
            if (file.id in importedIds || file.id in existingDriveFileIds) continue
            val bytes = googleDriveService.downloadFileBytes(file.id)
            val parsed = geminiAiService.parseReceipt(
                fileName = file.name,
                mimeType = file.mimeType,
                fileBytes = bytes
            )
            val resolvedOdo = if (parsed.odometer > 0) {
                parsed.odometer
            } else {
                fallbackOdometer ?: 0
            }
            val newRecord = MaintenanceRecordEntity(
                id = UUID.randomUUID().toString(),
                carId = carId,
                title = parsed.title,
                date = parsed.date,
                odometer = resolvedOdo,
                description = parsed.description,
                receiptUrl = file.webViewLink,
                cost = parsed.cost,
                driveFileId = file.id
            )
            recordDao.insertRecord(newRecord)
            preferencesManager.markFileAsImported(file.id)

            val currentCarOdo = carDao.getCarById(carId)?.odometer
            if (resolvedOdo > 0 && (currentCarOdo == null || resolvedOdo > currentCarOdo)) {
                carDao.updateOdometer(carId, resolvedOdo)
            }
            if (resolvedOdo > 0) {
                val ts = try {
                    LocalDate.parse(parsed.date.take(10))
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }
                odometerLogDao.insertLog(
                    OdometerLogEntity(
                        id = "rec_${newRecord.id}",
                        carId = carId,
                        odometer = resolvedOdo,
                        timestamp = ts,
                        source = "MAINTENANCE"
                    )
                )
            }
        }
    }
}
