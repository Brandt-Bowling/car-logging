package com.example.car_logger.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = topRadius, topEnd = topRadius),
        color = backgroundColor,
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    }
}
