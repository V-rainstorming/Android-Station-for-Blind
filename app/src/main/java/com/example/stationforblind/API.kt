package com.example.stationforblind

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

interface API {
    @Headers("Content-Type: application/json")
    @POST("/BlindBusRoute")
    fun getPostList(@Body params: HashMap<String, Any>): Call<Post>
    @POST("/RegisterService")
    fun getServiceID(@Body params: HashMap<String, Any>): Call<ServiceID>
    @GET("/azimuth")
    fun getAzimuthFromServer(): Call<Azimuth>
    @GET("/bus_station_nickname")
    fun getMappedKeyword(
        @Query("bus_station_nickname") nickname: String
    ): Call<StationNickname>
    @GET("/MobileBusBlindControl")
    @Streaming
    fun getPos(
        @Query("service_id") serviceID: Int
    ): Call<StreamData>
}

object RetrofitBuilder{
    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
    var api : API = Retrofit.Builder()
        .baseUrl("https://www.devhsb.com")
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(API::class.java)
}
data class Azimuth (@SerializedName("azimuth") val azimuth: Int)

data class StationNickname (@SerializedName("station_name") val stationName: String)

data class Post (@SerializedName("status") val status: String,
                 @SerializedName("code") val code: Int,
                 @SerializedName("data") val busData: List<BusData>)

data class BusData (@SerializedName("bus_no") val busNumber: Int,
                    @SerializedName("left_time") val travelTime: Int,
                    @SerializedName("left_station") val numberOfStops: Int,
                    @SerializedName("bus_color") val busColor: String,
                    @SerializedName("depature_station_name") val sourceName: String,
                    @SerializedName("destination_station_name") val destinationName: String,
                    @SerializedName("bus_id") val busID: Int,
                    @SerializedName("depature_station_id") val sourceID: Int,
                    @SerializedName("destination_station_id") val destinationID: Int)

data class ServiceID (@SerializedName("status") val status: String,
                      @SerializedName("code") val code: Int,
                      @SerializedName("service_id") val serviceID: Int)
