package exoticatechnologies.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

/**
 * Generates a list of equidistant points along a vector.
 *
 * **NOTE:** Either [numPoints] or [spacing] must be provided.
 *
 * @param start The origin point of the line.
 * @param end The end of the line
 * @param numPoints Optional: If provided, generates exactly this many points.
 * @param spacing Optional: If provided, calculates the best fit for this spacing.
 * @return A list of [Vector2f] points along the line.
 *
 * @throws IllegalArgumentException when both [numPoints] and [spacing] are null
 */
fun generateStraightLineVisual(
    start: Vector2f,
    end: Vector2f, // Use the end vector instead of angle/length
    numPoints: Int? = null,
    spacing: Float? = null
): List<Vector2f> {

    // 1. do validation first
    if (numPoints == null && spacing == null) {
        throw IllegalArgumentException("Must provide either 'numPoints' or 'spacing' for point generation.")
    }

    val totalLength = start.distanceTo(end)

    // 2. Determine the number of points (n)
    val n: Int = when {
        numPoints != null -> numPoints.coerceAtLeast(1)
        spacing != null -> {
            // Calculate how many segments fit into the length
            // By using round() we ensure we don't 'lose' length; we just adjust spacing slightly.
            // So that it becomes "at least" and not "exactly"
            val segments = Math.round(totalLength / spacing).coerceAtLeast(1).toInt()

            // And return the number of points, which is always (segments + 1)
            segments + 1
        }
        else -> 0
    }

    // 3. Generate points using Linear Interpolation
    val points = mutableListOf<Vector2f>()

    for (i in 0 until n) {
        // Calculate the interpolation fraction t (0.0 to 1.0)
        // If n is 1, t is 0 (just the start point).
        val t = if (n > 1) { i.toFloat() / (n - 1).toFloat() } else { 0f }
        val currentDist = t * totalLength

        // Now perform the *actual* lerping which is faster than by using getPointOnCircumference(...)
        val x = start.x + (end.x - start.x) * t
        val y = start.y + (end.y - start.y) * t

        // and add it to the list of current points we've accumulated
        points.add(Vector2f(x, y))
    }

    // After the lerp-ing finishes, just return these points
    return points
}



data class ArcSelection(
    val origin: Vector2f,
    val leftVec: Vector2f,  // Normalized vector for left boundary
    val rightVec: Vector2f, // Normalized vector for right boundary
    val length: Float
) {

    /**
     * Checks if a ship is within the cone.
     * @param target the target [ShipAPI] to check
     * @return whether the [target] is within this arc or not
     */
    fun isWithinArc(target: ShipAPI): Boolean {
        val targetPos = target.location
        val toTarget = targetPos.sub(origin)

        // 1. Distance check
        val distSq = toTarget.lengthSquared()
        if (distSq > length * length) return false

        // 2. Angular check using 2D cross product
        // Check whether the target is clockwise of the 'left' vector and counterclockwise of the 'right' vector
        val isTargetCWOfLeftVector = leftVec.crossProduct(toTarget) < 0
        val isTargetCCWOfRightVector = rightVec.crossProduct(toTarget) > 0

        return isTargetCWOfLeftVector && isTargetCCWOfRightVector
    }

    /**
     * Iterates over all ships in the combat engine and runs logic if they are in the arc.
     */
    fun forEveryShipInArc(shipsToCheck: List<ShipAPI>, action: (ShipAPI) -> Unit) {
        for (ship in shipsToCheck) {
            if (isWithinArc(ship)) {
                action(ship)
            }
        }
    }

    /**
     * Draws X dots along the left and right boundary vectors.
     */
    fun draw(
        numDots: Int,
        color: Color
    ) {
        for (i in 0..numDots) {
            val t = i.toFloat() / numDots.toFloat()
            val currentDist = t * length

            // Calculate dot positions
            val dotLeft = Vector2f(origin.x + leftVec.x * currentDist, origin.y + leftVec.y * currentDist)
            val dotRight = Vector2f(origin.x + rightVec.x * currentDist, origin.y + rightVec.y * currentDist)

            //TODO lets actually make the Arc contain only two StraightLines and just call into their draw(...) here
        }
    }

    /**
     * Visualizes the boundaries using particles.
     */
    fun drawParticles(numParticles: Int?, spacing: Int?) {
        // Throw if both arguments are null
        if (numParticles == null && spacing == null) {
            throw IllegalArgumentException("Must use either a fixed number of particles or dynamic via spacing. numParticles and spacing cannot both be null")
        }
        // Prefer numParticles over spacing
        numParticles?.let { particleCount ->
            for (i in 0..particleCount) {
                val t = i.toFloat() / particleCount.toFloat()
                val d = t * length

                val pLeft = Vector2f(origin.x + leftVec.x * d, origin.y + leftVec.y * d)
                val pRight = Vector2f(origin.x + rightVec.x * d, origin.y + rightVec.y * d)

//                engine.addHitParticle(pLeft, Vector2f(), 5f, 1f, 0.1f, Color.CYAN)
//                engine.addHitParticle(pRight, Vector2f(), 5f, 1f, 0.1f, Color.CYAN)
                //TODO this will just call into both StraightLines' drawParticles(...)
            }
        }

        // Check spacing too
        spacing?.let {
            val steps = (length / spacing).toInt()
            // Now start iteratively calculating the particle's distance
            for (i in 0..steps) {
                // Calculate distance for this specific particle
                val currentDist = i * spacing

                // Boundary positions
                val pLeft = Vector2f(
                    origin.x + leftVec.x * currentDist,
                    origin.y + leftVec.y * currentDist
                )
                val pRight = Vector2f(
                    origin.x + rightVec.x * currentDist,
                    origin.y + rightVec.y * currentDist
                )

                //TODO this doesn't make sense...
                // we should use either 'numParticles' or 'spacing' when generating StraightLines and not within this draw
            }
        }
    }
}

