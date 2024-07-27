package exoticatechnologies.modifications.exotics

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import exoticatechnologies.modifications.ModSpecialItemPlugin
import exoticatechnologies.modifications.exotics.types.ExoticType
import exoticatechnologies.util.StringUtils
import org.apache.log4j.Logger

open class ExoticSpecialItemPlugin : ModSpecialItemPlugin() {
    /**
     * The [Exotic] instance of this special item plugin
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
//                exoticData = ExoticData(modId!!)
//                if (exoticData!!.key != modId) {
//                    modId = exoticData!!.key
//                    stack.specialDataIfSpecial.data.replace(param, modId!!)
//                }
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

//                    exoticData!!.type = ExoticType.NORMAL
                    exoticData?.let {
                        it.type = ExoticType.NORMAL
                    }
                } else {
//                    exoticData!!.type = ExoticType.valueOf(param)
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
//            log.error("exotic was null\t\texoticData: ${exoticData}, exoticData.exotic.name: ${exoticData?.exotic?.name}, exoticData.exotic.cost: ${exoticData?.exotic?.getBasePrice()}")
            // Read the base price from exoticData's exotic, or fallback to 250000
            exoticData?.exotic?.getBasePrice() ?: Exotic.DEFAULT_BASE_PRICE
        }

//        log.info("<-- getBasePrice() returning ${price}")
        return price
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
        private val log: Logger = Logger.getLogger(ExoticSpecialItemPlugin::class.java)
    }
}