package exoticatechnologies.util

import com.fs.starfarer.api.combat.CollisionClass
import com.fs.starfarer.api.combat.ShipAPI
import org.apache.log4j.Logger
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.geom.Line2D
import kotlin.math.sqrt

object CollisionUtil {

    private val logger: Logger = Logger.getLogger(CollisionUtil::class.java)

    private fun getShipCollisionPoint(segStart: Vector2f, segEnd: Vector2f, ship: ShipAPI, accurateShieldEdgeTest: Boolean): Vector2f? {
        // if target can not be hit, return null

        if (ship.collisionClass == CollisionClass.NONE) {
            return null
        }
        val shield = ship.shield

        // Check hit point when shield is off.
        if (shield == null || shield.isOff) {
            return CollisionUtils.getCollisionPoint(segStart, segEnd, ship)
        } // If ship's shield is on, thing goes complicated...
        else {
            val circleCenter = shield.location
            val circleRadius = shield.radius
            // calculate the shield collision point
            val tmp1 = getCollisionPointOnCircle(segStart, segEnd, circleCenter, circleRadius)
            if (tmp1 != null) {
                // OK! hit the shield in face
                if (shield.isWithinArc(tmp1)) {
                    return tmp1
                } else {
                    var hit = false
                    var tmp: Vector2f? = Vector2f(segEnd)

                    //the beam cannot go farther than it's max range or the hull
                    val hullHit = CollisionUtils.getCollisionPoint(segStart, segEnd, ship)
                    if (hullHit != null) {
                        tmp = hullHit
                        hit = true
                    }

                    // if the hit come outside the shield's arc but it hit the shield's "edge", find that point.
                    if (accurateShieldEdgeTest) {
                        val shieldEdge1 = MathUtils.getPointOnCircumference(circleCenter, circleRadius, MathUtils.clampAngle(shield.facing + shield.activeArc / 2))
                        val tmp2 = CollisionUtils.getCollisionPoint(segStart, tmp, circleCenter, shieldEdge1)
                        if (tmp2 != null) {
                            tmp = tmp2
                            hit = true
                        }
                        val shieldEdge2 = MathUtils.getPointOnCircumference(circleCenter, circleRadius, MathUtils.clampAngle(shield.facing - shield.activeArc / 2))
                        val tmp3 = CollisionUtils.getCollisionPoint(segStart, tmp, circleCenter, shieldEdge2)
                        if (tmp3 != null) {
                            tmp = tmp3
                            hit = true
                        }
                    }

                    // return null if segment not hit anything.
                    return if (hit) tmp else null
                }
            }
        }
        return null
    }

    fun getShipCollisionPoint(segStart: Vector2f?, segEnd: Vector2f?, ship: ShipAPI?): Vector2f? {
        return if (segStart != null && segEnd != null && ship != null) {
            getShipCollisionPoint(segStart, segEnd, ship, false)
        } else {
            val segStartNull = segStart == null
            val segEndNull = segEnd == null
            val shipNull = ship == null

            // figure out which one was null
            // log accordingly about it being null
            val nullMessages = mutableListOf<String>()

            if (segStartNull) {
                nullMessages.add("segStart was null")
            }
            if (segEndNull) {
                nullMessages.add("segEnd was null")
            }
            if (shipNull) {
                nullMessages.add("ship was null")
            }

            log("${nullMessages.joinToString(", ")}, returning null")

            //return null
            null
        }
    }

    private fun getCollisionPointOnCircle(segStart: Vector2f, segEnd: Vector2f, circleCenter: Vector2f, circleRadius: Float): Vector2f? {
        if (segStart == segEnd) return null
        val startToEnd = Vector2f.sub(segEnd, segStart, null)
        val startToCenter = Vector2f.sub(circleCenter, segStart, null)
        val ptLineDistSq = Line2D.ptLineDistSq(segStart.x.toDouble(), segStart.y.toDouble(), segEnd.x.toDouble(), segEnd.y.toDouble(), circleCenter.x.toDouble(), circleCenter.y.toDouble()).toFloat().toDouble()

        val circleRadiusSq = circleRadius * circleRadius

        // if lineStart is within the circle, return it directly
        if (startToCenter.lengthSquared() < circleRadiusSq) {
            return segStart
        }

        // if lineStart is outside the circle and segment can not reach the circumference, return null
        if (ptLineDistSq > circleRadiusSq || startToCenter.length() - circleRadius > startToEnd.length()) {
            return null
        }

        // calculate the intersection point.
        startToEnd.normalise(startToEnd)
        val dist = Vector2f.dot(startToCenter, startToEnd) - sqrt(circleRadiusSq - ptLineDistSq)
        startToEnd.scale(dist.toFloat())
        return Vector2f.add(segStart, startToEnd, null)
    }

