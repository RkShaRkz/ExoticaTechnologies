package exoticatechnologies.modifications.conditions.impl

import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.conditions.OperatorCondition

class EquippedBallistics : OperatorCondition() {
    override val key = "equippedBallistics"

    override fun getActual(member: FleetMemberAPI, mods: ShipModifications?, variant: ShipVariantAPI): Any? {
        return variant.fittedWeaponSlots
                .asSequence()
                .map { variant.getSlot(it) }
                .map { weaponSlotAPI -> variant.getWeaponSpec(weaponSlotAPI.id) }
                .count { weaponSpecAPI -> weaponSpecAPI.type == WeaponAPI.WeaponType.BALLISTIC }
    }
}