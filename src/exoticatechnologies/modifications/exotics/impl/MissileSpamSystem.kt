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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MissileSpamSystem(key: String, settings: JSONObject) : Exotic(key, settings) {
    private lateinit var originalShip: ShipAPI

    private val logger: Logger = Logger.getLogger(MissileSpamSystem::class.java)

    override fun canAfford(fleet: CampaignFleetAPI, market: MarketAPI?): Boolean {
        return (Utilities.hasItem(fleet.cargo, ITEM)
                && fleet.cargo.credits.get() >= COST_CREDITS)
    }

    override fun removeItemsFromFleet(fleet: CampaignFleetAPI, member: FleetMemberAPI, market: MarketAPI?): Boolean {
        fleet.cargo.credits.subtract(COST_CREDITS)
        Utilities.takeItemQuantity(fleet.cargo, ITEM, 1f)
        return true
    }

    override fun modifyToolTip(tooltip: TooltipMakerAPI, title: UIComponentAPI, member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData, expand: Boolean) {
        StringUtils.getTranslation(key, "longDescription")
                .format("passive_boost", passiveBoostString)
                .format("active_boost", activeBoostString)
                .format("active_duration", activeDurationString)
                .format("cooldown_string", cooldownString)
                .addToTooltip(tooltip, title)
    }

    override fun applyToShip(id: String, member: FleetMemberAPI, ship: ShipAPI, mods: ShipModifications, exoticData: ExoticData) {
        super.applyToShip(id, member, ship, mods, exoticData)

        originalShip = ship
        val subsystem = MissileSpammer(ship)
        MagicSubsystemsManager.addSubsystemToShip(ship, subsystem)

        // passives
        ship.mutableStats.missileRoFMult.modifyMult(buffId, PASSIVE_BUFF_MISSILE_ROF_MULT)
    }

    private fun shouldAffectWeapon(weapon: WeaponAPI): Boolean {
        return weapon.slot != null
                && (weapon.spec.mountType == WeaponAPI.WeaponType.MISSILE || weapon.type == WeaponAPI.WeaponType.MISSILE || weapon.spec.type == WeaponAPI.WeaponType.MISSILE || weapon.slot.weaponType == WeaponAPI.WeaponType.MISSILE)
//                && !weapon.spec.hasTag(Tags.NO_RELOAD)
//                && weapon.spec.maxAmmo > 1
//                && weapon.ammoTracker != null
//                && weapon.ammoTracker.usesAmmo()
//                && weapon.ammoTracker.ammoPerSecond == 0f
        //FIXME well, we don't care if the weapons use ammo or not, we just care if they're missiles.
    }

    inner class MissileSpammer(ship: ShipAPI) : MagicSubsystem(ship) {
        private val affectedWeapons = ship.allWeapons.filter { weapon -> shouldAffectWeapon(weapon) }
        private var systemActivated = AtomicBoolean(false)
        private val refireMap = HashMap<WeaponAPI, RefireData>() //map of WeaponID,RefireData

        override fun getBaseActiveDuration(): Float = ABILITY_DURATION_IN_SEC

        override fun getBaseCooldownDuration(): Float = ABILITY_COOLDOWN_IN_SEC

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
                    ABILITY_DURATION_IN_SEC,
                    0f,
                    true,
                    false,
                    true
            )
            val originalMissileRoF = ship.mutableStats.missileRoFMult.modifiedValue
            ship.mutableStats.missileRoFMult.modifyMult(ACTIVE_BUFF_ID, ACTIVE_BUFF_MISSILE_ROF_MULT)
            debugLog("onActivate()\tapplied buff ${ACTIVE_BUFF_ID}: buffed missileRoFMult to ${ship.mutableStats.missileRoFMult.modifiedValue} from ${originalMissileRoF}")
            /*
            // Generate the refireMap *and* set each affected weapon's refire rate to something low
            for (weapon in affectedWeapons) {
                // put in map if not already there
                if (refireMap.contains(weapon).not()) {
                    refireMap[weapon] = RefireData(weapon, NUMBER_OF_FREE_RELOADS)
                    // Set it's refire rate to be 33% of what it was, or 1, whichever is lower
                    weapon.refireDelay = min(REFIRE_DELAY_MIN, weapon.refireDelay * REFIRE_DELAY_MULT) //0.001f
                } else {
                    // if it's already in the map, do nothing
                }
            }

            // avoid doing this if debug isn't set
            if (DEBUG) {
                debugLog("onActivate()\tAffected weapons: ${convertListOfWeaponsToListOfIDs(affectedWeapons)}")
                for (weapon in affectedWeapons) {
                    logWeaponStats(weapon)
                }
            }
             */
        }

        override fun onFinished() {
            super.onFinished()

            systemActivated.compareAndSet(true, false)
            val buffedMissileRoF = ship.mutableStats.missileRoFMult.modifiedValue
            ship.mutableStats.missileRoFMult.unmodify(ACTIVE_BUFF_ID)
            debugLog("onFinished()\tunapplied buff ${ACTIVE_BUFF_ID}: restoring missileRoFMult to ${ship.mutableStats.missileRoFMult.modifiedValue} from ${buffedMissileRoF}")
            // Restore all weapons original refire delays
//            for (weapon in affectedWeapons) {
//                refireMap[weapon]?.let {
//                    debugLog("onFinished()\trestoring weapon: ${weapon.id} refire delay from ${weapon.refireDelay} to ${it.originalRefireDelay}")
//                    weapon.refireDelay = it.originalRefireDelay
//                }
//            }
//
//            refireMap.clear()
        }

        /*
        override fun advance(amount: Float, isPaused: Boolean) {
            super.advance(amount, isPaused)

            if (!isPaused) {
                if (systemActivated.get()) {
                    // Go through all affected weapons, if anyone's reload status is close to 0, presume it just fired
                    // and add it to "refire again" list - refiring being "free reloads"
                    for (weapon in affectedWeapons) {
                        // Finally, deal with the actual missile duplication by going through the map,
                        // adding one ammo and reloading the weapon if it nearly fired, and decrementing the refires
                        if (weapon.ammoTracker.reloadProgress <= RECENTLY_EMPTIED_CLIP_THRESHOLD) {
                            refireMap[weapon]?.let {
                                debugLog("weapon ${weapon.id} has recently emptied clip, reloading\tfree reloads remaining: ${it.freeReloadsRemaining}")
                                var reloads = it.freeReloadsRemaining
                                if (reloads > 0 && weapon.usesAmmo() && weapon.ammoTracker.ammo == 0) {
                                    reloads--
                                    if (MISSILE_SPAM_AMMO_IS_FREE) {
                                        weapon.ammoTracker.addOneAmmo()
                                    }
                                    weapon.ammoTracker.reloadProgress = 1f
                                    refireMap[weapon] = RefireData(weapon, reloads)
                                }
                            }
                        }

                        logWeaponStats(weapon)
                    }
                }
            }
        }
         */

        private fun logWeaponStats(weapon: WeaponAPI) {
            debugLog("Weapon ID: ${weapon.id}, reloadProgress: ${weapon.ammoTracker.reloadProgress}, refireDelay: ${weapon.refireDelay}, cooldown: ${weapon.cooldown}, cooldownRemaining: ${weapon.cooldownRemaining}")
        }

        private fun convertListOfWeaponsToListOfIDs(weaponList: List<WeaponAPI>) : List<String> {
            val retVal = mutableListOf<String>()
            for (weapon in weaponList) {
                retVal.add(weapon.id)
            }
            return retVal
        }
    }

    data class RefireData(val weapon: WeaponAPI, val reloads: Int) {
        val originalRefireDelay = weapon.refireDelay
        val freeReloadsRemaining = max(reloads, 0)
    }

    private fun debugLog(log: String) {
        if (DEBUG) logger.info("[MissileSpamSystem] $log")
    }

    companion object {
        private const val DEBUG = true
        private const val COST_CREDITS: Float = 500000f
        private const val ITEM = "et_missileautoloader"

        private const val ABILITY_DURATION_IN_SEC = 10f
        private const val ABILITY_COOLDOWN_IN_SEC = 60f

        private const val REFIRE_DELAY_MULT = 1/3f
        private const val REFIRE_DELAY_MIN = 1f

        private const val PASSIVE_BUFF_MISSILE_ROF_MULT = 2f
        private const val ACTIVE_BUFF_ID = "MissileSpamSystemRoFIncrease"
        private const val ACTIVE_BUFF_MISSILE_ROF_MULT = 60f

        private const val COOLDOWN_REPLACEMENT = "*Has a cooldown of {cooldown} seconds*."
        private val cooldownString: String by lazy {
            "${
                COOLDOWN_REPLACEMENT.replace("{cooldown}", ABILITY_COOLDOWN_IN_SEC.toString())
            }"
        }

        private const val SPAM_ABILITY_BOOST_REPLACEMENT = "*either {spam_ability_boost} of original value, whichever is lower*."
        val SPAM_ABILITY_BOOST = "${REFIRE_DELAY_MIN} seconds or ${(REFIRE_DELAY_MULT * 100).roundToInt()}%%"
        private val spamAbilityBoostString: String by lazy {
            "${
                SPAM_ABILITY_BOOST_REPLACEMENT.replace("{spam_ability_boost}", SPAM_ABILITY_BOOST.toString())
            }"
        }

        val passiveBoostString = "${PASSIVE_BUFF_MISSILE_ROF_MULT*100}%%"
        val activeBoostString = "${ACTIVE_BUFF_MISSILE_ROF_MULT*100}%%"

        val activeDurationString = "${ABILITY_DURATION_IN_SEC} seconds"
    }
}