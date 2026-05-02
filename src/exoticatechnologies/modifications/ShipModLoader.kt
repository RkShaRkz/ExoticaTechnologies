package exoticatechnologies.modifications

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial
import exoticatechnologies.util.ShipStatsRegistry
import exoticatechnologies.util.FleetMemberUtils
import exoticatechnologies.util.FleetMemberUtils.findMemberFromShip
import exoticatechnologies.util.combineIntoList

class ShipModLoader {
    private var providers: List<Provider> = mutableListOf(
        VariantTagProvider.inst,
        ZigguratDataProvider.inst,
        PersistentDataProvider.inst
    )

    private fun getData(member: FleetMemberAPI, variant: ShipVariantAPI = member.variant): ShipModifications? {
        return providers.firstNotNullOfOrNull { it.get(member, variant) }
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
        // As well as to their FMAPIs
//        val allShipSectionsFMAPIs = allShipSections.map { findMemberFromShip(it) }
        val allShipSectionsFMAPIs = allShipSections.map { it.fleetMember }
        val rootFMAPI = findMemberFromShip(ship)

        // Now that we have all of this, we can build a list of ship mods, by grabbing
        // each index and calling getData(fmapi, variant)
        //TODO this is debug only, but will point out potential problems immediatelly
        // even though it could very well be that e.g. installing exotica on just one module will make it's size 1 versus the others being fuller
        assertTrue(allShipSections.size == allShipSectionVariants.size && allShipSectionVariants.size == allShipSectionsFMAPIs.size, "The sizes of three lists did not match")

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
        // First, grab all ships' stats
        //TODO get rid of ShipStatsRegistry in favor of FleetMemberHierarchy
        val allShipStats = ShipStatsRegistry.getWholeShipsStatsFromSingleStats(stats)
        val allShipFleetMembers = allShipStats.map { it.fleetMember }
        val rootModuleFleetMember = FleetMemberUtils.findMemberForStats(stats)

        val allModsList = mutableListOf<ShipModifications>()
        for (someStats in allShipStats) {
            val someStatsFM: FleetMemberAPI? = someStats.fleetMember
            // if some stats FMAPI is non-null, proceed
            someStatsFM?.let { statsFM ->
                val moduleMods = ShipModLoader.get(statsFM, someStats.getVariant())
                moduleMods?.let {
                    allModsList.add(it)
                }
            }
        }

        return allModsList.toList()
    }

    private fun assertTrue(value: Boolean, message: String) {
        return if (!value) {
            throw RuntimeException(message)
        } else {
//            value
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
                val mods = VariantTagProvider.inst.getFromVariant(shipData.getVariant())
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
            return VariantTagProvider.inst.getFromVariant(variant)
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
