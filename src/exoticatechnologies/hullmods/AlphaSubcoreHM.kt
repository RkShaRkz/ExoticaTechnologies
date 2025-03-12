package exoticatechnologies.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize
import com.fs.starfarer.api.combat.listeners.FighterOPCostModifier
import com.fs.starfarer.api.combat.listeners.WeaponOPCostModifier
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.loading.WeaponSpecAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import exoticatechnologies.hullmods.exotics.ExoticHullmod
import exoticatechnologies.hullmods.exotics.ExoticHullmodLookup
import exoticatechnologies.hullmods.exotics.HullmodExoticHandler
import exoticatechnologies.modifications.exotics.impl.AlphaSubcore
import exoticatechnologies.modifications.exotics.impl.DaemonCore
import exoticatechnologies.util.AnonymousLogger
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.exhaustive
import exoticatechnologies.util.getAllShipSections

/**
 * Exotic Hullmod used by both [AlphaSubcore] and [DaemonCore]
 */
class AlphaSubcoreHM : ExoticHullmod() {
    val listener = OPCostListener()
    var listenerAddedMap = hashMapOf<MutableShipStatsAPI, Boolean>()

    override val hullModId: String = "et_alphasubcore"

    init {
        ExoticHullmodLookup.addToLookupMap(this)
    }

//    override fun removeEffectsAfterShipCreation(ship: ShipAPI, id: String) {
        //TODO
//    }

    override fun applyEffectsBeforeShipCreation(hullSize: HullSize, stats: MutableShipStatsAPI, id: String) {
        AnonymousLogger.log("--> applyEffectsBeforeShipCreation()\thullSize: ${hullSize}, stats: ${stats}, id: ${id}", "AlphaSubcoreHM")
        if(stats.variant.hullMods.any { AlphaSubcore.BLOCKED_HULLMODS.contains(it) }) {
            return
        }

        val listenerAdded = listenerAddedMap[stats] ?: false // If there is no such key, we surely didn't add it
        AnonymousLogger.log("applyEffectsBeforeShipCreation()\tstats: ${stats}\tlistenerAdded: ${listenerAdded}", "AlphaSubcoreHM")
        if (listenerAdded.not()) {
            stats.addListener(listener)
            listenerAddedMap[stats] = true
            // Ok, so it seems that 'entity' is either null or points to an actual module
            val entity = stats.entity
            val fleetMember = stats.fleetMember
            val typeOfEntity: Class<Any>? = entity?.javaClass
            AnonymousLogger.log("applyEffectsBeforeShipCreation()\tstats.entity: ${entity}\tentity type: ${typeOfEntity}\tfleetMember: ${fleetMember}", "AlphaSubcoreHM")
            //TODO special case
            if (entity is ShipAPI) {
                entity?.let {
                    val fm: FleetMemberAPI? = it.fleetMember
                    AnonymousLogger.log("applyEffectsBeforeShipCreation()\tentity is ShipAPI !!! Id: ${it.id}", "AlphaSubcoreHM")
                    AnonymousLogger.log("applyEffectsBeforeShipCreation()\tfleetMember: ${fm}, fm.hullId: ${fm?.hullId}", "AlphaSubcoreHM")
                }
            }
        } else {
            stats.removeListener(listener)
            listenerAddedMap[stats] = false
        }

        AnonymousLogger.log("listenerAddedMap: ${listenerAddedMap}", "AlphaSubcoreHM")
    }

