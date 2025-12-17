package com.example.customrpc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("RestartReceiver", "Broadcast received! Attempting to revive RpcService...")
        
        // Ambil data dari SharedPref karena Intent mungkin kosong
        val sharedPref = context.getSharedPreferences("RpcSettings", Context.MODE_PRIVATE)
        val token = sharedPref.getString("token", "")
        val appName = sharedPref.getString("appName", "Custom RPC")

        if (!token.isNullOrEmpty()) {
            val serviceIntent = Intent(context, RpcService::class.java).apply {
                action = RpcService.ACTION_START
                putExtra("TOKEN", token)
                putExtra("APP_NAME", appName)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
