package com.smartprint.direct.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprint.direct.model.HpPrinter
import com.smartprint.direct.ui.theme.HpBlue
import com.smartprint.direct.ui.theme.StatusGreen

@Composable
fun HpHeroPrinterCard(
    printer: HpPrinter?,
    onSelectPrinterClick: () -> Unit,
    onRefreshStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEAF5FB)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Printer",
                            tint = Color(printer?.brand?.badgeColor ?: 0xFF0096D6L),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = printer?.name ?: "No Printer Selected",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0A2240)
                            )
                            if (printer != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(printer.brand.badgeColor),
                                    modifier = Modifier.padding(2.dp)
                                ) {
                                    Text(
                                        text = printer.brand.displayName,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (printer?.isOnline == true) StatusGreen else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (printer?.isOnline == true) "Ready · ${printer.ipAddress} (${printer.protocol.name})" else "Offline · Tap to scan",
                                fontSize = 11.sp,
                                color = if (printer?.isOnline == true) Color(0xFF059669) else Color(0xFF6B7280)
                            )
                        }
                    }
                }

                IconButton(onClick = onRefreshStatus) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = HpBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = Color(0xFFF3F4F6), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Ink Gauge
            if (printer != null && printer.inkLevels.isNotEmpty()) {
                HpInkGaugeBar(cartridges = printer.inkLevels)
            } else {
                Text(
                    text = "Tap to discover HP, Canon, Epson, Brother printers on Wi-Fi/USB",
                    fontSize = 13.sp,
                    color = HpBlue,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { onSelectPrinterClick() }
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}
