package com.smartprint.direct.ui.screens

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
import com.smartprint.direct.ui.theme.HpBlue
import com.smartprint.direct.ui.viewmodel.SmartPrintViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectCopyScreen(
    viewModel: SmartPrintViewModel,
    onBack: () -> Unit
) {
    var copyCount by remember { mutableStateOf(1) }
    var isColor by remember { mutableStateOf(true) }
    var is2Sided by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Direct Copy", fontWeight = FontWeight.Bold) },
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "NUMBER OF COPIES",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            IconButton(
                                onClick = { if (copyCount > 1) copyCount-- },
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    Icons.Default.RemoveCircle,
                                    contentDescription = "Decrease",
                                    tint = HpBlue,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Text(
                                text = "$copyCount",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            IconButton(
                                onClick = { if (copyCount < 99) copyCount++ },
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    Icons.Default.AddCircle,
                                    contentDescription = "Increase",
                                    tint = HpBlue,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Output Color", fontWeight = FontWeight.Medium)
                            Row {
                                FilterChip(
                                    selected = isColor,
                                    onClick = { isColor = true },
                                    label = { Text("Color Copy") }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FilterChip(
                                    selected = !isColor,
                                    onClick = { isColor = false },
                                    label = { Text("Black & White") }
                                )
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("2-Sided Duplex Copy", fontWeight = FontWeight.Medium)
                            Switch(
                                checked = is2Sided,
                                onCheckedChange = { is2Sided = it }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.executeDirectCopy(copyCount, isColor)
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HpBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Copy ($copyCount)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
