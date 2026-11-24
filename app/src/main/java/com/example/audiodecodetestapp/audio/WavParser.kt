package com.example.audiodecodetestapp.audio

import android.content.Context
import android.net.Uri
import java.io.InputStream

/**
 * 极简 WAV 解析：只读取 RIFF/WAVE 头部的 fmt 与 data 块，
 * 用于展示格式信息与定位 PCM 数据起点。
 */
object WavParser {

    fun parse(context: Context, uri: Uri): WavInfo? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { parse(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun parse(input: InputStream): WavInfo? {
        val riff = ByteArray(12)
        if (readFully(input, riff) != 12) return null
        if (ascii(riff, 0) != "RIFF") return null
        if (ascii(riff, 8) != "WAVE") return null

        var audioFormat = 0
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataOffset = -1L
        var dataSize = 0L
        var consumed = 12L

        val header = ByteArray(8)
        while (readFully(input, header) == 8) {
            val id = ascii(header, 0)
            val size = le32(header, 4)
            consumed += 8

            when (id) {
                "fmt " -> {
                    val fmt = ByteArray(size.toInt().coerceAtLeast(16))
                    if (readFully(input, fmt) < 16) return null
                    consumed += size
                    audioFormat = le16(fmt, 0)
                    channels = le16(fmt, 2)
                    sampleRate = le32(fmt, 4).toInt()
                    bitsPerSample = le16(fmt, 14)
                    if (size % 2 == 1L) {
                        skipFully(input, 1)
                        consumed += 1
                    }
                }

                "data" -> {
                    dataOffset = consumed
                    dataSize = size
                    break
                }

                else -> {
                    skipFully(input, size)
                    consumed += size
                    if (size % 2 == 1L) {
                        skipFully(input, 1)
                        consumed += 1
                    }
                }
            }
        }

        if (channels <= 0 || sampleRate <= 0 || bitsPerSample <= 0 || dataOffset < 0) {
            return null
        }
        return WavInfo(
            audioFormat = audioFormat,
            channels = channels,
            sampleRate = sampleRate,
            bitsPerSample = bitsPerSample,
            dataOffset = dataOffset,
            dataSize = dataSize
        )
    }

    fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) return
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n <= 0) break
            read += n
        }
        return read
    }

    private fun ascii(bytes: ByteArray, offset: Int): String =
        String(bytes, offset, 4, Charsets.US_ASCII)

    private fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun le32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
}
