package com.example.running.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.running.core.model.Sex
import com.example.running.data.settings.SettingsRepository
import com.example.running.setup.ProfileForm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ProfileEditUiState(
    val form: ProfileForm = ProfileForm(),
    val showErrors: Boolean = false,
    val saved: Boolean = false,
)

internal class ProfileEditViewModel(private val settings: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProfileEditUiState())
    val state: StateFlow<ProfileEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.update { it.copy(form = ProfileForm.from(settings.current().profile)) } }
    }

    fun setWeight(v: String) = _state.update { it.copy(form = it.form.copy(weight = v)) }
    fun setAge(v: String) = _state.update { it.copy(form = it.form.copy(age = v)) }
    fun setHeight(v: String) = _state.update { it.copy(form = it.form.copy(height = v)) }
    fun setSex(v: Sex) = _state.update { it.copy(form = it.form.copy(sex = v)) }

    fun save() {
        viewModelScope.launch {
            val profile = _state.value.form.toProfile(settings.current().profile)
            if (profile == null) {
                _state.update { it.copy(showErrors = true) }
            } else {
                settings.updateProfile(profile)
                _state.update { it.copy(saved = true) }
            }
        }
    }
}
