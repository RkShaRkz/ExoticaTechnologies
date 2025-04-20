package exoticatechnologies.ui.impl.shop.exotics

import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.*
import exoticatechnologies.cargo.CrateItemPlugin
import exoticatechnologies.modifications.Modification
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticSpecialItemPlugin
import exoticatechnologies.modifications.exotics.ExoticsHandler
import exoticatechnologies.ui.ButtonHandler
import exoticatechnologies.ui.InteractiveUIPanelPlugin
import exoticatechnologies.ui.StringTooltip
import exoticatechnologies.ui.impl.shop.exotics.methods.ExoticMethod
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.runningFromExoticaTechnologiesScreen
import exoticatechnologies.util.runningFromRefitScreen
import org.apache.log4j.Logger
import java.awt.Color

class ExoticMethodsUIPlugin(
    var parentPanel: CustomPanelAPI,
    var exotic: Exotic,
    var member: FleetMemberAPI,
    var variant: ShipVariantAPI,
    var mods: ShipModifications,
    var market: MarketAPI?
) : InteractiveUIPanelPlugin() {
    private var mainPanel: CustomPanelAPI? = null
    private var methodsTooltip: TooltipMakerAPI? = null
    private var listeners: MutableList<Listener> = mutableListOf()
    private var cannotApplyLabelList: MutableList<LabelAPI> = mutableListOf()

    fun layoutPanels(): CustomPanelAPI {
        val panel = parentPanel.createCustomPanel(panelWidth, panelHeight, this)
        mainPanel = panel

        showTooltip()
        parentPanel.addComponent(panel).inBR(0f, 0f)

        return panel
    }

    fun showTooltip() {
        val tooltip = mainPanel!!.createUIElement(panelWidth, panelHeight, false)
        methodsTooltip = tooltip

        var prev: UIComponentAPI? = null
        if (mods.hasExotic(exotic)) {
            tooltip.addTitle(StringUtils.getString("ExoticsDialog", "InstalledTitle"))
        } else if (!exotic.canApply(member, mods)) {
            if (exotic.conditionsDisjunct) {
                val titleString = StringUtils
                        .getTranslation("Conditions", "CannotApplyTitleDisjunct")
                        .format("minConditions", exotic.conditionsNecessaryToSatify.toString())
                        .format("maxConditions", exotic.conditions.size.toString())
                        .toString()
                tooltip.addTitle(titleString, Color(200, 100, 100))
            } else {
                tooltip.addTitle(StringUtils.getString("Conditions", "CannotApplyTitle"), Color(200, 100, 100))
            }
            showCannotApply(mods, tooltip)

            prev = tooltip.prev
        } else if (exotic.showWarningIfApplyingFromRefitScreen() && runningFromExoticaTechnologiesScreen().not()) {
            tooltip.addTitle(StringUtils.getString("ExoticsDialog", "DontDoFromRefitScreen"), Color(200, 50, 0))
        } else if (!isUnderExoticLimit(member, mods)) {
            tooltip.addTitle(StringUtils.getString("Conditions", "CannotApplyTitle"), Color(200, 100, 100))

            StringUtils.getTranslation("Conditions", "CannotApplyBecauseTooManyExotics")
                .addToTooltip(tooltip, Color(100, 200, 100))

            prev = tooltip.prev
        } else {
            tooltip.addTitle(StringUtils.getString("UpgradeMethods", "UpgradeMethodsTitle"))
        }

        showMethods(tooltip, mods, prev)

        mainPanel!!.addUIElement(tooltip).inTL(0f, 0f)
    }

    fun showCannotApply(mods: ShipModifications, tooltip: TooltipMakerAPI) {
        val reasons: List<String> = exotic.getCannotApplyReasons(member, mods)
        var lastLabel: LabelAPI

        if (reasons.isNotEmpty()) {
            reasons.forEach {reason ->
                if (reason != Modification.DISJUNCT_LABEL_TEXT) {
                    lastLabel = tooltip.addPara(reason, 1f)
                    cannotApplyLabelList.add(lastLabel)
                } else {
                    val orLabel = tooltip.addPara(reason, 1f)
                    orLabel.setAlignment(Alignment.MID)
                    cannotApplyLabelList.add(orLabel)
                }
            }
        } else if (!exotic.checkTags(member, mods, exotic.tags)) {
            val names: List<String> = mods.getModsThatConflict(exotic.tags).map { it.name }

            StringUtils.getTranslation("Conditions", "CannotApplyBecauseTags")
                .format("conflictMods", names.joinToString(", "))
                .addToTooltip(tooltip)
        }
    }

    fun showMethods(tooltip: TooltipMakerAPI, mods: ShipModifications, lastComponent: UIComponentAPI? = tooltip.prev) {

        //this list automatically places buttons on new rows if the previous row had too many
        var lastButton: UIComponentAPI? = null
        var nextButtonX = 0f
        var rowYOffset: Float = if (cannotApplyLabelList.isNotEmpty()) {
            // Kind of a hack but works fine...
            when (cannotApplyLabelList.size) {
                0,
                1 -> BUTTONS_Y_OFFSET
                2,
                3 -> 2 * BUTTONS_Y_OFFSET
                4,
                5 -> 3 * BUTTONS_Y_OFFSET
                6,
                7 -> 4 * BUTTONS_Y_OFFSET
                else -> {
                    logger.info("Fix UI and add support for 'cannotApplyLabelList' of size ${cannotApplyLabelList.size}")
                    BUTTONS_Y_OFFSET
                }
            }
        } else {
            BUTTONS_Y_OFFSET
        }

        if (lastComponent != null) {
            rowYOffset += lastComponent.position.height
        }

        for (method in ExoticsHandler.EXOTIC_METHODS) {
            val buttonText = method.getButtonText(exotic)
            tooltip.setButtonFontDefault()

            val buttonWidth: Float = tooltip.computeStringWidth(buttonText) + 16f
            if (nextButtonX + buttonWidth >= panelWidth) {
                nextButtonX = 0f
                rowYOffset += 24f
                lastButton = null
            }

            if (method.canShow(member, mods, exotic, market) && exotic.canUseMethod(member, mods, method)) {
                val methodButton: ButtonAPI = tooltip.addButton(buttonText, "", buttonWidth, 18f, 2f)

                method.getButtonTooltip(exotic)?.let {
                    tooltip.addTooltipToPrevious(
                        StringTooltip(tooltip, it),
                        TooltipMakerAPI.TooltipLocation.BELOW
                    )
                }

                methodButton.isEnabled =
                    (market != null || method.canUseIfMarketIsNull()) && method.canUse(member, mods, exotic, market)
                buttons[methodButton] = MethodButtonHandler(method, this)

                if (lastButton == null) {
                    methodButton.position.inTL(0f, rowYOffset)
                } else {
                    methodButton.position.rightOfTop(lastButton, 3f)
                }
                lastButton = methodButton
                nextButtonX += 100f
            }
        }
    }

    fun destroyTooltip() {
        methodsTooltip?.let {
            buttons.clear()
            mainPanel!!.removeComponent(it)
        }
        methodsTooltip = null
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun callListenerHighlighted(method: ExoticMethod) {
        listeners.forEach {
            if (it.highlighted(method)) {
                return
            }
        }
    }

    fun callListenerUnhighlighted(method: ExoticMethod) {
        listeners.forEach {
            if (it.unhighlighted(method)) {
                return
            }
        }
    }

    fun callListenerChecked(method: ExoticMethod) {
        listeners.forEach {
            if (it.checked(method)) {
                return
            }
        }
    }

    open class MethodButtonHandler(val method: ExoticMethod, val shopPlugin: ExoticMethodsUIPlugin) :
        ButtonHandler() {
        override fun checked() {
            shopPlugin.callListenerChecked(method)
        }

        override fun highlighted() {
            shopPlugin.callListenerHighlighted(method)
        }

        override fun unhighlighted() {
            shopPlugin.callListenerUnhighlighted(method)
        }
    }

    abstract class Listener {
        /**
         * Return true to skip other listeners.
         */
        open fun checked(method: ExoticMethod): Boolean {
            return false
        }

        /**
         * Return true to skip other listeners.
         */
        open fun highlighted(method: ExoticMethod): Boolean {
            return false
        }

        /**
         * Return true to skip other listeners.
         */
        open fun unhighlighted(method: ExoticMethod): Boolean {
            return false
        }
    }

    companion object {
        private val logger = Logger.getLogger(ExoticMethodsUIPlugin::class.java)
        private const val BUTTONS_Y_OFFSET = 25f

        @JvmStatic
        fun isUnderExoticLimit(member: FleetMemberAPI, mods: ShipModifications): Boolean {
            return mods.getMaxExotics(member) > mods.exotics.getCount(member)
        }

        fun getExoticChips(
            cargo: CargoAPI,
            member: FleetMemberAPI,
            mods: ShipModifications,
            exotic: Exotic
        ): List<CargoStackAPI> {
            val stacks: List<CargoStackAPI> = cargo.stacksCopy
                .flatMap { stack ->
                    if (stack.plugin is CrateItemPlugin)
                        getChipsFromCrate(stack, member, mods, exotic)
                    else
                        listOf(stack)
                }
                .filter { it.plugin is ExoticSpecialItemPlugin }
                .map { it to it.plugin as ExoticSpecialItemPlugin }
                .filter { (_, plugin) -> plugin.modId == exotic.key }
                .map { (stack, _) -> stack }

            return stacks
        }

        /**
         * gets all valid upgrade chips for member from crate
         */
        fun getChipsFromCrate(
            stack: CargoStackAPI,
            member: FleetMemberAPI,
            mods: ShipModifications,
            exotic: Exotic
        ): List<CargoStackAPI> {
            return getExoticChips((stack.plugin as CrateItemPlugin).cargo, member, mods, exotic)
        }
    }
}
