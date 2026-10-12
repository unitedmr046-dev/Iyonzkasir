package com.iyonzkasir.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iyonzkasir.BRAND
import com.iyonzkasir.DANGER
import com.iyonzkasir.SUCCESS

// ═══════════════════════════════════════════════════════════
// SPACING — konsisten di seluruh app
// ═══════════════════════════════════════════════════════════
object Sp {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 40.dp

    val screenX = 16.dp
    val screenY = 16.dp
    val cardInner = 16.dp
}

// ═══════════════════════════════════════════════════════════
// RADIUS — rounded, friendly
// ═══════════════════════════════════════════════════════════
object Rd {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 20.dp
    val xl = 28.dp
    val full = 999.dp

    val chip = RoundedCornerShape(sm)
    val button = RoundedCornerShape(md)
    val card = RoundedCornerShape(md)
    val cardLg = RoundedCornerShape(lg)
    val iconBox = RoundedCornerShape(14.dp)
    val bottomSheet = RoundedCornerShape(topStart = xl, topEnd = xl)
}

// ═══════════════════════════════════════════════════════════
// ELEVATION — soft, layered
// ═══════════════════════════════════════════════════════════
object El {
    val none = 0.dp
    val card = 1.dp
    val cardHover = 3.dp
    val fab = 6.dp
    val modal = 12.dp
    val sheet = 16.dp
}

// ═══════════════════════════════════════════════════════════
// SURFACE HELPERS — tema-aware
// ═══════════════════════════════════════════════════════════
@Composable
fun surfaceCard(): Color = MaterialTheme.colorScheme.surface

@Composable
fun surfaceCardAlt(): Color = MaterialTheme.colorScheme.surfaceVariant

@Composable
fun surfaceElevated(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
fun borderColor(): Color = MaterialTheme.colorScheme.outlineVariant

@Composable
fun textMuted(): Color = MaterialTheme.colorScheme.onSurfaceVariant

// ═══════════════════════════════════════════════════════════
// APP BUTTON — 6 varian konsisten
// ═══════════════════════════════════════════════════════════
enum class AppBtnVariant { PRIMARY, SECONDARY, OUTLINE, GHOST, DANGER, SUCCESS }
enum class AppBtnSize { SMALL, MEDIUM, LARGE }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppBtnVariant = AppBtnVariant.PRIMARY,
    size: AppBtnSize = AppBtnSize.MEDIUM,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    fullWidth: Boolean = false
) {
    val height = when (size) {
        AppBtnSize.SMALL -> 40.dp
        AppBtnSize.MEDIUM -> 48.dp
        AppBtnSize.LARGE -> 56.dp
    }
    val hPad = when (size) {
        AppBtnSize.SMALL -> 16.dp
        AppBtnSize.MEDIUM -> 20.dp
        AppBtnSize.LARGE -> 24.dp
    }
    val txtStyle = when (size) {
        AppBtnSize.SMALL -> MaterialTheme.typography.labelLarge
        AppBtnSize.MEDIUM -> MaterialTheme.typography.labelLarge
        AppBtnSize.LARGE -> MaterialTheme.typography.titleMedium
    }
    val shape = Rd.button
    val mod = if (fullWidth) modifier.fillMaxWidth() else modifier

    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(100),
        label = "btnScale"
    )

    val (bg, fg, border) = when (variant) {
        AppBtnVariant.PRIMARY -> Triple(BRAND, Color.White, null)
        AppBtnVariant.SECONDARY -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant, null
        )
        AppBtnVariant.OUTLINE -> Triple(
            Color.Transparent,
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.outline
        )
        AppBtnVariant.GHOST -> Triple(
            Color.Transparent,
            MaterialTheme.colorScheme.onSurfaceVariant, null
        )
        AppBtnVariant.DANGER -> Triple(DANGER, Color.White, null)
        AppBtnVariant.SUCCESS -> Triple(SUCCESS, Color.White, null)
    }

    Surface(
        onClick = { if (enabled && !loading) onClick() },
        enabled = enabled && !loading,
        shape = shape,
        color = bg,
        contentColor = fg,
        border = border?.let { BorderStroke(1.5.dp, it) },
        tonalElevation = if (variant == AppBtnVariant.PRIMARY) El.card else El.none,
        modifier = mod.scale(scale)
    ) {
        Row(
            Modifier.height(height).padding(horizontal = hPad),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = fg
                )
                Spacer(Modifier.width(Sp.sm))
            } else if (icon != null) {
                Icon(icon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Sp.sm))
            }
            Text(
                text,
                style = txtStyle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// APP CARD — outlined / elevated
// ═══════════════════════════════════════════════════════════
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    outlined: Boolean = false,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = Rd.card
    val baseMod = modifier
        .fillMaxWidth()
        .clip(shape)
        .then(
            if (outlined)
                Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            else
                Modifier.shadow(El.card, shape, clip = false)
        )
        .background(color, shape)

    Column(
        modifier = (if (onClick != null) baseMod.clickable(onClick = onClick) else baseMod)
            .padding(Sp.cardInner),
        content = content
    )
}

