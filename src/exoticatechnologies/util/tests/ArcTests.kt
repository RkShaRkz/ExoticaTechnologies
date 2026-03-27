package exoticatechnologies.util.tests

import exoticatechnologies.util.AngleDegreeType
import exoticatechnologies.util.drawutils.LineUtils
import exoticatechnologies.util.remapAngleToTrigonometricCoordinateSystem
import exoticatechnologies.util.tests.utils.ShipAPIUtils
import org.junit.Assert
import org.junit.Test
import org.lwjgl.util.vector.Vector2f

class ArcTests {
    @Test
    fun `check whether the example from documentation works and breaks as expected`() {
        val ourLocation = Vector2f(0f, 0f)
        val ourTrigonometricFacing = 90f
        // Generate a 1500-long 300-degree backwards arc
        val trigonometricAngleArc = LineUtils.generateArc(
            origin = ourLocation,
            facing = ourTrigonometricFacing,
            leftOffset = -30f,
            rightOffset = 30f,
            length = 1500f,
            degreeType = AngleDegreeType.TRIGONOMETRIC,
        )

        // Generate a target slightly in front of our origin
        val potentialTarget = Vector2f(0f, 10f)

        // Now, if my documentation is correct, the potential target will not be in trigonometric arc
        val isWithinTrigArc = trigonometricAngleArc.isWithinArc(
            target = ShipAPIUtils.createAnonymousShipAPI(location = potentialTarget)
        )
        val expectedWithinTrigArc = false

        Assert.assertEquals("Seems like my assumption was wrong, and TRIGONOMETRIC big-arc is not broken", expectedWithinTrigArc, isWithinTrigArc)

        // Since our facing is 'trigonometric', we should remap it to user-centric
        // because after we pass AngleDegreeType.USER_CENTRIC - then **all** angles are expected to be user-centric
        val userCentricFacing = remapAngleToTrigonometricCoordinateSystem(ourTrigonometricFacing)
        // Generate a 1500-long 60-degree forward arc
        val userCentricAngleArc = LineUtils.generateArc(
            origin = ourLocation,
            facing = userCentricFacing,
            leftOffset = -30f,
            rightOffset = 30f,
            length = 1500f,
            degreeType = AngleDegreeType.USER_CENTRIC,
        )

        // Now, if my documentation is correct, the potential target will not be in trigonometric arc
        val isWithinUserCentricArc = userCentricAngleArc.isWithinArc(
            target = ShipAPIUtils.createAnonymousShipAPI(location = potentialTarget)
        )
        val expectedWithinUserCentricArc = true
        Assert.assertEquals("Seems like my assumption was wrong, and USER_CENTRIC *is* broken", expectedWithinUserCentricArc, isWithinUserCentricArc)
    }

    @Test
    fun `verify that straightLineVisuals generates correct number of points by using the numPoints generating style`() {
        val start = Vector2f(0f, 0f)
        val end = Vector2f(0f, 4f)
        val expectedNumberOfPoints = 5

        val visualsList = LineUtils.generateStraightLineVisual(
            start = start,
            end = end,
            numPoints = expectedNumberOfPoints
        )

        // Now we should end up with 5 dots, at 0, 1, 2, 3, 4
        for (test in 0..4) {
            // Check each visualList's member and validate that they are indeed 0,1,2,3,4
            Assert.assertEquals("Seems like visuals aren't making proper vectors for numPoints", test.toFloat(), visualsList[test].y)
        }
        Assert.assertEquals("Seems like visuals aren't generating correct number of points for numPoints", expectedNumberOfPoints, visualsList.size)
    }

    @Test
    fun `verify that straightLineVisuals generates correct number of points with correct spacing by using the spacing generating style`() {
        val start = Vector2f(0f, 0f)
        val end = Vector2f(0f, 200f)
        val expectedNumberOfPoints = 5

        val visualsList = LineUtils.generateStraightLineVisual(
            start = start,
            end = end,
            spacing = 50f
        )

        // Now we should end up with 5 dots, at 0, 50, 100, 150, 200
        for (index in visualsList.indices) {
            Assert.assertEquals("Seems like visuals aren't making proper vectors for spacing", index*50f, visualsList[index].y)
        }
        Assert.assertEquals("Seems like visuals aren't generating correct number of points for spacing", expectedNumberOfPoints, visualsList.size)
    }

}
