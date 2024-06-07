package com.example.stationforblind

import com.fasterxml.jackson.annotation.JsonProperty

data class RouteData(
    @JsonProperty("user_status") val userStatus: String,
    @JsonProperty("bus_data") val routeData: List<Station>
)

data class Station(
    @JsonProperty("station_name") val stationName: String,
    @JsonProperty("latitude") val latitude: Double,
    @JsonProperty("longitude") val longitude: Double,
    @JsonProperty("id") val id: Int
)
