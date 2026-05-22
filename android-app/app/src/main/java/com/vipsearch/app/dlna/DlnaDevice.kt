package com.vipsearch.app.dlna

data class DlnaDevice(
    val usn: String,
    val friendlyName: String,
    val controlUrl: String,
    val location: String,
    val lastSeen: Long = System.currentTimeMillis()
)
