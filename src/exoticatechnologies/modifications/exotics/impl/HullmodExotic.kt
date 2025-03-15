package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import exoticatechnologies.hullmods.ExoticaTechHM
import exoticatechnologies.hullmods.exotics.ExoticHullmod
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

/**
 * Base class denoting a type of [Exotic] whose main purpose is to install a [ExoticHullmod] with the ID of [hullmodId]
 * since that's where all of it's functionality lies.
 *
 * It's a bridge between a concrete [Exotic] implementation and it's corresponding [ExoticHullmod]
 */
open class HullmodExotic(
    key: String,
    settingsObj: JSONObject,
    private val hullmodId: String,
    private val statDescriptionKey: String,
    override var color: Color,
) : Exotic(key, settingsObj) {
    private val logger: Logger = Logger.getLogger(HullmodExotic::class.java)

    override fun onInstall(member: FleetMemberAPI) {
        val shouldShareEffectToOtherModules = shouldShareEffectToOtherModules(null, null)
        val isChildModule = member.shipName.isNullOrEmpty()
        logger.info("--> onInstall()\tmember = ${member}\tmember.id = ${member.id}\tshouldShareEffectToOtherModules = ${shouldShareEffectToOtherModules}, isChildModule = ${isChildModule}")
        if (shouldShareEffectToOtherModules) {
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
                    //TODO also need to install "exoticatechnologies" hullmod to submodules since it's not visible currently

                    //TODO both the HullmodExoticHandler and the InstallData offer two different ways of stopping recursion
                    // obviously, once the system is installed somewhere, the recursion starts from that module and spreads
                    // on all other modules.
                    // However, now that they're both in place, not only have I forgotten how the InstallData was supposed
                    // to be working (and be used) but as it is - it gets rejected every single time, thus not applying
                    // the exoticatech hullmod properly.
                    // Both of these most likely need to be unified into just one method.
                    // As such, the proper idea of handling this, might also involve the HullmodExotic keeping a reference
                    // to the "installing FleetMember" so that all other HullmodExotics cannot be uninstalled from their
                    // non-originating FleetMembers to avoid duplicating them ad-infinitum
                    // I need to look back into how this used to work, and either rewrite this whole piece, or do something
                    // clever about it, but one thing is for certain:
                    // 1. HullmodExotics that were "synthetically" installed should not be uninstallable from non-originating modules
                    // 2. The recursion will be problematic if we call exotic.onInstall() from this onInstall() method
                    // 3. If we keep getting 'fake' FleetMemberAPIs, maybe generating them once and having them be a part of the
                    // "expected child fleetMembers" list will make them match.
                    // 4. installWorkaround most likely needs to go away and it's functionality needs to be moved back here
                    // 5. this whole thing needs to be extracted into a "installToSubmodule(...)" method
                    val shouldInstallOnModuleVariant = HullmodExoticHandler.shouldInstallHullmodExoticToVariant(
                            hullmodExotic = this,
                            parentFleetMember = member,
                            variant = moduleVariant,
                            variantList = Optional.of(variantList)
                    )
                    logger.info("onInstall()\tshouldInstallOnModuleVariant: ${shouldInstallOnModuleVariant}, variant: ${moduleVariant}")
                    if (shouldInstallOnModuleVariant) {
                        // Lets try starting from the HullmodExoticHandler installation
                        val installResult = HullmodExoticHandler.installHullmodExoticToVariant(
                                hullmodExotic = this,
                                parentFleetMember = member,     //oh lets hope this one works out ...
                                variant = moduleVariant
                        )
                        logger.info("onInstall()\tinstallHullmodExoticToVariant result: ${installResult}")
                        logger.info("onInstall()\t--> installHullmodOnVariant()\tmoduleVariant: ${moduleVariant}")
                        installHullmodOnVariant(moduleVariant)

                        // This is the installWorkaround code
                        mods.putExotic(ExoticData(this.key))

                        ShipModLoader.set(member, moduleVariant, mods)
                        // Install the exoticatech hullmod to show the thing we just installed
                        ExoticaTechHM.addToFleetMember(member, moduleVariant)
                    }
                }
            }
        }
        // This 'variantList' is complicating things alot
        // because the Optional must be present if we don't already have an entry in HullmodExoticHandler's map
        // but if we do, we can just send whatever because it won't be used.
        // Taking non-modular ships into account, we should check if we have an entry in the lookup map
        // and if we do - we can send Optional.empty() since it won't be used
        // but if we do not - we need to generate a list of our variants (which is most likely just this variant
        // because the modules are handled first up there and they'd have already setup the whole entry
        val variantListOptional = if(HullmodExoticHandler.doesEntryExist(this, member)) {
            // Entry exists, lets just send an empty optional
            Optional.empty()
        } else {
            // Entry does not exist, lets just create one, even though we're probably not a multimodule ship
            val moduleSlotList = member.variant.moduleSlots
            val variants = if(moduleSlotList == null || moduleSlotList.isEmpty()) {
                // It's just this variant
                listOf(member.variant)
            } else {
                // Otherwise, it's all of them
                val mutableVariantList = moduleSlotList
                        .map { slot -> member.variant.getModuleVariant(slot) }
                        .toMutableList()
                // Obviously, add the 'member' variant to the list as well
                mutableVariantList.add(member.variant)  //TODO get rid of this, we should probably just rename this into childModulesVariantList

                mutableVariantList.toList()
            }

            // Return the optional of the list
            Optional.of(variants)
        }

        val shouldInstallOnMemberVariant = HullmodExoticHandler.shouldInstallHullmodExoticToVariant(
                hullmodExotic = this,
                parentFleetMember = member,
                variant = member.variant,
                variantList = variantListOptional
        )
        logger.info("onInstall()\tshouldInstallOnMemberVariant: ${shouldInstallOnMemberVariant}, variant: ${member.variant}")
        if (shouldInstallOnMemberVariant) {
            installHullmodOnVariant(member.variant)
            HullmodExoticHandler.installHullmodExoticToVariant(
                    hullmodExotic = this,
                    parentFleetMember = member,
                    variant = member.variant
            )
            installHullmodOnVariant(member.checkRefitVariant())
        }
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
            val moduleSlotsNullOrEmpty = member.variant.moduleSlots == null || member.variant.moduleSlots.isEmpty()
            if (moduleSlotsNullOrEmpty.not()) {
                val moduleSlotList = member.variant.moduleSlots
                for (slot in moduleSlotList) {
                    val moduleVariant = member.variant.getModuleVariant(slot)
                    if (moduleVariant == null) continue
                    val mods = get(member, moduleVariant)
                    mods?.let { nonNullMods ->
                        // Since this can be *any* HullmodExotic referencing their own ExoticHullmods, we should first
                        // check the ExoticHullmodLookup map for any instances of the exotic hullmod.
                        // And if we find one, we'll just pass it over to the HullmodExoticHandler to remove it from this fleetmember
                        val shouldRemoveFromModuleVariant = HullmodExoticHandler.shouldRemoveHullmodExoticFromVariant(
                                hullmodExotic = this,
                                parentFleetMember = member,
                                variant = moduleVariant
                        )
                        logger.info("onDestroy()\tshouldRemoveFromModuleVariant: ${shouldRemoveFromModuleVariant}, variant: ${moduleVariant}")
                        if (shouldRemoveFromModuleVariant) {
                            HullmodExoticHandler.removeHullmodExoticFromVariant(
                                    hullmodExotic = this,
                                    parentFleetMember = member,
                                    variant = moduleVariant
                            )

                            mods.removeExotic(this)
                            ShipModLoader.set(member, moduleVariant, mods)
                            ExoticaTechHM.addToFleetMember(member, moduleVariant)
                            removeHullmodFromVariant(moduleVariant)
                        }
                    }
                }
            }
        }

        val shouldRemoveFromMemberVariant = HullmodExoticHandler.shouldRemoveHullmodExoticFromVariant(
                hullmodExotic = this,
                parentFleetMember = member,
                variant = member.variant
        )
        logger.info("onDestroy()\tshouldRemoveFromMemberVariant: ${shouldRemoveFromMemberVariant}, variant: ${member.variant}")
        if (shouldRemoveFromMemberVariant) {
            HullmodExoticHandler.removeHullmodExoticFromVariant(
                    hullmodExotic = this,
                    parentFleetMember = member,
                    variant = member.variant
            )

            removeHullmodFromVariant(member.variant)
            removeHullmodFromVariant(member.checkRefitVariant())
            //TODO And finally, for good measure
//            HullmodExoticHandler.removeHullmodExoticFromFleetMember(member)
        }

        val check = member.checkRefitVariant().hasHullMod(hullmodId)
        InstallData.clearStatus()
        logger.info("<-- onDestroy()\tStill has hullmod: ${check}")
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
        mods.removeExotic(exotic)
        //TODO Put in the HullmodExoticHandler here
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

    fun getHullmodId(): String {
        return hullmodId
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
}
