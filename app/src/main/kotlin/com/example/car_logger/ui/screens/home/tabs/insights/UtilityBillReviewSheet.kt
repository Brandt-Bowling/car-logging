package com.example.car_logger.ui.screens.home.tabs.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.car_logger.data.local.entity.UtilityBillEntity
import com.example.car_logger.services.ParsedUtilityBillDraft
import com.example.car_logger.ui.screens.home.tabs.insights.review.BillBreakdownCard
import com.example.car_logger.ui.screens.home.tabs.insights.review.UtilityBillActionRow
import com.example.car_logger.ui.screens.home.tabs.insights.review.UtilityBillCoreFieldsSection
import com.example.car_logger.ui.screens.home.tabs.insights.review.UtilityBillEffectiveRateCard
import com.example.car_logger.ui.screens.home.tabs.insights.review.UtilityBillHeaderSection
import com.example.car_logger.ui.screens.home.tabs.insights.review.UtilityBillReplaceWarningBanner
import com.example.car_logger.ui.screens.home.tabs.insights.review.UtilityBillReviewState
import com.example.car_logger.ui.screens.home.tabs.insights.review.UtilityBillTouOverrideCard
import com.example.car_logger.ui.screens.home.tabs.insights.review.rememberUtilityBillReviewState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UtilityBillReviewSheet(
    initialDraft: ParsedUtilityBillDraft?,
    existingBills: List<UtilityBillEntity>,
    onUploadDifferentFile: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        providerName: String,
        startDateMs: Long,
        endDateMs: Long,
        totalKwh: Double,
        totalAmount: Double,
        fixedCharges: Double,
        overrideRate: Double?,
        replaceExistingId: String?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
    val topClearance = maxOf(topInset + 8.dp, 72.dp)
    val state = rememberUtilityBillReviewState(initialDraft)
    val exactMatchingBill = remember(state.startDateText, state.endDateText, existingBills) {
        state.findExactMatchingBill(existingBills)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
        modifier = modifier.padding(top = topClearance)
    ) {
        UtilityBillSheetContent(
            state = state,
            initialDraft = initialDraft,
            exactMatchingBill = exactMatchingBill,
            onUploadDifferentFile = onUploadDifferentFile,
            onDismiss = onDismiss,
            onSave = onSave
        )
    }
}

@Composable
private fun UtilityBillSheetContent(
    state: UtilityBillReviewState,
    initialDraft: ParsedUtilityBillDraft?,
    exactMatchingBill: UtilityBillEntity?,
    onUploadDifferentFile: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        providerName: String,
        startDateMs: Long,
        endDateMs: Long,
        totalKwh: Double,
        totalAmount: Double,
        fixedCharges: Double,
        overrideRate: Double?,
        replaceExistingId: String?
    ) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        UtilityBillHeaderSection(initialDraft = initialDraft, onUploadDifferentFile = onUploadDifferentFile)
        UtilityBillCoreFieldsSection(state = state)
        UtilityBillEffectiveRateCard(state = state)

        if (initialDraft != null || state.parsedAmount > 0.0 || state.parsedKwh > 0.0) {
            BillBreakdownCard(
                breakdownItems = initialDraft?.breakdownItems.orEmpty(),
                offPeakKwh = initialDraft?.offPeakKwh,
                onPeakKwh = initialDraft?.onPeakKwh,
                offPeakSupplyRate = state.parsedSupplyComponent ?: initialDraft?.offPeakSupplyRate,
                onPeakSupplyRate = initialDraft?.onPeakSupplyRate,
                distributionRatePerKwh = state.parsedDistComponent ?: initialDraft?.distributionRatePerKwh,
                totalAmount = state.parsedAmount,
                fixedCharges = state.parsedFixed,
                totalKwh = state.parsedKwh,
                calculatedBlendedRate = state.calculatedRate,
                useTouRate = state.useOverrideRate
            )
        }

        UtilityBillTouOverrideCard(state = state)

        exactMatchingBill?.let { UtilityBillReplaceWarningBanner(exactMatchingBill = it) }

        state.errorMessage?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        UtilityBillActionRow(
            isReplacing = exactMatchingBill != null,
            onDismiss = onDismiss,
            onSaveClick = { state.submit(exactMatchingBill?.id, onSave) }
        )
    }
}
