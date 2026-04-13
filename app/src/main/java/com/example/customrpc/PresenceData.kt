package com.example.customrpc

import java.io.Serializable

data class PresenceData(
    val appId: String,
    val name: String,
    val details: String,
    val state: String,
    val largeImageKey: String,
    val largeImageText: String,
    val smallImageKey: String,
    val smallImageText: String,
    val activityType: Int, // 0=Playing, 2=Listening, 3=Watching, 5=Competing
    val partySize: Int?,
    val partyMax: Int?,
    val button1Label: String,
    val button1Url: String,
    val button2Label: String,
    val button2Url: String,
    val timestampStart: Long?,
    val timestampEnd: Long?,
    val partyId: String? = null,
    val joinSecret: String? = null,
    val userStatus: String = "online"
) : Serializable
