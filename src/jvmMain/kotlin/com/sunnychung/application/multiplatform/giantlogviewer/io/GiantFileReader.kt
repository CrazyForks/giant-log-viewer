package com.sunnychung.application.multiplatform.giantlogviewer.io

import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile
import java.util.LinkedList
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private const val BLOCK_CURRENT = 1

class GiantFileReader(private val filePath: String, private val blockSize: Int = 1 * 1024 * 1024) : AutoCloseable {
    private val file = RandomAccessFile(filePath, "r")

    private val blockCacheLock = ReentrantReadWriteLock()
    private val blockCache: Array<FileBlock?> = arrayOfNulls(4)
    private var bytePositions: LongRange = -1L .. -2L

    override fun close() {
        file.close()
    }

    fun lengthInBytes(): Long = file.length()

    private fun readBlock(block: FileBlockPosition, fileSize: Long): Pair<ByteArray, LongRange> {
        if (block.position < 0 || block.position > fileSize / blockSize) {
            throw IndexOutOfBoundsException("Attempt to read block ${block.position} from ${block.anchor} but there are only ${fileSize / blockSize} blocks. File size is ${fileSize}.")
        }
        if (block.anchor == FileAnchor.Start) {
            val readStart = block.position * blockSize
            val readLength = minOf(blockSize, (fileSize - readStart).toInt())
            val bytes = ByteArray(readLength)
            if (readLength > 0) {
                file.seek(readStart)
                file.read(bytes)
            }
            return bytes to (readStart ..< readStart + readLength)
        } else {
            throw NotImplementedError()
        }
    }

    private fun readBlockIfNotRead(block: FileBlockPosition): FileBlock {
        val fileSize = file.length()
        blockCache.forEach {
            if (it != null
                && it.pos == block
                && (!isLastBlock(it.pos, fileSize) || it.bytes.size == (fileSize % blockSize).toInt())
            ) {
                return it
            }
        }
        return readBlock(block, fileSize).let {
            FileBlock(block, it.first, it.second)
        }
    }

    private fun isLastBlock(pos: FileBlockPosition, fileSize: Long): Boolean {
        return if (pos.anchor == FileAnchor.Start) {
            pos.position >= fileSize / blockSize
        } else {
            pos.position == 0L
        }
    }

    private fun loadBlockPosition(block: FileBlockPosition) {
        val fileSize = file.length()
        if (block.position < 0 || block.position > fileSize / blockSize) {
            throw IndexOutOfBoundsException("Attempt to read block ${block.position} from ${block.anchor} but there are only ${fileSize / blockSize} blocks. File size is ${fileSize}.")
        }
        val currentBlock = readBlockIfNotRead(block)
        val prevBlock = block.takeIf { it.position > 0 }?.let {
            val pos = block.copy(position = it.position - 1)
            readBlockIfNotRead(pos)
        }
        val nextBlock1 = block.takeIf { it.position < fileSize / blockSize }?.let {
            val pos = block.copy(position = it.position + 1)
            readBlockIfNotRead(pos)
        }
        val nextBlock2 = block.takeIf { it.position + 1 < fileSize / blockSize }?.let {
            val pos = block.copy(position = it.position + 2)
            readBlockIfNotRead(pos)
        }
        blockCacheLock.write {
            blockCache[0] = prevBlock
            blockCache[1] = currentBlock
            blockCache[2] = nextBlock1
            blockCache[3] = nextBlock2 // this is for tracking multi-byte characters that start at the end of nextBlock1
            bytePositions = (blockCache.filterNotNull().minOfOrNull { it.bytePositions.first } ?: -1) ..
                (blockCache.filterNotNull().maxOfOrNull { it.bytePositions.last } ?: -2)
        }
    }

    private fun loadBytePosition(position: Long) {
//        val bytePositions = blockCacheLock.read { bytePositions }
//        if (position in bytePositions) {
//            return
//        }

        blockCacheLock.read {
            if (blockCache[BLOCK_CURRENT]?.bytePositions?.contains(position) == true) {
                return
            }
        }

        loadBlockPosition(FileBlockPosition(FileAnchor.Start, position / blockSize))
    }

    /**
     * This should be called within a lock scope
     */
    private fun read(positionInBlock: PositionInBlock?): Byte? {
        if (positionInBlock == null) {
            return null
        }
        return blockCache[positionInBlock.blockIndex]?.bytes?.get(positionInBlock.bytePosition)
    }

