package com.smartprint.direct.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprint.direct.model.HpPrinter
import com.smartprint.direct.model.PrinterBrand
import com.smartprint.direct.model.PrintProtocol
import com.smartprint.direct.ui.theme.HpBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSelectionDialog(
    printers: List<HpPrinter>,
    isScanning: Boolean,
    onSelectPrinter: (HpPrinter) -> Unit,
    onManualAdd: (ip: String, name: String, brand: PrinterBrand, protocol: PrintProtocol) -> Unit,
    onDismiss: () -> Unit
) {
    var showManualInput by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("Network Printer") }
    var selectedBrand by remember { mutableStateOf(PrinterBrand.HP) }
    var selectedProtocol by remember { mutableStateOf(PrintProtocol.RAW_9100) }
    var filterBrand by remember { mutableStateOf<PrinterBrand?>(null) }

    val filteredPrinters = if (filterBrand == null) printers else printers.filter { it.brand == filterBrand }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (showManualInput) "Add Printer by IP / Protocol" else "Available Printers (All Brands)",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            if (showManualInput) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        label = { Text("Printer IP Address (e.g. 192.168.1.150)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        label = { Text("Printer Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("SELECT PRINTER BRAND", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(PrinterBrand.HP, PrinterBrand.CANON, PrinterBrand.EPSON, PrinterBrand.BROTHER, PrinterBrand.PANTUM, PrinterBrand.SAMSUNG)) { b ->
                            FilterChip(
                                selected = selectedBrand == b,
                                onClick = {
                                    selectedBrand = b
                                    if (b == PrinterBrand.CANON) selectedProtocol = PrintProtocol.CANON_BJNP
                                    else selectedProtocol = PrintProtocol.RAW_9100
                                },
                                label = { Text(b.displayName, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("PROTOCOL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(PrintProtocol.RAW_9100, PrintProtocol.IPP_EVERYWHERE, PrintProtocol.CANON_BJNP, PrintProtocol.LPR_515)) { p ->
                            FilterChip(
                                selected = selectedProtocol == p,
                                onClick = { selectedProtocol = p },
                                label = { Text(p.name, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Brand Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = filterBrand == null,
                                onClick = { filterBrand = null },
                                label = { Text("All", fontSize = 11.sp) }
                            )
                        }
                        items(listOf(PrinterBrand.HP, PrinterBrand.CANON, PrinterBrand.EPSON, PrinterBrand.BROTHER, PrinterBrand.PANTUM)) { b ->
                            FilterChip(
                                selected = filterBrand == b,
                                onClick = { filterBrand = if (filterBrand == b) null else b },
                                label = { Text(b.displayName, fontSize = 11.sp) }
                            )
                        }
                    }

                    if (isScanning) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = HpBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scanning Wi-Fi & USB for all brands...", fontSize = 12.sp)
                        }
                    }

                    if (filteredPrinters.isEmpty() && !isScanning) {
                        Text(
                            text = "No printers detected. Ensure printer is turned on and connected to the same Wi-Fi or USB OTG.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(filteredPrinters) { printer ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectPrinter(printer)
                                            onDismiss()
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Print,
                                        contentDescription = null,
                                        tint = Color(printer.brand.badgeColor)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(printer.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(3.dp),
                                                color = Color(printer.brand.badgeColor)
                                            ) {
                                                Text(
                                                    text = printer.brand.displayName,
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text("${printer.ipAddress} · ${printer.protocol.displayName}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                                Divider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showManualInput) {
                Button(
                    onClick = {
                        if (manualIp.isNotBlank()) {
                            onManualAdd(manualIp.trim(), manualName.trim(), selectedBrand, selectedProtocol)
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HpBlue)
                ) {
                    Text("Connect")
                }
            } else {
                TextButton(onClick = { showManualInput = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Enter IP Manually")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
