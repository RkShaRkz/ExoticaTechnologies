package exoticatechnologies.combat

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import exoticatechnologies.util.getAllShipWeapons

object ExoticaCombatUtils {
    fun getAverageWeaponRange(ship: ShipAPI, includePD: Boolean): Float {
        val weaponRanges = ship.allWeapons
            .filter { it.type != WeaponAPI.WeaponType.MISSILE }
            .filter { !(includePD || it.hasAIHint(WeaponAPI.AIHints.PD)) }
            .map { it.range }
            .ifEmpty { mutableListOf(0f) }

        return weaponRanges.sum() / weaponRanges.size
    }

    fun getMaxWeaponRange(ship: ShipAPI, includePD: Boolean): Float {
        return ship.allWeapons
            .filter { it.type != WeaponAPI.WeaponType.MISSILE }
            .filter { !(includePD || it.hasAIHint(WeaponAPI.AIHints.PD)) }
            .ifEmpty { mutableListOf() }
            .maxOfOrNull { it.range } ?: 0f
    }

    fun getLargestDamageContributingRange(ship: ShipAPI): Float {
        // Create a range to "weapon damage potential" map
        val rangeDamageMap = mutableMapOf<Float, Float>()
        // For all weapons on installing ship, "calculate" it's DPS and derive potential damage over 10 seconds
        for (weapon in getAllShipWeapons(ship)) {
            // If weapon is broken, skip it
            if (weapon.isDisabled || weapon.isPermanentlyDisabled) continue
            // "sustainedDps" might make sense but not really because it evaluates over "ship fires for infinite amount of time"
//                weapon.derivedStats.sustainedDps
            // Otherwise, grab it's DPS, and calculate the "damage contribution" over 10 seconds
            val weaponDps = weapon.derivedStats.dps
            val weapon10secPotential = weaponDps * 10f

            val weaponRange = weapon.range
            // Add to range in the map
            val currentRangeDamageValue = rangeDamageMap[weaponRange] ?: 0f
            // Update map
            rangeDamageMap[weaponRange] = currentRangeDamageValue + weapon10secPotential
        }

        // After all weapons were processed, grab the key with the highest value
        val largestEntry = rangeDamageMap.maxByOrNull { it.value }
        return if (largestEntry != null) {
            largestEntry.key
        } else {
            // no maximum was found, fallback to 0
            0f
        }
    }
}
