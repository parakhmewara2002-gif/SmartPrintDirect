package com.smartprint.direct.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprint.direct.model.ColorMode
import com.smartprint.direct.ui.theme.HpBlue
import com.smartprint.direct.ui.theme.StatusGreen
import com.smartprint.direct.ui.viewmodel.SmartPrintViewModel
import com.smartprint.direct.util.IdCardPdfGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdCardPrintScreen(
    viewModel: SmartPrintViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var frontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isColor by remember { mutableStateOf(false) } // Default B&W Xerox Photocopy
    var copies by remember { mutableStateOf(1) }
    var activeSlot by remember { mutableStateOf<String?>("front") } // "front" or "back"

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bmp = IdCardPdfGenerator.uriToBitmap(context, uri)
            if (bmp != null) {
                if (activeSlot == "front") {
                    frontBitmap = bmp
                    activeSlot = if (backBitmap == null) "back" else null
                } else if (activeSlot == "back") {
                    backBitmap = bmp
                    activeSlot = null
                }
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            if (activeSlot == "front") {
                frontBitmap = bmp
                activeSlot = if (backBitmap == null) "back" else null
            } else if (activeSlot == "back") {
                backBitmap = bmp
                activeSlot = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ID Card Print (Front + Back)", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Info Pill
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFEAF5FB),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Badge, contentDescription = null, tint = HpBlue)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Prints Front & Back of Aadhaar / PAN / License on 1 single page like a photocopy.",
                        fontSize = 12.sp,
                        color = Color(0xFF0A2240),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 1. FRONT SIDE CAPTURE CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CreditCard, contentDescription = null, tint = HpBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("1. ID Card Front (आगे का भाग)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        if (frontBitmap != null) {
                            Text("Ready ✓", color = StatusGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (frontBitmap != null) {
                        Image(
                            bitmap = frontBitmap!!.asImageBitmap(),
                            contentDescription = "Front Side",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = {
                                activeSlot = "front"
                                takePictureLauncher.launch(null)
                            }) {
                                Text("Retake Camera", fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                activeSlot = "front"
                                imagePicker.launch("image/*")
                            }) {
                                Text("Pick Gallery", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    activeSlot = "front"
                                    takePictureLauncher.launch(null)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan Camera")
                            }
                            OutlinedButton(
                                onClick = {
                                    activeSlot = "front"
                                    imagePicker.launch("image/*")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("From Gallery")
                            }
                        }
                    }
                }
            }

            // 2. BACK SIDE CAPTURE CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FlipToBack, contentDescription = null, tint = HpBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("2. ID Card Back (पीछे का भाग)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        if (backBitmap != null) {
                            Text("Ready ✓", color = StatusGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (backBitmap != null) {
                        Image(
                            bitmap = backBitmap!!.asImageBitmap(),
                            contentDescription = "Back Side",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = {
                                activeSlot = "back"
                                takePictureLauncher.launch(null)
                            }) {
                                Text("Retake Camera", fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                activeSlot = "back"
                                imagePicker.launch("image/*")
                            }) {
                                Text("Pick Gallery", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    activeSlot = "back"
                                    takePictureLauncher.launch(null)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan Camera")
                            }
                            OutlinedButton(
                                onClick = {
                                    activeSlot = "back"
                                    imagePicker.launch("image/*")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("From Gallery")
                            }
                        }
                    }
                }
            }

            // 3. SETTINGS: COLOR / XEROX B&W + COPIES
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("PRINT OUTPUT STYLE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Print Style", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !isColor,
                                onClick = { isColor = false },
                                label = { Text("B&W Xerox", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = isColor,
                                onClick = { isColor = true },
                                label = { Text("Original Color", fontSize = 12.sp) }
                            )
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Number of Copies", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (copies > 1) copies-- }) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = HpBlue)
                            }
                            Text("$copies", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp))
                            IconButton(onClick = { copies++ }) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = HpBlue)
                            }
                        }
                    }
                }
            }

            // 4. ACTION PRINT BUTTON
            val isReady = (frontBitmap != null && backBitmap != null)

            Button(
                onClick = {
                    if (frontBitmap != null && backBitmap != null) {
                        val pdfUri = IdCardPdfGenerator.createIdCardA4Pdf(
                            context = context,
                            frontBitmap = frontBitmap!!,
                            backBitmap = backBitmap!!,
                            isColor = isColor
                        )
                        viewModel.printDocument(
                            uri = pdfUri,
                            title = "ID_Card_2in1_Copy.pdf",
                            copies = copies,
                            colorMode = if (isColor) ColorMode.COLOR else ColorMode.MONOCHROME
                        )
                        onBack()
                    }
                },
                enabled = isReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HpBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isReady) "Print ID Card (Front + Back) Now" else "Please Capture Front & Back",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
