package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Tags
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
                .addToTooltip(tooltip, title)
    }

    override fun applyToShip(id: String, member: FleetMemberAPI, ship: ShipAPI, mods: ShipModifications, exoticData: ExoticData) {
        super.applyToShip(id, member, ship, mods, exoticData)

        originalShip = ship
        val subsystem = MissileSpammer(ship)
        MagicSubsystemsManager.addSubsystemToShip(ship, subsystem)
    }

    private fun shouldAffectWeapon(weapon: WeaponAPI): Boolean {
        return weapon.slot != null
                && (weapon.spec.mountType == WeaponAPI.WeaponType.MISSILE || weapon.type == WeaponAPI.WeaponType.MISSILE || weapon.spec.type == WeaponAPI.WeaponType.MISSILE || weapon.slot.weaponType == WeaponAPI.WeaponType.MISSILE)
                && !weapon.spec.hasTag(Tags.NO_RELOAD)
                && weapon.spec.maxAmmo > 1
                && weapon.ammoTracker != null
                && weapon.ammoTracker.usesAmmo()
                && weapon.ammoTracker.ammoPerSecond == 0f
    }

    inner class MissileSpammer(ship: ShipAPI) : MagicSubsystem(ship) {
        private val affectedWeapons = ship.allWeapons.filter { weapon -> shouldAffectWeapon(weapon) }
        private var systemActivated = AtomicBoolean(false)
        private val refireMap = HashMap<WeaponAPI, RefireData>()

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

            return hasMissileWeapons && atLeastOneMissileWeaponHasAmmo && hasEnemiesInRange
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

            // avoid doing this if debug isn't set
            if (DEBUG) {
                debugLog("onActivate()\tAffected weapons: ${convertListOfWeaponsToListOfIDs(affectedWeapons)}")
                for (weapon in affectedWeapons) {
                    debugLog("onActivate()\tweapon: ${weapon.id}, reloadProgress: ${weapon.ammoTracker.reloadProgress}, refireDelay: ${weapon.refireDelay}")
                }
            }
        }

        override fun onFinished() {
            super.onFinished()

            systemActivated.compareAndSet(true, false)
            // Restore all weapons original refire delays
            for (weapon in affectedWeapons) {
                refireMap[weapon]?.let {
                    debugLog("onFinished()\trestoring weapon: ${weapon.id} refire delay from ${weapon.refireDelay} to ${it.originalRefireDelay}")
                    weapon.refireDelay = it.originalRefireDelay
                }
            }
        }

        override fun advance(amount: Float, isPaused: Boolean) {
            super.advance(amount, isPaused)

            if (!isPaused) {
                if (systemActivated.get()) {
                    // Go through all affected weapons, if anyone's reload status is close to 0, presume it just fired
                    // and add it to "refire again" list
                    for (weapon in affectedWeapons) {
                        if (weapon.ammoTracker.reloadProgress <= NEARLY_FIRED_THRESHOLD) {
                            // put in map if not already there
                            if (refireMap.contains(weapon).not()) {
                                refireMap[weapon] = RefireData(weapon, NUMBER_OF_REFIRES)
                                weapon.refireDelay = NEARLY_FIRED_THRESHOLD //just so it's not 0
                            } else {
                                // if it's already in the map, do nothing
                            }
                        }

                        // Otherwise, refill RefireData only if it's at 0 and the weapon is present in the map
                        if (refireMap.contains(weapon) && weapon.ammoTracker.reloadProgress >= ALMOST_RELOADED) {
                            // check the number of refires and refill if necessary
                            // but after a "missile spam" volley, we will require one full reload cycle
                            refireMap[weapon]?.let {
                                if (it.refires == 0) {
                                    // refill, we're empty
                                    refireMap[weapon] = RefireData(weapon, NUMBER_OF_REFIRES)
                                }
                            }
                        }

                        // Finally, deal with the actual missile duplication by going through the map,
                        // adding one ammo and reloading the weapon if it nearly fired, and decrementing the refires
                        if (weapon.ammoTracker.reloadProgress <= NEARLY_FIRED_THRESHOLD) {
                            refireMap[weapon]?.let {
                                var refires = it.refiresRemaining
                                if (refires > 0) {
                                    refires--
                                    if (MISSILE_SPAM_AMMO_IS_FREE) {
                                        weapon.ammoTracker.addOneAmmo()
                                    }
                                    weapon.ammoTracker.reloadProgress = 1f
                                    refireMap[weapon] = RefireData(weapon, refires)
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun convertListOfWeaponsToListOfIDs(weaponList: List<WeaponAPI>) : List<String> {
            val retVal = mutableListOf<String>()
            for (weapon in weaponList) {
                retVal.add(weapon.id)
            }
            return retVal
        }
    }

    data class RefireData(val weapon: WeaponAPI, val refires: Int) {
        val originalRefireDelay = weapon.refireDelay
        val refiresRemaining = max(refires, 0)
    }

    private fun debugLog(log: String) {
        if (DEBUG) logger.info("[MissileSpamSystem] $log")
    }

    companion object {
        private const val DEBUG = true
        private const val COST_CREDITS: Float = 500000f
        private const val ITEM = "et_missileautoloader"

        private const val NUMBER_OF_REFIRES = 2
        private const val NEARLY_FIRED_THRESHOLD: Float = 0.1f
        private const val ALMOST_RELOADED: Float = 1 - NEARLY_FIRED_THRESHOLD
        private const val ABILITY_DURATION_IN_SEC = 10f
        private const val ABILITY_COOLDOWN_IN_SEC = 60f
        private const val MISSILE_SPAM_AMMO_IS_FREE = false
    }
}