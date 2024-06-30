package com.skaggsm.mumblelinkmod.main

import com.skaggsm.mumblelinkmod.client.ClientConfig.AutoLaunchOption
import com.skaggsm.mumblelinkmod.client.ClientMumbleLinkMod
import com.skaggsm.mumblelinkmod.main.MainMumbleLinkMod.LOG
import com.skaggsm.mumblelinkmod.main.MainMumbleLinkMod.MODID
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
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
    val fragment: String
) : CustomPayload {

    companion object : ClientPlayNetworking.PlayPayloadHandler<SendMumbleURL> {
        val PACKET_ID = CustomPayload.Id<SendMumbleURL>(Identifier.of(MODID, "broadcast_mumble_url_v2"))
        val PACKET_CODEC: PacketCodec<RegistryByteBuf, SendMumbleURL> = PacketCodec.of(::encode, ::decode)

        private fun encode(packet: SendMumbleURL, buf: RegistryByteBuf) {
            buf.writeEnumConstant(packet.voipClient)
            buf.writeString(packet.userinfo)
            buf.writeString(packet.host)
            buf.writeInt(packet.port)
            buf.writeString(packet.path)
            buf.writeString(packet.query)
            buf.writeString(packet.fragment)
        }

        private fun decode(buf: RegistryByteBuf): SendMumbleURL {
            return SendMumbleURL(
                buf.readEnumConstant(MainConfig.VoipClient::class.java),
                buf.readString().ifEmpty { "" },
                buf.readString().ifEmpty { "" },
                buf.readInt(),
                buf.readString().ifEmpty { "" },
                buf.readString().ifEmpty { "" },
                buf.readString().ifEmpty { "" }
            )
        }

        private fun ensureNotHeadless() {
            if (GraphicsEnvironment.isHeadless()) {
                LOG.warn("Unable to unset headless earlier (are you using macOS?), doing it with nasty reflection now!")
                val headlessField = GraphicsEnvironment::class.java.getDeclaredField("headless")
                headlessField.isAccessible = true
                headlessField[null] = false
            }
        }

        override fun receive(payload: SendMumbleURL, context: ClientPlayNetworking.Context) {
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
                LOG.warn("Unable to use the \"BROWSE\" intent to open your VoIP client automatically! Check that you aren't using a headless or server JVM.")
            }
        }
    }

    override fun getId(): CustomPayload.Id<out CustomPayload> {
        return PACKET_ID
    }
}
