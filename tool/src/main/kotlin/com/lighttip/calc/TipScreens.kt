package com.lighttip.calc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.buildDatabase
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp

/** Bottom-bar labels. The active tab is bracketed, since colour is not available. */
private fun tabLabel(name: String, active: Boolean) = if (active) "[ $name ]" else name

@InitialScreen
class TipScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, TipViewModel>(sealedActivity) {

    override val viewModelClass: Class<TipViewModel> = TipViewModel::class.java

    override fun createViewModel(): TipViewModel {
        val database = lightContext.buildDatabase(TipDatabase::class.java, "light-tip.db")
        return TipViewModel(TipRepository(database.tipDao(), lightContext), database)
    }

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        val repository = viewModel.repository

        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    center = LightTopBarCenter.Text("Tip Calculator"),
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.SETTINGS,
                        onClick = { navigateTo({ SettingsScreen(it, repository) }) },
                        contentDescription = "Settings",
                    ),
                )

                AmountReadout(state.amountCents)
                Hairline()
                TotalsStrip(tipCents = state.tipCents, totalCents = state.totalCents)
                Hairline()
                TipPresetGrid(
                    presets = TIP_PRESETS,
                    selected = state.tipPercent,
                    isCustom = state.isCustomPercent,
                    rowHeightUnits = 2.4f,
                    onSelect = { viewModel.selectPreset(it) },
                    onCustom = {
                        navigateTo(
                            { PercentScreen(it, state.tipPercent) },
                            resultCallback = { percent -> viewModel.setCustomPercent(percent) },
                        )
                    },
                )
                Hairline()
                LightKeypad(
                    onDigit = { viewModel.pushDigit(it) },
                    onBackspace = { viewModel.backspace() },
                    onClear = { viewModel.clear() },
                )
                LightBottomBar(
                    items = listOf(
                        LightBarButton.Text(tabLabel("TIP", true), onClick = {}),
                        LightBarButton.Text(
                            tabLabel("SPLIT", false),
                            onClick = { navigateTo({ SplitListScreen(it, repository) }) },
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun AmountReadout(amountCents: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.2f.gridUnitsAsDp())
            .padding(horizontal = 1f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText("BILL", variant = LightTextVariant.Detail, lighten = true)
        Box(Modifier.weight(1f))
        LightText(
            amountCents.asMoney(),
            variant = LightTextVariant.Title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TotalsStrip(tipCents: Long, totalCents: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(3f.gridUnitsAsDp())
            .padding(horizontal = 1f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelledFigure("TIP", tipCents, Modifier.weight(1f), TextAlign.Start)
        LabelledFigure("TOTAL", totalCents, Modifier.weight(1f), TextAlign.End)
    }
}

@Composable
private fun LabelledFigure(
    label: String,
    cents: Long,
    modifier: Modifier = Modifier,
    align: TextAlign,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (align == TextAlign.End) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        LightText(label, variant = LightTextVariant.Micro, lighten = true)
        LightText(
            cents.asMoney(),
            variant = LightTextVariant.Subheading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Free-entry percentage on the same keypad, returned to the caller on accept. */
class PercentScreen(
    sealedActivity: SealedLightActivity,
    private val initial: Int,
) : LightScreen<Int, PercentViewModel>(sealedActivity) {

    override val viewModelClass: Class<PercentViewModel> = PercentViewModel::class.java
    override fun createViewModel() = PercentViewModel(initial)

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val percent by viewModel.percent.collectAsState()

        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Custom tip"),
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.ACCEPT,
                        onClick = { goBack(percent) },
                        contentDescription = "Use this percentage",
                    ),
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(5f.gridUnitsAsDp()),
                    contentAlignment = Alignment.Center,
                ) {
                    LightText("$percent%", variant = LightTextVariant.Title)
                }
                Hairline()
                LightKeypad(
                    onDigit = { viewModel.pushDigit(it) },
                    onBackspace = { viewModel.backspace() },
                    onClear = { viewModel.clear() },
                )
            }
        }
    }
}
