package exoticatechnologies.campaign.market

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.impl.campaign.ids.Strings
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import exoticatechnologies.modifications.ModSpecialItemPlugin
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.modifications.exotics.ExoticSpecialItemPlugin
import exoticatechnologies.modifications.upgrades.Upgrade
import exoticatechnologies.modifications.upgrades.UpgradeSpecialItemPlugin
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.exhaustive

object MarketMenu {

    fun open(market: MarketAPI, dialog: InteractionDialogAPI) {
        val marketData = MarketManager.getDataForMarket(market)

        dialog.showCargoPickerDialog(
                "Exotica Market",
                StringUtils.getString("Options", "confirm"),
                StringUtils.getString("Options", "cancel"),
                false,
                400f,
                marketData.cargo,
                MarketCargoListener(dialog, marketData, market)
        )
    }
}

class MarketCargoListener(val dialog: InteractionDialogAPI, val marketData: MarketData, val market: MarketAPI) : CargoPickerListener {
    val faction
        get() = marketData.faction

    override fun pickedCargo(cargo: CargoAPI) {
        val bounty: Float = computeCreditValue(cargo)
        if (Global.getSector().playerFleet.cargo.credits.get() < bounty) {
            // Return items back to the store
            val itemsList = deserializeCargoIntoItems(cargo)
            for (item in itemsList) {
                // pretty plaky but... it works.
                // Set the item to be returned depending on whether the item in the "purchase list" was
                // an Upgrade or Exotic item chip plugin
                val returnedItem: SpecialItemData = when (item) {
                    is UpgradeSpecialItemPlugin -> {
                        item.upgrade?.let {
                            Upgrade.getNewSpecialItemData(it.key, item.upgradeLevel)
                        } ?: throw IllegalArgumentException("Upgrade data is missing!")
                    }

                    is ExoticSpecialItemPlugin -> {
                        item.exoticData?.let {
                            Exotic.getNewSpecialItemData(it.key, it.type)
                        } ?: throw IllegalArgumentException("ExoticData data is missing!")
                    }

                    else -> {
                        // should never happen...
                        throw IllegalArgumentException("Invalid SpecialItemPlugin type!")
                    }
                }.exhaustive

                marketData.cargo.addSpecial(returnedItem, 1f)
            }

            // And finally return because we couldn't afford the new items and we returned them to the store.
            return
        }

        marketData.cargo.removeAll(cargo)
        Global.getSector().playerFleet.cargo.addAll(cargo)
        Global.getSector().playerFleet.cargo.credits.subtract(bounty)

        StringUtils.getTranslation("MarketMenu", "PurchasedText")
                .format("credits", Misc.getWithDGS(bounty) + Strings.C)
                .addToTextPanel(dialog.textPanel)
    }

    override fun cancelledCargoSelection() {
        //do nothing
    }

    override fun recreateTextPanel(
            panel: TooltipMakerAPI,
            cargo: CargoAPI,
            pickedUp: CargoStackAPI?,
            pickedUpFromSource: Boolean,
            combined: CargoAPI
    ) {
        panel.setParaFontOrbitron()
        panel.addPara(Misc.ucFirst(faction.displayName), faction.baseUIColor, 1f)
        panel.setParaFontDefault()

        panel.addImage(faction.logo, 310 * 1f, 3f)

        val playerCredits = Global.getSector().playerFleet.cargo.credits.get()
        StringUtils.getTranslation("MarketMenu", "CreditsText")
                .format("credits", Misc.getWithDGS(playerCredits) + Strings.C)
                .addToTooltip(panel)

        val purchasePrice: Float = computeCreditValue(combined)
        val canAfford = playerCredits >= purchasePrice

        if (canAfford) {
            StringUtils.getTranslation("MarketMenu", "MenuText")
                    .format("credits", Misc.getWithDGS(purchasePrice) + Strings.C)
                    .addToTooltip(panel)
        } else {
            StringUtils.getTranslation("MarketMenu", "MenuTextCannotAfford")
                    .format("credits", Misc.getWithDGS(purchasePrice) + Strings.C)
                    .addToTooltip(panel)
        }
    }

    protected fun deserializeCargoIntoItems(cargo: CargoAPI): List<ModSpecialItemPlugin> {
        val retVal = mutableListOf<ModSpecialItemPlugin>()
        for (stack in cargo.stacksCopy) {
            val plugin = stack.plugin
            // Since the stack.plugin is a SpecialItemPlugin, which is a superclass of the ModSpecialItemPlugin's
            // superclass, we just have to do it like this
            if (plugin is UpgradeSpecialItemPlugin) {
                retVal.add(plugin)
            } else if (plugin is ExoticSpecialItemPlugin) {
                retVal.add(plugin)
            }
        }

        return retVal
    }

    protected fun computeCreditValue(cargo: CargoAPI): Float {
        var bounty = 0f
        for (stack in cargo.stacksCopy) {
            val plugin = stack.plugin
            if (plugin is UpgradeSpecialItemPlugin) {
                bounty += plugin.calculateCreditCost(plugin.upgradeLevel)
            } else if (plugin is ExoticSpecialItemPlugin) {
                bounty += plugin.getBasePrice() * stack.size
            }
        }
        return bounty
    }
}