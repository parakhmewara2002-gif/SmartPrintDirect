package com.smartprint.direct.model

enum class ConnectionType {
    WIFI_NETWORK,
    WIFI_DIRECT,
    USB_OTG
}

enum class PrinterBrand(val displayName: String, val badgeColor: Long) {
    HP("HP", 0xFF0096D6L),
    CANON("Canon", 0xFFCC0000L),
    EPSON("Epson", 0xFF003399L),
    BROTHER("Brother", 0xFF005BACL),
    SAMSUNG("Samsung", 0xFF1428A0L),
    PANTUM("Pantum", 0xFFE65100L),
    RICOH("Ricoh", 0xFFCF142BL),
    XEROX("Xerox", 0xFFD9272EL),
    GENERIC("Generic", 0xFF4B5563L)
}

enum class PrintProtocol(val defaultPort: Int, val displayName: String) {
    RAW_9100(9100, "RAW / JetDirect (Port 9100)"),
    IPP_EVERYWHERE(631, "IPP / AirPrint (Port 631)"),
    CANON_BJNP(8611, "Canon BJNP (Port 8611)"),
    LPR_515(515, "LPR / LPD (Port 515)"),
    USB_OTG(0, "USB OTG Direct")
}

enum class PrinterState {
    IDLE_READY,
    PRINTING,
    PAPER_JAM,
    OUT_OF_PAPER,
    DOOR_OPEN,
    OFFLINE
}

enum class ColorMode {
    COLOR,
    MONOCHROME
}

enum class PaperSize(val displayName: String, val widthMm: Int, val heightMm: Int) {
    A4("A4 (210 x 297 mm)", 210, 297),
    LETTER("Letter (8.5 x 11 in)", 216, 279),
    PHOTO_4X6("Photo 4x6 in (10x15 cm)", 102, 152),
    PHOTO_5X7("Photo 5x7 in (13x18 cm)", 127, 178),
    LEGAL("Legal (8.5 x 14 in)", 216, 356)
}

enum class PrintQuality {
    DRAFT,
    NORMAL,
    BEST
}

data class InkCartridge(
    val colorName: String,
    val hexColor: Long,
    val levelPercentage: Int, // 0 - 100
    val isLow: Boolean = levelPercentage <= 15
)

data class HpPrinter(
    val id: String,
    val name: String,
    val model: String = "Printer Device",
    val brand: PrinterBrand = PrinterBrand.HP,
    val protocol: PrintProtocol = PrintProtocol.RAW_9100,
    val ipAddress: String,
    val port: Int = 9100,
    val ippPort: Int = 631,
    val isOnline: Boolean = true,
    val state: PrinterState = PrinterState.IDLE_READY,
    val connectionType: ConnectionType = ConnectionType.WIFI_NETWORK,
    // Empty = unknown / not reported. Never invent supply levels.
    val inkLevels: List<InkCartridge> = emptyList()
)

data class PrintJob(
    val title: String,
    val documentUriString: String,
    val pageCount: Int = 1,
    val copies: Int = 1,
    val colorMode: ColorMode = ColorMode.COLOR,
    val paperSize: PaperSize = PaperSize.A4,
    val quality: PrintQuality = PrintQuality.NORMAL,
    val isDuplex: Boolean = false
)
