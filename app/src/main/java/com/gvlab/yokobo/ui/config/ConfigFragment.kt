package com.gvlab.yokobo.ui.config

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.location.LocationManager
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.core.app.ActivityCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.gvlab.yokobo.R
import com.gvlab.yokobo.databinding.FragmentConfigBinding
import java.util.*


class ConfigFragment : Fragment(), BleManagerListener {

    companion object {
        fun newInstance() = ConfigFragment()
        private const val REQUEST_ENABLE_BT     = 1
        private const val REQUEST_ENABLE_LOC    = 2

        private const val MODE_LIST_VISIBLE     = 1
        private const val MODE_LIST_GONE        = 2
    }

    private val arrPerm: ArrayList<String> = ArrayList()
    private var permissionGranted: Map<String, Boolean> = emptyMap<String, Boolean>()

    private lateinit var textViewBt: TextView
    private lateinit var btnRetry: Button

    private val scanResults = mutableListOf<ScanResult>()
    private val scanResultAdapter: ScanResultAdapter by lazy {
        ScanResultAdapter(scanResults) { result ->
            // User tapped on a scan result
            if (bleManager.scanning) {
                bleManager.scanLeDevice() // stop scanning
            }
            with(result.device) {
                Log.w("ScanResultAdapter", "Connecting to $address")
                connectGatt(context, false, bleManager.gattCallback)
            }
        }
    }
    private var bleManager = BleManager(scanResults, scanResultAdapter)

