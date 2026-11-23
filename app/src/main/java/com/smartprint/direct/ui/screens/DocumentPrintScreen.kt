package com.smartprint.direct.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.smartprint.direct.model.ColorMode
import com.smartprint.direct.model.PaperSize
import com.smartprint.direct.ui.theme.HpBlue
import com.smartprint.direct.ui.theme.HpDarkBlue
import com.smartprint.direct.ui.viewmodel.SmartPrintViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentPrintScreen(
    viewModel: SmartPrintViewModel,
    onBack: () -> Unit
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var documentName by remember { mutableStateOf("Selected Document.pdf") }
    var copies by remember { mutableStateOf(1) }
    var colorMode by remember { mutableStateOf(ColorMode.COLOR) }
    var paperSize by remember { mutableStateOf(PaperSize.A4) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            documentName = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Print Documents", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // File selector card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = HpBlue,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (selectedUri != null) documentName else "No file selected",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("application/pdf") },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = HpBlue)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Browse PDF Files")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Print settings
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("PRINT SETTINGS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Copies counter
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Copies", fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { if (copies > 1) copies-- }) {
                                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease", tint = HpBlue)
                                }
                                Text("$copies", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp))
                                IconButton(onClick = { copies++ }) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase", tint = HpBlue)
                                }
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        // Color Mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Color Output", fontWeight = FontWeight.Medium)
                            Row {
                                FilterChip(
                                    selected = colorMode == ColorMode.COLOR,
                                    onClick = { colorMode = ColorMode.COLOR },
                                    label = { Text("Color") }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FilterChip(
                                    selected = colorMode == ColorMode.MONOCHROME,
                                    onClick = { colorMode = ColorMode.MONOCHROME },
                                    label = { Text("Black & White") }
                                )
                            }
                        }
                    }
                }
            }

            // Print Button
            Button(
                onClick = {
                    selectedUri?.let { uri ->
                        viewModel.printDocument(uri, documentName, copies, colorMode)
                        onBack()
                    }
                },
                enabled = selectedUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HpBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Print Document Now", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
