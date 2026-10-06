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
import exoticatechnologies.util.*
import exoticatechnologies.util.datastructures.Optional
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

    override fun showWarningIfApplyingFromRefitScreen() = false

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
            // Screen-aware anchor (plan-rev17): on the REFIT screen this anchors on the stable
            // refit-root id (CHANGE A, plan-rev11) - the only dependable marker there, since the
            // refit creates transient station-module FMAPIs whose ids reshuffle every query; if the
            // refit root is not cached/null, do ZERO whole-ship work. Outside the refit (planet-side
            // exoticatech shop / campaign storm) the entering member IS the real root FleetMemberAPI
            // with a stable id, so we anchor on it directly - never a cross-fleet fuzzy scan, which
            // previously bled whole-ship installs onto the wrong ship (the stale refit cache).
            val resolved = FleetMemberUtils.resolveWholeShipRootMember(member)
            if (resolved == null) {
                logIfOverMinLogLevel("onInstall()\tresolveWholeShipRootMember() returned null (refit root not cached) - doing ZERO whole-ship work\tmember.id = ${member.id}", Level.ERROR)
                return
            }
            resolved
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

        // Whole-ship installs: the flows now feed the FULL variant graph (campaign tree, refit
        // and display trees, child FMs' .variants) into the iterate+expected sets and install on
        // every graph through the callbacks above - see plan-hullmod_exotic_installs_on_whole_ship-revision16.
        // The child flow iterates the refit and display roots too, so no separate pour pass is needed.
    }

    private fun installHullmodOnVariant(variant: ShipVariantAPI?) {
        variant?.let {
            variant.addPermaMod(hullmodId)
        }
    }

    override fun onDestroy(member: FleetMemberAPI) {
        val rootMember = if (installsOnWholeShip()) {
            // Screen-aware anchor (plan-rev17): on the REFIT screen this anchors on the stable
            // refit-root id (CHANGE A, plan-rev11) - the only dependable marker there, since the
            // refit creates transient station-module FMAPIs whose ids reshuffle every query; if the
            // refit root is not cached/null, do ZERO whole-ship work. Outside the refit (planet-side
            // exoticatech shop / campaign) the entering member IS the real root FleetMemberAPI with
            // a stable id, so we anchor on it directly - never a cross-fleet fuzzy scan. Without
            // this, planet-side uninstall bailed at the anchor and never reached the root/children.
            val resolved = FleetMemberUtils.resolveWholeShipRootMember(member)
            if (resolved == null) {
                logger.error("onDestroy()\tCHANGE-A\tresolveWholeShipRootMember() returned null (refit root not cached) - doing ZERO whole-ship work\tmember.id = ${member.id}")
                return
            }
            resolved
        } else {
            member
        }
        // Tell HullmodExoticHandler that we're initiating uninstallation for this 'rootMember'
        HullmodExoticHandler.startUninstallSequenceForMember(rootMember)

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

            // If we're doing the whole ship, we should cover the root as well
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
        }

        // And this one actually covers the 'regular' child module's FMAPI
        HullmodExoticHandler.Flows.CheckAndRemoveFromMemberModule(
                fleetMember = member,
                fleetMemberVariant = member.variant,
                hullmodExotic = this@HullmodExotic,
                onShouldCallback = object : HullmodExoticHandler.Flows.OnShouldCallback {
                    override fun execute(onShouldResult: Boolean, moduleVariant: ShipVariantAPI) {
                        // Again, do nothing
                    }
                },
                onRemoveFromMemberModuleCallback = object : HullmodExoticHandler.Flows.OnRemoveFromMemberCallback {
                    override fun execute(onRemoveResult: Boolean, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications) {

                        unapplyExoticHullmodAndRemoveExoticaAndHullmod(
                                member = member,
                                moduleVariant = moduleVariant,
                                optionalMemberMods = Optional.empty()
                        )
                    }
                }
        )

        if (runningFromRefitScreen()) {
            HullmodExoticHandler.Flows.CheckAndRemoveFromMemberModule(
                    fleetMember = member,
                    fleetMemberVariant = member.checkRefitVariant(),
                    hullmodExotic = this@HullmodExotic,
                    onShouldCallback = object : HullmodExoticHandler.Flows.OnShouldCallback {
                        override fun execute(onShouldResult: Boolean, moduleVariant: ShipVariantAPI) {
                            // Again, do nothing
                        }
                    },
                    onRemoveFromMemberModuleCallback = object : HullmodExoticHandler.Flows.OnRemoveFromMemberCallback {
                        override fun execute(onRemoveResult: Boolean, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications) {

                            unapplyExoticHullmodAndRemoveExoticaAndHullmod(
                                    member = member,
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


        // Whole-ship removals: the remove flows now reach every graph the install wrote to through the
        // install bookkeeping (see plan-hullmod_exotic_installs_on_whole_ship-revision16), so no
        // separate strip/unapply/nuke pass is needed anymore. The child remove flow iterates the
        // refit and display roots from listOfVariantsWeInstalledOn, matching the install side.
        val check = member.checkRefitVariant().hasHullMod(hullmodId)
        logIfOverMinLogLevel("<-- onDestroy()\tStill has hullmod: ${check}", Level.INFO)
    }

    override fun onPostDestroy(member: FleetMemberAPI, variant: ShipVariantAPI, mods: ShipModifications) {
        super.onPostDestroy(member, variant, mods)
        logIfOverMinLogLevel("--> onPostDestroy()\tmember: ${member}, variant: ${variant}, mods: ${mods}", Level.INFO)
        // If we're installing everywhere, figure out the rootMember as well like in onDestroy
        val rootMember = if (installsOnWholeShip()) {
            // Screen-aware anchor (plan-rev17): on the REFIT screen this anchors on the stable
            // refit-root id (CHANGE A, plan-rev11) - the only dependable marker there, since the
            // refit creates transient station-module FMAPIs whose ids reshuffle every query; if the
            // refit root is not cached/null, do ZERO whole-ship work. Outside the refit (planet-side
            // exoticatech shop / campaign) the entering member IS the real root FleetMemberAPI with
            // a stable id, so we anchor on it directly - never a cross-fleet fuzzy scan. Without
            // this, planet-side uninstall bailed at the anchor and never reached the root/children.
            val resolved = FleetMemberUtils.resolveWholeShipRootMember(member)
            if (resolved == null) {
                logger.error("onPostDestroy()\tCHANGE-A\tresolveWholeShipRootMember() returned null (refit root not cached) - doing ZERO whole-ship work\tmember.id = ${member.id}")
                HullmodExoticHandler.finishUninstallSequenceForMember(member)
                return
            }
            resolved
        } else {
            member
        }

        // And why not nuke it again from both just to be sure ...
        HullmodExoticHandler.removeHullmodExoticFromFleetMember(
                exoticHullmodId = getHullmodId(),
                fleetMember = member
        )
        HullmodExoticHandler.removeHullmodExoticFromFleetMember(
                exoticHullmodId = getHullmodId(),
                fleetMember = rootMember
        )

        // And also, finish the uninstall sequence for this rootMember
        HullmodExoticHandler.finishUninstallSequenceForMember(rootMember)

        // In case the HullmodExoticHandler nuked the key with the first call, we'll have some stranglers remaining...
        // At this point, the HullmodExotic uninstallation is guaranteed to be completed;
        // however, some hullmods or just hullmod effects might still have lingered on - those need cleaning up.
        clearHullmodLeftovers(member, rootMember, variant)

        val check = member.checkRefitVariant().hasHullMod(hullmodId)
        logIfOverMinLogLevel("<-- onPostDestroy()\tStill has hullmod (hullmodId=${hullmodId}): ${check}", if (check) { Level.ERROR } else { Level.INFO })
    }

    /**
     * Method for cleaning up the 'dirty' hullmod leftovers (and their effects) left after uninstalling the HullmodExotic.
     * It will remove the hullmod via [removeHullmodFromVariant] and remove their effects via [ExoticHullmod.removeEffectsBeforeShipCreation]
     *
     * It will scrub:
     * - each [variant]'s module variant,
     * - each [member.variant]'s module variant,
     * - whole [member]'s variant graph ([getWholeVariantGraph])
     * - whole [rootMember]'s variant graph ([getWholeVariantGraph])
     *
     * @param member the member/child module member we're scrubbing from
     * @param rootMember the ship's root module member we should also scrub
     * @param variant the variant we're scrubbing from as well.
     */
    private fun clearHullmodLeftovers(member: FleetMemberAPI, rootMember: FleetMemberAPI, variant: ShipVariantAPI) {
        val exoticHullmodOptional = ExoticHullmodLookup.getFromMap(hullmodId)
        val exoticHullmod: ExoticHullmod
        if (exoticHullmodOptional.isPresent()) {
            exoticHullmod = exoticHullmodOptional.get()

            // Scrub everything under reachable module variants
            variant.forEachModuleVariant { moduleVariant ->
                removeHullmodFromVariant(moduleVariant)

                exoticHullmod.removeEffectsBeforeShipCreation(
                        hullSize = moduleVariant.hullSpec.hullSize,
                        stats = member.stats,
                        id = exoticHullmod.hullModId
                )
                moduleVariant.statsForOpCosts?.let { statsForOpCosts ->
                    exoticHullmod.removeEffectsBeforeShipCreation(
                            hullSize = moduleVariant.hullSpec.hullSize,
                            stats = statsForOpCosts,
                            id = exoticHullmod.hullModId
                    )
                }
            }
            member.variant.forEachModuleVariant { moduleVariant ->
                removeHullmodFromVariant(moduleVariant)

                exoticHullmod.removeEffectsBeforeShipCreation(
                        hullSize = member.variant.hullSpec.hullSize,
                        stats = member.stats,
                        id = exoticHullmod.hullModId
                )
                moduleVariant.statsForOpCosts?.let { statsForOpCosts ->
                    exoticHullmod.removeEffectsBeforeShipCreation(
                            hullSize = moduleVariant.hullSpec.hullSize,
                            stats = statsForOpCosts,
                            id = exoticHullmod.hullModId
                    )
                }
            }

            // Now do the whole graphs - first for 'member' then for 'rootMember'
            val memberGraphVariantAPIs = getWholeVariantGraph(member)
            for (graphVariant in memberGraphVariantAPIs) {
                removeHullmodFromVariant(graphVariant)

                exoticHullmod.removeEffectsBeforeShipCreation(
                        hullSize = graphVariant.hullSpec.hullSize,
                        stats = graphVariant.statsForOpCosts,
                        id = exoticHullmod.hullModId
                )
                graphVariant.statsForOpCosts?.let { statsForOpCosts ->
                    exoticHullmod.removeEffectsBeforeShipCreation(
                            hullSize = graphVariant.hullSpec.hullSize,
                            stats = statsForOpCosts,
                            id = exoticHullmod.hullModId
                    )
                }
            }

            // And the same thing for 'rootMember'
            val rootMemberGraphVariantAPIs = getWholeVariantGraph(rootMember)
            for (graphVariant in rootMemberGraphVariantAPIs) {
                removeHullmodFromVariant(graphVariant)

                exoticHullmod.removeEffectsBeforeShipCreation(
                        hullSize = graphVariant.hullSpec.hullSize,
                        stats = graphVariant.statsForOpCosts,
                        id = exoticHullmod.hullModId
                )
                graphVariant.statsForOpCosts?.let { statsForOpCosts ->
                    exoticHullmod.removeEffectsBeforeShipCreation(
                            hullSize = graphVariant.hullSpec.hullSize,
                            stats = statsForOpCosts,
                            id = exoticHullmod.hullModId
                    )
                }
            }
        }
    }

    /**
     * Very common method for [HullmodExotic] which does a very frequent "magic", consisting of:
     *
     * - removing this [Exotic] from the [moduleVariant]s [ShipModifications]
     * - invoking [ShipModLoader.set] with [member], [moduleVariant] and [ShipModifications]
     * - Toggling [ExoticaTechHM] by calling [ExoticaTechHM.addToFleetMember]
     * - removing the [ExoticHullmod] by calling [removeHullmodFromVariant]
     *
     * The stat-unapply happens BEFORE this runs, inside [HullmodExoticHandler.removeHullmodExoticFromVariant]'s
     * dual-scrub (parent member's stats + the variant's statsForOpCosts).
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

        // The handler's removeHullmodExoticFromVariant already dual-scrubs the stats objects
        // (parent member's stats + the variant's statsForOpCosts) BEFORE this callback runs, so no
        // unapply is needed here - see plan-hullmod_exotic_installs_on_whole_ship-revision9.
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
        // IMPORTANT CAVEAT: this method re-introduces a very similar "logic gate" that HullmodExoticHandler already does
        // in it's Flows. And the problem arrives/happens in the following scenario:
        // - MAX_EXOTICS is 2
        // - root module has one Exotic installed on it
        // - children have 0 or 1 exotica on them
        // - we install some HullmodExotic (e.g. AlphaSubcore) on root module from planetside "exoticatech" or refit
        // - we enter this method, the InstallMethod already pre-installed the Exotica on the installing module
        // - HullmodExoticHandler will let them through to children modules AND the root/installing module
        // - this method will fail for the root/installing module, because 2 < 2 ? false
        // This is why we should let the installation proceed if we're under exotica limit OR we already have the exotica in our mods
        val isUnderExoticLimitOrAlreadyContains = moduleVariantMods.shouldAllowInstallation(member, this)

        if (isUnderExoticLimitOrAlreadyContains) {
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
