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
import exoticatechnologies.util.StringUtils
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
                    StringUtils.getTranslation(STRING_PARENT_KEY, "Title").toString(),
                    StringUtils.getTranslation(STRING_PARENT_KEY, "FleetPickerOKText").toString(),
                    StringUtils.getTranslation(STRING_PARENT_KEY, "FleetPickerCancelText").toString(),
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
            interactionDialog?.optionPanel?.addOption(
                    StringUtils.getTranslation(STRING_PARENT_KEY, "ConfirmationButtonYesText").toString(),
                    CONFIRM_YES_OPTION
            )
            interactionDialog?.optionPanel?.addOption(
                    StringUtils.getTranslation(STRING_PARENT_KEY, "ConfirmationButtonNoText").toString(),
                    CONFIRM_NO_OPTION
            )
            interactionDialog?.optionPanel?.addOptionConfirmation(
                    CONFIRM_YES_OPTION,
                    StringUtils.getTranslation(STRING_PARENT_KEY, "EstimatedCostText")
                            .format("price",membersPrices?.toFormattedString())
                            .toString(),
                    StringUtils.getTranslation(STRING_PARENT_KEY, "ConfirmationButtonYesText").toString(),
                    StringUtils.getTranslation(STRING_PARENT_KEY, "ConfirmationButtonNoText").toString(),
            )
            interactionDialog?.plugin = this
        } else {
            goBackToExoticaTechMenu()
        }
    }

    override fun cancelledFleetMemberPicking() {
        // Just go back to exotica tech menu
        goBackToExoticaTechMenu()
    }

    override fun init(dialog: InteractionDialogAPI?) { /* no op */ }

    override fun optionSelected(optionText: String?, optionData: Any?) {
        when (optionData) {
            CONFIRM_YES_OPTION -> {
                selectedMembers?.let {
                    performBFSUpgradeToMax(it)
                }
                goBackToExoticaTechMenu()
            }
            CONFIRM_NO_OPTION -> {
                goBackToExoticaTechMenu()
            }
        }
    }

    /**
     * Goes back to the "initial" planetside dialog menu
     */
    private fun goBackToInitialPlanetDialog() {
        // Programatically invoke the "back" option
        programaticallyInvokeOption(PLANETSIDE_ROOT_MAINMENU_KEY)
    }

    /**
     * Goes back to the "exotica technologies" dialog menu
     */
    private fun goBackToExoticaTechMenu() {
        // Programatically invoke the "exotica technologies" option
        programaticallyInvokeOption(EXOTICA_TECH_MAINMENU_KEY)
    }

    private fun programaticallyInvokeOption(optionData: String) {
        interactionDialog?.let { dialog ->
            originalInteractionPlugin?.let { originalPlugin ->
                dialog.plugin = originalPlugin
                originalPlugin.optionSelected("", optionData)
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
        private const val CONFIRM_YES_OPTION = "CONFIRM"
        private const val CONFIRM_NO_OPTION = "CANCEL"

        private const val STRING_PARENT_KEY = "MaxBandwidthDialog"
        private const val EXOTICA_TECH_MAINMENU_KEY = "ETMainMenu"
        private const val PLANETSIDE_ROOT_MAINMENU_KEY = "ETDialogBack"
    }
}
