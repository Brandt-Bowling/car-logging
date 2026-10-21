package com.example.car_logger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.car_logger.ui.theme.ExpressiveThemeTokens

/**
 * Material 3 Expressive enveloped body canvas.
 * Implements the distinct sheet layer (28dp top radius) resting over the outer shell
 * (top app bar and bottom navigation bar) matching native Google Android apps like Gmail.
 */
@Composable
fun EnvelopedBodyCanvas(
    modifier: Modifier = Modifier,
    topRadius: Dp = 28.dp,
    backgroundColor: Color = ExpressiveThemeTokens.canvasColor,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = topRadius, topEnd = topRadius))
            .background(backgroundColor),
        content = content
    )
}
