package exoticatechnologies.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.OptionPanelAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.util.MutableValue
import com.fs.starfarer.campaign.fleet.CampaignFleet
import com.fs.starfarer.campaign.fleet.FleetData
import com.fs.starfarer.campaign.fleet.FleetMember
import exoticatechnologies.util.datastructures.Optional
import exoticatechnologies.util.reflect.ReflectionUtils
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
     * Check whether we're running from "Exotica Technologies" screen
     */
    fun runningFromExoticaTechnologiesScreen(): Boolean {
        // This one will just naively rely on the fact that we have options showing in the background
        val optional = getSectorHasOptionsOptional()
        // If optional is empty, bail out
        if (optional.isEmpty()) return false
        // Proceed otherwise
        val hasOptions = optional.isPresent() && optional.get()
        return hasOptions
    }

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
        // Refit screen is going to be on the REFIT core UI tab and won't have options
        val optional = getSectorHasOptionsOptional()
        // If empty, bail out
        if (optional.isEmpty()) return false
        // Otherwise proceed
        val hasOptions = optional.isPresent() && optional.get()
        val runningFromRefitScreen = Global.getSector().campaignUI.currentCoreTab == CoreUITabId.REFIT
        return runningFromRefitScreen && hasOptions.not()
    }

    /**
     * Yucky dumb method that reaches through to [Global.getSector().campaignUI.currentInteractionDialog.optionPanel.hasOptions()]
     * in a very null-safe and secure way
     *
     * @return if everything is non-null, it returns the [Optional] of final [InteractionDialogAPI]'s [OptionPanelAPI.hasOptions],
     * otherwise it returns [Optional.empty]
     */
    private fun getSectorHasOptionsOptional(): Optional<Boolean> {
        return if (Global.getSector().campaignUI != null) {
            if (Global.getSector().campaignUI.currentInteractionDialog != null) {
                if (Global.getSector().campaignUI.currentInteractionDialog.optionPanel != null) {
                    Optional.of(Global.getSector().campaignUI.currentInteractionDialog.optionPanel.hasOptions())
                } else {
                    Optional.empty()
                }
            } else {
                Optional.empty()
            }
        } else {
            Optional.empty()
        }
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

    @Synchronized
    fun getMembersFleetCredits(member: FleetMemberAPI): MutableValue {
        return synchronized(StarsectorAPIInteractor.javaClass) {
            member.fleetData.fleet.cargo.credits
        }
    }

    fun promoteFleetDatasOnPlayerShipsIfNotAlreadyPromoted() {
        // get player's fleet
//        val originalFleetData = Global.getSector().playerFleet.fleetData
        val campaignFleet = Global.getSector().playerFleet as CampaignFleet
        val originalFleetData = campaignFleet.fleetData
        val newFleetData = ETFleetData(originalFleetData)
        ReflectionUtils.copyAllFields(originalFleetData, newFleetData)
        ReflectionUtils.set("fleetData", campaignFleet, newFleetData)
        val campaignFleet2 = Global.getSector().playerFleet as CampaignFleet


        // promote each fleetmember's fleetdata if it's not already promoted
//        for (member in Global.getSector().playerFleet.membersWithFightersCopy) {
        val playerFleet = Global.getSector().playerFleet as CampaignFleet
        for (member in playerFleet.fleetData.sortedMembers) {
            AnonymousLogger.log("member.fleetData !is ETFleetData ? ${member.fleetData !is ETFleetData}")
            AnonymousLogger.log("member.fleetData is ETFleetData ? ${member.fleetData is ETFleetData}")
            AnonymousLogger.log("member.fleetData is FleetData ? ${member.fleetData is FleetData}")
            if (member.fleetData !is ETFleetData) {
                // Only promote vanilla (non-ETFleetData) fleet datas
                AnonymousLogger.log("Promoting ${member.shipName} fleet data to ETFleetData")
                val fm = member as FleetMember
                if (member.fleetData != null) {
                    val originalFleetMembersFleetData = fm.fleetData
                    fm.fleetData = ETFleetData(member.fleetData)
                    val newFleetMembersFleetData = fm.fleetData
                    AnonymousLogger.log("originalFleetData: ${originalFleetMembersFleetData}, newFleetData: ${newFleetMembersFleetData}")
                    ReflectionUtils.copyAllFields(originalFleetMembersFleetData, fm.fleetData)
                    AnonymousLogger.log("AFTER COPY originalFleetData: ${originalFleetMembersFleetData}, newFleetData: ${newFleetMembersFleetData}")
                } else {
                    AnonymousLogger.log("Encountered member with NULL fleetdata! member: "+member)
                }
                AnonymousLogger.log("Promoted ${member.shipName} fleet data to ETFleetData")
//                member.fleetData?.let { fleetData ->
//                    fm.fleetData = ETFleetData(fleetData)
//                }

            }
        }
    }
}
