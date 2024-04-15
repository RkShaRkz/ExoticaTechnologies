package exoticatechnologies.modifications.upgrades

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI
import com.fs.starfarer.api.campaign.SpecialItemPlugin
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import exoticatechnologies.config.FactionConfigLoader
import exoticatechnologies.modifications.ModSpecialItemPlugin
import exoticatechnologies.util.RenderUtils
import exoticatechnologies.util.RomanNumeral
import org.apache.log4j.Logger
import org.json.JSONObject
import org.lazywizard.lazylib.ui.LazyFont
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.*

class UpgradeSpecialItemPlugin : ModSpecialItemPlugin() {
    var upgradeLevel = 0
    var upgrade: Upgrade? = null
        get() {
            if (field == null) {
//                field = UpgradesHandler.UPGRADES[modId]!!
                // Lets do it safer
                if (UpgradesHandler.UPGRADES[modId] != null) {
                    field = UpgradesHandler.UPGRADES[modId]
                } else {
                    log.error(">>>\tUpgradesHandler.UPGRADES[${modId}] was null!")
                }
            }
            return field
        }

    override fun getName(): String {
//        return String.format("%s - %s (%s)", super.getName(), upgrade!!.name, upgradeLevel)
        return if (upgrade != null) {
            upgrade?.let {
                String.format("%s - %s (%s)", super.getName(), it.name, upgradeLevel)
            } ?: String.format("%s - %s (%s)", super.getName(), "ERROR: upgrade is null", upgradeLevel)
        } else {
            String.format("%s - %s (%s)", super.getName(), "ERROR: upgrade is null", upgradeLevel)
        }
    }

    override val type: ModType
        get() = ModType.UPGRADE
    override val sprite: SpriteAPI
        get() = upgrade?.let {
            Global.getSettings().getSprite("upgrades", it.key)
        } ?: Global.getSettings().getSprite("upgrades", "INVALID")

    override fun resolveDropParamsToSpecificItemData(params: String, random: Random): String? {
        val paramsObj = JSONObject(params)

        var upgrade: Upgrade? = null
        var level = paramsObj.optInt("level", -1)

        if (paramsObj.optBoolean("rng")) {
            upgrade = UpgradesGenerator.getDefaultPicker(random).pick()
        } else if (paramsObj.optString("faction") != null) {
            val factionConfig = FactionConfigLoader.getFactionConfig(paramsObj.getString("faction"))
            upgrade = UpgradesGenerator.getPicker(random, factionConfig.allowedUpgrades).pick()
        } else if (paramsObj.optString("upgrade") != null) {
            upgrade = UpgradesHandler.UPGRADES[paramsObj.getString("upgrade")]
        }

        upgrade ?: return null

        if (level == -1) {
            level = (random.nextFloat() * upgrade.maxLevel).toInt().coerceAtLeast(1)
        }

        return "${upgrade.key},${level}"
    }

    override fun createTooltip(
            tooltip: TooltipMakerAPI,
            expanded: Boolean,
            transferHandler: CargoTransferHandlerAPI,
            stackSource: Any,
            useGray: Boolean
    ) {
        val opad = 10.0f
        tooltip.addTitle(this.name)

        val design = this.designType
        Misc.addDesignTypePara(tooltip, design, opad)
        if (spec.desc.isNotEmpty()) {
            var c = Misc.getTextColor()
            if (useGray) {
                c = Misc.getGrayColor()
            }
            tooltip.addPara(spec.desc, c, opad)
        }

        if (upgrade != null) {
            upgrade?.let {
                tooltip.addPara(it.description, Misc.getTextColor(), opad)
            } ?: tooltip.addPara("ERROR: upgrade was null, no description", Misc.getTextColor(), opad)
        } else {
            tooltip.addPara("ERROR: upgrade was null, no description", Misc.getTextColor(), opad)
        }
    }

    override fun render(
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            alphaMult: Float,
            glowMult: Float,
            renderer: SpecialItemPlugin.SpecialItemRendererAPI
    ) {
        super.render(x, y, w, h, alphaMult, glowMult, renderer)

        val tX = 0.57f
        val tY = 0.7f
        RenderUtils.addText(RomanNumeral.toRoman(upgradeLevel), Color(255, 255, 255), Vector2f(x + (1 * tX) * w, y + (1 * tY) * h), LazyFont.TextAlignment.RIGHT)
    }

    override fun handleParam(index: Int, param: String, stack: CargoStackAPI) {
        when (Param[index]) {
            Param.UPGRADE_ID -> {
                modId = param
                if (UpgradesHandler.UPGRADES.containsKey(modId)) {
                    upgrade = UpgradesHandler.UPGRADES[modId]
                }
            }

            Param.UPGRADE_LEVEL -> upgradeLevel = param.toInt()
            Param.IGNORE_CRATE -> ignoreCrate = java.lang.Boolean.parseBoolean(param)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UpgradeSpecialItemPlugin

        if (upgradeLevel != other.upgradeLevel) return false
        if (upgrade != other.upgrade) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = upgradeLevel
        result = 31 * result + (upgrade?.hashCode() ?: 0)
        result = 31 * result + type.hashCode()
        return result
    }

    override fun toString(): String {
        return "UpgradeSpecialItemPlugin(upgradeLevel=$upgradeLevel, upgrade=$upgrade, type=$type, sprite=<redacted>)"
    }


    private enum class Param {
        UPGRADE_ID, UPGRADE_LEVEL, IGNORE_CRATE;

        companion object {
            operator fun get(index: Int): Param {
                return values()[index]
            }
        }
    }


    companion object {
        private val log: Logger = Logger.getLogger(UpgradeSpecialItemPlugin::class.java)
    }
}