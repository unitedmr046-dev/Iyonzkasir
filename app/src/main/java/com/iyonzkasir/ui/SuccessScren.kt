package com.iyonzkasir.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iyonzkasir.*
import com.iyonzkasir.data.*
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════
// SUCCESS SCREEN — Full Page (setelah bayar)
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

    // Auto play sound saat muncul
    LaunchedEffect(Unit) {
        if (FeatureManager.isEnabled(FeatureKey.AUDIT_LOG)) {
            // placeholder — suara di-handle dari BayarRoute
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            // ═══ Big check icon ═══
            Box(
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(SUCCESS.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = SUCCESS,
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            // ═══ Title ═══
            Text(
                "Transaksi Berhasil!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Data telah disimpan ke sistem",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            // ═══ Summary Card ═══
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Order ID
                    Row {
                        Text("No. Order",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("#${order.id}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }

                    // Antrian kalau ada
                    if (order.nomorAntrian > 0) {
                        Row {
                            Text("No. Antrian",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("#${order.nomorAntrian}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = BRAND)
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp)

                    // Total
                    Row {
                        Text("Total Harga",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(order.total.rupiah(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }

                    // Dibayar
                    Row {
                        Text("Dibayar",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(order.dibayar.rupiah(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(thickness = 0.5.dp)

                    // ═══ Kembalian di-highlight ═══
                    Surface(
                        color = Color(0xFFE3F2FD),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Kembalian",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1976D2))
                            Text(order.kembalian.rupiah(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1565C0))
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ═══ 4 Action Buttons ═══
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(
                    icon = Icons.Default.Check,
                    label = "Selesai",
                    color = SUCCESS,
                    onClick = onDone
                )
                if (printerEnabled) {
                    ActionButton(
                        icon = Icons.Default.Print,
                        label = "Struk",
                        color = BRAND,
                        onClick = onPrint
                    )
                }
                ActionButton(
                    icon = Icons.Default.Receipt,
                    label = "Preview",
                    color = Color(0xFF43A047),
                    onClick = onPreview
                )
                if (waEnabled) {
                    ActionButton(
                        icon = Icons.Default.Share,
                        label = "Bagikan",
                        color = Color(0xFF1E88E5),
                        onClick = onShare
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ═══ Big "Buat Transaksi Baru" button ═══
            Button(
                onClick = onNewTransaction,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFB300)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "BUAT TRANSAKSI BARU",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            textAlign = TextAlign.Center)
    }
}
