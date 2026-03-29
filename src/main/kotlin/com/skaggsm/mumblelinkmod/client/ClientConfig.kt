package com.skaggsm.mumblelinkmod.client

import kotlinx.serialization.Serializable
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

@Environment(EnvType.CLIENT)
@Serializable
class ClientConfig {
    var clientAutoLaunchOption: AutoLaunchOption = AutoLaunchOption.ACCEPT

    var clientDimensionYAxisAdjust: Float = 0.0f

    @Serializable
    enum class AutoLaunchOption {
        IGNORE, // PROMPT,
        ACCEPT,
    }
}