data class StraightLineInfo(
    val length: Float,
    //TODO replace these two with a new Angle class, which has 'degrees' and 'degreeType'
    // and offers the whole toRadians(), toDegrees() and so on...
    val angle: Float,
    val degreeType: AngleDegreeType
)

fun generateStraightLine(
    start: Vector2f,
    end: Vector2f? = null,
    info: StraightLineInfo? = null,
    generateParticles: Boolean = false,
    particleSegments: Int?,
    particleSpacing: Float?,
    particleDrawInterval: Float = 0.1f
): StraightLine {
    // Lets take a look at our options...
    // We will cover 3 cases:
    // - when 'end' is non-null
    // - when 'info' is non-null
    // - when they're both non-null, we will prioritize 'end'
    // - otherwise, throw IllegalArgumentException
    val bothNonNull = end != null && info != null
    val endNonNull = end != null

    return when {
        bothNonNull || endNonNull -> {
            val delta = Vector2f.sub(end, start, Vector2f())
            val dist = delta.length()
            // get angles in TRIGONOMETRIC (engine) sense
            val angle = delta.getFacing()

            // And return the StraightLine
            StraightLine(
                start = start,
                end = end!!,
                length = dist,
                generateParticles = generateParticles,
                particleSegments = particleSegments,
                particleSpacing = particleSpacing,
                particleDrawInterval = particleDrawInterval,
                trigonometricAngle = angle
            )
        }

        info != null -> {
            // Convert the input angle to the system the engine understands
            val standardizedAngle = when(info.degreeType) {
                AngleDegreeType.USER_CENTRIC -> {
                    // User centric needs to be remapped
                    remapAngleToTrigonometricCoordinateSystem(info.angle)
                }
                AngleDegreeType.TRIGONOMETRIC -> {
                    // trigonometric doesn't need remapping
                    info.angle
                }
            }.exhaustive

            // Calculate end point
            val calculatedEnd = getPointOnCircumference(
                start,
                info.length,
                standardizedAngle
            )

            // And return the straight line
            StraightLine(
                start = start,
                end = calculatedEnd,
                length = info.length,
                generateParticles = generateParticles,
                particleSegments = particleSegments,
                particleSpacing = particleSpacing,
                particleDrawInterval = particleDrawInterval,
                trigonometricAngle = standardizedAngle
            )
        }
        else -> {
            throw IllegalArgumentException("Cannot generate StraightLine when both 'end' and 'info' are null!!!")
        }
    }
}

