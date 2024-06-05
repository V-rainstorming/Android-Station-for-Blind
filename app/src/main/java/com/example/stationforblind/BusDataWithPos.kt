package com.example.stationforblind

import com.fasterxml.jackson.annotation.JsonProperty

data class BusDataWithPos(
        @JsonProperty("move_rate") val moveRate: Int,
        @JsonProperty("bus_x_pos") val busX: Double,
        @JsonProperty("bus_y_pos") val busY: Double,
        @JsonProperty("user_x_pos") val userX: Double,
        @JsonProperty("user_y_pos") val userY: Double,
        @JsonProperty("station_x_pos") val stationX: Double,
        @JsonProperty("station_y_pos") val stationY: Double,
        @JsonProperty("user_station_dist") val distanceUserStation: Int,
        @JsonProperty("bus_color") val busColor: String,
        @JsonProperty("bus_now_station_id") val busNowStationID: Int,
        @JsonProperty("start_station_id") val sourceStationID: Int,
        @JsonProperty("dest_station_id") val destinationStationID: Int,
        @JsonProperty("left_time") val leftTime: Int,
        @JsonProperty("left_station") val leftStation: Int
)
