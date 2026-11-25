package com.smartprint.direct.hp

import android.util.Log
import com.smartprint.direct.model.InkCartridge
import java.io.InputStream
import java.util.regex.Pattern

object HpInkStatusParser {

    /**
     * Parses HP ProductStatusCap.xml or ConsumableConfig.xml returned by HP printer Embedded Web Server (EWS).
     */
    fun parseEwsXml(inputStream: InputStream): List<InkCartridge> {
        val cartridges = mutableListOf<InkCartridge>()
        try {
            val xmlText = inputStream.bufferedReader().use { it.readText() }

            // HP EWS XML standard markers
            // Look for <ConsumableLabelCode>C, M, Y, K</ConsumableLabelCode> and <ConsumablePercentageLevelRemaining>
            val pattern = Pattern.compile(
                "<(?:\\w+:)?ConsumableLabelCode>(.*?)</(?:\\w+:)?ConsumableLabelCode>.*?<(?:\\w+:)?ConsumablePercentageLevelRemaining>(\\d+)</(?:\\w+:)?ConsumablePercentageLevelRemaining>",
                Pattern.DOTALL
            )
            val matcher = pattern.matcher(xmlText)
            while (matcher.find()) {
                val code = matcher.group(1)?.trim() ?: ""
                val level = (matcher.group(2)?.toIntOrNull() ?: continue).coerceIn(0, 100)
                when {
                    code.contains("K", ignoreCase = true) || code.contains("Black", ignoreCase = true) ->
                        cartridges.add(InkCartridge("Black", 0xFF1A1A1AL, level))
                    code.contains("C", ignoreCase = true) || code.contains("Cyan", ignoreCase = true) ->
                        cartridges.add(InkCartridge("Cyan", 0xFF00AEEFL, level))
                    code.contains("M", ignoreCase = true) || code.contains("Magenta", ignoreCase = true) ->
                        cartridges.add(InkCartridge("Magenta", 0xFFEC008CL, level))
                    code.contains("Y", ignoreCase = true) || code.contains("Yellow", ignoreCase = true) ->
                        cartridges.add(InkCartridge("Yellow", 0xFFFFF100L, level))
                }
            }

            if (cartridges.isEmpty()) {
                // Secondary fallback pattern for HP DeskJet / Ink Advantage XML format
                val fallbackPattern = Pattern.compile("<(?:\\w+:)?ConsumablePercentageLevelRemaining>(\\d+)</(?:\\w+:)?ConsumablePercentageLevelRemaining>")
                val fbMatcher = fallbackPattern.matcher(xmlText)
                val levels = mutableListOf<Int>()
                while (fbMatcher.find()) {
                    fbMatcher.group(1)?.toIntOrNull()?.let { levels.add(it) }
                }
                if (levels.size >= 4) {
                    cartridges.add(InkCartridge("Black", 0xFF1A1A1AL, levels[0]))
                    cartridges.add(InkCartridge("Cyan", 0xFF00AEEFL, levels[1]))
                    cartridges.add(InkCartridge("Magenta", 0xFFEC008CL, levels[2]))
                    cartridges.add(InkCartridge("Yellow", 0xFFFFF100L, levels[3]))
                } else if (levels.size == 2) {
                    // Tricolor + Black
                    cartridges.add(InkCartridge("Black", 0xFF1A1A1AL, levels[0]))
                    cartridges.add(InkCartridge("Tri-Color", 0xFF00AEEFL, levels[1]))
                }
            }
        } catch (e: Exception) {
            Log.w("HpInkStatusParser", "Error parsing HP XML", e)
        }

        return cartridges // empty = unknown; the UI hides the gauge
    }
}
