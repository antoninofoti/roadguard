package com.example.roadguard.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Report(
    @DocumentId
    val id: String = "",
    val imageUrl: String = "",
    val location: GeoPoint? = null,
    @ServerTimestamp
    val timestamp: Date? = null,
    val userId: String = "",
    val severity: Float = 0f
)
