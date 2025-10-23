package exoticatechnologies.hullmods.exotics

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import exoticatechnologies.modifications.exotics.impl.HullmodExotic

/**
 * Class that should be used exclusively by "exotic hullmods" used by [HullmodExotic]s.
 *
 * It's a slight extension on top of [BaseHullMod] that should let us cleanup after the hullmod has been removed.
 */
abstract class ExoticHullmod: BaseHullMod() {

    /**
     * This needs to match the ID from the hull_mods.csv
     */
    abstract val hullModId: String

    /**
     * You should undo all effects you've applied to the [stats] in [applyEffectsBeforeShipCreation]
     */
    abstract fun removeEffectsBeforeShipCreation(hullSize: HullSize, stats: MutableShipStatsAPI, id: String)
}
