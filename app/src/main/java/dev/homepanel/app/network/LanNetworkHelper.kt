package dev.homepanel.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Selects a real LAN transport (Wi-Fi first, then Ethernet) instead of blindly using the
 * process default route. This matters on wall tablets with a VPN, private DNS or multiple
 * simultaneous networks: Home Assistant may work through the VPN/Internet while MQTT/RTSP
 * must use the local Wi-Fi/Ethernet interface.
 */
data class LanNetworkInfo(
    val network: Network,
    val transport: String,
    val interfaceName: String?,
    val ipv4Address: String?
)

class LanNetworkHelper(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun select(): LanNetworkInfo? {
        val candidates = connectivityManager.allNetworks.mapNotNull { network ->
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return@mapNotNull null
            val priority = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 0
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 1
                else -> return@mapNotNull null
            }
            val link = connectivityManager.getLinkProperties(network)
            val ipv4 = link?.linkAddresses
                ?.map { it.address }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress && it.isSiteLocalAddress }
                ?: link?.linkAddresses
                    ?.map { it.address }
                    ?.filterIsInstance<Inet4Address>()
                    ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            Triple(priority, network, LanNetworkInfo(
                network = network,
                transport = if (priority == 0) "Wi-Fi" else "Ethernet",
                interfaceName = link?.interfaceName,
                ipv4Address = ipv4?.hostAddress
            ))
        }
        return candidates.sortedBy { it.first }.firstOrNull()?.third
    }

    fun resolve(host: String, info: LanNetworkInfo? = select()): List<InetAddress> {
        if (info != null) {
            runCatching { info.network.getAllByName(host).toList() }.getOrNull()?.let { if (it.isNotEmpty()) return it }
        }
        return InetAddress.getAllByName(host).toList()
    }
}
