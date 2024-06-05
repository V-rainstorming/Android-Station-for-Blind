package com.example.stationforblind

import android.os.Parcel
import android.os.Parcelable

data class BusInformation(
    val busNumber: Int,
    val busColor: String,
    val travelTime: Int,
    val numberOfStops: Int,
    val sourceName: String,
    val destinationName: String,
    val busID: Int,
    val sourceID: Int,
    val destinationID: Int) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(busNumber)
        parcel.writeString(busColor)
        parcel.writeInt(travelTime)
        parcel.writeInt(numberOfStops)
        parcel.writeString(sourceName)
        parcel.writeString(destinationName)
        parcel.writeInt(busID)
        parcel.writeInt(sourceID)
        parcel.writeInt(destinationID)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<BusInformation> {
        override fun createFromParcel(parcel: Parcel): BusInformation {
            return BusInformation(parcel)
        }

        override fun newArray(size: Int): Array<BusInformation?> {
            return arrayOfNulls(size)
        }
    }
}
