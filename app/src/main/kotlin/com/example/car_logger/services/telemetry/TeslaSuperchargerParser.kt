package com.example.car_logger.services.telemetry

import com.example.car_logger.data.local.entity.ChargingSessionEntity
import com.example.car_logger.data.model.Car
import com.example.car_logger.services.ElectricityImportParser
import com.example.car_logger.services.OdometerSyncService
import com.example.car_logger.services.TeslaSuperchargerSession
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

internal object TeslaSuperchargerParser {

    fun buildSuperchargerDateWindows(
        endInstant: Instant,
        lookbackDays: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
        maxWindowDays: Int = OdometerSyncService.TESLA_CHARGING_HISTORY_MAX_WINDOW_DAYS
    ): List<Pair<Instant, Instant>> {
        val safeMaxDays = maxWindowDays.coerceAtLeast(1)
        val clampedLookbackDays = lookbackDays.coerceIn(1, 730)
        val endTruncated = endInstant.truncatedTo(ChronoUnit.SECONDS)
        val maxWindowSeconds = safeMaxDays.toLong() * 86_400L

        if (clampedLookbackDays <= safeMaxDays) {
            val startCandidate = endTruncated.atZone(zoneId)
                .minusDays(clampedLookbackDays.toLong())
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant()
            val earliestSingleWindowStart = endTruncated.minusSeconds(maxWindowSeconds)
            val windowStart = if (startCandidate.isBefore(earliestSingleWindowStart)) {
                earliestSingleWindowStart
            } else {
                startCandidate
            }
            return listOf(windowStart to endTruncated)
        }

        val overallStart = endTruncated.minusSeconds(clampedLookbackDays.toLong() * 86_400L)
        return buildSuperchargerDateWindowsForRange(
            startInstant = overallStart,
            endInstant = endTruncated,
            nowInstant = endTruncated,
            maxWindowDays = safeMaxDays
        )
    }

    fun buildSuperchargerDateWindowsForRange(
        startInstant: Instant,
        endInstant: Instant,
        nowInstant: Instant = Instant.now(),
        maxWindowDays: Int = OdometerSyncService.TESLA_CHARGING_HISTORY_MAX_WINDOW_DAYS
    ): List<Pair<Instant, Instant>> {
        val safeMaxDays = maxWindowDays.coerceAtLeast(1)
        val maxWindowSeconds = safeMaxDays.toLong() * 86_400L
        val nowTruncated = nowInstant.truncatedTo(ChronoUnit.SECONDS)
        val requestedEnd = endInstant.truncatedTo(ChronoUnit.SECONDS)
        val clampedEnd = if (requestedEnd.isAfter(nowTruncated)) nowTruncated else requestedEnd
        val requestedStart = startInstant.truncatedTo(ChronoUnit.SECONDS)
        val clampedStart = if (!requestedStart.isBefore(clampedEnd)) {
            clampedEnd.minusSeconds(3_600L)
        } else {
            requestedStart
        }

        val windows = mutableListOf<Pair<Instant, Instant>>()
        var cursorEnd = clampedEnd
        while (cursorEnd.isAfter(clampedStart)) {
            val candidateStart = cursorEnd.minusSeconds(maxWindowSeconds)
            val windowStart = if (candidateStart.isBefore(clampedStart)) clampedStart else candidateStart
            windows.add(windowStart to cursorEnd)
            cursorEnd = windowStart
        }
        return windows
    }

    fun resolveIncrementalSuperchargerWindows(
        existingSessions: List<ChargingSessionEntity>,
        nowInstant: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        maxWindowDays: Int = OdometerSyncService.TESLA_CHARGING_HISTORY_MAX_WINDOW_DAYS,
        overlapDays: Int = OdometerSyncService.SUPERCHARGER_INCREMENTAL_OVERLAP_DAYS
    ): List<Pair<Instant, Instant>> {
        val safeMaxDays = maxWindowDays.coerceAtLeast(1)
        val maxWindowSeconds = safeMaxDays.toLong() * 86_400L
        val nowTruncated = nowInstant.truncatedTo(ChronoUnit.SECONDS)
        val latestSyncedMs = existingSessions
            .asSequence()
            .filter { it.uploadBatchId == ElectricityImportParser.AUTO_SUPERCHARGER_BATCH_ID }
            .maxOfOrNull { maxOf(it.startDateMs, it.endDateMs) }

        if (latestSyncedMs == null || latestSyncedMs <= 0L) {
            return buildSuperchargerDateWindows(
                endInstant = nowTruncated,
                lookbackDays = OdometerSyncService.DEFAULT_SUPERCHARGER_INITIAL_LOOKBACK_DAYS,
                zoneId = zoneId,
                maxWindowDays = safeMaxDays
            )
        }

        val bufferSeconds = overlapDays.coerceAtLeast(0).toLong() * 86_400L
        val latestInstant = Instant.ofEpochMilli(latestSyncedMs).truncatedTo(ChronoUnit.SECONDS)
        val earliestAllowedStart = nowTruncated.minusSeconds(maxWindowSeconds)
        val rawStart = latestInstant.minusSeconds(bufferSeconds)
        val clampedStart = when {
            rawStart.isBefore(earliestAllowedStart) -> earliestAllowedStart
            !rawStart.isBefore(nowTruncated) -> nowTruncated.minusSeconds(maxOf(bufferSeconds, 3_600L))
            else -> rawStart
        }
        return buildSuperchargerDateWindowsForRange(
            startInstant = clampedStart,
            endInstant = nowTruncated,
            nowInstant = nowTruncated,
            maxWindowDays = safeMaxDays
        )
    }

