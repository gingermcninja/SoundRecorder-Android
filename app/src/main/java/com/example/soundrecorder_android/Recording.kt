package com.example.soundrecorder_android

data class Recording(
    val id: Long,
    val name: String,
    val filePath: String,
    val durationSeconds: Int,
)
