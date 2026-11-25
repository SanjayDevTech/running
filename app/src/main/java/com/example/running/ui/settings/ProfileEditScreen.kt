package com.example.running.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.running.R
import com.example.running.di.appViewModel
import com.example.running.ui.components.AppTopBar
import com.example.running.ui.components.PrimaryButton
import com.example.running.ui.theme.Black
import com.example.running.ui.theme.Spacing

/** CONTRACT(F). Public signature frozen (architecture.md §3.8). */
@Composable
fun ProfileEditScreen(onBack: () -> Unit) {
    val vm = appViewModel<ProfileEditViewModel> { c -> ProfileEditViewModel(c.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Column(Modifier.fillMaxSize().background(Black).imePadding()) {
        AppTopBar(stringResource(R.string.settings_profile_title), onBack = onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
        ) {
            ProfileFields(
                form = state.form, showErrors = state.showErrors,
                onWeight = vm::setWeight, onAge = vm::setAge, onSex = vm::setSex, onHeight = vm::setHeight,
            )
            PrimaryButton(stringResource(R.string.common_save), vm::save, Modifier.fillMaxWidth())
        }
    }
}
