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
        logger.info("--> onInstall()\tmember = ${member}\tmember.id = ${member.id}\tshouldShareEffectToOtherModules = ${shouldShareEffectToOtherModules}, isChildModule = ${isChildModule}")
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
                            logger.info("onInstall()\tshouldInstallOnModuleVariant: ${onShouldResult}, variant: ${moduleVariant}")
                        }
                    },
                    onInstallToChildModuleCallback = object : HullmodExoticHandler.Flows.OnInstallToChildModuleCallback {
                        override fun execute(onInstallResult: Boolean, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications) {
                            logger.info("onInstall()\tinstallHullmodExoticToVariant result: ${onInstallResult}")
                            logger.info("onInstall()\t--> installHullmodOnVariant()\tmoduleVariant: ${moduleVariant}")
                            installHullmodOnVariant(moduleVariant)

                            // This is the installWorkaround code
                            moduleVariantMods.putExotic(ExoticData(this@HullmodExotic.key))

                            ShipModLoader.set(member, moduleVariant, moduleVariantMods)
                            // Install the exoticatech hullmod to show the thing we just installed
                            ExoticaTechHM.addToFleetMember(member, moduleVariant)
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
                        logger.info("onInstall()\tshouldInstallOnMemberVariant: ${onShouldResult}, variant: ${moduleVariant}")
                    }
                },
                onInstallCallback = object: HullmodExoticHandler.Flows.OnInstallToMemberCallback {
                    override fun execute(onInstallResult: Boolean, moduleVariant: ShipVariantAPI) {
                        installHullmodOnVariant(moduleVariant)
                        //TODO get rid of this one
                        installHullmodOnVariant(member.checkRefitVariant())
                    }
                }
        )
    }

    fun installHullmodOnVariant(variant: ShipVariantAPI?) {
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
                    fleetMemberVariant = member.variant,
                    hullmodExotic = this@HullmodExotic,
                    onShouldCallback = object: HullmodExoticHandler.Flows.OnShouldCallback {
                        override fun execute(onShouldResult: Boolean, moduleVariant: ShipVariantAPI) {
                            logger.info("onDestroy()\tshouldRemoveFromModuleVariant: ${onShouldResult}, variant: ${moduleVariant}")
                            // Well, let's carry over the old thing if "we should not"
                            //TODO this should probably be removed
                            if (onShouldResult.not()) {
                                unapplyExoticHullmodFromVariant(moduleVariant)
                            }
                        }
                    },
                    onRemoveFromChildModuleCallback = object: HullmodExoticHandler.Flows.OnRemoveFromChildModuleCallback {
                        override fun execute(onRemoveResult: Boolean, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications) {
                            logger.info("onDestroy()\tremoveFromModuleVariant: ${onRemoveResult}, variant: ${moduleVariant}")
                            moduleVariantMods.removeExotic(this@HullmodExotic)
                            ShipModLoader.set(member, moduleVariant, moduleVariantMods)
                            ExoticaTechHM.addToFleetMember(member, moduleVariant)
                            removeHullmodFromVariant(moduleVariant)
                            //TODO try to get rid of this one too
                            onDestroy(member = member)
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
                        logger.info("onDestroy()\tshouldRemoveFromMemberVariant: ${onShouldResult}, variant: ${moduleVariant}")
                    }
                },
                onRemoveFromMemberModuleCallback = object : HullmodExoticHandler.Flows.OnRemoveFromMemberCallback {
                    override fun execute(onRemoveResult: Boolean, moduleVariant: ShipVariantAPI) {
                        logger.info("onDestroy()\tremoveFromMemberVariant: ${onRemoveResult}, variant: ${moduleVariant}")
                        removeHullmodFromVariant(moduleVariant)
                        //TODO get rid of this one
                        removeHullmodFromVariant(member.checkRefitVariant())
                        onDestroy(member = member)
                    }
                }
        )

        // Try the refit variant too
        



        //TODO this part doesn't work for refit screen - so see what you can do about this with the flows
        // and the whole is(runningFromRefitScreen()) / otherwise


        // Again, just like before, just unapply from both member variants ...
        unapplyExoticHullmodFromVariant(member.variant)
        unapplyExoticHullmodFromVariant(member.checkRefitVariant())

        //TODO And finally, for good measure
        HullmodExoticHandler.removeHullmodExoticFromFleetMember(
                exoticHullmodId = getHullmodId(),
                fleetMember = member
        )
        val memberMods = get(member, member.variant)
        memberMods?.let { nonNullMemberMods ->
            nonNullMemberMods.removeExotic(this)
            ShipModLoader.set(member, member.variant, nonNullMemberMods)
            ExoticaTechHM.addToFleetMember(member, member.variant)
        }
        // And again for the refit variant
        member.checkRefitVariant()?.let { memberRefitVariant ->
            val memberRefitMods = get(member, memberRefitVariant)
            memberRefitMods?.let { nonNullMemberRefitMods ->
                nonNullMemberRefitMods.removeExotic(this)
                ShipModLoader.set(member, memberRefitVariant, nonNullMemberRefitMods)
                ExoticaTechHM.addToFleetMember(member, memberRefitVariant)
            }
        }

        val check = member.checkRefitVariant().hasHullMod(hullmodId)
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

    private fun unapplyExoticHullmodFromVariant(variant: ShipVariantAPI) {
        val variantHullSize = variant.hullSpec.hullSize
        exoticHullmod.removeEffectsBeforeShipCreation(variantHullSize, variant.statsForOpCosts, exoticHullmod.hullModId)
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
}
