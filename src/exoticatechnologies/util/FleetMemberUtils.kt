package exoticatechnologies.util

import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import exoticatechnologies.campaign.listeners.CampaignEventListener.Companion.activeFleets
import exoticatechnologies.util.FleetMemberUtils.findFleetForVariant

object FleetMemberUtils {
    @JvmField
    val moduleMap: MutableMap<String, FleetMemberAPI> = HashMap()

    @JvmStatic
    fun findMemberFromShip(ship: ShipAPI): FleetMemberAPI? {
        val id = ship.variant.hullVariantId
        if (moduleMap.containsKey(id)) {
            return moduleMap[id]
        }
        if (ship.parentStation != null) {
            return findMemberFromShip(ship.parentStation)
        }
        return if (ship.fleetMember != null) {
            ship.fleetMember
        } else {
            findMemberForStats(ship.mutableStats)
        }
    }

    @JvmStatic
    fun findMemberForStats(stats: MutableShipStatsAPI): FleetMemberAPI? {
        val id = stats.variant.hullVariantId
        if (moduleMap.containsKey(id)) {
            return moduleMap[id]
        }
        if (stats.fleetMember != null) {
            return stats.fleetMember
        }
        if (stats.entity is ShipAPI) {
            val ship = stats.entity as ShipAPI
            if (ship.fleetMember != null) {
                return ship.fleetMember
            }
        }

        //note: this looks awful, but it actually doesn't go into this loop all that often.
        for (fleet in activeFleets) {
            val fm = searchFleetForStats(fleet, stats)
            if (fm != null) {
                return fm
            }
        }
        return null
    }

    private fun searchFleetForStats(fleet: CampaignFleetAPI?, stats: MutableShipStatsAPI): FleetMemberAPI? {
        if (fleet == null) {
            return null
        }
        for (member in fleet.fleetData.membersListCopy) {
            if (member.isFighterWing) continue
            val memberStats = member.stats
            if (memberStats === stats) {
                return member
            } else if (stats.entity != null && memberStats.entity === stats.entity) {
                return member
            } else if (stats.fleetMember != null && stats.fleetMember === member) {
                return member
            } else if (stats.variant != null && member.variant === stats.variant) {
                return member
            } else {
                var opStats: MutableShipStatsAPI? = null
                try {
                    opStats = member.variant.statsForOpCosts
                } catch (ex: Throwable) {
                    // do nothing
                }
                if (opStats != null) {
                    if (opStats === stats) {
                        return member
                    } else if (stats.entity != null && opStats.entity === stats.entity) {
                        return member
                    } else if (stats.fleetMember != null && opStats.fleetMember === stats.fleetMember) {
                        return member
                    } else if (stats.variant != null && opStats.variant === stats.variant) {
                        return member
                    }
                }
            }
            val shipVariant = member.variant
            for (moduleVariantId in shipVariant.stationModules.keys) {
                val moduleVariant = shipVariant.getModuleVariant(moduleVariantId)
                var moduleStats: MutableShipStatsAPI?
                moduleStats = try {
                    moduleVariant.statsForOpCosts
                } catch (ex: Throwable) {
                    continue
                }
                if (moduleStats != null) {
                    if (moduleStats === stats) {
                        return member
                    } else if (stats.entity != null && stats.entity === moduleStats.entity) {
                        return member
                    } else if (stats.fleetMember != null && moduleStats.fleetMember === stats.fleetMember) {
                        return member
                    }
                }
            }
        }
        return null
    }

    fun findFleetForVariant(variant: ShipVariantAPI, member: FleetMemberAPI): CampaignFleetAPI? {
        val id = variant.hullVariantId
        if (moduleMap.containsKey(id)) {
            return moduleMap[id]!!.fleetData.fleet
        }

        member.fleetData?.fleet?.let {
            return it
        }

        member.fleetCommander?.fleet?.let {
            return it
        }

        member.captain?.fleet?.let {
            return it
        }

        return activeFleets //do not remove the filterNotNull
            .filterNotNull()
            .firstOrNull { fleet ->
            fleet.membersWithFightersCopy?.any { member ->
                member.variant.stationModules.keys.any {
                    member.variant.getModuleVariant(it) == variant }
            } ?: false
        }
    }

    /**
     * Gets commander from [member] by either doing [FleetMemberAPI.getFleetCommander]
     * and if that is null, it tries going through [CampaignFleetAPI]. Bails out if
     * [CampaignFleetAPI.getFleetData] is null to avoid NPE.
     *
     * @param member the member to look up the Fleet Commander for
     * @return the fleet commander if found (and has fleet data) or null
     */
    fun getFleetCommander(member: FleetMemberAPI): PersonAPI? {
        var fleetCommander = member.fleetCommander
        if (member.fleetCommander == null) {
            fleetCommander = if (member.fleetData != null) {
                member.fleetData.commander
            } else {
                // Since '.commander' or `.getCommander()` goes through fleet data, check again
//                findFleetForVariant(member.variant, member)?.commander
                // relevant code from CampaignFleetAPI:
                // public Person getCommander() {
                //     return this.fleetData.getCommander();
                // }
                val campaignFleet = findFleetForVariant(member.variant, member)

                if (campaignFleet?.fleetData != null) {
                    campaignFleet.commander
                } else {
                    null
                }
            }
        }

        return fleetCommander
    }

}

fun FleetMemberAPI.getFleetModuleSafe(): CampaignFleetAPI? {
    return findFleetForVariant(this.variant, this)
}

fun FleetMemberAPI.propagateHullmodToChildFms(hullmodId: String) {
    if (this.variant.stationModules.isEmpty()) return
    val fleet = findFleetForVariant(this.variant, this) ?: return
    val fleetMembers = fleet.fleetData.membersListCopy
    val matched = HashSet<FleetMemberAPI>().also { it.add(this) }
    addHullmodToChildFms(this.variant, fleetMembers, matched, hullmodId)
}

private fun addHullmodToChildFms(
    parentV: ShipVariantAPI,
    fleetMembers: List<FleetMemberAPI>,
    matched: MutableSet<FleetMemberAPI>,
    hullmodId: String
) {
    for ((slotId, _) in parentV.stationModules) {
        val childV = parentV.getModuleVariant(slotId) ?: continue
        // Match each station module slot to the first fleet member with the same hullVariantId
        // that hasn't already been matched (handles symmetric modules sharing a hullVariantId).
        val childFM = fleetMembers.firstOrNull { fm ->
            fm !in matched && fm.variant.hullVariantId == childV.hullVariantId
        }
        if (childFM != null) {
            matched.add(childFM)
            if (!childFM.variant.hasHullMod(hullmodId)) {
                childFM.variant.addPermaMod(hullmodId)
            }
        }
        addHullmodToChildFms(childV, fleetMembers, matched, hullmodId)
    }
}
