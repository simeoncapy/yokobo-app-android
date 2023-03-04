package com.gvlab.yokobo.ui.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gvlab.yokobo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.InetAddress


class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    // TODO: Implement the ViewModel
    val isYokoboConnected: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }

    init {
        isYokoboConnected.value = false
    }

    fun checkIP(sharedPref:SharedPreferences)
    {
        viewModelScope.launch(Dispatchers.IO) {
            val ip = sharedPref.getString(getApplication<Application>().resources.getString(R.string.key_shared_preference_yokobo_ip), "empty")
            if (ip != null && ip != "empty") {
                Log.i("Share", ip)
                var inet = InetAddress.getByName(ip)

                Log.i("IP add", "Sending Ping Request to $ip")
                isYokoboConnected.postValue( if (inet.isReachable(5000)) {
                        Log.i("IP add", "Host is reachable")
                        true
                    } else {
                        Log.i("IP add", "Host is NOT reachable")
                        false
                    }
                )
            }
        }
    }
}