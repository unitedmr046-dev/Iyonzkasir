package com.iyonzkasir.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iyonzkasir.BRAND

// ═══ SPACING ═══
object Sp {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

// ═══ RADIUS ═══
object Rd {
    val xs = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp

    val card = RoundedCornerShape(md)
    val cardLg = RoundedCornerShape(lg)
    val chip = RoundedCornerShape(sm)
    val button = RoundedCornerShape(md)
    val iconBox = RoundedCornerShape(14.dp)
}

// ═══ ELEVATION ═══
object El {
    val card = 1.dp
    val cardHover = 2.dp
    val fab = 4.dp
    val modal = 8.dp
    val none = 0.dp   // ⬅️ PATCH 2A
}

// ═══ DARK MODE SURFACE COLORS ═══  ⬅️ PATCH 2A
@Composable
fun surfaceCard(): Color = if (isSystemInDarkTheme())
    com.iyonzkasir.SURFACE_DARK else MaterialTheme.colorScheme.surface

@Composable
fun surfaceCardAlt(): Color = if (isSystemInDarkTheme())
    com.iyonzkasir.SURFACE_DARK_2 else MaterialTheme.colorScheme.surfaceVariant

@Composable
fun borderColor(): Color = if (isSystemInDarkTheme())
    com.iyonzkasir.BORDER_DARK else MaterialTheme.colorScheme.outlineVariant
// ⬆️ END PATCH 2A

// ═══ EMPTY STATE ═══
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
            Modifier.size(96.dp).clip(CircleShape)
                .background(BRAND.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(44.dp),
                tint = BRAND.copy(alpha = 0.55f))
        }
        Spacer(Modifier.height(Sp.lg))
        Text(title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(Sp.xs))
            Text(subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
        if (ctaLabel != null && onCta != null) {
            Spacer(Modifier.height(Sp.xl))
            Button(onClick = onCta,
                colors = ButtonDefaults.buttonColors(containerColor = BRAND),
                shape = Rd.button,
                contentPadding = PaddingValues(
                    horizontal = Sp.xl, vertical = Sp.md)) {
                Text(ctaLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ═══ SECTION HEADER ═══
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(title,
        modifier = modifier.padding(
            start = Sp.xl, end = Sp.xl,
            top = Sp.lg, bottom = Sp.sm),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = BRAND)
}
