package com.example.running.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Spacing

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = Spacing.xxxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = AppText.titleL, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Text(
            body,
            style = AppText.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s),
        )
        if (actionLabel != null && onAction != null) {
            PrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = Spacing.xxl))
        }
    }
}