class StraightLine(
    private val start: Vector2f,
    private val end: Vector2f,
    private val length: Float,
    private val generateParticles: Boolean,
    private val particleSegments: Int?,
    private val particleSpacing: Float?,
    private val particleDrawInterval: Float,
    private val trigonometricAngle: Float
) {
    private val particlePoints: StraightLineParticles?
    private val intervalUtil: MultiIntervalUtil
    private var drawIterations: Int = 0
    private var previousLinePostDrawIndex: Int = 0

    init {
        particlePoints = if (generateParticles) {
            // prepare VisualLine's particle list
            val straightLineParticles = StraightLineParticles()
            // Move on to further generate the whole line
            val linePoints = generateStraightLineVisual(
                start = start,
                end = end,
                // The number of points should always be the number of segments + 1
                numPoints = particleSegments?.let { it + 1 },
                spacing = particleSpacing
            )
            straightLineParticles.addMorePoints(linePoints)

            // And finally, return the list of StraightLineParticles
            straightLineParticles
        } else {
            // If we should not generate particles, just return null
            null
        }

        // After generating the particles, instantiate the intervalUtil
        intervalUtil = MultiIntervalUtil(particleDrawInterval)
    }

    fun draw(numDots: Int, color: Color) {
        for (i in 0..numDots) {
            val t = i.toFloat() / numDots.toFloat()
            val pos = Vector2f(
                start.x + (end.x - start.x) * t,
                start.y + (end.y - start.y) * t
            )
            // Draw logic...
        }
    }

    /**
     * Method that draws particles over time, and should be called repeatedly until all arms are finished.
     *
     * @param amount the amount of time that has passed, used to feed into [intervalUtil]
     * @param particleSize the particle size
     * @param particleDuration the lifetime of each particle
     * @param particlesToDrawPerInterval depending on the [particleDrawMode] it will either:
     * - For [ParticleDrawMode.ONE_AT_A_TIME] it will call [SwirlArmParticles.draw] this many times
     * - For [ParticleDrawMode.WHOLE_ARM] it will call [SwirlArmParticles.draw] followed by [SwirlArmParticles.removePoints] with this number
     * @param particleColors a **non-empty** list of [Color]s to use for the particles depending on which segment of the arm they belong to.
     * Needs at least one color, **list cannot be empty**
     * @param particleDrawMode the [ParticleDrawMode] to use
     * @param particleArmsToDraw how many swirl arms to draw, relevant only when [particleDrawMode] is [ParticleDrawMode.WHOLE_ARM]
     * @param continuousDrain optional parameter determining whether to pre-drain arms before drawing. See [ContinuousDrainMode]
     */
    fun drawParticles(
        amount: Float,
        particleSize: Float = 12f,
        particleDuration: Float = 0.25f,
        particlesToDrawPerInterval: Int = 1,
        particleColors: List<Color>,
        particleDrawMode: CircleUtils.ParticleDrawMode,     //TODO extract to DrawUtils
        continuousDrain: CircleUtils.ContinuousDrainMode?   //TODO extract to drawutils
    ) {
        // Validate color list
        if (particleColors.isEmpty()) throw IllegalArgumentException("particleColors list must NOT be empty")
        // Drawing particles is rather simple. Feed the amount into the interval util, if amount has passed -
        // call draw on each SwirlArmParticles instance. They will automatically remove the drawn point.
        intervalUtil.advance(amount)
        intervalUtil.onIntervalElapsed {
            // Depending on the particle draw mode, we will either:
            // draw 'particlesToDrawPerInterval' points from each arm at the same time
            // draw whole arms one at a time and remove points from them until they all drain out
            when(particleDrawMode) {
                CircleUtils.ParticleDrawMode.ONE_AT_A_TIME -> {
                    particlePoints?.let { lineParticles ->
                        // Pre-drain if we should
                        continuousDrain?.let {
                            when(it) {
                                CircleUtils.ContinuousDrainMode.ITERATION_BASED_MODE -> {
                                    lineParticles.removePoints(drawIterations)
                                }
                                CircleUtils.ContinuousDrainMode.PREVIOUS_ARM_SIZE_MODE -> {
                                    lineParticles.removePoints(previousLinePostDrawIndex)
                                }
                            }.exhaustive
                        }

                        // If we should draw more particles, do so
                        repeat(particlesToDrawPerInterval) {
                            // decode color based on where we are in the list
                            val colorIndex = lineParticles.getCurrentPointIndex() / lineParticles.getLinePointsSize()
                            val color = if (colorIndex < particleColors.size) {
                                particleColors[colorIndex]
                            } else {
                                particleColors.last()
                            }

                            // And finally draw the arm
                            lineParticles.draw(
                                particleSize = particleSize,
                                particleDuration = particleDuration,
                                particleColors = listOf(color),
                                particleDrawMode = particleDrawMode
                            )

                            // It makes sense to do this in the 'repeat' after drawing, because if we drew only one point
                            // we want the next arm to pre-drain one point;
                            // If we drew 5 points, we want the next arm to pre-drain 5 points before drawing as well.
                            drawIterations++

                            // And return nothing
                            Unit
                        }
                        previousLinePostDrawIndex = lineParticles.getCurrentPointIndex()
                    }
                }

                CircleUtils.ParticleDrawMode.WHOLE_ARM -> {
                    // For whole arm, we do not need to decode the color to use, the arm will do that itself.
                    // Idea is, we will keep on drawing arms one at a time, or many at a time depending on particleArmsToDraw
                    particlePoints?.let { lineParticles ->
//                        val stride = ceil(
//                            particlePoints.size.toDouble() / particleArmsToDraw
//                        ).toInt()

                            // calculate proper index, deducting so it goes in the right direction but i guess it doesn't matter
//                            val armIndex = wrapAroundMod(lastDrawnArm - i * stride, particlePoints.size)

                            // Pre-drain if we should (will drain once on a per-arm basis)
                            continuousDrain?.let {
                                when (it) {
                                    CircleUtils.ContinuousDrainMode.ITERATION_BASED_MODE -> {   //TODO extract the ContinuousDrainMode
                                        particlePoints.removePoints(drawIterations)
                                    }

                                    CircleUtils.ContinuousDrainMode.PREVIOUS_ARM_SIZE_MODE -> {
                                        particlePoints.removePoints(previousLinePostDrawIndex)
                                    }
                                }.exhaustive
                            }

                            // draw the arm
                            particlePoints.draw(
                                particleSize = particleSize,
                                particleDuration = particleDuration,
                                particleColors = particleColors,
                                particleDrawMode = particleDrawMode
                            )
                            // deduct points from that arm
                            particlePoints.removePoints(particlesToDrawPerInterval)

                        // And bump the drawIterations and previousArmPostDrawIndex
                        drawIterations++
                        previousLinePostDrawIndex = particlePoints.getCurrentPointIndex()

                        // And return nothing
                        Unit
                    }
                }
            }.exhaustive
        }
    }

    inner class StraightLineParticles(
        private val particlePoints: MutableList<Vector2f> = mutableListOf()
    ) {
        private var linePointsSize: Int = 0
        private var isFinished = false

        fun draw(
            particleSize: Float = 12f,
            particleDuration: Float = 0.25f,
            particleColors: List<Color>,
            particleBrightness: Float = 1f,
            particleDrawMode: CircleUtils.ParticleDrawMode  //TODO extract this to DrawUtils
        ) {
            if (particlePoints.isNotEmpty()) {
                when(particleDrawMode) {
                    CircleUtils.ParticleDrawMode.ONE_AT_A_TIME -> {
                        val point = particlePoints.removeAt(0)
                        val engine = Global.getCombatEngine()
                        val particleColor = particleColors.first()
                        EngineParticlePainter.addParticle(
                            engine = engine,
                            particleType = ParticleType.SMOOTH_PARTICLE,
                            particleParams = ParticleParams.Smooth.Basic(
                                location = point,
                                velocity = Vector2f(0f, 0f),
                                size = particleSize,
                                brightness = particleBrightness,
                                duration = particleDuration,
                                color = particleColor
                            )
                        )
                    }
                    CircleUtils.ParticleDrawMode.WHOLE_ARM -> {
                        // Fetch the original smooth particle limit
                        val engine = Global.getCombatEngine()
                        for (point in particlePoints) {
                            val colorIndex = getCurrentPointIndex() / getLinePointsSize()
                            val color = if (colorIndex < particleColors.size) {
                                particleColors[colorIndex]
                            } else {
                                particleColors.last()
                            }

                            EngineParticlePainter.addParticle(
                                engine = engine,
                                particleType = ParticleType.SMOOTH_PARTICLE,
                                particleParams = ParticleParams.Smooth.Basic(
                                    location = point,
                                    velocity = Vector2f(0f, 0f),
                                    size = particleSize,
                                    brightness = particleBrightness,
                                    duration = particleDuration,
                                    color = color
                                )
                            )
                        }
                    }
                }.exhaustive
            } else {
                isFinished = true
            }
        }

        // adding more points should just add them
        fun addMorePoints(points: List<Vector2f>) {
            particlePoints.addAll(points)
            linePointsSize += points.size
        }

        /**
         * Method for returning the "line size". This is **not** the number of points remaining in the line.
         * This is just a hacky representation of how many points the [particlePoints] contained after we added
         * all points to it.
         *
         * @return the initial size of this [StraightLineParticles] collection before we started draining it via [draw]
         */
        fun getLinePointsSize(): Int {
            return linePointsSize
        }

        /**
         * Gets the current point "index" or rather the difference between [linePointsSize] and size of [particlePoints]
         *
         * Better call this before [draw]ing because drawing will mutate it.
         *
         * @return the "next drawing point" index
         */
        fun getCurrentPointIndex(): Int {
            return getLinePointsSize() - particlePoints.size
        }

        /**
         * Method that checks whether this particle arm has finished or not.
         * A particle arm is considered "finished" when it has exhausted all of it's points
         *
         * @return whether this arm has finished or not
         */
        fun hasFinished(): Boolean {
            return isFinished
        }

        /**
         * Method that removes [pointsToRemove] first points from this arm.
         * If the method tries to remove more points than we have in [particlePoints] it will remove as many
         * as it can and then do nothing.
         */
        fun removePoints(pointsToRemove: Int) {
            repeat(pointsToRemove) {
                if (particlePoints.isNotEmpty()) {
                    particlePoints.removeAt(0)
                }
            }
        }
    }
}
