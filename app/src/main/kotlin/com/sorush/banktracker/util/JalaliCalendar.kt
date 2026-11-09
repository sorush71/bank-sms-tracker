package com.sorush.banktracker.util

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

data class JalaliDate(
    val year: Int,
    val month: Int,
    val day: Int
) {
    val monthName: String
        get() = JalaliCalendar.MONTH_NAMES.getOrElse(month - 1) { "" }

    fun formatted(): String = String.format("%04d/%02d/%02d", year, month, day)
    fun monthTitle(): String = "$monthName $year"
}

object JalaliCalendar {

    val MONTH_NAMES = listOf(
        "فروردین", "اردیبهشت", "خرداد",
        "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر",
        "دی", "بهمن", "اسفند"
    )

    fun fromTimestamp(timestamp: Long): JalaliDate {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"))
        cal.timeInMillis = timestamp
        val gYear = cal.get(Calendar.YEAR)
        val gMonth = cal.get(Calendar.MONTH) + 1
        val gDay = cal.get(Calendar.DAY_OF_MONTH)
        return gregorianToJalali(gYear, gMonth, gDay)
    }

    fun now(): JalaliDate {
        return fromTimestamp(System.currentTimeMillis())
    }

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += ((days - 1) / 365)
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return JalaliDate(jy, jm, jd)
    }

    fun getMonthRangeTimestamps(jYear: Int, jMonth: Int): Pair<Long, Long> {
        // Approximate start and end timestamps for a Jalali month
        // Find 1st day of this month
        val startCal = jalaliToGregorianCalendar(jYear, jMonth, 1, 0, 0, 0)
        val daysInMonth = when {
            jMonth <= 6 -> 31
            jMonth <= 11 -> 30
            isLeapJalaliYear(jYear) -> 30
            else -> 29
        }
        val endCal = jalaliToGregorianCalendar(jYear, jMonth, daysInMonth, 23, 59, 59)
        return Pair(startCal.timeInMillis, endCal.timeInMillis)
    }

    private fun isLeapJalaliYear(jy: Int): Boolean {
        val r = (jy % 33)
        return r == 1 || r == 5 || r == 9 || r == 13 || r == 17 || r == 22 || r == 26 || r == 30
    }

    fun jalaliToGregorianCalendar(jy: Int, jm: Int, jd: Int, hour: Int, min: Int, sec: Int): Calendar {
        var days = (jy - 979) * 365 + ((jy - 979) / 33) * 8 + (((jy - 979) % 33 + 3) / 4)
        for (i in 0 until (jm - 1)) {
            days += if (i < 6) 31 else 30
        }
        days += jd - 1

        var gy = 1600 + 400 * (days / 146097)
        days %= 146097
        if (days >= 36525) {
            days--
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days >= 366) {
            days--
            gy += days / 365
            days %= 365
        }

        val gDaysInMonth = intArrayOf(31, if ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 12 && days >= gDaysInMonth[gm]) {
            days -= gDaysInMonth[gm]
            gm++
        }
        val gd = days + 1

        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"))
        cal.set(gy, gm, gd, hour, min, sec)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }
}
