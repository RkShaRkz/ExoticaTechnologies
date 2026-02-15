package exoticatechnologies.util.tests.utils

import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.CombatListenerManagerAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.loading.WeaponSlotAPI
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.*

object ShipAPIUtils {

    /**
     * Creates a [ShipAPI] instance located at [location], facing at [facing] - which defaults to facing North (90)
     */
    fun createAnonymousShipAPI(location: Vector2f, facing: Float = 90f): ShipAPI {
        return object : ShipAPI {
            override fun getLocation(): Vector2f {
                return location
            }

            override fun getVelocity(): Vector2f {
                TODO("Not yet implemented")
            }

            override fun getFacing(): Float {
                return facing
            }

            override fun setFacing(facing: Float) {
                TODO("Not yet implemented")
            }

            override fun getAngularVelocity(): Float {
                TODO("Not yet implemented")
            }

            override fun setAngularVelocity(angVel: Float) {
                TODO("Not yet implemented")
            }

            override fun getOwner(): Int {
                TODO("Not yet implemented")
            }

            override fun setOwner(owner: Int) {
                TODO("Not yet implemented")
            }

            override fun getCollisionRadius(): Float {
                TODO("Not yet implemented")
            }

            override fun getCollisionClass(): CollisionClass {
                TODO("Not yet implemented")
            }

            override fun setCollisionClass(collisionClass: CollisionClass?) {
                TODO("Not yet implemented")
            }

            override fun getMass(): Float {
                TODO("Not yet implemented")
            }

            override fun setMass(mass: Float) {
                TODO("Not yet implemented")
            }

            override fun getExactBounds(): BoundsAPI {
                TODO("Not yet implemented")
            }

            override fun getShield(): ShieldAPI {
                TODO("Not yet implemented")
            }

            override fun getHullLevel(): Float {
                TODO("Not yet implemented")
            }

            override fun getHitpoints(): Float {
                TODO("Not yet implemented")
            }

            override fun getMaxHitpoints(): Float {
                TODO("Not yet implemented")
            }

            override fun setCollisionRadius(radius: Float) {
                TODO("Not yet implemented")
            }

            override fun getAI(): Any {
                TODO("Not yet implemented")
            }

            override fun isExpired(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setCustomData(key: String?, data: Any?) {
                TODO("Not yet implemented")
            }

            override fun removeCustomData(key: String?) {
                TODO("Not yet implemented")
            }

            override fun getCustomData(): MutableMap<String, Any> {
                TODO("Not yet implemented")
            }

            override fun setHitpoints(value: Float) {
                TODO("Not yet implemented")
            }

            override fun getFleetMemberId(): String {
                TODO("Not yet implemented")
            }

            override fun getMouseTarget(): Vector2f {
                TODO("Not yet implemented")
            }

            override fun isShuttlePod(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isDrone(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isFighter(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isFrigate(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isDestroyer(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isCruiser(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isCapital(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getHullSize(): ShipAPI.HullSize {
                TODO("Not yet implemented")
            }

            override fun getShipTarget(): ShipAPI {
                TODO("Not yet implemented")
            }

            override fun setShipTarget(ship: ShipAPI?) {
                TODO("Not yet implemented")
            }

            override fun getOriginalOwner(): Int {
                TODO("Not yet implemented")
            }

            override fun setOriginalOwner(originalOwner: Int) {
                TODO("Not yet implemented")
            }

            override fun resetOriginalOwner() {
                TODO("Not yet implemented")
            }

            override fun getMutableStats(): MutableShipStatsAPI {
                TODO("Not yet implemented")
            }

            override fun isHulk(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getAllWeapons(): MutableList<WeaponAPI> {
                TODO("Not yet implemented")
            }

            override fun getPhaseCloak(): ShipSystemAPI {
                TODO("Not yet implemented")
            }

            override fun getSystem(): ShipSystemAPI {
                TODO("Not yet implemented")
            }

            override fun getTravelDrive(): ShipSystemAPI {
                TODO("Not yet implemented")
            }

            override fun toggleTravelDrive() {
                TODO("Not yet implemented")
            }

            override fun setShield(type: ShieldAPI.ShieldType?, shieldUpkeep: Float, shieldEfficiency: Float, arc: Float) {
                TODO("Not yet implemented")
            }

            override fun getHullSpec(): ShipHullSpecAPI {
                TODO("Not yet implemented")
            }

            override fun getVariant(): ShipVariantAPI {
                TODO("Not yet implemented")
            }

            override fun useSystem() {
                TODO("Not yet implemented")
            }

            override fun getFluxTracker(): FluxTrackerAPI {
                TODO("Not yet implemented")
            }

            override fun getWingMembers(): MutableList<ShipAPI> {
                TODO("Not yet implemented")
            }

            override fun getWingLeader(): ShipAPI {
                TODO("Not yet implemented")
            }

            override fun isWingLeader(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getWing(): FighterWingAPI {
                TODO("Not yet implemented")
            }

            override fun getDeployedDrones(): MutableList<ShipAPI> {
                TODO("Not yet implemented")
            }

            override fun getDroneSource(): ShipAPI {
                TODO("Not yet implemented")
            }

            override fun getWingToken(): Any {
                TODO("Not yet implemented")
            }

            override fun getArmorGrid(): ArmorGridAPI {
                TODO("Not yet implemented")
            }

            override fun setRenderBounds(renderBounds: Boolean) {
                TODO("Not yet implemented")
            }

            override fun setCRAtDeployment(cr: Float) {
                TODO("Not yet implemented")
            }

            override fun getCRAtDeployment(): Float {
                TODO("Not yet implemented")
            }

            override fun getCurrentCR(): Float {
                TODO("Not yet implemented")
            }

            override fun setCurrentCR(cr: Float) {
                TODO("Not yet implemented")
            }

            override fun getWingCRAtDeployment(): Float {
                TODO("Not yet implemented")
            }

            override fun getTimeDeployedForCRReduction(): Float {
                TODO("Not yet implemented")
            }

            override fun getFullTimeDeployed(): Float {
                TODO("Not yet implemented")
            }

            override fun losesCRDuringCombat(): Boolean {
                TODO("Not yet implemented")
            }

            override fun controlsLocked(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setControlsLocked(controlsLocked: Boolean) {
                TODO("Not yet implemented")
            }

            override fun setShipSystemDisabled(systemDisabled: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getDisabledWeapons(): MutableSet<WeaponAPI> {
                TODO("Not yet implemented")
            }

            override fun getNumFlameouts(): Int {
                TODO("Not yet implemented")
            }

            override fun getHullLevelAtDeployment(): Float {
                TODO("Not yet implemented")
            }

            override fun setSprite(category: String?, key: String?) {
                TODO("Not yet implemented")
            }

            override fun setSprite(sprite: SpriteAPI?) {
                TODO("Not yet implemented")
            }

            override fun getSpriteAPI(): SpriteAPI {
                TODO("Not yet implemented")
            }

            override fun getEngineController(): ShipEngineControllerAPI {
                TODO("Not yet implemented")
            }

            override fun giveCommand(command: ShipCommand?, param: Any?, groupNumber: Int) {
                TODO("Not yet implemented")
            }

            override fun setShipAI(ai: ShipAIPlugin?) {
                TODO("Not yet implemented")
            }

            override fun getShipAI(): ShipAIPlugin {
                TODO("Not yet implemented")
            }

            override fun resetDefaultAI() {
                TODO("Not yet implemented")
            }

            override fun turnOnTravelDrive() {
                TODO("Not yet implemented")
            }

            override fun turnOnTravelDrive(dur: Float) {
                TODO("Not yet implemented")
            }

            override fun turnOffTravelDrive() {
                TODO("Not yet implemented")
            }

            override fun isRetreating(): Boolean {
                TODO("Not yet implemented")
            }

            override fun abortLanding() {
                TODO("Not yet implemented")
            }

            override fun beginLandingAnimation(target: ShipAPI?) {
                TODO("Not yet implemented")
            }

            override fun isLanding(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isFinishedLanding(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isAlive(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isInsideNebula(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setInsideNebula(isInsideNebula: Boolean) {
                TODO("Not yet implemented")
            }

            override fun isAffectedByNebula(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setAffectedByNebula(affectedByNebula: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getDeployCost(): Float {
                TODO("Not yet implemented")
            }

            override fun removeWeaponFromGroups(weapon: WeaponAPI?) {
                TODO("Not yet implemented")
            }

            override fun applyCriticalMalfunction(module: Any?) {
                TODO("Not yet implemented")
            }

            override fun applyCriticalMalfunction(module: Any?, permanent: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getBaseCriticalMalfunctionDamage(): Float {
                TODO("Not yet implemented")
            }

            override fun getEngineFractionPermanentlyDisabled(): Float {
                TODO("Not yet implemented")
            }

            override fun getCombinedAlphaMult(): Float {
                TODO("Not yet implemented")
            }

            override fun getLowestHullLevelReached(): Float {
                TODO("Not yet implemented")
            }

            override fun getAIFlags(): ShipwideAIFlags {
                TODO("Not yet implemented")
            }

            override fun getWeaponGroupsCopy(): MutableList<WeaponGroupAPI> {
                TODO("Not yet implemented")
            }

            override fun isHoldFire(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isHoldFireOneFrame(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setHoldFireOneFrame(holdFireOneFrame: Boolean) {
                TODO("Not yet implemented")
            }

            override fun isPhased(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isAlly(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setWeaponGlow(glow: Float, color: Color?, types: EnumSet<WeaponAPI.WeaponType>?) {
                TODO("Not yet implemented")
            }

            override fun setVentCoreColor(color: Color?) {
                TODO("Not yet implemented")
            }

            override fun setVentFringeColor(color: Color?) {
                TODO("Not yet implemented")
            }

            override fun getVentCoreColor(): Color {
                TODO("Not yet implemented")
            }

            override fun getVentFringeColor(): Color {
                TODO("Not yet implemented")
            }

            override fun getHullStyleId(): String {
                TODO("Not yet implemented")
            }

            override fun getWeaponGroupFor(weapon: WeaponAPI?): WeaponGroupAPI {
                TODO("Not yet implemented")
            }

            override fun setCopyLocation(loc: Vector2f?, copyAlpha: Float, copyFacing: Float) {
                TODO("Not yet implemented")
            }

            override fun getCopyLocation(): Vector2f {
                TODO("Not yet implemented")
            }

            override fun setAlly(ally: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getId(): String {
                TODO("Not yet implemented")
            }

            override fun getName(): String {
                TODO("Not yet implemented")
            }

            override fun setJitter(source: Any?, color: Color?, intensity: Float, copies: Int, range: Float) {
                TODO("Not yet implemented")
            }

            override fun setJitter(source: Any?, color: Color?, intensity: Float, copies: Int, minRange: Float, range: Float) {
                TODO("Not yet implemented")
            }

            override fun setJitterUnder(source: Any?, color: Color?, intensity: Float, copies: Int, range: Float) {
                TODO("Not yet implemented")
            }

            override fun setJitterUnder(source: Any?, color: Color?, intensity: Float, copies: Int, minRange: Float, range: Float) {
                TODO("Not yet implemented")
            }

            override fun getTimeDeployedUnderPlayerControl(): Float {
                TODO("Not yet implemented")
            }

            override fun getSmallTurretCover(): SpriteAPI {
                TODO("Not yet implemented")
            }

            override fun getSmallHardpointCover(): SpriteAPI {
                TODO("Not yet implemented")
            }

            override fun getMediumTurretCover(): SpriteAPI {
                TODO("Not yet implemented")
            }

            override fun getMediumHardpointCover(): SpriteAPI {
                TODO("Not yet implemented")
            }

            override fun getLargeTurretCover(): SpriteAPI {
                TODO("Not yet implemented")
            }

            override fun getLargeHardpointCover(): SpriteAPI {
                TODO("Not yet implemented")
            }

            override fun isDefenseDisabled(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setDefenseDisabled(defenseDisabled: Boolean) {
                TODO("Not yet implemented")
            }

            override fun setPhased(phased: Boolean) {
                TODO("Not yet implemented")
            }

            override fun setExtraAlphaMult(transparency: Float) {
                TODO("Not yet implemented")
            }

            override fun setApplyExtraAlphaToEngines(applyExtraAlphaToEngines: Boolean) {
                TODO("Not yet implemented")
            }

            override fun setOverloadColor(color: Color?) {
                TODO("Not yet implemented")
            }

            override fun resetOverloadColor() {
                TODO("Not yet implemented")
            }

            override fun getOverloadColor(): Color {
                TODO("Not yet implemented")
            }

            override fun isRecentlyShotByPlayer(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getMaxSpeedWithoutBoost(): Float {
                TODO("Not yet implemented")
            }

            override fun getHardFluxLevel(): Float {
                TODO("Not yet implemented")
            }

            override fun fadeToColor(source: Any?, color: Color?, durIn: Float, durOut: Float, maxShift: Float) {
                TODO("Not yet implemented")
            }

            override fun isShowModuleJitterUnder(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setShowModuleJitterUnder(showModuleJitterUnder: Boolean) {
                TODO("Not yet implemented")
            }

            override fun addAfterimage(color: Color?, locX: Float, locY: Float, velX: Float, velY: Float, maxJitter: Float, `in`: Float, dur: Float, out: Float, additive: Boolean, combineWithSpriteColor: Boolean, aboveShip: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getCaptain(): PersonAPI {
                TODO("Not yet implemented")
            }

            override fun getStationSlot(): WeaponSlotAPI {
                TODO("Not yet implemented")
            }

            override fun setStationSlot(stationSlot: WeaponSlotAPI?) {
                TODO("Not yet implemented")
            }

            override fun getParentStation(): ShipAPI {
                TODO("Not yet implemented")
            }

            override fun setParentStation(station: ShipAPI?) {
                TODO("Not yet implemented")
            }

            override fun getFixedLocation(): Vector2f {
                TODO("Not yet implemented")
            }

            override fun setFixedLocation(fixedLocation: Vector2f?) {
                TODO("Not yet implemented")
            }

            override fun hasRadarRibbonIcon(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isTargetable(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setStation(isStation: Boolean) {
                TODO("Not yet implemented")
            }

            override fun isSelectableInWarroom(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isShipWithModules(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setShipWithModules(isShipWithModules: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getChildModulesCopy(): MutableList<ShipAPI> {
                TODO("Not yet implemented")
            }

            override fun isPiece(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getVisualBounds(): BoundsAPI {
                TODO("Not yet implemented")
            }

            override fun getRenderOffset(): Vector2f {
                TODO("Not yet implemented")
            }

            override fun splitShip(): ShipAPI {
                TODO("Not yet implemented")
            }

            override fun getNumFighterBays(): Int {
                TODO("Not yet implemented")
            }

            override fun isPullBackFighters(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setPullBackFighters(pullBackFighters: Boolean) {
                TODO("Not yet implemented")
            }

            override fun hasLaunchBays(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getLaunchBaysCopy(): MutableList<FighterLaunchBayAPI> {
                TODO("Not yet implemented")
            }

            override fun getFighterTimeBeforeRefit(): Float {
                TODO("Not yet implemented")
            }

            override fun setFighterTimeBeforeRefit(fighterTimeBeforeRefit: Float) {
                TODO("Not yet implemented")
            }

            override fun getAllWings(): MutableList<FighterWingAPI> {
                TODO("Not yet implemented")
            }

            override fun getSharedFighterReplacementRate(): Float {
                TODO("Not yet implemented")
            }

            override fun areSignificantEnemiesInRange(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getUsableWeapons(): MutableList<WeaponAPI> {
                TODO("Not yet implemented")
            }

            override fun getModuleOffset(): Vector2f {
                TODO("Not yet implemented")
            }

            override fun getMassWithModules(): Float {
                TODO("Not yet implemented")
            }

            override fun getOriginalCaptain(): PersonAPI {
                TODO("Not yet implemented")
            }

            override fun isRenderEngines(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setRenderEngines(renderEngines: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getSelectedGroupAPI(): WeaponGroupAPI {
                TODO("Not yet implemented")
            }

            override fun setHullSize(hullSize: ShipAPI.HullSize?) {
                TODO("Not yet implemented")
            }

            override fun ensureClonedStationSlotSpec() {
                TODO("Not yet implemented")
            }

            override fun setMaxHitpoints(maxArmor: Float) {
                TODO("Not yet implemented")
            }

            override fun setDHullOverlay(spriteName: String?) {
                TODO("Not yet implemented")
            }

            override fun isStation(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isStationModule(): Boolean {
                TODO("Not yet implemented")
            }

            override fun areAnyEnemiesInRange(): Boolean {
                TODO("Not yet implemented")
            }

            override fun blockCommandForOneFrame(command: ShipCommand?) {
                TODO("Not yet implemented")
            }

            override fun getMaxTurnRate(): Float {
                TODO("Not yet implemented")
            }

            override fun getTurnAcceleration(): Float {
                TODO("Not yet implemented")
            }

            override fun getTurnDeceleration(): Float {
                TODO("Not yet implemented")
            }

            override fun getDeceleration(): Float {
                TODO("Not yet implemented")
            }

            override fun getAcceleration(): Float {
                TODO("Not yet implemented")
            }

            override fun getMaxSpeed(): Float {
                TODO("Not yet implemented")
            }

            override fun getFluxLevel(): Float {
                TODO("Not yet implemented")
            }

            override fun getCurrFlux(): Float {
                TODO("Not yet implemented")
            }

            override fun getMaxFlux(): Float {
                TODO("Not yet implemented")
            }

            override fun getMinFluxLevel(): Float {
                TODO("Not yet implemented")
            }

            override fun getMinFlux(): Float {
                TODO("Not yet implemented")
            }

            override fun setLightDHullOverlay() {
                TODO("Not yet implemented")
            }

            override fun setMediumDHullOverlay() {
                TODO("Not yet implemented")
            }

            override fun setHeavyDHullOverlay() {
                TODO("Not yet implemented")
            }

            override fun isJitterShields(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setJitterShields(jitterShields: Boolean) {
                TODO("Not yet implemented")
            }

            override fun isInvalidTransferCommandTarget(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setInvalidTransferCommandTarget(invalidTransferCommandTarget: Boolean) {
                TODO("Not yet implemented")
            }

            override fun clearDamageDecals() {
                TODO("Not yet implemented")
            }

            override fun syncWithArmorGridState() {
                TODO("Not yet implemented")
            }

            override fun syncWeaponDecalsWithArmorDamage() {
                TODO("Not yet implemented")
            }

            override fun isDirectRetreat(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setRetreating(retreating: Boolean, direct: Boolean) {
                TODO("Not yet implemented")
            }

            override fun isLiftingOff(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setVariantForHullmodCheckOnly(variant: ShipVariantAPI?) {
                TODO("Not yet implemented")
            }

            override fun getShieldCenterEvenIfNoShield(): Vector2f {
                TODO("Not yet implemented")
            }

            override fun getShieldRadiusEvenIfNoShield(): Float {
                TODO("Not yet implemented")
            }

            override fun getFleetMember(): FleetMemberAPI {
                TODO("Not yet implemented")
            }

            override fun getShieldTarget(): Vector2f {
                TODO("Not yet implemented")
            }

            override fun setShieldTargetOverride(x: Float, y: Float) {
                TODO("Not yet implemented")
            }

            override fun getListenerManager(): CombatListenerManagerAPI {
                TODO("Not yet implemented")
            }

            override fun addListener(listener: Any?) {
                TODO("Not yet implemented")
            }

            override fun removeListener(listener: Any?) {
                TODO("Not yet implemented")
            }

            override fun removeListenerOfClass(c: Class<*>?) {
                TODO("Not yet implemented")
            }

            override fun hasListener(listener: Any?): Boolean {
                TODO("Not yet implemented")
            }

            override fun hasListenerOfClass(c: Class<*>?): Boolean {
                TODO("Not yet implemented")
            }

            override fun <T : Any?> getListeners(c: Class<T>?): MutableList<T> {
                TODO("Not yet implemented")
            }

            override fun getParamAboutToApplyDamage(): Any {
                TODO("Not yet implemented")
            }

            override fun setParamAboutToApplyDamage(param: Any?) {
                TODO("Not yet implemented")
            }

            override fun getFluxBasedEnergyWeaponDamageMultiplier(): Float {
                TODO("Not yet implemented")
            }

            override fun setName(name: String?) {
                TODO("Not yet implemented")
            }

            override fun setHulk(isHulk: Boolean) {
                TODO("Not yet implemented")
            }

            override fun setCaptain(captain: PersonAPI?) {
                TODO("Not yet implemented")
            }

            override fun getShipExplosionRadius(): Float {
                TODO("Not yet implemented")
            }

            override fun setCircularJitter(circular: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getExtraAlphaMult(): Float {
                TODO("Not yet implemented")
            }

            override fun setAlphaMult(alphaMult: Float) {
                TODO("Not yet implemented")
            }

            override fun getAlphaMult(): Float {
                TODO("Not yet implemented")
            }

            override fun setAnimatedLaunch() {
                TODO("Not yet implemented")
            }

            override fun setLaunchingShip(launchingShip: ShipAPI?) {
                TODO("Not yet implemented")
            }

            override fun isNonCombat(considerOrders: Boolean): Boolean {
                TODO("Not yet implemented")
            }

            override fun findBestArmorInArc(facing: Float, arc: Float): Float {
                TODO("Not yet implemented")
            }

            override fun getAverageArmorInSlice(direction: Float, arc: Float): Float {
                TODO("Not yet implemented")
            }

            override fun setHoldFire(holdFire: Boolean) {
                TODO("Not yet implemented")
            }

            override fun cloneVariant() {
                TODO("Not yet implemented")
            }

            override fun setTimeDeployed(timeDeployed: Float) {
                TODO("Not yet implemented")
            }

            override fun setFluxVentTextureSheet(textureId: String?) {
                TODO("Not yet implemented")
            }

            override fun getFluxVentTextureSheet(): String {
                TODO("Not yet implemented")
            }

            override fun getAimAccuracy(): Float {
                TODO("Not yet implemented")
            }

            override fun getForceCarrierTargetTime(): Float {
                TODO("Not yet implemented")
            }

            override fun setForceCarrierTargetTime(forceCarrierTargetTime: Float) {
                TODO("Not yet implemented")
            }

            override fun getForceCarrierPullBackTime(): Float {
                TODO("Not yet implemented")
            }

            override fun setForceCarrierPullBackTime(forceCarrierPullBackTime: Float) {
                TODO("Not yet implemented")
            }

            override fun getForceCarrierTarget(): ShipAPI {
                TODO("Not yet implemented")
            }

            override fun setForceCarrierTarget(forceCarrierTarget: ShipAPI?) {
                TODO("Not yet implemented")
            }

            override fun setWing(wing: FighterWingAPI?) {
                TODO("Not yet implemented")
            }

            override fun getExplosionScale(): Float {
                TODO("Not yet implemented")
            }

            override fun setExplosionScale(explosionScale: Float) {
                TODO("Not yet implemented")
            }

            override fun getExplosionFlashColorOverride(): Color {
                TODO("Not yet implemented")
            }

            override fun setExplosionFlashColorOverride(explosionFlashColorOverride: Color?) {
                TODO("Not yet implemented")
            }

            override fun getExplosionVelocityOverride(): Vector2f {
                TODO("Not yet implemented")
            }

            override fun setExplosionVelocityOverride(explosionVelocityOverride: Vector2f?) {
                TODO("Not yet implemented")
            }

            override fun setNextHitHullDamageThresholdMult(threshold: Float, multBeyondThreshold: Float) {
                TODO("Not yet implemented")
            }

            override fun isEngineBoostActive(): Boolean {
                TODO("Not yet implemented")
            }

            override fun makeLookDisabled() {
                TODO("Not yet implemented")
            }

            override fun setExtraAlphaMult2(transparency: Float) {
                TODO("Not yet implemented")
            }

            override fun getExtraAlphaMult2(): Float {
                TODO("Not yet implemented")
            }

            override fun setDrone(isDrone: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getLayer(): CombatEngineLayers {
                TODO("Not yet implemented")
            }

            override fun setLayer(layer: CombatEngineLayers?) {
                TODO("Not yet implemented")
            }

            override fun isForceHideFFOverlay(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setForceHideFFOverlay(forceHideFFOverlay: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getTags(): MutableSet<String> {
                TODO("Not yet implemented")
            }

            override fun addTag(tag: String?) {
                TODO("Not yet implemented")
            }

            override fun hasTag(tag: String?): Boolean {
                TODO("Not yet implemented")
            }

            override fun getPeakTimeRemaining(): Float {
                TODO("Not yet implemented")
            }

            override fun getActiveLayers(): EnumSet<CombatEngineLayers> {
                TODO("Not yet implemented")
            }

            override fun isShipSystemDisabled(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isDoNotFlareEnginesWhenStrafingOrDecelerating(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setDoNotFlareEnginesWhenStrafingOrDecelerating(doNotFlare: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getFleetCommander(): PersonAPI {
                TODO("Not yet implemented")
            }

        }
    }
}
