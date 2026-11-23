package com.smartprint.direct.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartprint.direct.model.InkCartridge

@Composable
fun HpInkGaugeBar(
    cartridges: List<InkCartridge>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "ESTIMATED SUPPLY LEVELS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6B7280),
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            cartridges.forEach { ink ->
                InkTankItem(
                    ink = ink,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun InkTankItem(
    ink: InkCartridge,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tank Visual
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFE5E7EB)),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Liquid Fill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(ink.levelPercentage / 100f)
                    .background(Color(ink.hexColor))
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${ink.levelPercentage}%",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F2937)
        )
        Text(
            text = ink.colorName,
            fontSize = 10.sp,
            color = Color(0xFF6B7280)
        )
    }
}
