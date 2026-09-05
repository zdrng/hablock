package dev.hablock.app.domain.model

import java.security.MessageDigest

private val HEX = CharArray(16) { "0123456789abcdef"[it] }

fun hashPasscode(code: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(code.toByteArray())
    val sb = StringBuilder(digest.size * 2)
    for (b in digest) {
        val v = b.toInt() and 0xff
        sb.append(HEX[v ushr 4])
        sb.append(HEX[v and 0x0f])
    }
    return sb.toString()
}

fun verifyPasscode(code: String, expectedHash: String?): Boolean =
    expectedHash != null && hashPasscode(code) == expectedHash
