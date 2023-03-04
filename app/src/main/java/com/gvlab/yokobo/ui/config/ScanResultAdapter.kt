package com.gvlab.yokobo.ui.config

import android.bluetooth.le.ScanResult
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gvlab.yokobo.R
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.config_row_scan_result.view.*
import org.jetbrains.anko.layoutInflater

class ScanResultAdapter(
    private val items: List<ScanResult>,
    private val onClickListener: ((device: ScanResult) -> Unit)
) : RecyclerView.Adapter<ScanResultAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        Log.e("Parent", parent.toString())
        val view = parent.context.layoutInflater.inflate(
            R.layout.config_row_scan_result,
            parent,
            false
        )
        return ViewHolder(view, onClickListener)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    class ViewHolder(
        private val view: View,
        private val onClickListener: ((device: ScanResult) -> Unit)
    ) : RecyclerView.ViewHolder(view) {

        fun bind(result: ScanResult) {
            view.device_name.text = result.device.name ?: "Unnamed"
            view.mac_address.text = result.device.address
            //view.signal_strength.text = "${result.rssi} dBm"
            if(result.rssi > -60) {
                view.signal_strength.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_signal_full, 0)
            } else if (result.rssi > -85)
                view.signal_strength.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_signal_2_bar, 0)
            else
                view.signal_strength.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_signal_1_bar, 0)
            view.setOnClickListener { onClickListener.invoke(result) }
        }
    }
}