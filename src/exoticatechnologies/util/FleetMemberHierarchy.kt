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

    /**
     * Maps a child module variant's [ShipVariantAPI.hullVariantId] to its parent variant's
     * [ShipVariantAPI.hullVariantId]. Populated alongside [variantToParent] during [mapVariantTree].
     *
     * Unlike [variantToParent] (which uses identity-based [WeakHashMap] keys), this map uses
     * stable [String] keys, making it safe to query from [MutableShipStatsAPI]-based contexts
     * where the concrete [ShipVariantAPI] instance may differ from the one cached in [variantToParent].
     */
    private val variantIdToParentId = HashMap<String, String>()

    @JvmStatic fun isRootModule(stats: MutableShipStatsAPI?): Boolean = isRootModule(stats?.fleetMember)
    @JvmStatic fun isRootModule(member: FleetMemberAPI?): Boolean {
        var result = false
        val currentV = member?.checkRefitVariant()
        val fleet = getFleetContext(member)

        if (currentV != null && fleet != null) {
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
        if (variant != null) {
            if (!variantToParent.containsKey(variant)) {
                getFleetContext(member)
            }
            result = variantToParent.containsKey(variant)
        }
        return result
    }

    @JvmStatic fun getRootModule(stats: MutableShipStatsAPI?): FleetMemberAPI? = getRootModule(stats?.fleetMember)
    @JvmStatic fun getRootModule(member: FleetMemberAPI?): FleetMemberAPI? {
        var result = member
        val currentV = member?.checkRefitVariant()
        val fleet = getFleetContext(member)

        if (member != null && fleet != null && currentV != null) {
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
            // Root is now at the end
            modules.removeAt(modules.size - 1)
        }
        return modules.toList()
    }

    @JvmStatic fun getAllModulesStatsFromSingleStats(stats: MutableShipStatsAPI?): List<MutableShipStatsAPI> {
        return getAllModules(stats).map { it.stats }
    }

    @JvmStatic fun getAllModules(stats: MutableShipStatsAPI?): List<FleetMemberAPI> = getAllModules(
        if (stats != null) FleetMemberUtils.findMemberForStats(stats) else null
    )
    @JvmStatic fun getAllModules(member: FleetMemberAPI?): List<FleetMemberAPI> {
        val resultList = mutableListOf<FleetMemberAPI>()
        val currentV = member?.checkRefitVariant()
        val fleet = getFleetContext(member)

        if (member != null && fleet != null && currentV != null) {
            val rootV = findRootVariant(currentV)
            val rootMember = fleet.fleetData.membersListCopy.find { it.checkRefitVariant() === rootV }

            if (rootMember != null) {
                collectModuleMembers(rootV, fleet, resultList)
                resultList.add(rootMember) // Root module is ALWAYS last
            }
        } else if (member != null) {
            resultList.add(member)
        }
        return resultList.toList()
    }

    @JvmStatic fun isChildStats(stats: MutableShipStatsAPI?): Boolean {
        if (stats?.variant == null) return false
        return variantIdToParentId.containsKey(stats.variant.hullVariantId)
    }

    @JvmStatic fun getCacheSize(): Int = variantIdToParentId.size

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

    @JvmStatic fun findRootVariantId(childVariantId: String): String? {
        var current = childVariantId
        val seen = HashSet<String>()
        while (variantIdToParentId.containsKey(current)) {
            val parent = variantIdToParentId[current]
            if (parent != null && seen.add(current)) {
                current = parent
            } else {
                break
            }
        }
        return if (current == childVariantId) null else current
    }

    @JvmStatic fun refreshAllCaches() {
        logInfo("--> refreshAllCaches()")
        for (fleet in CampaignEventListener.activeFleets) {
            refreshCache(fleet)
        }
        logInfo("<-- refreshAllCaches()")
    }

    @JvmStatic fun reinitialize() {
        // If Global.getSector() is non-null, call refreshAllCaches(), otherwise log error and bail out
        variantToParent.clear()
        variantIdToParentId.clear()
        if (Global.getSector() != null) {
            refreshAllCaches()
        } else {
            logError("Global.getSector() was null! Bailing out without calling refreshAllCaches() ...")
        }
    }

    private fun refreshCache(fleet: CampaignFleetAPI?) {
        logInfo("--> refreshCache()\tfleet: ${fleet}")
        return if (fleet == null) {
            // If fleet was null, just do nothing
        } else {
            // Otherwise remap everything
            for (m in fleet.fleetData.membersListCopy) {
                mapVariantTree(m.checkRefitVariant())
            }
        }
        logInfo("<-- refreshCache()\tfleet: ${fleet}")
    }

    private fun mapVariantTree(parent: ShipVariantAPI) {
        for (slotId in parent.stationModules.keys) {
            val child = parent.getModuleVariant(slotId)
            if (child != null) {
                variantToParent[child] = parent
                variantIdToParentId[child.hullVariantId] = parent.hullVariantId
                mapVariantTree(child)
            }
        }
    }

    private fun getFleetContext(member: FleetMemberAPI?): CampaignFleetAPI? {
        var result: CampaignFleetAPI? = null
        val targetV = member?.checkRefitVariant() ?: return null

        for (activeFleet in CampaignEventListener.activeFleets) {
            if (isVariantInFleet(activeFleet, targetV)) {
                result = activeFleet
                break
            }
        }

        if (result == null && Global.getSector()?.playerFleet != null) {
            if (isVariantInFleet(Global.getSector().playerFleet, targetV)) {
                result = Global.getSector().playerFleet
            }
        }

        if (result == null) {
            logError("Fleet context missing for member: ${member.shipName} [${member.hullId}]")
        }
        return result
    }

    private fun isVariantInFleet(fleet: CampaignFleetAPI, targetV: ShipVariantAPI): Boolean {
        for (m in fleet.fleetData.membersListCopy) {
            val rootV = m.checkRefitVariant()
            if (rootV === targetV || isOwnerOf(rootV, targetV)) return true
        }
        return false
    }

    private fun isOwnerOf(parent: ShipVariantAPI?, child: ShipVariantAPI): Boolean {
        if (parent == null) return false
        var found = false
        for (slotId in parent.stationModules.keys) {
            val moduleV = parent.getModuleVariant(slotId)
            if (moduleV != null) {
                if (!variantToParent.containsKey(moduleV)) {
                    variantToParent[moduleV] = parent
                }
                if (moduleV === child || isOwnerOf(moduleV, child)) {
                    found = true
                    break
                }
            }
        }
        return found
    }

    private fun collectModuleMembers(parent: ShipVariantAPI, fleet: CampaignFleetAPI, list: MutableList<FleetMemberAPI>) {
        for (slotId in parent.stationModules.keys) {
            val childV = parent.getModuleVariant(slotId)
            if (childV != null) {
                val moduleMember = fleet.fleetData.membersListCopy.find { it.checkRefitVariant() === childV }
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

    private fun logInfo(message: String) {
        log(message, logger, Level.INFO)
    }
}
