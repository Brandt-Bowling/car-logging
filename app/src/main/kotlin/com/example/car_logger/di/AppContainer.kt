package com.example.car_logger.di

import android.content.Context
import com.example.car_logger.data.local.AndroidUriContentReader
import com.example.car_logger.data.local.AppDatabase
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.UriContentReader
import com.example.car_logger.data.local.dao.CarDao
import com.example.car_logger.data.local.dao.ChargingSessionDao
import com.example.car_logger.data.local.dao.GloveboxDao
import com.example.car_logger.data.local.dao.MaintenanceRecordDao
import com.example.car_logger.data.local.dao.OdometerLogDao
import com.example.car_logger.data.local.dao.TireDao
import com.example.car_logger.data.local.dao.UtilityBillDao
import com.example.car_logger.data.vehicle.VehicleImageRepository
import com.example.car_logger.services.AndroidSyncSchedulerController
import com.example.car_logger.services.AutoDevService
import com.example.car_logger.services.DriveReceiptSyncUseCase
import com.example.car_logger.services.GeminiAiService
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.SyncSchedulerController
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer private constructor(
    context: Context?,
    preferencesManagerOverride: PreferencesManager? = null,
    uriContentReaderOverride: UriContentReader? = null,
    syncSchedulerControllerOverride: SyncSchedulerController? = null,
    googleDriveServiceOverride: GoogleDriveService? = null,
    carDaoOverride: CarDao? = null,
    maintenanceRecordDaoOverride: MaintenanceRecordDao? = null,
    tireDaoOverride: TireDao? = null,
    gloveboxDaoOverride: GloveboxDao? = null,
    odometerLogDaoOverride: OdometerLogDao? = null,
    utilityBillDaoOverride: UtilityBillDao? = null,
    chargingSessionDaoOverride: ChargingSessionDao? = null,
    vehicleImageRepositoryOverride: VehicleImageRepository? = null,
    driveReceiptSyncUseCaseOverride: DriveReceiptSyncUseCase? = null
) {
    constructor(context: Context) : this(context = context, preferencesManagerOverride = null)

    private val appContext: Context? = context?.let { it.applicationContext ?: it }

    private fun requireAppContext(): Context =
        checkNotNull(appContext) { "AppContainer requires an Android Context for this dependency" }

    val httpClient: OkHttpClient by lazy { defaultHttpClient }

    val database: AppDatabase by lazy { AppDatabase.getInstance(requireAppContext()) }

    val carDao: CarDao by lazy { carDaoOverride ?: database.carDao() }

    val maintenanceRecordDao: MaintenanceRecordDao by lazy {
        maintenanceRecordDaoOverride ?: database.maintenanceRecordDao()
    }

    val tireDao: TireDao by lazy { tireDaoOverride ?: database.tireDao() }

    val gloveboxDao: GloveboxDao by lazy { gloveboxDaoOverride ?: database.gloveboxDao() }

    val odometerLogDao: OdometerLogDao by lazy {
        odometerLogDaoOverride ?: database.odometerLogDao()
    }

    val utilityBillDao: UtilityBillDao by lazy {
        utilityBillDaoOverride ?: database.utilityBillDao()
    }

    val chargingSessionDao: ChargingSessionDao by lazy {
        chargingSessionDaoOverride ?: database.chargingSessionDao()
    }

    val preferencesManager: PreferencesManager by lazy {
        preferencesManagerOverride ?: PreferencesManager(requireAppContext())
    }

    val uriContentReader: UriContentReader by lazy {
        uriContentReaderOverride ?: AndroidUriContentReader(requireAppContext())
    }

    val syncSchedulerController: SyncSchedulerController by lazy {
        syncSchedulerControllerOverride ?: AndroidSyncSchedulerController(requireAppContext())
    }

    val googleDriveService: GoogleDriveService by lazy {
        googleDriveServiceOverride ?: GoogleDriveService(requireAppContext())
    }

    val vehicleImageRepository: VehicleImageRepository by lazy {
        vehicleImageRepositoryOverride ?: VehicleImageRepository(
            context = requireAppContext(),
            carDao = carDao,
            uriContentReader = uriContentReader
        )
    }

    val autoDevService: AutoDevService by lazy {
        AutoDevService(
            preferencesManager = preferencesManager,
            client = httpClient.newBuilder()
                .connectTimeout(AUTO_DEV_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(AUTO_DEV_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        )
    }

    val geminiAiService: GeminiAiService by lazy {
        GeminiAiService(
            preferencesManager = preferencesManager,
            client = httpClient.newBuilder()
                .connectTimeout(GEMINI_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        )
    }

    val odometerSyncService: OdometerSyncService by lazy {
        OdometerSyncService(
            preferencesManager = preferencesManager,
            carDao = carDao,
            odometerLogDao = odometerLogDao,
            chargingSessionDao = chargingSessionDao,
            client = httpClient
        )
    }

    val driveReceiptSyncUseCase: DriveReceiptSyncUseCase by lazy {
        driveReceiptSyncUseCaseOverride ?: DriveReceiptSyncUseCase(
            googleDriveService = googleDriveService,
            preferencesManager = preferencesManager,
            geminiAiService = geminiAiService,
            recordDao = maintenanceRecordDao,
            carDao = carDao,
            odometerLogDao = odometerLogDao
        )
    }

    companion object {
        const val CONNECT_TIMEOUT_SECONDS = 20L
        const val READ_TIMEOUT_SECONDS = 60L
        const val WRITE_TIMEOUT_SECONDS = 60L
        const val AUTO_DEV_TIMEOUT_SECONDS = 15L
        const val GEMINI_CONNECT_TIMEOUT_SECONDS = 30L

        val defaultHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }

        fun newSharedHttpClient(
            connectTimeoutSeconds: Long = CONNECT_TIMEOUT_SECONDS,
            readTimeoutSeconds: Long = READ_TIMEOUT_SECONDS,
            writeTimeoutSeconds: Long = WRITE_TIMEOUT_SECONDS
        ): OkHttpClient = defaultHttpClient.newBuilder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
            .build()

        internal fun forTest(
            preferencesManager: PreferencesManager,
            carDao: CarDao,
            maintenanceRecordDao: MaintenanceRecordDao,
            tireDao: TireDao,
            gloveboxDao: GloveboxDao,
            odometerLogDao: OdometerLogDao,
            utilityBillDao: UtilityBillDao,
            chargingSessionDao: ChargingSessionDao,
            uriContentReader: UriContentReader,
            syncSchedulerController: SyncSchedulerController,
            googleDriveService: GoogleDriveService,
            vehicleImageRepository: VehicleImageRepository
        ): AppContainer = AppContainer(
            context = null,
            preferencesManagerOverride = preferencesManager,
            uriContentReaderOverride = uriContentReader,
            syncSchedulerControllerOverride = syncSchedulerController,
            googleDriveServiceOverride = googleDriveService,
            carDaoOverride = carDao,
            maintenanceRecordDaoOverride = maintenanceRecordDao,
            tireDaoOverride = tireDao,
            gloveboxDaoOverride = gloveboxDao,
            odometerLogDaoOverride = odometerLogDao,
            utilityBillDaoOverride = utilityBillDao,
            chargingSessionDaoOverride = chargingSessionDao,
            vehicleImageRepositoryOverride = vehicleImageRepository
        )
    }
}
