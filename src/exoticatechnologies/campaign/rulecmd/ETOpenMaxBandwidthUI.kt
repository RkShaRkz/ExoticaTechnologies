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
import exoticatechnologies.util.getMods
import exoticatechnologies.util.toFormattedString

class ETOpenMaxBandwidthUI: BaseCommandPlugin(), FleetMemberPickerListener, InteractionDialogPlugin {

    private var interactionDialog: InteractionDialogAPI? = null
    private var interactionMarket: MarketAPI? = null
    private var selectedMembers: List<FleetMemberAPI>? = null
    private var originalInteractionPlugin: InteractionDialogPlugin? = null

    override fun execute(ruleId: String?, dialog: InteractionDialogAPI?, params: MutableList<Misc.Token>?, memoryMap: MutableMap<String, MemoryAPI>?): Boolean {
        interactionDialog = dialog
        originalInteractionPlugin = interactionDialog?.plugin

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
        if (members.isNullOrEmpty().not()) {
            interactionDialog?.optionPanel?.clearOptions()
            interactionDialog?.optionPanel?.addOption(CONFIRMATION_BUTTON_YES, CONFIRM_YES_OPTION)
            interactionDialog?.optionPanel?.addOption(CONFIRMATION_BUTTON_NO, CONFIRM_NO_OPTION)
            interactionDialog?.optionPanel?.addOptionConfirmation(
                    CONFIRM_YES_OPTION,
                    "Estimated price for maxing out these ships is: ${membersPrices?.toFormattedString()}",
                    CONFIRMATION_BUTTON_YES,
                    CONFIRMATION_BUTTON_NO
            )
            interactionDialog?.plugin = this
        } else {
            dismiss(true)
        }
    }

    override fun cancelledFleetMemberPicking() {
        // Do nothing
        dismiss(true)
    }

    override fun init(dialog: InteractionDialogAPI?) { /* no op */ }

    override fun optionSelected(optionText: String?, optionData: Any?) {
        when (optionData) {
            CONFIRM_YES_OPTION -> {
                selectedMembers?.let {
                    performBFSUpgradeToMax(it)
                }
                dismiss()
            }
            CONFIRM_NO_OPTION -> {
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
        // Assume we can't afford any ships
        var shouldProceed: Boolean
        do {
            // At start of each iteration, reset the 'shouldProceed' back to false, so that one 'true' doesn't end up
            // carrying us into a deadlock / infinite loop
            shouldProceed = false

            // Go through each member, and attempt upgrading. Note their upgrade result
            for (member in memberList) {
                val didMemberSuccessfullyUpgrade = if (BandwidthHandler.canUpgrade(member.getMods(), member)) {
                    // Can upgrade, return whether we can afford and upgrade was successful
                    BandwidthHandler.performNextBandwidthUpgrade(
                            member, member.getMods(), interactionMarket, member.variant, false
                    )
                } else {
                    // Can't upgrade, was not successful
                    false
                }
                // now perform an OR operation so that one 'true' can keep on carrying the do/while
                shouldProceed = shouldProceed or didMemberSuccessfullyUpgrade
            }
        } while (shouldProceed)
    }

    override fun optionMousedOver(optionText: String?, optionData: Any?) { /* no op */ }

    override fun advance(amount: Float) { /* no op */ }

    override fun backFromEngagement(battleResult: EngagementResultAPI?) { /* no op */ }

    override fun getContext(): Any { return this }

    override fun getMemoryMap(): MutableMap<String, MemoryAPI> { return mutableMapOf() }

    companion object {
        private const val CONFIRMATION_BUTTON_YES = "Do it!"
        private const val CONFIRMATION_BUTTON_NO = "NOPE!"

        private const val CONFIRM_YES_OPTION = "CONFIRM"
        private const val CONFIRM_NO_OPTION = "CANCEL"
    }
}
