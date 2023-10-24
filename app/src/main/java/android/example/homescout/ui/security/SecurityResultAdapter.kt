package android.example.homescout.ui.security

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.example.homescout.R
import android.example.homescout.database.DeviceInfo
import android.example.homescout.utils.Constants.NANOSECONDS_TO_MINUTES
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
//import kotlin.math
import kotlin.math.abs

class SecurityResultAdapter(
    private val items: List<ScanResult>,
    private val onClickListener: ((detail: DeviceInfo) -> Unit),
    ) : RecyclerView.Adapter<SecurityResultAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SecurityResultAdapter.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_security_result, parent,false)

        return SecurityResultAdapter.ViewHolder(view, onClickListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount() = items.size

    class ViewHolder(
        private val view: View,
        private val onClickListener: (detail: DeviceInfo) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val macAddress: TextView = view.findViewById(R.id.mac_address)
        private val signalStrength: TextView = view.findViewById(R.id.signal_strength)
        private val securityScore: TextView = view.findViewById(R.id.security_score)
        private val fileName = "OUIs.txt"


        fun readOUIs (filename: String) : MutableList<String>{
            val input = view.context.assets.open(filename)
            val inputstreamreader = BufferedReader(InputStreamReader(input))
            try {
                // Read the file and create a list of lines (strings)
                var ouis: MutableList<String> = mutableListOf()
                inputstreamreader.use {
                    it.forEachLine { s ->
                        ouis.add(s)
                    }
                }
                return ouis
            }
            catch (e: IOException)
            {
                e.printStackTrace()
            }
            finally
            {
                inputstreamreader.close()
            }
            return mutableListOf()
        }

        @SuppressLint("MissingPermission", "SetTextI18n")
        fun bind(result: ScanResult) {

            fun decimalToIdentifier(decimal: Int): String {
                val hexString = Integer.toHexString(decimal).uppercase(Locale.ROOT)
                val companyIdentifier = "0x" + hexString.padStart(4, '0')
                return companyIdentifier
            }

            fun deviceCompany(companyIDDecimal: Int): String {
                // if manufacturer specific data exists
                if (companyIDDecimal != 0) {
                    val companyID = decimalToIdentifier(companyIDDecimal)
                    // if it belong to the assigned list
                    val firstCompany = "0x0000"
                    val lastCompany = "0x0CF5"
                    if (companyID in firstCompany..lastCompany) {
                        return "Known Company"
                    }
                    return "Unknown Company"
                }
                return "Unknown Company"
            }

            fun firstSixDigits(macAddress: String): String {
                val hexDigits = macAddress.split(":")
                return StringBuilder().append(hexDigits[0]).append(hexDigits[1])
                    .append(hexDigits[2]).toString()
            }

            fun macAddressToBinary(macAddress: String): String {
                val hexDigits = macAddress.split(":")
                val binaryDigits = StringBuilder()

                for (hexDigit in hexDigits) {
                    val decimalValue = Integer.parseInt(hexDigit, 16)
                    val binaryValue = Integer.toBinaryString(decimalValue).padStart(8, '0')
                    binaryDigits.append(binaryValue)
                }

                return binaryDigits.toString()
            }

            fun getAdvertisingAddressType(macAddr: String): String {
                val publicOUIs = readOUIs(fileName)
                val binaryMacAddress = macAddressToBinary(macAddr)
                val first6 = firstSixDigits(macAddr)
                val first6hex = firstSixDigits(macAddr).toLong(16)
                val notOUI1 : List<String> = listOf("0009FA", "00242D", "00243E", "002457", "002534", "00253F", "0025F8", "002649", "00264B")

                val AddrType: String = when {
                    // filter out several constant sequence in the OUI list to avoid large txt file
                    publicOUIs.contains(first6) -> "Public Address"
                    first6hex <= "000832".toLong(16) -> "Public Address"
                    first6hex >= "00084E".toLong(16) && first6hex <= "002722".toLong(16) &&  first6 !in notOUI1 -> "Public Address"
                    first6hex >= "003000".toLong(16) && first6hex <= "0030FF".toLong(16) -> "Public Address"
                    first6hex >= "004000".toLong(16) && first6hex <= "0040FF".toLong(16) -> "Public Address"
                    first6hex >= "006000".toLong(16) && first6hex <= "0060FF".toLong(16) -> "Public Address"
                    first6hex >= "008000".toLong(16) && first6hex <= "0080FF".toLong(16) -> "Public Address"
                    first6hex >= "009000".toLong(16) && first6hex <= "0090FF".toLong(16) -> "Public Address"
                    first6hex >= "00A000".toLong(16) && first6hex <= "00A0FF".toLong(16) -> "Public Address"
                    first6hex >= "00C000".toLong(16) && first6hex <= "00C0FF".toLong(16) -> "Public Address"
                    first6hex >= "00D000".toLong(16) && first6hex <= "00D0FF".toLong(16) -> "Public Address"
                    first6hex >= "00E000".toLong(16) && first6hex <= "00E0FF".toLong(16) -> "Public Address"
                    binaryMacAddress.startsWith("11") -> "Random Static Address"
                    binaryMacAddress.startsWith("00") -> "Random Non-resolvable Private Address"
                    binaryMacAddress.startsWith("01") -> "Random Resolvable Private Address"
                    else -> "Invalid"
                }
                return AddrType
            }

            fun addressUpdate(priorAddrs: List<String>, addr: String, timeStamp: Long): String {
                val resultFile = "result_test.csv"
                val file = File(view.context.filesDir, resultFile)
                var dif = 0L
                if (addr in priorAddrs){
                    try {
                        BufferedReader(file.reader()).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                val lastmac = line?.split(Regex(","))?.firstOrNull()
                                if (lastmac == addr){
                                    val earliestTime = line?.split(Regex(","))?.lastOrNull()
                                    if (earliestTime != null) {
                                        dif = abs((timeStamp - earliestTime.toLong()) / NANOSECONDS_TO_MINUTES)
                                        println("$addr : $dif")
                                        break
                                    } else {
                                        dif = 0L
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    dif = 0
                }
                return if (dif >= 15L){
                    "No Update"
                } else {
                    "Update"
                }
            }

            fun securityLevel(company: String, addrType: String, update: String): String {

                val manufacturerScore = if (company == "Known Company") {
                    2
                } else {
                    0
                }
                val addrTypeScore: Double = when (addrType) {
                    "Random Non-resolvable Private Address" -> 1.5
                    "Random Resolvable Private Address" -> 0.5
                    else -> 0.0
                }

                val updateOffsetScore = if (update == "Update"){
                    0.5
                } else {
                    0.0
                }

                val totalScore = manufacturerScore + addrTypeScore + updateOffsetScore
                val lowMedium = 2.0
                val mediumHigh = 3.0
                val level: String = when {
                    totalScore <= lowMedium -> "Low Security"
                    totalScore <= mediumHigh -> "Medium Security"
                    else -> "High Security"
                }
//                println(totalScore)
                return level
            }

            // display the mac address, RSSI and the security level of ble device

            val manufacturer = result.scanRecord?.manufacturerSpecificData.toString()
            var companyIDDecimal = 0
            if (manufacturer != null) {
                companyIDDecimal = manufacturer.substringAfter("{").substringBefore('=').toInt()
            }

            val company = deviceCompany(companyIDDecimal)
            val addressType = getAdvertisingAddressType(result.device.address)

            // Open a file output stream in the app's internal storage directory
            val resultFile = "result_test.csv"
            val file = File(view.context.filesDir, resultFile)

            //load prior scanned device
            val lastResult = mutableListOf<String>()

            try {
                BufferedReader(file.reader()).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val lastmac = line?.split(Regex(","))?.firstOrNull()
                        if (lastmac != null) {
                            lastResult.add(lastmac)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
//                println(lastResult.distinct())
            val deviceAddress = result.device.address
            val timeStampNano = result.timestampNanos

            val update = if (addressType == "Public Address") {
                "No Update"
            } else {
                addressUpdate(lastResult.distinct(), deviceAddress, timeStampNano)
            }
            val level = securityLevel(company, addressType, update)

            val device = DeviceInfo(result.device.address, company, addressType, update, level)


            //add current scanned device
            file.appendText("$deviceAddress,$company,$addressType,$update,$level,$timeStampNano\n")

            macAddress.text = result.device.address
            securityScore.text = level
            signalStrength.text = "${result.rssi} dBm"

            view.setOnClickListener { onClickListener.invoke(device) }
        }

    }
}