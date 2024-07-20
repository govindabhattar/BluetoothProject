package com.example.bluetoothproject

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(private val devices: List<BluetoothDevice>) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private var onPairClickListener: ((BluetoothDevice) -> Unit)? = null
    private var onUnpairClickListener: ((BluetoothDevice) -> Unit)? = null
    private var onBatteryClickListener: ((BluetoothDevice) -> Unit)? = null

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deviceName: TextView = itemView.findViewById(R.id.device_name)
        private val pairButton: Button = itemView.findViewById(R.id.pair_button)
        private val unpairButton: Button = itemView.findViewById(R.id.unpair_button)
        private val batteryButton: Button = itemView.findViewById(R.id.battery_button)

        @SuppressLint("MissingPermission")
        fun bind(device: BluetoothDevice) {
            deviceName.text = device.name ?: "Unknown Device"

            pairButton.setOnClickListener { onPairClickListener?.invoke(device) }
            unpairButton.setOnClickListener { onUnpairClickListener?.invoke(device) }
            batteryButton.setOnClickListener { onBatteryClickListener?.invoke(device) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_paired_device_item, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    fun setOnPairClickListener(listener: (BluetoothDevice) -> Unit) {
        onPairClickListener = listener
    }

    fun setOnUnpairClickListener(listener: (BluetoothDevice) -> Unit) {
        onUnpairClickListener = listener
    }

    fun setOnBatteryClickListener(listener: (BluetoothDevice) -> Unit) {
        onBatteryClickListener = listener
    }
}