    override fun removeEffectsBeforeShipCreation(hullSize: HullSize, stats: MutableShipStatsAPI, id: String) {
        AnonymousLogger.log("--> removeEffectsBeforeShipCreation()\thullSize: ${hullSize}, stats: ${stats}, id: ${id}", "AlphaSubcoreHM")
        val fleetMember = stats.fleetMember
        AnonymousLogger.log("removeEffectsBeforeShipCreation()\tfleetMember: ${fleetMember}", "AlphaSubcoreHM")
//        val listenerAdded = listenerAddedMap[stats]
//        AnonymousLogger.log("removeEffectsBeforeShipCreation()\tlistenerAdded: ${listenerAdded}", "AlphaSubcoreHM")
        AnonymousLogger.log("removeEffectsBeforeShipCreation()\tlistenerAdded: ${listenerAddedMap[stats]}", "AlphaSubcoreHM")
        stats.removeListenerOfClass(OPCostListener::class.java)
        if (stats is ShipAPI) {
            AnonymousLogger.log("removeEffectsBeforeShipCreation()\tstats is ShipAPI !!!", "AlphaSubcoreHM")
            val ship = stats as ShipAPI
            val entity = stats.entity
            AnonymousLogger.log("removeEffectsBeforeShipCreation()\tis stats.entity a ShipAPI ? ${entity is ShipAPI}", "AlphaSubcoreHM")
//            val modules = getAllShipSections(ship)    //TODO
            val modules = ship.childModulesCopy
            AnonymousLogger.log("removeEffectsBeforeShipCreation()\tchildModules size: ${modules.size}", "AlphaSubcoreHM")
            for (module in modules) {
//                removeEffectsBeforeShipCreation(module.hullSize, module, id)
//                removeEffectsBeforeShipCreation(module.hullSize, module.mutableStats, id)    //TODO
                removeEffectsBeforeShipCreation(module.hullSize, module as MutableShipStatsAPI, id)    //TODO
            }
            AnonymousLogger.log("removeEffectsBeforeShipCreation()\tdone with child modules, lets try the ExoticDataHandler ...", "AlphaSubcoreHM")
            // Since childModulesCopy doesnt quite seem to be working
            val allKeys = HullmodExoticHandler.grabAllKeysForParticularFleetMember(fleetMember)
            for (key in allKeys) {
                val exoticHandlerDataOptional = HullmodExoticHandler.getDataForKey(key)
                if (exoticHandlerDataOptional.isPresent()) {
                    val exoticHandlerData = exoticHandlerDataOptional.get()
                    for (variant in exoticHandlerData.listOfVariantsWeInstalledOn) {
                        val variantHullSize = variant.hullSpec.hullSize
                        removeEffectsBeforeShipCreation(variantHullSize, variant.statsForOpCosts, id)
                    }
                }
                //TODO keep track of the keys we 'removed' so we can actually remove them from the HullmodExoticHandler's lookupMap
            }

        }
        AnonymousLogger.log("<-- removeEffectsBeforeShipCreation()\tlistenerAdded: ${listenerAddedMap[stats]}\thasListener: ${stats.hasListenerOfClass(OPCostListener::class.java)}", "AlphaSubcoreHM")
    }

    override fun addPostDescriptionSection(
        tooltip: TooltipMakerAPI?,
        hullSize: HullSize?,
        ship: ShipAPI?,
        width: Float,
        isForModSpec: Boolean
    ) {
        if(ship?.variant?.hullMods?.any { AlphaSubcore.BLOCKED_HULLMODS.contains(it) } == true) {
            StringUtils
                    .getTranslation("AlphaSubcore", "conflictDetected")
                    .addToTooltip(tooltip)
        }

        super.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec)
    }

    override fun getDescriptionParam(index: Int, hullSize: HullSize): String? {
        return when(index) {
            0 -> COST_REDUCTION_SM.toString()
            1 -> COST_REDUCTION_MED.toString()
            2 -> COST_REDUCTION_LG.toString()
            3 -> COST_REDUCTION_FIGHTER.toString()
            4 -> COST_REDUCTION_BOMBER.toString()
            else -> null
        }.exhaustive
    }

    override fun affectsOPCosts(): Boolean {
        return true
    }

    class OPCostListener : WeaponOPCostModifier,FighterOPCostModifier {
        override fun getWeaponOPCost(stats: MutableShipStatsAPI, weapon: WeaponSpecAPI, currCost: Int): Int {
            return currCost - (WEAPON_REDUCTIONS[weapon.size] ?: 0)
        }

        override fun getFighterOPCost(stats: MutableShipStatsAPI, fighter: FighterWingSpecAPI, currCost: Int): Int {
            if (fighter.isBomber)
                return currCost - BOMBER_REDUCTION
            return currCost - FIGHTER_REDUCTION
        }
    }

    companion object {
        @JvmStatic
        val WEAPON_REDUCTIONS = mutableMapOf(
            WeaponSize.SMALL to 1,
            WeaponSize.MEDIUM to 2,
            WeaponSize.LARGE to 4
        )

        const val BOMBER_REDUCTION = 4
        const val FIGHTER_REDUCTION = 2

        const val COST_REDUCTION_LG = 4
        const val COST_REDUCTION_MED = 2
        const val COST_REDUCTION_SM = 1
        const val COST_REDUCTION_FIGHTER = 2
        const val COST_REDUCTION_BOMBER = 4
    }
}
