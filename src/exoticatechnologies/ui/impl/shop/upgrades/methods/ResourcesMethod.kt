package exoticatechnologies.ui.impl.shop.upgrades.methods

import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import exoticatechnologies.hullmods.ExoticaTechHM
import exoticatechnologies.modifications.ShipModLoader.Companion.set
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.upgrades.Upgrade
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.Utilities
import lombok.Getter

class ResourcesMethod : DefaultUpgradeMethod() {
    @Getter
    override var key = "resources"
    override fun getOptionText(member: FleetMemberAPI, mods: ShipModifications, upgrade: Upgrade, market: MarketAPI?): String {
        return StringUtils.getString("UpgradeMethods", "ResourcesOption")
    }

    override fun getOptionTooltip(
        member: FleetMemberAPI,
        mods: ShipModifications,
        upgrade: Upgrade,
        market: MarketAPI?
    ): String? {
        return null
    }

    override fun canUse(member: FleetMemberAPI, mods: ShipModifications, upgrade: Upgrade, market: MarketAPI?): Boolean {
        if (upgrade.resourceRatios.isEmpty()) return false
        val upgradeCosts = upgrade.getResourceCosts(member, mods.getUpgrade(upgrade))
        val totalStacks = Utilities.getTotalResources(member.fleetData.fleet, market, upgradeCosts.keys)
        var canUpgrade = true
        for ((key, value) in upgradeCosts) {
            var remaining = totalStacks[key]!! - value
            if (remaining < 0) {
                canUpgrade = false
                remaining = 0
            }
            totalStacks[key] = remaining
        }
        return (canUpgrade
                && super.canUse(member, mods, upgrade, market))
    }

    override fun apply(fm: FleetMemberAPI, mods: ShipModifications, upgrade: Upgrade, market: MarketAPI?): String {
        val upgradeCosts = upgrade.getResourceCosts(fm, mods.getUpgrade(upgrade))
        Utilities.takeResources(fm.fleetData.fleet, market, upgradeCosts)
        mods.putUpgrade(upgrade)
        set(fm, mods)
        ExoticaTechHM.addToFleetMember(fm)
        return StringUtils.getTranslation("UpgradesDialog", "UpgradePerformedSuccessfully")
            .format("name", upgrade.name)
            .format("level", mods.getUpgrade(upgrade))
            .toString()
    }

    override fun getResourceCostMap(
        fm: FleetMemberAPI,
        mods: ShipModifications,
        upgrade: Upgrade,
        market: MarketAPI?,
        hovered: Boolean
    ): Map<String, Float> {
        val resourceCosts: MutableMap<String, Float> = HashMap()
        if (hovered) {
            val upgradeCosts = upgrade.getResourceCosts(fm, mods.getUpgrade(upgrade))
            for (key in upgradeCosts.keys) {
                val cost = java.lang.Float.valueOf(upgradeCosts[key]!!.toFloat())
                if (cost != 0f) {
                    resourceCosts[key] = cost
                }
            }
        }
        return resourceCosts
    }
}