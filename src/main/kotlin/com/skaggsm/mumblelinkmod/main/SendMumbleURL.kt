package com.skaggsm.mumblelinkmod.main

import com.skaggsm.mumblelinkmod.client.ClientConfig.AutoLaunchOption
import com.skaggsm.mumblelinkmod.client.ClientMumbleLinkMod
import com.skaggsm.mumblelinkmod.main.MainMumbleLinkMod.LOG
import com.skaggsm.mumblelinkmod.main.MainMumbleLinkMod.MODID
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.net.URI
import java.net.URISyntaxException

/**
 * Created by Mitchell Skaggs on 5/28/2019.
 */
data class SendMumbleURL(
    val voipClient: MainConfig.VoipClient,
    val userinfo: String,
    val host: String,
    val port: Int,
    val path: String,
    val query: String,
    val fragment: String,
) : CustomPacketPayload {
    companion object {
        val PACKET_ID = CustomPacketPayload.Type<SendMumbleURL>(Identifier.fromNamespaceAndPath(MODID, "broadcast_mumble_url_v2"))
        val PACKET_CODEC: StreamCodec<RegistryFriendlyByteBuf, SendMumbleURL> = CustomPacketPayload.codec(SendMumbleURL::encode, ::decode)

        private fun encode(
            packet: SendMumbleURL,
            buf: RegistryFriendlyByteBuf,
        ) {
            buf.writeEnum(packet.voipClient)
            buf.writeUtf(packet.userinfo)
            buf.writeUtf(packet.host)
            buf.writeInt(packet.port)
            buf.writeUtf(packet.path)
            buf.writeUtf(packet.query)
            buf.writeUtf(packet.fragment)
        }

        private fun decode(buf: RegistryFriendlyByteBuf): SendMumbleURL =
            SendMumbleURL(
                buf.readEnum(MainConfig.VoipClient::class.java),
                buf.readUtf().ifEmpty { "" },
                buf.readUtf().ifEmpty { "" },
                buf.readInt(),
                buf.readUtf().ifEmpty { "" },
                buf.readUtf().ifEmpty { "" },
                buf.readUtf().ifEmpty { "" },
            )

        private fun ensureNotHeadless() {
            if (GraphicsEnvironment.isHeadless()) {
                LOG.warn("Unable to unset headless earlier (are you using macOS?), doing it with nasty reflection now!")
                val headlessField = GraphicsEnvironment::class.java.getDeclaredField("headless")
                headlessField.isAccessible = true
                headlessField[null] = false
            }
        }

        fun receive(
            payload: SendMumbleURL,
            context: ClientPlayNetworking.Context,
        ) {
            if (ClientMumbleLinkMod.config.clientAutoLaunchOption == AutoLaunchOption.IGNORE) return

            val voipClient = payload.voipClient
            val userinfo = payload.userinfo
            val host = payload.host
            val port = payload.port
            val path = payload.path
            val query = payload.query
            val fragment = payload.fragment

            try {
                val uri = URI(voipClient.scheme, userinfo, host, port, path, query, fragment)
                ensureNotHeadless()
                Desktop.getDesktop().browse(uri)
            } catch (e: URISyntaxException) {
                LOG.warn("Ignoring invalid VoIP client URI \"${e.input}\"")
            } catch (e: UnsupportedOperationException) {
                LOG.warn(
                    "Unable to use the \"BROWSE\" intent to open your VoIP client automatically! Check that you aren't using a headless or server JVM.",
                )
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = PACKET_ID
}
