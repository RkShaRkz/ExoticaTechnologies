package exoticatechnologies.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

/**
 * Singleton class to decouple starsector API interactions from other pieces of the code
 *
 * @see runningFromRefitScreen
 * @see setWorkingInTestMode
 */
object StarsectorAPIInteractor {

    private var IS_IN_TEST_MODE = false
    private var TEST_MODE_VALUE = false

    /**
     * Returns whether we're currently located in the Refit screen or not
     */
    fun runningFromRefitScreen(): Boolean {
        return if (IS_IN_TEST_MODE) {
            TEST_MODE_VALUE
        } else {
            actualStarsectorAPIrunningFromRefitScreen()
        }
    }

    private fun actualStarsectorAPIrunningFromRefitScreen(): Boolean {
        val runningFromRefitScreen = Global.getSector().campaignUI.currentCoreTab == CoreUITabId.REFIT
        return runningFromRefitScreen
    }

    /**
     * Method to set whether we're currently running in test mode, and additionally set the return value.
     * 
     * Obviously, super necessary since while we're running tests, all StarsectorAPI calls will simply return null.
     *
     * **NEVER USE THIS IN PRODUCTION CODE**
     */
    @VisibleForTesting
    @TestOnly
    fun setWorkingInTestMode(workingInTestMode: Boolean, isInRefitScreen: Boolean) {
        IS_IN_TEST_MODE = workingInTestMode
        TEST_MODE_VALUE = isInRefitScreen
    }
}
