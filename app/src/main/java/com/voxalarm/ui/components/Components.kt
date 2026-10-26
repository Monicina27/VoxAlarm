package com.voxalarm.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voxalarm.ui.theme.LocalAccent
import com.voxalarm.ui.theme.NeonPalette
import com.voxalarm.ui.theme.VoxBlack
import com.voxalarm.ui.theme.VoxCard
import com.voxalarm.ui.theme.VoxOutline
import com.voxalarm.ui.theme.VoxTextDim
import com.voxalarm.ui.theme.VoxTextFaint
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/** Soft neon glow drawn behind a rounded shape (visible on Android 9+). */
fun Modifier.neonGlow(
    color: Color,
    radius: Dp = 18.dp,
    corner: Dp = 24.dp,
    alpha: Float = 0.55f,
): Modifier = this.drawBehind {
    if (alpha <= 0f) return@drawBehind
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val fp = paint.asFrameworkPaint()
        fp.color = color.copy(alpha = 0f).toArgb()
        fp.setShadowLayer(radius.toPx(), 0f, 0f, color.copy(alpha = alpha).toArgb())
        canvas.drawRoundRect(0f, 0f, size.width, size.height, corner.toPx(), corner.toPx(), paint)
    }
}

@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    glow: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glowAlpha by animateFloatAsState(if (glow && accent != null) 0.45f else 0f, tween(500), label = "glow")
    val borderColor by animateColorAsState(
        if (glow && accent != null) accent.copy(alpha = 0.55f) else VoxOutline.copy(alpha = 0.6f),
        tween(500), label = "border"
    )
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .neonGlow(accent ?: Color.Transparent, radius = 22.dp, alpha = glowAlpha)
            .clip(shape)
            .background(VoxCard)
            .border(BorderStroke(1.dp, borderColor), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = LocalAccent.current.color) {
    Row(modifier.padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Big pill-shaped gradient button with glow. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush = NeonPalette.gradient(0),
    glowColor: Color = LocalAccent.current.color,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(height)
            .neonGlow(glowColor, radius = 16.dp, corner = height / 2, alpha = if (enabled) 0.5f else 0f)
            .clip(shape)
            .background(if (enabled) brush else SolidColor(VoxOutline))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = VoxBlack, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, color = VoxBlack, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** Outlined neon button for secondary actions. */
@Composable
fun NeonOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalAccent.current.color,
    icon: ImageVector? = null,
    height: Dp = 48.dp,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(height)
            .clip(shape)
            .border(1.5.dp, color.copy(alpha = 0.8f), shape)
            .background(color.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun neonSwitchColors(color: Color) = SwitchDefaults.colors(
    checkedThumbColor = VoxBlack,
    checkedTrackColor = color,
    checkedBorderColor = color,
    uncheckedThumbColor = VoxTextDim,
    uncheckedTrackColor = VoxBlack,
    uncheckedBorderColor = VoxOutline,
)

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    color: Color = LocalAccent.current.color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (checked) color else VoxTextFaint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VoxTextDim)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = neonSwitchColors(color))
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    steps: Int = 0,
    icon: ImageVector? = null,
    color: Color = LocalAccent.current.color,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = color)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = VoxOutline,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

/** Row of selectable pills. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoicePills(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalAccent.current.color,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            Pill(label, value == selected, { onSelect(value) }, color)
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, color: Color = LocalAccent.current.color) {
    val bg by animateColorAsState(if (selected) color else Color.Transparent, label = "pillBg")
    val fg by animateColorAsState(if (selected) VoxBlack else VoxTextDim, label = "pillFg")
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .neonGlow(color, radius = 10.dp, corner = 20.dp, alpha = if (selected) 0.45f else 0f)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) color else VoxOutline, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

/** Seven round day toggles (Mon → Sun). */
@Composable
fun DayToggles(mask: Int, onChange: (Int) -> Unit, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { day ->
            val bit = 1 shl (day.value - 1)
            val on = mask and bit != 0
            val bg by animateColorAsState(if (on) color else Color.Transparent, label = "day")
            Box(
                Modifier
                    .size(40.dp)
                    .neonGlow(color, radius = 10.dp, corner = 20.dp, alpha = if (on) 0.5f else 0f)
                    .clip(CircleShape)
                    .background(bg)
                    .border(1.dp, if (on) color else VoxOutline, CircleShape)
                    .clickable(role = Role.Checkbox) { onChange(mask xor bit) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    color = if (on) VoxBlack else VoxTextDim,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Neon colour swatches. */
@Composable
fun ColorSwatches(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        NeonPalette.colors.forEachIndexed { index, neon ->
            val isSel = index == selected
            Box(
                Modifier
                    .size(42.dp)
                    .neonGlow(neon.color, radius = 12.dp, corner = 21.dp, alpha = if (isSel) 0.8f else 0.15f)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(neon.color, neon.partner)))
                    .border(if (isSel) 3.dp else 0.dp, if (isSel) Color.White else Color.Transparent, CircleShape)
                    .clickable(role = Role.RadioButton) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSel) Icon(Icons.Filled.Check, neon.name, tint = VoxBlack, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Animated equaliser bars – lively while [active], calm otherwise. */
@Composable
fun SoundWave(active: Boolean, color: Color, modifier: Modifier = Modifier, bars: Int = 24) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "phase"
    )
    val amplitude by animateFloatAsState(if (active) 1f else 0.12f, tween(450), label = "amp")
    Canvas(modifier) {
        val gap = size.width / (bars * 2f)
        val barW = gap
        for (i in 0 until bars) {
            val x = gap / 2 + i * gap * 2
            val wave = (sin(phase + i * 0.55f) * 0.5f + 0.5f) * (sin(phase * 0.5f + i * 0.21f) * 0.35f + 0.65f)
            val h = (0.12f + 0.88f * wave * amplitude) * size.height
            val top = (size.height - h) / 2
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(color, color.copy(alpha = 0.35f)), startY = top, endY = top + h),
                topLeft = Offset(x, top),
                size = androidx.compose.ui.geometry.Size(barW, h),
                cornerRadius = CornerRadius(barW / 2, barW / 2),
            )
        }
    }
}

/** Concentric pulsing neon rings. */
@Composable
fun PulsingRings(color: Color, partner: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val t by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "t"
    )
    val breathe by transition.animateFloat(
        0.92f, 1.04f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val maxR = size.minDimension / 2
            val core = maxR * 0.55f * breathe
            for (k in 0 until 3) {
                val p = (t + k / 3f) % 1f
                val r = core + (maxR - core) * p
                drawCircle(color.copy(alpha = (1f - p) * 0.55f), radius = r, style = Stroke(width = 3.dp.toPx()))
            }
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = 0.55f), partner.copy(alpha = 0.15f), Color.Transparent), radius = core * 1.25f),
                radius = core * 1.25f,
            )
            drawCircle(color, radius = core, style = Stroke(width = 4.dp.toPx()))
        }
        content()
    }
}
