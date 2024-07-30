package exoticatechnologies.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import org.apache.log4j.Logger

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

/**
 * For simple ships, this is exactly the same as [ShipAPI.getAllWeapons] but for multi-segmented ships, it will
 * walk through all segments and return *all* weapons on the ship
 */
fun getAllShipWeapons(ship: ShipAPI): List<WeaponAPI> {
    AnonymousLogger.log("--> getAllShipWeapons()\tship: ${ship}\tship hullspec hullname: ${ship.hullSpec.hullName}\tship childModulesCopy size: ${ship.childModulesCopy.size}\tis ship with modules: ${ship.isShipWithModules}")
    val weaponList: MutableList<WeaponAPI> = mutableListOf()
    weaponList.addAll(ship.allWeapons)
    AnonymousLogger.log("getAllShipWeapons()\tship: ${ship}\tship's all weapons: ${ship.allWeapons.map { weapon -> weapon.id }}")
    AnonymousLogger.log("getAllShipWeapons()\tship.variant.stationModules.size: ${ship.variant.stationModules.size}")
    if (ship.childModulesCopy.isNotEmpty()) {
        for (module in ship.childModulesCopy) {
            AnonymousLogger.log("getAllShipWeapons()\tmodule: ${module}\tmodule name: ${module.name}\tmodule hullspec hullname: ${module.hullSpec.hullName}\tmodule's all weapons: ${module.allWeapons.map { weapon -> weapon.id }}")
            weaponList.addAll(module.allWeapons)
        }
    }
    AnonymousLogger.log("<-- getAllShipWeapons()\treturning ${weaponList.map {weapon -> weapon.id} }")



    return weaponList
}

object AnonymousLogger {
    private val logger: Logger = Logger.getLogger(AnonymousLogger::class.java)

    fun log(log: String) {
        logger.info("[AnonymousLogger] $log")
    }

    fun log(log: String, logtag: String) {
        logger.info("[$logtag] $log")
    }
}