    fun parseTeslaSuperchargerHistory(rawJson: String): Pair<List<TeslaSuperchargerSession>, Boolean> {
        return try {
            val rootElem = telemetryJson.parseToJsonElement(rawJson)
            val root = rootElem as? JsonObject
            val responseElem = root?.get("response")
            val containerObj = (responseElem as? JsonObject) ?: root
            val dataArr = (rootElem as? JsonArray)
                ?: (responseElem as? JsonArray)
                ?: (containerObj?.get("data") as? JsonArray)
                ?: return Pair(emptyList(), false)

            val explicitHasMore = containerObj?.get("hasMore").asBooleanOrNull()
                ?: containerObj?.get("has_more").asBooleanOrNull()
                ?: false

            val sessions = mutableListOf<TeslaSuperchargerSession>()
            for (elem in dataArr) {
                val obj = elem as? JsonObject ?: continue
                val sessionId = obj["sessionId"].asStringOrNull()
                    ?: obj["chargeSessionId"].asStringOrNull()
                    ?: continue
                val startMs = TelemetryJsonUtils.parseFlexibleTimestampMs(obj["chargeStartDateTime"]) ?: continue
                val rawEndMs = TelemetryJsonUtils.parseFlexibleTimestampMs(obj["chargeStopDateTime"])
                    ?: TelemetryJsonUtils.parseFlexibleTimestampMs(obj["unlatchDateTime"])
                val endMs = if (rawEndMs != null && rawEndMs >= startMs) rawEndMs else startMs

                val feesArr = (obj["fees"] as? JsonArray).orEmpty()
                var totalKwh = 0.0
                var totalCost = 0.0
                var currency: String? = null

                for (feeElem in feesArr) {
                    val feeObj = feeElem as? JsonObject ?: continue
                    currency = currency ?: feeObj["currencyCode"].asStringOrNull()
                    val feeType = feeObj["feeType"].asStringOrNull()?.uppercase(Locale.US).orEmpty()
                    val uom = feeObj["uom"].asStringOrNull()?.lowercase(Locale.US).orEmpty()

                    val netDue = feeObj["netDue"].asDoubleOrNull()
                    val totalDue = feeObj["totalDue"].asDoubleOrNull()
                    val tierTotalsSum = listOf(
                        "totalBase", "totalTier1", "totalTier2", "totalTier3", "totalTier4"
                    ).sumOf { feeObj[it].asDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0 }
                    val feeCost = (netDue ?: totalDue ?: tierTotalsSum).coerceAtLeast(0.0)
                    totalCost += feeCost

                    val isTimeUom = uom == "min" || uom.startsWith("min") ||
                        uom.startsWith("sec") || uom.startsWith("hour")
                    if ((feeType == "CHARGING" || uom == "kwh") && !isTimeUom) {
                        val usageBase = feeObj["usageBase"].asDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        var feeKwh = usageBase
                        for (tier in 1..4) {
                            val usageT = feeObj["usageTier$tier"].asDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                            val rateT = feeObj["rateTier$tier"].asDoubleOrNull() ?: 0.0
                            val totalT = feeObj["totalTier$tier"].asDoubleOrNull() ?: 0.0
                            // Only count usageTierN when actually billed to avoid counting unbilled minute tiers
                            if (usageT > 0.0 && (rateT > 0.0 || totalT > 0.0)) {
                                feeKwh += usageT
                            }
                        }
                        if (feeKwh <= 0.0 && uom == "kwh") {
                            feeKwh = (1..4).sumOf {
                                feeObj["usageTier$it"].asDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                            }
                        }
                        totalKwh += feeKwh
                    }
                }

                val roundedKwh = round(totalKwh * 1000.0) / 1000.0
                if (roundedKwh < 0.1) continue
                val roundedCost = round(totalCost * 100.0) / 100.0

                sessions.add(
                    TeslaSuperchargerSession(
                        sessionId = sessionId,
                        vin = obj["vin"].asStringOrNull()?.uppercase(Locale.US),
                        vehicleMakeType = obj["vehicleMakeType"].asStringOrNull()?.uppercase(Locale.US),
                        siteLocationName = obj["siteLocationName"].asStringOrNull(),
                        startDateMs = startMs,
                        endDateMs = endMs,
                        energyKwh = roundedKwh,
                        totalPaidCost = roundedCost,
                        currencyCode = currency
                    )
                )
            }
            Pair(sessions.sortedBy { it.startDateMs }, explicitHasMore)
        } catch (_: Exception) {
            Pair(emptyList(), false)
        }
    }

