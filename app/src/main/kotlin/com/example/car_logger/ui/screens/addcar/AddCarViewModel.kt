package com.example.car_logger.ui.screens.addcar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.car_logger.data.local.PreferencesManager
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.vehicle.VehicleData
import com.example.car_logger.services.AutoDevService
import com.example.car_logger.services.AutoDevVehicleInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class AddCarViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    private val autoDevService = AutoDevService(preferencesManager)

    val vinInput = MutableStateFlow("")
    val isLoadingVin = MutableStateFlow(false)
    val vinError = MutableStateFlow<String?>(null)
    val decodedInfo = MutableStateFlow<AutoDevVehicleInfo?>(null)

    val selectedMake = MutableStateFlow<String?>(null)
    val selectedModel = MutableStateFlow<String?>(null)
    val selectedYear = MutableStateFlow(2020)
    val selectedOdometer = MutableStateFlow<Int?>(null)
    val licensePlate = MutableStateFlow("")
    val isEv = MutableStateFlow(false)
    val tireRotationInterval = MutableStateFlow<Int?>(7500)
    val selectedImageUrl = MutableStateFlow<String?>(null)

    fun onVinChange(vin: String) {
        vinInput.value = vin.uppercase()
        vinError.value = null
    }

    fun decodeVin() {
        val vin = vinInput.value.trim()
        if (vin.length < 11) {
            vinError.value = "Please enter a valid 17-character VIN"
            return
        }

        viewModelScope.launch {
            isLoadingVin.value = true
            vinError.value = null
            val result = autoDevService.decodeAndFetchDetails(vin)
            isLoadingVin.value = false

            if (result.vinValid && result.errorMessage == null) {
                decodedInfo.value = result
                selectedMake.value = result.make
                selectedModel.value = result.model
                result.year?.let { selectedYear.value = it }
                isEv.value = result.isEv
                selectedImageUrl.value = result.photoUrl
            } else {
                vinError.value = result.errorMessage ?: "Failed to decode VIN"
            }
        }
    }

    fun onMakeSelected(make: String) {
        selectedMake.value = make
        selectedModel.value = null
        val models = VehicleData.getModelsForMake(make)
        if (models.isNotEmpty()) {
            selectedModel.value = models.first()
            checkEvAndImage(make, models.first())
        }
    }

    fun onModelSelected(model: String) {
        selectedModel.value = model
        val make = selectedMake.value ?: return
        checkEvAndImage(make, model)
    }

    private fun checkEvAndImage(make: String, model: String) {
        isEv.value = VehicleData.isKnownEv(make, model)
        val defaultImage = VehicleData.getDefaultImageUrl(make, model)
        if (defaultImage != null) {
            selectedImageUrl.value = defaultImage
        }
    }

    fun buildCar(): Car? {
        val make = selectedMake.value?.trim() ?: return null
        val model = selectedModel.value?.trim() ?: return null
        val decoded = decodedInfo.value

        return Car(
            id = UUID.randomUUID().toString(),
            make = make,
            model = model,
            year = selectedYear.value,
            imageUrl = selectedImageUrl.value,
            licensePlate = licensePlate.value.trim().ifEmpty { null },
            vin = vinInput.value.trim().ifEmpty { null },
            odometer = selectedOdometer.value,
            isEv = isEv.value,
            trim = decoded?.trim,
            bodyStyle = decoded?.body ?: decoded?.style,
            tireRotationInterval = if (isEv.value) (tireRotationInterval.value ?: 7500) else tireRotationInterval.value
        )
    }
}
