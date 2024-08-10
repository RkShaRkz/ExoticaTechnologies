package exoticatechnologies.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.loading.VariantSource
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import exoticatechnologies.modifications.ShipModFactory
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.util.reflect.ReflectionUtils
import org.apache.log4j.Logger
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

fun FleetMemberAPI.getMods(): ShipModifications = ShipModFactory.generateForFleetMember(this)

fun ShipVariantAPI.getRefitVariant(): ShipVariantAPI {
    var shipVariant = this
    if (shipVariant.isStockVariant || shipVariant.source != VariantSource.REFIT) {
        shipVariant = shipVariant.clone()
        shipVariant.originalVariant = null
        shipVariant.source = VariantSource.REFIT
    }
    return shipVariant
}

fun FleetMemberAPI.fixVariant() {
    val newVariant = this.variant.getRefitVariant()
    if (newVariant != this.variant) {
        this.setVariant(newVariant, false, false)
    }

    newVariant.fixModuleVariants()
}

fun ShipVariantAPI.fixModuleVariants() {
    this.stationModules.forEach { (slotId, _) ->
        val moduleVariant = this.getModuleVariant(slotId)
        val newModuleVariant = moduleVariant.getRefitVariant()
        if (newModuleVariant != moduleVariant) {
            this.setModuleVariant(slotId, newModuleVariant)
        }

        newModuleVariant.fixModuleVariants()
    }
}

fun UIPanelAPI.getChildrenCopy(): List<UIComponentAPI> {
    return ReflectionUtils.invoke("getChildrenCopy", this) as List<UIComponentAPI>
}

fun UIPanelAPI.getChildrenNonCopy(): List<UIComponentAPI> {
    return ReflectionUtils.invoke("getChildrenNonCopy", this) as List<UIComponentAPI>
}

fun UIComponentAPI.getParent(): UIPanelAPI {
    return ReflectionUtils.invoke("getParent", this) as UIPanelAPI
}

/**
 * Null-safely checks for equality, covering the case when either (or both) of the objects are null.
 */
fun Any?.safeEquals(other: Any?): Boolean {
    // Check if both objects are null
    if (this == null && other == null) {
        return true
    }
    // Check if one of the objects is null
    if (this == null || other == null) {
        return false
    }
    // Use the regular equals() method to compare non-null objects
    return this == other
}

/**
 * Returns the [Vector2f] of where the ship is looking (facing) at
 * @return the ship's forward vector, similar to [com.fs.starfarer.api.util.Misc.getUnitVectorAtDegreeAngle] used with the ship's [ShipAPI.getFacing]
 */
fun ShipAPI.getForwardVector(): Vector2f {
    val rotationRadians = Math.toRadians(this.facing.toDouble())

    // Calculate the components of the forward vector
    val x = cos(rotationRadians).toFloat()
    val y = sin(rotationRadians).toFloat()

    // Return the forward vector
    return Vector2f(x, y)
}

/**
 * Returns the angle (in degrees) between this ship's Forward Vector and *anotherShip*
 * @return the difference in degrees
 * @see [ShipAPI.getForwardVector]
 */
fun ShipAPI.getAngleToAnotherShip(anotherShip: ShipAPI): Float {
    val targetDirectionAngle = VectorUtils.getAngle(this.location, anotherShip.location)
    val myForwardVector = this.getForwardVector()
    val myAngle = VectorUtils.getAngle(myForwardVector, anotherShip.location)
    val differenceInDegrees = (myAngle - targetDirectionAngle)

    return differenceInDegrees
}

/**
 * Returns the absolue angle (in degrees) between this ship and *anotherShip*
 * @return the difference in degrees, as absolute value
 * @see [ShipAPI.getAngleToAnotherShip]
 */
fun ShipAPI.getAbsoluteAngleToAnotherShip(anotherShip: ShipAPI): Float {
    return this.getAngleToAnotherShip(anotherShip).absoluteValue
}

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
//    AnonymousLogger.log("--> getAllShipWeapons()\tship: ${ship}\tship hullspec hullname: ${ship.hullSpec.hullName}\tship childModulesCopy size: ${ship.childModulesCopy.size}\tis ship with modules: ${ship.isShipWithModules}")
    val weaponList: MutableList<WeaponAPI> = mutableListOf()
    weaponList.addAll(ship.allWeapons)
//    AnonymousLogger.log("getAllShipWeapons()\tship: ${ship}\tship's all weapons: ${ship.allWeapons.map { weapon -> weapon.id }}")
//    AnonymousLogger.log("getAllShipWeapons()\tship.variant.stationModules.size: ${ship.variant.stationModules.size}")
    if (ship.childModulesCopy.isNotEmpty()) {
        for (module in ship.childModulesCopy) {
//            AnonymousLogger.log("getAllShipWeapons()\tmodule: ${module}\tmodule name: ${module.name}\tmodule hullspec hullname: ${module.hullSpec.hullName}\tmodule's all weapons: ${module.allWeapons.map { weapon -> weapon.id }}")
            weaponList.addAll(module.allWeapons)
        }
    }
//    AnonymousLogger.log("<-- getAllShipWeapons()\treturning ${weaponList.map {weapon -> weapon.id} }")

    return weaponList
}
val <T> T.exhaustive: T
    get() = this

object AnonymousLogger {
    private val logger: Logger = Logger.getLogger(AnonymousLogger::class.java)

    fun log(log: String) {
        logger.info("[AnonymousLogger] $log")
    }

    fun log(log: String, logtag: String) {
        logger.info("[$logtag] $log")
    }
}