package android.example.homescout.ui.security

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.example.homescout.R
import android.example.homescout.databinding.FragmentSecurityBinding
import android.example.homescout.ui.intro.PermissionAppIntro
import android.example.homescout.ui.scan.ScanResultAdapter
import android.example.homescout.utils.BluetoothAPILogger
import android.example.homescout.utils.Constants
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter


class SecurityFragment : Fragment() {

    private var _binding: FragmentSecurityBinding? = null
    private val binding get() = _binding!!

    private val isBluetoothEnabled : Boolean
        get() = bluetoothAdapter.isEnabled

    private val scanResults = mutableListOf<ScanResult>()

    private var isStarting = false
        set(value) {
            field = value
            if (value) {
                binding.buttonStart.text = getString(R.string.security_button_interrupt)
            } else {
                binding.buttonStart.text = getString(R.string.security_button_start)
            }
        }

//    private var lastScanResults : List<ScanResult> = emptyList()

    // PROPERTIES lateinit
    private lateinit var scanSettings: ScanSettings

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = activity?.getSystemService(AppCompatActivity.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val securityResultAdapter: SecurityResultAdapter by lazy {
        SecurityResultAdapter(scanResults) { detail ->
            // User tapped on a scan result, display security details
            with(detail) {
                var builder= AlertDialog.Builder(requireActivity())
                builder.setTitle("Details")
                val exist = if (detail.deviceAddressUpdate == "Update") {
                    "Existed less than 15 min"
                } else {
                    "Existed more than 15 min"
                }
                builder.setMessage("Device Address: ${detail.deviceAddress}\nManufacturer: ${detail.deviceManufacturer}\nAddress Type: ${detail.deviceAddressType}\nAddress Existed Status: ${exist}\nOverall Security Level: ${detail.deviceSecurityLevel}\nTips: Without two scans at least 15 minutes apart, Address Existed Status will be inaccurate!")
                builder.setPositiveButton("OK"){dialog, which ->
                    dialog.dismiss();
                }
                var dialog: AlertDialog =builder.create()
                if (!dialog.isShowing) {
                    dialog.show()
                }
            }
        }
    }

    // LIFECYCLE FUNCTIONS
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        setupViewModelAndBinding(inflater, container)
        buildScanSettings()
        setOnClickListenerForStartButton()
        setupRecyclerView()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // FUNCTIONS USED IN onCreateView() (for code readability)
    private fun setupViewModelAndBinding(inflater: LayoutInflater, container: ViewGroup?) {
        _binding = FragmentSecurityBinding.inflate(inflater, container,false)
    }

    private fun buildScanSettings() {
        scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
    }

    fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }

    private fun setOnClickListenerForStartButton() {
        binding.buttonStart.setOnClickListener {
            if (!isBluetoothEnabled) {

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Enable Bluetooth required!")
                    .setMessage("Please turn on Bluetooth. Thanks.")
                    .setPositiveButton("Ok") { _, _ ->
                        // Respond to positive button press
                        val intentBluetooth = Intent().apply {
                            action = Settings.ACTION_BLUETOOTH_SETTINGS
                        }
                        requireContext().startActivity(intentBluetooth)
                    }
                    .show()
                return@setOnClickListener
            }

            if (isStarting) {
                stopSecurityScoring()
                println("Interrupted!")
                isStarting = false
            } else {
                startSecurityScoring()
                isStarting = true
            }
        }
    }

    private fun setupRecyclerView() {
        binding.securityResultsRecyclerView.apply {
            adapter = securityResultAdapter
            layoutManager = LinearLayoutManager(
                activity,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false
        }

        val animator = binding.securityResultsRecyclerView.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }

    private fun checkIfBluetoothIsEnabled() {

        binding.buttonStart.isEnabled = isBluetoothEnabled

        if (!isBluetoothEnabled) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Bluetooth required!")
                .setMessage("Please enable Bluetooth. Thanks")
                .setPositiveButton("Ok") { _, _ ->
                    // Respond to positive button press
                    val intentBluetooth = Intent().apply {
                        action = Settings.ACTION_BLUETOOTH_SETTINGS
                    }
                    requireContext().startActivity(intentBluetooth)

                }
                .show()
        }
    }

    // callbacks
    private val securityCallback = object : ScanCallback() {


        override fun onScanResult(callbackType: Int, result: ScanResult) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                BluetoothAPILogger().logResults(result)
            }


            // this might needs to be changed as the device.address might change due to
            // MAC randomization
            // check if the current found result is already in the entire scanResult list
            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
            // element not found returns -1
            if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result
                securityResultAdapter.notifyItemChanged(indexQuery)
            } else { // found new device
//                with(result.device) {
//                    //Timber.i( address: $address")
                scanResults.add(result)
                securityResultAdapter.notifyItemInserted(scanResults.size - 1)
            }
            val devList = mutableListOf<String>()
            for (dev in scanResults) {
                val devmac = dev.device.address
                devList.add(devmac)
//                println(devmac)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.i("onScanFailed: code $errorCode")
        }
    }

    // PRIVATE FUNCTIONS
    private fun startSecurityScoring() {

        if (!isBluetoothEnabled) { checkIfBluetoothIsEnabled() }
//        println("scanresult: $scanResults")
//        if (scanResults.isNotEmpty()){
//            println("have last res")
//            for (item in scanResults){
//                if (item in lastRes){
//                    val indexAddr = lastRes.indexOfFirst { it.device.address == item.device.address }
//                    println(item.timestampNanos)
//                    println(lastRes[indexAddr].timestampNanos)
//                }
//            }
//        }
        scanResults.clear()
        securityResultAdapter.notifyDataSetChanged()
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            startActivity(Intent(requireContext(), PermissionAppIntro::class.java))
            return
        }
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            isStarting = false
            bleScanner.stopScan(securityCallback)
        }, Constants.SECURITY_SCAN_PERIOD)

        bleScanner.startScan(null, scanSettings, securityCallback)

        isStarting = true
    }

    private fun stopSecurityScoring() : List<ScanResult>{
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            startActivity(Intent(requireContext(), PermissionAppIntro::class.java))
            return emptyList()
        }
        bleScanner.stopScan(securityCallback)
        isStarting = false
        return scanResults
    }

}