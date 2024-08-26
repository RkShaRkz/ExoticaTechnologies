package exoticatechnologies.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.loading.VariantSource
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import exoticatechnologies.modifications.ShipModFactory
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.util.reflect.ReflectionUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
 * Returns names of all ships in a list
 *
 * @param shipList list of ships whose names to return
 * @return list of these ships' names
 */
fun getNamesOfShipsInListOfShips(shipList: List<ShipAPI>): List<String> {
    return shipList.map { ship -> ship.name }
}

/**
 * For simple ships, this is exactly the same as [ShipAPI.getAllWeapons] but for multi-segmented ships, it will
 * walk through all segments and return *all* weapons on the ship regardless of whether [ship] was the main module or not
 *
 * @param ship ship which to walk through and gather all weapons to be returned
 * @return all weapons contained in [ship], all of it's children or all of it's parent's children
 */
fun getAllShipWeapons(ship: ShipAPI): List<WeaponAPI> {
    return getAllShipSections(ship)
            .flatMap { shipModule -> shipModule.allWeapons }
}

/**
 * Data class for holding all necessary params to pass to [ShipAPI.addAfterimage]
 */
data class AfterimageData(
        /**
         * Color to use
         */
        val color: Color,
        /**
         * Location X, be aware that afterimages use ship-relative coordinates so anything other than 0 will
         * displace it from the ship
         */
        val locX: Float,
        /**
         * Location Y, be aware that afterimages use ship-relative coordinates so anything other than 0 will
         * displace it from the ship
         */
        val locY: Float,
        /**
         * Velocity X, velocity to apply to the afterimage
         */
        val velX: Float,
        /**
         * Velocity Y, velocity to apply to the afterimage
         */
        val velY: Float,
        /**
         * Maximum jitter to apply
         */
        val maxJitter: Float,
        /**
         * The "transitioning-in" duration
         */
        val inDuration: Float,
        /**
         * The afterimage duration
         */
        val duration: Float,
        /**
         * The "transitioning-out" duration
         */
        val outDuration: Float,
        /**
         * Whether this afterimage is additive
         */
        val additive: Boolean,
        /**
         * Whether the afterimage will combine with sprite color
         */
        val combineWithSpriteColor: Boolean,
        /**
         * Whether this afterimage is above or below the ship
         */
        val aboveShip: Boolean
)

/**
 * Adds an afterimage to [ship] by using data from [AfterimageData]
 *
 * @param ship to add the afterimage to
 * @param data afterimage arguments
 */
fun addAfterimageTo(ship: ShipAPI, data: AfterimageData) {
    ship.addAfterimage(
            data.color,
            data.locX,
            data.locY,
            data.velX,
            data.velY,
            data.maxJitter,
            data.inDuration,
            data.duration,
            data.outDuration,
            data.additive,
            data.combineWithSpriteColor,
            data.aboveShip
    )
}

/**
 * Applies an afterimage to all modules of [ship], while applying [data] to the [ShipAPI.addAfterimage]
 *
 * @param ship the ship to which to apply the afterimage
 * @param data the [AfterimageData] to apply
 */
fun addAfterimageToWholeShip(ship: ShipAPI, data: AfterimageData) {
    // If ship is parent, apply to children
    if (ship.childModulesCopy.isNotEmpty()) {
        for (module in ship.childModulesCopy) {
            addAfterimageTo(module, data)
        }
        // also apply to ship since he's parent
        addAfterimageTo(ship, data)

        // and return because we're done
        return
    }

    // If ship is a submodule, get parent, and apply to all his children
    if (ship.parentStation != null) {
        val parent = ship.parentStation
        for (module in parent.childModulesCopy) {
            addAfterimageTo(module, data)
        }
        // but also apply to parent now that all his children (including the original 'ship' which was a child) were painted
        addAfterimageTo(parent, data)

        // and return because we're done
        return
    }

    // The last scenario is - it's single module ship, and it needs to be painted, so just paint it
    addAfterimageTo(ship, data)

    // and return because we're done
    return
}

/**
 * Returns all modules of the ship the [ship] belongs to. For single-module ships, [ship] is already the whole ship.
 *
 * For multi-module ships, the method will gather all modules regardless of whether [ship] is the main module or
 * child module.
 *
 * @param ship the ship for which to collect all modules
 * @return list of [ship]'s ship modules
 */
