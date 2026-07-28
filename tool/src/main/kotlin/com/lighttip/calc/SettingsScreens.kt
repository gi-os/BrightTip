package com.lighttip.calc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter

class SettingsScreen(
    sealedActivity: SealedLightActivity,
    private val repository: TipRepository,
) : LightScreen<Unit, EmptyViewModel>(sealedActivity) {

    override val viewModelClass = EmptyViewModel::class.java
    override fun createViewModel() = EmptyViewModel()

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Anthropic API key"),
                )
                MenuRow("Scan API key (QR)") {
                    navigateTo({ KeyScannerScreen(it, repository) })
                }
                MenuRow("Type API key manually") {
                    navigateTo({ ApiKeyScreen(it, repository) })
                }
            }
        }
    }
}

class ApiKeyScreen(
    sealedActivity: SealedLightActivity,
    private val repository: TipRepository,
) : LightScreen<Unit, TextEntryViewModel>(sealedActivity) {

    override val viewModelClass: Class<TextEntryViewModel> = TextEntryViewModel::class.java
    override fun createViewModel() = TextEntryViewModel(
        load = { repository.getApiKey() },
        save = { repository.setApiKey(it) },
    )

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        val input = key(state.inputSession) { rememberTextFieldState(state.draft) }
        val keyboard = rememberKeyboardOptions()

        LightTheme(colors = colors) {
            LightTextInputEditor(
                title = "Anthropic API key",
                editorKey = state.inputSession,
                keyboardOptionsFlow = keyboard,
                state = input,
                singleLine = true,
                onSubmit = { raw -> viewModel.submit(raw.toString()) { goBack() } },
                onBack = { goBack() },
                submitIcon = LightIcons.ACCEPT,
                showBackButton = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