    internal fun hasOverlappingPublicSession(
        existingSessions: List<ChargingSessionEntity>,
        carId: String?,
        startDateMs: Long,
        endDateMs: Long,
        kwh: Double
    ): Boolean {
        val candidateMid = (startDateMs + endDateMs) / 2L
        val kwhTolerance = maxOf(0.5, kwh * 0.10)
        return existingSessions.any { existing ->
            !existing.isSummary &&
                ElectricityImportParser.normalizeVenue(existing.venue) == ElectricityImportParser.VENUE_PUBLIC &&
                (existing.carId == null || carId == null || existing.carId == carId) &&
                abs(((existing.startDateMs + existing.endDateMs) / 2L) - candidateMid) <= 30L * 60_000L &&
                abs(existing.kwh - kwh) <= kwhTolerance
        }
    }

    private fun resolveCarForSupercharger(
        session: TeslaSuperchargerSession,
        cars: List<Car>
    ): Car? {
        val activeCars = cars.filter { !it.isArchived }
        if (activeCars.isEmpty()) return null

        val vinUpper = session.vin?.trim()?.uppercase(Locale.US)
        if (!vinUpper.isNullOrBlank()) {
            activeCars.firstOrNull { it.vin?.trim()?.uppercase(Locale.US) == vinUpper }?.let { return it }
        }

        val teslaEvs = activeCars.filter {
            TelemetryJsonUtils.resolveProviderForCar(it) == OdometerSyncService.PROVIDER_TESLA
        }
        val nonTeslaEvs = activeCars.filter {
            it.isEv && TelemetryJsonUtils.resolveProviderForCar(it) != OdometerSyncService.PROVIDER_TESLA
        }

        val makeType = session.vehicleMakeType?.trim()?.uppercase(Locale.US)
        val isExplicitTeslaMake = makeType == "TSLA" || makeType == "TESLA"
        val isExplicitNonTesla = when {
            !makeType.isNullOrBlank() && (
                makeType.contains("NON_TESLA") ||
                    makeType.contains("NONTESLA") ||
                    makeType.contains("NON-TESLA") ||
                    !isExplicitTeslaMake
                ) -> true
            !vinUpper.isNullOrBlank() && vinUpper.length == 17 &&
                !vinUpper.startsWith("5YJ") && !vinUpper.startsWith("7SA") && !vinUpper.startsWith("7G2") &&
                !vinUpper.startsWith("LRW") && !vinUpper.startsWith("XP7") -> true
            else -> false
        }

        if (isExplicitNonTesla) {
            return nonTeslaEvs.singleOrNull()
        }
        if (isExplicitTeslaMake || !vinUpper.isNullOrBlank()) {
            return teslaEvs.singleOrNull()
        }
        return teslaEvs.singleOrNull()
    }

    fun buildSuperchargerSessionEntities(
        rawSessions: List<TeslaSuperchargerSession>,
        cars: List<Car>,
        existingSessions: List<ChargingSessionEntity> = emptyList(),
        uploadedAtMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<ChargingSessionEntity> {
        if (rawSessions.isEmpty()) return emptyList()
        val seenIds = existingSessions.map { it.id }.toMutableSet()
        val seenFingerprints = existingSessions.map { it.fingerprint }.toMutableSet()
        val combinedPublicForOverlap = existingSessions.toMutableList()
        val entities = mutableListOf<ChargingSessionEntity>()

        for (raw in rawSessions) {
            if (raw.energyKwh < 0.1) continue
            val cleanSessionId = raw.sessionId.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val entityId = "sc_tesla_$cleanSessionId"
            if (!seenIds.add(entityId)) continue

            val targetCar = resolveCarForSupercharger(raw, cars)
            val fingerprint = ElectricityImportParser.buildFingerprint(
                startDateMs = raw.startDateMs,
                endDateMs = raw.endDateMs,
                kwh = raw.energyKwh,
                venue = ElectricityImportParser.VENUE_PUBLIC,
                carId = targetCar?.id,
                zoneId = zoneId
            )
            if (!seenFingerprints.add(fingerprint)) continue
            if (hasOverlappingPublicSession(combinedPublicForOverlap, targetCar?.id, raw.startDateMs, raw.endDateMs, raw.energyKwh)) {
                continue
            }

            val siteLabel = raw.siteLocationName?.takeIf { it.isNotBlank() }
                ?.let { "Supercharger · $it" }
                ?: "Tesla Supercharger"

            val entity = ChargingSessionEntity(
                id = entityId,
                carId = targetCar?.id,
                startDateMs = raw.startDateMs,
                endDateMs = raw.endDateMs,
                kwh = raw.energyKwh,
                venue = ElectricityImportParser.VENUE_PUBLIC,
                directCost = raw.totalPaidCost,
                chargerName = siteLabel,
                isSummary = false,
                fingerprint = fingerprint,
                uploadBatchId = ElectricityImportParser.AUTO_SUPERCHARGER_BATCH_ID,
                uploadedAtMs = uploadedAtMs
            )
            entities.add(entity)
            combinedPublicForOverlap.add(entity)
        }
        return entities
    }
}
