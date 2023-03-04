package com.gvlab.yokobo.ui.config

interface BleManagerListener {
    fun onConnected()
    fun onReadData(data : String)
    fun onScanFinished()
}