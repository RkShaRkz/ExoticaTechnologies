package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import exoticatechnologies.hullmods.ExoticaTechHM
import exoticatechnologies.hullmods.exotics.ExoticHullmodLookup
import exoticatechnologies.hullmods.exotics.HullmodExoticHandler
import exoticatechnologies.modifications.ShipModLoader
import exoticatechnologies.modifications.ShipModLoader.Companion.get
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.refit.checkRefitVariant
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.datastructures.Optional
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
        logger.info("--> onInstall()\tmember = ${member}\tshouldShareEffectToOtherModules = ${shouldShareEffectToOtherModules(null, null)}")
        if (shouldShareEffectToOtherModules(null, null)) {
            val moduleSlotList = member.variant.moduleSlots
            logger.info("onInstall()\tmoduleSlots: ${moduleSlotList}")
            if (moduleSlotList == null || moduleSlotList.isEmpty()) return

            // Print out the module slot list, and generate a small map of
            // <this exotic>, <parent FleetMember> + <list of expected variants> + <list of variants we installed on>
            val mutableVariantList = moduleSlotList
                    .map { slot -> member.variant.getModuleVariant(slot) }
                    .toMutableList()
            // Obviously, add the 'member' variant to the list as well
            mutableVariantList.add(member.variant)
            // Now forget about the mutable version and use a immutable version
            val variantList = mutableVariantList.toList()

            // Iterate through each slot, getting their variant and trying to install there
            for (slot in moduleSlotList) {
                val moduleVariant = member.variant.getModuleVariant(slot)
                logger.info("onInstall()\tslot: ${slot}\tmoduleVariant: ${moduleVariant}")
                if (moduleVariant == null) continue
                val mods = get(member, moduleVariant)
                logger.info("onInstall()\tmods: ${mods}")
                mods?.let { nonNullMods ->
                    if (HullmodExoticHandler.shouldInstallHullmodExoticToVariant(
                            hullmodExotic = this,
                            parentFleetMember = member,
                            variant = moduleVariant,
                            variantList = Optional.of(variantList)
                    )) {
                        logger.info("onInstall()\t--> installWorkaround()\tmember: ${member}, moduleVariant: ${moduleVariant}, mods: ${mods}, exotic: ${this}")
                        installWorkaround(member, moduleVariant, nonNullMods, this)
                        logger.info("onInstall()\t--> installHullmodOnVariant()\tmoduleVariant: ${moduleVariant}")
                        installHullmodOnVariant(moduleVariant)
                        // And mark it as installed in the HullmodExoticHandler
                        HullmodExoticHandler.installHullmodExoticToVariant(
                                hullmodExotic = this,
                                parentFleetMember = member,     //oh lets hope this one works out ...
                                variant = moduleVariant
                        )
                    }
                }
            }
        }
        installHullmodOnVariant(member.variant)
        HullmodExoticHandler.installHullmodExoticToVariant(
                hullmodExotic = this,
                parentFleetMember = member,
                variant = member.variant
        )
        installHullmodOnVariant(member.checkRefitVariant())
        // Clear install data
        InstallData.clearStatus()
    }

    fun installHullmodOnVariant(variant: ShipVariantAPI?) {
        variant?.let {
            variant.addPermaMod(hullmodId)
        }
    }

    fun installWorkaround(
            member: FleetMemberAPI,
            variant: ShipVariantAPI,
            mods: ShipModifications,
            exotic: Exotic
    ) {
        mods.putExotic(ExoticData(exotic.key))

        ShipModLoader.set(member, variant, mods)
        // Now, check if we should continue
        if (InstallData.shouldProceed(member, variant, mods, exotic)) {
            // Update the installation data status first, so we can avoid the stackoverflow trap
            InstallData.updateStatus(member, variant, mods, exotic)

            ExoticaTechHM.addToFleetMember(member, variant)
            // We will skip this and avoid a StackOverflowError since that's the method that called this one
            exotic.onInstall(member)
        }
    }

    override fun onDestroy(member: FleetMemberAPI) {
        if (shouldShareEffectToOtherModules(null, null)) {
            logger.info("onDestroy()\tmoduleSlots: ${member.variant.moduleSlots}")
            if (member.variant.moduleSlots == null || member.variant.moduleSlots.isEmpty()) return
            val moduleSlotList = member.variant.moduleSlots
            for (slot in moduleSlotList) {
                val moduleVariant = member.variant.getModuleVariant(slot)
                logger.info("onDestroy()\tslot: ${slot}\tmoduleVariant: ${moduleVariant}")
                if (moduleVariant == null) continue
                val mods = get(member, moduleVariant)
                logger.info("onDestroy()\tmods: ${mods}")
                mods?.let { nonNullMods ->
                    logger.info("onDestroy()\t--> unapply()\tmember: ${member}, moduleVariant: ${moduleVariant}, mods: ${mods}, exotic: ${this}")
                    Global.getSettings().allHullModSpecs
                    logger.info("onDestroy()\t--> destroyWorkaround()\tmember: ${member}, moduleVariant: ${moduleVariant}, mods: ${mods}, exotic: ${this}")
                    destroyWorkaround(member, moduleVariant, nonNullMods, this)
                    logger.info("onDestroy()\t--> removeHullmodFromVariant()\tmoduleVariant: ${moduleVariant}")
                    removeHullmodFromVariant(moduleVariant)
                }
            }
        }
        removeHullmodFromVariant(member.variant)
        removeHullmodFromVariant(member.checkRefitVariant())

        val check = member.checkRefitVariant().hasHullMod(hullmodId)
        logger.info("onDestroy()\t$check has mod still")
        InstallData.clearStatus()
        logger.info("<-- onDestroy()\tStill has mod: ${check}")
    }

    private fun removeHullmodFromVariant(variant: ShipVariantAPI?) {
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

    fun destroyWorkaround(
            member: FleetMemberAPI,
            variant: ShipVariantAPI,
            mods: ShipModifications,
            exotic: Exotic
    ) {
        // Since this can be *any* HullmodExotic referencing their own ExoticHullmods, we should first
        // check the ExoticHullmodLookup map for any instances of the exotic hullmod.
        // And if we find one, we'll just pass it over to the HullmodExoticHandler to remove it from this fleetmember

        val hullmodOptional = ExoticHullmodLookup.getFromMap(hullmodId = hullmodId)
        if (hullmodOptional.isPresent()) {
            val hullmodInstance = hullmodOptional.get()
            HullmodExoticHandler.removeHullmodExoticFromFleetMember(hullmodInstance, member)
        }
        mods.removeExotic(exotic)
        if (InstallData.shouldProceed(member, variant, mods, exotic)) {
            // Update the installation data status first, so we can avoid the stackoverflow trap
            InstallData.updateStatus(member, variant, mods, exotic)

            exotic.onDestroy(member)

            ShipModLoader.set(member, variant, mods)
            ExoticaTechHM.addToFleetMember(member, variant)
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

    object InstallData {
        var _member: Optional<FleetMemberAPI> = Optional.empty()
        var _moduleVariants: Optional<List<ShipVariantAPI>> = Optional.empty()
        var _mods: Optional<ShipModifications> = Optional.empty()
        var _exotic: Optional<Exotic> = Optional.empty()

        fun shouldProceed(member: FleetMemberAPI, moduleVariant: ShipVariantAPI, modifications: ShipModifications, exotic: Exotic): Boolean {
            // The moduleVariants seem to be the biggest hurdle here since we're always restarting for the same variant
            // but, we want to return false only when we hit the exact same parameters that we already have stored here
            val storedMember = if(_member.isPresent()) _member.get() else null
            val storedVariants = if(_moduleVariants.isPresent()) _moduleVariants.get() else listOf()
            val storedMods = if(_mods.isPresent()) _mods.get() else null
            val storedExotic = if(_exotic.isPresent()) _exotic.get() else null

            val memberMatches = member == storedMember
            val variantsMatches = storedVariants.contains(moduleVariant)
            val modsMatches = modifications == storedMods
            val exoticMatches = exotic == storedExotic

            return memberMatches && variantsMatches && modsMatches && exoticMatches
        }

        fun updateStatus(member: FleetMemberAPI, moduleVariant: ShipVariantAPI, modifications: ShipModifications, exotic: Exotic) {
            _member = Optional.of(member)
            val storedVariants = if (_moduleVariants.isPresent()) { _moduleVariants.get().toMutableList() } else { mutableListOf() }
            storedVariants.add(moduleVariant)
            _moduleVariants = Optional.of(storedVariants.toList())
            _mods = Optional.of(modifications)
            _exotic = Optional.of(exotic)
        }

        fun clearStatus() {
            _member = Optional.empty()
            _moduleVariants = Optional.empty()
            _mods = Optional.empty()
            _exotic = Optional.empty()
        }
    }

    companion object {
        val logger: Logger = Logger.getLogger(HullmodExotic::class.java)
    }
}
