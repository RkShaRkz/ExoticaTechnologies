package exoticatechnologies.modifications.conditions.impl

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.conditions.OperatorCondition


class AmmoWeapons : OperatorCondition() {
    override val key = "ammoWeapons"

    override fun getActual(member: FleetMemberAPI, mods: ShipModifications?, variant: ShipVariantAPI): Any? {
        return variant.fittedWeaponSlots
                .map { slotId -> variant.getWeaponId(slotId) }
                .map { weaponId -> Global.getSettings().getWeaponSpec(weaponId) }
                .map { weaponSpecAPI -> weaponSpecAPI.usesAmmo() }
                .count { it }
    }
}