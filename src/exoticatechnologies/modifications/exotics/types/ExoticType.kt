package exoticatechnologies.modifications.exotics.types

import com.fs.starfarer.api.fleet.FleetMemberAPI
import exoticatechnologies.modifications.ShipModFactory
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.types.impl.CorruptedType
import exoticatechnologies.modifications.exotics.types.impl.GuerillaType
import exoticatechnologies.modifications.exotics.types.impl.PureType
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.StringUtils.Translation
import java.awt.Color

abstract class ExoticType(val nameKey: String, val colorOverlay: Color = Color(255, 255, 255, 0), val sprite: String? = null) {
    val name: String
        get() = StringUtils.getTranslation("ExoticTypes", nameKey)
            .format("exoticName", "")
            .toStringNoFormats().trim()

    fun getMergedColor(otherColor: Color): Color {
        val ratio = colorOverlay.alpha / 255
        val red = (otherColor.red - (255 - colorOverlay.red) * ratio).coerceIn(0, 255)
        val green = (otherColor.green - (255 - colorOverlay.green) * ratio).coerceIn(0, 255)
        val blue = (otherColor.blue - (255 - colorOverlay.blue) * ratio).coerceIn(0, 255)

        return Color(red, green, blue, otherColor.alpha)
    }

    open fun getPositiveMult(member: FleetMemberAPI, mods: ShipModifications): Float {
        return 1f
    }

    open fun getNegativeMult(member: FleetMemberAPI, mods: ShipModifications): Float {
        return 1f
    }

    open fun getItemDescTranslation(): Translation? {
        return StringUtils.getTranslation("ExoticTypes", "NormalItemDescription")
    }

    open fun getDescriptionTranslation(member: FleetMemberAPI, mods: ShipModifications): Translation? {
        return StringUtils.getTranslation("ExoticTypes", "TooltipText")
                .formatFloat("positiveMult", getPositiveMult(member, mods))
                .formatFloat("negativeMult", getNegativeMult(member, mods))
    }

    open fun mutateGenerationContext(context: ShipModFactory.GenerationContext) {

    }

    open fun getChanceMult(context: ShipModFactory.GenerationContext): Float {
        return 1f
    }

    companion object {
        val NORMAL: ExoticType = NormalType()

        val types: MutableMap<String, ExoticType> = mutableMapOf()

        init {
            putType(NORMAL)
            putType(CorruptedType())
            putType(PureType())
            putType(GuerillaType())
        }

        fun valueOf(key: String): ExoticType {
            return if (types[key.uppercase()] != null) {
                types[key.uppercase()]!!
            } else {
                throw IllegalArgumentException("ExoticType with key ${key} is not registered. Please add it to ExoticType's 'types' list")
            }
        }

        private fun putType(type: ExoticType) {
            types[type.nameKey] = type
        }

        /**
         * Grabs keys of all [ExoticType]s and returns them as lowercased string
         */
        @JvmStatic
        fun stringifyTypes() : String {
            val sb = StringBuilder()
            for (key in types.keys) {
                sb.append(key).append(", ")
            }
            // remove last two characters
            sb.setLength(sb.length-2)

            return sb.toString().lowercase()
        }

        @JvmStatic
        fun getTypesKeys() : List<String> {
            return types.keys.toList()
        }
    }
}

class NormalType : ExoticType("NORMAL")
