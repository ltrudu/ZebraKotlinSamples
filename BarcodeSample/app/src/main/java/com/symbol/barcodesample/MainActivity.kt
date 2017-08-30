package com.symbol.barcodesample

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import com.symbol.emdk.EMDKManager
import com.symbol.emdk.EMDKResults
import com.symbol.emdk.barcode.*
import java.util.ArrayList

class MainActivity : Activity(), EMDKManager.EMDKListener, Scanner.DataListener, Scanner.StatusListener, BarcodeManager.ScannerConnectionListener, CompoundButton.OnCheckedChangeListener {

    private var emdkManager: EMDKManager? = null
    private var barcodeManager: BarcodeManager? = null
    private var scanner: Scanner? = null

    private var bContinuousMode = false

    private var textViewData: TextView? = null
    private var textViewStatus: TextView? = null

    private var checkBoxEAN8: CheckBox? = null
    private var checkBoxEAN13: CheckBox? = null
    private var checkBoxCode39: CheckBox? = null
    private var checkBoxCode128: CheckBox? = null
    private var checkBoxContinuous: CheckBox? = null

    private var spinnerScannerDevices: Spinner? = null
    private var spinnerTriggers: Spinner? = null

    private var deviceList: List<ScannerInfo>? = null

    private var scannerIndex = 0 // Keep the selected scanner
    private var defaultIndex = 0 // Keep the default scanner
    private var triggerIndex = 0
    private var dataLength = 0
    private var statusString : String? = null;

    private val triggerStrings = arrayOf("HARD", "SOFT")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deviceList = ArrayList<ScannerInfo>()

        val results = EMDKManager.getEMDKManager(applicationContext, this)
        if (results.statusCode != EMDKResults.STATUS_CODE.SUCCESS) {
            textViewStatus!!.text = "Status: " + "EMDKManager object request failed!"
        }

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
        setDefaultOrientation()

        textViewData = findViewById<View>(R.id.textViewData) as TextView
        textViewStatus = findViewById<View>(R.id.textViewStatus) as TextView
        checkBoxEAN8 = findViewById<View>(R.id.checkBoxEAN8) as CheckBox
        checkBoxEAN13 = findViewById<View>(R.id.checkBoxEAN13) as CheckBox
        checkBoxCode39 = findViewById<View>(R.id.checkBoxCode39) as CheckBox
        checkBoxCode128 = findViewById<View>(R.id.checkBoxCode128) as CheckBox
        checkBoxContinuous = findViewById<View>(R.id.checkBoxContinuous) as CheckBox
        spinnerScannerDevices = findViewById<View>(R.id.spinnerScannerDevices) as Spinner
        spinnerTriggers = findViewById<View>(R.id.spinnerTriggers) as Spinner

        checkBoxEAN8!!.setOnCheckedChangeListener(this)
        checkBoxEAN13!!.setOnCheckedChangeListener(this)
        checkBoxCode39!!.setOnCheckedChangeListener(this)
        checkBoxCode128!!.setOnCheckedChangeListener(this)

        addSpinnerScannerDevicesListener()
        populateTriggers()
        addSpinnerTriggersListener()
        addStartScanButtonListener()
        addStopScanButtonListener()
        addCheckBoxListener()

