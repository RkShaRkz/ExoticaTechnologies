package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.Misc
import exoticatechnologies.combat.ExoticaCombatUtils
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.Utilities
import org.apache.log4j.Logger
import org.json.JSONObject
import org.magiclib.subsystems.MagicSubsystem
import org.magiclib.subsystems.MagicSubsystemsManager
import java.awt.Color
import java.util.concurrent.atomic.AtomicBoolean

class MissileSpamSystem(key: String, settings: JSONObject) : Exotic(key, settings) {
    override var color = RADIOACTIVE_GREEN
    private lateinit var originalShip: ShipAPI

    private val logger: Logger = Logger.getLogger(MissileSpamSystem::class.java)

    override fun getBasePrice(): Int = COST_CREDITS.toInt()

    override fun canAfford(fleet: CampaignFleetAPI, market: MarketAPI?): Boolean {
        return Utilities.hasItem(fleet.cargo, ITEM)
    }

    override fun removeItemsFromFleet(fleet: CampaignFleetAPI, member: FleetMemberAPI, market: MarketAPI?): Boolean {
        Utilities.takeItemQuantity(fleet.cargo, ITEM, 1f)
        return true
    }

    override fun modifyToolTip(tooltip: TooltipMakerAPI, title: UIComponentAPI, member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData, expand: Boolean) {
        StringUtils.getTranslation(key, "longDescription")
                .format("passive_boost", passiveBoostString)
                .format("active_boost", activeBoostString)
                .formatFloat("duration", ABILITY_DURATION_IN_SEC * getPositiveMult(member, mods, exoticData))
                .formatFloat("cooldown", ABILITY_COOLDOWN_IN_SEC * getNegativeMult(member, mods, exoticData))
                .addToTooltip(tooltip, title)
    }

    override fun applyToShip(id: String, member: FleetMemberAPI, ship: ShipAPI, mods: ShipModifications, exoticData: ExoticData) {
        super.applyToShip(id, member, ship, mods, exoticData)

        originalShip = ship
        val subsystem = MissileSpammer(ship, member, mods, exoticData)
        MagicSubsystemsManager.addSubsystemToShip(ship, subsystem)

        // passives
        ship.mutableStats.missileRoFMult.modifyMult(buffId, PASSIVE_BUFF_MISSILE_ROF_MULT)
    }

    private fun shouldAffectWeapon(weapon: WeaponAPI): Boolean {
        return weapon.slot != null
                && (
                        weapon.spec.mountType == WeaponAPI.WeaponType.MISSILE
                        || weapon.type == WeaponAPI.WeaponType.MISSILE
                        || weapon.spec.type == WeaponAPI.WeaponType.MISSILE
                        || weapon.slot.weaponType == WeaponAPI.WeaponType.MISSILE
                    )
    }

    inner class MissileSpammer(
            ship: ShipAPI,
            val member: FleetMemberAPI,
            val mods: ShipModifications,
            val exoticData: ExoticData
    ) : MagicSubsystem(ship) {
        private val affectedWeapons = ship.allWeapons.filter { weapon -> shouldAffectWeapon(weapon) }
        private var systemActivated = AtomicBoolean(false)

        override fun getBaseActiveDuration(): Float = ABILITY_DURATION_IN_SEC * getPositiveMult(member, mods, exoticData)

        override fun getBaseCooldownDuration(): Float = ABILITY_COOLDOWN_IN_SEC * getNegativeMult(member, mods, exoticData)

        override fun shouldActivateAI(amount: Float): Boolean {
            // If we have non-empty missile weapons and targets in range - yes
            val hasMissileWeapons = affectedWeapons.isNotEmpty()
            val weaponsMaxRange = ExoticaCombatUtils.getMaxWeaponRange(ship, false)
            val enemiesInRange = Global.getCombatEngine().ships.filter { filterShip ->
                Misc.getDistance(filterShip.location, ship.location) < weaponsMaxRange
                        && filterShip.owner != ship.owner
            }
            val hasEnemiesInRange = enemiesInRange.isNotEmpty()
            val atLeastOneMissileWeaponHasAmmo = affectedWeapons.any { it.ammoTracker.ammo > 0 }

            val retVal = hasMissileWeapons && atLeastOneMissileWeaponHasAmmo && hasEnemiesInRange
//            debugLog("shouldActivateAI()\thasMissileWeapons: ${hasMissileWeapons}, atLeastOneMissileWeaponHasAmmo: ${atLeastOneMissileWeaponHasAmmo}, hasEnemiesInRange: ${hasEnemiesInRange}\treturning ${retVal}")
            return retVal
        }

        override fun getDisplayText() = "Missile Spam System"

        override fun onActivate() {
            super.onActivate()

            systemActivated.compareAndSet(false, true)
            ship.addAfterimage(
                    Color.GREEN.brighter(),
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    baseActiveDuration,
                    0f,
                    true,
                    false,
                    true
            )
            val originalMissileRoF = ship.mutableStats.missileRoFMult.modifiedValue
            ship.mutableStats.missileRoFMult.modifyMult(ACTIVE_BUFF_ID, ACTIVE_BUFF_MISSILE_ROF_MULT)
            debugLog("onActivate()\tapplied buff ${ACTIVE_BUFF_ID}: buffed missileRoFMult to ${ship.mutableStats.missileRoFMult.modifiedValue} from ${originalMissileRoF}")
        }

        override fun onFinished() {
            super.onFinished()

            systemActivated.compareAndSet(true, false)
            val buffedMissileRoF = ship.mutableStats.missileRoFMult.modifiedValue
            ship.mutableStats.missileRoFMult.unmodify(ACTIVE_BUFF_ID)
            debugLog("onFinished()\tunapplied buff ${ACTIVE_BUFF_ID}: restoring missileRoFMult to ${ship.mutableStats.missileRoFMult.modifiedValue} from ${buffedMissileRoF}")
        }

    }

    private fun debugLog(log: String) {
        if (DEBUG) logger.info("[MissileSpamSystem] $log")
    }

    companion object {
        private const val DEBUG = false
        private const val COST_CREDITS: Float = 500000f
        private const val ITEM = "et_missileautoloader"

        private const val ABILITY_DURATION_IN_SEC = 10f
        private const val ABILITY_COOLDOWN_IN_SEC = 60f

        private const val PASSIVE_BUFF_MISSILE_ROF_MULT = 2f
        private const val ACTIVE_BUFF_ID = "MissileSpamSystemRoFIncrease"
        private const val ACTIVE_BUFF_MISSILE_ROF_MULT = 60f

        val passiveBoostString = "*${PASSIVE_BUFF_MISSILE_ROF_MULT * 100}%%*"
        val activeBoostString = "*${ACTIVE_BUFF_MISSILE_ROF_MULT * 100}%%*"

        private val RADIOACTIVE_GREEN = Color(0x00FF0A)
    }
}