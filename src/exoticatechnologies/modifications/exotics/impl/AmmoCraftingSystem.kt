package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.DamageType
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.IntervalUtil
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.Utilities
import exoticatechnologies.util.datastructures.ExoticStuffHolder
import org.apache.log4j.Logger
import org.json.JSONObject
import org.lazywizard.lazylib.MathUtils
import org.magiclib.subsystems.MagicSubsystem
import org.magiclib.subsystems.MagicSubsystemsManager
import java.awt.Color
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlin.random.Random

class AmmoCraftingSystem(key: String, settings: JSONObject) : Exotic(key, settings) {
    override var color = Color(0xAE1919)
    private lateinit var originalShip: ShipAPI
    private lateinit var stuffHolder: ExoticStuffHolder

    private val logger: Logger = Logger.getLogger(AmmoCraftingSystem::class.java)

    override fun getBasePrice(): Int = COST_CREDITS.toInt()

    override fun canAfford(fleet: CampaignFleetAPI, market: MarketAPI?): Boolean {
        return Utilities.hasItem(fleet.cargo, ITEM)
    }

    override fun removeItemsFromFleet(fleet: CampaignFleetAPI, member: FleetMemberAPI, market: MarketAPI?): Boolean {
        Utilities.takeItemQuantity(fleet.cargo, ITEM, 1f)
        return true
    }

    override fun modifyToolTip(
            tooltip: TooltipMakerAPI,
            title: UIComponentAPI,
            member: FleetMemberAPI,
            mods: ShipModifications,
            exoticData: ExoticData,
            expand: Boolean
    ) {
        StringUtils.getTranslation(key, "longDescription")
                .format("ammo_regen_percent", getPeriodReloadAmount(member, mods, exoticData))
                .format("ammo_regen_period", getPeriodDuration(member, mods, exoticData))
                .formatFloat("duration", getPeriodDuration(member, mods, exoticData))
                .format("ammo_regen_fail_chance", getFailChance(member, mods, exoticData))
                .formatFloat("fail_min_damage", getFailMinDamage(member, mods, exoticData))
                .formatFloat("fail_max_damage", getFailMaxDamage(member, mods, exoticData))
                .formatFloat("cooldown", calculateSystemCooldownDuration(member, mods, exoticData))
                .addToTooltip(tooltip, title)
    }

    override fun applyToShip(id: String, member: FleetMemberAPI, ship: ShipAPI, mods: ShipModifications, exoticData: ExoticData) {
        super.applyToShip(id, member, ship, mods, exoticData)

        originalShip = ship
        stuffHolder = ExoticStuffHolder(member, mods, exoticData)
        val subsystem: MagicSubsystem = AmmoCreator(ship, member, mods, exoticData)
        MagicSubsystemsManager.addSubsystemToShip(ship, subsystem)
    }

    private fun calculateSystemActivationDuration(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        // Calculate how many periods are required to fill up ammo to 100%
        // then multiply that with number of period durations
        val requiredPeriodsCount = 100 / getPeriodReloadAmount(member, mods, exoticData)
        val requiredActivationsDuration = requiredPeriodsCount * getPeriodDuration(member, mods, exoticData)

        return requiredActivationsDuration
    }

    private fun calculateSystemCooldownDuration(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return BASE_COOLDOWN_DURATION_IN_SECONDS * getNegativeMult(member, mods, exoticData)
    }

    private fun getPeriodReloadAmount(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return RELOADING_AMOUNT_PER_PERIOD_PERCENT * getPositiveMult(member, mods, exoticData)
    }

    private fun getPeriodDuration(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return RELOADING_PERIOD_DURATION_IN_SECONDS * getNegativeMult(member, mods, exoticData)
    }

