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
import exoticatechnologies.util.playSound
import org.apache.log4j.Logger
import org.json.JSONObject
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.combat.entities.SimpleEntity
import org.lwjgl.util.vector.Vector2f
import org.magiclib.subsystems.MagicSubsystem
import org.magiclib.subsystems.MagicSubsystemsManager
import java.awt.Color
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class AmmoCraftingSystem(key: String, settings: JSONObject) : Exotic(key, settings) {
    override var color = Color(0xAE1919)
    private lateinit var originalShip: ShipAPI

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
                .format("ammo_regen_percent", getPeriodReloadAmountPercentage(member, mods, exoticData))
                .format("ammo_regen_period", getPeriodDuration(member, mods, exoticData))
                .formatFloat("duration", calculateSystemActivationDuration(member, mods, exoticData))
                .format("ammo_regen_fail_chance", getFailChance(member, mods, exoticData))
                .formatFloat("fail_min_damage", getFailMinDamage(member, mods, exoticData))
                .formatFloat("fail_max_damage", getFailMaxDamage(member, mods, exoticData))
                .formatFloat("cooldown", calculateSystemCooldownDuration(member, mods, exoticData))
                .addToTooltip(tooltip, title)
    }

    override fun applyToShip(id: String, member: FleetMemberAPI, ship: ShipAPI, mods: ShipModifications, exoticData: ExoticData) {
        super.applyToShip(id, member, ship, mods, exoticData)

        originalShip = ship
        val subsystem: MagicSubsystem = AmmoCreator(ship, member, mods, exoticData)
        MagicSubsystemsManager.addSubsystemToShip(ship, subsystem)
    }

    private fun calculateSystemActivationDuration(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        // Calculate how many periods are required to fill up ammo to 100%
        // then multiply that with number of period durations
        val requiredPeriodsCount = 100 / getPeriodReloadAmountPercentage(member, mods, exoticData)
        val requiredActivationsDuration = requiredPeriodsCount * getPeriodDuration(member, mods, exoticData)

        return requiredActivationsDuration
    }

    private fun calculateSystemCooldownDuration(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return BASE_COOLDOWN_DURATION_IN_SECONDS * getNegativeMult(member, mods, exoticData)
    }

    /**
     * Returns the [RELOADING_AMOUNT_PER_PERIOD_PERCENT] modified by [getPositiveMult] as a decimal number, e.g. 0.3 for 30%
     */
    private fun getPeriodReloadAmount(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return (RELOADING_AMOUNT_PER_PERIOD_PERCENT / 100f) * getPositiveMult(member, mods, exoticData)
    }

    /**
     * Returns the [RELOADING_AMOUNT_PER_PERIOD_PERCENT] modified by [getPositiveMult] as a whole number, e.g. 30 for 30%
     */
    private fun getPeriodReloadAmountPercentage(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return RELOADING_AMOUNT_PER_PERIOD_PERCENT * getPositiveMult(member, mods, exoticData)
    }

    /**
     * Returns the [RELOADING_PERIOD_DURATION_IN_SECONDS] modified by [getNegativeMult]
     */
    private fun getPeriodDuration(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return RELOADING_PERIOD_DURATION_IN_SECONDS * getNegativeMult(member, mods, exoticData)
    }

    private fun getFailChance(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Int {
        return (RELOADING_FAIL_CHANCE_PERCENT * getNegativeMult(member, mods, exoticData)).roundToInt()
    }

    private fun getFailMinDamage(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return RELOADING_FAILS_CAUSE_DAMAGE_MIN_DAMAGE * getNegativeMult(member, mods, exoticData)
    }

    private fun getFailMaxDamage(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return RELOADING_FAILS_CAUSE_DAMAGE_MAX_DAMAGE * getNegativeMult(member, mods, exoticData)
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

    private fun reloadWeapon(weapon: WeaponAPI, member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData) {
        val reloadAmount = max(
                1f, weapon.ammoTracker.maxAmmo * getPeriodReloadAmount(member = member, mods = mods, exoticData = exoticData)
        ).roundToInt()  // 1 ammo or 10% of maxAmmo, whichever is higher - 10% of 8 is 0.8 so we want to reload at least 1 bullet in that case

        weapon.ammoTracker.ammo = min(weapon.ammoTracker.maxAmmo, weapon.ammoTracker.ammo + reloadAmount)
    }

    inner class AmmoCreator(
            ship: ShipAPI,
            val member: FleetMemberAPI,
            val mods: ShipModifications,
            val exoticData: ExoticData
    ) : MagicSubsystem(ship) {
        private val affectedWeapons = ship.allWeapons.filter { weapon -> shouldAffectWeapon(weapon) }
        private var systemActivated = AtomicBoolean(false)
        private val timeCounter = IntervalUtil(
                getPeriodDuration(member, mods, exoticData) - EPSILON, getPeriodDuration(member, mods, exoticData) + EPSILON
        )
        private val random = Random(System.nanoTime())

        override fun getBaseActiveDuration(): Float = calculateSystemActivationDuration(member, mods, exoticData)

        override fun getBaseCooldownDuration(): Float = calculateSystemCooldownDuration(member, mods, exoticData)

        override fun shouldActivateAI(amount: Float): Boolean {
            // This one is simple, if we have at least two weapons that aren't on max ammo - we should turn it on
            var weaponsNotOnFullAmmo = 0
            for (weapon in affectedWeapons) {
                if (weapon.ammoTracker.ammo < weapon.ammoTracker.maxAmmo) {
                    weaponsNotOnFullAmmo++
                }
                if (weaponsNotOnFullAmmo >= MIN_NONFULL_WEAPONS_NEEDED_FOR_ACTIVATION + 1) break
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
                        val rolledChance = random.nextInt(0, 100) //the until is exclusive, so we need 100 to make it [0,99]
                        debugLog("Period has passed, rolled chance for reloading\trolledChance: ${rolledChance}\tfail chance: ${getFailChance(member, mods, exoticData)}")
                        if (rolledChance < getFailChance(member, mods, exoticData)) {
                            // failed
                            val randomLocationOnShip = generateRandomLocationOnShip(ship)
                            if (RELOADING_FAILS_CAUSE_DAMAGE) {
                                Global
                                        .getCombatEngine()
                                        .applyDamage(
                                                ship,
                                                randomLocationOnShip,
                                                MathUtils.getRandomNumberInRange(
                                                        getFailMinDamage(member, mods, exoticData), getFailMaxDamage(member, mods, exoticData)
                                                ),
                                                DamageType.ENERGY,
                                                0f,
                                                true,
                                                true,
                                                ship
                                        )
                            }
                            Global
                                    .getCombatEngine()
                                    .spawnExplosion(
//                                                ship.location,
                                            randomLocationOnShip,
                                            ship.velocity,
//                                                Color.ORANGE.brighter(),
                                            Color(0x9B3707),
//                                                30f,
                                            250f,
                                            AFTERIMAGE_BLIP_DURATION
                                    )
                            spawnBadAfterimage()
                            spawnFailedReloadText(randomLocationOnShip)
                            playSound(RELOAD_FAIL_SOUND, ship)
                            spawnDebugInfo(randomLocationOnShip)
                        } else {
                            // success
                            debugLog("Reloading weapons\trefill amount: ${getPeriodReloadAmount(member, mods, exoticData)}")
                            for (weapon in affectedWeapons) {
                                debugLog("Reloading ${weapon.id}\treload amount: ${weapon.ammoTracker.maxAmmo * getPeriodReloadAmount(member, mods, exoticData)}")
                                reloadWeapon(weapon, member, mods, exoticData)
                            }
                            spawnGoodAfterimage()
                            playSound(RELOAD_SUCCESS_SOUND, ship)
                        }
                    }
                }
            }
        }

        private fun generateRandomLocationOnShip(ship: ShipAPI): Vector2f {
            val shipLocation = ship.location
            debugLog("--> generateRandomLocationOnShip()\tshipLocation: ${shipLocation}\tship name: ${ship.name}")
            debugLog("generateRandomLocationOnShip()\tship sprite width: ${ship.spriteAPI.width}, height: ${ship.spriteAPI.height}")
            // First things first, lets grab a random location on the ship
            val segmentLocation = if (ship.exactBounds != null) {
                debugLog("generateRandomLocationOnShip()\tsegments size: ${ship.exactBounds.segments.size}")
                ship.exactBounds.segments.random().p1
            } else {
                shipLocation
            }

            debugLog("generateRandomLocationOnShip()\tsegmentLocation: ${segmentLocation}")


            // Just find a point between segmentLocation and location; if they're the same point apply jittering
            val retVal = if (segmentLocation != shipLocation) {
                debugLog("generateRandomLocationOnShip()\tsegment location != ship location")
                // do a random point here
                val segmentX = segmentLocation.x
                val segmentY = segmentLocation.y

                debugLog("generateRandomLocationOnShip()\tsegmentX: ${segmentX}, segmentY: ${segmentY}")

//                val randX = MathUtils.getRandomNumberInRange(-segmentX, segmentX)
//                val randY = MathUtils.getRandomNumberInRange(-segmentY, segmentY)
                // We are going to get a random point between the ship location (middle of the ship) and this segment X/Y one
//                val randX = MathUtils.getRandomNumberInRange(shipLocation.x, segmentX)
//                val randY = MathUtils.getRandomNumberInRange(shipLocation.y, segmentY)

                var diffX = shipLocation.x - segmentLocation.x
                var diffY = shipLocation.y - segmentLocation.y
                debugLog("generateRandomLocationOnShip()\tdiffX: ${diffX}, diffY: ${diffY}")
                // I'm going to abs this, because I don't want to care if the diff is -1300 or not, it's still larger than 1000
                // And that's the most usual case I'm seeing, diffs that are beyond a -1000
                val relativeLocation = if (abs(diffX) > 1000 || abs(diffY) > 1000) {
                    // Even though this could technically be a valid number, more likely this means that
                    // our ship was at e.g. [0, -1500] and the segment at like [-133, -48] which just means
                    // there's no way this segment location isn't relative.
                    true
                } else { false }

                if (relativeLocation) {
                    debugLog("based on the diff amount, it was determined that the segmentLocation had to be relative, so diffX/Y are being recalibrated")
                    diffX = segmentLocation.x
                    diffY = segmentLocation.y
                    debugLog("recalibrated diffX: ${diffX}, diffY: ${diffY}")
                }

                val roundedDiffX = diffX.roundToInt()
                val roundedDiffY = diffY.roundToInt()

                val scaledDiffX = (3/4f * roundedDiffX).roundToInt()
                val scaledDiffY = (3/4f * roundedDiffY).roundToInt()

                val randX = MathUtils.getRandomNumberInRange(shipLocation.x, shipLocation.x + scaledDiffX)
                val randY = MathUtils.getRandomNumberInRange(shipLocation.y, shipLocation.y + scaledDiffY)

                debugLog("generateRandomLocationOnShip()\trandX: ${randX}, randY: ${randY}")

//                Vector2f(
//                        shipLocation.x + randX,
//                        shipLocation.y + randY
//                )
                Vector2f(
                        randX,
                        randY
                )
            } else {
                debugLog("generateRandomLocationOnShip()\tsegment location == ship location")
                // take half of ship's width/height, randomize teh values, add it to location and use that
                val shipWidth = ship.spriteAPI.width
                val shipHeight = ship.spriteAPI.height

                debugLog("generateRandomLocationOnShip()\tship width: ${shipWidth}\tship height: ${shipHeight}")

                val randXjitter = MathUtils.getRandomNumberInRange(-shipWidth / 3, shipWidth / 3)
                val randYjitter = MathUtils.getRandomNumberInRange(-shipHeight / 3, shipHeight / 3)
                debugLog("generateRandomLocationOnShip()\trandXjitter: ${randXjitter}, randYjitter: ${randYjitter}")

                Vector2f(
                        shipLocation.x + randXjitter,
                        shipLocation.y + randYjitter
                )
            }

            debugLog("<-- generateRandomLocationOnShip()\tretVal: ${retVal}")
            return retVal
        }

        private fun spawnFailedReloadText(location: Vector2f) {
            Global
                    .getCombatEngine()
                    .addFloatingText(
                            location,
                            "Krrr-*CLANK*",
//                            14f,
                            24f,
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
                    Color(0x2D2A2A),
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

        private fun spawnDebugInfo(location: Vector2f) {
            Global
                    .getCombatEngine()
                    .spawnExplosion(
                            location,
                            Vector2f(0f, 0f),
                            Color(0x9B3707),
                            25f,
                            100f
                    )

            Global
                    .getCombatEngine()
                    .addFloatingText(
                            location,
                            location.toString(),
                            34f,
                            Color.WHITE.brighter(),
                            SimpleEntity(location),
                            5f,
                            calculateSystemActivationDuration(member, mods, exoticData)
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

        private const val RELOAD_SUCCESS_SOUND = "ammo_creator_reload_success"
        private const val RELOAD_FAIL_SOUND = "ammo_creator_reload_fail"

        private const val EPSILON = 0.1f
    }
}