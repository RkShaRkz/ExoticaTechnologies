package exoticatechnologies.util.tests.utils

import com.fs.starfarer.api.AnimationAPI
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.loading.MuzzleFlashSpec
import com.fs.starfarer.api.loading.WeaponSlotAPI
import com.fs.starfarer.api.loading.WeaponSpecAPI
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

/**
 * Class for 'mocking' and instantiating anonymous [WeaponAPI] instances usable in tests
 */
object WeaponAPIUtils {
    fun createAnonymousWeaponAPI(range: Float, dps: Float, isDisabled: Boolean = false ): WeaponAPI {
        return object : WeaponAPI {
            override fun getId(): String {
                TODO("Not yet implemented")
            }

            override fun getType(): WeaponAPI.WeaponType {
                TODO("Not yet implemented")
            }

            override fun getSize(): WeaponAPI.WeaponSize {
                TODO("Not yet implemented")
            }

            override fun setPD(pd: Boolean) {
                TODO("Not yet implemented")
            }

            override fun distanceFromArc(target: Vector2f?): Float {
                TODO("Not yet implemented")
            }

            override fun isAlwaysFire(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getCurrSpread(): Float {
                TODO("Not yet implemented")
            }

            override fun getCurrAngle(): Float {
                TODO("Not yet implemented")
            }

            override fun getArcFacing(): Float {
                TODO("Not yet implemented")
            }

            override fun getArc(): Float {
                TODO("Not yet implemented")
            }

            override fun setCurrAngle(angle: Float) {
                TODO("Not yet implemented")
            }

            override fun getRange(): Float {
                return range
            }

            override fun getDisplayArcRadius(): Float {
                TODO("Not yet implemented")
            }

            override fun getChargeLevel(): Float {
                TODO("Not yet implemented")
            }

            override fun getTurnRate(): Float {
                TODO("Not yet implemented")
            }

            override fun getProjectileSpeed(): Float {
                TODO("Not yet implemented")
            }

            override fun getDisplayName(): String {
                TODO("Not yet implemented")
            }

            override fun getAmmo(): Int {
                TODO("Not yet implemented")
            }

            override fun getMaxAmmo(): Int {
                TODO("Not yet implemented")
            }

            override fun setMaxAmmo(maxAmmo: Int) {
                TODO("Not yet implemented")
            }

            override fun resetAmmo() {
                TODO("Not yet implemented")
            }

            override fun getCooldownRemaining(): Float {
                TODO("Not yet implemented")
            }

            override fun getCooldown(): Float {
                TODO("Not yet implemented")
            }

            override fun setRemainingCooldownTo(value: Float) {
                TODO("Not yet implemented")
            }

            override fun isBeam(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isBurstBeam(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isPulse(): Boolean {
                TODO("Not yet implemented")
            }

            override fun requiresFullCharge(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getLocation(): Vector2f {
                TODO("Not yet implemented")
            }

            override fun isFiring(): Boolean {
                TODO("Not yet implemented")
            }

            override fun usesAmmo(): Boolean {
                TODO("Not yet implemented")
            }

            override fun usesEnergy(): Boolean {
                TODO("Not yet implemented")
            }

            override fun hasAIHint(hint: WeaponAPI.AIHints?): Boolean {
                TODO("Not yet implemented")
            }

            override fun getProjectileCollisionClass(): CollisionClass {
                TODO("Not yet implemented")
            }

            override fun beginSelectionFlash() {
                TODO("Not yet implemented")
            }

            override fun getFluxCostToFire(): Float {
                TODO("Not yet implemented")
            }

            override fun getMaxHealth(): Float {
                TODO("Not yet implemented")
            }

            override fun getCurrHealth(): Float {
                TODO("Not yet implemented")
            }

            override fun isDisabled(): Boolean {
                return isDisabled
            }

            override fun getDisabledDuration(): Float {
                TODO("Not yet implemented")
            }

            override fun isPermanentlyDisabled(): Boolean {
                return isDisabled
            }

            override fun getDamageType(): DamageType {
                TODO("Not yet implemented")
            }

            override fun getShip(): ShipAPI {
                TODO("Not yet implemented")
            }

            override fun getDerivedStats(): WeaponAPI.DerivedWeaponStatsAPI {
                return object : WeaponAPI.DerivedWeaponStatsAPI {
                    override fun getBurstFireDuration(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getSustainedDps(): Float {
                        return dps
                    }

                    override fun getEmpPerSecond(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getDamageOver30Sec(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getDps(): Float {
                        return dps
                    }

                    override fun getBurstDamage(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getFluxPerDam(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getRoF(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getFluxPerSecond(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getSustainedFluxPerSecond(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getDamagePerShot(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getEmpPerShot(): Float {
                        TODO("Not yet implemented")
                    }

                }
            }

            override fun setAmmo(ammo: Int) {
                TODO("Not yet implemented")
            }

            override fun getAnimation(): AnimationAPI {
                TODO("Not yet implemented")
            }

            override fun getSprite(): SpriteAPI {
                TODO("Not yet implemented")
            }

            override fun getUnderSpriteAPI(): SpriteAPI {
                TODO("Not yet implemented")
            }

            override fun getBarrelSpriteAPI(): SpriteAPI {
                TODO("Not yet implemented")
            }

            override fun renderBarrel(sprite: SpriteAPI?, loc: Vector2f?, alphaMult: Float) {
                TODO("Not yet implemented")
            }

            override fun isRenderBarrelBelow(): Boolean {
                TODO("Not yet implemented")
            }

            override fun disable() {
                TODO("Not yet implemented")
            }

            override fun disable(permanent: Boolean) {
                TODO("Not yet implemented")
            }

            override fun repair() {
                TODO("Not yet implemented")
            }

            override fun getSpec(): WeaponSpecAPI {
                TODO("Not yet implemented")
            }

            override fun getSlot(): WeaponSlotAPI {
                TODO("Not yet implemented")
            }

            override fun getEffectPlugin(): EveryFrameWeaponEffectPlugin {
                TODO("Not yet implemented")
            }

            override fun getMissileRenderData(): MutableList<MissileRenderDataAPI> {
                TODO("Not yet implemented")
            }

            override fun getDamage(): DamageAPI {
                TODO("Not yet implemented")
            }

            override fun getProjectileFadeRange(): Float {
                TODO("Not yet implemented")
            }

            override fun isDecorative(): Boolean {
                TODO("Not yet implemented")
            }

            override fun ensureClonedSpec() {
                TODO("Not yet implemented")
            }

            override fun getAmmoPerSecond(): Float {
                TODO("Not yet implemented")
            }

            override fun setPDAlso(pdAlso: Boolean) {
                TODO("Not yet implemented")
            }

            override fun setCurrHealth(currHealth: Float) {
                TODO("Not yet implemented")
            }

            override fun getMuzzleFlashSpec(): MuzzleFlashSpec {
                TODO("Not yet implemented")
            }

            override fun getBeams(): MutableList<BeamAPI> {
                TODO("Not yet implemented")
            }

            override fun getFirePoint(barrel: Int): Vector2f {
                TODO("Not yet implemented")
            }

            override fun setTurnRateOverride(turnRateOverride: Float?) {
                TODO("Not yet implemented")
            }

            override fun getGlowSpriteAPI(): SpriteAPI {
                TODO("Not yet implemented")
            }

            override fun getAmmoTracker(): AmmoTrackerAPI {
                TODO("Not yet implemented")
            }

            override fun setRefireDelay(delay: Float) {
                TODO("Not yet implemented")
            }

            override fun setFacing(facing: Float) {
                TODO("Not yet implemented")
            }

            override fun updateBeamFromPoints() {
                TODO("Not yet implemented")
            }

            override fun isKeepBeamTargetWhileChargingDown(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setKeepBeamTargetWhileChargingDown(keepTargetWhileChargingDown: Boolean) {
                TODO("Not yet implemented")
            }

            override fun setScaleBeamGlowBasedOnDamageEffectiveness(scaleGlowBasedOnDamageEffectiveness: Boolean) {
                TODO("Not yet implemented")
            }

            override fun setForceFireOneFrame(forceFire: Boolean) {
                TODO("Not yet implemented")
            }

            override fun setGlowAmount(glow: Float, glowColor: Color?) {
                TODO("Not yet implemented")
            }

            override fun setForceNoFireOneFrame(forceNoFireOneFrame: Boolean) {
                TODO("Not yet implemented")
            }

            override fun setSuspendAutomaticTurning(suspendAutomaticTurning: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getBurstFireTimeRemaining(): Float {
                TODO("Not yet implemented")
            }

            override fun getRenderOffsetForDecorativeBeamWeaponsOnly(): Vector2f {
                TODO("Not yet implemented")
            }

            override fun setRenderOffsetForDecorativeBeamWeaponsOnly(renderOffsetForDecorativeBeamWeaponsOnly: Vector2f?) {
                TODO("Not yet implemented")
            }

            override fun getRefireDelay(): Float {
                TODO("Not yet implemented")
            }

            override fun forceShowBeamGlow() {
                TODO("Not yet implemented")
            }

            override fun isInBurst(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getOriginalSpec(): WeaponSpecAPI {
                TODO("Not yet implemented")
            }

            override fun setWeaponGlowWidthMult(weaponGlowWidthMult: Float) {
                TODO("Not yet implemented")
            }

            override fun setWeaponGlowHeightMult(weaponGlowHeightMult: Float) {
                TODO("Not yet implemented")
            }

            override fun stopFiring() {
                TODO("Not yet implemented")
            }

        }
    }
}
