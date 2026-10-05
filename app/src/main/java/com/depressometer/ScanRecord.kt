package com.depressometer

/** One completed scan. */
data class ScanRecord(
    val timestamp: Long,
    val score: Float,
    val level: String,
    val camera: String
)
