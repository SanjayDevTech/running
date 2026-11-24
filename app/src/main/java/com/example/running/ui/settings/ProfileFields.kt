package com.example.running.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.example.running.R
import com.example.running.core.model.Sex
import com.example.running.setup.ProfileForm
import com.example.running.ui.components.ChoiceChips
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Spacing

@Composable
internal fun Sex.label(): String = stringResource(
    when (this) {
        Sex.MALE -> R.string.profile_sex_male
        Sex.FEMALE -> R.string.profile_sex_female
        Sex.UNSPECIFIED -> R.string.profile_sex_unspecified
    },
)

/** Weight / age / sex / optional height. Shared by onboarding and the profile editor. */
@Composable
internal fun ProfileFields(
    form: ProfileForm,
    showErrors: Boolean,
    onWeight: (String) -> Unit,
    onAge: (String) -> Unit,
    onSex: (Sex) -> Unit,
    onHeight: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val errors = if (showErrors) form.errors else emptySet()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        NumberField(
            value = form.weight, onChange = onWeight, label = stringResource(R.string.onboarding_weight),
            error = if (ProfileForm.Error.WEIGHT in errors) stringResource(R.string.onboarding_error_weight) else null,
            decimal = true,
        )
        NumberField(
            value = form.age, onChange = onAge, label = stringResource(R.string.onboarding_age),
            error = if (ProfileForm.Error.AGE in errors) stringResource(R.string.onboarding_error_age) else null,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.onboarding_sex), style = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val sexLabels = Sex.entries.associateWith { it.label() }
            ChoiceChips(Sex.entries, form.sex, { sexLabels.getValue(it) }, onSex)
        }
        NumberField(
            value = form.height, onChange = onHeight, label = stringResource(R.string.onboarding_height),
            error = if (ProfileForm.Error.HEIGHT in errors) stringResource(R.string.onboarding_error_height) else null,
        )
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, error: String?, decimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || (decimal && (c == '.' || c == ',')) }.take(6)) },
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
