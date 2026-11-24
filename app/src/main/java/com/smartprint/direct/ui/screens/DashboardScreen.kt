package com.smartprint.direct.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprint.direct.ui.components.*
import com.smartprint.direct.ui.theme.HpBlue
import com.smartprint.direct.ui.theme.HpDarkBlue
import com.smartprint.direct.ui.viewmodel.SmartPrintViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: SmartPrintViewModel,
    onNavigateToDocumentPrint: () -> Unit,
    onNavigateToPhotoPrint: () -> Unit,
    onNavigateToCameraScan: () -> Unit,
    onNavigateToDirectCopy: () -> Unit,
    onNavigateToIdCardPrint: () -> Unit,
    onNavigateToReports: () -> Unit
) {
    val printer by viewModel.selectedPrinter.collectAsState()
    val printers by viewModel.discoveredPrinters.collectAsState()
    val networkState by viewModel.networkState.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val printStatus by viewModel.printStatus.collectAsState()
    val deviceDetails by viewModel.deviceDetails.collectAsState()
    val updateInfo by viewModel.updateState.collectAsState()
    val isDownloadingUpdate by viewModel.isDownloadingUpdate.collectAsState()

    var showPrinterDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = HpBlue,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "hp",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Smart Direct",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF0A2240)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (updateInfo?.hasUpdate == true) {
                            showUpdateDialog = true
                        } else {
                            viewModel.checkForUpdates()
                        }
                    }) {
                        BadgedBox(
                            badge = {
                                if (updateInfo?.hasUpdate == true) {
                                    Badge(containerColor = Color(0xFFEF4444))
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "Check for Updates",
                                tint = if (updateInfo?.hasUpdate == true) HpBlue else Color.Gray
                            )
                        }
                    }

                    IconButton(onClick = { showPrinterDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = "Add Printer",
                            tint = HpDarkBlue
                        )
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "dash_1", contentType = "card") {
                Spacer(modifier = Modifier.height(2.dp))
                DualNetworkStatusBar(networkState = networkState)
            }

            if (updateInfo?.hasUpdate == true) {
                item(key = "dash_2", contentType = "card") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEFF6FF),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = HpBlue)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Update Available (v${updateInfo?.latestVersionName})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF0A2240)
                                    )
                                    Text(
                                        text = updateInfo?.releaseNotes ?: "Tap to download and install",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        maxLines = 1
                                    )
                                }
                            }
                            if (isDownloadingUpdate) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = HpBlue)
                            } else {
                                Button(
                                    onClick = { viewModel.startUpdateDownload() },
                                    colors = ButtonDefaults.buttonColors(containerColor = HpBlue),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (printStatus != null) {
                item(key = "dash_3", contentType = "card") {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEFF6FF),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = printStatus ?: "",
                                fontSize = 13.sp,
                                color = HpDarkBlue,
                                fontWeight = FontWeight.Medium
                            )
                            IconButton(
                                onClick = { viewModel.clearStatus() },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = HpDarkBlue)
                            }
                        }
                    }
                }
            }

            item(key = "dash_4", contentType = "card") {
                HpHeroPrinterCard(
                    printer = printer,
                    onSelectPrinterClick = { showPrinterDialog = true },
                    onRefreshStatus = { viewModel.refreshPrinters() }
                )
            }

            if (deviceDetails != null && printer != null) {
                item(key = "dash_5", contentType = "card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "AUTHENTIC PRINTER HARDWARE STATUS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Firmware: ${deviceDetails?.firmwareVersion}", fontSize = 11.sp, color = Color.DarkGray)
                                    Text("Serial: ${deviceDetails?.serialNumber}", fontSize = 11.sp, color = Color.DarkGray)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Total Pages: ${deviceDetails?.totalImpressions}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = HpBlue)
                                    Text("Color: ${deviceDetails?.colorImpressions} · Mono: ${deviceDetails?.monoImpressions}", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }

            // NEW FEATURE: ID Card Print (Front + Back) Hero Banner
            item(key = "dash_6", contentType = "card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .iosBouncyClick { onNavigateToIdCardPrint() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFDE68A),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = "ID Card Copy",
                                    tint = Color(0xFFB45309),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ID Card Print (Front + Back)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF78350F)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFD97706)
                                ) {
                                    Text(
                                        text = "NEW",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Aadhaar, PAN & License dono sides 1 page par",
                                fontSize = 12.sp,
                                color = Color(0xFF92400E)
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFFB45309)
                        )
                    }
                }
            }

            // 2x2 Action Tiles
            item(key = "dash_7", contentType = "card") {
                Text(
                    text = "PRINT & SCAN SHORTCUTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6B7280),
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HpActionTile(
                        title = "Print Documents",
                        subtitle = "PDF, Docs, Text",
                        icon = Icons.Default.Description,
                        onClick = onNavigateToDocumentPrint,
                        modifier = Modifier.weight(1f)
                    )
                    HpActionTile(
                        title = "Print Photos",
                        subtitle = "Gallery, Borderless",
                        icon = Icons.Default.PhotoLibrary,
                        onClick = onNavigateToPhotoPrint,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item(key = "dash_8", contentType = "card") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HpActionTile(
                        title = "Camera Scan",
                        subtitle = "Document OCR & PDF",
                        icon = Icons.Default.DocumentScanner,
                        onClick = onNavigateToCameraScan,
                        modifier = Modifier.weight(1f)
                    )
                    HpActionTile(
                        title = "Direct Copy",
                        subtitle = "Instant Multi-Copy",
                        icon = Icons.Default.ContentCopy,
                        onClick = onNavigateToDirectCopy,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick Tools & Actions
            item(key = "dash_9", contentType = "card") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onNavigateToReports,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null, tint = HpBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Printer Reports", color = Color(0xFF0A2240), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { showPrinterDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = HpBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Switch Printer", color = Color(0xFF0A2240), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showPrinterDialog) {
        PrinterSelectionDialog(
            printers = printers,
            isScanning = isScanning,
            onSelectPrinter = { viewModel.selectPrinter(it) },
            onManualAdd = { ip, name, brand, protocol -> viewModel.addManualPrinter(ip, name, brand, protocol) },
            onDismiss = { showPrinterDialog = false }
        )
    }

    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Update Available", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Version: ${updateInfo?.latestVersionName}", fontWeight = FontWeight.SemiBold, color = HpBlue)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(updateInfo?.releaseNotes ?: "Performance improvements & bug fixes")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.startUpdateDownload()
                        showUpdateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HpBlue)
                ) {
                    Text("Download & Install")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Later")
                }
            }
        )
    }
}
