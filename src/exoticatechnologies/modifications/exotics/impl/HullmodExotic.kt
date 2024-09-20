package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import exoticatechnologies.hullmods.ExoticaTechHM
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.refit.checkRefitVariant
import exoticatechnologies.util.StringUtils
import org.apache.log4j.Logger
import org.json.JSONObject
import java.awt.Color

open class HullmodExotic(
    key: String,
    settingsObj: JSONObject,
    private val hullmodId: String,
    private val statDescriptionKey: String,
    override var color: Color
) : Exotic(key, settingsObj) {
    override fun onInstall(member: FleetMemberAPI) {
        logger.debug("--> onInstall()\tmember = ${member}\tshouldShareEffectToOtherModules = ${shouldShareEffectToOtherModules(null, null)}")
        if (shouldShareEffectToOtherModules(null, null)) {
            logger.debug("onInstall()\tmoduleSlots: ${member.variant.moduleSlots}")
            if (member.variant.moduleSlots == null || member.variant.moduleSlots.isEmpty()) return
            val moduleSlotList = member.variant.moduleSlots
            for (slot in moduleSlotList) {
                val moduleVariant = member.variant.getModuleVariant(slot)
                logger.debug("onInstall()\tslot: ${slot}\tmoduleVariant: ${moduleVariant}")
                if (moduleVariant == null) continue
                logger.debug("onInstall()\t--> applyExoticaHullmodToVariant(moduleVariant)")
                applyExoticaHullmodToVariant(moduleVariant)
                logger.debug("onInstall()\t--> installOnVariant(moduleVariant)")
                installOnVariant(moduleVariant)
            }
        }
        installOnVariant(member.variant)
        installOnVariant(member.checkRefitVariant())
    }

    fun installOnVariant(variant: ShipVariantAPI?) {
        variant?.let {
            variant.addPermaMod(hullmodId)
        }
    }

    fun applyExoticaHullmodToVariant(variant: ShipVariantAPI?) {
        variant?.let {
            if (it.hasHullMod("exoticatech").not()) {
                variant.addPermaMod("exoticatech")
            }
        }
    }

    override fun onDestroy(member: FleetMemberAPI) {
        if (shouldShareEffectToOtherModules(null, null)) {
            logger.debug("onInstall()\tmoduleSlots: ${member.variant.moduleSlots}")
            if (member.variant.moduleSlots == null || member.variant.moduleSlots.isEmpty()) return
            val moduleSlotList = member.variant.moduleSlots
            for (slot in moduleSlotList) {
                val moduleVariant = member.variant.getModuleVariant(slot)
                logger.debug("onInstall()\tslot: ${slot}\tmoduleVariant: ${moduleVariant}")
                if (moduleVariant == null) continue
                logger.debug("onInstall()\t--> applyExoticaHullmodToVariant(moduleVariant)")
                applyExoticaHullmodToVariant(moduleVariant) //TODO remove if empty
                logger.debug("onInstall()\t--> installOnVariant(moduleVariant)")
                removeFromVariant(moduleVariant)
            }
        }
        removeFromVariant(member.variant)
        removeFromVariant(member.checkRefitVariant())

        val check = member.checkRefitVariant().hasHullMod(hullmodId)
        println("$check has mod still")
    }

    private fun removeFromVariant(variant: ShipVariantAPI?) {
        variant?.let {
            variant.removePermaMod(hullmodId)
            variant.removeMod(hullmodId)
            variant.removePermaMod(hullmodId)
            variant.addSuppressedMod(hullmodId)
            variant.removeMod(hullmodId)
            variant.removePermaMod(hullmodId)
            variant.removeMod(hullmodId)
            variant.removeSuppressedMod(hullmodId)
            variant.removePermaMod(hullmodId)
            variant.removeMod(hullmodId)
            variant.removePermaMod(hullmodId)
        }
    }



    override fun applyExoticToStats(
        id: String,
        stats: MutableShipStatsAPI,
        member: FleetMemberAPI,
        mods: ShipModifications,
        exoticData: ExoticData
    ) {
        onInstall(member)
    }

    override fun applyToShip(
        id: String,
        member: FleetMemberAPI,
        ship: ShipAPI,
        mods: ShipModifications,
        exoticData: ExoticData
    ) {
        onInstall(member)
    }

    override fun modifyToolTip(
        tooltip: TooltipMakerAPI,
        title: UIComponentAPI,
        member: FleetMemberAPI,
        mods: ShipModifications,
        exoticData: ExoticData,
        expand: Boolean
    ) {
        if (expand) {
            StringUtils.getTranslation(key, statDescriptionKey)
                .addToTooltip(tooltip, title)
        }
    }

    companion object {
        val logger: Logger = Logger.getLogger(ExoticaTechHM::class.java)
    }
}