package com.example.car_logger.ui.screens.home.tabs.insights.electricity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.car_logger.ui.theme.expressiveBounceClick
import java.util.Locale

@Stable
internal class InlineRateEditorState(
    initialRate: Double,
    val isEditable: Boolean,
    private val onSaveCustomRate: (Float) -> Unit,
    private val onLeftClick: () -> Unit,
    private val keyboardController: SoftwareKeyboardController?
) {
    var isEditing by mutableStateOf(false)
        private set
    var rateDraft by mutableStateOf(String.format(Locale.US, "%.3f", initialRate))
    var hasInputError by mutableStateOf(false)

    fun syncRate(rateValue: Double) {
        if (!isEditing) {
            rateDraft = String.format(Locale.US, "%.3f", rateValue)
        }
    }

    fun startOrClick(currentRate: Double) {
        if (isEditable) {
            rateDraft = String.format(Locale.US, "%.3f", currentRate)
            hasInputError = false
            isEditing = true
        } else {
            onLeftClick()
        }
    }

    fun updateDraft(newDraft: String) {
        rateDraft = newDraft
        hasInputError = false
    }

    fun commitRate() {
        val parsed = rateDraft.trim().toFloatOrNull()
        if (parsed != null && parsed >= 0f) {
            hasInputError = false
            isEditing = false
            keyboardController?.hide()
            onSaveCustomRate(parsed)
        } else {
            hasInputError = true
        }
    }

    fun cancelEdit(currentRate: Double) {
        hasInputError = false
        rateDraft = String.format(Locale.US, "%.3f", currentRate)
        isEditing = false
        keyboardController?.hide()
    }
}

@Composable
internal fun RateChipsRow(
    rateValue: Double,
    rateSourceLabel: String,
    isEstimated: Boolean,
    isEditable: Boolean,
    leftOverline: String,
    rightOverline: String,
    onSaveCustomRate: (Float) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val editorState = remember(isEditable, keyboardController) {
        InlineRateEditorState(rateValue, isEditable, onSaveCustomRate, onLeftClick, keyboardController)
    }
    LaunchedEffect(rateValue) { editorState.syncRate(rateValue) }
    LaunchedEffect(editorState.isEditing) {
        if (editorState.isEditing) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RateEditorLeftChip(
            rateValue = rateValue,
            isEstimated = isEstimated,
            leftOverline = leftOverline,
            editorState = editorState,
            focusRequester = focusRequester,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        RateSourceRightChip(
            rightOverline = rightOverline,
            rateSourceLabel = rateSourceLabel,
            onClick = onRightClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun RateEditorLeftChip(
    rateValue: Double,
    isEstimated: Boolean,
    leftOverline: String,
    editorState: InlineRateEditorState,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val isEditing = editorState.isEditing
    Surface(
        modifier = modifier.then(
            if (isEditing) Modifier else Modifier.expressiveBounceClick { editorState.startOrClick(rateValue) }
        ),
        shape = RoundedCornerShape(16.dp),
        color = if (isEditing) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = when {
            isEditing && editorState.hasInputError -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
            isEditing -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            else -> null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "SET RATE ($/kWh)" else leftOverline,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isEditing) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cancel editing rate",
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable { editorState.cancelEdit(rateValue) },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (editorState.isEditable) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit electric rate",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (isEditing) {
                RateDraftInputRow(
                    rateDraft = editorState.rateDraft,
                    onDraftChange = editorState::updateDraft,
                    onCommit = editorState::commitRate,
                    focusRequester = focusRequester
                )
            } else {
                RateDisplayRow(rateValue = rateValue, isEstimated = isEstimated)
            }
        }
    }
}

@Composable
private fun RateDraftInputRow(
    rateDraft: String,
    onDraftChange: (String) -> Unit,
    onCommit: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BasicTextField(
            value = rateDraft,
            onValueChange = onDraftChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(24.dp)
                .expressiveBounceClick { onCommit() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, contentDescription = "Save electric rate", modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun RateDisplayRow(
    rateValue: Double,
    isEstimated: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "$${String.format(Locale.US, "%.3f", rateValue)}/kWh",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isEstimated) {
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(
                    text = "Est.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun RateSourceRightChip(
    rightOverline: String,
    rateSourceLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.expressiveBounceClick { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = rightOverline,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = rateSourceLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
