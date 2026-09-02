package red.thugs.wardrive.scan

/** WiFi channel number from centre frequency in MHz, or null if it is not a channel we know. */
fun channelForFrequency(freqMhz: Int): Int? = when (freqMhz) {
    2484 -> 14
    in 2412..2472 -> (freqMhz - 2407) / 5
    in 5160..5885 -> (freqMhz - 5000) / 5
    in 5955..7115 -> (freqMhz - 5950) / 5 // 6 GHz (WiFi 6E)
    else -> null
}
