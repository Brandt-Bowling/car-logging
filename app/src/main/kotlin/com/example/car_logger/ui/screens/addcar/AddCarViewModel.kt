package com.example.car_logger.ui.screens.addcar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.vehicle.VehicleData
import com.example.car_logger.services.AutoDevService
import com.example.car_logger.services.AutoDevVehicleInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class AddCarViewModel(
    private val autoDevService: AutoDevService
) : ViewModel() {

    private val _vinInput = MutableStateFlow("")
    val vinInput: StateFlow<String> = _vinInput.asStateFlow()

    private val _isLoadingVin = MutableStateFlow(false)
    val isLoadingVin: StateFlow<Boolean> = _isLoadingVin.asStateFlow()

    private val _vinError = MutableStateFlow<String?>(null)
    val vinError: StateFlow<String?> = _vinError.asStateFlow()

    private val _decodedInfo = MutableStateFlow<AutoDevVehicleInfo?>(null)
    val decodedInfo: StateFlow<AutoDevVehicleInfo?> = _decodedInfo.asStateFlow()

    private val _selectedMake = MutableStateFlow<String?>(null)
    val selectedMake: StateFlow<String?> = _selectedMake.asStateFlow()

    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()

    private val _selectedYear = MutableStateFlow(2020)
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    private val _selectedOdometer = MutableStateFlow<Int?>(null)
    val selectedOdometer: StateFlow<Int?> = _selectedOdometer.asStateFlow()

    private val _licensePlate = MutableStateFlow("")
    val licensePlate: StateFlow<String> = _licensePlate.asStateFlow()

    private val _isEv = MutableStateFlow(false)
    val isEv: StateFlow<Boolean> = _isEv.asStateFlow()

    private val _tireRotationInterval = MutableStateFlow<Int?>(7500)
    val tireRotationInterval: StateFlow<Int?> = _tireRotationInterval.asStateFlow()

    private val _selectedImageUrl = MutableStateFlow<String?>(null)
    val selectedImageUrl: StateFlow<String?> = _selectedImageUrl.asStateFlow()

    fun onVinChange(vin: String) {
        _vinInput.value = vin.uppercase()
        _vinError.value = null
    }

    fun decodeVin() {
        val vin = _vinInput.value.trim()
        if (vin.length < 11) {
            _vinError.value = "Enter a 17-character VIN"
            return
        }

        viewModelScope.launch {
            _isLoadingVin.value = true
            _vinError.value = null
            val result = autoDevService.decodeAndFetchDetails(vin)
            _isLoadingVin.value = false

            if (result.vinValid && result.errorMessage == null) {
                _decodedInfo.value = result
                _selectedMake.value = result.make
                _selectedModel.value = result.model
                result.year?.let { _selectedYear.value = it }
                _isEv.value = result.isEv
                _selectedImageUrl.value = result.photoUrl
            } else {
                _vinError.value = result.errorMessage ?: "Couldn't look up VIN"
            }
        }
    }

    fun onMakeSelected(make: String) {
        _selectedMake.value = make
        _selectedModel.value = null
        val models = VehicleData.getModelsForMake(make)
        if (models.isNotEmpty()) {
            _selectedModel.value = models.first()
            checkEvAndImage(make, models.first())
        }
    }

    fun onModelSelected(model: String) {
        _selectedModel.value = model
        val make = _selectedMake.value ?: return
        checkEvAndImage(make, model)
    }

    fun onImageUrlSelected(url: String?) {
        _selectedImageUrl.value = url
    }

    fun onYearChange(year: Int) {
        _selectedYear.value = year
    }

    fun onOdometerChange(odometer: Int?) {
        _selectedOdometer.value = odometer
    }

    fun onLicensePlateChange(plate: String) {
        _licensePlate.value = plate
    }

    fun onIsEvChange(ev: Boolean) {
        _isEv.value = ev
    }

    fun onTireRotationIntervalChange(interval: Int?) {
        _tireRotationInterval.value = interval
    }

    private fun checkEvAndImage(make: String, model: String) {
        _isEv.value = VehicleData.isKnownEv(make, model)
        val defaultImage = VehicleData.getDefaultImageUrl(make, model)
        if (defaultImage != null) {
            _selectedImageUrl.value = defaultImage
        }
    }

    fun buildCar(): Car? {
        val make = _selectedMake.value?.trim() ?: return null
        val model = _selectedModel.value?.trim() ?: return null
        val decoded = _decodedInfo.value

        return Car(
            id = UUID.randomUUID().toString(),
            make = make,
            model = model,
            year = _selectedYear.value,
            imageUrl = _selectedImageUrl.value,
            licensePlate = _licensePlate.value.trim().ifEmpty { null },
            vin = _vinInput.value.trim().ifEmpty { null },
            odometer = _selectedOdometer.value,
            isEv = _isEv.value,
            trim = decoded?.trim,
            bodyStyle = decoded?.body ?: decoded?.style,
            tireRotationInterval = if (_isEv.value) (_tireRotationInterval.value ?: 7500) else _tireRotationInterval.value
        )
    }
}
