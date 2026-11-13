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
     * Static RGB color: 7E 07 05 03 <b0> <b1> <b2> 10 EF.
     *
     * The three payload bytes follow the strip's linear pixel order: for an
     * RGB strip b0=red, while for the common GRB hardware b0=green. Remapping
     * here keeps every caller (presets, color wheel, music FFT) correct.
     */
    fun color(order: PixelOrder, red: Int, green: Int, blue: Int): ByteArray {
        val ch = order.remap(red, green, blue)
        return frame(0x07, 0x05, 0x03, ch[0], ch[1], ch[2], 0x10)
    }

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
     * Linear pixel/channel order: 7E 06 81 <pin0> <pin1> <pin2> FF 00 EF.
     * Default from the stock app is 01 02 03 (RGB), packed as int 0x010203.
     */
    fun pixelOrder(order: PixelOrder): ByteArray = frame(
        0x06, 0x81,
        order.pins[0], order.pins[1], order.pins[2],
        0xFF, 0x00
    )

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

/** Linear channel sequence of the strip. Pins match the stock app defaults. */
enum class PixelOrder(val pins: IntArray) {
    RGB(intArrayOf(0x01, 0x02, 0x03)),
    RBG(intArrayOf(0x01, 0x03, 0x02)),
    GRB(intArrayOf(0x02, 0x01, 0x03)),
    GBR(intArrayOf(0x02, 0x03, 0x01)),
    BRG(intArrayOf(0x03, 0x01, 0x02)),
    BGR(intArrayOf(0x03, 0x02, 0x01));

    /** Maps logical (r, g, b) onto the byte order the strip expects. */
    fun remap(red: Int, green: Int, blue: Int): IntArray = when (this) {
        RGB -> intArrayOf(red, green, blue)
        RBG -> intArrayOf(red, blue, green)
        GRB -> intArrayOf(green, red, blue)
        GBR -> intArrayOf(green, blue, red)
        BRG -> intArrayOf(blue, red, green)
        BGR -> intArrayOf(blue, green, red)
    }

    companion object {
        /**
         * Default is GRB: the common ELK-BLEDOB clones from AliExpress wire
         * WS2812-style dies in green-first order (verified against hardware
         * where the stock RGB command lit green).
         */
        fun fromName(name: String?): PixelOrder =
            entries.firstOrNull { it.name == name } ?: GRB
    }
}
