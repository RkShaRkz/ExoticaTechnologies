package exoticatechnologies.util.datastructures

import com.fs.starfarer.api.fleet.FleetMemberAPI
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData

/**
 * Utility class for holding the necessary baggage every [Exotic] needs, namely the [member], [mods] and [exoticData]
 */
data class ExoticStuffHolder(
        val member: FleetMemberAPI,
        val mods: ShipModifications,
        val exoticData: ExoticData
)