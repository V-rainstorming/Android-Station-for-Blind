package com.example.stationforblind

import com.fasterxml.jackson.annotation.JsonProperty

data class StreamData(
    @JsonProperty("user_status") val userStatus: String,
    @JsonProperty("bus_data") val busDataWithPos: BusDataWithPos
)