package com.example.customrpc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.i("BootReceiver", "Device boot completed. Checking if RPC should restart...")
            
            val sharedPref = context.getSharedPreferences("RpcSettings", Context.MODE_PRIVATE)
            val shouldStartOnBoot = sharedPref.getBoolean("autoStartOnBoot", true)
            val wasActive = sharedPref.getBoolean("serviceActive", false)
            val token = sharedPref.getString("token", "") ?: ""

            if (shouldStartOnBoot && wasActive && token.isNotEmpty()) {
                Log.i("BootReceiver", "Restarting RpcService...")
                val serviceIntent = Intent(context, RpcService::class.java).apply {
                    action = RpcService.ACTION_START
                    putExtra("TOKEN", token)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                Log.i("BootReceiver", "RPC not restarted. (autoStart=$shouldStartOnBoot, wasActive=$wasActive)")
            }
        }
    }
}
