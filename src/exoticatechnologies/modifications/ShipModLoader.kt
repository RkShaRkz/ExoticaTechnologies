package exoticatechnologies.modifications

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial
import exoticatechnologies.util.FleetMemberHierarchy
import exoticatechnologies.util.FleetMemberUtils
import exoticatechnologies.util.FleetMemberUtils.findMemberFromShip
import exoticatechnologies.util.combineIntoList
import org.apache.log4j.Logger

class ShipModLoader {
    private val log = Logger.getLogger(ShipModLoader::class.java)

    private var providers: List<Provider> = mutableListOf(
        VariantTagProvider.getInstance(),
        ZigguratDataProvider.inst,
        PersistentDataProvider.inst
    )

    private val LOG_TAG = "ShipModLoader"

    private fun diagnosticLog(message: String) {
        log.info("[DIAG] $message")
    }

    private fun getData(member: FleetMemberAPI, variant: ShipVariantAPI = member.variant): ShipModifications? {
        val result = providers.firstNotNullOfOrNull { it.get(member, variant) }
        diagnosticLog(
            "getData | member=${member.id} variant=${variant.hullVariantId} " +
            "variantTags=${variant.tags.size} result=${if (result == null) "null" else "mods(UPGRADES: ${result.getUpgradeMap()}, EXOTICS: ${result.getExoticSet()})"}"
        )
        return result
    }

    private fun saveData(member: FleetMemberAPI, variant: ShipVariantAPI, mods: ShipModifications) {
        for (i in providers.indices) {
            val provider = providers[i]
            if (!provider.setOnlyIfFirst() || i == 0) {
                provider.set(member, variant, mods)
            }
        }
    }

    private fun removeData(member: FleetMemberAPI, variant: ShipVariantAPI) {
        providers.forEach { it.remove(member, variant) }
    }

    private fun getAllShipSections(ship: ShipAPI): List<ShipAPI> {
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

    private fun getAllDataForShipAPI(ship: ShipAPI): List<ShipModifications> {
        // First, grab all ship's sections
        val allShipSections = getAllShipSections(ship)
        // Now, map them onto variants
        val allShipSectionVariants = allShipSections.map { it.variant }
        val allShipSectionsFMAPIs = allShipSections.map { findMemberFromShip(it) }

        val allModsList = mutableListOf<ShipModifications>()
        for (index in allShipSectionVariants.indices) {
            val variant = allShipSectionVariants[index]
            val fmapi = allShipSectionsFMAPIs[index]
            fmapi?.let {member ->
                val mods = getData(member, variant)
                mods?.let { shipMods ->
                    allModsList.add(shipMods)
                }
            }
        }
        // Now we have all mods, so return them.
        return allModsList.toList()
    }

    private fun getAllDataFromStatsAPI(stats: MutableShipStatsAPI): List<ShipModifications> {
        val allModsList = mutableSetOf<ShipModifications>()

        // FleetMemberHierarchy cannot connect child-to-child, so it may collapse
        // all module variants to rootFM. Keep this loop as a fallback.
        val allShipModulesFromStats = FleetMemberHierarchy.getAllModules(stats)
        diagnosticLog("getAllDataFromStatsAPI | allShipModulesFromStats.size: ${allShipModulesFromStats.size} | allShipModulesFromStats hullVariantIds: ${allShipModulesFromStats.map { it.variant.hullVariantId }}")
        for (someModule in allShipModulesFromStats) {
            val variantId = someModule.variant.hullVariantId
            diagnosticLog("getAllDataFromStatsAPI | FM loop | member=${someModule.id} variantId=$variantId")
            val moduleMods = get(someModule, someModule.variant)
            moduleMods?.let {
                allModsList.add(it)
            }
        }

        // Walk the variant tree to catch all module variants — FM hierarchy
        // collapses child modules to rootFM, so sibling modules are missed.
        val rootFM = FleetMemberUtils.findMemberForStats(stats)
        if (rootFM != null) {
            collectModuleMods(rootFM, rootFM.variant, allModsList)
        }

        return allModsList.toList()
    }

    private fun collectModuleMods(member: FleetMemberAPI, variant: ShipVariantAPI, result: MutableSet<ShipModifications>) {
        for (slotId in variant.stationModules.keys) {
            val childV = variant.getModuleVariant(slotId) ?: continue
            val variantId = childV.hullVariantId
            diagnosticLog("getAllDataFromStatsAPI | variant tree | slot=$slotId variantId=$variantId")
            val childMods = get(member, childV)
            childMods?.let {
                result.add(it)
            }
            collectModuleMods(member, childV, result)
        }
    }

    companion object {
        private val inst = ShipModLoader()

        @JvmStatic
        @Synchronized
        fun get(member: FleetMemberAPI, variant: ShipVariantAPI): ShipModifications? {
            return inst.getData(member, variant)
        }

        @JvmStatic
        @Synchronized
        fun set(member: FleetMemberAPI, variant: ShipVariantAPI, mods: ShipModifications) {
            return inst.saveData(member, variant, mods)
        }

        @JvmStatic
        @Synchronized
        fun remove(member: FleetMemberAPI, variant: ShipVariantAPI) {
            return inst.removeData(member, variant)
        }

        @JvmStatic
        @Synchronized
        fun getForSpecialData(shipData: ShipRecoverySpecial.PerShipData): ShipModifications? {
            if (shipData.getVariant() != null) {
                val mods = VariantTagProvider.getInstance().getFromVariant(shipData.getVariant())
                if (mods != null) {
                    return mods
                }
            }

            if (shipData.fleetMemberId != null) {
                val mods = PersistentDataProvider.inst.getFromId(shipData.fleetMemberId)
                if (mods != null) {
                    return mods
                }
            }

            return null
        }

        @JvmStatic
        @Synchronized
        fun getFromVariant(variant: ShipVariantAPI): ShipModifications? {
            return VariantTagProvider.getInstance().getFromVariant(variant)
        }

        @JvmStatic
        @Synchronized
        fun getAllForShipAPI(ship: ShipAPI): List<ShipModifications> {
            return inst.getAllDataForShipAPI(ship)
        }

        @JvmStatic
        @Synchronized
        fun getAllForStats(stats: MutableShipStatsAPI): List<ShipModifications> {
            return inst.getAllDataFromStatsAPI(stats).distinct()
        }
    }

    interface Provider {
        fun get(member: FleetMemberAPI, variant: ShipVariantAPI): ShipModifications?
        fun set(member: FleetMemberAPI, variant: ShipVariantAPI, mods: ShipModifications)
        fun remove(member: FleetMemberAPI, variant: ShipVariantAPI)

        fun setOnlyIfFirst(): Boolean {
            return true
        }
    }
}
