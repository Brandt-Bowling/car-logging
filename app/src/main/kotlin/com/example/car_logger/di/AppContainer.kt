package com.example.car_logger.di

import android.content.Context
import com.example.car_logger.data.local.AndroidUriContentReader
import com.example.car_logger.data.local.AppDatabase
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.local.UriContentReader
import com.example.car_logger.data.vehicle.VehicleImageRepository
import com.example.car_logger.services.AndroidSyncSchedulerController
import com.example.car_logger.services.AutoDevService
import com.example.car_logger.services.GeminiAiService
import com.example.car_logger.services.GoogleDriveService
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.SyncSchedulerController
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext ?: context

    val httpClient: OkHttpClient by lazy { defaultHttpClient }

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val preferencesManager: PreferencesManager by lazy { PreferencesManager(appContext) }

    val uriContentReader: UriContentReader by lazy { AndroidUriContentReader(appContext) }

    val syncSchedulerController: SyncSchedulerController by lazy {
        AndroidSyncSchedulerController(appContext)
    }

    val googleDriveService: GoogleDriveService by lazy { GoogleDriveService(appContext) }

    val vehicleImageRepository: VehicleImageRepository by lazy {
        VehicleImageRepository(
            context = appContext,
            carDao = database.carDao(),
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
            carDao = database.carDao(),
            odometerLogDao = database.odometerLogDao(),
            chargingSessionDao = database.chargingSessionDao(),
            client = httpClient
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
    }
}