        textViewData!!.isSelected = true
        textViewData!!.movementMethod = ScrollingMovementMethod()

    }


    private fun setDefaultOrientation() {

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rotation = windowManager.defaultDisplay.rotation


        val dm = DisplayMetrics()
        getWindowManager().defaultDisplay.getMetrics(dm)

        var width = 0
        var height = 0

        when (rotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                width = dm.widthPixels
                height = dm.heightPixels
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                width = dm.heightPixels
                height = dm.widthPixels
            }
            else -> {
            }
        }


        if (width > height) {
            setContentView(R.layout.activity_main_landscape)
        } else {
            setContentView(R.layout.activity_main)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // De-initialize scanner
        deInitScanner()

        // Remove connection listener
        if (barcodeManager != null) {
            barcodeManager!!.removeConnectionListener(this)
            barcodeManager = null
        }

        // Release all the resources
        if (emdkManager != null) {
            emdkManager!!.release()
            emdkManager = null

        }

    }

    override fun onPause() {
        super.onPause()
        // The application is in background

        // De-initialize scanner
        deInitScanner()

        // Remove connection listener
        if (barcodeManager != null) {
            barcodeManager!!.removeConnectionListener(this)
            barcodeManager = null
            deviceList = null
        }

        // Release the barcode manager resources
        if (emdkManager != null) {
            emdkManager!!.release(EMDKManager.FEATURE_TYPE.BARCODE)
        }
    }

    override fun onResume() {
        super.onResume()
        // The application is in foreground

        // Acquire the barcode manager resources
        if (emdkManager != null) {
            barcodeManager = emdkManager!!.getInstance(EMDKManager.FEATURE_TYPE.BARCODE) as BarcodeManager

            // Add connection listener
            if (barcodeManager != null) {
                barcodeManager!!.addConnectionListener(this)
            }

            // Enumerate scanner devices
            enumerateScannerDevices()

            // Set selected scanner
            spinnerScannerDevices!!.setSelection(scannerIndex)

            // Initialize scanner
            initScanner()
            setTrigger()
            setDecoders()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        if (id == R.id.action_settings) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onOpened(emdkManager: EMDKManager) {

        textViewStatus!!.text = "Status: " + "EMDK open success!"

        this.emdkManager = emdkManager

        // Acquire the barcode manager resources
        barcodeManager = emdkManager.getInstance(EMDKManager.FEATURE_TYPE.BARCODE) as BarcodeManager

        // Add connection listener
        if (barcodeManager != null) {
            barcodeManager!!.addConnectionListener(this)
        }

        // Enumerate scanner devices
        enumerateScannerDevices()

        // Set default scanner
        spinnerScannerDevices!!.setSelection(defaultIndex)
    }

    override fun onClosed() {

        if (emdkManager != null) {

            // Remove connection listener
            if (barcodeManager != null) {
                barcodeManager!!.removeConnectionListener(this)
                barcodeManager = null
            }

            // Release all the resources
            emdkManager!!.release()
            emdkManager = null
        }
        textViewStatus!!.text = "Status: " + "EMDK closed unexpectedly! Please close and restart the application."
    }

    override fun onData(scanDataCollection: ScanDataCollection?) {

        if (scanDataCollection != null && scanDataCollection.result == ScannerResults.SUCCESS) {
            val scanData = scanDataCollection.scanData
            for (data in scanData) {

                val dataString = data.data

                AsyncDataUpdate().execute(dataString)
            }
        }
    }

    override fun onStatus(statusData: StatusData) {

        val state = statusData.state
        when (state) {
            StatusData.ScannerStates.IDLE -> {
                statusString = statusData.friendlyName + " is enabled and idle..."
                AsyncStatusUpdate().execute(statusString)
                if (bContinuousMode) {
                    try {
                        // An attempt to use the scanner continuously and rapidly (with a delay < 100 ms between scans)
                        // may cause the scanner to pause momentarily before resuming the scanning.
                        // Hence add some delay (>= 100ms) before submitting the next read.
                        try {
                            Thread.sleep(100)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }

                        scanner!!.read()
                    } catch (e: ScannerException) {
                        statusString = e.message
                        AsyncStatusUpdate().execute(statusString)
                    }

                }
                AsyncUiControlUpdate().execute(true)
            }
            StatusData.ScannerStates.WAITING -> {
                statusString = "Scanner is waiting for trigger press..."
                AsyncStatusUpdate().execute(statusString)
                AsyncUiControlUpdate().execute(false)
            }
            StatusData.ScannerStates.SCANNING -> {
                statusString = "Scanning..."
                AsyncStatusUpdate().execute(statusString)
                AsyncUiControlUpdate().execute(false)
            }
            StatusData.ScannerStates.DISABLED -> {
                statusString = statusData.friendlyName + " is disabled."
                AsyncStatusUpdate().execute(statusString)
                AsyncUiControlUpdate().execute(true)
            }
            StatusData.ScannerStates.ERROR -> {
                statusString = "An error has occurred."
                AsyncStatusUpdate().execute(statusString)
                AsyncUiControlUpdate().execute(true)
            }
            else -> {
            }
        }
    }

    private fun addSpinnerScannerDevicesListener() {

        spinnerScannerDevices!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(parent: AdapterView<*>, arg1: View?,
                                        position: Int, arg3: Long) {

                if (scannerIndex != position || scanner == null) {
                    scannerIndex = position
                    deInitScanner()
                    initScanner()
                    setTrigger()
                    setDecoders()
                }

            }

            override fun onNothingSelected(arg0: AdapterView<*>) {

            }

        }
    }

    private fun addSpinnerTriggersListener() {

        spinnerTriggers!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(arg0: AdapterView<*>, arg1: View?,
                                        position: Int, arg3: Long) {

                triggerIndex = position
                setTrigger()

            }

            override fun onNothingSelected(arg0: AdapterView<*>) {

            }
        }
    }

    private fun addStartScanButtonListener() {

        val btnStartScan = findViewById<View>(R.id.buttonStartScan) as Button

        btnStartScan.setOnClickListener { startScan() }
    }

    private fun addStopScanButtonListener() {

        val btnStopScan = findViewById<View>(R.id.buttonStopScan) as Button

        btnStopScan.setOnClickListener { stopScan() }
    }

    private fun addCheckBoxListener() {

        checkBoxContinuous!!.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                bContinuousMode = true
            } else {
                bContinuousMode = false
            }
        }
    }

    private fun enumerateScannerDevices() {

        if (barcodeManager != null) {

            val friendlyNameList = ArrayList<String>()
            var spinnerIndex = 0

            deviceList = barcodeManager!!.supportedDevicesInfo

            if (deviceList != null && deviceList!!.size != 0) {

                val it = deviceList!!.iterator()
                while (it.hasNext()) {
                    val scnInfo = it.next()
                    friendlyNameList.add(scnInfo.friendlyName)
                    if (scnInfo.isDefaultScanner) {
                        defaultIndex = spinnerIndex
                    }
                    ++spinnerIndex
                }
            } else {
                textViewStatus!!.text = "Status: " + "Failed to get the list of supported scanner devices! Please close and restart the application."
            }

            val spinnerAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, friendlyNameList)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            spinnerScannerDevices!!.adapter = spinnerAdapter
        }
    }

    private fun populateTriggers() {

        val spinnerAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, triggerStrings)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinnerTriggers!!.adapter = spinnerAdapter
        spinnerTriggers!!.setSelection(triggerIndex)
    }

    private fun setTrigger() {

        if (scanner == null) {
            initScanner()
        }

        if (scanner != null) {
            when (triggerIndex) {
                0 // Selected "HARD"
                -> scanner!!.triggerType = Scanner.TriggerType.HARD
                1 // Selected "SOFT"
                -> scanner!!.triggerType = Scanner.TriggerType.SOFT_ALWAYS
            }
        }
    }

    private fun setDecoders() {

        if (scanner == null) {
            initScanner()
        }

        if (scanner != null && scanner!!.isEnabled) {
            try {

                val config = scanner!!.config

                // Set EAN8
                if (checkBoxEAN8!!.isChecked)
                    config.decoderParams.ean8.enabled = true
                else
                    config.decoderParams.ean8.enabled = false

                // Set EAN13
                if (checkBoxEAN13!!.isChecked)
                    config.decoderParams.ean13.enabled = true
                else
                    config.decoderParams.ean13.enabled = false

                // Set Code39
                if (checkBoxCode39!!.isChecked)
                    config.decoderParams.code39.enabled = true
                else
                    config.decoderParams.code39.enabled = false

                //Set Code128
                if (checkBoxCode128!!.isChecked)
                    config.decoderParams.code128.enabled = true
                else
                    config.decoderParams.code128.enabled = false

                config.decoderParams.dataMatrix.enabled = true


                scanner!!.config = config

            } catch (e: ScannerException) {

                textViewStatus!!.text = "Status: " + e.message
            }

        }
    }


    private fun startScan() {

        if (scanner == null) {
            initScanner()
        }

        if (scanner != null) {
            try {

                if (scanner!!.isEnabled) {
                    // Submit a new read.
                    scanner!!.read()

                    if (checkBoxContinuous!!.isChecked)
                        bContinuousMode = true
                    else
                        bContinuousMode = false

                    AsyncUiControlUpdate().execute(false)
                } else {
                    textViewStatus!!.text = "Status: Scanner is not enabled"
                }

            } catch (e: ScannerException) {

                textViewStatus!!.text = "Status: " + e.message
            }

        }

    }

    private fun stopScan() {

        if (scanner != null) {

            try {

                // Reset continuous flag
                bContinuousMode = false

                // Cancel the pending read.
                scanner!!.cancelRead()

                AsyncUiControlUpdate().execute(true)

            } catch (e: ScannerException) {

                textViewStatus!!.text = "Status: " + e.message
            }

        }
    }

    private fun initScanner() {

        if (scanner == null) {

            if (deviceList != null && deviceList!!.size != 0) {
                scanner = barcodeManager!!.getDevice(deviceList!![scannerIndex])
            } else {
                textViewStatus!!.text = "Status: " + "Failed to get the specified scanner device! Please close and restart the application."
                return
            }

            if (scanner != null) {

                scanner!!.addDataListener(this)
                scanner!!.addStatusListener(this)

                try {
                    scanner!!.enable()
                } catch (e: ScannerException) {

                    textViewStatus!!.text = "Status: " + e.message
                }

            } else {
                textViewStatus!!.text = "Status: " + "Failed to initialize the scanner device."
            }
        }
    }

    private fun deInitScanner() {

        if (scanner != null) {

            try {

                scanner!!.cancelRead()
                scanner!!.disable()

            } catch (e: ScannerException) {

                textViewStatus!!.text = "Status: " + e.message
            }

            scanner!!.removeDataListener(this)
            scanner!!.removeStatusListener(this)
            try {
                scanner!!.release()
            } catch (e: ScannerException) {

                textViewStatus!!.text = "Status: " + e.message
            }

            scanner = null
        }
    }

    private inner class AsyncDataUpdate : AsyncTask<String, Void, String>() {

        override fun doInBackground(vararg params: String): String {

            return params[0]
        }

        override fun onPostExecute(result: String?) {

            if (result != null) {
                if (dataLength++ > 100) { //Clear the cache after 100 scans
                    textViewData!!.text = ""
                    dataLength = 0
                }

                textViewData!!.append(result + "\n")


                (findViewById<View>(R.id.scrollView1) as View).post { (findViewById<View>(R.id.scrollView1) as ScrollView).fullScroll(View.FOCUS_DOWN) }

            }
        }
    }

    private inner class AsyncStatusUpdate : AsyncTask<String, Void, String>() {

        override fun doInBackground(vararg params: String): String {

            return params[0]
        }

        override fun onPostExecute(result: String) {

            textViewStatus!!.text = "Status: " + result
        }
    }

    private inner class AsyncUiControlUpdate : AsyncTask<Boolean, Void, Boolean>() {

        override fun doInBackground(vararg p0: Boolean?): Boolean? {
            return p0[0]
        }

        override fun onPostExecute(bEnable: Boolean?) {

            checkBoxEAN8!!.isEnabled = bEnable!!
            checkBoxEAN13!!.isEnabled = bEnable
            checkBoxCode39!!.isEnabled = bEnable
            checkBoxCode128!!.isEnabled = bEnable
            spinnerScannerDevices!!.isEnabled = bEnable
            spinnerTriggers!!.isEnabled = bEnable
        }
    }

    override fun onCheckedChanged(arg0: CompoundButton, arg1: Boolean) {

        setDecoders()
    }

    override fun onConnectionChange(scannerInfo: ScannerInfo, connectionState: BarcodeManager.ConnectionState) {

        val status: String
        var scannerName = ""

        val statusExtScanner = connectionState.toString()
        val scannerNameExtScanner = scannerInfo.friendlyName

        if (deviceList!!.size != 0) {
            scannerName = deviceList!![scannerIndex].friendlyName
        }

        if (scannerName.equals(scannerNameExtScanner, ignoreCase = true)) {

            when (connectionState) {
                BarcodeManager.ConnectionState.CONNECTED -> {
                    deInitScanner()
                    initScanner()
                    setTrigger()
                    setDecoders()
                }
                BarcodeManager.ConnectionState.DISCONNECTED -> {
                    deInitScanner()
                    AsyncUiControlUpdate().execute(true)
                }
            }

            status = scannerNameExtScanner + ":" + statusExtScanner
            AsyncStatusUpdate().execute(status)
        } else {
            status = "$statusString $scannerNameExtScanner:$statusExtScanner"
            AsyncStatusUpdate().execute(status)
        }
    }
}
