package com.circapin.data.local

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate

class Converters {
    @TypeConverter
    fun localDateToEpochDays(date: LocalDate?): Long? = date?.toEpochDays()

    @TypeConverter
    fun epochDaysToLocalDate(epochDays: Long?): LocalDate? = epochDays?.let { LocalDate.fromEpochDays(it) }

    @TypeConverter
    fun floatsToText(values: List<Float>): String = values.joinToString(SEPARATOR)

    @TypeConverter
    fun textToFloats(text: String): List<Float> =
        if (text.isEmpty()) emptyList() else text.split(SEPARATOR).map(String::toFloat)

    private companion object {
        const val SEPARATOR = ","
    }
}
