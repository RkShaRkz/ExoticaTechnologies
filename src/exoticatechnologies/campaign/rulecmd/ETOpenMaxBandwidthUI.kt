package exoticatechnologies.campaign.rulecmd

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.FleetMemberPickerListener
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin
import com.fs.starfarer.api.util.Misc
import exoticatechnologies.modifications.ShipModLoader
import exoticatechnologies.modifications.bandwidth.BandwidthHandler
import exoticatechnologies.util.AnonymousLogger

class ETOpenMaxBandwidthUI: BaseCommandPlugin(), FleetMemberPickerListener {

    private var interactionDialog: InteractionDialogAPI? = null
    private var interactionMarket: MarketAPI? = null

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
        val membersPrices = members?.let {
            return@let it.map { item ->
                BandwidthHandler.getCostPrognosisToMaxBandwidth(
                        item,
                        ShipModLoader.get(item, item.getVariant()),
                        interactionMarket
                )
            }.sum()
        }
        interactionDialog?.optionPanel?.clearOptions();
//        interactionDialog?.optionPanel?.addOption("Yes", "CONFIRM");
//        interactionDialog?.optionPanel?.addOption("No", "CANCEL");
        interactionDialog?.optionPanel?.addOptionConfirmation("CONFIRM", "Estimated price for maxing out these ships is: ${membersPrices}", "Do it!", "NOPE!")
//        interactionDialog?.showCon
    }

    override fun cancelledFleetMemberPicking() {
        // Do nothing
        AnonymousLogger.log("Cancelled fleet picker dialog", "FleetPickerDialogListener")
    }

//    override fun optionSelected
}
