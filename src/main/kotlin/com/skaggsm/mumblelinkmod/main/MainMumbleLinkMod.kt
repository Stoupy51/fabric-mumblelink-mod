package com.skaggsm.mumblelinkmod.main

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.text.MessageFormat
import java.util.Locale
import kotlin.io.path.div

/**
 * Created by Mitchell Skaggs on 5/29/2019.
 */
object MainMumbleLinkMod : ModInitializer {
    // Common constants
    const val MODID: String = "fabric-mumblelink-mod"
    val LOG: Logger = LogManager.getLogger(MODID)
    private val JSON =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    val configFolder: Path = FabricLoader.getInstance().configDir

    // Config files
    private val configFile = configFolder / "fabric-mumblelink-mod-main.json"

    lateinit var config: MainConfig

    override fun onInitialize() {
        setupConfig()
        setupEvents()
    }

    private fun setupConfig() {
        config = MainConfig()

        if (Files.exists(configFile)) {
            deserialize()
        } else {
            serialize()
        }
    }

    fun serialize() {
        val serialized = JSON.encodeToString(config)
        Files.writeString(configFile, serialized, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }

    private fun deserialize() {
        try {
            config = JSON.decodeFromString(Files.readString(configFile, java.nio.charset.StandardCharsets.UTF_8))
        } catch (e: SerializationException) {
            val backup = configFile.resolveSibling("${configFile.fileName}.corrupt")
            runCatching { Files.move(configFile, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
            LOG.error("Main config was corrupted and could not be parsed. Backed it up to {} and regenerated defaults.", backup, e)
            config = MainConfig()
            serialize()
        }
    }

    private fun setupEvents() {
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            sendVoipPacket(handler.player)
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            sendAllVoipPackets(server)
        }
    }

    private fun sendAllVoipPackets(server: MinecraftServer) {
        server.playerList.players.forEach { sendVoipPacket(it) }
    }

    private fun sendVoipPacket(
        player: ServerPlayer,
        toWorld: ResourceKey<Level> = player.level().dimension(),
    ) {
        // No VoIP server configured (the default, e.g. in singleplayer), so there's nothing to point the client at.
        if (config.voipServerHost.isBlank()) return

        LOG.trace("Updating VoIP location for ${player.name.string}!")

        val dim = toWorld.toString()
        val dimNamespace =
            dim.substringBefore(':').split('_').joinToString(" ") {
                it.replaceFirstChar { c ->
                    if (c.isLowerCase()) {
                        c.titlecase(
                            Locale.getDefault(),
                        )
                    } else {
                        c.toString()
                    }
                }
            }
        val dimPath =
            dim.substringAfter(':').split('_').joinToString(" ") {
                it.replaceFirstChar { c ->
                    if (c.isLowerCase()) {
                        c.titlecase(
                            Locale.getDefault(),
                        )
                    } else {
                        c.toString()
                    }
                }
            }
        val dimId = "$dimNamespace $dimPath"

        val teamName = player.team?.name ?: ""

        val templateParams: Array<Any> = arrayOf(dimId, dimNamespace, dimPath, teamName)

        val path: String = MessageFormat.format(config.voipServerPath, *templateParams)
        val query: String = MessageFormat.format(config.voipServerQuery, *templateParams)

        // val payload = CustomPayload(SendMumbleURL.ID, buf)
        val payload =
            SendMumbleURL(
                config.voipClient,
                config.voipServerUserinfo,
                config.voipServerHost,
                config.voipServerPort,
                path,
                query,
                config.voipServerFragment,
            )
        ServerPlayNetworking.send(player, payload)
    }
}
