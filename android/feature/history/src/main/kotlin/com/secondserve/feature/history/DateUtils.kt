package com.secondserve.feature.history

import java.text.SimpleDateFormat
import java.util.Date

internal fun formatDate(ts: Long, format: SimpleDateFormat): String =
    if (ts <= 0L) "Date inconnue" else format.format(Date(ts))
