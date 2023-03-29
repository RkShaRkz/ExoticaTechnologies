package exoticatechnologies.modifications.upgrades

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.util.WeightedRandomPicker
import exoticatechnologies.modifications.ShipModFactory
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.util.Utilities
import lombok.extern.log4j.Log4j
import java.util.*

@Log4j
object UpgradesGenerator {
    @JvmStatic
    fun generate(member: FleetMemberAPI, mods: ShipModifications, context: ShipModFactory.GenerationContext): ETUpgrades {
        val config = context.factionConfig!!
        val allowedUpgrades = config.allowedUpgrades
        var upgradeChance = config.upgradeChance.toFloat() * getUpgradeChance(member)

        if (member.fleetData != null && member.fleetData.fleet != null) {
            if (member.fleetData.fleet.memoryWithoutUpdate.contains("\$exotica_upgradeMult")) {
                upgradeChance *= member.fleetData.fleet.memoryWithoutUpdate.getFloat("\$exotica_upgradeMult")
            }
        }

        val upgrades = mods.upgrades

        val smodCount = Utilities.getSModCount(member)
        upgradeChance *= (1 + smodCount).toFloat()

        val random = ShipModFactory.random
        if (random.nextFloat() < upgradeChance) {

            var usableBandwidth = mods.getBandwidthWithExotics(member)
            val perUpgradeMult = 1 + smodCount * 0.5f
            val upgradePicker = getPicker(random, allowedUpgrades)
            while (random.nextFloat() < usableBandwidth / 100f * perUpgradeMult && !upgradePicker.isEmpty) {
                var upgrade: Upgrade? = null
                while (upgrade == null && !upgradePicker.isEmpty) {
                    upgrade = upgradePicker.pick()

                    if (upgrade!!.getMaxLevel(member) <= upgrades.getUpgrade(upgrade)
                        || !upgrade.canApply(member, mods)
                        || usableBandwidth - upgrade.bandwidthUsage < 0f) {
                        upgradePicker.remove(upgrade)
                        upgrade = null
                    } else if (upgrades.getUpgrade(upgrade) == 0
                        && random.nextFloat() > (0.8f - smodCount * 0.25f).coerceAtLeast(0.05f)
                        && hasLeveledUpgrades(upgradePicker, member, upgrades)
                    ) {
                        upgrade = null
                    }
                }

                if (upgrade != null) {
                    if (random.nextFloat() < (upgrade.spawnChance * (1 + 0.2f * smodCount) * allowedUpgrades[upgrade]!! * context.upgradeChanceMult)) {
                        upgrades.addUpgrades(upgrade, 1)
                    }
                    usableBandwidth -= upgrade.bandwidthUsage
                }
            }
        }
        return upgrades
    }

    private fun getUpgradeChance(member: FleetMemberAPI): Float {
        val sizeFactor: Float = when (member.hullSpec.hullSize) {
            ShipAPI.HullSize.CAPITAL_SHIP -> 1.33f
            ShipAPI.HullSize.CRUISER -> 1.22f
            ShipAPI.HullSize.DESTROYER -> 1.11f
            else -> 1.0f
        }

        return sizeFactor
    }

    private fun getPicker(random: Random, allowedUpgrades: Map<Upgrade, Float>): WeightedRandomPicker<Upgrade> {
        val upgradePicker = WeightedRandomPicker<Upgrade>(random)
        allowedUpgrades.forEach { (upgrade, factionChance) ->
            upgradePicker.add(upgrade, upgrade.spawnChance * factionChance)
        }
        return upgradePicker
    }

    private fun hasLeveledUpgrades(upgradePicker: WeightedRandomPicker<Upgrade>, member: FleetMemberAPI, upgrades: ETUpgrades): Boolean {
        return upgradePicker.items.any {
            upgrades.getUpgrade(it) > 0 && it.getMaxLevel(member) > upgrades.getUpgrade(it)
        }
    }
}