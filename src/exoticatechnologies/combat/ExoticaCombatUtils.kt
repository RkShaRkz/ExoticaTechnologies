package exoticatechnologies.combat

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import exoticatechnologies.util.getAllShipWeapons

object ExoticaCombatUtils {

    /**
     * Returns average weapon range for [ship].
     * If [ship] is a multimodule ship, this will not consider all weapons on all modules unless [includeWeaponsOnAllModules] is set
     *
     * @param ship the ship to get average weapon range for
     * @param includePD whether PD weapons should be included or not
     * @param includeWeaponsOnAllModules whether weapons on all modules should be included or not. Defaults to **false**
     *
     * @return the average range of all weapons on the [ship] module
     */
    fun getAverageWeaponRange(ship: ShipAPI, includePD: Boolean, includeWeaponsOnAllModules: Boolean = false): Float {
        val listToUse = if (includeWeaponsOnAllModules) {
            getAllShipWeapons(ship)
        } else {
            ship.allWeapons
        }

        val weaponRanges = listToUse
            .filter { it.type != WeaponAPI.WeaponType.MISSILE }
            .filter { !(includePD || it.hasAIHint(WeaponAPI.AIHints.PD)) }
            .map { it.range }
            .ifEmpty { listOf(0f) }

        return weaponRanges.sum() / weaponRanges.size
    }

    /**
     * Returns maximum weapon range for [ship].
     * If [ship] is a multimodule ship, this will not consider all weapons on all modules unless [includeWeaponsOnAllModules] is set
     *
     * @param ship the ship to get maximum weapon range for
     * @param includePD whether PD weapons should be included or not
     * @param includeWeaponsOnAllModules whether weapons on all modules should be included or not. Defaults to **false**
     */
    fun getMaxWeaponRange(ship: ShipAPI, includePD: Boolean, includeWeaponsOnAllModules: Boolean = false): Float {
        val listToUse = if(includeWeaponsOnAllModules) {
            getAllShipWeapons(ship)
        } else {
            ship.allWeapons
        }
        return listToUse
            .filter { it.type != WeaponAPI.WeaponType.MISSILE }
            .filter { !(includePD || it.hasAIHint(WeaponAPI.AIHints.PD)) }
            .ifEmpty { mutableListOf() }
            .maxOfOrNull { it.range } ?: 0f
    }

    /**
     * Returns the largest damage contributing range, which is the range that has the most DPS over a 10 second potential.
     *
     * Example:
     * Consider a ship that has four guns that deal 25 damage every second of range 300, and one gun that deals 100 damage every 5 seconds, of range 800.
     * Looking at their 'per shot', both the 300 range ones and 800 range one have identical damage output - 100.
     * However, when their "10 second potential" is calculated, the potential 1000 damage at range 300 outweight the 200 damage at range 800.
     *
     * @param ship the ship to calculate the largest damage contributing range for.
     * @return the largest damage contributing range.
     */
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
