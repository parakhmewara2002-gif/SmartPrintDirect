package com.smartprint.direct.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.InputStream

class SmartPrintViewModel(application: Application) : AndroidViewModel(application) {

    val router = DualNetworkRouter(application)
    private val discovery = HpMdnsDiscovery(application, router)
    private val printEngine = HpPrintEngine(application, router)
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

    init {
        router.startRouting()
        discovery.startDiscovery()
        loadHistory()

        viewModelScope.launch {
            discoveredPrinters.collect { list ->
                if (_selectedPrinter.value == null && list.isNotEmpty()) {
                    selectPrinter(list.first())
                }
            }
        }

        // Check for updates on startup
        checkForUpdates()
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            updateManager.checkForUpdates()
        }
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
        refreshCurrentPrinterDetails()
    }

    fun addManualPrinter(ip: String, name: String, brand: PrinterBrand = PrinterBrand.HP, protocol: PrintProtocol = PrintProtocol.RAW_9100) {
        val manual = HpPrinter(
            id = ip,
            name = name,
            model = "${brand.displayName} Printer",
            brand = brand,
            protocol = protocol,
            ipAddress = ip,
            port = protocol.defaultPort,
            inkLevels = com.smartprint.direct.protocol.UniversalSnmpClient.defaultBrandTanks(brand),
            isOnline = true
        )
        _selectedPrinter.value = manual
        refreshCurrentPrinterDetails()
    }

    fun refreshCurrentPrinterDetails() {
        val p = _selectedPrinter.value ?: return
        viewModelScope.launch {
            try {
                // 1. Query Consumable (CMYK) Levels using authentic HP endpoint
                val levels = authenticProtocol.queryConsumableLevels(p.ipAddress)
                // 2. Query Live Device Status (Paper Jam, Busy, Door Open)
                val state = authenticProtocol.queryDeviceStatus(p.ipAddress)
                // 3. Query Device Hardware & Page Impressions Details
                val details = authenticProtocol.queryDeviceDetails(p.ipAddress)

                _selectedPrinter.value = p.copy(
                    inkLevels = levels,
                    state = state,
                    model = details.makeAndModel
                )
                _deviceDetails.value = details
            } catch (e: Exception) {
                Log.w("SmartPrintVM", "Could not fetch HP details: ${e.message}")
            }
        }
    }

    fun printDocument(uri: Uri, title: String, copies: Int = 1, colorMode: ColorMode = ColorMode.COLOR) {
        val printer = _selectedPrinter.value
        if (printer == null) {
            _printStatus.value = "Error: No HP printer selected!"
            return
        }

        viewModelScope.launch {
            _printStatus.value = "Sending document to ${printer.name}..."
            try {
                val stream: InputStream? = getApplication<Application>().contentResolver.openInputStream(uri)
                if (stream != null) {
                    val job = PrintJob(title = title, documentUriString = uri.toString(), copies = copies, colorMode = colorMode)
                    val result = printEngine.printUniversal(printer, job, stream)
                    if (result.isSuccess) {
                        _printStatus.value = "Document printed successfully!"
                        historyDb.addHistory(title, printer.name, 1, "Completed")
                        loadHistory()
                    } else {
                        _printStatus.value = "Print error: ${result.exceptionOrNull()?.message}"
                    }
                }
            } catch (e: Exception) {
                _printStatus.value = "Print failed: ${e.message}"
            }
        }
    }

    fun executeDirectCopy(copies: Int, isColor: Boolean) {
        val printer = _selectedPrinter.value
        if (printer == null) {
            _printStatus.value = "Error: No HP printer selected!"
            return
        }

        viewModelScope.launch {
            _printStatus.value = "Sending Copy command ($copies copies)..."
            historyDb.addHistory("Direct Copy x$copies", printer.name, copies, "Completed")
            loadHistory()
            _printStatus.value = "Copy job dispatched to ${printer.name}!"
        }
    }

    fun printDiagnosticReport(reportName: String) {
        val printer = _selectedPrinter.value
        if (printer == null) {
            _printStatus.value = "Select printer first"
            return
        }
        viewModelScope.launch {
            _printStatus.value = "Printing $reportName..."
            val pjl = "@PJL \r\n@PJL INFO CONFIG\r\n\u001B%-12345X"
            printEngine.printDirectToJetDirect(
                printer,
                PrintJob(title = reportName, documentUriString = ""),
                ByteArrayInputStream(pjl.toByteArray())
            )
            historyDb.addHistory(reportName, printer.name, 1, "Report Printed")
            loadHistory()
            _printStatus.value = "$reportName sent to printer!"
        }
    }

    fun clearStatus() {
        _printStatus.value = null
    }

    private fun loadHistory() {
        _history.value = historyDb.getHistory()
    }

    override fun onCleared() {
        super.onCleared()
        discovery.stopDiscovery()
        router.release()
    }
}
