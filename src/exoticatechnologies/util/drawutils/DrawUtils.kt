package exoticatechnologies.util.drawutils

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI
import exoticatechnologies.util.EngineParticlePainter
import exoticatechnologies.util.ParticleParams
import exoticatechnologies.util.ParticleType
import exoticatechnologies.util.drawutils.CircleUtils.Swirl
import exoticatechnologies.util.drawutils.LineUtils.StraightLine
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

object DrawUtils {

    fun drawPersistentLine(
            fromPoint: Vector2f,
            toPoint: Vector2f,
            segments: Int = 16,
            size: Float = 12f,
            brightness: Float = 1f,
            duration: Float = 1f,
            color: Color,
            combatEngine: CombatEngineAPI = Global.getCombatEngine()
    ) {
        val dx = (toPoint.x - fromPoint.x) / segments
        val dy = (toPoint.y - fromPoint.y) / segments
        var x = fromPoint.x
        var y = fromPoint.y
        for (i in 0..segments) {
            EngineParticlePainter.addParticle(
                engine = combatEngine,
                particleType = ParticleType.SMOOTH_PARTICLE,
                particleParams = ParticleParams.Smooth.Basic(
                    location = Vector2f(x, y),
                    velocity = Vector2f(0f, 0f),
                    size = size,
                    brightness = brightness,
                    duration = duration,
                    color = color
                )
            )
            x += dx
            y += dy
        }
    }

}


/**
 * Enum describing how particles should be drawn when using [Swirl.drawParticles] and [Swirl.SwirlArmParticles.draw]
 *
 * Works very identical for line-based [StraightLine.drawParticles] and [StraightLine.StraightLineParticles.draw]
 * and other line classes, except that unlike Swirl they have only one "arm" (line in their case)
 *
 * @see ONE_AT_A_TIME
 * @see WHOLE_ARM
 */
enum class ParticleDrawMode {
    /**
     * Enum signifying the "one dot at a time" mode, which draws one dot and removes it from the arm
     */
    ONE_AT_A_TIME,

    /**
     * Enum signifying the "whole arm at a time" mode, which draws the whole arm and removes a number of dots from it
     */
    WHOLE_ARM
}

/**
 * Enum class describing the "continuous drain" and how many particles should be pre-drained before drawing
 */
enum class ContinuousDrainMode {
    /**
     * Pre-drain based on drawing iteration, first draw iteration pre-draining 0, second draw-iteration pre-draining 1 and so on...
     */
    ITERATION_BASED_MODE,

    /**
     * Pre-drain based on previous arm's post-draw size, so that the next arm's draw state begins from the size of the last arm's drawing state
     */
    PREVIOUS_ARM_SIZE_MODE
}
