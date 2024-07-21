package com.example.bluetoothproject

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.IOException
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var deviceList: MutableList<BluetoothDevice>? = null
    private lateinit var listView: ListView
    private lateinit var refreshButton: Button
    private lateinit var rvNearbyDevices: RecyclerView
    private lateinit var nearByDevicesAdapter: NearByDevicesAdapter
    private var bluetoothGatt: BluetoothGatt? = null
    private val mPlayer = MediaPlayer()
    private var isDevicePaired = false



    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (!deviceList!!.contains(it)) {
                            deviceList!!.add(it)
                            nearByDevicesAdapter.notifyDataSetChanged()
                        }
                        Log.d("BluetoothDevice", "Device found: ${it.name} [${it.address}]")
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.device_list_view)
        refreshButton = findViewById(R.id.refresh_button)
        rvNearbyDevices = findViewById(R.id.rvNearbyDevices)

        setUpRecyclerView()
        registerPairingReceiver()

        refreshButton.setOnClickListener { refreshDeviceList() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_FINE_LOCATION
            )
        } else {
            requestBluetoothPermissions()
        }
    }

        private fun setUpRecyclerView() {
            deviceList = ArrayList()
            nearByDevicesAdapter = NearByDevicesAdapter(deviceList!!)

            rvNearbyDevices.layoutManager = LinearLayoutManager(this)
            rvNearbyDevices.addItemDecoration(DividerItemDecoration(this, LinearLayout.VERTICAL))
            rvNearbyDevices.adapter = nearByDevicesAdapter

            nearByDevicesAdapter.setOnPairClickListener { device ->
                pairDevice(device)
            }

            nearByDevicesAdapter.setOnUnpairClickListener { device ->
                unpairDevice(device)
            }

            nearByDevicesAdapter.setOnBatteryClickListener { device ->
                 queryBatteryLevel(device)
                //getBatteryLevel(device,this)

            }
    }

    @SuppressLint("MissingPermission")
    private fun pairDevice(device: BluetoothDevice) {
        try {
            val result = device.createBond()
            if (result) {
                Toast.makeText(this, "Pairing initiated", Toast.LENGTH_SHORT).show()
                bluetoothGatt = device.connectGatt(this, false, gattCallback)
            } else {
                Toast.makeText(this, "Pairing failed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Pairing failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun playMusic() {
        if (isDevicePaired) {
            val mPlayer = MediaPlayer()
            try {
                mPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                mPlayer.setDataSource(
                    this,
                    Uri.parse("https://www.hrupin.com/wp-content/uploads/mp3/testsong_20_sec.mp3")
                )
                mPlayer.setOnPreparedListener {
                    it.start()
                }
                mPlayer.prepareAsync()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else {
            Toast.makeText(this, "Device is not paired", Toast.LENGTH_SHORT).show()
        }
    }



    @SuppressLint("MissingPermission")
    private fun unpairDevice(device: BluetoothDevice) {
        try {
            val removeBondMethod = BluetoothDevice::class.java.getMethod("removeBond")
            removeBondMethod.invoke(device)
            Toast.makeText(this, "Unpairing initiated", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Unpairing failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.S)
    private fun requestBluetoothPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ), REQUEST_BLUETOOTH_PERMISSIONS
            )
        } else {
            initializeBluetooth()
        }
    }

    @SuppressLint("MissingPermission")
    private fun initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Device doesn't support Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else {
            startDiscovery()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery() {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        if (bluetoothAdapter!!.isDiscovering) {
            bluetoothAdapter!!.cancelDiscovery()
        }
        bluetoothAdapter!!.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun refreshDeviceList() {
        deviceList!!.clear()
        nearByDevicesAdapter.notifyDataSetChanged()
        if (bluetoothAdapter!!.isDiscovering) {
            bluetoothAdapter!!.cancelDiscovery()
        }
        bluetoothAdapter!!.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        mPlayer.release()
        unregisterPairingReceiver()
        bluetoothGatt = null
        if (bluetoothAdapter != null && bluetoothAdapter!!.isDiscovering) {
            bluetoothAdapter!!.cancelDiscovery()
        }
        unregisterReceiver(receiver)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                startDiscovery()
            } else {
                Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_SHORT).show()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_FINE_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    requestBluetoothPermissions()
                }
            } else {
                Toast.makeText(
                    this,
                    "Location permission is required to find Bluetooth devices",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeBluetooth()
            } else {
                Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_SHORT)
                    .show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_FINE_LOCATION = 2
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 3
        private val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
        private val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    }

    @SuppressLint("MissingPermission")
    private fun queryBatteryLevel(device: BluetoothDevice) {
        // Disconnect any previous GATT connection
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()

        bluetoothGatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("BluetoothGatt", "Connected to GATT server.")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("BluetoothGatt", "Disconnected from GATT server.")
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BluetoothGatt", "Services discovered.")
                    for (service in gatt.services) {
                        Log.d("BluetoothGatt", "Service found: ${service.uuid}")
                        for (characteristic in service.characteristics) {
                            Log.d("BluetoothGatt", "Characteristic found: ${characteristic.uuid}")
                            if (isBatteryCharacteristic(characteristic)) {
                                gatt.readCharacteristic(characteristic)
                                return
                            }
                        }
                    }
                    Log.d("BluetoothGatt", "Battery characteristic not found.")
                } else {
                    Log.e("BluetoothGatt", "Failed to discover services: $status")
                }
            }

            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (isBatteryCharacteristic(characteristic)) {
                        val batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                        Log.d("BluetoothGatt", "Battery level read: $batteryLevel%")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Battery Level: $batteryLevel%", Toast.LENGTH_SHORT).show()
                        }
                        // Disconnect after reading
                        gatt.disconnect()
                        gatt.close()
                        bluetoothGatt = null
                    }
                } else {
                    Log.e("BluetoothGatt", "Failed to read characteristic: $status")
                }
            }
        })
    }

    private fun isBatteryCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.uuid == UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    }
    private fun registerPairingReceiver() {
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(pairingReceiver, filter)
    }

    private fun unregisterPairingReceiver() {
        unregisterReceiver(pairingReceiver)
    }
    private val pairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)

            when (bondState) {
                BluetoothDevice.BOND_BONDED -> {
                    isDevicePaired = true
                    Toast.makeText(context, "Device paired successfully", Toast.LENGTH_SHORT).show()
                    // Optionally, you can now start playing music
                    playMusic()
                }
                BluetoothDevice.BOND_NONE -> {
                    isDevicePaired = false
                    Toast.makeText(context, "Device pairing failed", Toast.LENGTH_SHORT).show()
                }
                // Handle other bond states if necessary
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Handle disconnection
            }
        }
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                val batteryLevelChar = batteryService?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
                batteryLevelChar?.let {
                    gatt.readCharacteristic(it)
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                    val batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Battery Level: $batteryLevel%", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

  private  fun getBatteryLevel(pairedDevice: BluetoothDevice?, mContext: Context): Int {
        if (pairedDevice != null) {
            try {
                // Using reflection to call the getBatteryLevel method
                val batteryLevelObj =
                    pairedDevice.javaClass.getMethod("getBatteryLevel").invoke(pairedDevice)
                if (batteryLevelObj is Int) {
                    Toast.makeText(mContext, batteryLevelObj.toString(), Toast.LENGTH_SHORT).show()
                    return batteryLevelObj

                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
        return -1
    }
}