    fun getCollisionPointOnCircumference(segStart: Vector2f, segEnd: Vector2f, circleCenter: Vector2f, circleRadius: Float): Vector2f? {
        val startToEnd = Vector2f.sub(segEnd, segStart, null)
        val startToCenter = Vector2f.sub(circleCenter, segStart, null)
        val ptLineDistSq = Line2D.ptLineDistSq(segStart.x.toDouble(), segStart.y.toDouble(), segEnd.x.toDouble(), segEnd.y.toDouble(), circleCenter.x.toDouble(), circleCenter.y.toDouble()).toFloat().toDouble()
        val circleRadiusSq = circleRadius * circleRadius
        var CoS = false
        // if lineStart is within the circle, return it directly
        if (startToCenter.lengthSquared() < circleRadiusSq) {
            CoS = true
        }

        // if lineStart is outside the circle and segment can not reach the circumference, return null
        if (ptLineDistSq > circleRadiusSq || startToCenter.length() - circleRadius > startToEnd.length()) {
            return null
        }

        // calculate the intersection point.
        startToEnd.normalise(startToEnd)
        val dist: Double
        if (CoS) {
            dist = Vector2f.dot(startToCenter, startToEnd) + sqrt(circleRadiusSq - ptLineDistSq)
            if (dist < startToEnd.length()) {
                return null
            }
        } else {
            dist = Vector2f.dot(startToCenter, startToEnd) - sqrt(circleRadiusSq - ptLineDistSq)
        }
        startToEnd.scale(dist.toFloat())
        return Vector2f.add(segStart, startToEnd, null)
    }

    private fun getShieldCollisionPoint(segStart: Vector2f, segEnd: Vector2f, ship: ShipAPI, ignoreHull: Boolean, accurateShieldEdgeTest: Boolean): Vector2f? {
        // if target not shielded, return null
        val shield = ship.shield
        if (ship.collisionClass == CollisionClass.NONE || shield == null || shield.isOff) {
            return null
        }
        val circleCenter = shield.location
        val circleRadius = shield.radius
        // calculate the shield collision point
        val tmp1 = getCollisionPointOnCircle(segStart, segEnd, circleCenter, circleRadius)
        if (tmp1 != null) {
            // OK! hit the shield in face
            if (shield.isWithinArc(tmp1)) {
                return tmp1
            } else {
                // if the hit come outside the shield's arc but it hit the shield's "edge", find that point.

                var tmp: Vector2f? = Vector2f(segEnd)
                var hit = false
                if (accurateShieldEdgeTest) {
                    val shieldEdge1 = MathUtils.getPointOnCircumference(circleCenter, circleRadius, MathUtils.clampAngle(shield.facing + shield.activeArc / 2))
                    val tmp2 = CollisionUtils.getCollisionPoint(segStart, tmp, circleCenter, shieldEdge1)
                    if (tmp2 != null) {
                        tmp = tmp2
                        hit = true
                    }

                    val shieldEdge2 = MathUtils.getPointOnCircumference(circleCenter, circleRadius, MathUtils.clampAngle(shield.facing - shield.activeArc / 2))
                    val tmp3 = CollisionUtils.getCollisionPoint(segStart, tmp, circleCenter, shieldEdge2)
                    if (tmp3 != null) {
                        tmp = tmp3
                        hit = true
                    }
                }
                // If we don't ignore hull hit, check if there is one...
                if (!ignoreHull && CollisionUtils.getCollisionPoint(segStart, tmp, ship) != null) {
                    return null
                }
                // return null if do not hit shield.
                return if (hit) tmp else null
            }
        }
        return null
    }

    fun getShieldCollisionPoint(segStart: Vector2f, segEnd: Vector2f, ship: ShipAPI, ignoreHull: Boolean): Vector2f? {
        return getShieldCollisionPoint(segStart, segEnd, ship, ignoreHull, false)
    }

    private fun log(logString: String) {
        logger.info(logString)
    }
}
