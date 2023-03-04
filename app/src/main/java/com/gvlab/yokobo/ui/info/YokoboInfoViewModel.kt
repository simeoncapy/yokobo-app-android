package com.gvlab.yokobo.ui.info

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.beust.klaxon.Parser.Companion.default
import nep.Node
import nep.Subscriber
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class YokoboInfoViewModel(ip: String) : ViewModel() {
    // TODO: Implement the ViewModel
    private var port = 9093
    private var node: Node = Node("node_android")
    //var pub: Publisher = node!!.new_pub("test", ip, port, "one2many")
    var sub: Subscriber = node.new_sub("test_android", ip, port, "one2many")
    private var _ip = ip
    var isRunning = false
    private var killNep = false

    fun runNep()
    {
        viewModelScope.launch(Dispatchers.IO) {
            isRunning = true
            val parser: Parser = default()
            while (true) {
                if(killNep) break
                val msg = sub.listen()
                if ("{}" != msg) {
                    Log.e("NEP", msg)
                    //println(msg)
                    // From JSON string to Java object
                    //val gson = Gson()
                    //val obj: json_value = gson.fromJson(msg, json_value::class.java)
                    //System.out.println(obj.message)
                    val stringBuilder: StringBuilder = StringBuilder(msg)
                    val json: JsonObject = parser.parse(stringBuilder) as JsonObject
                    json.string("test")?.let { Log.i("NEP", it) }
                }
            }
            isRunning = false
            Log.e("NEP", "stopped")
        }
    }

    fun checkIp(ip: String)
    {
        if(_ip != ip)
        {
            stopNep()
            //pub = node!!.new_pub("test", ip, port, "one2many")
            sub = node.new_sub("test", ip, port, "one2many")
            _ip = ip
        }
    }

    fun stopNep()
    {
        killNep = true
        while (isRunning) Thread.sleep(50)
        killNep = false
    }
}