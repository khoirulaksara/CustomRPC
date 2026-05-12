package com.example.customrpc

import java.io.Serializable

data class AppOverride(
    val packageName: String,
    val appName: String,
    val rpcData: PresenceData
) : Serializable
