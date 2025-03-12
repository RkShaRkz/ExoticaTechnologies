package exoticatechnologies.hullmods

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize
import com.fs.starfarer.api.combat.listeners.FighterOPCostModifier
import com.fs.starfarer.api.combat.listeners.WeaponOPCostModifier
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.loading.WeaponSpecAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import exoticatechnologies.hullmods.exotics.ExoticHullmod
import exoticatechnologies.hullmods.exotics.ExoticHullmodLookup
import exoticatechnologies.modifications.exotics.impl.AlphaSubcore
import exoticatechnologies.modifications.exotics.impl.DaemonCore
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.exhaustive

/**
 * Exotic Hullmod used by both [AlphaSubcore] and [DaemonCore]
 */
class AlphaSubcoreHM : ExoticHullmod() {
    val listener = OPCostListener()

    override val hullModId: String = "et_alphasubcore"

    init {
        ExoticHullmodLookup.addToLookupMap(this)
    }

    private fun hasOPCostListener(stats: MutableShipStatsAPI): Boolean {
        return stats.hasListenerOfClass(OPCostListener::class.java)
    }

    override fun applyEffectsBeforeShipCreation(hullSize: HullSize, stats: MutableShipStatsAPI, id: String) {
        if(stats.variant.hullMods.any { AlphaSubcore.BLOCKED_HULLMODS.contains(it) }) {
            return
        }

        val listenerAdded = hasOPCostListener(stats)
        if (listenerAdded.not()) {
            stats.addListener(listener)
        }
    }

    override fun removeEffectsBeforeShipCreation(hullSize: HullSize, stats: MutableShipStatsAPI, id: String) {
        stats.removeListenerOfClass(OPCostListener::class.java)
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
