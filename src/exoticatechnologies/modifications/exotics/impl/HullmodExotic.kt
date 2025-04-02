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
import exoticatechnologies.modifications.ShipModLoader.Companion.get
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.refit.checkRefitVariant
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

    override fun onInstall(member: FleetMemberAPI) {
        val shouldShareEffectToOtherModules = shouldShareEffectToOtherModules(null, null)
        val isChildModule = member.shipName.isNullOrEmpty()
        logIfOverMinLogLevel("--> onInstall()\tmember = ${member}\tmember.id = ${member.id}\tshouldShareEffectToOtherModules = ${shouldShareEffectToOtherModules}, isChildModule = ${isChildModule}", Level.INFO)
        //FIXME: for the time being, only "root" modules are able to share effects to all other (child) modules
        // Ideally, any module should be able to share effects to all other modules
        // Relevant issue: https://github.com/RkShaRkz/ExoticaTechnologies/issues/39
        if (shouldShareEffectToOtherModules) {
            // If we should share to other modules, lets just focus on being able to share from the root module
            // to other modules for now. Later on, when this issue starts 'hurting' more, we can take a better look
            // on how to allow replicating from *any* module to *all* other modules.
            // SPOILER: the lookup to find the root module from which we'll discover the other modules is going to be
            // much more difficult/trickier/slower
            HullmodExoticHandler.Flows.CheckAndInstallOnAllChildModulesVariants(
                    fleetMember = member,
                    fleetMemberVariant = member.variant,
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
                            installThisHullmodExoticToFleetMembersVariant(member, moduleVariant, moduleVariantMods)
                        }
                    }
            )
        }
        HullmodExoticHandler.Flows.CheckAndInstallOnMemberModule(
                member = member,
                memberVariant = member.variant,
                hullmodExotic = this@HullmodExotic,
                onShouldCallback = object: HullmodExoticHandler.Flows.OnShouldCallback {
                    override fun execute(onShouldResult: Boolean, moduleVariant: ShipVariantAPI) {
                        logIfOverMinLogLevel("onInstall()\tshouldInstallOnMemberVariant: ${onShouldResult}, variant: ${moduleVariant}", Level.INFO)
                    }
                },
                onInstallCallback = object: HullmodExoticHandler.Flows.OnInstallToMemberCallback {
                    override fun execute(onInstallResult: Boolean, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications) {
                        installThisHullmodExoticToFleetMembersVariant(member, moduleVariant, moduleVariantMods)
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
        if (shouldShareEffectToOtherModules(null, null)) {
            // If we should share to other modules, lets just focus on being able to share from the root module
            // to other modules for now. Later on, when this issue starts 'hurting' more, we can take a better look
            // on how to allow replicating from *any* module to *all* other modules.
            // SPOILER: the lookup to find the root module from which we'll discover the other modules is going to be
            // much more difficult/trickier/slower
            HullmodExoticHandler.Flows.CheckAndRemoveFromAllChildModulesVariants(
                    fleetMember = member,
                    hullmodExotic = this@HullmodExotic,
                    onShouldCallback = object: HullmodExoticHandler.Flows.OnShouldCallback {
                        override fun execute(onShouldResult: Boolean, moduleVariant: ShipVariantAPI) {
                            // Do nothing
                        }
                    },
                    onRemoveFromChildModuleCallback = object: HullmodExoticHandler.Flows.OnRemoveFromChildModuleCallback {
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

        // While this totally isn't needed for when we're sharing to other modules, it is **VERY** much necessary for when we don't
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
                fleetMember = member
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
            val memberMods = get(member, moduleVariant)
            memberMods?.let { nonNullMods ->
                nonNullMods.removeExotic(this@HullmodExotic)
                ShipModLoader.set(member, moduleVariant, memberMods)
            }
        }
        // And the shared common part for both that has nothing to do with the mods
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
            StringUtils.getTranslation(key, statDescriptionKey)
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
     */
    private fun installThisHullmodExoticToFleetMembersVariant(member: FleetMemberAPI, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications) {
        installHullmodOnVariant(moduleVariant)

        // This is the installWorkaround code
        //TODO check if we have it first before adding
        moduleVariantMods.putExotic(ExoticData(this@HullmodExotic.key))

        ShipModLoader.set(member, moduleVariant, moduleVariantMods)
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
