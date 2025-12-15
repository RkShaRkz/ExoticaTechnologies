package exoticatechnologies.modifications.exotics.misc

import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.ShipAPI
import org.apache.log4j.Logger
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import kotlin.math.max

object ForceApplier {
    private const val LOGS_ENABLED = false
    private val logger = Logger.getLogger(ForceApplier::class.java)

    fun applyMomentum(
        entity: CombatEntityAPI?,
        pointOfImpact: Vector2f?,
        direction: Vector2f,
        momentum: Float,
        elasticCollision: Boolean,
        modifyAngularVelocity: Boolean = true
    ) {
        log("--> applyMomentum()")
        // This whole thing is weird, but necessary since arguments are being reassigned for some reason
        var entity = entity
        var direction = direction
        var momentum = momentum

        if (entity == null) {
            return
        } else {
            // Filter out forces without a direction
            if (direction.lengthSquared() == 0f) {
                return
            }
            // Avoid divide-by-zero errors...
            var mass = max(1.0, entity.mass.toDouble()).toFloat()
            // We should not move stations, right?
            if (entity is ShipAPI) {
                val ship = entity
                if (ship.isStation || (ship.isStationModule && ship.parentStation.isStation)) {
                    return
                }
                if (ship.isStationModule && ship.isShipWithModules) {
                    entity = ship.parentStation
                    mass = max(1.0, ship.massWithModules.toDouble()).toFloat()
                }
            }
            // Momentum is far too weak otherwise
            momentum *= 100f
            log("Actual momentum ${momentum}")
            // Doing some vector calculate
            val BPtoMC = entity?.let { Vector2f.sub(it.location, pointOfImpact, null) }
                    ?: throw RuntimeException("entity was null while assigning to BPtoMC -- this should not be happening. Look into GuardianShield -> ForceApplier::applyMomentum()")
            val forceV = Vector2f()
            direction.normalise(forceV)
            forceV.scale(momentum)
            // get force vector
            BPtoMC.normalise(BPtoMC)
            // calculate acceleration
            BPtoMC.scale(Vector2f.dot(forceV, BPtoMC) / mass)
            if (elasticCollision) {
                // Apply velocity change
                Vector2f.add(BPtoMC, entity.velocity, entity.velocity)
            } else {
                // Apply velocity change
                direction = Vector2f(forceV)
                direction.scale(1 / mass)
                Vector2f.add(direction, entity.velocity, entity.velocity)
            }
            if (modifyAngularVelocity) {
                // calculate moment change
                var angularAcc = VectorUtils.getCrossProduct(forceV, BPtoMC) / (0.5f * mass * entity.collisionRadius * entity.collisionRadius)
                angularAcc = Math.toDegrees(angularAcc.toDouble()).toFloat()
                // Apply angular velocity change
                if (elasticCollision) {
                    entity.angularVelocity = entity.angularVelocity + angularAcc
                } else {
                    entity.angularVelocity = entity.angularVelocity - angularAcc
                }
            }
        }
        log("<-- applyMomentum()")
    }

    private fun log(logMsg: String) {
        if (LOGS_ENABLED) {
            logger.info(logMsg)
        }
    }
}
