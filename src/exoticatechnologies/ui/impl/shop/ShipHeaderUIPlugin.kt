package exoticatechnologies.ui.impl.shop

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.Misc
import exoticatechnologies.modifications.ShipModLoader
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.bandwidth.Bandwidth
import exoticatechnologies.modifications.bandwidth.BandwidthHandler
import exoticatechnologies.modifications.bandwidth.BandwidthHandler.BandwidthUpgradeResult
import exoticatechnologies.modifications.bandwidth.BandwidthUtil
import exoticatechnologies.refit.RefitButtonAdder
import exoticatechnologies.ui.InteractiveUIPanelPlugin
import exoticatechnologies.ui.StringTooltip
import exoticatechnologies.util.AnonymousLogger
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.toFormattedString
import kotlin.math.max
import kotlin.math.min

class ShipHeaderUIPlugin(
    dialog: InteractionDialogAPI?,
    var member: FleetMemberAPI,
    var variant: ShipVariantAPI,
    var mods: ShipModifications,
    var parentPanel: CustomPanelAPI
) : InteractiveUIPanelPlugin() {
    private val pad = 3f
    private val opad = 10f

    override var panelWidth: Float = Global.getSettings().screenHeight * 0.65f
    override var panelHeight: Float = max(100f, Global.getSettings().screenHeight * 0.166f)

    var market: MarketAPI? = dialog?.interactionTarget?.market
    var lastValue: Float = -1f
    var lastCredits: Float = -1f

    var shipImgTooltip: TooltipMakerAPI? = null

    var shipTextTooltip: TooltipMakerAPI? = null
    var shipBandwidthText: LabelAPI? = null
    var placeHelpTextNextToThisComponent: UIComponentAPI? = null
    var usedBandwidthText: LabelAPI? = null
    var bandwidthHelpText: LabelAPI? = null

    var bandwidthTooltip: TooltipMakerAPI? = null
    var bandwidthUpgradeLabel: LabelAPI? = null
    var bandwidthButton: ButtonAPI? = null
    var maxBandwidthButton: ButtonAPI? = null

    override fun advancePanel(amount: Float) {
        if (mods.getValue() != lastValue) {
            lastValue = mods.getValue()
            setBandwidthText()
        }

        if (lastCredits != Global.getSector().playerFleet.cargo.credits.get()) {
            lastCredits = Global.getSector().playerFleet.cargo.credits.get()
            setBandwidthUpgradeLabel()
        }
    }

    fun layoutPanel(tooltip: TooltipMakerAPI): CustomPanelAPI {
        val shipNameColor = member.captain.faction.baseUIColor

        val panel: CustomPanelAPI =
            parentPanel.createCustomPanel(panelWidth, panelHeight, this)

        // Ship image with tooltip of the ship class
        val shipImg = panel.createUIElement(iconSize, iconSize, false)
        shipImgTooltip = shipImg
        shipImg.addShipList(1, 1, iconSize, Misc.getBasePlayerColor(), mutableListOf(member), 0f)
        panel.addUIElement(shipImg).inTL(0f, 0f)

        // Ship name, class, bandwidth
        val shipText = panel.createUIElement(panelWidth - iconSize, 22f * 3f, false)
        shipTextTooltip = shipText
        shipText.setParaOrbitronLarge()
        shipText.addPara("${member.shipName} (${member.hullSpec.nameWithDesignationWithDashClass})", shipNameColor, 0f)
        shipText.setParaFontDefault()

        shipBandwidthText = shipText.addPara("", 0f)
        usedBandwidthText = shipText.addPara("", 0f)
        placeHelpTextNextToThisComponent = shipText.prev

        bandwidthHelpText = shipText.addPara("[?]", Misc.getDarkHighlightColor(), 3f)
        val bandwidthHelpString = StringUtils.getTranslation("BandwidthDialog", "BandwidthHelp")
            .format("bandwidthLimit", BandwidthUtil.getFormattedBandwidth(Bandwidth.MAX_BANDWIDTH))
            .toStringNoFormats()

        shipText.addTooltipToPrevious(
            StringTooltip(shipText, bandwidthHelpString),
            TooltipMakerAPI.TooltipLocation.BELOW
        )

        setBandwidthText()

        panel.addUIElement(shipText).rightOfTop(shipImg, pad)

        val bandwidthHeight = panelHeight - shipText.position.height
        bandwidthTooltip = panel.createUIElement(panelWidth - iconSize, bandwidthHeight, false)

        bandwidthUpgradeLabel = bandwidthTooltip?.addPara("", 0f)
        bandwidthButton = bandwidthTooltip?.addButton(
            StringUtils.getString("BandwidthDialog", "BandwidthPurchase"), "test",
            Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.C2_MENU, 72F, 22F, 3F
        )
        maxBandwidthButton = bandwidthTooltip?.addButton(
            StringUtils.getString("BandwidthDialog", "MaxBandwidthPurchase"), "test",
            Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.C2_MENU, 106F, 22F, 3F
        )

        // Add the button handler to the "purchase" button
        bandwidthButton?.let {
            buttons[it] = BandwidthButtonHandler(this)
        } ?: throw IllegalStateException("bandwidthButton should not have been null in ShipHeaderUIPlugin !!!")

        // Add the button handler to the "purchase max" button
        maxBandwidthButton?.let { nonNullMaxBandwidth ->
            // Set the "max bandwidth" button to the right of "bandwidth" button, 10px away
            bandwidthButton?.let { nonNullBandwidth ->
                nonNullMaxBandwidth.position?.rightOfMid(nonNullBandwidth, 10f)
            }

            buttons[nonNullMaxBandwidth] = MaxBandwidthButtonHandler(this)
        } ?: throw IllegalStateException("maxBandwidthButton should not have been null in ShipHeaderUIPlugin !!!")

        setBandwidthUpgradeLabel()

        panel.addUIElement(bandwidthTooltip).belowLeft(shipText, pad)


        // done, add row to TooltipMakerAPI
        pos = tooltip.addCustom(panel, opad).position

        return panel
    }

    private fun setBandwidthText() {
        val baseBandwidth = mods.getBaseBandwidth(member)
        val bandwidthWithExotics = mods.getBandwidthWithExotics(member)
        val usedBandwidth = mods.getUsedBandwidth()

        if (baseBandwidth != bandwidthWithExotics) {
            //something installed that increases bandwidth. bandwidth value is exotic bandwidth
            StringUtils.getTranslation("CommonOptions", "BandwidthWithExoticsForShip")
                .format(
                    "shipBandwidth",
                    BandwidthUtil.getFormattedBandwidth(baseBandwidth),
                    Bandwidth.getColor(baseBandwidth)
                )
                .format(
                    "exoticBandwidth",
                    "+" + BandwidthUtil.getFormattedBandwidth(bandwidthWithExotics - baseBandwidth),
                    Misc.getPositiveHighlightColor()
                )
                .setLabelText(shipBandwidthText)
        } else {
            StringUtils.getTranslation("CommonOptions", "BandwidthForShip")
                .format(
                    "shipBandwidth",
                    BandwidthUtil.getFormattedBandwidth(baseBandwidth),
                    Bandwidth.getColor(baseBandwidth)
                )
                .setLabelText(shipBandwidthText)
        }

        StringUtils.getTranslation("CommonOptions", "UsedBandwidthForShip")
            .format(
                "usedBandwidth",
                BandwidthUtil.getFormattedBandwidth(usedBandwidth),
                Misc.getHighlightColor()
            )
            .format(
                "allBandwidth",
                BandwidthUtil.getFormattedBandwidthWithName(bandwidthWithExotics),
                Bandwidth.getColor(bandwidthWithExotics)
            )
            .setLabelText(usedBandwidthText)

        bandwidthHelpText?.let {
            it.position
                .belowRight(placeHelpTextNextToThisComponent!!, 3f)
                .setXAlignOffset(4 + it.computeTextWidth(usedBandwidthText!!.text))
                .setYAlignOffset(it.computeTextHeight(usedBandwidthText!!.text))
        }
    }

    private fun setBandwidthUpgradeLabel() {
        if (market == null) {
            StringUtils.getTranslation("CommonOptions", "MustBeDockedAtMarket")
                .setLabelText(bandwidthUpgradeLabel)
            bandwidthUpgradeLabel?.setColor(Misc.getNegativeHighlightColor())
            bandwidthButton?.isEnabled = false
            maxBandwidthButton?.isEnabled = false
            return
        }

        bandwidthUpgradeLabel?.let {
            if (BandwidthHandler.canUpgrade(mods, member).not()) {
                // If can't upgrade anymore, then change the label that we reached max
                modifyBandwidthUpgradeLabel(it, -1f, -1f, "BandwidthDialog", "BandwidthUpgradePeak")
                bandwidthButton?.isEnabled = false
                maxBandwidthButton?.isEnabled = false
            } else {
                val marketMult = BandwidthHandler.getMarketBandwidthMult(market)
                val upgradePrice = BandwidthHandler.getBandwidthUpgradePrice(member, mods.getBaseBandwidth(), marketMult)
                val newBandwidth = Bandwidth.BANDWIDTH_STEP * marketMult

                if (BandwidthHandler.isAbleToPayForBandwidthUpgrade(Global.getSector().playerFleet, upgradePrice).not()) {
                    // If can't afford upgrade, change the label to say so
                    modifyBandwidthUpgradeLabel(
                        it,
                        newBandwidth,
                        upgradePrice,
                        "BandwidthDialog",
                        "BandwidthUpgradeCostCannotAfford"
                    )
                    bandwidthButton?.isEnabled = false
                    maxBandwidthButton?.isEnabled = false
                } else {
                    // Otherwise, make it state the cost
                    modifyBandwidthUpgradeLabel(
                        it,
                        newBandwidth,
                        upgradePrice,
                        "BandwidthDialog",
                        "BandwidthUpgradeCost"
                    )
                    bandwidthButton?.isEnabled = true
                    maxBandwidthButton?.isEnabled = true
                }
            }
        }
    }

    private fun modifyBandwidthUpgradeLabel(
        label: LabelAPI,
        bandwidth: Float,
        upgradePrice: Float,
        translationParent: String,
        translationKey: String
    ) {
        val creditsText = Misc.getDGSCredits(upgradePrice)
        StringUtils.getTranslation(translationParent, translationKey)
            .format("bonusBandwidth", BandwidthUtil.getFormattedBandwidth(bandwidth))
            .format("costCredits", creditsText)
            .format("credits", Misc.getDGSCredits(Global.getSector().playerFleet.cargo.credits.get()))
            .setAdaptiveHighlights()
            .setLabelText(label)
    }

    private fun doBandwidthUpgrade() {
        BandwidthHandler.performNextBandwidthUpgrade(member, mods, market, variant, false)

        if (Global.getSector().campaignUI.currentCoreTab == CoreUITabId.REFIT) {
            RefitButtonAdder.requiresVariantUpdate = true
        }

        Global.getSoundPlayer().playUISound("ui_char_increase_skill_new", 1f, 0.75f)
    }

    private fun doMaxBandwidthUpgrade() {
        // TODO get rid of this - just log prognosed value
        val prognosedMaxPrice = BandwidthHandler.getCostPrognosisToMaxBandwidth(member, market)         //TODO delete
        AnonymousLogger.log("cost prognis to max out member ${member.shipName} is: ${prognosedMaxPrice.toFormattedString()}")   //TODO delete
        val resultList = mutableListOf<BandwidthUpgradeResult>();   //TODO delete

        // While we have money and bandwidth can be upgraded, just roll this and play the sound at the end
        while (BandwidthHandler.isAbleToPayForNextBandwidthUpgrade(member, mods, market) && BandwidthHandler.canUpgrade(mods, member)) {
            val upgradeResult = BandwidthHandler.performNextBandwidthUpgrade(member, mods, market, variant, false)
            resultList.add(upgradeResult)   //TODO delete

            if (Global.getSector().campaignUI.currentCoreTab == CoreUITabId.REFIT) {
                RefitButtonAdder.requiresVariantUpdate = true
            }

            Global.getSoundPlayer().playUISound("ui_char_increase_skill_new", 1f, 0.75f)
        }
        val priceList = resultList.map { item -> item.upgradeCost } //TODO delete
    }

    fun bandwidthButtonClicked() {
        bandwidthButton?.isChecked = false
        doBandwidthUpgrade()
        setBandwidthUpgradeLabel()
    }

    fun maxBandwidthButtonClicked() {
        maxBandwidthButton?.isChecked = false
        doMaxBandwidthUpgrade()
        setBandwidthUpgradeLabel()
    }
}
