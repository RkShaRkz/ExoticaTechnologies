package exoticatechnologies.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI

/**
 * Method that plays a sound with a [soundId] ID, using the [ship]'s location and velocity,
 * with a [pitch] and [volume] pitch and volume
 */
fun playSound(soundId: String, ship: ShipAPI, pitch: Float = 1.0f, volume: Float = 1.0f) {
    Global.getSoundPlayer().playSound(
            soundId,
            pitch,
            volume,
            ship.location,
            ship.velocity
    )
}