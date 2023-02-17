package exoticatechnologies.ui.impl.shop.exotics

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticSpecialItemPlugin
import exoticatechnologies.ui.InteractiveUIPanelPlugin
import exoticatechnologies.ui.TimedUIPlugin
import exoticatechnologies.ui.impl.shop.chips.ChipPanelUIPlugin
import exoticatechnologies.ui.impl.shop.exotics.chips.ExoticChipPanelUIPlugin
import exoticatechnologies.ui.impl.shop.exotics.methods.*
import exoticatechnologies.util.RenderUtils
import exoticatechnologies.util.getMods
import java.awt.Color

class ExoticPanelUIPlugin(
    var parentPanel: CustomPanelAPI,
    var exotic: Exotic,
    var member: FleetMemberAPI,
    var market: MarketAPI
) : InteractiveUIPanelPlugin() {
    private var mainPanel: CustomPanelAPI? = null
    private var descriptionPlugin: ExoticDescriptionUIPlugin? = null
    private var methodsPlugin: ExoticMethodsUIPlugin? = null
    private var resourcesPlugin: ExoticResourcesUIPlugin? = null
    private var chipsPlugin: ExoticChipPanelUIPlugin? = null

    fun layoutPanels(): CustomPanelAPI {
        val panel = parentPanel.createCustomPanel(panelWidth, panelHeight, this)
        mainPanel = panel

        descriptionPlugin = ExoticDescriptionUIPlugin(panel, exotic, member)
        descriptionPlugin!!.panelWidth = panelWidth / 2
        descriptionPlugin!!.panelHeight = panelHeight
        descriptionPlugin!!.layoutPanels().position.inTL(0f, 0f)

        val methods = getMethods()

        resourcesPlugin = ExoticResourcesUIPlugin(panel, exotic, member, market, methods)
        resourcesPlugin!!.panelWidth = panelWidth / 2
        resourcesPlugin!!.panelHeight = panelHeight / 2
        resourcesPlugin!!.layoutPanels().position.inTR(0f, 0f)

        methodsPlugin = ExoticMethodsUIPlugin(panel, exotic, member, market, methods)
        methodsPlugin!!.panelWidth = panelWidth / 2
        methodsPlugin!!.panelHeight = panelHeight / 2
        methodsPlugin!!.layoutPanels().position.inBR(0f, 0f)
        methodsPlugin!!.addListener(MethodListener())

        parentPanel.addComponent(panel).inTR(0f, 0f)

        return panel
    }

    private fun getMethods(): List<Method> {
        return mutableListOf(
            InstallMethod(),
            RecoverMethod(),
            DestroyMethod()
        )
    }

    fun checkedMethod(method: Method): Boolean {
        if (method is ChipMethod) {
            //do something else.
            showChipsPanel()
            return true
        } else {
            applyMethod(exotic, method)
            return false
        }
    }

    fun highlightedMethod(method: Method?): Boolean {
        resourcesPlugin!!.redisplayResourceCosts(method)
        return false
    }

    fun applyMethod(exotic: Exotic, method: Method) {
        val mods = member.getMods()
        methodsPlugin!!.destroyTooltip()
        resourcesPlugin!!.destroyTooltip()

        method.apply(member, mods, exotic, market)

        Global.getSoundPlayer().playUISound("ui_char_increase_skill_new", 1f, 1f)

        resourcesPlugin!!.redisplayResourceCosts(method)
        methodsPlugin!!.showTooltip()
    }

    fun showChipsPanel() {
        methodsPlugin!!.destroyTooltip()
        resourcesPlugin!!.destroyTooltip()

        chipsPlugin = ExoticChipPanelUIPlugin(mainPanel!!, exotic, member, market)
        chipsPlugin!!.panelWidth = panelWidth / 2 - 6f
        chipsPlugin!!.panelHeight = panelHeight - 6f
        chipsPlugin!!.layoutPanels().position.inTR(9f, 3f)

        chipsPlugin!!.addListener(ChipPanelListener())
    }

    fun clickedChipPanelBackButton() {
        chipsPlugin!!.destroyTooltip()
        chipsPlugin = null

        resourcesPlugin!!.redisplayResourceCosts(null)
        methodsPlugin!!.showTooltip()
    }

    fun clickedChipStack(stack: CargoStackAPI) {
        chipsPlugin!!.destroyTooltip()
        chipsPlugin = null

        val method = ChipMethod()
        method.chipStack = stack

        applyMethod(exotic, method)
    }


    private inner class MethodListener : ExoticMethodsUIPlugin.Listener() {
        override fun checked(method: Method): Boolean {
            return this@ExoticPanelUIPlugin.checkedMethod(method)
        }

        override fun highlighted(method: Method): Boolean {
            return this@ExoticPanelUIPlugin.highlightedMethod(method)
        }

        override fun unhighlighted(method: Method): Boolean {
            return this@ExoticPanelUIPlugin.highlightedMethod(null)
        }
    }

    private inner class ChipPanelListener: ChipPanelUIPlugin.Listener<ExoticSpecialItemPlugin>() {
        override fun checkedBackButton() {
            this@ExoticPanelUIPlugin.clickedChipPanelBackButton()
        }

        override fun checked(stack: CargoStackAPI, plugin: ExoticSpecialItemPlugin) {
            this@ExoticPanelUIPlugin.clickedChipStack(stack)
        }
    }

    private class AppliedUIListener(val mainPlugin: ExoticPanelUIPlugin, val tooltip: TooltipMakerAPI) :
        TimedUIPlugin.Listener {
        override fun end() {
            mainPlugin.mainPanel!!.removeComponent(tooltip)
            mainPlugin.resourcesPlugin!!.redisplayResourceCosts(null)
            mainPlugin.methodsPlugin!!.showTooltip()
        }

        override fun render(pos: PositionAPI, alphaMult: Float, currLife: Float, endLife: Float) {

        }

        override fun renderBelow(pos: PositionAPI, alphaMult: Float, currLife: Float, endLife: Float) {
            RenderUtils.pushUIRenderingStack()
            val panelX = pos.x
            val panelY = pos.y
            val panelW = pos.width
            val panelH = pos.height
            RenderUtils.renderBox(
                panelX,
                panelY,
                panelW,
                panelH,
                Color.yellow,
                alphaMult * (endLife - currLife) / endLife
            )
            RenderUtils.popUIRenderingStack()
        }
    }
}