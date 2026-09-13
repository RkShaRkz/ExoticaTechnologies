package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import exoticatechnologies.hullmods.ExoticaTechHM
import exoticatechnologies.hullmods.exotics.ExoticHullmod
import exoticatechnologies.hullmods.exotics.ExoticHullmodLookup
import exoticatechnologies.hullmods.exotics.HullmodExoticHandler
import exoticatechnologies.modifications.ShipModLoader
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.refit.checkRefitVariant
import exoticatechnologies.util.FleetMemberUtils
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.datastructures.Optional
import exoticatechnologies.util.runningFromRefitScreen
import exoticatechnologies.util.shouldLog
import org.apache.log4j.Level
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
    private val exoticHullmod: ExoticHullmod
        get() {
            val exoticHullmodOptional = ExoticHullmodLookup.getFromMap(hullmodId)
            return if (exoticHullmodOptional.isPresent()) {
                exoticHullmodOptional.get()
            } else {
                throw IllegalStateException("No ExoticHullmod with ID ${hullmodId} found in ExoticHullmodLookup !!!")
            }
        }

    override fun showWarningIfApplyingFromRefitScreen() = true

    /**
     * Whether this [HullmodExotic] installs its hullmod on the whole ship (every module) or only on the
     * module it was installed on.
     *
     * This controls *install extent* and is separate from [shouldShareEffectToOtherModules], which governs
     * whether an exotic owned by one module applies its effects (e.g. [applyExoticToStats]) to other modules.
     * A [HullmodExotic] can therefore install its hullmod on a single module while still sharing its effects
     * ship-wide.
     *
     * @return true if the hullmod should be installed on all modules of the ship, false if only on the owning module
     */
    open fun installsOnWholeShip(): Boolean = false

    override fun onInstall(member: FleetMemberAPI) {
        val installsOnWholeShip = installsOnWholeShip()
        val isChildModule = member.shipName.isNullOrEmpty()
        logIfOverMinLogLevel("--> onInstall()\tmember = ${member}\tmember.id = ${member.id}\tinstallsOnWholeShip = ${installsOnWholeShip}, isChildModule = ${isChildModule}", Level.INFO)
        // Whole-ship installs anchor on the ship's root member, so they reach all modules no matter
        // which module triggered them. For non-whole-ship installs we simply act on the member as-is.
        // Relevant issue: https://github.com/RkShaRkz/ExoticaTechnologies/issues/39
        val rootMember = if (installsOnWholeShip) {
            FleetMemberUtils.findRootVariantMember(member)
        } else {
            member
        }
        if (installsOnWholeShip) {
            HullmodExoticHandler.Flows.CheckAndInstallOnAllChildModulesVariants(
                    fleetMember = rootMember,
                    fleetMemberVariant = rootMember.variant,
                    hullmodExotic = this,
                    onShouldCallback = object: HullmodExoticHandler.Flows.OnShouldCallback {
                        override fun execute(onShouldResult: Boolean, moduleVariant: ShipVariantAPI) {
                            logIfOverMinLogLevel("onInstall()\tshouldInstallOnModuleVariant: ${onShouldResult}, variant: ${moduleVariant}", Level.INFO)
                        }
                    },
                    onInstallToChildModuleCallback = object : HullmodExoticHandler.Flows.OnInstallToChildModuleCallback {
                        override fun execute(onInstallResult: Boolean, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications) {
                            logIfOverMinLogLevel("onInstall()\tinstallHullmodExoticToVariant result: ${onInstallResult}", Level.INFO)
                            logIfOverMinLogLevel("onInstall()\t--> installHullmodOnVariant()\tmoduleVariant: ${moduleVariant}", Level.INFO)
                            installThisHullmodExoticToFleetMembersVariant(rootMember, moduleVariant, moduleVariantMods)
                        }
                    }
            )
        }
        HullmodExoticHandler.Flows.CheckAndInstallOnMemberModule(
                member = rootMember,
                memberVariant = rootMember.variant,
                hullmodExotic = this@HullmodExotic,
                onShouldCallback = object: HullmodExoticHandler.Flows.OnShouldCallback {
                    override fun execute(onShouldResult: Boolean, moduleVariant: ShipVariantAPI) {
                        logIfOverMinLogLevel("onInstall()\tshouldInstallOnMemberVariant: ${onShouldResult}, variant: ${moduleVariant}", Level.INFO)
                    }
                },
                onInstallCallback = object: HullmodExoticHandler.Flows.OnInstallToMemberCallback {
                    override fun execute(onInstallResult: Boolean, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications) {
                        installThisHullmodExoticToFleetMembersVariant(rootMember, moduleVariant, moduleVariantMods)
                    }
                }
        )
    }

    private fun installHullmodOnVariant(variant: ShipVariantAPI?) {
        variant?.let {
            variant.addPermaMod(hullmodId)
        }
    }

    override fun onDestroy(member: FleetMemberAPI) {
        val rootMember = if (installsOnWholeShip()) {
            // Whole-ship removals anchor on the ship's root member, mirroring onInstall, so the remove
            // bookkeeping matches the root-anchored InstallData no matter which module triggered it.
            FleetMemberUtils.findRootVariantMember(member)
        } else {
            member
        }
        if (installsOnWholeShip()) {
            HullmodExoticHandler.Flows.CheckAndRemoveFromAllChildModulesVariants(
                    fleetMember = rootMember,
                    hullmodExotic = this@HullmodExotic,
                    onShouldCallback = object: HullmodExoticHandler.Flows.OnShouldCallback {
                        override fun execute(onShouldResult: Boolean, moduleVariant: ShipVariantAPI) {
                            // Do nothing
                        }
                    },
                    onRemoveFromChildModuleCallback = object: HullmodExoticHandler.Flows.OnRemoveFromChildModuleCallback {
                        override fun execute(onRemoveResult: Boolean, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications) {

                            unapplyExoticHullmodAndRemoveExoticaAndHullmod(
                                    member = rootMember,
                                    moduleVariant = moduleVariant,
                                    optionalMemberMods = Optional.of(moduleVariantMods)
                            )
                        }
                    }
            )
        }

        HullmodExoticHandler.Flows.CheckAndRemoveFromMemberModule(
                fleetMember = rootMember,
                fleetMemberVariant = rootMember.variant,
                hullmodExotic = this@HullmodExotic,
                onShouldCallback = object : HullmodExoticHandler.Flows.OnShouldCallback {
                    override fun execute(onShouldResult: Boolean, moduleVariant: ShipVariantAPI) {
                        // Again, do nothing
                    }
                },
                onRemoveFromMemberModuleCallback = object : HullmodExoticHandler.Flows.OnRemoveFromMemberCallback {
                    override fun execute(onRemoveResult: Boolean, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications) {

                        unapplyExoticHullmodAndRemoveExoticaAndHullmod(
                                member = rootMember,
                                moduleVariant = moduleVariant,
                                optionalMemberMods = Optional.empty()
                        )
                    }
                }
        )

        // While this totally isn't needed for when we're sharing to other modules, it is **VERY** much necessary for when we don't
        if (runningFromRefitScreen()) {
            HullmodExoticHandler.Flows.CheckAndRemoveFromMemberModule(
                    fleetMember = rootMember,
                    fleetMemberVariant = rootMember.checkRefitVariant(),
                    hullmodExotic = this@HullmodExotic,
                    onShouldCallback = object : HullmodExoticHandler.Flows.OnShouldCallback {
                        override fun execute(onShouldResult: Boolean, moduleVariant: ShipVariantAPI) {
                            // Again, do nothing
                        }
                    },
                    onRemoveFromMemberModuleCallback = object : HullmodExoticHandler.Flows.OnRemoveFromMemberCallback {
                        override fun execute(onRemoveResult: Boolean, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications) {

                            unapplyExoticHullmodAndRemoveExoticaAndHullmod(
                                    member = rootMember,
                                    moduleVariant = moduleVariant,
                                    optionalMemberMods = Optional.of(moduleVariantMods)
                            )
                        }
                    }
            )
        }

        // And finally, for good measure
        HullmodExoticHandler.removeHullmodExoticFromFleetMember(
                exoticHullmodId = getHullmodId(),
                fleetMember = rootMember
        )

        val check = member.checkRefitVariant().hasHullMod(hullmodId)
        logIfOverMinLogLevel("<-- onDestroy()\tStill has hullmod: ${check}", Level.INFO)
    }

    /**
     * Very common method for [HullmodExotic] which does a very frequent "magic", consisting of:
     *
     * - removing this [Exotic] from the [moduleVariant]s [ShipModifications]
     * - invoking [ShipModLoader.set] with [member], [moduleVariant] and [ShipModifications]
     * - Toggling [ExoticaTechHM] by calling [ExoticaTechHM.addToFleetMember]
     * - removing the [ExoticHullmod] by calling [removeHullmodFromVariant]
     * - finally, unapplies the ExoticHullmod by calling [unapplyExoticHullmodFromVariant]
     *
     * **NOTE**: The [optionalMemberMods] is a somewhat "special" parameter that either contains [ShipModifications]
     * of the [moduleVariant] or in case it's empty, the 'mods' will be fetched manually via [get] before commencing
     * with the first step - removal of the exotica.
     *
     * Opposite method of [installThisHullmodExoticToFleetMembersVariant]
     *
     * @param member the "parent" [FleetMemberAPI]
     * @param moduleVariant the [ShipVariantAPI] of the module in question
     * @param optionalMemberMods an [Optional] that either contains the [ShipModifications] of the [moduleVariant] or not.
     */
    private fun unapplyExoticHullmodAndRemoveExoticaAndHullmod(member: FleetMemberAPI, moduleVariant: ShipVariantAPI, optionalMemberMods: Optional<ShipModifications>) {
        // If optional is there
        if (optionalMemberMods.isPresent()) {
            val memberMods = optionalMemberMods.get()
            memberMods.removeExotic(this@HullmodExotic)
            ShipModLoader.set(member, moduleVariant, memberMods)
        } else {
            // Otherwise, extract them manually and use them
            val memberMods = ShipModLoader.getFromVariant(moduleVariant)
            memberMods?.let { nonNullMods ->
                nonNullMods.removeExotic(this@HullmodExotic)
                ShipModLoader.set(member, moduleVariant, nonNullMods)
            }
        }

        // Refresh (or remove) the ExoticaTech hullmod
        ExoticaTechHM.addToFleetMember(member, moduleVariant)
        removeHullmodFromVariant(moduleVariant)

        // grab stats to use
        val stats = HullmodExoticHandler.getNonNullStatsToUse(member, moduleVariant)
        unapplyExoticHullmodFromVariant(moduleVariant, stats)
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

    /**
     * Utility method for calling [ExoticHullmod.removeEffectsBeforeShipCreation] on the 'internal' [exoticHullmod] with
     * necessary parameters
     *
     * @param variant a [ShipVariantAPI] from which to unapply the [ExoticHullmod]
     * @param stats the [MutableShipStatsAPI] from which to unapply the [ExoticHullmod]
     */
    private fun unapplyExoticHullmodFromVariant(variant: ShipVariantAPI, stats: MutableShipStatsAPI) {
        val variantHullSize = variant.hullSpec.hullSize
        exoticHullmod.removeEffectsBeforeShipCreation(variantHullSize, stats, exoticHullmod.hullModId)
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
            StringUtils
                    .getTranslation(key, statDescriptionKey)
                    .addToTooltip(tooltip, title)
        }
    }

    fun getHullmodId(): String {
        return hullmodId
    }


    /**
     * The opposite method of [unapplyExoticHullmodAndRemoveExoticaAndHullmod] which:
     *
     * - installs the [ExoticHullmod] on the [moduleVariant]
     * - adds this [HullmodExotic.key] to the [moduleVariantMods]
     * - calls [ShipModLoader.set] to install the [Exotic]
     * - and finally adds the ExoticaTech hullmod by calling [ExoticaTechHM.addToFleetMember]
     *
     * @param member the root module's [FleetMemberAPI]
     * @param moduleVariant the [ShipVariantAPI] of the module we're supposed to install the hullmod to
     * @param moduleVariantMods the [ShipModifications] belonging to the module where the Exotic is going to be installed
     */
    private fun installThisHullmodExoticToFleetMembersVariant(member: FleetMemberAPI, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications) {
        // Since we already have the exotic installed when we come to this method,
        // due to chip's InstallMethod implementation, the whole "add hullmods if we don't have any mods" approach
        // is broken by design, so just proceed to install normally

        // This is the installWorkaround code - relevant mostly for modules we "shared installation" to
        // We have this "if under exotic limit" for child modules, since the InstallMethod will take care of
        // the installing-module (it won't be applicable if over)
        if (moduleVariantMods.isUnderExoticLimit(member)) {
            // Install the hullmod since we're under the exotic limit, this could have been done outside
            // but somehow feels cleaner to do here
            installHullmodOnVariant(moduleVariant)

            // And proceed to install the HullmodExotic into the ShipModifications moduleVariant's "mods"
            moduleVariantMods.putExotic(ExoticData(this@HullmodExotic.key))

            ShipModLoader.set(member, moduleVariant, moduleVariantMods)
        } else {
            logIfOverMinLogLevel("Bailing out - not installing HullmodExotic ${this} on module variant ${moduleVariant} due to already being at Max Exoticas Limit !!!", Level.ERROR)
        }
        // Install the exoticatech hullmod to show the thing we just installed
        ExoticaTechHM.addToFleetMember(member, moduleVariant)
    }

    private fun logIfOverMinLogLevel(logMsg: String, logLevel: Level) {
        shouldLog(
                logMsg = logMsg,
                logger = logger,
                logLevel = logLevel,
                minLogLevel = MIN_LOG_LEVEL
        )
    }

    companion object {
        val MIN_LOG_LEVEL: Level = Level.WARN
    }
}
