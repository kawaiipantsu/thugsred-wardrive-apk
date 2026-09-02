package red.thugs.wardrive.scan

/** WiFi channel number from centre frequency in MHz, or null if it is not a channel we recognise. */
fun channelForFrequency(freqMhz: Int): Int? = when (freqMhz) {
    2484 -> 14
    in 2412..2472 -> (freqMhz - 2407) / 5
    in 5150..5895 -> (freqMhz - 5000) / 5   // 5 GHz (channels 30–179)
    in 5925..7125 -> (freqMhz - 5950) / 5   // 6 GHz (WiFi 6E)
    else -> null
}
