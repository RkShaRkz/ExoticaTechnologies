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
import exoticatechnologies.modifications.exotics.impl.AlphaSubcore
import exoticatechnologies.modifications.exotics.impl.DaemonCore
import exoticatechnologies.util.*

/**
 * Exotic Hullmod used by both [AlphaSubcore] and [DaemonCore]
 */
class AlphaSubcoreHM : BaseHullMod() {

    override fun applyEffectsBeforeShipCreation(hullSize: HullSize, stats: MutableShipStatsAPI, id: String) {
        AnonymousLogger.log("--> applyEffectsBeforeShipCreation()\thullSize: ${hullSize}, stats: ${stats}, id: ${id}\tstats.fleetmember: ${stats.fleetMember}\tstats.variant: ${stats.variant}, stats.variant.displayName: ${stats.variant.displayName}\tstats.variant.hullVariantId: ${stats.variant.hullVariantId}", "AlphaSubcoreHM")
        AnonymousLogger.log("applyEffectsBeforeShipCreation()\tstats.variant.hullSpec.hullId: ${stats.variant.hullSpec.hullId}", "AlphaSubcoreHM")
        AnonymousLogger.log("applyEffectsBeforeShipCreation(),\tstats.fleetMember.variant.hullSpec.hullId: ${stats.fleetMember?.variant?.hullSpec?.hullId ?: "null"},\tstats.fleetMember.variant.hullSpec.baseHullId: ${stats.fleetMember?.variant?.hullSpec?.baseHullId ?: "null"}", "AlphaSubcoreHM")
        if(stats.variant.hullMods.any { AlphaSubcore.BLOCKED_HULLMODS.contains(it) }) {
            return
        }

        AnonymousLogger.log("applyEffectsBeforeShipCreation()\tadding stats: ${stats} under fleetmember: ${stats.fleetMember} to new map", "AlphaSubcoreHM")
        stats.fleetMember?.let {
            AnonymousLogger.log("applyEffectsBeforeShipCreation()\tfleetmember hull ID: ${stats.fleetMember.hullId}", "AlphaSubcoreHM")
            addValueToStatsMap(it, stats)
        }
        val listenerAdded = stats.hasListener(listener) // If there is no such key, we surely didn't add it
        AnonymousLogger.log("applyEffectsBeforeShipCreation()\tstats: ${stats}\thasListener: ${listenerAdded}", "AlphaSubcoreHM")
        if (listenerAdded.not()) {
            AnonymousLogger.log("applyEffectsBeforeShipCreation()\tadding listener to stats: ${stats}", "AlphaSubcoreHM")
            stats.addListener(listener)
        }

        val containsVariant = AlphaSubcore.doesMapContainVariant(stats.variant)
        val containsFleetMember = AlphaSubcore.doesMapContainFleetMemberAPI(stats.fleetMember)
        val containsHullSpec = AlphaSubcore.doesMapContainVariantHullSpec(stats.variant)
        val containsHullSpecHullId = AlphaSubcore.doesMapContainVariantHullId(stats.variant)
        if (containsVariant || containsFleetMember || containsHullSpec || containsHullSpecHullId) {
            // Get the fleetmember (key) to the matching value
            val fleetMember = AlphaSubcore.getMapKeyMatchingSearchCriteria(stats.variant)
            AnonymousLogger.log("applyEffectsBeforeShipCreation()\tfound fleetMember: ${fleetMember} for stats.variant: ${stats.variant}\t\tfleetMember.isMultiModuleShip() ? ${fleetMember.isMultiModuleShip()}", "AlphaSubcoreHM")
            // Compare a few things to ensure that this *is* the one we've been searching for
            if (stats.variant.displayName == fleetMember.variant.displayName) AnonymousLogger.log("ERROR: stats.variant.displayName != fleetMember.variant.displayName", "AlphaSubcoreHM")
            if (fleetMember.isMultiModuleShip()) {
                // Add to the remapping map if multimodule ship
                AlphaSubcore.addStatsToMultimoduleStatsMap(fleetMember, stats)
                // Dumb, but redo for all keys too
                val fmList = AlphaSubcore.getAllMapKeysMatchingSearchCriteria(stats.variant)
                for (fm in fmList) {
                    AlphaSubcore.addStatsToMultimoduleStatsMap(fm, stats)
                }
            }
        }


//        AnonymousLogger.log("<-- applyEffectsBeforeShipCreation()\tlistenerAddedMap: ${listenerAddedMap}", "AlphaSubcoreHM")
        AnonymousLogger.log("applyEffectsBeforeShipCreation()\t\tAlphaSubcore map contains stats.variant ? ${AlphaSubcore.doesMapContainVariant(stats.variant)}\t\tAlphaSubcore map contains stats.fleetmember ? ${AlphaSubcore.doesMapContainFleetMemberAPI(stats.fleetMember)}", "AlphaSubcoreHM")
        AnonymousLogger.log("applyEffectsBeforeShipCreation()\t\tAlphaSubcore map contains stats.variant.hullSpec ? ${AlphaSubcore.doesMapContainVariantHullSpec(stats.variant)}\t\tAlphaSubcore map contains stats.variant.hullID ? ${AlphaSubcore.doesMapContainVariantHullId(stats.variant)}", "AlphaSubcoreHM")
        AnonymousLogger.log("<-- applyEffectsBeforeShipCreation()", "AlphaSubcoreHM")
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

        val listener = OPCostListener()
        val fleetMemberStatsMap = hashMapOf<FleetMemberAPI, List<MutableShipStatsAPI>>()

        fun addValueToStatsMap(member: FleetMemberAPI, stats: MutableShipStatsAPI) {
            val oldList: List<MutableShipStatsAPI> = fleetMemberStatsMap[member] ?: listOf()
            val newList = oldList.toMutableList()
            newList.add(stats)
            fleetMemberStatsMap[member] = newList.toList()
        }

        @JvmStatic
        fun removeListenerFrom(member: FleetMemberAPI) {
            AnonymousLogger.log("--> removeListenerFrom()\tmember: ${member}\tmember.hullID: ${member.hullId}", "AlphaSubcoreHM")
            val memberStats = member.stats
            val memberVariant = member.variant
            AnonymousLogger.log("removeListenerFrom()\tmemberStats: ${memberStats}\tmemberVariant: ${memberVariant}\tmemberVariant.hullVariantId: ${memberVariant.hullVariantId}", "AlphaSubcoreHM")
            AnonymousLogger.log("removeListenerFrom()\tmember.isMultiModuleShip() ? ${member.isMultiModuleShip()}", "AlphaSubcoreHM")

            val isMemberMultimoduleShip = member.isMultiModuleShip()

            // If member is a multimodule ship, use the new approach
            // otherwise, just remove the listener from it's stats and call it a day

            if (isMemberMultimoduleShip.not()) {
                val hadListener = removeListenerFromStats(memberStats)
                AnonymousLogger.log("removeListenerFrom()\t\tNOT multimodule ship case\tlistenerAdded: ${hadListener}", "AlphaSubcoreHM")
            } else {
                AnonymousLogger.log("removeListenerFrom()\t\tmultimodule ship case", "AlphaSubcoreHM")
                // Use the maps instead of this old garbage
                val statsList = AlphaSubcore.getStatsFromMultimoduleStatsMap(member)
                AnonymousLogger.log("removeListenerFrom()\t\tstatsList: ${statsList}", "AlphaSubcoreHM")
                // Start off from 'true' to keep the chain going
                var removedAllOfThem = true
                for (stats in statsList) {
                    removedAllOfThem = removedAllOfThem && removeListenerFromStats(stats)
                }
                AnonymousLogger.log("removeListenerFrom()\t\tmultimodule ship case\t\tremoved all listeners: ${removedAllOfThem}", "AlphaSubcoreHM")
                // Do jank now, get all keys similar to the member, then wipe all of their shit from their stats
                val keysList = AlphaSubcore.getAllStatsMapKeysMatchingSearchedKey(member)
                // Start off from 'true' to keep the chain going
                var removedAllKeysListeners = true
                for (key in keysList) {
                    val keyStatsList = AlphaSubcore.getStatsFromMultimoduleStatsMap(member)
                    AnonymousLogger.log("removeListenerFrom()\t\tkeyStatsList: ${keyStatsList}\tkeyStatsList.isEmpty ? ${keyStatsList.isEmpty()}", "AlphaSubcoreHM")

                    for (stats in statsList) {
                        removedAllKeysListeners = removedAllKeysListeners && removeListenerFromStats(stats)
                    }
                }
                AnonymousLogger.log("removeListenerFrom()\t\tremovedAllKeysListeners: ${removedAllKeysListeners}", "AlphaSubcoreHM")
            }
            AnonymousLogger.log("<-- removeListenerFrom()", "AlphaSubcoreHM")
        }

        fun removeListenerFromStats(stats: MutableShipStatsAPI): Boolean {
            val listenerAdded = stats.hasListener(listener)

            if (listenerAdded) {
                stats.removeListener(listener)
                //TODO revert back to just once but...
                stats.removeListener(listener)
                stats.removeListener(listener)
                stats.removeListener(listener)
                stats.removeListener(listener)
            }

            return listenerAdded
        }
    }
}