// ═══════════════════════════════════════════════════════════
// ICON CIRCLE
// ═══════════════════════════════════════════════════════════
@Composable
fun IconCircle(
    icon: ImageVector,
    tint: Color = BRAND,
    bgAlpha: Float = 0.12f,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = bgAlpha)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

// ═══════════════════════════════════════════════════════════
// APP CHIP
// ═══════════════════════════════════════════════════════════
@Composable
fun AppChip(
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    leadingEmoji: String? = null,
    trailingCount: Int? = null,
    color: Color = BRAND
) {
    Surface(
        onClick = onClick,
        shape = Rd.chip,
        color = if (selected) color else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) null
        else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = if (selected) El.card else El.none
    ) {
        Row(
            Modifier.padding(horizontal = Sp.md, vertical = Sp.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leadingEmoji != null) {
                Text(leadingEmoji, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (trailingCount != null) {
                Surface(
                    color = if (selected) Color.White.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                ) {
                    Text(
                        trailingCount.toString(),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// SECTION HEADER
// ═══════════════════════════════════════════════════════════
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Sp.screenX, vertical = Sp.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = BRAND
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        action?.invoke()
    }
}

// ═══════════════════════════════════════════════════════════
// EMPTY STATE
// ═══════════════════════════════════════════════════════════
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    ctaLabel: String? = null,
    onCta: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Sp.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            BRAND.copy(alpha = 0.12f),
                            BRAND.copy(alpha = 0.04f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, null,
                Modifier.size(44.dp),
                tint = BRAND.copy(alpha = 0.65f)
            )
        }
        Spacer(Modifier.height(Sp.lg))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(Sp.xs))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (ctaLabel != null && onCta != null) {
            Spacer(Modifier.height(Sp.xl))
            AppButton(
                text = ctaLabel,
                onClick = onCta,
                variant = AppBtnVariant.PRIMARY,
                size = AppBtnSize.MEDIUM
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// STAT TILE
// ═══════════════════════════════════════════════════════════
@Composable
fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color = BRAND,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(icon, tint = color, size = 44.dp, iconSize = 22.dp)
            Spacer(Modifier.width(Sp.md))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// SHIMMER
// ═══════════════════════════════════════════════════════════
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = Rd.card
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    Box(
        modifier
            .clip(shape)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.08f)
            )
    )
}

// ═══════════════════════════════════════════════════════════
// APP DIVIDER
// ═══════════════════════════════════════════════════════════
@Composable
fun AppDivider(
    modifier: Modifier = Modifier,
    paddingStart: Dp = 0.dp
) {
    HorizontalDivider(
        modifier = modifier.padding(start = paddingStart),
        thickness = 0.8.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

// ═══════════════════════════════════════════════════════════
// APP BADGE
// ═══════════════════════════════════════════════════════════
@Composable
fun AppBadge(
    text: String,
    color: Color = BRAND,
    filled: Boolean = true
) {
    Surface(
        color = if (filled) color else color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(Rd.xs),
        contentColor = if (filled) Color.White else color
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