fun getAllShipSections(ship: ShipAPI): List<ShipAPI> {
    // If ship is parent, apply to children
    if (ship.childModulesCopy.isNotEmpty()) {
        return combineIntoList(ship.childModulesCopy, ship)
    }

    // If ship is a submodule, get parent, and apply to all his children
    if (ship.parentStation != null) {
        val parent = ship.parentStation
        return combineIntoList(parent.childModulesCopy, parent)
    }

    // The last scenario is - it's single module ship, so just return that
    return listOf(ship)
}

/**
 * Returns whether the [ship] is a multi-module (or belongs to a multi-module) ship.
 * Internally calls [getAllShipSections] so you might not want to call this in every frame
 *
 * @param ship the ship to check
 * @return [false] if it's a single-module or [true] if it's a multi-module ship
 * @see getAllShipSections
 * @see isMultiModuleShipFast
 */
fun isMultiModuleShip(ship: ShipAPI): Boolean {
    return getAllShipSections(ship).size > 1
}

/**
 * Faster version to check whether the [ship] is a multi-module (or belongs to a multi-module) ship.
 * Internally checks whether [ShipAPI.getParentStation] is non-null or [ShipAPI.getChildModulesCopy] is non-empty
 *
 * @param ship the ship to check
 * @return [false] if it's a single-module or [true] if it's a multi-module ship
 * @see isMultiModuleShip
 */
fun isMultiModuleShipFast(ship: ShipAPI): Boolean {
    return ship.parentStation != null || ship.childModulesCopy.isNotEmpty()
}

/**
 * Returns whether this ship is a multi-module (or belongs to a multi-module) ship.
 * Internally calls [getAllShipSections] so you might not want to call this in every frame
 *
 * @return whether [false] if it's a single-module or [true] if it's a multi-module ship
 * @see getAllShipSections
 * @see isMultiModuleShip
 * @see isMultiModuleShipFast
 */
fun ShipAPI.isThisAMultiModuleShip(): Boolean {
    return getAllShipSections(this).size > 1
}

/**
 * Faster version to check whether this ship is a multi-module (or belongs to a multi-module) ship.
 * Internally checks whether [ShipAPI.getParentStation] is non-null or [ShipAPI.getChildModulesCopy] is non-empty
 *
 * @return whether [false] if it's a single-module or [true] if it's a multi-module ship
 * @see ShipAPI.isThisAMultiModuleShip
 */
fun ShipAPI.isThisAMultiModuleShipFast(): Boolean {
    return this.parentStation != null || this.childModulesCopy.isNotEmpty()
}

/**
 * Method for calculating a vector pointing from [fromVector] to [toVector]
 *
 * @param fromVector the vector from which we want to start pointing
 * @param toVector the vector to which we want to point to
 * @return vector pointing from [fromVector] to [toVector]
 */
fun getDirectionVector(fromVector: Vector2f, toVector: Vector2f): Vector2f {
    return toVector.sub(fromVector)
}


/**
 * Returns vector pointing from this vector to [toVector]
 *
 * @param toVector the vector to point to
 * @return vector pointing from this vector to [toVector]
 */
fun Vector2f.getDirectionVectorTo(toVector: Vector2f): Vector2f {
    return toVector.sub(this)
}

/**
 * Calculates velocity vector, a vector which will allow us to get from
 * [fromVector] to [toVector] in [time] amount of time.
 *
 * @param fromVector first vector, from which we're starting to travel
 * @param toVector second vector, to which we are going to travel
 * @param time amount of time in which we want to get from [fromVector] to [toVector]
 * @return the velocity vector
 */
fun getVelocityVector(fromVector: Vector2f, toVector: Vector2f, time: Float): Vector2f {
    return toVector.sub(fromVector).div(time)
}

/**
 * Scalar division of [this] vector by [scalar]
 *
 * @param scalar the scalar to divide this vector with
 * @return vector that has it's X and Y divided by [scalar]
 */
fun Vector2f.div(scalar: Float): Vector2f {
    return Vector2f(this.x / scalar, this.y / scalar)
}

/**
 * Scalar multiplication of [this] vector by [scalar]
 *
 * @param scalar the scalar to multiply this vector with
 * @return vector that has it's X and Y multiplied by [scalar]
 */
fun Vector2f.mul(scalar: Float): Vector2f {
    return Vector2f(this.x * scalar, this.y * scalar)
}

/**
 * Scalar addition of [this] vector and [scalar]
 *
 * @param scalar the scalar to add to this vector
 * @return vector that has it's X and Y increased by [scalar]
 */
fun Vector2f.add(scalar: Float): Vector2f {
    return Vector2f(this.x + scalar, this.y + scalar)
}

/**
 * Scalar subtraction of [this] vector and [scalar]
 *
 * @param scalar the scalar to substract from this vector
 * @return vector that has it's X and Y subtracted by [scalar]
 */
