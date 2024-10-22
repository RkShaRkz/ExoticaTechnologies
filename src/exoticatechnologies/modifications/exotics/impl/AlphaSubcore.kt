package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.Misc
import exoticatechnologies.hullmods.AlphaSubcoreHM
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.util.*
import org.json.JSONObject
import java.awt.Color
import kotlin.properties.Delegates

class AlphaSubcore(key: String, settingsObj: JSONObject) :
    HullmodExotic(key, settingsObj, "et_alphasubcore", "AlphaSubcore", Color.cyan) {

    override fun getSalvageChance(chanceMult: Float): Float {
        return 0.05f * chanceMult
    }

    override fun canAfford(fleet: CampaignFleetAPI, market: MarketAPI?): Boolean {
        return Utilities.hasItem(fleet.cargo, ITEM)
                || Utilities.hasItem(Misc.getStorageCargo(market), ITEM)
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
            StringUtils.getTranslation(key, "longDescription")
                .format("bandwidthIncrease", BANDWIDTH_INCREASE * getPositiveMult(member, mods, exoticData))
                .format("smallReduction", AlphaSubcoreHM.WEAPON_REDUCTIONS[WeaponAPI.WeaponSize.SMALL])
                .format("medReduction", AlphaSubcoreHM.WEAPON_REDUCTIONS[WeaponAPI.WeaponSize.MEDIUM])
                .format("largeReduction", AlphaSubcoreHM.WEAPON_REDUCTIONS[WeaponAPI.WeaponSize.LARGE])
                .format("fghtrReduction", AlphaSubcoreHM.FIGHTER_REDUCTION)
                .format("bmberReduction", AlphaSubcoreHM.BOMBER_REDUCTION)
                .addToTooltip(tooltip, title)

            if(member.variant.hullMods.any { BLOCKED_HULLMODS.contains(it) }) {
                StringUtils
                        .getTranslation("AlphaSubcore", "conflictDetected")
                        .addToTooltip(tooltip)
            }
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

    override fun onInstall(member: FleetMemberAPI) {
        super.onInstall(member)

        if (member.isMultiModuleShip()) {
            val memberVariant = member.variant
            val childVariants = getChildModuleVariantList(member)
            addValueToMultimoduleShipMap(member, FleetMemberVariantData(member, memberVariant, childVariants))
        }
    }

    override fun onDestroy(member: FleetMemberAPI) {
        AnonymousLogger.log("--> onDestroy()\tmember: ${member}", "AlphaSubcore")
        super.onDestroy(member)
        AlphaSubcoreHM.removeListenerFrom(member)
        member.updateStats()
        multimoduleShipMap.remove(FleetMemberKeyWrapper(member))
        multimoduleShipStatsRemapping.remove(FleetMemberKeyWrapper(member))
        AnonymousLogger.log("onDestroy()\tmultimoduleShipMap: ${multimoduleShipMap}", "AlphaSubcore")
        AnonymousLogger.log("onDestroy()\tmultimoduleShipStatsRemapping: ${multimoduleShipStatsRemapping}", "AlphaSubcore")
        AnonymousLogger.log("<-- onDestroy()", "AlphaSubcore")
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
        return BANDWIDTH_INCREASE * getPositiveMult(member, mods, exoticData)
    }

    override fun shouldShareEffectToOtherModules(ship: ShipAPI?, module: ShipAPI?) = true

    override fun shouldAffectModulesToShareEffectsToOtherModules() = false

    companion object {
        private const val ITEM = "alpha_core"
        private const val BANDWIDTH_INCREASE = 60

        val BLOCKED_HULLMODS: MutableSet<String> = HashSet<String>().apply {
            add("specialsphmod_alpha_core_upgrades")
            add("specialsphmod_beta_core_upgrades")
            add("specialsphmod_gamma_core_upgrades")
        }

        private val multimoduleShipMap: HashMap<FleetMemberKeyWrapper, FleetMemberVariantData> = hashMapOf()
        private val multimoduleShipStatsRemapping: HashMap<FleetMemberKeyWrapper, List<MutableShipStatsAPI>> = hashMapOf()

        @JvmStatic
        fun addStatsToMultimoduleStatsMap(member: FleetMemberAPI, stats: MutableShipStatsAPI) {
            AnonymousLogger.log("--> addStatsToMultimoduleStatsMap()\tmember: ${member}, stats: ${stats}", "AlphaSubcore")
            val oldList = multimoduleShipStatsRemapping[FleetMemberKeyWrapper(member)] ?: emptyList()
            oldList.toMutableList().add(stats)
            multimoduleShipStatsRemapping[FleetMemberKeyWrapper(member)] = oldList.toList()
            AnonymousLogger.log("<-- addStatsToMultimoduleStatsMap()\tmultimoduleShipStatsRemapping: ${multimoduleShipStatsRemapping}", "AlphaSubcore")
        }

        @JvmStatic
        fun getStatsFromMultimoduleStatsMap(member: FleetMemberAPI): List<MutableShipStatsAPI> {
            val retVal: List<MutableShipStatsAPI> = if(multimoduleShipStatsRemapping[FleetMemberKeyWrapper(member)] != null) {
                // Since we're in a null-guard, it should be safe to doublebang?
                multimoduleShipStatsRemapping[FleetMemberKeyWrapper(member)]!!
            } else {
                throw IllegalArgumentException("FleetMemberAPI ${member} not found in multimoduleShipStatsRemapping")
            }

            return retVal
        }

        @JvmStatic
        fun addValueToMultimoduleShipMap(member: FleetMemberAPI, data: FleetMemberVariantData) {
            AnonymousLogger.log("--> addValueToMultimoduleMap()\tmember: ${member}, data: ${data}", "AlphaSubcore")
            multimoduleShipMap[FleetMemberKeyWrapper(member)] = data
            AnonymousLogger.log("<-- addValueToMultimoduleMap()\tmultimoduleMap: ${multimoduleShipMap}", "AlphaSubcore")
        }

        @JvmStatic
        fun doesMapContainFleetMemberAPI(member: FleetMemberAPI?): Boolean {
            if (member != null) {
                for (value in multimoduleShipMap.keys) {
                    if (value.fleetMember == member || value.fleetMember.id == member.id) return true
                }
            }

            return false
        }

        @JvmStatic
        fun doesMapContainVariant(variant: ShipVariantAPI): Boolean {
            for (value in multimoduleShipMap.values) {
                if (value.childVariants.contains(variant) || value.parentVariant == variant) return true
            }

            return false
        }

        @JvmStatic
        fun doesMapContainVariantHullSpec(variant: ShipVariantAPI): Boolean {
            for (value in multimoduleShipMap.values) {
                if (value.childVariants.contains(variant) || value.parentVariant == variant) return true
                // If we didn't find it above, dig a little deeper
                val remappedToHullSpecs = value.childVariants.map { childVariant -> childVariant.hullSpec }
                if (remappedToHullSpecs.contains(variant.hullSpec) || value.parentVariant.hullSpec == variant.hullSpec) return true
            }

            return false
        }

        @JvmStatic
        fun doesMapContainVariantHullId(variant: ShipVariantAPI): Boolean {
            for (value in multimoduleShipMap.values) {
                if (value.childVariants.contains(variant) || value.parentVariant == variant) return true
                // If we didn't find it above, dig a little deeper
                val remappedToHullSpecs = value.childVariants.map { childVariant -> childVariant.hullSpec }
                val remappedToHullIDs = remappedToHullSpecs.map { childVariantHullSpec -> childVariantHullSpec.hullId }
                if (remappedToHullSpecs.contains(variant.hullSpec) || value.parentVariant.hullSpec == variant.hullSpec) return true
                if (remappedToHullIDs.contains(variant.hullSpec.hullId) || value.parentVariant.hullSpec.hullId == variant.hullSpec.hullId) return true
            }

            return false
        }

        @JvmStatic
        fun getMapKeyMatchingSearchCriteria(variant: ShipVariantAPI): FleetMemberAPI {
            var retVal: FleetMemberAPI by Delegates.notNull<FleetMemberAPI>()
            for (key in multimoduleShipMap.keys) {
                val value = multimoduleShipMap[key]
                value?.let {
                    if (value.childVariants.contains(variant) || value.parentVariant == variant) retVal = key.fleetMember
                    // If we didn't find it above, dig a little deeper
                    val remappedToHullSpecs = value.childVariants.map { childVariant -> childVariant.hullSpec }
                    val remappedToHullIDs = remappedToHullSpecs.map { childVariantHullSpec -> childVariantHullSpec.hullId }
                    if (remappedToHullSpecs.contains(variant.hullSpec) || value.parentVariant.hullSpec == variant.hullSpec) retVal = key.fleetMember
                    if (remappedToHullIDs.contains(variant.hullSpec.hullId) || value.parentVariant.hullSpec.hullId == variant.hullSpec.hullId) retVal = key.fleetMember
                }
            }

            return retVal
        }

        @JvmStatic
        fun getAllMapKeysMatchingSearchCriteria(variant: ShipVariantAPI): List<FleetMemberAPI> {
            val retVal = mutableListOf<FleetMemberAPI>()
            for (key in multimoduleShipMap.keys) {
                val value = multimoduleShipMap[key]
                value?.let {
                    if (value.childVariants.contains(variant) || value.parentVariant == variant) retVal.add(key.fleetMember)
                    // If we didn't find it above, dig a little deeper
                    val remappedToHullSpecs = value.childVariants.map { childVariant -> childVariant.hullSpec }
                    val remappedToHullIDs = remappedToHullSpecs.map { childVariantHullSpec -> childVariantHullSpec.hullId }
                    if (remappedToHullSpecs.contains(variant.hullSpec) || value.parentVariant.hullSpec == variant.hullSpec) retVal.add(key.fleetMember)
                    if (remappedToHullIDs.contains(variant.hullSpec.hullId) || value.parentVariant.hullSpec.hullId == variant.hullSpec.hullId) retVal.add(key.fleetMember)
                }
            }

            return retVal.toList()
        }

        @JvmStatic
        fun getAllStatsMapKeysMatchingSearchedKey(fleetMember: FleetMemberAPI): List<FleetMemberAPI> {
            val retVal = mutableListOf<FleetMemberAPI>()
            for (key in multimoduleShipStatsRemapping.keys) {
                if (FleetMemberKeyWrapper.equalsTo(key.fleetMember, fleetMember)) retVal.add(key.fleetMember)
            }

            return retVal.toList()
        }
    }

    data class FleetMemberVariantData(
            val parent: FleetMemberAPI,
            val parentVariant: ShipVariantAPI,
            val childVariants: List<ShipVariantAPI>
    ) {
        override fun toString(): String {
            return "FleetMemberVariantData[${super.toString()}] { parent=${parent}, parentVariant=${parentVariant}, childVariants=${childVariants} }"
        }
    }

    private class FleetMemberKeyWrapper(val fleetMember: FleetMemberAPI) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FleetMemberKeyWrapper) return false
            val fleetMembersEqual = fleetMember == other.fleetMember
            val fleetMemberHullsEqual = fleetMember.hullId == other.fleetMember.hullId
            val fleetMembersHullSpecEqual = fleetMember.hullSpec == other.fleetMember.hullSpec
            val fleetMembersVariantsEqual = fleetMember.variant == other.fleetMember.variant
            val fleetMembersIDsEqual = fleetMember.id == other.fleetMember.id
            val fleetMemberOwnersEqual = fleetMember.owner == other.fleetMember.owner
            val fleetMemberNamesEqual = fleetMember.variant.displayName == other.fleetMember.variant.displayName
            val retVal = fleetMembersEqual && fleetMemberHullsEqual && fleetMembersHullSpecEqual && fleetMembersVariantsEqual && fleetMembersIDsEqual && fleetMemberOwnersEqual && fleetMemberNamesEqual
            AnonymousLogger.log("<-- equals() returning ${retVal}:\tfleetMembersEqual: ${fleetMembersEqual}, fleetMemberHullsEqual: ${fleetMemberHullsEqual}, fleetMembersHullSpecEqual: ${fleetMembersHullSpecEqual}, fleetMembersVariantsEqual: ${fleetMembersVariantsEqual}, fleetMembersIDsEqual: ${fleetMembersIDsEqual}, fleetMemberOwnersEqual: ${fleetMemberOwnersEqual}, fleetMemberNamesEqual: ${fleetMemberNamesEqual}\t\t\tchecked ($this).equals($other)", "AlphaSubcore:FleetMemberKeyWrapper")
            return retVal
        }

        override fun toString(): String {
            return fleetMember.toString()
        }

        override fun hashCode(): Int {
            var result = fleetMember.hashCode()
            result = 31 * result + fleetMember.hullId.hashCode()
            result = 31 * result + fleetMember.hullSpec.hashCode()
            result = 31 * result + fleetMember.variant.hashCode()
            result = 31 * result + fleetMember.id.hashCode()
            result = 31 * result + fleetMember.owner
            result = 31 * result + fleetMember.variant.displayName.hashCode()
            return result
        }

        companion object {
            @JvmStatic
            fun equalsTo(first: FleetMemberAPI?, second: FleetMemberAPI?): Boolean {
                if (first === second) return true
                if (first == null && second != null) return false;
                if (first != null && second == null) return false;
                if (first == null && second == null) return true;

                if (first != null && second != null) {
                    val fleetMembersEqual = first == second
                    val fleetMemberHullsEqual = first.hullId == second.hullId
                    val fleetMembersHullSpecEqual = first.hullSpec == second.hullSpec
                    val fleetMembersVariantsEqual = first.variant == second.variant
                    val fleetMembersIDsEqual = first.id == second.id
                    val fleetMemberOwnersEqual = first.owner == second.owner
                    val fleetMemberNamesEqual = first.variant.displayName == second.variant.displayName
                    val retVal = fleetMembersEqual && fleetMemberHullsEqual && fleetMembersHullSpecEqual && fleetMembersVariantsEqual && fleetMembersIDsEqual && fleetMemberOwnersEqual && fleetMemberNamesEqual
                    AnonymousLogger.log("<-- equalsTo() returning ${retVal}:\tfleetMembersEqual: ${fleetMembersEqual}, fleetMemberHullsEqual: ${fleetMemberHullsEqual}, fleetMembersHullSpecEqual: ${fleetMembersHullSpecEqual}, fleetMembersVariantsEqual: ${fleetMembersVariantsEqual}, fleetMembersIDsEqual: ${fleetMembersIDsEqual}, fleetMemberOwnersEqual: ${fleetMemberOwnersEqual}, fleetMemberNamesEqual: ${fleetMemberNamesEqual}\t\t\tchecked ($first).equals($second)", "AlphaSubcore:FleetMemberKeyWrapper")
                    return retVal
                }

                // Return false otherwise I suppose
                return false;
            }
        }
    }
}