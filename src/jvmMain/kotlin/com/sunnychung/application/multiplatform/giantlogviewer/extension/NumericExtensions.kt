package com.sunnychung.application.multiplatform.giantlogviewer.extension

internal fun Double.coerceToLong(): Long {
    return when {
        !java.lang.Double.isFinite(this) || this <= 0.0 -> 0L
        this >= Long.MAX_VALUE.toDouble() -> Long.MAX_VALUE
        else -> toLong()
    }
}

internal fun Long.toClampedInt(): Int {
    return coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}

internal val IntRange.safeEndExclusive: Int
    get() = if (last == Int.MAX_VALUE) Int.MAX_VALUE else last + 1

internal val LongRange.safeEndExclusive: Long
    get() = if (last == Long.MAX_VALUE) Long.MAX_VALUE else last + 1L
