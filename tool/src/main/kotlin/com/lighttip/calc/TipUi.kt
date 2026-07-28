package com.lighttip.calc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/**
 * A 1dp rule. The panel is matte and every surface is true black, so a solid
 * hairline is the only reliable way to separate regions — tonal elevation and
 * low-alpha fills both vanish on this screen.
 */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LightThemeTokens.colors.contentSecondary),
    )
}

@Composable
fun CenterMessage(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background)
            .padding(2f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(message, variant = LightTextVariant.Copy, align = TextAlign.Center)
    }
}

/** Full-width tappable row of text, the standard LightOS menu affordance. */
@Composable
fun MenuRow(
    label: String,
    detail: String? = null,
    heightUnits: Float = 3f,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightUnits.gridUnitsAsDp())
            .lightClickable(onClickLabel = label, role = Role.Button) { onClick() }
            .padding(horizontal = 1f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            label,
            variant = LightTextVariant.Copy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (detail != null) {
            LightText(detail, variant = LightTextVariant.Detail, lighten = true)
        }
    }
}

/**
 * Selection is shown by inverting the chip rather than tinting it. Hue is discarded
 * on this display, so full inversion is the only state change that survives.
 */
@Composable
fun RowScopeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(if (selected) colors.content else colors.background)
            .lightClickable(onClickLabel = label, role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            label,
            variant = LightTextVariant.Button,
            color = if (selected) colors.background else colors.content,
            maxLines = 1,
        )
    }
}

/** Tip presets, two rows of three. The last cell opens a free-entry percentage. */
@Composable
fun TipPresetGrid(
    presets: List<Int>,
    selected: Int,
    isCustom: Boolean,
    rowHeightUnits: Float,
    onSelect: (Int) -> Unit,
    onCustom: () -> Unit,
) {
    val rows = presets.chunked(3)
    Column(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(rowHeightUnits.gridUnitsAsDp()),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                row.forEach { percent ->
                    RowScopeChip(
                        label = "$percent%",
                        selected = !isCustom && percent == selected,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(percent) },
                    )
                }
                if (rowIndex == rows.lastIndex) {
                    repeat(2 - row.lastIndex) {
                        RowScopeChip(
                            label = if (isCustom) "$selected%" else "OTHER",
                            selected = isCustom,
                            modifier = Modifier.weight(1f),
                            onClick = onCustom,
                        )
                    }
                }
            }
            if (rowIndex != rows.lastIndex) Hairline()
        }
    }
}

/**
 * POS-style keypad: digits push in from the right, so 1-2-3-4 reads as $12.34.
 * Drawn in the tool rather than borrowed from the keyboard service — the targets
 * need to be thumb-sized on a 3.92" panel and there is no decimal point to mistype.
 */
@Composable
fun ColumnScope.LightKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "<"),
    )
    Column(modifier = modifier.fillMaxWidth().weight(1f)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                row.forEach { key ->
                    KeypadKey(
                        key = key,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (key) {
                                "C" -> onClear()
                                "<" -> onBackspace()
                                else -> onDigit(key.toInt())
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(key: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val label = when (key) {
        "<" -> "DEL"
        else -> key
    }
    val description = when (key) {
        "<" -> "Delete last digit"
        "C" -> "Clear"
        else -> key
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .lightClickable(onClickLabel = description, role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            label,
            variant = if (key.length == 1 && key[0].isDigit()) {
                LightTextVariant.Heading
            } else {
                LightTextVariant.Button
            },
        )
    }
}
