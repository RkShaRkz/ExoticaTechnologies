package exoticatechnologies.util

import com.fs.starfarer.api.combat.CombatEngineAPI
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

/**
 * Wrapper class around `CombatEngineAPI.add<whatever>Particle` methods to decouple my code from directly invoking
 * StarSector API code and make it more testable. Also provides a single entry-point to all these various particle-drawing
 * methods via [addParticle]
 */
object EngineParticlePainter {

    /**
     * Method for adding particles. Depending on the [particleType], a different kind of [ParticleParams] has to be provided.
     *
     * @param engine the [CombatEngineAPI] instance to use for drawing particles
     * @param particleType the [ParticleType] to draw:
     *
     * - [ParticleType.HIT_PARTICLE] needs a [ParticleParams.Hit] (sub)class parameter instance
     * - [ParticleType.SMOOTH_PARTICLE] needs a [ParticleParams.Smooth] (sub)class parameter instance
     * - [ParticleType.SMOKE_PARTICLE] needs a [ParticleParams.Smoke] (sub)class parameter instance
     * - [ParticleType.NEGATIVE_PARTICLE] needs a [ParticleParams.Negative] (sub)class parameter instance
     * - [ParticleType.NEBULA_PARTICLE] needs a [ParticleParams.Nebula.Plain] (sub)class parameter instance
     * - [ParticleType.NEGATIVE_NEBULA_PARTICLE] needs a [ParticleParams.Nebula.Negative] (sub)class parameter instance
     * - [ParticleType.NEBULA_SMOKE_PARTICLE] needs a [ParticleParams.Nebula.Smoke] (sub)class parameter instance
     * - [ParticleType.SWIRLY_NEBULA_PARTICLE] needs a [ParticleParams.Nebula.Swirly.Plain] (sub)class parameter instance
     * - [ParticleType.NEGATIVE_SWIRLY_NEBULA_PARTICLE] needs a [ParticleParams.Nebula.Swirly.Negative] (sub)class parameter instance
     * - [ParticleType.NEBULA_SMOOTH_PARTICLE] needs a [ParticleParams.Nebula.Smooth] (sub)class parameter instance
     *
     * @param particleParams the [ParticleParams] containing data to use in drawing
     */
    fun addParticle(
        engine: CombatEngineAPI,
        particleType: ParticleType,
        particleParams: ParticleParams
    ) {
        when(particleType) {
            ParticleType.HIT_PARTICLE -> {
                if (particleParams !is ParticleParams.Hit) {
                    throw IllegalArgumentException("`particleParams` must be of ParticleParams.Hit (sub)type when drawing ParticleType.HIT_PARTICLE particles !!!")
                } else {
                    when(particleParams) {
                        is ParticleParams.Hit.NoDuration -> {
                            engine.addHitParticle(
                                particleParams.location,
                                particleParams.velocity,
                                particleParams.size,
                                particleParams.brightness,
                                particleParams.color
                            )
                        }
                        is ParticleParams.Hit.Basic -> {
                            engine.addHitParticle(
                                particleParams.location,
                                particleParams.velocity,
                                particleParams.size,
                                particleParams.brightness,
                                particleParams.duration,
                                particleParams.color
                            )
                        }
                        is ParticleParams.Hit.ComplexDuration -> {
                            engine.addHitParticle(
                                particleParams.location,
                                particleParams.velocity,
                                particleParams.size,
                                particleParams.brightness,
                                particleParams.durationIn,
                                particleParams.totalDuration,
                                particleParams.color
                            )
                        }
                    }.exhaustive
                }

            }
            ParticleType.SMOOTH_PARTICLE -> {
                if (particleParams !is ParticleParams.Smooth) {
                    throw IllegalArgumentException("`particleParams` must be of ParticleParams.Smooth (sub)type when drawing ParticleType.SMOOTH_PARTICLE particles !!!")
                } else {
                    when (particleParams) {
                        is ParticleParams.Smooth.Basic -> {
                            engine.addSmoothParticle(
                                particleParams.location,
                                particleParams.velocity,
                                particleParams.size,
                                particleParams.brightness,
                                particleParams.duration,
                                particleParams.color
                            )
                        }
                        is ParticleParams.Smooth.WithRamp -> {
                            engine.addSmoothParticle(
                                particleParams.location,
                                particleParams.velocity,
                                particleParams.size,
                                particleParams.brightness,
                                particleParams.rampUpFraction,
                                particleParams.totalDuration,
                                particleParams.color
                            )
                        }
                    }.exhaustive
                }
            }
            ParticleType.SMOKE_PARTICLE -> {
                // There are no other engine.addSmokeParticle methods, this is fine
                if (particleParams !is ParticleParams.Smoke) {
                    throw IllegalArgumentException("`particleParams` must be of ParticleParams.Smoke type when drawing ParticleType.SMOKE_PARTICLE particles !!!")
                } else {
                    engine.addSmokeParticle(
                        particleParams.location,
                        particleParams.velocity,
                        particleParams.size,
                        particleParams.opacity,
                        particleParams.duration,
                        particleParams.color
                    )
                }
            }

            ParticleType.NEGATIVE_PARTICLE -> {
                // There are no other engine.addNegativeParticle methods, this is fine
                if (particleParams !is ParticleParams.Negative) {
                    throw IllegalArgumentException("`particleParams` must be of ParticleParams.Negative type when drawing ParticleType.NEGATIVE_PARTICLE particles !!!")
                } else {
                    engine.addNegativeParticle(
                        particleParams.location,
                        particleParams.velocity,
                        particleParams.size,
                        particleParams.rampUpFraction,
                        particleParams.totalDuration,
                        particleParams.color
                    )
                }
            }
            ParticleType.NEBULA_PARTICLE -> {
                if (particleParams !is ParticleParams.Nebula.Plain) {
                    throw IllegalArgumentException("`particleParams` must be of ParticleParams.Nebula.Plain (sub)type when drawing ParticleType.NEBULA_PARTICLE particles !!!")
                } else {
                    when (particleParams) {
                        is ParticleParams.Nebula.Plain.Basic -> {
                            engine.addNebulaParticle(
                                particleParams.location,
                                particleParams.velocity,
                                particleParams.size,
                                particleParams.endSizeMult,
                                particleParams.rampUpFraction,
                                particleParams.fullBrightnessFraction,
                                particleParams.totalDuration,
                                particleParams.color
                            )
                        }
                        is ParticleParams.Nebula.Plain.Expansive -> {
                            engine.addNebulaParticle(
                                particleParams.location,
                                particleParams.velocity,
                                particleParams.size,
                                particleParams.endSizeMult,
                                particleParams.rampUpFraction,
                                particleParams.fullBrightnessFraction,
                                particleParams.totalDuration,
                                particleParams.color,
                                particleParams.expandAsSqrt
                            )
                        }
                    }.exhaustive
                }
            }
            ParticleType.NEGATIVE_NEBULA_PARTICLE -> {
                // There are no other engine.addNegativeNebulaParticle methods, this is fine
                if (particleParams !is ParticleParams.Nebula.Negative) {
                    throw IllegalArgumentException("`particleParams` must be of ParticleParams.Nebula.Negative type when drawing ParticleType.NEGATIVE_NEBULA_PARTICLE particles !!!")
                } else {
                    engine.addNegativeNebulaParticle(
                        particleParams.location,
                        particleParams.velocity,
                        particleParams.size,
                        particleParams.endSizeMult,
                        particleParams.rampUpFraction,
                        particleParams.fullBrightnessFraction,
                        particleParams.totalDuration,
                        particleParams.color
                    )
                }
            }
            ParticleType.NEBULA_SMOKE_PARTICLE -> {
                // There are no other engine.addNebulaSmokeParticle methods, this is fine
                if (particleParams !is ParticleParams.Nebula.Smoke) {
                    throw IllegalArgumentException("`particleParams` must be of ParticleParams.Nebula.Smoke type when drawing ParticleType.NEGATIVE_SMOKE_PARTICLE particles !!!")
                } else {
                    engine.addNebulaSmokeParticle(
                        particleParams.location,
                        particleParams.velocity,
                        particleParams.size,
                        particleParams.endSizeMult,
                        particleParams.rampUpFraction,
                        particleParams.fullBrightnessFraction,
                        particleParams.totalDuration,
                        particleParams.color
                    )
                }
            }
            ParticleType.SWIRLY_NEBULA_PARTICLE -> {
                // There are no other engine.addSwirlyNebulaParticle methods, this is fine
                if (particleParams !is ParticleParams.Nebula.Swirly.Plain) {
                    throw IllegalArgumentException("`particleParams` must be of ParticleParams.Nebula.Swirly.Plain type when drawing ParticleType.SWIRLY_NEBULA_PARTICLE particles !!!")
                } else {
                    engine.addSwirlyNebulaParticle(
                        particleParams.location,
                        particleParams.velocity,
                        particleParams.size,
                        particleParams.endSizeMult,
                        particleParams.rampUpFraction,
                        particleParams.fullBrightnessFraction,
                        particleParams.totalDuration,
                        particleParams.color,
                        particleParams.expandAsSqrt
                    )
                }
            }
            ParticleType.NEGATIVE_SWIRLY_NEBULA_PARTICLE -> {
                // There are no other engine.addNegativeSwirlyNebulaParticle methods, this is fine
                if (particleParams !is ParticleParams.Nebula.Swirly.Negative) {
                    throw IllegalArgumentException("`particleParams` must be of ParticleParams.Nebula.Swirly.Negative type when drawing ParticleType.SWIRLY_NEBULA_PARTICLE particles !!!")
                } else {
                    engine.addNegativeSwirlyNebulaParticle(
                        particleParams.location,
                        particleParams.velocity,
                        particleParams.size,
                        particleParams.endSizeMult,
                        particleParams.rampUpFraction,
                        particleParams.fullBrightnessFraction,
                        particleParams.totalDuration,
                        particleParams.color
                    )
                }
            }
            ParticleType.NEBULA_SMOOTH_PARTICLE -> {
                if (particleParams !is ParticleParams.Nebula.Smooth) {
                    throw IllegalArgumentException("`particleParams` must be of ParticleParams.Nebula.Smooth (sub)type when drawing ParticleType.NEBULA_SMOOTH_PARTICLE particles !!!")
                } else {
                    when (particleParams) {
                        is ParticleParams.Nebula.Smooth.Expansive -> {
                            engine.addNebulaSmoothParticle(
                                particleParams.location,
                                particleParams.velocity,
                                particleParams.size,
                                particleParams.endSizeMult,
                                particleParams.rampUpFraction,
                                particleParams.fullBrightnessFraction,
                                particleParams.totalDuration,
                                particleParams.color,
                                particleParams.expandAsSqrt
                            )
                        }
                        is ParticleParams.Nebula.Smooth.Plain -> {
                            engine.addNebulaSmoothParticle(
                                particleParams.location,
                                particleParams.velocity,
                                particleParams.size,
                                particleParams.endSizeMult,
                                particleParams.rampUpFraction,
                                particleParams.fullBrightnessFraction,
                                particleParams.totalDuration,
                                particleParams.color
                            )
                        }
                    }.exhaustive
                }
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

/**
 * General upper-level sealed class of all shared parameters for all particle families
 *
 * @param location the [Vector2f] location where the particle should spawn
 * @param velocity the [Vector2f] describing its velocity
 * @param size the size of the particle
 * @param color the color of the particle
 *
 * @see Hit
 * @see Smooth
 * @see Smoke
 * @see Negative
 * @see Nebula
 */
sealed class ParticleParams(
    open val location: Vector2f,
    open val velocity: Vector2f,
    open val size: Float,
    open val color: Color
) {

    /**
     * Sealed class encapsulating all subclasses relevant to all [CombatEngineAPI.addHitParticle] methods
     *
     * @param brightness the brightness of the particle
     *
     * @see Basic
     * @see NoDuration
     * @see ComplexDuration
     */
    sealed class Hit(
        override val location: Vector2f,
        override val velocity: Vector2f,
        override val size: Float,
        override val color: Color,
        open val brightness: Float
    ) : ParticleParams(location, velocity, size, color) {

        /**
         * Class describing the [CombatEngineAPI.addHitParticle] with [duration] parameter
         *
         * @param duration the duration (lifespan) of the particle
         */
        data class Basic(
            override val location: Vector2f,
            override val velocity: Vector2f,
            override val size: Float,
            override val color: Color,
            override val brightness: Float,
            val duration: Float
        ) : Hit(location, velocity, size, color, brightness)

        /**
         * Class describing the [CombatEngineAPI.addHitParticle] without `duration` parameter
         */
        data class NoDuration(
            override val location: Vector2f,
            override val velocity: Vector2f,
            override val size: Float,
            override val color: Color,
            override val brightness: Float
        ) : Hit(location, velocity, size, color, brightness)

        /**
         * Class describing the [CombatEngineAPI.addHitParticle] without [durationIn] and [totalDuration] parameters
         */
        data class ComplexDuration(
            override val location: Vector2f,
            override val velocity: Vector2f,
            override val size: Float,
            override val color: Color,
            override val brightness: Float,
            val durationIn: Float,
            val totalDuration: Float
        ) : Hit(location, velocity, size, color, brightness)
    }

    /**
     * Sealed class encapsulating all subclasses relevant to all [CombatEngineAPI.addSmoothParticle] methods
     *
     * @param brightness the brightness of the particle
     *
     * @see Basic
     * @see WithRamp
     */
    sealed class Smooth(
        override val location: Vector2f,
        override val velocity: Vector2f,
        override val size: Float,
        override val color: Color,
        open val brightness: Float
    ) : ParticleParams(location, velocity, size, color) {

        /**
         * Class describing the [CombatEngineAPI.addSmoothParticle] with [duration] parameter
         *
         * @param duration the duration (lifespan) of the particle
         */
        data class Basic(
            override val location: Vector2f,
            override val velocity: Vector2f,
            override val size: Float,
            override val color: Color,
            override val brightness: Float,
            val duration: Float
        ) : Smooth(location, velocity, size, color, brightness)

        /**
         * Class describing the [CombatEngineAPI.addSmoothParticle] with [rampUpFraction] and [totalDuration] parameters
         *
         * @param rampUpFraction fraction of particle's lifespan that is spent ramping up to full brightness
         * @param totalDuration the duration (lifespan) of the particle
         */
        data class WithRamp(
            override val location: Vector2f,
            override val velocity: Vector2f,
            override val size: Float,
            override val color: Color,
            override val brightness: Float,
            val rampUpFraction: Float,
            val totalDuration: Float
        ) : Smooth(location, velocity, size, color, brightness)
    }

    /**
     * Class describing the [CombatEngineAPI.addSmokeParticle] method
     *
     * @param opacity the opacity of the particle
     * @param duration the duration (lifespan) of the particle
     */
    data class Smoke(
        override val location: Vector2f,
        override val velocity: Vector2f,
        override val size: Float,
        override val color: Color,
        val opacity: Float,
        val duration: Float
    ) : ParticleParams(location, velocity, size, color)

    /**
     * Class describing the [CombatEngineAPI.addNegativeParticle] method
     *
     * @param rampUpFraction fraction of particle's lifespan that is spent ramping up to full brightness
     * @param totalDuration the duration (lifespan) of the particle
     */
    data class Negative(
        override val location: Vector2f,
        override val velocity: Vector2f,
        override val size: Float,
        override val color: Color,
        val rampUpFraction: Float,
        val totalDuration: Float
    ) : ParticleParams(location, velocity, size, color)

    /**
     * Class describing the [CombatEngineAPI.addNebulaParticle], [CombatEngineAPI.addNebulaSmoothParticle],
     * [CombatEngineAPI.addNebulaSmokeParticle], [CombatEngineAPI.addSwirlyNebulaParticle], [CombatEngineAPI.addNegativeNebulaParticle],
     * [CombatEngineAPI.addNegativeSwirlyNebulaParticle] methods.
     *
     * @param endSizeMult size multiplier applied to particle size at end of duration (lifespan)
     * @param rampUpFraction fraction of particle's lifespan that is spent ramping up to full brightness
     * @param fullBrightnessFraction fraction of particle's lifespan that is spent at peak (full) brightness
     * @param totalDuration the duration (lifespan) of the particle
     *
     * @see Plain
     * @see Negative
     * @see Smoke
     * @see Swirly
     * @see Smooth
     */
    sealed class Nebula(
        override val location: Vector2f,
        override val velocity: Vector2f,
        override val size: Float,
        override val color: Color,
        open val endSizeMult: Float,
        open val rampUpFraction: Float,
        open val fullBrightnessFraction: Float,
        open val totalDuration: Float
    ) : ParticleParams(location, velocity, size, color) {

        /**
         * Class describing the [CombatEngineAPI.addNebulaParticle] methods' parameters
         *
         * @see Basic
         * @see Expansive
         */
        sealed class Plain(
            override val location: Vector2f,
            override val velocity: Vector2f,
            override val size: Float,
            override val color: Color,
            override val endSizeMult: Float,
            override val rampUpFraction: Float,
            override val fullBrightnessFraction: Float,
            override val totalDuration: Float
        ) : Nebula(location, velocity, size, color, endSizeMult, rampUpFraction, fullBrightnessFraction, totalDuration) {

            /**
             * Class describing the [CombatEngineAPI.addNebulaParticle] without `expandAsSqrt` parameter
             */
            data class Basic(
                override val location: Vector2f,
                override val velocity: Vector2f,
                override val size: Float,
                override val color: Color,
                override val endSizeMult: Float,
                override val rampUpFraction: Float,
                override val fullBrightnessFraction: Float,
                override val totalDuration: Float
            ): Plain(location, velocity, size, color, endSizeMult, rampUpFraction, fullBrightnessFraction, totalDuration)

            /**
             * Class describing the [CombatEngineAPI.addNebulaParticle] with [expandAsSqrt] parameter
             *
             * @param expandAsSqrt Whether the expansion curve should follow sqrt rather than liner. If [true] follows sqrt, otherwise linear
             */
            data class Expansive(
                override val location: Vector2f,
                override val velocity: Vector2f,
                override val size: Float,
                override val color: Color,
                override val endSizeMult: Float,
                override val rampUpFraction: Float,
                override val fullBrightnessFraction: Float,
                override val totalDuration: Float,
                val expandAsSqrt: Boolean
            ): Plain(location, velocity, size, color, endSizeMult, rampUpFraction, fullBrightnessFraction, totalDuration)
        }

        /**
         * Class describing the [CombatEngineAPI.addNegativeNebulaParticle] method's parameters
         */
        data class Negative(
            override val location: Vector2f,
            override val velocity: Vector2f,
            override val size: Float,
            override val color: Color,
            override val endSizeMult: Float,
            override val rampUpFraction: Float,
            override val fullBrightnessFraction: Float,
            override val totalDuration: Float
        ) : Nebula(location, velocity, size, color, endSizeMult, rampUpFraction, fullBrightnessFraction, totalDuration)

        /**
         * Class describing the [CombatEngineAPI.addNebulaSmokeParticle] method's parameters
         */
        data class Smoke(
            override val location: Vector2f,
            override val velocity: Vector2f,
            override val size: Float,
            override val color: Color,
            override val endSizeMult: Float,
            override val rampUpFraction: Float,
            override val fullBrightnessFraction: Float,
            override val totalDuration: Float
        ) : Nebula(location, velocity, size, color, endSizeMult, rampUpFraction, fullBrightnessFraction, totalDuration)

        /**
         * Class describing the [CombatEngineAPI.addSwirlyNebulaParticle] and [CombatEngineAPI.addNegativeSwirlyNebulaParticle] methods' parameters
         *
         * @see Plain
         * @see Negative
         */
        sealed class Swirly(
            override val location: Vector2f,
            override val velocity: Vector2f,
            override val size: Float,
            override val color: Color,
            override val endSizeMult: Float,
            override val rampUpFraction: Float,
            override val fullBrightnessFraction: Float,
            override val totalDuration: Float,
        ) : Nebula(location, velocity, size, color, endSizeMult, rampUpFraction, fullBrightnessFraction, totalDuration) {

            /**
             * Class describing the [CombatEngineAPI.addSwirlyNebulaParticle] method's parameters
             *
             * @param expandAsSqrt Whether the expansion curve should follow sqrt rather than liner. If [true] follows sqrt, otherwise linear
             */
            data class Plain(
                override val location: Vector2f,
                override val velocity: Vector2f,
                override val size: Float,
                override val color: Color,
                override val endSizeMult: Float,
                override val rampUpFraction: Float,
                override val fullBrightnessFraction: Float,
                override val totalDuration: Float,
                val expandAsSqrt: Boolean
            ) : Swirly(location, velocity, size, color, endSizeMult, rampUpFraction, fullBrightnessFraction, totalDuration)

            /**
             * Class describing the [CombatEngineAPI.addNegativeSwirlyNebulaParticle] method's parameters
             */
            data class Negative(
                override val location: Vector2f,
                override val velocity: Vector2f,
                override val size: Float,
                override val color: Color,
                override val endSizeMult: Float,
                override val rampUpFraction: Float,
                override val fullBrightnessFraction: Float,
                override val totalDuration: Float
            ) : Swirly(location, velocity, size, color, endSizeMult, rampUpFraction, fullBrightnessFraction, totalDuration)
        }

        /**
         * Class describing the [CombatEngineAPI.addNebulaSmoothParticle] methods' parameters
         *
         * @see Plain
         * @see Expansive
         */
        sealed class Smooth(
            override val location: Vector2f,
            override val velocity: Vector2f,
            override val size: Float,
            override val color: Color,
            override val endSizeMult: Float,
            override val rampUpFraction: Float,
            override val fullBrightnessFraction: Float,
            override val totalDuration: Float,
        ) : Nebula(location, velocity, size, color, endSizeMult, rampUpFraction, fullBrightnessFraction, totalDuration) {

            /**
             * Class describing the [CombatEngineAPI.addNebulaSmoothParticle] without `expandAsSqrt` parameter
             */
            data class Plain(
                override val location: Vector2f,
                override val velocity: Vector2f,
                override val size: Float,
                override val color: Color,
                override val endSizeMult: Float,
                override val rampUpFraction: Float,
                override val fullBrightnessFraction: Float,
                override val totalDuration: Float,
            ) : Smooth(location, velocity, size, color, endSizeMult, rampUpFraction, fullBrightnessFraction, totalDuration)

            /**
             * Class describing the [CombatEngineAPI.addNebulaSmoothParticle] with [expandAsSqrt] parameter
             *
             * @param expandAsSqrt Whether the expansion curve should follow sqrt rather than liner. If [true] follows sqrt, otherwise linear
             */
            data class Expansive(
                override val location: Vector2f,
                override val velocity: Vector2f,
                override val size: Float,
                override val color: Color,
                override val endSizeMult: Float,
                override val rampUpFraction: Float,
                override val fullBrightnessFraction: Float,
                override val totalDuration: Float,
                val expandAsSqrt: Boolean = false
            ) : Smooth(location, velocity, size, color, endSizeMult, rampUpFraction, fullBrightnessFraction, totalDuration)
        }
    }
}