    private fun getFailChance(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Int {
        return (RELOADING_FAIL_CHANCE_PERCENT * getNegativeMult(member, mods, exoticData)).roundToInt()
    }

    private fun getFailMinDamage(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return RELOADING_FAILS_CAUSE_DAMAGE_MIN_DAMAGE * getNegativeMult(member, mods, exoticData)
    }

    private fun getFailMinDamage(stuffHolder: ExoticStuffHolder): Float {
        return getFailMinDamage(
                member = stuffHolder.member,
                mods = stuffHolder.mods,
                exoticData = stuffHolder.exoticData
        )
    }

    private fun getFailMaxDamage(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return RELOADING_FAILS_CAUSE_DAMAGE_MAX_DAMAGE * getNegativeMult(member, mods, exoticData)
    }

    private fun getFailMaxDamage(stuffHolder: ExoticStuffHolder): Float {
        return getFailMaxDamage(
                member = stuffHolder.member,
                mods = stuffHolder.mods,
                exoticData = stuffHolder.exoticData
        )
    }

    private fun shouldAffectWeapon(weapon: WeaponAPI): Boolean {
        return weapon.slot != null
                && (isMissileWeapon(weapon) || isBallisticWeapon(weapon))
                && !weapon.spec.hasTag(Tags.NO_RELOAD)
                && weapon.spec.maxAmmo > 1
                && weapon.ammoTracker != null
                && weapon.ammoTracker.usesAmmo()
                && weapon.ammoTracker.ammoPerSecond == 0f
    }

    private fun isMissileWeapon(weapon: WeaponAPI): Boolean {
        return (
                weapon.spec.mountType == WeaponAPI.WeaponType.MISSILE
                        || weapon.type == WeaponAPI.WeaponType.MISSILE
                        || weapon.spec.type == WeaponAPI.WeaponType.MISSILE
                        || weapon.slot.weaponType == WeaponAPI.WeaponType.MISSILE
                )
    }

    private fun isBallisticWeapon(weapon: WeaponAPI): Boolean {
        return (
                weapon.spec.mountType == WeaponAPI.WeaponType.BALLISTIC
                        || weapon.type == WeaponAPI.WeaponType.BALLISTIC
                        || weapon.spec.type == WeaponAPI.WeaponType.BALLISTIC
                        || weapon.slot.weaponType == WeaponAPI.WeaponType.BALLISTIC
                )
    }

    private fun reloadWeapon(weapon: WeaponAPI) {
        val reloadAmount = weapon.ammoTracker.maxAmmo * getPeriodReloadAmount(member = stuffHolder.member, mods = stuffHolder.mods, exoticData = stuffHolder.exoticData)
        weapon.ammoTracker.reloadSize = reloadAmount
//        weapon.ammoTracker.ammoPerSecond = max(
//                1f, weapon.ammoTracker.reloadSize * getPeriodReloadAmount(member = stuffHolder.member, mods = stuffHolder.mods, exoticData = stuffHolder.exoticData)
//        ) // 1 or 10% of reloadSize, whichever is higher - 10% of 8 is 0.8 so we want to reload at least a whole bullet in that case
        weapon.ammoTracker.ammoPerSecond = weapon.ammoTracker.reloadSize
        weapon.ammoTracker.resetAmmo()
    }

    inner class AmmoCreator(
            ship: ShipAPI,
            val member: FleetMemberAPI,
            val mods: ShipModifications,
            val exoticData: ExoticData
    ) : MagicSubsystem(ship) {
        private val affectedWeapons = ship.allWeapons.filter { weapon -> shouldAffectWeapon(weapon) }
        private var systemActivated = AtomicBoolean(false)
        private val timeCounter = IntervalUtil(9.9f, 10.1f)
        private val random = Random(System.nanoTime())

        override fun getBaseActiveDuration(): Float = calculateSystemActivationDuration(member, mods, exoticData)

        override fun getBaseCooldownDuration(): Float = calculateSystemCooldownDuration(member, mods, exoticData)

        override fun shouldActivateAI(amount: Float): Boolean {
            // This one is simple, if we have at least two weapons that aren't on max ammo - we should turn it on
            var weaponsNotOnFullAmmo = 0;
            for (weapon in affectedWeapons) {
                if (weapon.ammoTracker.ammo < weapon.ammoTracker.maxAmmo) {
                    weaponsNotOnFullAmmo++
                }
                if (weaponsNotOnFullAmmo >= MIN_NONFULL_WEAPONS_NEEDED_FOR_ACTIVATION + 1) break;
            }

            return weaponsNotOnFullAmmo >= MIN_NONFULL_WEAPONS_NEEDED_FOR_ACTIVATION
        }

        override fun getDisplayText() = "Ammo Crafting System"

        override fun onActivate() {
            super.onActivate()

            systemActivated.compareAndSet(false, true)
        }

        override fun onFinished() {
            super.onFinished()

            systemActivated.compareAndSet(true, false)
        }

        override fun advance(amount: Float, isPaused: Boolean) {
            super.advance(amount, isPaused)

            if (!isPaused) {

                if (systemActivated.get()) {
                    timeCounter.advance(amount)
                    if (timeCounter.intervalElapsed()) {
                        // Another period seconds passed, roll a die and reload
                        val rolledChance = random.nextInt() % 100
                        debugLog("Period has passed, rolled chance for reloading\trolledChance: ${rolledChance}")
                        if (rolledChance < getFailChance(member, mods, exoticData)) {
                            // failed
                            if (RELOADING_FAILS_CAUSE_DAMAGE) {
                                Global
                                        .getCombatEngine()
                                        .applyDamage(
                                                ship,
                                                ship.location,
                                                MathUtils.getRandomNumberInRange(
                                                        getFailMinDamage(stuffHolder), getFailMaxDamage(stuffHolder)
                                                ),
                                                DamageType.ENERGY,
                                                0f,
                                                true,
                                                true,
                                                ship
                                        )

                                Global
                                        .getCombatEngine()
                                        .spawnExplosion(
                                                ship.location,
                                                ship.velocity,
                                                Color.ORANGE.brighter(),
                                                30f,
                                                AFTERIMAGE_BLIP_DURATION
                                        )
                            }
                            spawnBadAfterimage()
                            spawnFailedReloadText()
                        } else {
                            // success
                            for (weapon in affectedWeapons) {
                                reloadWeapon(weapon)
                            }
                            spawnGoodAfterimage()
                        }
                    }
                }
            }
        }

        private fun spawnFailedReloadText() {
            Global.getCombatEngine().addFloatingText(
                    ship.location,
                    "Krrr-*CLANK*",
                    14f,
                    Color.WHITE.brighter(),
                    ship,
                    2f,
                    2f
            )
        }

        private fun spawnGoodAfterimage() {
            ship.addAfterimage(
                    Color.RED.brighter(),
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    AFTERIMAGE_BLIP_DURATION,
                    0f,
                    true,
                    false,
                    true
            )
        }

        private fun spawnBadAfterimage() {
            ship.addAfterimage(
                    Color.DARK_GRAY.darker(),
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    AFTERIMAGE_BLIP_DURATION,
                    0f,
                    true,
                    false,
                    true
            )
        }
    }

    private fun debugLog(log: String) {
        if (DEBUG) logger.info("[AmmoCraftingSystem] $log")
    }


    companion object {
        private const val DEBUG = true
        private const val COST_CREDITS: Float = 350000f
        private const val ITEM = "et_ammocreationmachine"

        private const val RELOADING_PERIOD_DURATION_IN_SECONDS = 10         //10secs
        private const val RELOADING_AMOUNT_PER_PERIOD_PERCENT = 10          //10%
        private const val RELOADING_FAIL_CHANCE_PERCENT = 30                //30%

        private const val RELOADING_FAILS_CAUSE_DAMAGE = true
        private const val RELOADING_FAILS_CAUSE_DAMAGE_MIN_DAMAGE = 10f
        private const val RELOADING_FAILS_CAUSE_DAMAGE_MAX_DAMAGE = 100f
        private const val MIN_NONFULL_WEAPONS_NEEDED_FOR_ACTIVATION = 1

        private const val BASE_COOLDOWN_DURATION_IN_SECONDS = 60            //60secs

        private const val AFTERIMAGE_BLIP_DURATION = 2f
    }
}