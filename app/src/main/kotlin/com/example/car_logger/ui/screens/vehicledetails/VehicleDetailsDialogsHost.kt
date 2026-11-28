package com.example.car_logger.ui.screens.vehicledetails

import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.car_logger.data.model.Car
import com.example.car_logger.data.model.Tire
import com.example.car_logger.ui.screens.dialogs.TireRotationIntervalDialog
import com.example.car_logger.ui.screens.dialogs.VehiclePhotoCustomizerSheet
import com.example.car_logger.ui.screens.vehicledetails.dialogs.EditLicensePlateDialog
import com.example.car_logger.ui.screens.vehicledetails.dialogs.EditVehicleDetailsDialog
import com.example.car_logger.ui.screens.vehicledetails.dialogs.EditVinDialog
import com.example.car_logger.ui.screens.vehicledetails.dialogs.LogTireRotationDialog
import com.example.car_logger.ui.screens.vehicledetails.dialogs.NewTireSetDialog
import com.example.car_logger.ui.screens.vehicledetails.dialogs.WakeOdometerConfirmDialog

internal data class VehicleDialogFlags(
    val folderPicker: Boolean = false,
    val photoCustomizer: Boolean = false,
    val interval: Boolean = false,
    val rotation: Boolean = false,
    val newTire: Boolean = false,
    val editVehicleName: Boolean = false,
    val editVin: Boolean = false,
    val editLicensePlate: Boolean = false,
    val wakeConfirm: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VehicleDetailsDialogsHost(
    carItem: Car,
    dialogState: VehicleDialogFlags,
    photoSheetState: SheetState,
    onUpdateDialogState: (VehicleDialogFlags) -> Unit,
    onConfirmWakeSync: () -> Unit,
    onGenerateAiImage: suspend (color: String, trim: String?, environment: String?) -> Result<ByteArray>,
    onSaveCustomImage: (ByteArray) -> Unit,
    onPickImageUri: (Uri) -> Unit,
    onResetToStock: () -> Unit,
    onUpdateCar: (Car) -> Unit,
    onLogRotation: (String, Int) -> Unit,
    onInstallNewTire: (Tire) -> Unit
) {
    val context = LocalContext.current
    val currentOdo = carItem.odometer ?: 0

    if (dialogState.wakeConfirm) {
        WakeOdometerConfirmDialog(
            carDisplayName = carItem.displayName,
            onConfirm = {
                onUpdateDialogState(dialogState.copy(wakeConfirm = false))
                onConfirmWakeSync()
            },
            onDismiss = { onUpdateDialogState(dialogState.copy(wakeConfirm = false)) }
        )
    }
    if (dialogState.photoCustomizer) {
        VehiclePhotoCustomizerSheet(
            car = carItem,
            sheetState = photoSheetState,
            onGenerateAiImage = onGenerateAiImage,
            onSaveImageBytes = onSaveCustomImage,
            onPickImageUri = onPickImageUri,
            onResetToStock = onResetToStock,
            onDismissRequest = { onUpdateDialogState(dialogState.copy(photoCustomizer = false)) }
        )
    }
    if (dialogState.interval) {
        TireRotationIntervalDialog(
            currentInterval = carItem.effectiveTireRotationInterval,
            onDismiss = { onUpdateDialogState(dialogState.copy(interval = false)) },
            onConfirm = { newInterval ->
                onUpdateCar(carItem.copy(tireRotationInterval = newInterval))
                onUpdateDialogState(dialogState.copy(interval = false))
                Toast.makeText(context, "Updated interval to $newInterval mi", Toast.LENGTH_SHORT).show()
            }
        )
    }
    if (dialogState.rotation) {
        LogTireRotationDialog(
            currentOdometer = currentOdo,
            onSave = { rotDate, odoInt ->
                onLogRotation(rotDate, odoInt)
                onUpdateDialogState(dialogState.copy(rotation = false))
                Toast.makeText(context, "Logged tire rotation at $odoInt mi", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { onUpdateDialogState(dialogState.copy(rotation = false)) }
        )
    }
    if (dialogState.newTire) {
        NewTireSetDialog(
            carId = carItem.id,
            currentOdometer = currentOdo,
            onSave = { newTire ->
                onInstallNewTire(newTire)
                onUpdateDialogState(dialogState.copy(newTire = false))
                Toast.makeText(context, "Saved ${newTire.manufacturer} ${newTire.model}", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { onUpdateDialogState(dialogState.copy(newTire = false)) }
        )
    }
    if (dialogState.editVehicleName) {
        EditVehicleDetailsDialog(
            car = carItem,
            onSave = { updated ->
                onUpdateCar(updated)
                onUpdateDialogState(dialogState.copy(editVehicleName = false))
                Toast.makeText(context, "Updated vehicle details", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { onUpdateDialogState(dialogState.copy(editVehicleName = false)) }
        )
    }
    if (dialogState.editVin) {
        EditVinDialog(
            currentVin = carItem.vin,
            onSave = { cleanVin ->
                onUpdateCar(carItem.copy(vin = cleanVin))
                onUpdateDialogState(dialogState.copy(editVin = false))
                Toast.makeText(context, "Saved VIN", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { onUpdateDialogState(dialogState.copy(editVin = false)) }
        )
    }
    if (dialogState.editLicensePlate) {
        EditLicensePlateDialog(
            currentPlate = carItem.licensePlate,
            onSave = { cleanPlate ->
                onUpdateCar(carItem.copy(licensePlate = cleanPlate))
                onUpdateDialogState(dialogState.copy(editLicensePlate = false))
                Toast.makeText(context, "Saved license plate", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { onUpdateDialogState(dialogState.copy(editLicensePlate = false)) }
        )
    }
}
