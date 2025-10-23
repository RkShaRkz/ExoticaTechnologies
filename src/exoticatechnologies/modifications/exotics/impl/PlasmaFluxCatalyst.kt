package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.util.FleetMemberUtils
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.Utilities
import exoticatechnologies.util.exhaustive
import org.json.JSONObject
import java.awt.Color
import java.util.*
import kotlin.math.ceil

class PlasmaFluxCatalyst(key: String, settings: JSONObject) : Exotic(key, settings) {
    override var color = Color(0x00BBFF)

    override fun canAfford(fleet: CampaignFleetAPI, market: MarketAPI?): Boolean {
        return Utilities.hasItem(fleet.cargo, ITEM)
    }

    override fun removeItemsFromFleet(fleet: CampaignFleetAPI, member: FleetMemberAPI, market: MarketAPI?): Boolean {
        Utilities.takeItemQuantity(fleet.cargo, ITEM, 1f)
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
            val maxCapsWithBonus = getMaxCapsWithBonus(member, mods, exoticData)
            val maxVentsWithBonus = getMaxVentsWithBonus(member, mods, exoticData)
            val crPenaltyMalus = getCRPenaltyMalus(member, mods, exoticData)
            StringUtils.getTranslation(key, "longDescription")
                .format("effectLevel", getPositiveMult(member, mods, exoticData) * 100f)
                .format("capacitorLimit", maxCapsWithBonus)
                .format("ventLimit", maxVentsWithBonus)
                .format("crDecrease", crPenaltyMalus)
                .addToTooltip(tooltip, title)
        }
    }

    private fun getMaxThings(member: FleetMemberAPI, things: PlasmaFluxCatalystThings): Int {
        val maxFluxForHull = MAX_FLUX_EQUIPMENT[member.hullSpec.hullSize] ?: 0
        val maxFluxFloat = maxFluxForHull.toFloat()
        val maxThings = when (things) {
            PlasmaFluxCatalystThings.VENTS -> FleetMemberUtils.getFleetCommander(member)?.stats?.maxVentsBonus
            PlasmaFluxCatalystThings.CAPACITORS -> FleetMemberUtils.getFleetCommander(member)?.stats?.maxCapacitorsBonus
        }.exhaustive
        val maxThingsBonus = maxThings?.computeEffective(maxFluxFloat) ?: maxFluxFloat

        val retVal = maxThingsBonus.toInt()

        return retVal
    }

    private fun getMaxCaps(member: FleetMemberAPI): Int {
        return getMaxThings(member, PlasmaFluxCatalystThings.CAPACITORS)
    }

    private fun getMaxVents(member: FleetMemberAPI): Int {
        return getMaxThings(member, PlasmaFluxCatalystThings.VENTS)
    }

    fun getCoefficient(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return 3f * getNegativeMult(member, mods, exoticData)
    }

    /**
     * Returns the massaged value of ceil ( (maxCaps * positiveMult) / (coeff * negativeMult) )
     */
    fun getMaxCapsWithBonus(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        val maxCaps = getMaxCaps(member) * getPositiveMult(member, mods, exoticData)
        // coeff already has the negative mult
        val coeff = getCoefficient(member, mods, exoticData)
        val retVal = ceil(maxCaps / coeff)

        return retVal
    }

    /**
     * Returns the massaged value of ceil ( (maxVents * positiveMult) / (coeff * negativeMult) )
     */
    fun getMaxVentsWithBonus(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        val maxVents = getMaxVents(member) * getPositiveMult(member, mods, exoticData)
        // coeff already has the negative mult
        val coeff = getCoefficient(member, mods, exoticData)
        val retVal = ceil(maxVents / coeff)

        return retVal
    }

    fun getCRPenaltyMalus(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return 1f * getNegativeMult(member, mods, exoticData)
    }

    override fun applyExoticToStats(
        id: String,
        stats: MutableShipStatsAPI,
        member: FleetMemberAPI,
        mods: ShipModifications,
        exoticData: ExoticData
    ) {
        if (FleetMemberUtils.getFleetCommander(member) == null) {
            return
        }
        if (isNPC(member)) {
            return
        }

        val numCapsStats = stats.variant.numFluxCapacitors
        val numVentsStats = stats.variant.numFluxVents
        val maxCapsWithBonus = getMaxCapsWithBonus(member, mods, exoticData)
        val maxVentsWithBonus = getMaxVentsWithBonus(member, mods, exoticData)

        var crReduction = 0f
        if (numCapsStats > maxCapsWithBonus) {
            crReduction += (numCapsStats - maxCapsWithBonus).toInt()
        }
        if (numVentsStats > maxVentsWithBonus) {
            crReduction += (numVentsStats - maxVentsWithBonus).toInt()
        }
        if (crReduction > 0) {
            // After calculating base CR reduction (with 1x multiplier) lets multiply with actual CR penalty malus before applying
            crReduction = crReduction * getCRPenaltyMalus(member, mods, exoticData)
            stats.maxCombatReadiness.modifyFlat(name, -crReduction / 100f, name)
        }
    }

    override fun applyToShip(
        id: String,
        member: FleetMemberAPI,
        ship: ShipAPI,
        mods: ShipModifications,
        exoticData: ExoticData
    ) {
        val numCaps = ship.variant.numFluxCapacitors
        val numVents = ship.variant.numFluxVents
        ship.mutableStats.fluxCapacity.modifyFlat(buffId,numCaps * 200 * getPositiveMult(member, mods, exoticData))
        ship.mutableStats.fluxDissipation.modifyFlat(buffId, numVents * 10 * getPositiveMult(member, mods, exoticData))
    }

    companion object {
        private const val ITEM = "et_plasmacatalyst"
        private val MAX_FLUX_EQUIPMENT: MutableMap<HullSize, Int> = EnumMap(HullSize::class.java)

        init {
            MAX_FLUX_EQUIPMENT[HullSize.FIGHTER] = 10
            MAX_FLUX_EQUIPMENT[HullSize.FRIGATE] = 10
            MAX_FLUX_EQUIPMENT[HullSize.DESTROYER] = 20
            MAX_FLUX_EQUIPMENT[HullSize.CRUISER] = 30
            MAX_FLUX_EQUIPMENT[HullSize.CAPITAL_SHIP] = 50
        }
    }
}

enum class PlasmaFluxCatalystThings{ VENTS, CAPACITORS }
