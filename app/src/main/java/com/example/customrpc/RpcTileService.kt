package com.example.customrpc

import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class RpcTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    private fun updateTile() {
        val sharedPref = getSharedPreferences("RpcSettings", Context.MODE_PRIVATE)
        val isActive = sharedPref.getBoolean("serviceActive", false)
        
        val tile = qsTile ?: return
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isActive) "RPC: ON" else "RPC: OFF"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val sharedPref = getSharedPreferences("RpcSettings", Context.MODE_PRIVATE)
        val isActive = sharedPref.getBoolean("serviceActive", false)
        val token = sharedPref.getString("token", "") ?: ""

        if (isActive) {
            val intent = Intent(this, RpcService::class.java).apply { action = RpcService.ACTION_STOP }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } else {
            if (token.isBlank()) {
                Log.w("RpcTileService", "Cannot start RPC: Token is missing.")
                return
            }
            val intent = Intent(this, RpcService::class.java).apply {
                action = RpcService.ACTION_START
                putExtra("TOKEN", token)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }
        
        // Wait a bit for service to update state then refresh tile
        // Or we could use a broadcast, but this is simpler for now
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updateTile()
        }, 500)
    }
}
