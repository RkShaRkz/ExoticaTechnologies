package exoticatechnologies.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import exoticatechnologies.campaign.listeners.CampaignEventListener
import org.apache.log4j.Level
import org.apache.log4j.Logger
import java.util.WeakHashMap
import exoticatechnologies.refit.checkRefitVariant


object FleetMemberHierarchy {

    private val logger: Logger = Logger.getLogger(FleetMemberHierarchy::class.java)

    private val variantToParent = WeakHashMap<ShipVariantAPI, ShipVariantAPI>()

    @JvmStatic fun isRootModule(stats: MutableShipStatsAPI?): Boolean = isRootModule(stats?.fleetMember)
    @JvmStatic fun isRootModule(member: FleetMemberAPI?): Boolean {
        var result = false
        val currentV = member?.checkRefitVariant()
        val fleet = getFleetContext(member)

        if (currentV != null && fleet != null) {
            refreshCache(fleet)
            val isInFleet = fleet.fleetData.membersListCopy.any { it.checkRefitVariant() === currentV }
            if (isInFleet && !variantToParent.containsKey(currentV)) {
                result = true
            }
        } else if (member != null) {
            result = true
        }
        return result
    }

    @JvmStatic fun isChildModule(stats: MutableShipStatsAPI?): Boolean = isChildModule(stats?.fleetMember)
    @JvmStatic fun isChildModule(member: FleetMemberAPI?): Boolean {
        var result = false
        val variant = member?.checkRefitVariant()
        val fleet = getFleetContext(member)

        if (variant != null && fleet != null) {
            refreshCache(fleet)
            if (variantToParent.containsKey(variant)) {
                result = true
            }
        }
        return result
    }

    @JvmStatic fun getRootModule(stats: MutableShipStatsAPI?): FleetMemberAPI? = getRootModule(stats?.fleetMember)
    @JvmStatic fun getRootModule(member: FleetMemberAPI?): FleetMemberAPI? {
        var result = member
        val fleet = getFleetContext(member)
        val currentV = member?.checkRefitVariant()

        if (member != null && fleet != null && currentV != null) {
            refreshCache(fleet)
            val rootV = findRootVariant(currentV)
            val foundRoot = fleet.fleetData.membersListCopy.find { it.checkRefitVariant() === rootV }
            if (foundRoot != null) {
                result = foundRoot
            }
        }
        return result
    }

    @JvmStatic fun getAllChildModules(stats: MutableShipStatsAPI?): List<FleetMemberAPI> = getAllChildModules(stats?.fleetMember)
    @JvmStatic fun getAllChildModules(member: FleetMemberAPI?): List<FleetMemberAPI> {
        val modules = getAllModules(member).toMutableList()
        if (modules.isNotEmpty()) {
            modules.removeAt(0)
        }
        return modules
    }

    @JvmStatic fun getAllModulesStatsFromSingleStats(stats: MutableShipStatsAPI?): List<MutableShipStatsAPI> {
        val wholeShipModulesList = getAllModules(stats)
        val wholeShipStatsList = wholeShipModulesList.map { it.stats }

        return wholeShipStatsList
    }
    @JvmStatic fun getAllModules(stats: MutableShipStatsAPI?): List<FleetMemberAPI> = getAllModules(stats?.fleetMember)
    @JvmStatic fun getAllModules(member: FleetMemberAPI?): List<FleetMemberAPI> {
        val resultList = mutableListOf<FleetMemberAPI>()
        val fleet = getFleetContext(member)
        val currentV = member?.checkRefitVariant()

        if (member != null && fleet != null && currentV != null) {
            refreshCache(fleet)
            val rootV = findRootVariant(currentV)
            val rootMember = fleet.fleetData.membersListCopy.find { it.checkRefitVariant() === rootV }

            if (rootMember != null) {
                resultList.add(rootMember)
                collectModuleMembers(rootV, fleet, resultList)
            }
        } else if (member != null) {
            resultList.add(member)
        }
        return resultList
    }

    @JvmStatic fun findRootVariant(variant: ShipVariantAPI): ShipVariantAPI {
        var current = variant
        val seen = HashSet<ShipVariantAPI>()
        while (variantToParent.containsKey(current)) {
            val parent = variantToParent[current]
            if (parent != null && seen.add(current)) {
                current = parent
            } else {
                break
            }
        }
        return current
    }

    @JvmStatic fun refreshAllCaches() {
        log("--> refreshAllCaches()", logger, Level.INFO)
        for (fleet in CampaignEventListener.activeFleets) {
            refreshCache(fleet)
        }
        log("<-- refreshAllCaches()", logger, Level.INFO)
    }

    @JvmStatic fun reinitialize() {
        // If Global.getSector() is non-null, call refreshAllCaches(), otherwise log error and bail out
        variantToParent.clear()
        if (Global.getSector() != null) {
            refreshAllCaches()
        } else {
            logError("Global.getSector() was null! Bailing out without calling refreshAllCaches()...")
        }
    }

    private fun refreshCache(fleet: CampaignFleetAPI) {
        log("--> refreshCache()\tfleet: ${fleet}", logger, Level.INFO)
        for (m in fleet.fleetData.membersListCopy) {
            val v = m.checkRefitVariant()
            mapVariantTree(v)
        }
        log("<-- refreshCache()\tfleet: ${fleet}", logger, Level.INFO)
    }

    private fun mapVariantTree(parent: ShipVariantAPI) {
        for (slotId in parent.stationModules.keys) {
            val child = parent.getModuleVariant(slotId)
            if (child != null) {
                variantToParent[child] = parent
                mapVariantTree(child)
            }
        }
    }

    private fun getFleetContext(member: FleetMemberAPI?): CampaignFleetAPI? {
        var result: CampaignFleetAPI? = null
        val targetV = member?.checkRefitVariant()

        if (targetV != null) {
            for (activeFleet in CampaignEventListener.activeFleets) {
                val members = activeFleet.fleetData.membersListCopy
                var foundInFleet = false
                for (m in members) {
                    val rootV = m.checkRefitVariant()
                    if (rootV === targetV || isOwnerOf(rootV, targetV)) {
                        foundInFleet = true
                        break
                    }
                }
                if (foundInFleet) {
                    result = activeFleet
                    break
                }
            }
        }

        if (result == null && member != null) {
            logError("Fleet context missing for member: ${member.shipName} [${member.hullId}]")
        }
        return result
    }

    private fun isOwnerOf(parent: ShipVariantAPI?, child: ShipVariantAPI): Boolean {
        var found = false
        if (parent != null) {
            for (slotId in parent.stationModules.keys) {
                val moduleV = parent.getModuleVariant(slotId)
                if (moduleV != null) {
                    if (moduleV === child || isOwnerOf(moduleV, child)) {
                        found = true
                        break
                    }
                }
            }
        }
        return found
    }

    private fun collectModuleMembers(parent: ShipVariantAPI, fleet: CampaignFleetAPI, list: MutableList<FleetMemberAPI>) {
        val members = fleet.fleetData.membersListCopy
        for (slotId in parent.stationModules.keys) {
            val childV = parent.getModuleVariant(slotId)
            if (childV != null) {
                val moduleMember = members.find { it.checkRefitVariant() === childV }
                if (moduleMember != null) {
                    list.add(moduleMember)
                    collectModuleMembers(childV, fleet, list)
                }
            }
        }
    }

    private fun logError(message: String) {
        log(message, logger, Level.ERROR)
    }
}

