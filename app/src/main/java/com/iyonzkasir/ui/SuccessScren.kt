package com.iyonzkasir.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iyonzkasir.*
import com.iyonzkasir.data.*

// ═══════════════════════════════════════════════════════════
// SUCCESS SCREEN — muncul setelah transaksi selesai
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuccessScreen(
    order: Order,
    onPrint: () -> Unit,
    onPreview: () -> Unit,
    onShare: () -> Unit,
    onNewTransaction: () -> Unit,
    onDone: () -> Unit
) {
    val printerEnabled = FeatureManager.isEnabled(FeatureKey.PRINTER_BT)
    val waEnabled = FeatureManager.isEnabled(FeatureKey.WHATSAPP_INTENT)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = Sp.xl, vertical = Sp.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Sp.xl))

            // ═══ SUCCESS ICON — dengan glow effect ═══
            Box(
                Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                SUCCESS.copy(alpha = 0.18f),
                                SUCCESS.copy(alpha = 0.04f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(SUCCESS, Color(0xFF3DD166))
                            )
                        )
                        .shadow(
                            elevation = 16.dp,
                            shape = CircleShape,
                            ambientColor = SUCCESS,
                            spotColor = SUCCESS
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check, null,
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(Modifier.height(Sp.xl))

            // ═══ TITLE ═══
            Text(
                "Transaksi Berhasil!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(Sp.xs))
            Text(
                "Data telah tersimpan ke sistem",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Sp.xxl))

            // ═══ SUMMARY CARD ═══
            AppCard {
                // Order ID + Antrian
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "No. Order",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "#${order.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (order.nomorAntrian > 0) {
                    Spacer(Modifier.height(Sp.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ConfirmationNumber, null,
                            tint = BRAND,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "No. Antrian",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "#${order.nomorAntrian}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BRAND
                        )
                    }
                }

                AppDivider(Modifier.padding(vertical = Sp.md))

                // Total + Dibayar
                Row {
                    Text(
                        "Total Harga",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        order.total.rupiah(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(Sp.xs))
                Row {
                    Text(
                        "Dibayar",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        order.dibayar.rupiah(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(Sp.md))

                // ═══ KEMBALIAN — HIGHLIGHT ═══
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Rd.md))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF1976D2),
                                    Color(0xFF1565C0)
                                )
                            )
                        )
                        .padding(Sp.lg)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Payments, null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(Sp.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Kembalian",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Text(
                                order.kembalian.rupiah(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ═══ ACTION BUTTONS ROW ═══
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButtonNew(
                    icon = Icons.Default.Check,
                    label = "Selesai",
                    color = SUCCESS,
                    onClick = onDone
                )
                if (printerEnabled) {
                    ActionButtonNew(
                        icon = Icons.Default.Print,
                        label = "Cetak",
                        color = BRAND,
                        onClick = onPrint
                    )
                }
                ActionButtonNew(
                    icon = Icons.Default.Receipt,
                    label = "Preview",
                    color = Color(0xFF43A047),
                    onClick = onPreview
                )
                if (waEnabled) {
                    ActionButtonNew(
                        icon = Icons.Default.Share,
                        label = "Bagikan",
                        color = Color(0xFF1E88E5),
                        onClick = onShare
                    )
                }
            }

            Spacer(Modifier.height(Sp.lg))

            // ═══ BIG CTA — BUAT TRANSAKSI BARU ═══
            AppButton(
                text = "Buat Transaksi Baru",
                onClick = onNewTransaction,
                variant = AppBtnVariant.PRIMARY,
                size = AppBtnSize.LARGE,
                icon = Icons.Default.Add,
                fullWidth = true
            )

            Spacer(Modifier.height(Sp.sm))
        }
    }
}

// ═══════════════════════════════════════════════════════════
// ACTION BUTTON — icon circle + label
// ═══════════════════════════════════════════════════════════
@Composable
private fun ActionButtonNew(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(100),
        label = "actionScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(Rd.md))
            .clickable {
                pressed = !pressed
                onClick()
            }
            .padding(horizontal = Sp.sm, vertical = Sp.sm)
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            color.copy(alpha = 0.18f),
                            color.copy(alpha = 0.1f)
                        )
                    )
                )
                .border(
                    1.dp,
                    color.copy(alpha = 0.2f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, null,
                tint = color,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(Sp.xs))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            textAlign = TextAlign.Center
        )
    }
}
