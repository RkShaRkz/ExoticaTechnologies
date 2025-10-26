package exoticatechnologies.campaign.rulecmd

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.FleetMemberPickerListener
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.InteractionDialogPlugin
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin
import com.fs.starfarer.api.util.Misc
import exoticatechnologies.modifications.bandwidth.BandwidthHandler
import exoticatechnologies.util.AnonymousLogger
import exoticatechnologies.util.getMods
import exoticatechnologies.util.toFormattedString

class ETOpenMaxBandwidthUI: BaseCommandPlugin(), FleetMemberPickerListener, InteractionDialogPlugin {

    private var interactionDialog: InteractionDialogAPI? = null
    private var interactionMarket: MarketAPI? = null
    private var selectedMembers: List<FleetMemberAPI>? = null
    private var originalInteractionPlugin: InteractionDialogPlugin? = null

    override fun execute(ruleId: String?, dialog: InteractionDialogAPI?, params: MutableList<Misc.Token>?, memoryMap: MutableMap<String, MemoryAPI>?): Boolean {
        interactionDialog = dialog

        val market = dialog?.interactionTarget?.market

        return if (market == null) {
            // IF no market, do nothing
            false
        } else {
            interactionMarket = market
            // Otherwise, grab player's ships, and show the picker dialog
            val playerShipPool = Global.getSector()
                    .playerFleet.membersWithFightersCopy
                    .filter { fmapi -> fmapi.isFighterWing.not() }

            dialog.showFleetMemberPickerDialog(
                    "Select members whose Bandwidth should be maxed",
                    "Max these",
                    "No thanks",
                    6,
                    6,
                    64f,
                    true,
                    true,
                    playerShipPool,
                    this
            )

            return true
        }
    }

    override fun pickedFleetMembers(members: MutableList<FleetMemberAPI>?) {
        AnonymousLogger.log("Picked fleet members: ${members}", "FleetPickerDialogListener")
        selectedMembers = members
        val membersPrices = members?.let {
            return@let it.map { item ->
                BandwidthHandler.getCostPrognosisToMaxBandwidth(
                        item,
                        item.getMods(),
                        interactionMarket
                )
            }.sum()
        }
        interactionDialog?.optionPanel?.clearOptions()
        interactionDialog?.optionPanel?.addOption(CONFIRMATION_BUTTON_YES, CONFIRM_YES_OPTION)
        interactionDialog?.optionPanel?.addOption(CONFIRMATION_BUTTON_NO, CONFIRM_NO_OPTION)
        interactionDialog?.optionPanel?.addOptionConfirmation(
                CONFIRM_YES_OPTION,
                "Estimated price for maxing out these ships is: ${membersPrices?.toFormattedString()}",
                CONFIRMATION_BUTTON_YES,
                CONFIRMATION_BUTTON_NO
        )
        originalInteractionPlugin = interactionDialog?.plugin
        interactionDialog?.plugin = this
    }

    override fun cancelledFleetMemberPicking() {
        // Do nothing
        AnonymousLogger.log("Cancelled fleet picker dialog", "FleetPickerDialogListener")
        dismiss(true)
    }

    override fun init(dialog: InteractionDialogAPI?) {
        AnonymousLogger.log("--> init()")
    }

    override fun optionSelected(optionText: String?, optionData: Any?) {
        when (optionData) {
            CONFIRM_YES_OPTION -> {
                AnonymousLogger.log("'CONFIRM' HEARD IN optionSelected()!\tthis is CONFIRM_YES")
                selectedMembers?.let {
                    performBFSUpgradeToMax(it)
                }
                dismiss()
            }
            CONFIRM_NO_OPTION -> {
                AnonymousLogger.log("'CANCEL' HEARD IN optionSelected()!\tthis is CONFIRM_NO")
                dismiss(true)
            }
        }
    }

    private fun dismiss(cancel: Boolean = false) {
        interactionDialog?.let {
            it.plugin = originalInteractionPlugin
            if (cancel) {
                it.dismissAsCancel()
            } else {
                it.dismiss()
            }
        }
    }

    private fun performBFSUpgradeToMax(memberList: List<FleetMemberAPI>) {
        //TODO BFS-improve all ships until:
        // 1) you either run out of money by all of them not being able to afford next upgrade
        // 2) all of them are already upgraded
        var successfulUpgrades = 0  //TODO remove
        val successfullyUpgradedMembers = mutableSetOf<FleetMemberAPI>()

        // Assume we can't afford any ships
        var shouldProceed: Boolean
        do {
            // At start of each iteration, reset the 'shouldProceed' back to false, so that one 'true' doesn't end up
            // carrying us into a deadlock / infinite loop
            shouldProceed = false

            // Go through each member, and attempt upgrading. Note their upgrade result
            for (member in memberList) {
                val didMemberSuccessfullyUpgrade = BandwidthHandler.performNextBandwidthUpgrade(
                        member, member.getMods(), interactionMarket, member.variant, false
                )
                if (didMemberSuccessfullyUpgrade) { successfulUpgrades++; successfullyUpgradedMembers.add(member) } //TODO remove
                // now perform an OR operation so that one 'true' can keep on carrying the do/while
                shouldProceed = shouldProceed or didMemberSuccessfullyUpgrade
            }
        } while (shouldProceed)

        AnonymousLogger.log("Successfully upgraded bandwidth levels: ${successfulUpgrades} on successfully upgraded members: ${successfullyUpgradedMembers.size}")//TODO remove
    }

    override fun optionMousedOver(optionText: String?, optionData: Any?) {
//        TODO("Not yet implemented")
    }

    override fun advance(amount: Float) {
//        TODO("Not yet implemented")
    }

    override fun backFromEngagement(battleResult: EngagementResultAPI?) {
//        TODO("Not yet implemented")
    }

    override fun getContext(): Any {
//        TODO("Not yet implemented")
        return this
    }

    override fun getMemoryMap(): MutableMap<String, MemoryAPI> {
//        TODO("Not yet implemented")
        return mutableMapOf()
    }

    companion object {
        private const val CONFIRMATION_BUTTON_YES = "Do it!"
        private const val CONFIRMATION_BUTTON_NO = "NOPE!"

        private const val CONFIRM_YES_OPTION = "CONFIRM"
        private const val CONFIRM_NO_OPTION = "CANCEL"
    }
}
