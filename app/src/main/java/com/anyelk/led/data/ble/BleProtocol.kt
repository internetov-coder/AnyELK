package com.anyelk.led.data.ble

import java.util.UUID

/**
 * Byte-level command set for ELK-BLEDOB / Lotus Lantern LED controllers.
 *
 * Frame layout (9 bytes):
 *   7E | len | cmd | p0 | p1 | p2 | p3 | p4 | EF
 *
 * Sources:
 *  - github.com/8none1/elk-bledob (nRF52840 sniffer captures)
 *  - github.com/FergusInLondon/ELK-BLEDOM (PROTCOL.md, decompiled duoCo app)
 *  - decompiled Lotus Lamp X / Evozi Smart LED services (pin sequence, time sync)
 */
object BleProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")

    /** Advertising names that typically belong to supported LED controllers. */
    val KNOWN_NAME_PREFIXES = listOf("ELK", "MELK", "LEDBLE", "XROCKER", "LOTUS", "LANTERN")

    private const val HEAD = 0x7E
    private const val TAIL = 0xEF

    fun isKnownDeviceName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val upper = name.uppercase()
        return KNOWN_NAME_PREFIXES.any { upper.startsWith(it) }
    }

    /** Power on: 7E 07 04 FF 00 01 02 01 EF */
    fun powerOn(): ByteArray = frame(0x07, 0x04, 0xFF, 0x00, 0x01, 0x02, 0x01)

    /** Power off: 7E 07 04 00 00 00 02 01 EF */
    fun powerOff(): ByteArray = frame(0x07, 0x04, 0x00, 0x00, 0x00, 0x02, 0x01)

    /**
     * Static RGB color: 7E 07 05 03 R G B 10 EF.
     *
     * The payload is ALWAYS logical [R, G, B]: the controller reads byte 4 as
     * red regardless of wiring. Physical channel order is handled separately
     * by the pin-sequence command (see [pixelOrder]) — remapping this packet
     * on top of pin-seq would double-transform and swap channels.
     */
    fun color(red: Int, green: Int, blue: Int): ByteArray = frame(
        0x07, 0x05, 0x03,
        red.coerceIn(0, 255),
        green.coerceIn(0, 255),
        blue.coerceIn(0, 255),
        0x10
    )

    /** Brightness 0-100%: 7E 04 01 <pct> 01 FF 02 01 EF */
    fun brightness(percent: Int): ByteArray = frame(
        0x04, 0x01, percent.coerceIn(0, 100), 0x01, 0xFF, 0x02, 0x01
    )

    /** Effect speed 0-100: 7E 07 02 <pct> FF FF FF 00 EF */
    fun speed(percent: Int): ByteArray = frame(
        0x07, 0x02, percent.coerceIn(0, 100), 0xFF, 0xFF, 0xFF, 0x00
    )

    /** Built-in effect (0x87..0x9C): 7E 07 03 <id> 03 FF FF 00 EF */
    fun effect(id: Int): ByteArray = frame(0x07, 0x03, id, 0x03, 0xFF, 0xFF, 0x00)

    /**
     * Pin sequence: 7E 06 81 <pinR> <pinG> <pinB> FF 00 EF.
     *
     * Semantics taken from the stock app's PinSequenceActivity (decompiled):
     * the three bytes are [pin of RED, pin of GREEN, pin of BLUE] (1-based).
     * The controller routes the logical RGB packet onto those pins, which is
     * how GRB-wired strips get corrected — the color packet itself must stay
     * logical [R, G, B].
     */
    fun pixelOrder(order: PixelOrder): ByteArray {
        val p = order.pins
        return frame(0x06, 0x81, p[0], p[1], p[2], 0xFF, 0x00)
    }

    /**
     * Time sync sent by the stock app on every connection:
     * 7E 06 83 HH MM SS DOW 00 EF (DOW = ISO weekday, Mon=1..Sun=7).
     */
    fun timeSync(hour: Int, minute: Int, second: Int, dayOfWeek: Int): ByteArray = frame(
        0x06, 0x83, hour, minute, second, dayOfWeek, 0x00
    )

    private fun frame(len: Int, cmd: Int, p0: Int, p1: Int, p2: Int, p3: Int, p4: Int): ByteArray =
        byteArrayOf(
            HEAD.toByte(), len.toByte(), cmd.toByte(),
            p0.toByte(), p1.toByte(), p2.toByte(), p3.toByte(), p4.toByte(),
            TAIL.toByte()
        )
}

/**
 * Linear channel sequence of the strip: [pinR, pinG, pinB] — the 1-based pin
 * that carries each logical color, matching the stock PinSequenceActivity UI
 * (row = color, column = pin, send = [pinR+1, pinG+1, pinB+1]).
 *
 * GRB (pin1=green, pin2=red) = [02, 01, 03] — verified against hardware that
 * lit green for the stock RGB command.
 */
enum class PixelOrder(val pins: IntArray) {
    RGB(intArrayOf(0x01, 0x02, 0x03)),
    RBG(intArrayOf(0x01, 0x03, 0x02)),
    GRB(intArrayOf(0x02, 0x01, 0x03)),
    GBR(intArrayOf(0x03, 0x01, 0x02)),
    BRG(intArrayOf(0x02, 0x03, 0x01)),
    BGR(intArrayOf(0x03, 0x02, 0x01));

    companion object {
        /**
         * Default is GRB: the common ELK-BLEDOB clones wire WS2812-style
         * dies green-first (verified: stock RGB command lit the green die).
         */
        fun fromName(name: String?): PixelOrder =
            entries.firstOrNull { it.name == name } ?: GRB
    }
}
