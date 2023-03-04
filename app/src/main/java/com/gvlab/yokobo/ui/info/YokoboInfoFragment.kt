package com.gvlab.yokobo.ui.info

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.text.Html
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.preference.Preference
import com.gvlab.yokobo.R
import com.gvlab.yokobo.databinding.FragmentConfigWifiBinding
import com.gvlab.yokobo.databinding.FragmentYokoboInfoBinding
import com.gvlab.yokobo.ui.settings.SettingsViewModel

class YokoboInfoFragment : Fragment() {

    companion object {
        fun newInstance() = YokoboInfoFragment()
    }

    private lateinit var viewModel: YokoboInfoViewModel
    private lateinit var viewModelSetting: SettingsViewModel
    private lateinit var sharedPref: SharedPreferences

    private var _binding: FragmentYokoboInfoBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentYokoboInfoBinding.inflate(inflater, container, false)
        return binding.root
        //return inflater.inflate(R.layout.fragment_yokobo_info, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        sharedPref = activity?.getPreferences(Context.MODE_PRIVATE)!!
        val ip = sharedPref.getString(getString(R.string.key_shared_preference_yokobo_ip), "0.0.0.0") ?: "0.0.0.0"
        viewModel = ViewModelProvider(this, viewModelFactory { YokoboInfoViewModel(ip) }).get(YokoboInfoViewModel::class.java)

        viewModelSetting = ViewModelProvider(this).get(SettingsViewModel::class.java)

        viewModelSetting.isYokoboConnected.observe(viewLifecycleOwner, Observer { isConnected ->
            var txtIp = binding.textInfoIp
            txtIp.text = ip
            /*if(isConnected) txtIp.compoundDrawables[0].setTint(resources.getColor(R.color.y_green))
            else txtIp.compoundDrawables[0].setTint(resources.getColor(R.color.y_dark_red))*/
        })

        var txtState = binding.textInfoState
        var txt = "<b><u>" + getString(R.string.info_state_state) + ":</u> " + getString(R.string.info_state_soh) + "</b><br/>" + getString(R.string.info_state_soh_desc)
        txtState.text = Html.fromHtml(txt)
    }

    protected inline fun <VM : ViewModel> viewModelFactory(crossinline f: () -> VM) =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(aClass: Class<T>):T = f() as T
        }

    override fun onStop() {
        super.onStop()
        viewModel.stopNep()
    }

    override fun onStart() {
        super.onStart()
        // Check if the IP changed since last time
        sharedPref.getString(getString(R.string.key_shared_preference_yokobo_ip), "0.0.0.0")
            ?.let { viewModel.checkIp(it) }
        viewModel.runNep()
    }
}