    private val requestMultiplePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            permissionGranted = permissions
            Log.e("Permissions", permissions.toString())
            if(!permissions.containsValue(false))
                createViewWithPermission()
        }

    private var _binding: FragmentConfigBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!


    override fun onConnected()
    {
        Log.e("Connected", "Connected to device")
        val bundle = bundleOf("bleManager" to bleManager)
        view?.findNavController()?.navigate(R.id.navigation_config_wifi, bundle)
    }

    override fun onReadData(data: String) {
        Log.e("Read data", "data: $data")
        bleManager.write(bleManager.byteArrayOfInts(0xFF, 0xFF, 0xAA, 0xCC, 0x89, 0x00))
    }

    override fun onScanFinished() {
        if(scanResults.isEmpty())
        {
            switchListNoResult(MODE_LIST_GONE)
            //temp (uncomment to skip bluetooth connection, for emulator)
            //val bundle = bundleOf("bleManager" to bleManager)
            //view?.findNavController()?.navigate(R.id.navigation_config_wifi, bundle)
        }
    }

    /**********************************************************
     ****************** METHODS *******************************
     **********************************************************/

    override fun onStart() {
        super.onStart()
        setupRecyclerView()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleManager.context = context
        bleManager.bluetoothName = getString(R.string.bluetooth_name)
        bleManager.addListener(this)
    }

    private fun setupRecyclerView() {
        binding.scanResultsRecyclerView.apply {
            adapter = scanResultAdapter
            layoutManager = LinearLayoutManager(
                context,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false
        }

        val animator = binding.scanResultsRecyclerView.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }

    //+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentConfigBinding.inflate(inflater, container, false)
        val root: View = binding.root

        textViewBt = binding.textConfigBluetooth
        btnRetry = binding.scanRetry

        btnRetry.setOnClickListener {
            switchListNoResult(MODE_LIST_VISIBLE)
            bleManager.scanLeDevice()
        }

        val textViewIntro: TextView = binding.textConfigIntro
        textViewIntro.setText(R.string.config_intro)
        textViewBt.text = getString(R.string.config_permission)

        if(Build.VERSION.SDK_INT <= 30)
            checkPermission(Manifest.permission.BLUETOOTH)
        else {
            checkPermission(Manifest.permission.BLUETOOTH_SCAN)
            checkPermission(Manifest.permission.BLUETOOTH_CONNECT)
        }
        checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

        Log.w("List Perm", arrPerm.toString())

        if(arrPerm.isNotEmpty())
            requestMultiplePermissionLauncher.launch(arrPerm.toTypedArray())
        else
            createViewWithPermission()

        return root
    }

    private fun createViewWithPermission()
    {
        when(bleManager.isBluetoothAvailable()){
            BleManager.BT_NOT_AVAILABLE -> {
                textViewBt.text = getString(R.string.config_bluetooth_unavailable)
                textViewBt.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_bluetooth_not_available, 0, 0, 0)
                textViewBt.setBackgroundResource(R.drawable.rec_error)
            }
            BleManager.BT_DISABLE -> {
                textViewBt.text = getString(R.string.config_bluetooth_disable)
                textViewBt.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_bluetooth_disabled, 0, 0, 0)
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
            BleManager.BT_AVAILABLE ->
            {
                if(locationStatus())
                    this.createPageIfBtActivated()
                else
                    askLocationActivation()
            }
        }
    }

    private fun locationStatus() : Boolean
    {
        val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var gpsEnabled = false
        var networkEnabled = false

        try {
            gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (ex: Exception) {
        }

        try {
            networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (ex: Exception) {
        }
        return (gpsEnabled || networkEnabled)
    }

    private fun askLocationActivation()
    {
        val enableLocIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivityForResult(enableLocIntent, REQUEST_ENABLE_LOC)
    }

    //+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode){
            REQUEST_ENABLE_BT -> {
                if(resultCode == Activity.RESULT_OK) {
                    if (locationStatus())
                        this.createPageIfBtActivated()
                    else
                        askLocationActivation()
                }
                else {
                    textViewBt.setBackgroundResource(R.drawable.rec_error)
                }
            }
            REQUEST_ENABLE_LOC -> {
                if(resultCode == Activity.RESULT_CANCELED && locationStatus())
                    this.createPageIfBtActivated()
                else {
                    textViewBt.text = getString(R.string.config_location_disabled)
                    textViewBt.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_baseline_location_off_24, 0, 0, 0)
                    textViewBt.setBackgroundResource(R.drawable.rec_error)
                }
            }
        }
    }

    //+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    private fun createPageIfBtActivated()
    {
        textViewBt.text = getString(R.string.config_bluetooth_enable)
        textViewBt.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_bluetooth, 0, 0, 0)

        val imgYokoboBluetoothButton: Drawable = resources.getDrawable(R.drawable.yokobo_app_bt)
        val imgWidth = 800
        val imgHeight = (imgWidth*(imgYokoboBluetoothButton.minimumHeight.toDouble()/imgYokoboBluetoothButton.minimumWidth.toDouble())).toInt()
        imgYokoboBluetoothButton.setBounds(0, 0, imgWidth, imgHeight)

        textViewBt.text = getString(R.string.config_bluetooth_explanation)
        //textViewBt.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, R.drawable.yokobo_app_bt)
        textViewBt.setCompoundDrawables(null, null, null, imgYokoboBluetoothButton)

        val titleList: TextView = binding.textTitleListBtDevices
        titleList.visibility = TextView.VISIBLE
        bleManager.scanLeDevice()
    }


    //+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    //+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    /*override fun onPause() {
        super.onPause()
        context?.unregisterReceiver(receiver)
    }*/

    private fun checkPermission(permission : String)
    {
        if (context?.let {
                ActivityCompat.checkSelfPermission(
                    it,
                    permission
                )
            } != PackageManager.PERMISSION_GRANTED
        ) {
            arrPerm.add(permission)
        }
    }

    private fun switchListNoResult(mode: Int) {
        val listDevices = binding.scanResultsRecyclerView
        val noResult = binding.scanNoResult
        val button = binding.scanRetry
        when (mode){
            MODE_LIST_VISIBLE ->
            {
                listDevices.visibility = RecyclerView.VISIBLE
                noResult.visibility = TextView.GONE
                button.visibility = Button.GONE
            }
            MODE_LIST_GONE ->
            {
                listDevices.visibility = RecyclerView.GONE
                noResult.visibility = TextView.VISIBLE
                button.visibility = Button.VISIBLE
            }
        }

    }
}
