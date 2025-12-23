package org.blockstack.android.sdk

import org.kethereum.crypto.getCompressedPublicKey
import org.kethereum.model.ECKeyPair
import org.komputing.kbase58.encodeToBase58String
import org.komputing.khash.ripemd160.extensions.digestRipemd160
import org.komputing.khash.sha256.extensions.sha256
import org.komputing.khex.extensions.toNoPrefixHexString
import org.komputing.khex.extensions.hexToByteArray
import org.komputing.khex.model.HexString

fun String.toBtcAddress(): String {
    val sha256 = HexString(this).hexToByteArray().sha256()
    val hash160 = sha256.digestRipemd160()
    val extended = "00${hash160.toNoPrefixHexString()}"
    val checksum = checksum(extended)
    val address = HexString(extended + checksum).hexToByteArray().encodeToBase58String()
    return address
}

private fun checksum(extended: String): String {
    val checksum = HexString(extended).hexToByteArray().sha256().sha256()
    val shortPrefix = checksum.slice(0..3)
    return shortPrefix.toNoPrefixHexString()
}
fun ECKeyPair.toHexPublicKey64(): String {
    return this.getCompressedPublicKey().toNoPrefixHexString()
}

fun ECKeyPair.toBtcAddress(): String {
    val publicKey = toHexPublicKey64()
    return publicKey.toBtcAddress()
}
