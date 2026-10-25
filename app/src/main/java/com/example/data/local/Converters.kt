package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.ScreenshotStatus

class Converters {
    @TypeConverter
    fun fromScreenshotStatus(status: ScreenshotStatus): String {
        return status.name
    }

    @TypeConverter
    fun toScreenshotStatus(value: String?): ScreenshotStatus {
        return value?.let {
            try {
                ScreenshotStatus.valueOf(it)
            } catch (e: Exception) {
                ScreenshotStatus.PROCESSED
            }
        } ?: ScreenshotStatus.PROCESSED
    }
}
