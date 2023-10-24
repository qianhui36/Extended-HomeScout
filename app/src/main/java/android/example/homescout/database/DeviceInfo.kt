package android.example.homescout.database

import androidx.room.PrimaryKey

data class DeviceInfo (
    val deviceAddress: String,
    val deviceManufacturer: String,
    val deviceAddressType: String,
    val deviceAddressUpdate: String,
    val deviceSecurityLevel: String,
){
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}