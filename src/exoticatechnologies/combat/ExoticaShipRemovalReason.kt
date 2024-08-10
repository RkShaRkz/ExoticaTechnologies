package exoticatechnologies.combat

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.mission.FleetSide
import com.fs.starfarer.api.combat.CombatFleetManagerAPI
import exoticatechnologies.combat.ExoticaShipRemovalReason.NONE_OR_UNKNOWN

/**
 * Enum class describing the reason how/why the ship was removed from combat.
 *
 * **NOTE:**
 * While this class tries it's best to explain the how/why, make no mistake about [NONE_OR_UNKNOWN] that it can in
 * any possible way mean or imply that the ship was not removed from combat
 */
enum class ExoticaShipRemovalReason {
    /**
     * Unknown removal reason, or 'none' as is the case when the combat ends and the remaining ships are removed from play
     */
    NONE_OR_UNKNOWN,

    /**
     * When the removed ship has either less-or-equal to 1% of hull or it's [ShipAPI.isAlive] returns false -
     * it's cause of removal is presumed to be because it died
     */
    DEATH,

    /**
     * When the removed ship is found in either [CombatFleetManagerAPI.getRetreatedCopy] for either [FleetSide],
     * it's cause of removal is presumed to be because it fled
     */
    RETREAT
}