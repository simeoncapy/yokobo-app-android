package com.gvlab.yokobo.ui.settings

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.gvlab.yokobo.R
import java.net.InetAddress
import java.util.*
import androidx.lifecycle.Observer


class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var contexte : Context

    companion object {
        fun newInstance() = SettingsFragment()
    }

    private lateinit var viewModel: SettingsViewModel
    private lateinit var sharedPref: SharedPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
        // Configuration page
        val configYokoboFragment: Preference? = findPreference(getString(R.string.setting_key_config))

        configYokoboFragment?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            view?.findNavController()?.navigate(R.id.navigation_config)
            true
        }

        // Yokobo information page
        val infoYokoboFragment : Preference? = findPreference(getString(R.string.setting_key_yokobo_info))
        infoYokoboFragment?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            view?.findNavController()?.navigate(R.id.navigation_info)
            true
        }

        // About page
        val aboutFragment: Preference? = findPreference(getString(R.string.setting_key_about))
        aboutFragment?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            view?.findNavController()?.navigate(R.id.navigation_about)
            true
        }

        // Language button
        val languageSelection: ListPreference = findPreference(getString(R.string.setting_key_language))!!
        languageSelection.setOnPreferenceChangeListener { preference, newValue ->
            if (preference is ListPreference) {
                val index = preference.findIndexOfValue(newValue.toString())
                //val entry = preference.entries[index] as String
                val value = preference.entryValues[index] as String
                //Log.e("Lang", value)
                activity?.let { this.setLocale(it, value) }
            }
            true
        }
    }


    /*override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }
*/
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        sharedPref = activity?.getPreferences(Context.MODE_PRIVATE)!!
        viewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)

        viewModel.isYokoboConnected.observe(viewLifecycleOwner, Observer { isConnected ->
            val yokoboInfo: Preference = findPreference(getString(R.string.setting_key_yokobo_info))!!
            if (isConnected)    yokoboInfo.summary = getString(R.string.setting_yokobo_info_connected)
            else                yokoboInfo.summary = getString(R.string.setting_yokobo_info_scan)
        })

        sharedPref.let { viewModel.checkIP(it) }

        val isConfigured = sharedPref.getString(getString(R.string.key_shared_preference_yokobo_configured), "false") ?: "false"
        val yokoboConfig: Preference = findPreference(getString(R.string.setting_key_config))!!
        if(isConfigured == "true")
        {
            yokoboConfig.title = getString(R.string.setting_yokobo_configured)
            yokoboConfig.summary = getString(R.string.setting_yokobo_reconfigure)
        }
        else
        {
            yokoboConfig.title = getString(R.string.setting_yokobo_not_configured)
            yokoboConfig.summary = getString(R.string.setting_yokobo_configure)
        }
    }

    private fun setLocale(activity: Activity, languageCode: String?) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val resources: Resources = activity.resources
        val config: Configuration = resources.getConfiguration()
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.getDisplayMetrics())
    }
}