fun Vector2f.sub(scalar: Float): Vector2f {
    return Vector2f(this.x - scalar, this.y - scalar)
}

/**
 * Vector division of [this] vector by [vector]
 *
 * @param vector the vector to divide this vector with
 * @return vector that has it's X and Y divided by [vector]'s X and Y
 */
fun Vector2f.div(vector: Vector2f): Vector2f {
    return Vector2f(this.x / vector.x, this.y / vector.y)
}

/**
 * Vector multiplication of [this] vector by [vector]
 *
 * @param vector the vector to multiply this vector with
 * @return vector that has it's X and Y multiplied by [vector]'s X and Y
 */
fun Vector2f.mul(vector: Vector2f): Vector2f {
    return Vector2f(this.x * vector.x, this.y * vector.y)
}

/**
 * Vector addition of [this] vector and [vector]
 *
 * @param vector the vector to add to this vector
 * @return vector that has it's X and Y increased by [vector]'s X and Y
 */
fun Vector2f.add(vector: Vector2f): Vector2f {
    return Vector2f(this.x + vector.x, this.y + vector.y)
}

/**
 * Vector subtraction of [this] vector and [vector]
 *
 * @param vector the vector to subtract from this vector
 * @return vector that has it's X and Y subtracted by [vector]'s X and Y
 */
fun Vector2f.sub(vector: Vector2f): Vector2f {
    return Vector2f(this.x - vector.x, this.y - vector.y)
}

/**
 * Calculate the magnitude (length) of this vector
 *
 * @see [Vector2f.length]
 */
fun Vector2f.magnitude(): Float {
    return sqrt(this.x * this.x + this.y * this.y)
}

/**
 * Normalize a vector, but unlike [Vector.normalise] this one doesn't throw for zero-length vectors
 *
 * @see [Vector.normalise]
 */
fun Vector2f.normalized(): Vector2f {
    val magnitude = this.magnitude()
    return if(magnitude != 0.0f) {
        Vector2f(x / magnitude, y / magnitude)
    } else {
        this
    }
}

/**
 * Returns a clone of [this] vector as a new instance
 */
fun Vector2f.clone(): Vector2f {
    return Vector2f(this.x, this.y)
}

/**
 * Calculates velocity vector which will take us from [fromVector] to [toVector] in [time] amount of time,
 * while taking distance into account.
 *
 * Unlike [getVelocityVector], this method takes speed into account, which makes the distance between the vectors
 * matter more
 *
 * @param fromVector first vector, from which we're starting to travel
 * @param toVector second vector, to which we are going to travel
 * @param time amount of time in which we want to get from [fromVector] to [toVector]
 * @return the velocity vector
 *
 * @see getVelocityVector
 */
fun calculateVelocityVector(fromVector: Vector2f, toVector: Vector2f, time: Float): Vector2f {
    val direction = getDirectionVector(fromVector, toVector)
    val distance = Misc.getDistance(fromVector, toVector)
    val speed = distance / time

    return direction.mul(speed / distance)
}

/**
 * Method that combines [lists] into a single List
 * List **have** to be of same type
 *
 * @param list the list to combine into the resulting list
 * @return list containing all listed elements (arguments)
 */
fun <T> combineIntoList(vararg lists: List<T>): List<T> {
    val retVal = mutableListOf<T>()
    for (list in lists) {
        retVal.addAll(list)
    }

    return retVal.toList()
}

/**
 * Method that combines a [list] and [elements] into a single List
 * List and elements **have** to be of same type
 *
 * @param list the list to combine into the resulting list
 * @param elements the elements to add onto that list
 * @return list containing all listed elements (arguments)
 */
fun <T> combineIntoList(list: List<T>, vararg elements: T): List<T> {
    val retVal = mutableListOf<T>()
    retVal.addAll(list)
    // I can't "pass" the vararg modifier to listOf() so calling it on 'elements' will just make a list of one array item
    for (element in elements) {
        retVal.add(element)
    }

    return retVal.toList()
}


val <T> T.exhaustive: T
    get() = this

fun log(logMsg: String, logger: Logger, logLevel: Level = Level.DEBUG) {
    with(logger) {
        when (logLevel) {
            Level.DEBUG -> this.debug(logMsg)
            Level.INFO -> this.info(logMsg)
            Level.WARN -> this.warn(logMsg)
            Level.ERROR -> this.error(logMsg)
            Level.FATAL -> this.fatal(logMsg)

            else -> { /* do nothing */ }
        }.exhaustive
    }
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