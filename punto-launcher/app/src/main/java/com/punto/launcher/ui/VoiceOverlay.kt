package com.punto.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.punto.launcher.assistant.Voice
import kotlinx.coroutines.delay

private val SUGGESTIONS = listOf(
    "Toca Legião Urbana",
    "Me leva para o posto mais próximo",
    "Como está o clima?",
    "Qual a minha velocidade?",
    "Próxima música",
    "Abre o Spotify",
)

/** Tela do assistente: ouvindo, o que entendeu e a resposta. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceOverlay(voice: Voice, onListen: () -> Unit, onCommand: (String) -> Unit) {
    AnimatedVisibility(visible = voice.overlayVisible, enter = fadeIn(), exit = fadeOut()) {
        BackHandler { voice.dismiss() }

        // Fecha sozinho alguns segundos depois de responder.
        LaunchedEffect(voice.reply, voice.listening) {
            if (!voice.listening && voice.reply.isNotEmpty()) {
                delay(8_000)
                voice.dismiss()
            }
        }

        val noRipple = remember { MutableInteractionSource() }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.93f))
                .clickable(interactionSource = noRipple, indication = null) { voice.dismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                MicOrb(listening = voice.listening, level = voice.level, onClick = onListen)
                Spacer(Modifier.height(20.dp))
                Text(
                    text = when {
                        voice.heard.isNotEmpty() -> "“${voice.heard}”"
                        voice.listening -> "Estou ouvindo…"
                        else -> "Toque no microfone e fale"
                    },
                    color = TextMain,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                if (voice.reply.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        voice.reply,
                        color = Accent,
                        fontSize = 19.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text("Experimente:", color = TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SUGGESTIONS.forEach { s ->
                        Text(
                            s,
                            color = TextMain,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(PanelBg2)
                                .clickable { onCommand(s) }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MicOrb(listening: Boolean, level: Float, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val breathing by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "breathing",
    )
    val voiceScale by animateFloatAsState(1f + level * 0.35f, label = "level")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
        if (listening) {
            Box(
                Modifier
                    .size(110.dp)
                    .scale(maxOf(breathing, voiceScale))
                    .background(Accent.copy(alpha = 0.25f), CircleShape),
            )
        }
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Accent, Danger)))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = "Falar", tint = Color.White, modifier = Modifier.size(44.dp))
        }
    }
}
