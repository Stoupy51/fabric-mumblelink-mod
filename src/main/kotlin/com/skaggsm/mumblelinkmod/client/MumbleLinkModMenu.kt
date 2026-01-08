package com.skaggsm.mumblelinkmod.client

import com.skaggsm.mumblelinkmod.client.ClientConfig.AutoLaunchOption
import com.skaggsm.mumblelinkmod.main.MainConfig
import com.skaggsm.mumblelinkmod.main.MainConfig.VoipClient
import com.skaggsm.mumblelinkmod.main.MainMumbleLinkMod
import com.skaggsm.mumblelinkmod.main.MainMumbleLinkMod.LOG
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import me.shedaniel.clothconfig2.api.ConfigBuilder
import me.shedaniel.clothconfig2.api.ConfigCategory
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.text.Text

/**
 * Created by Mitchell Skaggs on 5/30/2019.
 */
@Environment(EnvType.CLIENT)
class MumbleLinkModMenu : ModMenuApi {
    override fun getModConfigScreenFactory() =
        ConfigScreenFactory { parent ->
            try {
                val builder =
                    ConfigBuilder
                        .create()
                        .setParentScreen(parent)
                        .setTitle(Text.translatable("config.fabric-mumblelink-mod.title"))
                        .setSavingRunnable {
                            MainMumbleLinkMod.serialize()
                            ClientMumbleLinkMod.serialize()
                        }

                val entryBuilder = builder.entryBuilder()

                // Client category
                val clientCategory: ConfigCategory =
                    builder.getOrCreateCategory(
                        Text.translatable("config.fabric-mumblelink-mod.client"),
                    )

                clientCategory.addEntry(
                    entryBuilder
                        .startEnumSelector(
                            Text.translatable("config.fabric-mumblelink-mod.clientAutoLaunchOption"),
                            AutoLaunchOption::class.java,
                            ClientMumbleLinkMod.config.clientAutoLaunchOption,
                        ).setDefaultValue(AutoLaunchOption.ACCEPT)
                        .setSaveConsumer { ClientMumbleLinkMod.config.clientAutoLaunchOption = it }
                        .build(),
                )

                clientCategory.addEntry(
                    entryBuilder
                        .startFloatField(
                            Text.translatable("config.fabric-mumblelink-mod.clientDimensionYAxisAdjust"),
                            ClientMumbleLinkMod.config.clientDimensionYAxisAdjust,
                        ).setDefaultValue(0.0f)
                        .setSaveConsumer { ClientMumbleLinkMod.config.clientDimensionYAxisAdjust = it }
                        .build(),
                )

                // Server category
                val serverCategory: ConfigCategory =
                    builder.getOrCreateCategory(
                        Text.translatable("config.fabric-mumblelink-mod.main"),
                    )

                serverCategory.addEntry(
                    entryBuilder
                        .startEnumSelector(
                            Text.translatable("config.fabric-mumblelink-mod.voipClient"),
                            VoipClient::class.java,
                            MainMumbleLinkMod.config.voipClient,
                        ).setDefaultValue(VoipClient.MUMBLE)
                        .setSaveConsumer { MainMumbleLinkMod.config.voipClient = it }
                        .build(),
                )

                serverCategory.addEntry(
                    entryBuilder
                        .startStrField(
                            Text.translatable("config.fabric-mumblelink-mod.voipServerUserinfo"),
                            MainMumbleLinkMod.config.voipServerUserinfo,
                        ).setDefaultValue("")
                        .setSaveConsumer { MainMumbleLinkMod.config.voipServerUserinfo = it }
                        .build(),
                )

                serverCategory.addEntry(
                    entryBuilder
                        .startStrField(
                            Text.translatable("config.fabric-mumblelink-mod.voipServerHost"),
                            MainMumbleLinkMod.config.voipServerHost,
                        ).setDefaultValue("")
                        .setSaveConsumer { MainMumbleLinkMod.config.voipServerHost = it }
                        .build(),
                )

                serverCategory.addEntry(
                    entryBuilder
                        .startIntField(
                            Text.translatable("config.fabric-mumblelink-mod.voipServerPort"),
                            MainMumbleLinkMod.config.voipServerPort,
                        ).setDefaultValue(-1)
                        .setSaveConsumer { MainMumbleLinkMod.config.voipServerPort = it }
                        .build(),
                )

                serverCategory.addEntry(
                    entryBuilder
                        .startStrField(
                            Text.translatable("config.fabric-mumblelink-mod.voipServerPath"),
                            MainMumbleLinkMod.config.voipServerPath,
                        ).setDefaultValue("")
                        .setSaveConsumer { MainMumbleLinkMod.config.voipServerPath = it }
                        .build(),
                )

                serverCategory.addEntry(
                    entryBuilder
                        .startStrField(
                            Text.translatable("config.fabric-mumblelink-mod.voipServerQuery"),
                            MainMumbleLinkMod.config.voipServerQuery,
                        ).setDefaultValue("")
                        .setSaveConsumer { MainMumbleLinkMod.config.voipServerQuery = it }
                        .build(),
                )

                serverCategory.addEntry(
                    entryBuilder
                        .startStrField(
                            Text.translatable("config.fabric-mumblelink-mod.voipServerFragment"),
                            MainMumbleLinkMod.config.voipServerFragment,
                        ).setDefaultValue("")
                        .setSaveConsumer { MainMumbleLinkMod.config.voipServerFragment = it }
                        .build(),
                )

                builder.build()
            } catch (e: Exception) {
                LOG.error("Failed to create config screen", e)
                throw e
            }
        }
}
