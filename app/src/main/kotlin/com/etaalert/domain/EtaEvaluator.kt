package com.etaalert.domain

data class EtaResult(
    val etaMinutes: Int,
    val shouldAlert: Boolean,
    val trackingExpired: Boolean
)

class EtaEvaluator {

    fun evaluate(
        etaMinutes: Int,
        thresholdMinutes: Int,
        trackingStartTime: Long,
        durationMinutes: Int
    ): EtaResult {
        val shouldAlert = etaMinutes <= thresholdMinutes
        val trackingExpired = System.currentTimeMillis() >
                trackingStartTime + (durationMinutes * 60 * 1000L)

        return EtaResult(
            etaMinutes = etaMinutes,
            shouldAlert = shouldAlert,
            trackingExpired = trackingExpired
        )
    }
}
