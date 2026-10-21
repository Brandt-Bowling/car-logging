package com.example.car_logger.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Glovebox(
    val carId: String,
    val registrationUrl: String? = null,
    val insuranceUrl: String? = null,
    val manualUrl: String? = null
)
