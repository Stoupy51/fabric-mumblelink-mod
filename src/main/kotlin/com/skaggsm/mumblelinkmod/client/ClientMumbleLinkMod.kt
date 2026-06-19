package com.skaggsm.mumblelinkmod.client

import com.skaggsm.jmumblelink.MumbleLink
import com.skaggsm.jmumblelink.MumbleLinkImpl
import com.skaggsm.mumblelinkmod.main.MainMumbleLinkMod
import com.skaggsm.mumblelinkmod.main.MainMumbleLinkMod.LOG
import com.skaggsm.mumblelinkmod.main.SendMumbleURL
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType.CLIENT
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import org.lwjgl.system.Platform
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import kotlin.io.path.div

/**
 * Created by Mitchell Skaggs on 5/12/2019.
 */
@Environment(CLIENT)
object ClientMumbleLinkMod : ClientModInitializer {
    // Config files
    private val configFile = MainMumbleLinkMod.configFolder / "fabric-mumblelink-mod-client.json"
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    // Configs
    lateinit var config: ClientConfig

    private var mumble: MumbleLink? = null

    /**
     * Runs after [MainMumbleLinkMod.onInitialize].
     */
    override fun onInitializeClient() {
        setupConfig()
        setupEvents()
    }

    private fun setupConfig() {
        config = ClientConfig()

        if (Files.exists(configFile)) {
            deserialize()
        } else {
            serialize()
        }
    }

    fun serialize() {
        val serialized = json.encodeToString(config)
        Files.writeString(configFile, serialized, WRITE, CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)
    }

    private fun deserialize() {
        try {
            config = json.decodeFromString(Files.readString(configFile, java.nio.charset.StandardCharsets.UTF_8))
        } catch (e: SerializationException) {
            val backup = configFile.resolveSibling("${configFile.fileName}.corrupt")
            runCatching { Files.move(configFile, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
            LOG.error("Client config was corrupted and could not be parsed. Backed it up to {} and regenerated defaults.", backup, e)
            config = ClientConfig()
            serialize()
        }
    }

    private fun setupEvents() {
        PayloadTypeRegistry.serverboundPlay().register(SendMumbleURL.PACKET_ID, SendMumbleURL.PACKET_CODEC)
        PayloadTypeRegistry.clientboundPlay().register(SendMumbleURL.PACKET_ID, SendMumbleURL.PACKET_CODEC)
        ClientPlayNetworking.registerGlobalReceiver(SendMumbleURL.PACKET_ID, SendMumbleURL::receive)

        ClientTickEvents.START_CLIENT_TICK.register(
            ClientTickEvents.StartTick {
                val world = it.level
                val player = it.player

                if (world != null && player != null) {
                    val mumble = ensureLinked()

                    // Forge implementation :
                    // Vec3 position = game.player.getPosition(1f);
                    // Vec3 lookDirection = game.player.getLookAngle();
                    // Vec3 topDirection = game.player.getUpVector();
		
                    // Fabric implementation :
                    val position = player.getEyePosition(1.0f)
                    val lookDirection = player.lookAngle
                    val topDirection = player.getUpVector(1.0f)

                    // Convert to right-handed coordinate system.
                    val camPos = position.toRHArray
                    val camFro = lookDirection.toRHArray
                    val camTop = topDirection.toRHArray

                    // Make people in other dimensions far away so that they're muted.
                    camPos[2] += (world.dimension().toString().stableHash % 2048) * config.clientDimensionYAxisAdjust

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
            },
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
