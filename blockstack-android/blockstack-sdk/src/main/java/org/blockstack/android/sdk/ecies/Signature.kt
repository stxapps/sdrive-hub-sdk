package org.blockstack.android.sdk.ecies

import org.blockstack.android.sdk.model.SignatureObject
import org.blockstack.android.sdk.toHexPublicKey64
import org.kethereum.crypto.signMessageHash
import org.kethereum.crypto.toECKeyPair
import org.kethereum.model.PrivateKey
import org.kethereum.model.SignatureData
import org.komputing.khash.sha256.extensions.sha256
import org.komputing.khex.extensions.toNoPrefixHexString
import org.komputing.khex.model.HexString
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.math.ln
import kotlin.math.log10

fun signContent(content: Any, privateKey: String, toCanonical: Boolean = false): SignatureObject {
    val contentBuffer = if (content is ByteArray) {
        content
    } else {
        (content as String).toByteArray()
    }
    val keyPair = PrivateKey(HexString(privateKey)).toECKeyPair()
    val sigData = signMessageHash(contentBuffer.sha256(), keyPair, toCanonical)

    val signatureString = sigData.toDER()
    return SignatureObject(signatureString, keyPair.toHexPublicKey64())
}

data class Position(var place: Int)

const val ZERO = 0.toByte()
const val LENGTH = 0x80.toByte() // 128

fun addSize(arr: MutableList<Byte>, len: Int) {
    if (len < 128) {
        arr.add(len.toByte())
        return
    }
    val l = (log10(len.toDouble()) / ln(2.toDouble())).toInt() ushr 3
    var octets = 1 + l
    arr.add(octets.toByte() or LENGTH)
    octets -= 1
    while (octets.toByte() != ZERO) {
        arr.add(((len ushr (octets shl 3)) and 0xff).toByte())
    }
    arr.add(len.toByte())
}


fun getLength(buf: ByteArray, p: Position): Byte {
    val initial = buf[p.place++]
    if (initial and LENGTH == ZERO) {
        return initial
    }
    val octetLen = initial and 0xf.toByte()
    var value = 0
    var off = p.place
    for (i in 0 until octetLen) {
        value = value shl 8
        value = value or buf[off].toInt()
        off += 1
    }
    p.place = off
    return value.toByte()
}

fun rmPadding(buf: ByteArray): ByteArray {
    var i = 0
    val len = buf.size - 1
    while (buf[i] == ZERO && (buf[i + 1] and LENGTH) == ZERO && i < len) {
        i++
    }
    if (i == 0) {
        return buf
    }
    return buf.sliceArray(i until buf.size)
}

fun SignatureData.toDER(): String {
    var r = this.r.toByteArray()
    var s = this.s.toByteArray()

    // Pad values
    if (r[0] and LENGTH != ZERO) {
        r = byteArrayOf(0) + r
    }
    // Pad values
    if (s[0] and LENGTH != ZERO) {
        s = byteArrayOf(0) + s
    }

    r = rmPadding(r)
    s = rmPadding(s)

    while (s[0] == ZERO && (s[1] and LENGTH) == ZERO) {
        s = s.sliceArray(1 until s.size)
    }
    val arr = mutableListOf<Byte>(0x02)
    addSize(arr, r.size)
    arr.addAll(r.toTypedArray())

    arr.add(0x02.toByte())
    addSize(arr, s.size)
    arr.addAll(s.toTypedArray())

    val res = mutableListOf<Byte>(0x30)
    addSize(res, arr.size)
    res.addAll(arr)

    return res.toNoPrefixHexString()
}
