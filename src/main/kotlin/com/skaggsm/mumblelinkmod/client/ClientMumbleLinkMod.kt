package com.skaggsm.mumblelinkmod.client

import com.skaggsm.jmumblelink.MumbleLink
import com.skaggsm.jmumblelink.MumbleLinkImpl
import com.skaggsm.mumblelinkmod.client.ClientConfig.AutoLaunchOption
import com.skaggsm.mumblelinkmod.main.MainConfig
import com.skaggsm.mumblelinkmod.main.MainMumbleLinkMod
import com.skaggsm.mumblelinkmod.main.MainMumbleLinkMod.LOG
import com.skaggsm.mumblelinkmod.main.MainMumbleLinkMod.SERIALIZER
import com.skaggsm.mumblelinkmod.main.MainMumbleLinkMod.createSettings
import com.skaggsm.mumblelinkmod.main.SendMumbleURL
import io.github.fablabsmc.fablabs.api.fiber.v1.serialization.FiberSerialization
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.ConfigBranch
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.ConfigTree
import io.github.fablabsmc.fablabs.impl.fiber.tree.ConfigBranchImpl
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType.CLIENT
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.network.PacketByteBuf
import org.lwjgl.system.Platform
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import kotlin.io.path.div

/**
 * Created by Mitchell Skaggs on 5/12/2019.
 */
@Environment(CLIENT)
object ClientMumbleLinkMod : ClientModInitializer {
    // Config files
    private val configFile = MainMumbleLinkMod.configFolder / "fabric-mumblelink-mod-client.json"

    // Configs
    lateinit var config: ClientConfig
    lateinit var configTree: ConfigBranch
    lateinit var unionConfigTree: ConfigBranch

    private var mumble: MumbleLink? = null

    @Suppress("UNUSED_PARAMETER")
    private fun channelHandler(
        minecraftClient: MinecraftClient,
        clientPlayNetworkHandler: ClientPlayNetworkHandler,
        packetByteBuf: PacketByteBuf,
        packetSender: PacketSender
    ) {
        if (config.clientAutoLaunchOption == AutoLaunchOption.IGNORE) return

        val voipClient = packetByteBuf.readEnumConstant(MainConfig.VoipClient::class.java)
        val userinfo = packetByteBuf.readString().ifEmpty { null }
        val host = packetByteBuf.readString().ifEmpty { null }
        val port = packetByteBuf.readInt()
        val path = packetByteBuf.readString().ifEmpty { null }
        val query = packetByteBuf.readString().ifEmpty { null }
        val fragment = packetByteBuf.readString().ifEmpty { null }

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

    /**
     * Runs after [MainMumbleLinkMod.onInitialize].
     */
    override fun onInitializeClient() {
        setupConfig()
        setupEvents()
    }

    private fun setupConfig() {
        config = ClientConfig()

        configTree = ConfigTree.builder().applyFromPojo(config, createSettings()).withName("client").build()
        unionConfigTree = ConfigBranchImpl("union", null)
        unionConfigTree.items.add(configTree)
        unionConfigTree.items.add(MainMumbleLinkMod.configTree)

        if (Files.notExists(configFile)) {
            serialize()
        }

        // Verify save worked
        deserialize()
    }

    fun serialize() {
        FiberSerialization.serialize(
            configTree,
            Files.newOutputStream(configFile, WRITE, CREATE),
            SERIALIZER
        )
    }

    private fun deserialize() {
        FiberSerialization.deserialize(
            configTree,
            Files.newInputStream(configFile, READ),
            SERIALIZER
        )
    }

    private fun setupEvents() {
        ClientPlayNetworking.registerGlobalReceiver(SendMumbleURL.ID, ClientMumbleLinkMod::channelHandler)

        ClientTickEvents.START_CLIENT_TICK.register(
            ClientTickEvents.StartTick {
                val world = it.world
                val player = it.player

                if (world != null && player != null) {
                    val mumble = ensureLinked()

                    val camPos = player.getCameraPosVec(1F).toRHArray	// Keep Minecraft's right-handed coordinate system
                    val camFro = player.rotationVecClient.toLHArray		// Convert to Mumble's left-handed coordinate system
                    val camTop = floatArrayOf(0f, 0f, 1f) // x, z, y
                    // log camPos, camFro, camTop

                    // Make people in other dimensions far away so that they're muted.
                    val yAxisAdjuster = (world.registryKey.value.stableHash % 2048) * config.clientDimensionYAxisAdjust
                    camPos[2] += yAxisAdjuster

                    mumble.uiVersion = 2
                    mumble.uiTick++

                    mumble.avatarPosition = camPos
                    mumble.avatarFront = camFro
                    mumble.avatarTop = camTop

                    mumble.name = "Minecraft"

                    mumble.cameraPosition = camPos
                    mumble.cameraFront = camFro
                    mumble.cameraTop = camTop

                    mumble.identity = Json.encodeToString(Identity(world, player))

                    mumble.context = "{\"domain\":\"AllTalk\"}"

                    mumble.description = "A Minecraft mod that provides position data to VoIP clients."
                } else {
                    ensureClosed()
                }
            }
        )
    }

    private fun ensureLinked(): MumbleLink {
        var localMumble = mumble

        if (localMumble != null) {
            return localMumble
        }

        LOG.info("Linking to VoIP client...")
        localMumble = MumbleLinkImpl()
        mumble = localMumble
        LOG.info("Linked")

        return localMumble
    }

    private fun ensureClosed() {
        if (mumble != null) {
            LOG.info("Unlinking from VoIP client...")
            mumble?.close()
            mumble = null
            LOG.info("Unlinked")
        }
    }

    private fun ensureNotHeadless() {
        if (GraphicsEnvironment.isHeadless()) {
            LOG.warn("Unable to unset headless earlier (are you using macOS?), doing it with nasty reflection now!")
            val headlessField = GraphicsEnvironment::class.java.getDeclaredField("headless")
            headlessField.isAccessible = true
            headlessField[null] = false
        }
    }

    init {
        // Many mods assume java.awt.headless=true on macOS because they accidentally use AWT classes that triggers JNI stuff on classload if not headless.
        // That JNI stuff fails on Mac because of course it does, so we skip settings java.awt.headless=false now and set it on-demand later (hopefully after the mods have already triggered the AWT JNI code).
        if (Platform.get() == Platform.MACOSX) {
            LOG.warn("macOS needs java.awt.headless=true right now, so we'll set it later with a reflection hack!")
        } else {
            // If not on macOS isn't loaded, we can just set it here and skip the hassle later.
            // Required to open URIs
            System.setProperty("java.awt.headless", "false")
        }
    }
}
