package com.punto.launcher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val SWEEP = 270f

/** Velocímetro em arco de 270°, com marcações e número grande no centro. */
@Composable
fun Speedometer(
    speedKmh: Float,
    gpsFix: Boolean,
    modifier: Modifier = Modifier,
    maxKmh: Float = 200f,
) {
    val animated by animateFloatAsState(
        targetValue = speedKmh.coerceIn(0f, maxKmh),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "speed",
    )
    val measurer = rememberTextMeasurer()
    // Gradiente varre a partir de 0° (direita) no sentido horário.
    // O arco vai de 135° (início, ciano) passando pelo topo até 45° (fim, vermelho).
    val stops = remember {
        arrayOf(
            0.00f to Accent,
            0.125f to Danger,
            0.20f to Danger,
            0.30f to Cyan,
            0.375f to Cyan,
            0.79f to Amber,
            1.00f to Accent,
        )
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = min(maxWidth, maxHeight)
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = size.minDimension * 0.055f
                val radius = size.minDimension / 2f - stroke
                val topLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(radius * 2, radius * 2)

                drawArc(
                    color = Track,
                    startAngle = START_ANGLE,
                    sweepAngle = SWEEP,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                val frac = animated / maxKmh
                if (frac > 0.002f) {
                    drawArc(
                        brush = Brush.sweepGradient(*stops, center = center),
                        startAngle = START_ANGLE,
                        sweepAngle = SWEEP * frac,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }

                // Marcações a cada 10 km/h, números a cada 20.
                val labelStyle = TextStyle(color = TextDim, fontSize = (size.minDimension * 0.042f).toSp())
                var v = 0
                while (v <= maxKmh.toInt()) {
                    val major = v % 20 == 0
                    val rad = Math.toRadians((START_ANGLE + SWEEP * v / maxKmh).toDouble())
                    val dir = Offset(cos(rad).toFloat(), sin(rad).toFloat())
                    val outer = radius - stroke * 1.1f
                    val inner = outer - if (major) stroke * 0.8f else stroke * 0.4f
                    drawLine(
                        color = if (v <= animated) TextMain else TextDim.copy(alpha = 0.45f),
                        start = center + dir * inner,
                        end = center + dir * outer,
                        strokeWidth = if (major) 2.5.dp.toPx() else 1.2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    if (major) {
                        val layout = measurer.measure(v.toString(), labelStyle)
                        val p = center + dir * (inner - stroke * 1.0f)
                        drawText(
                            layout,
                            topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f),
                        )
                    }
                    v += 10
                }

                // Ponto brilhante na ponta do arco.
                val endRad = Math.toRadians((START_ANGLE + SWEEP * frac).toDouble())
                val tip = center + Offset(cos(endRad).toFloat(), sin(endRad).toFloat()) * radius
                drawCircle(Accent.copy(alpha = 0.35f), radius = stroke * 0.95f, center = tip)
                drawCircle(TextMain, radius = stroke * 0.5f, center = tip)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = animated.roundToInt().toString(),
                    color = TextMain,
                    fontSize = (side.value * 0.25f).sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
                Text(
                    text = "km/h",
                    color = TextDim,
                    fontSize = (side.value * 0.065f).sp,
                    modifier = Modifier.offset(y = (-side.value * 0.02f).dp),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -(side * 0.06f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(if (gpsFix) Ok else TextDim, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (gpsFix) "GPS" else "Sem GPS",
                    color = if (gpsFix) TextMain else TextDim,
                    fontSize = (side.value * 0.045f).coerceAtLeast(11f).sp,
                )
            }
        }
    }
}
