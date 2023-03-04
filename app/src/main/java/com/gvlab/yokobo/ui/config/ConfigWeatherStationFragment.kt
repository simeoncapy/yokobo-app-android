package com.gvlab.yokobo.ui.config

import android.content.Context
import android.content.res.Configuration
import android.net.http.SslError
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.gvlab.yokobo.databinding.FragmentConfigWeatherStationBinding

import android.widget.ProgressBar
import android.os.Build
import android.webkit.*
import androidx.annotation.RequiresApi
import android.webkit.WebSettings
import com.gvlab.yokobo.R


class ConfigWeatherStationFragment : Fragment(), BleManagerListener {

    companion object {
        fun newInstance() = ConfigWeatherStationFragment()
    }

    private val webViewClient = object : WebViewClient() {

        @RequiresApi(Build.VERSION_CODES.Q)
        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            binding.configWsPBar.visibility = ProgressBar.GONE

            if (url != null) {
                //if(url.startsWith("https://localhost:8080/")) {
                if(url.startsWith("https://www.yokobo.org/ws")) {
                    Log.i("WS", "replaced")
                    view.loadUrl("file:///android_asset/ws.html")
                    binding.textConfigWsWeb.visibility = WebView.GONE
                    //askAuthorisation("file:///android_asset/ws.html")
                    //binding.textConfigWsWeb.loadUrl(url.replace("https://localhost:8080/", "file:///"))
                    bleManager.writeMessage(BleManager.FUNCTION_WEATHER_STATION, BleManager.WEATHER_STATION_TOKEN, url)
                }
                Log.i("Loaded", url)
            }
        }


        override fun onLoadResource(view: WebView?, url: String?) {
            super.onLoadResource(view, url)
            //binding.configWsPBar.visibility = ProgressBar.VISIBLE
        }

        //@RequiresApi(Build.VERSION_CODES.M)
        /*@RequiresApi(Build.VERSION_CODES.M)
        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            Log.e("WebErr", error?.errorCode?.toString() + ": " + error?.description)
            //view?.stopLoading()
            //view?.loadUrl("file:///android_asset/ws.html")
            if (view?.url != null) {
                //if(url.startsWith("https://localhost:8080/")) {
                if(view?.url!!.startsWith("https://www.yokobo.org/ws")) {
                    Log.i("WebErr", "replaced")
                    binding.textConfigWsWeb.loadUrl("file:///android_asset/ws.html")
                    //askAuthorisation("file:///android_asset/ws.html")
                    //binding.textConfigWsWeb.loadUrl(url.replace("https://localhost:8080/", "file:///"))
                    //bleManager.writeMessage(BleManager.FUNCTION_WEATHER_STATION, BleManager.WEATHER_STATION_TOKEN, view?.url)
                }
                Log.i("WebErr", view?.url!!)
            }
            else
                Log.i("WebErr", "URL NULL")
        }*/

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            errorResponse?.toString()?.let { Log.e("WebErrHttp", it) }
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError?) {
            Log.e("WebErrSsl", error.toString())
            handler.proceed()
            super.onReceivedSslError(view, handler, error)

        }
    }

    private var _binding: FragmentConfigWeatherStationBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var bleManager: BleManager

    private lateinit var viewModel: ConfigWeatherStationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleManager = arguments?.getParcelable("bleManager")!!
        bleManager.addListener(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentConfigWeatherStationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(ConfigWeatherStationViewModel::class.java)
        // TODO: Use the ViewModel

        bleManager.writeMessage(BleManager.FUNCTION_WEATHER_STATION, BleManager.WEATHER_STATION_REQUEST)

    }

    override fun onConnected() {
        Log.e("ConfigWeatherStation", "No connection to BT should be done here")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onReadData(data: String) {
        Log.i("WS", data)
        if(URLUtil.isValidUrl(data))
        {
            askAuthorisation(data)
        }
        else
        {
            val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            var webView = binding.textConfigWsWeb
            webView.post(Runnable {
                webView.settings.allowContentAccess = true
                webView.settings.allowFileAccess = true
                webView.settings.javaScriptEnabled = true
                /*webView.settings.useWideViewPort = true*/
                if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                    webView.settings.forceDark = WebSettings.FORCE_DARK_ON
                }
                var dataUrl = data.replace("(", "").replace(")", "").replace(", ", "&out=")
                Log.i("WS", dataUrl)
                webView.webViewClient = webViewClient
                webView.loadUrl("file:///android_asset/ws-end.html?in=$dataUrl")
                binding.textConfigWsWeb.visibility = WebView.VISIBLE
            })

            val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
            with (sharedPref.edit()) {
                putString(getString(R.string.key_shared_preference_yokobo_configured), "true")
                //putString(getString(R.string.key_shared_preference_weather_station_id), data) TODO
                apply()
            }

            Log.i("Temperature", data) // change code to send station name + temperature
            bleManager.writeMessage(BleManager.FUNCTION_WEATHER_STATION, BleManager.WEATHER_STATION_CLOSE)
        }
    }

    override fun onScanFinished() {
        TODO("Not yet implemented")
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    private fun askAuthorisation(url: String)
    {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        var webView = binding.textConfigWsWeb
        webView.post(Runnable {
            webView.settings.allowContentAccess = true
            webView.settings.allowFileAccess = true
            webView.settings.javaScriptEnabled = true
            /*webView.settings.useWideViewPort = true*/
            if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                webView.settings.forceDark = WebSettings.FORCE_DARK_ON
            }
            webView.webViewClient = webViewClient
            webView.loadUrl(url)
            //webView.loadUrl("file:///android_asset/ws.html")
        })
    }
}