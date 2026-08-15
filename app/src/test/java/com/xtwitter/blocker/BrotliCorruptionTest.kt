package com.xtwitter.blocker

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class BrotliCorruptionTest {

    @Test
    fun testBinaryByteCorruptionThroughUtf8String() {
        // Random binary payload simulating Brotli or compressed stream
        val randomBytes = ByteArray(1024)
        Random(42).nextBytes(randomBytes)

        // Simulating: String(rawBytes, Charsets.UTF_8).toByteArray(Charsets.UTF_8)
        val stringRepresentation = String(randomBytes, Charsets.UTF_8)
        val reEncodedBytes = stringRepresentation.toByteArray(Charsets.UTF_8)

        // In almost 100% of binary cases, UTF-8 decoding replaces invalid bytes with \uFFFD (3 bytes: 0xEF 0xBF 0xBD)
        // so reEncodedBytes will NOT match randomBytes!
        val isCorrupted = !randomBytes.contentEquals(reEncodedBytes)
        assertTrue("Binary compressed data must be corrupted when decoded as UTF-8 string", isCorrupted)
        println("Original length: ${randomBytes.size}, Corrupted length: ${reEncodedBytes.size}")
    }
}
