package com.smartprint.direct.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.smartprint.direct.model.PaperSize
import com.smartprint.direct.ui.theme.HpBlue
import com.smartprint.direct.ui.viewmodel.SmartPrintViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPrintScreen(
    viewModel: SmartPrintViewModel,
    onBack: () -> Unit
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPaperSize by remember { mutableStateOf(PaperSize.PHOTO_4X6) }
    var borderless by remember { mutableStateOf(true) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) selectedUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Print Photos", fontWeight = FontWeight.Bold) },
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
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = HpBlue,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (selectedUri != null) "Photo Ready for Printing" else "Select Photo from Gallery",
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { photoPickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = HpBlue)
                        ) {
                            Text("Choose Photo")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("PHOTO SIZES", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedPaperSize == PaperSize.PHOTO_4X6,
                                onClick = { selectedPaperSize = PaperSize.PHOTO_4X6 },
                                label = { Text("4x6 in") }
                            )
                            FilterChip(
                                selected = selectedPaperSize == PaperSize.PHOTO_5X7,
                                onClick = { selectedPaperSize = PaperSize.PHOTO_5X7 },
                                label = { Text("5x7 in") }
                            )
                            FilterChip(
                                selected = selectedPaperSize == PaperSize.A4,
                                onClick = { selectedPaperSize = PaperSize.A4 },
                                label = { Text("A4 Full") }
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Borderless Printing", fontWeight = FontWeight.Medium)
                            Switch(
                                checked = borderless,
                                onCheckedChange = { borderless = it }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    selectedUri?.let { uri ->
                        viewModel.printDocument(uri, "Photo_${System.currentTimeMillis()}", paperSize = selectedPaperSize, borderless = borderless)
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
                Text("Print Photo", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
