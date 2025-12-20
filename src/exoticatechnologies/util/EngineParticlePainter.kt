package exoticatechnologies.util

import com.fs.starfarer.api.combat.CombatEngineAPI
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

object EngineParticlePainter {
    fun addParticle(
        engine: CombatEngineAPI,
        particleType: ParticleType,
        location: Vector2f,
        velocity: Vector2f,
        particleSize: Float,
        particleBrightness: Float,
        particleDuration: Float,
        particleColor: Color
    ) {
//        engine.addSmoothParticle(
//            point,
//            Vector2f(0f, 0f),
//            particleSize,
//            particleBrightness,
//            particleDuration,
//            particleColor
//        )
        when(particleType) {
            ParticleType.HIT_PARTICLE -> {
                //TODO add capability for other two engine.addHitParticle methods
                engine.addHitParticle(location, velocity, particleSize, particleDuration, particleColor)
            }
            ParticleType.SMOOTH_PARTICLE -> {
                //TODO add capability for other two engine.addSmoothParticle methods
                engine.addSmoothParticle(location, velocity, particleSize, particleBrightness, particleDuration, particleColor)
            }
            ParticleType.SMOKE_PARTICLE -> {
                // There are no other engine.addSmokeParticle methods, this is fine
                engine.addSmokeParticle(location, velocity, particleSize, particleBrightness, particleDuration, particleColor)
            }
            ParticleType.NEGATIVE_PARTICLE -> {
                // There are no other engine.addNegativeParticle methods, this is fine
                engine.addNegativeParticle(
                    location,
                    velocity,
                    particleSize,
                    /* rampUpFraction ?!? */ particleBrightness,
                    particleDuration,
                    particleColor
                )
            }
            ParticleType.NEBULA_PARTICLE -> {
                //TODO add capability for other engine.addNebulaParticle method that has 'expandAsSqrt'
                engine.addNebulaParticle(
                    location,
                    velocity,
                    particleSize,
                    /* endSizeMult ?!? */ 0f,
                    /* rampSizeFraction ?!? */ 0f,
                    /* fullBrightnessFraction ?!? */0f,
                    particleDuration,
                    particleColor
                )
            }
            ParticleType.NEGATIVE_NEBULA_PARTICLE -> {
                // There are no other engine.addNegativeNebulaParticle methods, this is fine
                engine.addNegativeNebulaParticle(
                    location,
                    velocity,
                    particleSize,
                    /* endSizeMult ?!? */ 0f,
                    /* rampUpFraction ?!? */ 0f,
                    /* fullBrightnessFraction ?!? */ 0f,
                    particleDuration,
                    particleColor
                )
            }
            ParticleType.NEBULA_SMOKE_PARTICLE -> {
                // There are no other engine.addNebulaSmokeParticle methods, this is fine
                engine.addNebulaSmokeParticle(
                    location,
                    velocity,
                    particleSize,
                    /* endSizeMult ?!? */ 0f,
                    /* rampUpFraction ?!? */ 0f,
                    /* fullBrightnessFraction ?!? */ 0f,
                    particleDuration,
                    particleColor
                )
            }
            ParticleType.SWIRLY_NEBULA_PARTICLE -> {
                // There are no other engine.addSwirlyNebulaParticle methods, this is fine
                engine.addSwirlyNebulaParticle(
                    location,
                    velocity,
                    particleSize,
                    /* endSizeMult ?!? */ 0f,
                    /* rampUpFraction ?!? */ 0f,
                    /* fullBrightnessFraction ?!? */ 0f,
                    particleDuration,
                    particleColor,
                    /* expandAsSqrt ?!? */ false
                )
            }
            ParticleType.NEGATIVE_SWIRLY_NEBULA_PARTICLE -> {
                // There are no other engine.addNegativeSwirlyNebulaParticle methods, this is fine
                engine.addNegativeSwirlyNebulaParticle(
                    location,
                    velocity,
                    particleSize,
                    /* endSizeMult ?!? */ 0f,
                    /* rampUpFraction ?!? */ 0f,
                    /* fullBrightnessFraction ?!? */ 0f,
                    particleDuration,
                    particleColor
                )
            }
            ParticleType.NEBULA_SMOOTH_PARTICLE -> {
                //TODO add support for other engine.addNebulaSmooth() method that has 'expandAsSqrt'
                engine.addNebulaSmoothParticle(
                    location,
                    velocity,
                    particleSize,
                    /* endSizeMult ?!? */ 0f,
                    /* rampUpFraction ?!? */ 0f,
                    /* fullBrightnessFraction ?!? */ 0f,
                    particleDuration,
                    particleColor
                )
            }
        }.exhaustive
    }
}

enum class ParticleType {
    HIT_PARTICLE,
    SMOOTH_PARTICLE,
    SMOKE_PARTICLE,
    NEGATIVE_PARTICLE,
    NEBULA_PARTICLE,
    NEGATIVE_NEBULA_PARTICLE,
    NEBULA_SMOKE_PARTICLE,
    SWIRLY_NEBULA_PARTICLE,
    NEGATIVE_SWIRLY_NEBULA_PARTICLE,
    NEBULA_SMOOTH_PARTICLE
}
