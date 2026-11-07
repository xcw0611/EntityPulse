package com.entitypulse.app.data.model

enum class TimeRangeFilter(
    val displayName: String,
    val durationMillis: Long
) {
    ONE_MONTH("近1个月", 30L * 24 * 3600 * 1000L),
    THREE_MONTHS("近3个月", 90L * 24 * 3600 * 1000L),
    HALF_YEAR("近半年", 182L * 24 * 3600 * 1000L),
    ONE_YEAR("近1年", 365L * 24 * 3600 * 1000L),
    ALL("全部历史", Long.MAX_VALUE);

    companion object {
        val DEFAULT = ONE_YEAR
    }
}
