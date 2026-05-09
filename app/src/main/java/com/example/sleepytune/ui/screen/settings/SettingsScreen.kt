package com.example.sleepytune.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.sleepytune.R
import com.example.sleepytune.ui.viewmodel.LanguageViewModel
import com.example.sleepytune.ui.viewmodel.ThemeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val languageViewModel: LanguageViewModel = hiltViewModel()
    val themeMode by themeViewModel.themeMode.collectAsState()
    val currentLanguage by languageViewModel.currentLanguage.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // 获取当前语言显示名称
    val languageDisplayName = remember(currentLanguage) {
        languageViewModel.getLanguageDisplayName(currentLanguage)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 语言设置
            SettingItem(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.settings_language),
                description = languageDisplayName
            ) {
                navController.navigate("languageSettings")
            }

            // 主题设置
            SettingItem(
                icon = Icons.Filled.Palette,
                title = stringResource(R.string.settings_theme),
                description = when (themeMode) {
                    ThemeViewModel.ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                    ThemeViewModel.ThemeMode.DARK -> stringResource(R.string.theme_dark)
                    ThemeViewModel.ThemeMode.AUTO -> stringResource(R.string.theme_system)
                }
            ) { showThemeDialog = true }

            SettingItem(Icons.Filled.Security, stringResource(R.string.settings_permissions), stringResource(R.string.settings_permissions_desc)) {
                navController.navigate("permissionGuide")
            }

            SettingItem(Icons.Filled.Info, stringResource(R.string.settings_about), stringResource(R.string.settings_about_desc)) {
                showAboutDialog = true
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showThemeDialog) {
        Dialog(onDismissRequest = { showThemeDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(20.dp))

                    ThemeViewModel.ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable {
                                themeViewModel.setThemeMode(mode)
                                showThemeDialog = false
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = { themeViewModel.setThemeMode(mode); showThemeDialog = false },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                when (mode) {
                                    ThemeViewModel.ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                    ThemeViewModel.ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                    ThemeViewModel.ThemeMode.AUTO -> stringResource(R.string.theme_system)
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { showThemeDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.confirm_cancel), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text("SleepyTune v1.0.0", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.about_desc), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.about_features), style = MaterialTheme.typography.titleSmall)
                    Text("• ${stringResource(R.string.about_feature_alarm)}", style = MaterialTheme.typography.bodyMedium)
                    Text("• ${stringResource(R.string.about_feature_fade)}", style = MaterialTheme.typography.bodyMedium)
                    Text("• ${stringResource(R.string.about_feature_playlist)}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("© 2026 SleepyTune", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text(stringResource(R.string.confirm_ok)) } },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick?.invoke() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        icon, title,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowRight, "more",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