    internal fun readAsByteArrayOutputStream(startBytePosition: Long, length: Int): Pair<ByteArrayOutputStream2, LongRange> {
        // TODO optimize
        // TODO support emoji sequences
        // TODO refactor as a UTF-8 decoder

        if (length > blockSize) {
            throw IllegalArgumentException("`length` should be less than or equal a block size")
        }

        // Below works for ASCII or UTF-8 files

//        var readLength = length
        val bb = ByteArrayOutputStream2(length)
        var start: Long = startBytePosition
        var end: Long = -2L

        with (Utf8Decoder) {
            blockCacheLock.read {
                loadBytePosition(startBytePosition)

                val block = blockCache[BLOCK_CURRENT]!!
                val currentBlockReadStart: Int = (startBytePosition - block.bytePositions.start).toInt()

                if (startBytePosition > block.bytePositions.endInclusive) {
                    return bb to (startBytePosition .. -2)
                }

                if (block.bytes[currentBlockReadStart].isContinuationByte()) {
                    val lookBehindBytes = LinkedList<Byte>()
                    var prevBytePos = PositionInBlock(BLOCK_CURRENT, currentBlockReadStart) - 1
                    while (prevBytePos != null) {
                        val b = read(prevBytePos)!!
                        lookBehindBytes.addFirst(b)
                        --start
                        if (lookBehindBytes.size < 3 && b.isContinuationByte()) { // a UTF-8 char has a maximum length of 4 bytes
                            prevBytePos -= 1
                        } else {
                            break
                        }
                    }
                    bb.write(lookBehindBytes.toByteArray())
                }
                val currentBlockReadLength: Int = length.coerceAtMost((block.bytePositions.endExclusive - startBytePosition).toInt())
                var remainLength: Int = length
                bb.write(block.bytes, currentBlockReadStart, currentBlockReadLength)
                end = block.bytePositions.start + currentBlockReadStart + currentBlockReadLength
                var lastBlockPos: PositionInBlock? = PositionInBlock(BLOCK_CURRENT, currentBlockReadStart + currentBlockReadLength - 1)
                remainLength -= currentBlockReadLength
                if (remainLength > 0 && blockCache[BLOCK_CURRENT + 1] != null) {
                    val nextBlock = blockCache[BLOCK_CURRENT + 1]!!
                    var nextReadLength = remainLength.coerceAtMost(nextBlock.bytes.size)
                    bb.write(nextBlock.bytes, 0, nextReadLength)
                    end += nextReadLength
                    lastBlockPos = lastBlockPos?.plus(nextReadLength)
                }
                var headerByteOffset = -1
                while (bb.readFromLastOrNull(headerByteOffset)?.isContinuationByte() == true && headerByteOffset > -3) {
                    --headerByteOffset
                }
                if (bb.readFromLastOrNull(headerByteOffset)?.isHeaderOfByteSequence() == true) {
                    val byteSequenceLength = bb.readFromLastOrNull(headerByteOffset)?.sequenceLengthRepresentedByThisHeaderByte() ?: 1
                    remainLength = byteSequenceLength - (- headerByteOffset)
                    (0 ..< remainLength).forEach {
                        lastBlockPos = lastBlockPos?.plus(1)
                        val b = read(lastBlockPos)
                        if (b != null) {
                            bb.write(b.toInt())
                            ++end
                        }
                    }
                }
            }
        }

        return bb to (start ..< end)
    }

    /**
     * Given `length` should be less than or equal a block size.
     *
     * It is not guaranteed that exactly `length` bytes would be read.
     *
     * @param startBytePosition 0-based, in bytes
     * @param length in bytes
     * @return pair of decoded string and the range of absolute byte positions of the decoded string
     */
    fun readString(startBytePosition: Long, length: Int): Pair<String, LongRange> {
        val (bb, byteRange) = readAsByteArrayOutputStream(startBytePosition, length)
        return bb.toString(Charsets.UTF_8) to byteRange
    }

    fun readStringBytes(startBytePosition: Long, length: Int): Pair<ByteArray, LongRange> {
        val (bb, byteRange) = readAsByteArrayOutputStream(startBytePosition, length)
        return bb.toByteArray() to byteRange
    }

    private class FileBlock(val pos: FileBlockPosition, val bytes: ByteArray, val bytePositions: LongRange)

    private inner class PositionInBlock(val blockIndex: Int, val bytePosition: Int) {
        operator fun plus(byteOffset: Int): PositionInBlock? {
            blockCacheLock.read {
                if (blockIndex !in blockCache.indices) {
                    throw IndexOutOfBoundsException()
                }
                val block = blockCache[blockIndex] ?: return null
                val newBytePosition = block.bytePositions.start + bytePosition + byteOffset
                (blockIndex - 1..blockIndex + 1)
                    .forEach {
                        val block = blockCache.getOrNull(it) ?: return@forEach
                        if (newBytePosition in block.bytePositions) {
                            return PositionInBlock(it, (newBytePosition - block.bytePositions.start).toInt())
                        }
                    }
            }
            return null
        }

        operator fun minus(byteOffset: Int): PositionInBlock? = plus(- byteOffset)
    }
}

enum class FileAnchor {
    Start, End
}

data class FileRelativePosition(val anchor: FileAnchor, val position: Long)

typealias FileBlockPosition = FileRelativePosition

private object Utf8Decoder {
    fun Byte.isContinuationByte(): Boolean = toUByte() in 0x80u .. 0xBFu
    fun Byte.isHeaderOfByteSequence(): Boolean = toUByte() in 0xC2u .. 0xF4u

    fun Byte.sequenceLengthRepresentedByThisHeaderByte(): Int = when (toUByte()) {
        in 0xC2u .. 0xDFu -> 2
        in 0xE0u .. 0xEFu -> 3
        in 0xF0u .. 0xF4u -> 4
        else -> 1 // invalid header byte -> let Java transforms it as an error byte
    }
}
