package com.example.running.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.running.R
import com.example.running.ui.theme.Accent
import com.example.running.ui.theme.AppText
import com.example.running.ui.theme.Raised2
import com.example.running.ui.theme.TextSecondary

enum class Tab(val route: Any, val labelRes: Int, val icon: ImageVector) {
    Record(RecordRoute, R.string.tab_record, Icons.AutoMirrored.Rounded.DirectionsRun),
    Activities(ActivitiesRoute, R.string.tab_activities, Icons.AutoMirrored.Rounded.FormatListBulleted),
    Progress(ProgressRoute, R.string.tab_progress, Icons.Rounded.BarChart),
    Settings(SettingsRoute, R.string.tab_settings, Icons.Rounded.Settings),
}

/** Raised near-black bar; selected item in accent (the one chrome use of accent the house rules allow). */
@Composable
fun BottomBar(selected: Tab?, recordingActive: Boolean, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = Raised2, tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = {
                    Box {
                        Icon(tab.icon, contentDescription = null)
                        if (tab == Tab.Record && recordingActive) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-2).dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Accent)
                            )
                        }
                    }
                },
                label = { Text(stringResource(tab.labelRes), style = AppText.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Accent,
                    selectedTextColor = Accent,
                    indicatorColor = Raised2,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                ),
            )
        }
    }
}

