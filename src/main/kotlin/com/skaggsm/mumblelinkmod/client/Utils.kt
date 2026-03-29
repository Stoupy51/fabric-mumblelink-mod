package com.skaggsm.mumblelinkmod.client

import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3

/**
 * @old Convert to a float 3-array in a left-handed coordinate system.
 * Minecraft is right-handed by default, Mumble needs left-handed.
 *
 * @see <a href="https://wiki.mumble.info/wiki/Link#Coordinate_system">Coordinate system</a>
 */
val Vec3.toLHArray: FloatArray
    get() = floatArrayOf(x.toFloat(), y.toFloat(), -z.toFloat())

/**
 * Convert to a float 3-array in a right-handed coordinate system.
 */
val Vec3.toRHArray: FloatArray
    get() = floatArrayOf(x.toFloat(), z.toFloat(), y.toFloat())

/**
 * A stable hash function designed for world IDs.
 * Different clients should be able to run this on the same world ID and get the same result.
 *
 * Based on the `djb2` hash function: [Hash Functions](http://www.cse.yorku.ca/~oz/hash.html)
 */
val Identifier.stableHash: Int
    get() {
        var hash = 5381

        for (c in this.toString()) {
            hash += (hash shl 5) + c.code
        }

        return hash
    }

val String.stableHash: Int
    get() {
        var hash = 5381

        for (c in this) {
            hash += (hash shl 5) + c.code
        }

        return hash
    }
