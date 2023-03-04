package com.gvlab.yokobo.ui.config

import android.content.res.Resources
import android.net.wifi.ScanResult
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gvlab.yokobo.R
import kotlinx.android.synthetic.main.config_wifi_row_scan_result.view.*
import org.jetbrains.anko.layoutInflater
import kotlin.math.round

class ScanResultWifiAdapter(
    private val items: List<ScanResult>,
    private val onClickListener: ((wifi: ScanResult) -> Unit)
) : RecyclerView.Adapter<ScanResultWifiAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = parent.context.layoutInflater.inflate(
            R.layout.config_wifi_row_scan_result,
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
        private val onClickListener: ((wifi: ScanResult) -> Unit)
    ) : RecyclerView.ViewHolder(view) {

        fun bind(result: ScanResult) {
            view.ssid.text = result.SSID

            var freq : Double = result.frequency.toDouble()
            freq = round(freq / 100.0) / 10.0
            view.frequency.text = freq.toString() + " GHz"//Resources.getSystem().getString(R.string.config_wifi_frequency, freq.toString())

            //Log.i("Secu", result.SSID + " " + result.capabilities)
            if(result.capabilities.contains("WPA") || result.capabilities.contains("RSN"))
                view.security.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_wifi_lock, 0)
            else
                view.security.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_wifi, 0)

            if(result.level > -60) {
                view.signal_strength.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_signal_full, 0)
            } else if (result.level > -85)
                view.signal_strength.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_signal_2_bar, 0)
            else
                view.signal_strength.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_signal_1_bar, 0)
            view.setOnClickListener { onClickListener.invoke(result) }
        }
    }
}