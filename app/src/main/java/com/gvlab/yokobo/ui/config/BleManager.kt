package com.gvlab.yokobo.ui.config

import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*
import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.widget.Toast
import com.gvlab.yokobo.R
import java.lang.ref.WeakReference

class BleManager(val scanResults: MutableList<ScanResult> = mutableListOf<ScanResult>(),
                 val scanResultAdapter: ScanResultAdapter): Parcelable {
    companion object CREATOR : Parcelable.Creator<BleManager> {
        override fun createFromParcel(parcel: Parcel): BleManager {
            return BleManager(parcel)
        }

        override fun newArray(size: Int): Array<BleManager?> {
            return arrayOfNulls(size)
        }

        // Stops scanning after 10 seconds.
        private const val SCAN_PERIOD: Long = 10000

        private val yokoboServiceUuid = UUID.fromString("0000ec00-0000-1000-8000-00805f9b34fb")
        private val yokoboServiceUuidChar = UUID.fromString("0000ec0e-0000-1000-8000-00805f9b34fb")
        private const val GATT_MAX_MTU_SIZE = 517

        const val BT_NOT_AVAILABLE   = 0
        const val BT_DISABLE         = 1
        const val BT_AVAILABLE       = 2

        const val FUNCTION_WIFI: Int                = 1
        const val FUNCTION_WEATHER_STATION          = 2

        const val WEATHER_STATION_REQUEST           = "1"
        const val WEATHER_STATION_TOKEN             = "2"
        const val WEATHER_STATION_CLOSE             = "3"
    }

    var context : Context? = null
    var bluetoothName : String? = null
    var serviceUuid : UUID = yokoboServiceUuid
    var serviceUuidChar : UUID = yokoboServiceUuidChar

    var isConnected = false

    private val bluetoothAdapter : BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    var scanning = false
    private val handler = Handler()
    private lateinit var bluetoothGatt : BluetoothGatt
    private var maximumTransmissionUnit = 23 // 23 is the minimum
    private var listener = WeakReference<BleManagerListener>(null)

    val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                    gatt.printGattTable()
                    bluetoothGatt = gatt
                    Handler(Looper.getMainLooper()).post {
                        bluetoothGatt?.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                    gatt.close()
                }
            } else {
                Log.w("BluetoothGattCallback", "Error $status encountered for $deviceAddress! Disconnecting...")
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.w("BluetoothGattCallback", "Discovered ${services.size} services for ${device.address}")
                printGattTable() // See implementation just above this section
                // Consider connection setup as complete here
                gatt.requestMtu(GATT_MAX_MTU_SIZE)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.w("MTU", "ATT MTU changed to $mtu, success: ${status == BluetoothGatt.GATT_SUCCESS}")
            //readYokobo()
            maximumTransmissionUnit = mtu
            isConnected = true
            listener.get()?.onConnected()
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        listener.get()?.onReadData(value.toString(Charsets.UTF_8))
                        Log.i("BluetoothGattCallback", "Read characteristic $uuid:\n${value.toHexString()} | UTF-8: ${String(value, Charsets.UTF_8)}")
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic read failed for $uuid, error: $status")
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i("BluetoothGattCallback", "Wrote to characteristic $uuid | value: ${value.toHexString()} | UTF-8: ${String(value, Charsets.UTF_8)}")
                        read()
                    }
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                        Log.e("BluetoothGattCallback", "Write exceeded connection ATT MTU!")
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Write not permitted for $uuid!")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic write failed for $uuid, error: $status")
                    }
                }
            }
        }
    }

    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i("printGattTable", "No service and characteristic available, call discoverServices() first?")
            return
        }
        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) { it.uuid.toString() }
            Log.i("printGattTable", "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
            )
        }
    }

    private fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    private fun BluetoothGattCharacteristic.isWritable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    private fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    private fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return properties and property != 0
    }

    fun ByteArray.toHexString(): String =
        joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

    fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte() }

    private fun getCharacteristics(serviceUuid: UUID, charUuid: UUID): BluetoothGattCharacteristic? {
        return bluetoothGatt.getService(serviceUuid)?.getCharacteristic(charUuid)
    }

    fun read()
    {
        readCharacteristic(null)
    }

    fun readCharacteristic(characteristic: BluetoothGattCharacteristic?) {
        val char = characteristic ?: getCharacteristics(serviceUuid, serviceUuidChar)
        if (char != null) {
            if (char.isReadable()) {
                bluetoothGatt.readCharacteristic(char)
            }
        }
    }

    fun write(data: ByteArray)
    {
        writeCharacteristic(null, data)
    }

    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic?, data: ByteArray) {
        val char = characteristic ?: getCharacteristics(serviceUuid, serviceUuidChar)
        if (char != null) {
            val writeType = when {
                char.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                char.isWritableWithoutResponse() -> {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                else -> error("Characteristic ${char.uuid} cannot be written to")
            }

            bluetoothGatt.let { gatt ->
                char.writeType = writeType
                char.value = data
                gatt.writeCharacteristic(char)
            } ?: error("Not connected to a BLE device!")
        }
    }

    fun scanLeDevice() {
        if (!scanning) { // Stops scanning after a pre-defined scan period.
            handler.postDelayed({
                if(scanning) {
                    scanning = false
                    Toast.makeText(
                        context,
                        R.string.config_bluetooth_discovering_stop,
                        Toast.LENGTH_SHORT
                    ).show()
                    bluetoothLeScanner?.stopScan(leScanCallback)
                    listener.get()?.onScanFinished()
                }
            }, SCAN_PERIOD)
            scanning = true
            scanResults.clear()
            scanResultAdapter.notifyDataSetChanged()

            val filters : List<ScanFilter>? = if(bluetoothName.isNullOrEmpty())
                null
            else {
                listOf(ScanFilter.Builder().setDeviceName(bluetoothName).build())
            }
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            Toast.makeText(context, R.string.config_bluetooth_discovering, Toast.LENGTH_SHORT).show()
            bluetoothLeScanner?.startScan(filters, scanSettings, leScanCallback)
        } else {
            scanning = false
            bluetoothLeScanner?.stopScan(leScanCallback)
            listener.get()?.onScanFinished()
        }
    }

    // Device scan callback.
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
            if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result
                scanResultAdapter.notifyItemChanged(indexQuery)
            } else {
                with(result.device) {
                    Log.i("ScanCallback", "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                }
                scanResults.add(result)
                scanResultAdapter.notifyItemInserted(scanResults.size - 1)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("Scan failed", errorCode.toString())
        }
    }

    constructor(parcel: Parcel) : this(
        TODO("scanResults"),
        TODO("scanResultAdapter")
    ) {
        bluetoothName = parcel.readString()
        scanning = parcel.readByte() != 0.toByte()
        maximumTransmissionUnit = parcel.readInt()
    }

    public fun isBluetoothAvailable() : Int
    {
        return if (bluetoothAdapter == null)
            BT_NOT_AVAILABLE
        else if (!bluetoothAdapter.isEnabled)
            BT_DISABLE
        else
            BT_AVAILABLE
    }

    fun addListener(listener : BleManagerListener)
    {
        this.listener = WeakReference(listener)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(bluetoothName)
        parcel.writeByte(if (scanning) 1 else 0)
        parcel.writeInt(maximumTransmissionUnit)
    }

    override fun describeContents(): Int {
        return 0
    }

    fun writeMessage(function:Int, vararg messages : String)
    {
        val charset = Charsets.UTF_8
        var sizeTotal = 0; for(mes in messages) sizeTotal += mes.toByteArray(charset).size
        val message = ByteArray(1 + sizeTotal + messages.count())

        var i : Int = 0
        message[i] = function.toByte()
        i++
        for(mes in messages)
        {
            message[i] = mes.toByteArray(charset).size.toByte()
            i++
            for (byte in mes.toByteArray(charset))
            {
                message[i] = byte
                i++
            }
        }
        write(message)
    }

    fun Boolean.toInt() = if (this) 1 else 0
}