package com.punto.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.punto.launcher.data.Prefs

@Composable
fun SettingsDialog(prefs: Prefs, actions: LauncherActions, onDismiss: () -> Unit) {
    var home by remember { mutableStateOf(prefs.homeAddress) }
    var work by remember { mutableStateOf(prefs.workAddress) }
    var music by remember { mutableStateOf(prefs.musicApp) }
    var nav by remember { mutableStateOf(prefs.navApp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelBg,
        title = { Text("Configurações") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = home,
                    onValueChange = { home = it },
                    label = { Text("Endereço de casa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = work,
                    onValueChange = { work = it },
                    label = { Text("Endereço do trabalho") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                Text("App de música", color = TextDim, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = music == Prefs.MUSIC_SPOTIFY,
                        onClick = { music = Prefs.MUSIC_SPOTIFY },
                        label = { Text("Spotify") },
                    )
                    FilterChip(
                        selected = music == Prefs.MUSIC_YTMUSIC,
                        onClick = { music = Prefs.MUSIC_YTMUSIC },
                        label = { Text("YouTube Music") },
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text("App de navegação", color = TextDim, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = nav == Prefs.NAV_WAZE,
                        onClick = { nav = Prefs.NAV_WAZE },
                        label = { Text("Waze") },
                    )
                    FilterChip(
                        selected = nav == Prefs.NAV_MAPS,
                        onClick = { nav = Prefs.NAV_MAPS },
                        label = { Text("Google Maps") },
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Track)
                TextButton(onClick = actions.openHomeSettings) {
                    Text("Usar o Punto OS como tela inicial")
                }
                TextButton(onClick = actions.openNotificationAccess) {
                    Text("Permitir mostrar a música tocando")
                }
                TextButton(onClick = actions.resetTrip) {
                    Text("Zerar viagem e velocidade máxima")
                }
                TextButton(onClick = actions.openSystemSettings) {
                    Text("Configurações do Android")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    prefs.homeAddress = home
                    prefs.workAddress = work
                    prefs.musicApp = music
                    prefs.navApp = nav
                    onDismiss()
                },
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
