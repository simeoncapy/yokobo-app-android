package com.gvlab.yokobo.ui.config

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.ApplicationMediaCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.gvlab.yokobo.databinding.FragmentConfigWifiBinding
import kotlinx.android.synthetic.main.config_wifi_row_scan_result.*
import android.net.wifi.WifiInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment
import com.gvlab.yokobo.R
import kotlinx.android.synthetic.main.dialog_wifi_password.*
import android.telephony.TelephonyManager
import androidx.core.os.bundleOf
import androidx.navigation.findNavController
import java.net.InetAddress


private const val TAG = "WifiList"
class ConfigWifiFragment : Fragment(), BleManagerListener, WifiPasswordDialogFragment.WifiPasswordDialogListener {

    companion object {
        fun newInstance() = ConfigWifiFragment()
    }

    private var _binding: FragmentConfigWifiBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var wifiManager : WifiManager
    private lateinit var btnScan: Button
    private lateinit var btnCurrent : Button
    private lateinit var txtPleaseWait : TextView
    private var dialogPassword = WifiPasswordDialogFragment()
    private var selectedWifi : SelectedWifi = SelectedWifi()

    class SelectedWifi{
        lateinit var ssid : String
        lateinit var security : String
    }

    private val wifiScanReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                scanSuccess()
            } else {
                scanFailure()
            }
        }
    }

    private val scanResults = mutableListOf<ScanResult>()
    private val scanResultWifiAdapter: ScanResultWifiAdapter by lazy {
        ScanResultWifiAdapter(scanResults) { result ->
            // User tapped on a scan result
            with(result.SSID) {
                Log.w("ScanResultAdapter", "Connecting to $ssid")
                connectToWifiNetwork(result.SSID, result.capabilities)
            }
        }
    }

    private val intentFilter = IntentFilter()

    private lateinit var bleManager: BleManager
    // List of saved wifi networks (each network stored in a wifiEntry object)

    override fun onStart() {
        super.onStart()
        setupRecyclerView()

        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
        val ip = sharedPref.getString(getString(R.string.key_shared_preference_yokobo_ip), "empty")
        if (ip != null) {
            Log.i("Share", ip)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleManager = arguments?.getParcelable("bleManager")!!
        bleManager.addListener(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentConfigWifiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        //bleManager.read()

        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context?.registerReceiver(wifiScanReceiver, intentFilter)
        wifiManager = context?.getSystemService(Context.WIFI_SERVICE) as WifiManager

        txtPleaseWait = binding.configWifiPleaseWait
        txtPleaseWait.visibility = TextView.GONE

        // Getting the current Wifi Network
        val currentWifi: WifiInfo = wifiManager.connectionInfo
        btnCurrent = binding.configWifiCurrentNetwork
        if(currentWifi.ssid == WifiManager.UNKNOWN_SSID){
            btnCurrent.text = getString(R.string.config_wifi_not_connected)
            btnCurrent.isClickable = false
            btnCurrent.isEnabled = false
        }
        else
            btnCurrent.text = currentWifi.ssid.replace("\"", "")

        btnCurrent.setOnClickListener{
            val networkList: List<ScanResult> = wifiManager.getScanResults()
            var security: String = ""
            if (networkList != null) {
                for (network in networkList) {
                    if(network.SSID == btnCurrent.text) {
                        security = network.capabilities
                        break
                    }
                }
            }
            connectToWifiNetwork(btnCurrent.text as String, security)
        }

        // Scanning for available networks
        btnScan = binding.configWifiBtnAnother

        btnScan.setOnClickListener {
            if(wifiManager == null)
                Log.e("WifiList", "null")
            else {
                binding.scanResultsWifi.visibility = RecyclerView.VISIBLE
                binding.configWifiBtnAnother.visibility = Button.GONE
                binding.configWifiTitleList.visibility = TextView.VISIBLE
                binding.configPBar.visibility = ProgressBar.VISIBLE
                val success = wifiManager.startScan()
                if (!success) {
                    // scan failure handling
                    scanFailure()
                }
            }
        }
    }

    override fun onConnected() {
        Log.e("ConfigWifi", "No connection to BT should be done here")
    }

    override fun onReadData(data: String) {
        var ipAddress = data.split("/")[0]
        var inet = InetAddress.getByName(ipAddress)

        Log.i("c", "Sending Ping Request to $ipAddress")
        if (inet.isReachable(5000)) Log.i("IP add", "Host is reachable") else Log.i("IP add", "Host is NOT reachable")

        // Saving the IP address on the shared preferences
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
        with (sharedPref.edit()) {
            putString(getString(R.string.key_shared_preference_yokobo_ip), ipAddress)
            apply()
        }

        var isConnected = data.split("/")[1].toInt().toBoolean()
        Log.i("IP add", isConnected.toString())

        // If configuration succeed, go to next step. TODO verify
        val bundle = bundleOf("bleManager" to bleManager)
        view?.findNavController()?.navigate(R.id.navigation_config_ws, bundle)
    }

    // Bluetooth scan
    override fun onScanFinished() {
        Log.e("ConfigWifi", "No BT scan should be done here")
    }


    // Wifi Scan
    private fun scanSuccess() {
        val results = wifiManager?.scanResults
        binding.configPBar.visibility = ProgressBar.GONE
        for(result in results)
        {
            if (result.SSID.isNotEmpty()) {
                scanResults.add(result)
                scanResultWifiAdapter.notifyItemInserted(scanResults.size - 1)
            }
        }
    }

    private fun scanFailure() {
        // handle failure: new scan did NOT succeed
        // consider using old scan results: these are the OLD results!
        //val results = wifiManager.scanResults
        //... potentially use older scan results ...
        Log.e("WifiList", "Error scan")
        TODO("Create the display in case of failure")
    }

    private fun setupRecyclerView() {
        binding.scanResultsWifi.apply {
            adapter = scanResultWifiAdapter
            layoutManager = LinearLayoutManager(
                context,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false
        }

        val animator = binding.scanResultsWifi.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }

    private fun connectToWifiNetwork(ssid: String, capabilities: String)
    {
        Log.i("Connection", ssid)

        selectedWifi.ssid = ssid
        selectedWifi.security = capabilities
        dialogPassword.setListener(this)
        getFragmentManager()?.let { dialogPassword.show(it, "passwordWifi") }
    }

    override fun onDialogPositiveClick(dialog: DialogFragment) {
        val regex = Regex("WPA[0-9]?-PSK")
        val security = if (regex.find(selectedWifi.security)?.value != null) "WPA-PSK" else "none"
        //val security = regex.find(selectedWifi.security)?.value ?: "none"


        val tm = context?.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val countryCodeValue = tm.networkCountryIso.uppercase()

        txtPleaseWait.visibility = TextView.VISIBLE

        bleManager.writeMessage(BleManager.FUNCTION_WIFI,
                                selectedWifi.ssid,
                                dialog.dialog?.config_wifi_password?.text.toString(),
                                security, // extract the security
                                binding.configWifiCheckboxForget.isChecked.toInt().toString(),
                                countryCodeValue) // Finding the country code
    }

    override fun onDialogNegativeClick(dialog: DialogFragment) {
        TODO("Not yet implemented")
        Log.i("ConfigWifi", "not ok")
    }

    private fun Boolean.toInt() = if (this) 1 else 0
    private fun Int.toBoolean() = this != 0
}