package exoticatechnologies.modifications.exotics

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI
import com.fs.starfarer.api.campaign.SpecialItemPlugin
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import exoticatechnologies.modifications.ModSpecialItemPlugin
import exoticatechnologies.modifications.exotics.types.ExoticType
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.datastructures.Optional
import org.apache.log4j.Logger
import org.magiclib.kotlin.setAlpha

/**
 * Base class for [Exotic] systems wrapped in a chip item
 *
 * @see exotic
 * @see ModSpecialItemPlugin
 * @see com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin
 */
open class ExoticSpecialItemPlugin : ModSpecialItemPlugin() {
    /**
     * The [Exotic] instance wrapped in this special item plugin
     * @see exoticData
     */
    var exotic: Exotic? = null

    var exoticData: ExoticData? = null
        get() {
            if (field == null) {
                if (exotic != null) {
                    field = ExoticData(exotic!!)
                    exotic = null
                } else if (stack != null) {
                    stack.cargo.removeStack(stack)
                    field = ExoticData(ExoticsHandler.EXOTIC_LIST[0].key)
                }
            }
            return field
        }

    override val type: ModType
        get() = ModType.EXOTIC

    override val sprite: SpriteAPI
        get() = Global.getSettings().getSprite("exotics", exoticData!!.key)

    override fun createTooltip(
            tooltip: TooltipMakerAPI,
            expanded: Boolean,
            transferHandler: CargoTransferHandlerAPI,
            stackSource: Any,
            useGray: Boolean
    ) {
        super.createTooltip(tooltip, expanded, transferHandler, stackSource, useGray)

        exoticData?.let { data ->
            data.type.getItemDescTranslation()?.let {
                StringUtils.getTranslation("ExoticTypes", "ItemTypeText")
                        .format("typeName", type.name)
                        .format("typeDescription", it.toStringNoFormats())
                        .addToTooltip(tooltip)

            }
        }
    }

    override fun handleParam(index: Int, param: String, stack: CargoStackAPI) {
        when (Param[index]) {
            Param.EXOTIC_ID -> {
                modId = param
                modId?.let {safeModId ->
                    exoticData = ExoticData(safeModId)
                    exoticData?.let {safeExoticData ->
                        if (safeExoticData.key != safeModId) {
                            modId = safeExoticData.key
                            stack.specialDataIfSpecial.data.replace(param, safeModId)
                        }
                    }
                }
            }

            Param.EXOTIC_TYPE -> {
                if (param == "true" || param == "false") {
                    val split = stack.specialDataIfSpecial.data
                            .split(",".toRegex())
                            .dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    val newData = String.format("%s,NORMAL,%s", split[0], split[1])
                    stack.specialDataIfSpecial.data = newData //fix saves
                    ignoreCrate = param.toBoolean()

                    exoticData?.let {
                        it.type = ExoticType.NORMAL
                    }
                } else {
                    exoticData?.let {
                        it.type = ExoticType.valueOf(param)
                    }
                }
            }

            Param.IGNORE_CRATE -> ignoreCrate = param.toBoolean()
        }
    }

    public fun getBasePrice(): Int {
        // This is using an elvis operator because even when it's nullguarded properly
        // kotlin complains that since 'exotic' is a mutable property, it could have changed
        // and it just doesn't make any sense to use a 'let' call in a null-guarded part of the if()
        //
        // Of course I would have used 'return if(exotic != null) { return exotic.getBasePrice() } else { 250000 }
        val price: Int = if (exotic != null) {
            exotic!!.getBasePrice()
        } else {
            // Read the base price from exoticData's exotic, or fallback to 250000
            exoticData?.exotic?.getBasePrice() ?: Exotic.DEFAULT_BASE_PRICE
        }

        return price
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
        val exoticSprite = Global.getSettings().getSprite("exotics", exoticData!!.exotic.icon)
        val tX = 0.4f
        val tY = 0.37f
        val tW = 0.70f
        val tH = 0.70f
        val mult = 1f
        if (isExoticItem()) {
            // If we're dealing with an exotic item, check whether we should overlay
            if (shouldOverlayExoticIconOverExoticItemIcon) {
                // If we should overlay for exotic items too, draw the sprite in it
                exoticSprite.alphaMult = alphaMult * mult
                exoticSprite.setNormalBlend()
                exoticSprite.setSize(tW * exoticSprite.width, tH * exoticSprite.height)
                exoticSprite.renderAtCenter(x + (1 + tX) * (w * tW) / 2, y + (1 + tY) * (h * tH) / 2)
            } else {
                // Otherwise, do nothing
            }
        }

        exoticData!!.type.sprite?.let {
            val overlay = Global.getSettings().getSprite(it)
            overlay.alphaMult = alphaMult * mult
            overlay.setNormalBlend()
            overlay.setSize(tW * overlay.width, tH * overlay.height)
            overlay.color = exoticData!!.type.colorOverlay.setAlpha(255)
            overlay.renderAtCenter(x + (1 + tX) * (w * tW) / 2, y + (1 + tY) * (h * tH) / 2)
        }

        val cx = x + w / 2f
        val cy = y + h / 2f
        val blX = cx - 24f
        val blY = cy - 17f
        val tlX = cx - 14f
        val tlY = cy + 26f
        val trX = cx + 28f
        val trY = cy + 25f
        val brX = cx + 20f
        val brY = cy - 18f

        renderer.renderScanlinesWithCorners(blX, blY, tlX, tlY, trX, trY, brX, brY, alphaMult * 0.75f, false)
    }

    /**
     * Returns the [stack] icon name if it's a [CargoStackAPI.isSpecialStack] and an instance of [ExoticSpecialItemPlugin]
     */
    private fun getStackItemIconName() : Optional<String> {
        // Currently, the getter from base class BaseSpecialItemPlugin shadows the custom getter for 'stack'
        // from ModSpecialItemPlugin, so to get around that, we will cast to *our* base class and check that way
        val KT55017fix = this as ModSpecialItemPlugin
        KT55017fix.stack?.let {
            if (it.isSpecialStack) {
                if (it.plugin is ExoticSpecialItemPlugin) {
                    val exoticPlugin = it.plugin as ExoticSpecialItemPlugin
                    return Optional.of(exoticPlugin.spec.iconName)
                }
            }
        }

        return Optional.empty()
    }

    /**
     * A somewhat plaky method to check whether we're dealing with an exotic item, or an exotic chip
     * We do this by checking if the item's icon is neither of the two chip ones
     */
    private fun isExoticItem() : Boolean {
        // Since there is no good way to do this, because Exotic items and Exotic chips are the same thing,
        // we can differentiate between the chip and the item based on the icon that's used for the background
        val optional = getStackItemIconName()
        var retVal = false

        if (optional.isPresent()) {
            val icon = optional.get()

            retVal = (icon != "graphics/icons/cargo/chip1_ex.png" && icon != "graphics/icons/cargo/chip2_ex.png")
        }

        return retVal
    }

    private enum class Param {
        EXOTIC_ID, EXOTIC_TYPE, IGNORE_CRATE;

        companion object {
            operator fun get(index: Int): Param {
                return values()[index]
            }
        }
    }

    companion object {
        /**
         * Determines whether the [Exotic]'s image should be superpositioned (overlaid) on the exotic item's image.
         *
         * Defaults to **false** because I don't like the exotic system's image obstructing the nice icons I made
         */
        @JvmStatic
        var shouldOverlayExoticIconOverExoticItemIcon = false

        private val log: Logger = Logger.getLogger(ExoticSpecialItemPlugin::class.java)
    }
}