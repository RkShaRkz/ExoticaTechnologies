package exoticatechnologies.modifications.conditions.impl

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Tags
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.conditions.OperatorCondition


class ReloadableAmmoUsingWeapons : OperatorCondition() {
    override val key = "reloadableAmmoUsingWeapons"

    override fun getActual(member: FleetMemberAPI, mods: ShipModifications?, variant: ShipVariantAPI): Any? {
        return variant.fittedWeaponSlots
                .map { slotId -> variant.getWeaponId(slotId) }
                .map { weaponId -> Global.getSettings().getWeaponSpec(weaponId) }
                .map { weaponSpecAPI -> weaponSpecAPI.usesAmmo() && !weaponSpecAPI.hasTag(Tags.NO_RELOAD)}
                .count { it }
    }
}