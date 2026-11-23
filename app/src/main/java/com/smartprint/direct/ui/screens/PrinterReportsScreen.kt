package com.smartprint.direct.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprint.direct.ui.theme.HpBlue
import com.smartprint.direct.ui.viewmodel.SmartPrintViewModel

data class ReportOption(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val command: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterReportsScreen(
    viewModel: SmartPrintViewModel,
    onBack: () -> Unit
) {
    val reports = listOf(
        ReportOption(
            title = "Printer Status Report",
            description = "Prints page count, firmware version, and error logs",
            icon = Icons.Default.Info,
            command = "Status_Report"
        ),
        ReportOption(
            title = "Network Configuration Page",
            description = "Prints Wi-Fi IP address, SSID, MAC, and Gateway details",
            icon = Icons.Default.Wifi,
            command = "Network_Config_Page"
        ),
        ReportOption(
            title = "Print Quality Diagnostic Page",
            description = "Diagnoses missing colors, streaks, and printhead nozzles",
            icon = Icons.Default.Palette,
            command = "Print_Quality_Diagnostic"
        ),
        ReportOption(
            title = "Wireless Network Test Report",
            description = "Tests Wi-Fi signal strength and channel interference",
            icon = Icons.Default.NetworkCheck,
            command = "Wireless_Test_Report"
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Printer Reports & Tools", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF7F9FA)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "INTERNAL PRINTER DIAGNOSTICS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            items(reports.size) { index ->
                val item = reports[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.printDiagnosticReport(item.title)
                            onBack()
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEAF5FB),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(item.icon, contentDescription = null, tint = HpBlue)
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0A2240))
                            Text(item.description, fontSize = 12.sp, color = Color(0xFF6B7280))
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
                    }
                }
            }
        }
    }
}
