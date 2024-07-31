package exoticatechnologies.modifications.conditions.impl

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Tags
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.conditions.OperatorCondition
import org.apache.log4j.Logger


class ReloadableAmmoUsingWeapons : OperatorCondition() {
    override val key = "reloadableAmmoUsingWeapons"

    override fun getActual(member: FleetMemberAPI, mods: ShipModifications?, variant: ShipVariantAPI): Any? {
        log("member: ${member}\tid: ${member.id}\tmember name: ${member.shipName}\thullID: ${member.hullId}")

        val fittedWeaponSlots = variant.fittedWeaponSlots
        val weaponIds = fittedWeaponSlots.map { slotId -> variant.getWeaponId(slotId) }
        val weaponSpecAPIs = weaponIds.map { weaponId -> Global.getSettings().getWeaponSpec(weaponId) }
        val reloadingAmmoUsingWeapons = weaponSpecAPIs.map { weaponSpecAPI ->
            log("WeaponSpecAPI: ${weaponSpecAPI}, name: ${weaponSpecAPI.weaponName}\tuses ammo ? ${weaponSpecAPI.usesAmmo()}\thas NO_RELOAD tag ? ${weaponSpecAPI.hasTag(Tags.NO_RELOAD)}")
            weaponSpecAPI.usesAmmo() && !weaponSpecAPI.hasTag(Tags.NO_RELOAD)
        }
        val count = reloadingAmmoUsingWeapons.count { it }
        log("Counted ${count} weapons meeting criteria")

        return variant.fittedWeaponSlots
                .map { slotId -> variant.getWeaponId(slotId) }
                .map { weaponId -> Global.getSettings().getWeaponSpec(weaponId) }
                .map { weaponSpecAPI -> weaponSpecAPI.usesAmmo() && !weaponSpecAPI.hasTag(Tags.NO_RELOAD) }
                .count { it }
    }

    private fun log(log: String) {
        if (DEBUG) {
            logger.info("[ReloadableAmmoUsingWeapons] $log")
        }
    }

    companion object {
        private val DEBUG = true
        private val logger: Logger = Logger.getLogger(ReloadableAmmoUsingWeapons::class.java)
    }
}