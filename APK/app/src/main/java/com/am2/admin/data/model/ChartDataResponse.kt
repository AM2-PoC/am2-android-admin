package com.am2.admin.data.model

data class ChartDataResponse(
    val labels: List<String>,
    val values: List<Int>,
    val status: String,
    val timestamp: String
)