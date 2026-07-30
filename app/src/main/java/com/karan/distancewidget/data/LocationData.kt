package com.karan.distancewidget.data

data class LocationData(
    val lat: Double,
    val lng: Double,
    val ts: Long            // epoch ms — used to detect stale location
)
