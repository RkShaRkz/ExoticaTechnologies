package exoticatechnologies.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.CombatEntityAPI
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
