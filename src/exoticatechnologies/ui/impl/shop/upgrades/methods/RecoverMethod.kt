package exoticatechnologies.ui.impl.shop.upgrades.methods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.util.Misc
import exoticatechnologies.hullmods.ExoticaTechHM
import exoticatechnologies.modifications.ShipModLoader.Companion.set
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.upgrades.Upgrade
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.Utilities
import lombok.Getter

class RecoverMethod : UpgradeMethod {
    @Getter
    override var key = "recover"
    override fun getOptionText(
        fm: FleetMemberAPI,
        mods: ShipModifications,
        upgrade: Upgrade,
        market: MarketAPI
    ): String {
        val creditCost = getCreditCost(fm, mods, upgrade)
        val creditCostFormatted = Misc.getFormat().format(creditCost.toLong())
        return StringUtils.getTranslation("UpgradeMethods", "RecoverOption")
            .format("credits", creditCostFormatted)
            .toString()
    }

    override fun getOptionTooltip(
        fm: FleetMemberAPI,
        es: ShipModifications,
        upgrade: Upgrade,
        market: MarketAPI
    ): String {
        return StringUtils.getTranslation("UpgradeMethods", "RecoverOptionTooltip").toString()
    }

    override fun canShow(fm: FleetMemberAPI, mods: ShipModifications, upgrade: Upgrade, market: MarketAPI): Boolean {
        return mods.hasUpgrade(upgrade)
    }

    override fun canUse(fm: FleetMemberAPI, mods: ShipModifications, upgrade: Upgrade, market: MarketAPI): Boolean {
        if (mods.hasUpgrade(upgrade)) {
            val creditCost = getCreditCost(fm, mods, upgrade)
            return Global.getSector().playerFleet.cargo.credits.get() >= creditCost
        }
        return false
    }

    override fun apply(fm: FleetMemberAPI, mods: ShipModifications, upgrade: Upgrade, market: MarketAPI): String {
        val fleet = Global.getSector().playerFleet
        val creditCost = getCreditCost(fm, mods, upgrade)
        val stack = Utilities.getUpgradeChip(fleet.cargo, upgrade.key, mods.getUpgrade(upgrade))
        if (stack != null) {
            stack.add(1f)
        } else {
            fleet.cargo.addSpecial(upgrade.getNewSpecialItemData(mods.getUpgrade(upgrade)), 1f)
        }
        mods.removeUpgrade(upgrade)
        fleet.cargo.credits.subtract(creditCost.toFloat())
        set(fm, mods)
        ExoticaTechHM.addToFleetMember(fm)
        return StringUtils.getString("UpgradesDialog", "UpgradeRecoveredSuccessfully")
    }

    override fun getResourceCostMap(
        fm: FleetMemberAPI,
        mods: ShipModifications,
        upgrade: Upgrade,
        market: MarketAPI,
        hovered: Boolean
    ): Map<String, Float> {
        val resourceCosts: MutableMap<String, Float> = HashMap()
        if (hovered) {
            resourceCosts[Commodities.CREDITS] = getCreditCost(fm, mods, upgrade).toFloat()
            var resourceName = StringUtils.getTranslation("ShipListDialog", "ChipName")
                .format("name", upgrade.name)
                .toString()
            if (mods.hasUpgrade(upgrade)) {
                resourceName = StringUtils.getTranslation("ShipListDialog", "UpgradeChipWithLevelText")
                    .format("upgradeName", upgrade.name)
                    .format("level", mods.getUpgrade(upgrade))
                    .toString()
            }
            resourceCosts[String.format("&%s", resourceName)] = -1f
        }
        return resourceCosts
    }

    override fun usesBandwidth(): Boolean {
        return false
    }

    override fun usesLevel(): Boolean {
        return false
    }

    companion object {
        /**
         * Sums up the floats in the map.
         *
         * @param resourceCosts resource cost map
         * @return The sum.
         */
        private fun getCreditCostForResources(resourceCosts: Map<String, Int>): Int {
            var creditCost = 0f
            for ((key, value) in resourceCosts) {
                creditCost += Utilities.getItemPrice(key) * value
            }
            return creditCost.toInt()
        }

        fun getCreditCost(fm: FleetMemberAPI, mods: ShipModifications, upgrade: Upgrade): Int {
            val resourceCreditCost =
                getCreditCostForResources(upgrade.getResourceCosts(fm, mods.getUpgrade(upgrade))).toFloat()
            return (resourceCreditCost * 0.166).toInt()
        }
    }
}