package com.sunnychung.application.multiplatform.giantlogviewer.io

import java.io.ByteArrayOutputStream

class ByteArrayOutputStream2(size: Int) : ByteArrayOutputStream(size) {

    @Synchronized
    fun readAt(index: Int): Byte {
        if (index !in 0 ..< count) {
            throw IndexOutOfBoundsException("index should be within the range [0, $count)")
        }
        return buf[index]
    }

    @Synchronized
    fun readFromLast(negativeIndex: Int): Byte {
        if (negativeIndex !in -count ..< 0) {
            throw IndexOutOfBoundsException("negativeIndex should be negative and within the range [-$count, 0)")
        }
        return buf[count + negativeIndex]
    }

    @Synchronized
    fun readFromLastOrNull(negativeIndex: Int): Byte? {
        if (negativeIndex !in -count ..< 0) {
            return null
        }
        return buf[count + negativeIndex]
    }
}
