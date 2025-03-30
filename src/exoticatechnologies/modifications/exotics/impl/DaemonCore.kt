package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.Misc
import exoticatechnologies.hullmods.DaemonCoreHM
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.Utilities
import org.json.JSONObject
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class DaemonCore(key: String, settingsObj: JSONObject) :
    HullmodExotic(key, settingsObj, "et_daemoncore", "DaemonCore", Color(150, 20, 20)) {

    override fun getSalvageChance(chanceMult: Float): Float {
        return 0f
    }

    override fun shouldShow(member: FleetMemberAPI, mods: ShipModifications, market: MarketAPI?): Boolean {
        return (canAfford(Global.getSector().playerFleet, market)
                || Utilities.hasExoticChip(
            Global.getSector().playerFleet.cargo,
            key
        ) || Misc.getStorageCargo(market) != null && Utilities.hasExoticChip(Misc.getStorageCargo(market), key))
    }

    override fun canAfford(fleet: CampaignFleetAPI, market: MarketAPI?): Boolean {
        return Utilities.hasItem(
            fleet.cargo,
            ITEM
        ) || Misc.getStorageCargo(market) != null && Utilities.hasItem(Misc.getStorageCargo(market), ITEM)
    }

    override fun removeItemsFromFleet(fleet: CampaignFleetAPI, member: FleetMemberAPI, market: MarketAPI?): Boolean {
        if (Utilities.hasItem(fleet.cargo, ITEM)) {
            Utilities.takeItemQuantity(fleet.cargo, ITEM, 1f)
        } else {
            Utilities.takeItemQuantity(Misc.getStorageCargo(market), ITEM, 1f)
        }
        return true
    }

    override fun modifyToolTip(
        tooltip: TooltipMakerAPI,
        title: UIComponentAPI,
        member: FleetMemberAPI,
        mods: ShipModifications,
        exoticData: ExoticData,
        expand: Boolean
    ) {
        if (expand) {
            StringUtils
                    .getTranslation(key, "longDescription")
                    .format("bandwidthIncrease", BANDWIDTH_INCREASE * getPositiveMult(member, mods, exoticData))
                    .format("smallReduction", DaemonCoreHM.WEAPON_REDUCTIONS[WeaponAPI.WeaponSize.SMALL])
                    .format("medReduction", DaemonCoreHM.WEAPON_REDUCTIONS[WeaponAPI.WeaponSize.MEDIUM])
                    .format("largeReduction", DaemonCoreHM.WEAPON_REDUCTIONS[WeaponAPI.WeaponSize.LARGE])
                    .format("fghtrReduction", DaemonCoreHM.FIGHTER_REDUCTION)
                    .format("bmberReduction", DaemonCoreHM.BOMBER_REDUCTION)
                    .format("doubleEdge", DOUBLE_EDGE * exoticData.type.getPositiveMult(member, mods))
                    .addToTooltip(tooltip, title)
        }
    }

    override fun getResourceCostMap(
        fm: FleetMemberAPI,
        mods: ShipModifications,
        market: MarketAPI?
    ): MutableMap<String, Float> {
        val resourceCosts: MutableMap<String, Float> = HashMap()
        resourceCosts[ITEM] = 1f
        return resourceCosts
    }

    override fun applyExoticToStats(
        id: String,
        stats: MutableShipStatsAPI,
        member: FleetMemberAPI,
        mods: ShipModifications,
        exoticData: ExoticData
    ) {
        onInstall(member)
    }

    override fun applyToShip(
        id: String,
        member: FleetMemberAPI,
        ship: ShipAPI,
        mods: ShipModifications,
        exoticData: ExoticData
    ) {
        if (!ship.hasListenerOfClass(DaemonCoreDamageTakenListener::class.java)) {
            ship.addListener(DaemonCoreDamageTakenListener(member, mods, exoticData))
        }
        if (!ship.hasListenerOfClass(DaemonCoreDamageDealtListener::class.java)) {
            ship.addListener(DaemonCoreDamageDealtListener(member, mods, exoticData))
        }
    }

    /**
     * extra bandwidth added directly to ship.
     *
     * @param member
     * @param mods
     * @param exoticData
     * @return
     */
    override fun getExtraBandwidth(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData?): Float {
        return 60f * (exoticData?.type?.getPositiveMult(member, mods) ?: 1f)
    }

    override fun shouldShareEffectToOtherModules(ship: ShipAPI?, module: ShipAPI?) = false

    override fun shouldAffectModulesToShareEffectsToOtherModules() = false

    override fun shouldAffectModule(moduleStats: MutableShipStatsAPI) = false

    override fun shouldAffectModule(ship: ShipAPI?, module: ShipAPI?) = false

    inner class DaemonCoreDamageTakenListener(val member: FleetMemberAPI, val mods: ShipModifications, val exoticData: ExoticData) : DamageTakenModifier {
        override fun modifyDamageTaken(
            param: Any?,
            target: CombatEntityAPI,
            damage: DamageAPI,
            point: Vector2f,
            shieldHit: Boolean
        ): String {
            damage.modifier.modifyMult(buffId, 1 + (DOUBLE_EDGE / 100f) * exoticData.type.getPositiveMult(member, mods))
            return buffId
        }
    }

    inner class DaemonCoreDamageDealtListener(val member: FleetMemberAPI, val mods: ShipModifications, val exoticData: ExoticData) : DamageDealtModifier {
        override fun modifyDamageDealt(
            param: Any?,
            target: CombatEntityAPI,
            damage: DamageAPI,
            point: Vector2f,
            shieldHit: Boolean
        ): String {
            damage.modifier.modifyMult(buffId, 1 + (DOUBLE_EDGE / 100f) * exoticData.type.getPositiveMult(member, mods))
            return buffId
        }
    }

    companion object {
        private const val ITEM = "tahlan_archdaemoncore"
        private const val BANDWIDTH_INCREASE = 60
        private const val DOUBLE_EDGE = 20f

        val BLOCKED_HULLMODS: MutableSet<String> = HashSet<String>().apply {
            add("specialsphmod_alpha_core_upgrades")
            add("specialsphmod_beta_core_upgrades")
            add("specialsphmod_gamma_core_upgrades")
//            add("et_alphasubcore")
        }
    }
}
