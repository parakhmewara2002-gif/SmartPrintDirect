package com.smartprint.direct.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartprint.direct.data.PrintHistoryDb
import com.smartprint.direct.data.PrintHistoryItem
import com.smartprint.direct.hp.HpAuthenticProtocol
import com.smartprint.direct.hp.HpDeviceDetails
import com.smartprint.direct.hp.HpMdnsDiscovery
import com.smartprint.direct.hp.HpPrintEngine
import com.smartprint.direct.model.*
import com.smartprint.direct.network.DualNetworkRouter
import com.smartprint.direct.network.DualNetworkState
import com.smartprint.direct.update.InAppUpdateManager
import com.smartprint.direct.update.UpdateInfo
import com.smartprint.direct.util.PdfBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class SmartPrintViewModel(application: Application) : AndroidViewModel(application) {

    val router = DualNetworkRouter(application)
    private val discovery = HpMdnsDiscovery(application, router)
    private val printEngine = HpPrintEngine(application, router, discovery.usbManager)
    private val historyDb = PrintHistoryDb(application)
    val authenticProtocol = HpAuthenticProtocol(router)
    val updateManager = InAppUpdateManager(application)

    val networkState: StateFlow<DualNetworkState> = router.networkState
    val discoveredPrinters: StateFlow<List<HpPrinter>> = discovery.printers
    val isScanning: StateFlow<Boolean> = discovery.isDiscovering
    val updateState: StateFlow<UpdateInfo?> = updateManager.updateState
    val isDownloadingUpdate: StateFlow<Boolean> = updateManager.isDownloading

    private val _selectedPrinter = MutableStateFlow<HpPrinter?>(null)
    val selectedPrinter: StateFlow<HpPrinter?> = _selectedPrinter.asStateFlow()

    private val _deviceDetails = MutableStateFlow<HpDeviceDetails?>(null)
    val deviceDetails: StateFlow<HpDeviceDetails?> = _deviceDetails.asStateFlow()

    private val _printStatus = MutableStateFlow<String?>(null)
    val printStatus: StateFlow<String?> = _printStatus.asStateFlow()

    private val _history = MutableStateFlow<List<PrintHistoryItem>>(emptyList())
    val history: StateFlow<List<PrintHistoryItem>> = _history.asStateFlow()

    // One job at a time: a printer cannot take concurrent connections (multi-share / double tap)
    private val printMutex = Mutex()
    private var detailsJob: Job? = null

    init {
        router.startRouting()
        discovery.startDiscovery()
        loadHistory()

        viewModelScope.launch {
            discoveredPrinters.collect { list ->
                val sel = _selectedPrinter.value
                if (sel == null) {
                    list.firstOrNull()?.let { selectPrinter(it) }
                } else {
                    // Keep the selection's protocol/port in sync with the best discovered entry
                    val updated = list.firstOrNull { it.id == sel.id }
                    if (updated != null && (updated.protocol != sel.protocol || updated.port != sel.port)) {
                        _selectedPrinter.update { cur ->
                            if (cur?.id == sel.id) cur.copy(protocol = updated.protocol, port = updated.port, ippPort = updated.ippPort) else cur
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            updateManager.downloadError.collect { err -> if (err != null) _printStatus.value = err }
        }

        checkForUpdates()
    }

    fun checkForUpdates() {
        viewModelScope.launch { updateManager.checkForUpdates() }
    }

    fun startUpdateDownload() {
        val update = updateState.value ?: return
        updateManager.startUpdateDownload(update.downloadUrl, update.expectedSha256)
    }

    fun refreshPrinters() {
        discovery.stopDiscovery()
        discovery.startDiscovery()
        refreshCurrentPrinterDetails()
    }

    fun selectPrinter(printer: HpPrinter) {
        _selectedPrinter.value = printer
        _deviceDetails.value = null
        refreshCurrentPrinterDetails()
    }

    fun addManualPrinter(ip: String, name: String, brand: PrinterBrand = PrinterBrand.HP, protocol: PrintProtocol = PrintProtocol.RAW_9100) {
        val host = ip.trim()
        val valid = Patterns.IP_ADDRESS.matcher(host).matches() ||
            Regex("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$").matches(host)
        if (!valid) {
            _printStatus.value = "Invalid printer address: $ip"
            return
        }
        val manual = HpPrinter(
            id = host,
            name = name.trim().ifBlank { "Network Printer" },
            model = "${brand.displayName} Printer",
            brand = brand,
            protocol = protocol,
            ipAddress = host,
            port = protocol.defaultPort,
            isOnline = true
        )
        discovery.addManual(manual)
        selectPrinter(manual)
    }

    fun refreshCurrentPrinterDetails() {
        val p = _selectedPrinter.value ?: return
        if (p.connectionType == ConnectionType.USB_OTG) return // no HTTP endpoint on a USB cable
        detailsJob?.cancel()
        detailsJob = viewModelScope.launch {
            try {
                val levels = authenticProtocol.queryConsumableLevels(p.ipAddress)
                val state = authenticProtocol.queryDeviceStatus(p.ipAddress)
                val details = authenticProtocol.queryDeviceDetails(p.ipAddress)

                // Apply only if this printer is still the selected one (user may have switched meanwhile)
                _selectedPrinter.update { cur ->
                    if (cur?.id == p.id) {
                        cur.copy(
                            inkLevels = levels,
                            state = state,
                            isOnline = state != PrinterState.OFFLINE,
                            model = details?.makeAndModel?.ifBlank { null } ?: cur.model
                        )
                    } else cur
                }
                if (_selectedPrinter.value?.id == p.id) _deviceDetails.value = details
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SmartPrintVM", "Could not fetch printer details: ${e.message}")
            }
        }
    }

    private fun displayName(uri: Uri, fallback: String): String = try {
        getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: fallback
    } catch (_: Exception) {
        fallback
    }

    fun printDocument(
        uri: Uri,
        title: String,
        copies: Int = 1,
        colorMode: ColorMode = ColorMode.COLOR,
        paperSize: PaperSize = PaperSize.A4,
        borderless: Boolean = false
    ) {
        val printer = _selectedPrinter.value
        if (printer == null) {
            _printStatus.value = "Error: No printer selected!"
            return
        }

        viewModelScope.launch {
            printMutex.withLock {
                _printStatus.value = "Preparing document..."
                var tempPdf: File? = null
                try {
                    val app = getApplication<Application>()
                    val prepared: Result<java.io.InputStream> = withContext(Dispatchers.IO) {
                        val mime = app.contentResolver.getType(uri).orEmpty()
                        when {
                            mime.startsWith("image/") -> {
                                // Printers expect PDF; raw JPEG/PNG bytes print as garbage or nothing
                                val out = File.createTempFile("img_", ".pdf", app.cacheDir)
                                tempPdf = out
                                val ok = PdfBuilder.imageToPdf(app, uri, paperSize, borderless, out)
                                if (ok == null) Result.failure(Exception("Could not read the image"))
                                else Result.success(FileInputStream(out))
                            }
                            else -> {
                                val stream = app.contentResolver.openInputStream(uri)
                                    ?: return@withContext Result.failure(Exception("Cannot open the file"))
                                val head = ByteArray(5)
                                val buffered = java.io.BufferedInputStream(stream)
                                buffered.mark(8)
                                val n = buffered.read(head)
                                buffered.reset()
                                if (n < 5 || String(head, Charsets.ISO_8859_1) != "%PDF-") {
                                    buffered.close()
                                    Result.failure(Exception("Only PDF files and images can be printed"))
                                } else Result.success(buffered)
                            }
                        }
                    }

                    val stream = prepared.getOrElse {
                        _printStatus.value = "Print error: ${it.message}"
                        return@withLock
                    }

                    _printStatus.value = "Sending document to ${printer.name}..."
                    val job = PrintJob(title = title, documentUriString = uri.toString(), copies = copies, colorMode = colorMode, paperSize = paperSize)
                    val result = printEngine.printUniversal(printer, job, stream)
                    if (result.isSuccess) {
                        _printStatus.value = "Document sent to ${printer.name}"
                        addHistory(title, printer.name, 1, "Completed")
                    } else {
                        _printStatus.value = "Print error: ${result.exceptionOrNull()?.message}"
                        addHistory(title, printer.name, 1, "Failed")
                    }
                } catch (e: Exception) {
                    _printStatus.value = "Print failed: ${e.message}"
                } finally {
                    tempPdf?.delete()
                }
            }
        }
    }

    /** Shared files arrive without a title; resolve the real file name. */
    fun printSharedUri(uri: Uri) = printDocument(uri, displayName(uri, "Shared Document"))

    fun fileDisplayName(uri: Uri, fallback: String) = displayName(uri, fallback)

    /** Direct copy needs the printer's scanner (eSCL) which this app does not drive yet - say so honestly. */
    fun executeDirectCopy(copies: Int, isColor: Boolean) {
        if (_selectedPrinter.value == null) {
            _printStatus.value = "Error: No printer selected!"
            return
        }
        _printStatus.value = "Direct Copy is not available yet (needs printer scanner support). Use Print ID Card or Camera Scan."
    }

    /** Camera scan: pages -> one PDF in app storage, history entry, and the PDF to share. */
    fun saveScan(pages: List<File>, onSaved: (File?) -> Unit) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val out = withContext(Dispatchers.IO) {
                val dir = File(app.filesDir, "scans").apply { mkdirs() }
                PdfBuilder.scansToPdf(pages, File(dir, "scan_${System.currentTimeMillis()}.pdf"))
            }
            if (out == null) {
                _printStatus.value = "Could not save the scan"
            } else {
                _printStatus.value = "Scan saved (${pages.size} page${if (pages.size == 1) "" else "s"})"
                addHistory(out.name, "Camera Scan", pages.size, "Saved")
            }
            pages.forEach { it.delete() }
            onSaved(out)
        }
    }

    fun printDiagnosticReport(reportName: String) {
        val printer = _selectedPrinter.value
        if (printer == null) {
            _printStatus.value = "Select printer first"
            return
        }
        viewModelScope.launch {
            _printStatus.value = "Requesting $reportName..."
            val result = printEngine.sendPjl(printer, "@PJL INFO STATUS")
            if (result.isSuccess) {
                _printStatus.value = "$reportName request sent (some printers only print reports from their own menu)"
            } else {
                _printStatus.value = "Could not reach printer: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearStatus() {
        _printStatus.value = null
    }

    private fun addHistory(title: String, printer: String, pages: Int, status: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                historyDb.addHistory(title, printer, pages, status)
                _history.value = historyDb.getHistory()
            } catch (e: Exception) {
                Log.w("SmartPrintVM", "History write failed: ${e.message}")
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try { _history.value = historyDb.getHistory() } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        discovery.close()
        router.release()
    }
}
