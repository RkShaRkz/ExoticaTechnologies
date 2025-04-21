package exoticatechnologies.hullmods.exotics

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import exoticatechnologies.modifications.ShipModLoader
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.impl.HullmodExotic
import exoticatechnologies.refit.checkRefitVariant
import exoticatechnologies.util.StarsectorAPIInteractor
import exoticatechnologies.util.datastructures.Optional
import exoticatechnologies.util.getChildModuleVariantList
import exoticatechnologies.util.runningFromExoticaTechnologiesScreen
import exoticatechnologies.util.shouldLog
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object HullmodExoticHandler {
    private val logger: Logger = Logger.getLogger(HullmodExoticHandler::class.java)
    private val MIN_LOG_LEVEL: Level = Level.WARN

    private val lookupMap: MutableMap<HullmodExoticKey, HullmodExoticInstallData> = ConcurrentHashMap()

    /**
     * Method for checking whether the [HullmodExotic] should be installed onto a [ShipVariantAPI] by checking
     * if we hadn't already installed it or not
     *
     * **NOTE** regarding this [Optional] parameter - it **must** be present for when there is no InstallData in the
     * [lookupMap] so it can be initialized properly
     *
     * @param hullmodExotic the HullmodExotic in question
     * @param parentFleetMember the [FleetMemberAPI] of the root module, or the whole ship's if it's a single-module ship
     * @param variant the variant to check
     * @param variantList list of module variants belonging to the parent module (optional)
     * @param workMode the [HullmodExoticHandlerWorkMode] we're currently operating in
     *
     * @return whether the [hullmodExotic] should be installed into the [variant] or not
     */
    private fun shouldInstallHullmodExoticToVariant(
            hullmodExotic: HullmodExotic,
            parentFleetMember: FleetMemberAPI,
            variant: ShipVariantAPI,
            variantList: Optional<List<ShipVariantAPI>>,
            workMode: HullmodExoticHandlerWorkMode
    ): Boolean {
        logIfOverMinLogLevel("--> shouldInstallHullmodExoticToVariant()\tvariant: ${variant}, runningFromRefit: ${runningFromRefitScreen()}", Level.INFO)
        // Prepare the key in the lookup map
        synchronized(lookupMap) {
            // First things first, check if we have this in the map
            // we will do this by forming up a [HullmodExotica, FleetMemberAPI] pair as a key
            val hullmodExoticKey = HullmodExoticKey(
                    hullmodExotic = hullmodExotic,
                    parentFleetMemberId = parentFleetMember.id
            )
            val currentInstallData = lookupMap[hullmodExoticKey]

            // SANITY CHECK
            // I have seen "duplicate keys" piling up, except that the keys aren't duplicate at all.
            // Major difference being in that the duplicates all have "shipName = null", so try discarding those if possible
            // unless those are for the child modules themselves.
            if (parentFleetMember.shipName == null) {
                logIfOverMinLogLevel("shouldInstallHullmodExoticToVariant()\tEncountered parentFleetMember with shipName == null !!! Bailing out !!!", Level.WARN)
                return false
            }

            // now, if we already have some install data, we are going to be checking the variant we're about to install
            // versus two different things:
            // 1. is it in the expected list?
            // 2. is it already NOT in the installed list?
            // we'll just do nothing if it fails either of these and return false.
            val retVal = if (currentInstallData != null) {
                logIfOverMinLogLevel("shouldInstallHullmodExoticToVariant()\talready had install data !!!", Level.INFO)
                // now, check if this variant is in the expected list
                val expectedVariants = currentInstallData.listOfExpectedVariants

                // For LENIENT work mode, we will merely check the HullSpec.HullID since we will get many similar
                // variants coming in from the fake FleetMemberAPIs created by the Refit screen.
                // For STRICT work mode, we will literally check if the variant is in the list.

                // obviously these don't match
                val isExpected: Boolean = if (workMode == HullmodExoticHandlerWorkMode.LENIENT) {
                    // LENIENT work mode - just check hullIDs
                    expectedVariants
                            .map { expectedVariant -> expectedVariant.hullSpec.hullId }
                            .contains(variant.hullSpec.hullId)
                } else {
                    // STRICT work mode - check whether they're in the list
                    expectedVariants.contains(variant)
                }
                // Everything else should remain the same ...
                logIfOverMinLogLevel("shouldInstallHullmodExoticToVariant()\t${workMode}\tisExpected: ${isExpected}", Level.INFO)

                val variantsWeAlreadyInstalledOn = currentInstallData.listOfVariantsWeInstalledOn
                val hasNotInstalledOnThisVariantAlready = variantsWeAlreadyInstalledOn.contains(variant).not()
                logIfOverMinLogLevel("shouldInstallHullmodExoticToVariant()\thasNotInstalledOnThisAlready: ${hasNotInstalledOnThisVariantAlready}", Level.INFO)

                // Since I don't know how to check anything with parentFleetMember yet, lets just leave it out of the equation for now
                isExpected && hasNotInstalledOnThisVariantAlready
            } else {
                logIfOverMinLogLevel("shouldInstallHullmodExoticToVariant()\tthere was no install data for this key ${hullmodExoticKey}", Level.WARN)
                // If we don't have any install data in the map, this is going to be simple. The optional variant list must be present
                // If not present, just... do nothing for now, because we can't start off from a submodule I hope. So for now, throw
                if (variantList.isPresent().not()) {
                    throw IllegalStateException("Since there is no HullmodExoticInstallData for these parameters, the Optional must be present!")
                }

                // And now, add stuff to the map ...
                synchronized(lookupMap) {
                    lookupMap[hullmodExoticKey] = HullmodExoticInstallData(
                            parentFleetMemberAPI = parentFleetMember,
                            listOfAllModuleVariants = variantList.get(),
                            listOfExpectedVariants = variantList.get(),
                            listOfVariantsWeInstalledOn = listOf()
                    )
                }

                // And obviously return true
                true
            }

            logIfOverMinLogLevel("<-- shouldInstallHullmodExoticToVariant()\treturning ${retVal}", Level.INFO)
            return retVal
        }
    }

    /**
     * Method that installs a [HullmodExotic] into the [variant]
     *
     * @param hullmodExotic the [HullmodExotic] in question (to be installed)
     * @param parentFleetMember the [FleetMemberAPI] of the root module, or the whole ship if it's a single-module ship
     * @param variant the [ShipVariantAPI] variant to install the HullmodExotic in to
     * @param workMode the [HullmodExoticHandlerWorkMode] work mode we're currently operating in
     *
     * @return whether the hullmod exotic was succesfully installed into the variant or not
     */
    private fun installHullmodExoticToVariant(
            hullmodExotic: HullmodExotic,
            parentFleetMember: FleetMemberAPI,
            variant: ShipVariantAPI,
            workMode: HullmodExoticHandlerWorkMode
    ): Boolean {
        synchronized(lookupMap) {
            logIfOverMinLogLevel("--> installHullmodExoticToVariant()\thullmodExotic: ${hullmodExotic}, parentFleetMember: ${parentFleetMember}, variant: ${variant}, runningFromRefit: ${runningFromRefitScreen()}", Level.INFO)
            // Basically, this should be easy:
            // 1. form up the key
            // 2. grab the InstallData. Throw if there is none.
            // 3. grab the already-installed list
            // 4. throw if we're already in it
            // 5. convert it to mutable list
            // 6. add the variant to it
            // 7. make a copy of the original lookup[key] value with the updated already-installed list
            // 8. update the map with new installed data
            val hullmodExoticKey = HullmodExoticKey(
                    hullmodExotic = hullmodExotic,
                    parentFleetMemberId = parentFleetMember.id
            )

            val currentInstallData = lookupMap[hullmodExoticKey]
            if (currentInstallData == null) {
                throw IllegalStateException("We should have had this key in the lookup map!!! lookupMap: ${lookupMap}")
            }

            val alreadyInstalledList = currentInstallData.listOfVariantsWeInstalledOn
            val expectedList = currentInstallData.listOfExpectedVariants
            // just check
            val alreadyIn = alreadyInstalledList.contains(variant)
            if (alreadyIn) {
                // If we're already installed on this variant, just log as warning - because it's not necessarily an error
                // if we already installed the HullmodExotic and then just go through the modules in the REFIT screen
                logIfOverMinLogLevel("installHullmodExoticToVariant()\t\t !!!!! Trying to install on a variant that's already been installed on, returning false!!!!!", Level.WARN)

                return false
            }

            // Now, the difference between LENIENT and STRICT will essentially fall down to the strict version
            // installing *only* on the expected variants, where the lenient one won't even look at the expected list,
            // as well as *not* reducing the 'expected' list because we'll get many more than originally expected ...

            return if (workMode == HullmodExoticHandlerWorkMode.STRICT) {
                // STRICT work mode - only install on the 'expected' variants and do nothing for unexpected ones
                val isExpected = expectedList.contains(variant)
                // If we're expected, remove from expected list and add to installed list and return true
                if (isExpected) {
                    val mutableInstalledList = alreadyInstalledList.toMutableList()
                    mutableInstalledList.add(variant)

                    val mutableExpectedList = expectedList.toMutableList()
                    mutableExpectedList.remove(variant)
                    val newKeyValue = currentInstallData.copy(
                            parentFleetMemberAPI = currentInstallData.parentFleetMemberAPI,
                            listOfExpectedVariants = mutableExpectedList.toList(),
                            listOfVariantsWeInstalledOn = mutableInstalledList.toList()
                    )
                    // Doubly-locked because why?
                    synchronized(lookupMap) {
                        lookupMap[hullmodExoticKey] = newKeyValue
                    }

                    logIfOverMinLogLevel("installHullmodExoticToVariant()\t\tnew expected list: ${mutableExpectedList}", Level.INFO)
                    logIfOverMinLogLevel("installHullmodExoticToVariant()\t\tnew installed list: ${mutableInstalledList}", Level.INFO)
                    logIfOverMinLogLevel("<-- installHullmodExoticToVariant()\t\treturning true", Level.INFO)

                    // And return 'true' to indicate we *should* install on this variant
                    true
                } else {
                    logIfOverMinLogLevel("installHullmodExoticToVariant()\t\t !!!!! Trying to install on a variant that's NOT expected, returning false!!!!!", Level.ERROR)

                    // And return 'false' to indicate we *should not* install on this variant
                    false
                }
            } else {
                // LENIENT work mode - install on whatever comes in and/or we get called with AND do not reduce expected list ... *shrug*
                val mutableInstalledList = alreadyInstalledList.toMutableList()
                mutableInstalledList.add(variant)

                val newKeyValue = currentInstallData.copy(
                        parentFleetMemberAPI = currentInstallData.parentFleetMemberAPI,
                        listOfExpectedVariants = expectedList.toList(),
                        listOfVariantsWeInstalledOn = mutableInstalledList.toList()
                )
                // Doubly-locked because why?
                synchronized(lookupMap) {
                    lookupMap[hullmodExoticKey] = newKeyValue
                }

                logIfOverMinLogLevel("installHullmodExoticToVariant()\t\tnew expected list: ${expectedList}", Level.INFO)
                logIfOverMinLogLevel("installHullmodExoticToVariant()\t\tnew installed list: ${mutableInstalledList}", Level.INFO)
                logIfOverMinLogLevel("<-- installHullmodExoticToVariant()\t\treturning true", Level.INFO)

                // And return 'true' to indicate we *should* install on this variant
                true
            }
        }
    }

    /**
     * Method for checking whether the [HullmodExotic] should be uninstalled from a [ShipVariantAPI] by checking
     * if we had already installed it
     *
     * @param hullmodExotic the HullmodExotic in question
     * @param parentFleetMember the [FleetMemberAPI] of the root module, or the whole ship's if it's a single-module ship
     * @param variant the variant to check
     * @param workMode the [HullmodExoticHandlerWorkMode] in which we're **currently** operating, because we do things differently for [HullmodExoticHandlerWorkMode.LENIENT] and [HullmodExoticHandlerWorkMode.STRICT] work modes
     *
     * @return whether the HullmodExotic should be removed from the variant or not
     */
    private fun shouldRemoveHullmodExoticFromVariant(
            hullmodExotic: HullmodExotic,
            parentFleetMember: FleetMemberAPI,
            variant: ShipVariantAPI,
            workMode: HullmodExoticHandlerWorkMode
    ): Boolean {
        // LENIENT vs STRICT - for STRICT, we should fail-fast if we don't have the hullmod; for LENIENT - just do nothing and fall through
        if (workMode == HullmodExoticHandlerWorkMode.STRICT) {
            // STRICT work mode - fail fast if we don't have the hullmod
            // Fail fast if the variant doesn't have the hullmod
            val hullmodId = hullmodExotic.getHullmodId()
            val alreadyHasHullmod = variant.hasHullMod(hullmodId)
            if (alreadyHasHullmod.not()) {
                logIfOverMinLogLevel("shouldRemoveHullmodExoticFromVariant()\tVariant ${variant} doesn't have with the ${hullmodId} ID. Nothing to do here. Bailing out !!!", Level.WARN)
                return false
            }
        } else {
            // LENIENT work mode - just ignore
        }
        // This should just check the entry for the given parentMember, and then look if the variant is in 'installed' list
        synchronized(lookupMap) {
            // First things first, check if we have this in the map
            // we will do this by forming up a [HullmodExotica, FleetMemberAPI] pair as a key
            val hullmodExoticKey = HullmodExoticKey(
                    hullmodExotic = hullmodExotic,
                    parentFleetMemberId = parentFleetMember.id
            )
            val currentInstallData = synchronized(lookupMap) { lookupMap[hullmodExoticKey] }

            // If we do not have install data, then obviously we should bail out and return false
            if (currentInstallData == null) {
                logIfOverMinLogLevel("There was no InstallData for this key ${hullmodExoticKey}, bailing out and returning false", Level.ERROR)
                return false
            }

            // STRICT vs LENIENT - well... this is going to be rough.
            // When the Refit screen used to work, there was no 'shouldRemove***' method(s) at all.
            // So for STRICT - it's easy; do what we're doing right now: check if it's installed on and is in the variant list
            // But for LENIENT - lets just check if it's in the variants list and then improve from there ...
            // On the other hand/thought - why not try making these two the same?
            // UPDATE: for some reason, the list of variants we get from the root module *does not* equal any of the variants
            // in the "all module variants" list, so similar to before with the 'is expected' - lets check via hullspec hullID
            if (workMode == HullmodExoticHandlerWorkMode.STRICT) {
                // STRICT work mode - validate if the variant is both in the 'installed on' list and 'module variants' list
                val isInstalledOn = currentInstallData.listOfVariantsWeInstalledOn.contains(variant)
                val isInVariantsList = currentInstallData.listOfAllModuleVariants.contains(variant)
                // And just return that, since we should allow uninstalling if we have it installed or prevent uninstalling if we dont
                return isInstalledOn and isInVariantsList
            } else {
                // LENIENT work mode - similar to strict, except we won't be doing a literal contains() but rather a slightly
                // more lax check - via the hullSpec.hullID
                val isInstalledOn = currentInstallData.listOfVariantsWeInstalledOn.contains(variant)
                val isInVariantsList = currentInstallData.listOfAllModuleVariants
                        .map { moduleVariant -> moduleVariant.hullSpec.hullId }
                        .contains(variant.hullSpec.hullId)
                // And just return whether we're both installed and in the variants list
                return isInstalledOn and isInVariantsList
            }
        }
    }

    /**
     * Method for removing the [HullmodExotic] from a [ShipVariantAPI] by checking if we had already installed it
     * and that the [ExoticHullmod] installed on it has a valid hullmod ID. Returns whether the removal was successful.
     *
     * @return whether the hullmod exotic was successfully removed or not
     *
     * @param hullmodExotic the HullmodExotic in question
     * @param parentFleetMember the [FleetMemberAPI] of the root module, or the whole ship's if it's a single-module ship
     * @param variant the variant to check
     * @param workMode the [HullmodExoticHandlerWorkMode] in which we're **currently** operating, because we do things differently for [HullmodExoticHandlerWorkMode.LENIENT] and [HullmodExoticHandlerWorkMode.STRICT] work modes
     */
    private fun removeHullmodExoticFromVariant(
            hullmodExotic: HullmodExotic,
            parentFleetMember: FleetMemberAPI,
            variant: ShipVariantAPI,
            workMode: HullmodExoticHandlerWorkMode
    ): Boolean {
        // STRICT vs LENIENT - for STRICT we should be reducing the 'installed on' list; for LENIENT - meh, lets try not to.
        synchronized(lookupMap) {
            // Now, we get the install data, check whether the 'variant' is in 'installed variant' list, if yes, we "uninstall" it
            // and finally unset that member from the list of installed variants
            val hullmodExoticKey = HullmodExoticKey(
                    hullmodExotic = hullmodExotic,
                    parentFleetMemberId = parentFleetMember.id
            )
            val currentInstallData = getDataForKey(hullmodExoticKey)

            return if (currentInstallData.isPresent()) {
                val installData = currentInstallData.get()
                val installedOnVariants = installData.listOfVariantsWeInstalledOn

                // If we're in the list, remove and unset from the list
                if (installedOnVariants.contains(variant)) {
                    // Grab the ExoticHullmod by it's key and uninstall from this variant
                    val hullmodId = hullmodExotic.getHullmodId()
                    val hullmodOptional = ExoticHullmodLookup.getFromMap(hullmodId = hullmodId)
                    if (hullmodOptional.isPresent()) {
                        val exoticHullmodInstance = hullmodOptional.get()

                        // Since single-moduled ship always return 'null' for variant.statsForOpCosts, and we don't care
                        // whether the ship is multimodule or singlemodule here, lets grab a non-null variant of them.
                        val statsToUse = getNonNullStatsToUse(parentFleetMember, variant)
                        exoticHullmodInstance.removeEffectsBeforeShipCreation(
                                hullSize = variant.hullSpec.hullSize,
                                stats = statsToUse,
                                id = exoticHullmodInstance.hullModId
                        )

                        // Now that we've uninstalled it, lets unset it from the list of installed variants and
                        // update the lookup map
                        // STRICT vs LENIENT - for STRICT we will reduce the 'installed on' list, for LENIENT we will not
                        val installListToUse = if (workMode == HullmodExoticHandlerWorkMode.STRICT) {
                            // STRICT work mode - reduce the 'installed on' list by removing the variant we will remove from
                            val newInstalledOnList = installedOnVariants.toMutableList()
                            newInstalledOnList.remove(variant)

                            newInstalledOnList
                        } else {
                            // LENIENT work mode - just do nothing
                            installedOnVariants
                        }
                        lookupMap[hullmodExoticKey] = installData.copy(
                                parentFleetMemberAPI = installData.parentFleetMemberAPI,
                                listOfExpectedVariants = installData.listOfExpectedVariants,
                                listOfVariantsWeInstalledOn = installListToUse
                        )
                        logIfOverMinLogLevel("The exoticHullmod ${exoticHullmodInstance.hullModId} has been removed and lookup map has been updated. [${workMode}] Old installed list size: ${installedOnVariants.size}, new install list size: ${installListToUse.size}", Level.INFO)

                        // Return 'true' since we should uninstall/remove from this variant since we successfully logically removed it
                        true
                    } else {
                        logIfOverMinLogLevel("No ExoticHullmod with id ${hullmodId} was found !!!", Level.ERROR)

                        // Return 'false' since we should not uninstall/remove from this variant because we couldn't find
                        // the ExoticHullmod we applied on it - this is a funny "should never happen" edge-case that is here
                        // just for completeness sake
                        false
                    }
                } else {
                    logIfOverMinLogLevel("The 'variant' was not in the 'installedOnVariants' list", Level.ERROR)

                    // Return 'false' since we should not uninstall/remove from this variant because we never installed on it
                    false
                }
            } else {
                logIfOverMinLogLevel("There was no installed data for key: ${hullmodExoticKey}", Level.ERROR)

                // Return 'false' since we should not uninstall/remove from this variant because we never had InstallData for it
                false
            }
        }
    }

    /**
     * Utility method that grabs the [ExoticHullmod] by it's ID, and calls it's [ExoticHullmod.removeEffectsBeforeShipCreation]
     * on variants we installed on, before nuking the key from the [lookupMap]
     *
     * @param exoticHullmodId the hullmod ID of the [ExoticHullmod]
     * @param fleetMember the [FleetMemberAPI] on whose variants' we want to uninstall the ExoticHullmod from
     */
    fun removeHullmodExoticFromFleetMember(exoticHullmodId: String, fleetMember: FleetMemberAPI) {
        synchronized(lookupMap) {
            // Obviously, using a Set for this was a wrong idea that led to the ever-growing problem in the first place
            // Somehow, the keys were matched fine in the set so the keysToRemove was always of either size 1 or 0
            // However, many such "duplicate" keys show up in the map just fine. Dunno why but - they do.
            val keysToRemove = mutableListOf<HullmodExoticKey>()

            // Grab the ExoticHullmod instance
            val exoticHullmodOptional = ExoticHullmodLookup.getFromMap(exoticHullmodId)
            val exoticHullmod: ExoticHullmod
            if (exoticHullmodOptional.isPresent()) {
                exoticHullmod = exoticHullmodOptional.get()
            } else {
                logIfOverMinLogLevel("removeHullmodExoticFromFleetMember()\texotic hullmod with ID ${exoticHullmodId} not found !!! bailing out", Level.ERROR)
                return
            }

            val allKeys = grabAllKeysForParticularFleetMember(fleetMember)
            for (key in allKeys) {
                val exoticHandlerDataOptional = getDataForKey(key)
                if (exoticHandlerDataOptional.isPresent()) {
                    val exoticHandlerData = exoticHandlerDataOptional.get()
                    // Lets only concern ourselves with keys that have to do with *the* hullmod we want to remove
                    if (areHullmodIDsEqual(key.hullmodExotic.getHullmodId(), exoticHullmod.hullModId)) {
                        for (variant in exoticHandlerData.listOfVariantsWeInstalledOn) {
                            val variantHullSize = variant.hullSpec.hullSize
                            // Since single-moduled ship always return 'null' for variant.statsForOpCosts, and we don't care
                            // whether the ship is multimodule or singlemodule here, lets grab a non-null variant of them.
                            val statsToUse = getNonNullStatsToUse(fleetMember, variant)
                            exoticHullmod.removeEffectsBeforeShipCreation(variantHullSize, statsToUse, exoticHullmod.hullModId)
                            // Lets not keep track of keys to remove here, but outside of this loop, this spot made sense while
                            // we used a Set to keep track of the keys, so multiple adds of the same key wouldn't cause problems.
                            // Now - we might end up wanting to remove more keys than the map has
                        }
                        // Lets keep track of 'removed' keys here because why not
                        keysToRemove.add(key)
                    }
                }
            }
            // Log before
            logIfOverMinLogLevel("removeHullmodExoticFromFleetMember()\tkeysToRemove.size: ${keysToRemove.size}\tkeysToRemove: ${keysToRemove}", Level.INFO)
            logIfOverMinLogLevel("removeHullmodExoticFromFleetMember()\tlookupMap before cleaning: ${lookupMap}", Level.INFO)
            logIfOverMinLogLevel("removeHullmodExoticFromFleetMember()\tlookupMap.size(): ${lookupMap.size}", Level.INFO)


            // Now, cleanup the lookupMap from all the to-remove keys
            for (keyToRemove in keysToRemove) {
                // Somehow, lookupMap.remove(keyToRemove) doesn't quite work here.... so lets try something else instead
                synchronized(lookupMap) {
                    val mapIterator = lookupMap.entries.iterator()
                    while (mapIterator.hasNext()) {
                        val mapEntry = mapIterator.next()
                        if (mapEntry.key == keyToRemove) {
                            mapIterator.remove()
                        }
                    }
                }
            }
            // Log after
            logIfOverMinLogLevel("<-- removeHullmodExoticFromFleetMember()\tlookupMap.size(): ${lookupMap.size}\tlookupMap AFTER cleaning: ${lookupMap}", Level.INFO)
        }
    }

    /**
     * Utility method to check if a [HullmodExoticKey] entry exists for a given [HullmodExotic] and [FleetMemberAPI] combination.
     *
     * @param hullmodExotic the hullmod exotic to look up
     * @param fleetMember the fleet member to look up
     * @return whether the [lookupMap] contains the key formed by hullmodExotic and fleetMember's ID
     */
    private fun doesEntryExist(hullmodExotic: HullmodExotic, fleetMember: FleetMemberAPI): Boolean {
        synchronized(lookupMap) {
            val hullmodExoticKey = HullmodExoticKey(
                    hullmodExotic = hullmodExotic,
                    parentFleetMemberId = fleetMember.id
            )

            return lookupMap.contains(hullmodExoticKey)
        }
    }

    /**
     * Utility method to grab **all** [HullmodExoticKey] keys matching a given [FleetMemberAPI] regardless of their [HullmodExotic]
     *
     * @param fleetMemberAPI the fleet member to look up the keys for
     * @return the list of keys pertaining to this fleet member
     */
    private fun grabAllKeysForParticularFleetMember(fleetMemberAPI: FleetMemberAPI): List<HullmodExoticKey> {
        synchronized(lookupMap) {
            val retVal = mutableSetOf<HullmodExoticKey>()
            for (key in lookupMap.keys) {
                if (areFleetMemberIDsEqual(key.parentFleetMemberId, fleetMemberAPI.id)) {
                    retVal.add(key)
                }
            }
            // And return all keys mentioning this fleetMember
            return retVal.toList()
        }
    }

    /**
     * Utility method that returns [HullmodExoticInstallData] for a given [HullmodExoticKey]
     *
     * @param hullmodExoticKey the key to look up the data for
     * @return returns an [Optional] that may or may not contain the data for the passed-in key
     */
    private fun getDataForKey(hullmodExoticKey: HullmodExoticKey): Optional<HullmodExoticInstallData> {
        return Optional.ofNullable(synchronized(lookupMap) { lookupMap.get(hullmodExoticKey) })
    }

    /**
     * Useless utility method that clearly checks whether the two HullmodIDs (strings) are equal.
     * It is currently doing it via a mere equality (`==`) check.
     *
     * @param id1 the hullmod ID of the first hullmod
     * @param id2 the hullmod ID of the second hullmod
     * @return whether they are equal
     */
    private fun areHullmodIDsEqual(id1: String, id2: String): Boolean {
        return id1 == id2
    }

    /**
     * Useless utility method that clearly checks whether the two FleetMemberIDs (strings) are equal.
     * It is currently doing it via a mere equality (`==`) check.
     *
     * @param id1 the ID of the first fleet member
     * @param id2 the ID of the second fleet member
     * @return whether they are equal
     */
    private fun areFleetMemberIDsEqual(id1: String, id2: String): Boolean {
        return id1 == id2
    }

    /**
     * Clears the [lookupMap] - should be called for every "new game" or "laoded game" to clean up the junk
     * that is probably left by fake modules' FleetMemberAPIs
     */
    public fun reinitialize() {
        lookupMap.clear()
    }

    /**
     * Because there's such a big discrepancy between running from the "Exotica Technologies" colony menu OR the
     * Refit screen, this needs to exist. So that we can either do tightly and properly checked code, or a more
     * "lax" version hopefully works ...
     */
    private fun runningFromRefitScreen(): Boolean {
        return StarsectorAPIInteractor.runningFromRefitScreen()
    }

    /**
     * Because, for some reason, single-module ships' [ShipVariantAPI.getStatsForOpCosts] returns [null], but works fine
     * for multimoduled ships, it is somewhat safe to assume that if there is only one variant (no child variants)
     * we can just use the [fleetMember]'s stats instead of the [variant]'s
     *
     * @param fleetMember the 'parent' (root module's) fleet member (or the whole ship for single-module ships)
     * @param variant the variant for which we should return stats, if available
     *
     * @return non-null stats that we should use; defaults to [ShipVariantAPI.getStatsForOpCosts] and
     * falls back to [FleetMemberAPI.getStats] if variant's stats were [null]
     */
    internal fun getNonNullStatsToUse(fleetMember: FleetMemberAPI, variant: ShipVariantAPI): MutableShipStatsAPI {
        return if(variant.statsForOpCosts == null) {
            fleetMember.stats
        } else {
            variant.statsForOpCosts
        }
    }

    /**
     * To avoid installing on the wrong list of [ShipModifications] from the [ShipModLoader], we'll use this method
     * to try and pinch out the mods from the concrete variant by using [ShipModLoader.getFromVariant] - if no such
     * mods are found, we'll fall back to [ShipModLoader.get]
     *
     * @return the found ship modifications list
     *
     * @see ShipModLoader.get
     * @see ShipModLoader.getFromVariant
     */
    internal fun getCorrectMods(fleetMember: FleetMemberAPI, variant: ShipVariantAPI): ShipModifications? {
        val fleetMemberMods = ShipModLoader.get(fleetMember, variant)
        val variantMods = ShipModLoader.getFromVariant(variant)

        return if (variantMods != null) {
            variantMods
        } else {
            fleetMemberMods
        }
    }

    /**
     * Inner "class" holding certain "flows" which are a somewhat long list of steps/actions to perform, such as:
     * - checking and installing on all child modules' variants
     * - checking and installing on member (root) module variant
     * - checking and removing from all child modules' variants
     * - checking and removing from member (root) module variant
     *
     * @see CheckAndInstallOnAllChildModulesVariants
     * @see CheckAndInstallOnMemberModule
     * @see CheckAndRemoveFromAllChildModulesVariants
     * @see CheckAndRemoveFromMemberModule
     */
    class Flows {
        /**
         * For each child module of [fleetMember] or rather, the fleetMember's [ShipVariantAPI] variant,
         * the method will first call [shouldInstallHullmodExoticToVariant] followed by [installHullmodExoticToVariant],
         * with their respective callbacks in proper places
         */
        companion object {

            /**
             * A "flow" method that checks whether we should install the [HullmodExotic] on all child modules of a [fleetMember],
             * throws a [OnShouldCallback] for each of them before proceeding to install the hullmod exotic on all of them
             * (meeting the "should install" criteria) after which a [OnInstallToChildModuleCallback] is called for each of them.
             *
             * @param fleetMember the [FleetMemberAPI] of the root module, from which all child modules will be obtained
             * @param fleetMemberVariant the [ShipVariantAPI] of the root module, so that we can generate a list of all variants on which we should install
             * @param hullmodExotic the [HullmodExotic] to install on these child modules' variants
             * @param onShouldCallback the callback to invoke for each of the child modules' variants with their "should install" result
             * @param onInstallToChildModuleCallback the callback to invoke for each of the child modules' variants after installing with their installation result
             *
             * @see shouldInstallHullmodExoticToVariant
             * @see installHullmodExoticToVariant
             */
            @JvmStatic
            fun CheckAndInstallOnAllChildModulesVariants(
                    fleetMember: FleetMemberAPI,
                    fleetMemberVariant: ShipVariantAPI,
                    hullmodExotic: HullmodExotic,
                    onShouldCallback: OnShouldCallback,
                    onInstallToChildModuleCallback: OnInstallToChildModuleCallback
            ) {
                // First things first, figure out whether we're running from Refit or Exotica screen
                val isFromRefitScreen = runningFromRefitScreen()
                val workModeOptional = getWorkModeOptional()
                if (workModeOptional.isEmpty()) {
                    // If empty, log error and return.
                    logIfOverMinLogLevel("Illegal state detected, tried to CheckAndInstallOnAllChildModulesVariants() from neither Refit or ExoticaTech screens! Bailing out...", Level.ERROR)
                    return
                }
                val workMode = workModeOptional.get()

                // Carry on

                val childModuleVariants = getChildModuleVariantList(fleetMember)
                val allVariantsList = if (isFromRefitScreen) {
                    // For refit screen, we'll include the member's refit variant
                    childModuleVariants
                            .toMutableList()
                            .apply { add(fleetMemberVariant) }
                            .apply { add(fleetMember.checkRefitVariant()) }
                            .toList()
                } else {
                    // Otherwise, we wont
                    childModuleVariants
                            .toMutableList()
                            .apply { add(fleetMemberVariant) }
                            .toList()
                }

                logIfOverMinLogLevel("onInstall()\tchildModuleVariants: ${childModuleVariants}", Level.INFO)
                if (childModuleVariants.isEmpty().not()) {
                    // Print out the module slot list, and generate a small map of
                    // <this exotic>, <parent FleetMember> + <list of expected variants> + <list of variants we installed on>
                    //
                    // Since we've already created the "all variants" and "child module" lists, lets just use them now
                    logIfOverMinLogLevel("onInstall()\tvariantList: ${allVariantsList}", Level.INFO)

                    // Iterate through each slot, getting their variant and trying to install there
                    for (moduleVariant in childModuleVariants) {
                        val mods = getCorrectMods(fleetMember, moduleVariant)
                        logIfOverMinLogLevel("onInstall()\tmods: ${mods}", Level.INFO)
                        mods?.let { nonNullMods ->
                            val shouldInstallOnModuleVariant = HullmodExoticHandler.shouldInstallHullmodExoticToVariant(
                                    hullmodExotic = hullmodExotic,
                                    parentFleetMember = fleetMember,
                                    variant = moduleVariant,
                                    variantList = Optional.of(allVariantsList),
                                    workMode = workMode
                            )
                            val underExoticLimit = nonNullMods.isUnderExoticLimit(fleetMember)
                            val shouldProceedWithInstallation = shouldInstallOnModuleVariant && underExoticLimit
                            onShouldCallback.execute(shouldProceedWithInstallation, moduleVariant)
                            logIfOverMinLogLevel("onInstall()\tshouldInstallOnModuleVariant: ${shouldInstallOnModuleVariant}, underExoticLimit: ${underExoticLimit}, variant: ${moduleVariant}", Level.INFO)
                            if (shouldProceedWithInstallation) {
                                // Lets try starting from the HullmodExoticHandler installation
                                val installResult = HullmodExoticHandler.installHullmodExoticToVariant(
                                        hullmodExotic = hullmodExotic,
                                        parentFleetMember = fleetMember,
                                        variant = moduleVariant,
                                        workMode = workMode
                                )

                                onInstallToChildModuleCallback.execute(installResult, moduleVariant, nonNullMods)
                            }
                        }
                    }
                }
            }

            /**
             * A "flow" method that checks whether we should install the [HullmodExotic] on the root module of a [fleetMember]
             * (or rather, the whole [fleetMember] itself for single-module ships), calls a [OnShouldCallback] with the result
             * before proceeding to install after which a [OnInstallToMemberCallback] is called with the installation result
             *
             * @param member the [FleetMemberAPI] of the root module
             * @param memberVariant the [ShipVariantAPI] of the root module, so that we can generate a list of all variants on which we should install
             * @param hullmodExotic the [HullmodExotic] to install on this fleet member's variant
             * @param onShouldCallback the callback to invoke with the "should install" result
             * @param onInstallCallback the callback to invoke for the root module's variant after installing, with the installation result
             *
             * @see shouldInstallHullmodExoticToVariant
             * @see installHullmodExoticToVariant
             */
            @JvmStatic
            fun CheckAndInstallOnMemberModule(
                    member: FleetMemberAPI,
                    memberVariant: ShipVariantAPI,
                    hullmodExotic: HullmodExotic,
                    onShouldCallback: OnShouldCallback,
                    onInstallCallback: OnInstallToMemberCallback
            ) {
                // First things first, figure out whether we're running from Refit or Exotica screen
                val isFromRefitScreen = runningFromRefitScreen()
                val workModeOptional = getWorkModeOptional()
                if (workModeOptional.isEmpty()) {
                    // If empty, log error and return.
                    logIfOverMinLogLevel("Illegal state detected, tried to CheckAndInstallOnMemberModule() from neither Refit or ExoticaTech screens! Bailing out...", Level.ERROR)
                    return
                }
                val workMode = workModeOptional.get()

                // Carry on
                val shouldShareEffectToOtherModules = hullmodExotic.shouldShareEffectToOtherModules(null, null)

                // This 'variantList' is complicating things alot
                // because the Optional must be present if we don't already have an entry in HullmodExoticHandler's map
                // but if we do, we can just send whatever because it won't be used.
                // Taking non-modular ships into account, we should check if we have an entry in the lookup map
                // and if we do - we can send Optional.empty() since it won't be used
                // but if we do not - we need to generate a list of our variants (which is most likely just this variant
                // because the modules are handled first up there and they'd have already setup the whole entry
                val variantListOptional = if (
                        HullmodExoticHandler.doesEntryExist(
                                hullmodExotic = hullmodExotic,
                                fleetMember = member
                        )
                ) {
                    // Entry exists, lets just send an empty optional
                    Optional.empty()
                } else {
                    // Entry does not exist, lets just create one, even though we're probably not a multimodule ship
                    // If we need to share, grab all child modules and add the member to it - otherwise, start from empty list
                    val variantsList = if (isFromRefitScreen) {
                        getInitialVariantsListForMember(member, shouldShareEffectToOtherModules)
                                // Change to mutable so we can add our variant
                                .toMutableList()
                                // Obviously, add the 'member' variant to the list as well
                                .apply { add(memberVariant) }
                                // And add the 'member' refit variant to the list as well
                                .apply { add(member.checkRefitVariant()) }
                    } else {
                        getInitialVariantsListForMember(member, shouldShareEffectToOtherModules)
                                // Change to mutable so we can add our variant
                                .toMutableList()
                                // Obviously, add the 'member' variant to the list as well
                                .apply { add(memberVariant) }
                    }
                    val variants = variantsList.toList()

                    // Return the optional of the list
                    Optional.of(variants)
                }

                // Now, get the mods
                val mods = getCorrectMods(member, memberVariant)
                mods?.let { nonNullMods ->
                    val shouldInstallOnMemberVariant = HullmodExoticHandler.shouldInstallHullmodExoticToVariant(
                            hullmodExotic = hullmodExotic,
                            parentFleetMember = member,
                            variant = memberVariant,
                            variantList = variantListOptional,
                            workMode = workMode
                    )
                    val underExoticLimit = nonNullMods.isUnderExoticLimit(member)
                    val shouldProceedWithInstallation = shouldInstallOnMemberVariant && underExoticLimit
                    onShouldCallback.execute(shouldProceedWithInstallation, memberVariant)
                    logIfOverMinLogLevel("onInstall()\tshouldInstallOnMemberVariant: ${shouldInstallOnMemberVariant}, underExoticLimit: ${underExoticLimit}, variant: ${memberVariant}", Level.INFO)
                    if (shouldProceedWithInstallation) {
                        val installResult = HullmodExoticHandler.installHullmodExoticToVariant(
                                hullmodExotic = hullmodExotic,
                                parentFleetMember = member,
                                variant = memberVariant,
                                workMode = workMode
                        )
                        onInstallCallback.execute(installResult, memberVariant, nonNullMods)
                    }
                }
            }

            /**
             * A "flow" method that checks whether we should remove the [HullmodExotic] from all child modules of a [fleetMember],
             * throws a [OnShouldCallback] for each of them before proceeding to remove the hullmod exotic from all of them
             * (meeting the "should remove" criteria) after which a [OnRemoveFromChildModuleCallback] is called for each of them.
             *
             * @param fleetMember the [FleetMemberAPI] of the root module, from which all child modules will be obtained
             * @param hullmodExotic the [HullmodExotic] to remove from these child modules' variants
             * @param onShouldCallback the callback to invoke for each of the child modules' variants with their "should remove" result
             * @param onRemoveFromChildModuleCallback the callback to invoke for each of the child modules' variants after removing with their removal result
             *
             * @see shouldRemoveHullmodExoticFromVariant
             * @see removeHullmodExoticFromVariant
             */
            @JvmStatic
            fun CheckAndRemoveFromAllChildModulesVariants(
                    fleetMember: FleetMemberAPI,
                    hullmodExotic: HullmodExotic,
                    onShouldCallback: OnShouldCallback,
                    onRemoveFromChildModuleCallback: OnRemoveFromChildModuleCallback
            ) {
                // First things first, figure out whether we're running from Refit or Exotica screen
                val workModeOptional = getWorkModeOptional()
                if (workModeOptional.isEmpty()) {
                    // If empty, log error and return.
                    logIfOverMinLogLevel("Illegal state detected, tried to CheckAndRemoveFromAllChildModulesVariants() from neither Refit or ExoticaTech screens! Bailing out...", Level.ERROR)
                    return
                }
                val workMode = workModeOptional.get()

                //Lets go through all 'installed modules' and uninstall from them, instead of these module slots
                // that were the root cause of the whole problem... damn you 3-5am copypasted code ...
                val hullmodExoticKey = HullmodExoticKey(
                        hullmodExotic = hullmodExotic,
                        parentFleetMemberId = fleetMember.id
                )
                val currentInstallData = synchronized(lookupMap) { lookupMap[hullmodExoticKey] }
                val installedOnVariantsList = if(currentInstallData != null) {
                    currentInstallData.listOfVariantsWeInstalledOn
                } else {
                    logIfOverMinLogLevel("No InstallData found for key ${hullmodExoticKey}", Level.ERROR)
                    return
                }

                // Carry on now that we have install data

                // If we have slots, start doing what needs to be done
                for (installedOnVariant in installedOnVariantsList) {
                    val mods = getCorrectMods(fleetMember, installedOnVariant)
                    mods?.let { nonNullMods ->
                        // Since this can be *any* HullmodExotic referencing their own ExoticHullmods, we should first
                        // check the ExoticHullmodLookup map for any instances of the exotic hullmod.
                        // And if we find one, we'll just pass it over to the HullmodExoticHandler to remove it from this fleetmember
                        val shouldRemoveFromModuleVariant = HullmodExoticHandler.shouldRemoveHullmodExoticFromVariant(
                                hullmodExotic = hullmodExotic,
                                parentFleetMember = fleetMember,
                                variant = installedOnVariant,
                                workMode = workMode
                        )
                        onShouldCallback.execute(
                                onShouldResult = shouldRemoveFromModuleVariant,
                                moduleVariant = installedOnVariant
                        )
                        logIfOverMinLogLevel("onDestroy()\tshouldRemoveFromModuleVariant: ${shouldRemoveFromModuleVariant}, variant: ${installedOnVariant}", Level.INFO)
                        if (shouldRemoveFromModuleVariant) {
                            val removeFromChildModuleResult = HullmodExoticHandler.removeHullmodExoticFromVariant(
                                    hullmodExotic = hullmodExotic,
                                    parentFleetMember = fleetMember,
                                    variant = installedOnVariant,
                                    workMode = workMode
                            )

                            onRemoveFromChildModuleCallback.execute(
                                    onRemoveResult = removeFromChildModuleResult,
                                    moduleVariant = installedOnVariant,
                                    moduleVariantMods = nonNullMods
                            )
                        }
                    }
                }
            }

            /**
             * A "flow" method that checks whether we should remove the [HullmodExotic] from the root module of a [fleetMember]
             * (or rather, the whole [fleetMember] itself for single-module ships), calls a [OnShouldCallback] with the result
             * before proceeding to remove after which a [OnRemoveFromMemberCallback] is called with the removal result
             *
             * @param fleetMember the [FleetMemberAPI] of the root module
             * @param fleetMemberVariant the [ShipVariantAPI] of the root module, so that we can generate a list of all variants from which we should remove
             * @param hullmodExotic the [HullmodExotic] to remove from this fleet member's variant
             * @param onShouldCallback the callback to invoke with the "should remove" result
             * @param onRemoveFromMemberModuleCallback the callback to invoke for the root module's variant after removing, with the removal result
             *
             * @see shouldRemoveHullmodExoticFromVariant
             * @see removeHullmodExoticFromVariant
             */
            @JvmStatic
            fun CheckAndRemoveFromMemberModule(
                    fleetMember: FleetMemberAPI,
                    fleetMemberVariant: ShipVariantAPI,
                    hullmodExotic: HullmodExotic,
                    onShouldCallback: OnShouldCallback,
                    onRemoveFromMemberModuleCallback: OnRemoveFromMemberCallback
            ) {
                // First things first, figure out whether we're running from Refit or Exotica screen
                val workModeOptional = getWorkModeOptional()
                if (workModeOptional.isEmpty()) {
                    // If empty, log error and return.
                    logIfOverMinLogLevel("Illegal state detected, tried to CheckAndRemoveFromMemberModule() from neither Refit or ExoticaTech screens! Bailing out...", Level.ERROR)
                    return
                }
                val workMode = workModeOptional.get()

                // Carry on
                val mods = getCorrectMods(fleetMember, fleetMemberVariant)
                mods?.let { nonNullMods ->
                    val shouldRemoveFromMemberVariant = HullmodExoticHandler.shouldRemoveHullmodExoticFromVariant(
                            hullmodExotic = hullmodExotic,
                            parentFleetMember = fleetMember,
                            variant = fleetMemberVariant,
                            workMode = workMode
                    )
                    onShouldCallback.execute(
                            onShouldResult = shouldRemoveFromMemberVariant,
                            moduleVariant = fleetMemberVariant
                    )
                    logIfOverMinLogLevel("onDestroy()\tshouldRemoveFromMemberVariant: ${shouldRemoveFromMemberVariant}, variant: ${fleetMemberVariant}", Level.INFO)
                    if (shouldRemoveFromMemberVariant) {
                        val removeFromMemberResult = HullmodExoticHandler.removeHullmodExoticFromVariant(
                                hullmodExotic = hullmodExotic,
                                parentFleetMember = fleetMember,
                                variant = fleetMemberVariant,
                                workMode = workMode
                        )

                        onRemoveFromMemberModuleCallback.execute(
                                onRemoveResult = removeFromMemberResult,
                                moduleVariant = fleetMemberVariant,
                                moduleVariantMods = nonNullMods
                        )
                    }
                }
            }
        }

        /**
         * Interface for a callback to be received after a "should remove" or "should install" evaluation was performed
         * on a module's variant
         */
        interface OnShouldCallback {
            fun execute(onShouldResult: Boolean, moduleVariant: ShipVariantAPI)
        }

        /**
         * Interface for a callback to be received after a installation was performed on a child module
         */
        interface OnInstallToChildModuleCallback {
            fun execute(onInstallResult: Boolean, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications)
        }

        /**
         * Interface for a callback to be received after a installation was performed on a root module
         */
        interface OnInstallToMemberCallback {
            fun execute(onInstallResult: Boolean, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications)
        }

        /**
         * Interface for a callback to be received after a removal (uninstallation) from a child module
         */
        interface OnRemoveFromChildModuleCallback {
            fun execute(onRemoveResult: Boolean, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications)
        }

        /**
         * Interface for a callback to be received after a removal (uninstallation) from the root module
         */
        interface OnRemoveFromMemberCallback {
            fun execute(onRemoveResult: Boolean, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications)
        }
    }

    /**
     * Method for returning an [Optional] containing the [HullmodExoticHandlerWorkMode] which corresponds
     * to the current screen we're in.
     *
     * For Refit screen - it's going to be [HullmodExoticHandlerWorkMode.LENIENT]
     *
     * For Exotica Technologies screen - it's going to be [HullmodExoticHandlerWorkMode.STRICT]
     *
     * For any other place - it's going to be [Optional.empty]
     *
     * @return the Optional containing the work mode, or nothing
     */
    private fun getWorkModeOptional(): Optional<HullmodExoticHandlerWorkMode> {
        return if (runningFromRefitScreen()) {
            Optional.of(HullmodExoticHandlerWorkMode.LENIENT)
        } else if (runningFromExoticaTechnologiesScreen()) {
            Optional.of(HullmodExoticHandlerWorkMode.STRICT)
        } else {
            Optional.empty()
        }
    }

    private fun logIfOverMinLogLevel(logMsg: String, logLevel: Level) {
        shouldLog(
                logMsg = logMsg,
                logger = logger,
                logLevel = logLevel,
                minLogLevel = MIN_LOG_LEVEL
        )
    }

    /**
     * Method that returns an "initial" list of variants for a given [member].
     * Depending on [shouldShareEffectToOtherModules], it returns either an empty list or [getChildModuleVariantList]
     *
     * @param member the [FleetMemberAPI] to return an initial list of variants for
     * @param shouldShareEffectToOtherModules whether the [HullmodExotic] should share effects to other modules or not
     * @return the initial list of variants
     */
    private fun getInitialVariantsListForMember(member: FleetMemberAPI, shouldShareEffectToOtherModules: Boolean): List<ShipVariantAPI> {
        return if(shouldShareEffectToOtherModules) { getChildModuleVariantList(member) } else { listOf() }
    }


    // test only methods here

    /**
     * Internally calls into [grabAllKeysForParticularFleetMember] - please, do not use this in production code
     *
     * Use the [Flows] methods instead of any of these
     */
    @VisibleForTesting
    @TestOnly
    internal fun testsOnly_grabAllKeysForParticularFleetMember(fleetMemberAPI: FleetMemberAPI): List<HullmodExoticKey> {
        return grabAllKeysForParticularFleetMember(fleetMemberAPI)
    }

    /**
     * Internally calls into [getDataForKey] - please, do not use this in production code
     *
     * Use the [Flows] methods instead of any of these
     */
    @VisibleForTesting
    @TestOnly
    internal fun testsOnly_getDataForKey(hullmodExoticKey: HullmodExoticKey): Optional<HullmodExoticInstallData> {
        return getDataForKey(hullmodExoticKey)
    }

    /**
     * Internally clears the [lookupMap] - please, do not use this in production code
     *
     * Use the [Flows] methods instead of any of these
     */
    @VisibleForTesting
    @TestOnly
    internal fun testsOnly_clearLookupMap() {
        lookupMap.clear()
    }

    /**
     * Internally calls into [shouldInstallHullmodExoticToVariant] - please, do not use this in production code
     *
     * Use the [Flows] methods instead of any of these
     */
    @VisibleForTesting
    @TestOnly
    internal fun testsOnly_shouldInstallHullmodExoticToVariant(hullmodExotic: HullmodExotic, parentFleetMember: FleetMemberAPI, variant: ShipVariantAPI, variantList: Optional<List<ShipVariantAPI>>): Boolean {
        return shouldInstallHullmodExoticToVariant(hullmodExotic, parentFleetMember, variant, variantList, HullmodExoticHandlerWorkMode.STRICT)
    }

    /**
     * Internally calls into [installHullmodExoticToVariant] - please, do not use this in production code
     *
     * Use the [Flows] methods instead of any of these
     */
    @VisibleForTesting
    @TestOnly
    internal fun testsOnly_installHullmodExoticToVariant(hullmodExotic: HullmodExotic, parentFleetMember: FleetMemberAPI, variant: ShipVariantAPI): Boolean {
        return installHullmodExoticToVariant(hullmodExotic, parentFleetMember, variant, HullmodExoticHandlerWorkMode.STRICT)
    }

    /**
     * Internally calls into [doesEntryExist] - please, do not use this in production code
     *
     * Use the [Flows] methods instead of any of these
     */
    @VisibleForTesting
    @TestOnly
    internal fun testsOnly_doesEntryExist(hullmodExotic: HullmodExotic, fleetMember: FleetMemberAPI): Boolean {
        return doesEntryExist(hullmodExotic, fleetMember)
    }

    /**
     * Internally calls into [shouldRemoveHullmodExoticFromVariant] - please, do not use this in production code
     *
     * Use the [Flows] methods instead of any of these
     */
    @VisibleForTesting
    @TestOnly
    internal fun testsOnly_shouldRemoveHullmodExoticFromVariant(hullmodExotic: HullmodExotic, parentFleetMember: FleetMemberAPI, variant: ShipVariantAPI): Boolean {
        return shouldRemoveHullmodExoticFromVariant(hullmodExotic, parentFleetMember, variant, HullmodExoticHandlerWorkMode.STRICT)
    }

    /**
     * Internally calls into [removeHullmodExoticFromVariant] - please, do not use this in production code
     *
     * Use the [Flows] methods instead of any of these
     */
    @VisibleForTesting
    @TestOnly
    internal fun testsOnly_removeHullmodExoticFromVariant(hullmodExotic: HullmodExotic, parentFleetMember: FleetMemberAPI, variant: ShipVariantAPI): Boolean {
        return removeHullmodExoticFromVariant(hullmodExotic, parentFleetMember, variant, HullmodExoticHandlerWorkMode.STRICT)
    }
}

/**
 * Data class holding all "install" data for a particular [FleetMemberAPI] so that we can track which Variants we installed
 * a particular [HullmodExotic] on, where we should also install it on, and where we can remove it from.
 */
data class HullmodExoticInstallData(
        val parentFleetMemberAPI: FleetMemberAPI,
        val listOfAllModuleVariants: List<ShipVariantAPI>,
        val listOfExpectedVariants: List<ShipVariantAPI>,
        val listOfVariantsWeInstalledOn: List<ShipVariantAPI>
)

/**
 * Data class used as a key for the [HullmodExoticHandler]'s lookup map
 */
data class HullmodExoticKey(
        val hullmodExotic: HullmodExotic,
        val parentFleetMemberId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HullmodExoticKey) return false

        // Compare IDs as Strings
        return hullmodExotic.getHullmodId().toString() == other.hullmodExotic.getHullmodId().toString()
                && parentFleetMemberId.toString() == other.parentFleetMemberId.toString()
    }

    override fun hashCode(): Int {
        return Objects.hash(
                hullmodExotic.getHullmodId().toString(),
                parentFleetMemberId.toString()
        )
    }

    override fun toString(): String {
        return "HullmodExoticKey{hullmodExoticId=${hullmodExotic.getHullmodId()}, parentFleetMemberId=${parentFleetMemberId}}"
    }
}

/**
 * Enum denoting the "work mode" in which the [HullmodExoticHandler] or it's "flows" are going to be operating.
 *
 * For reference:
 * - the Exotica Techonologies screen in the colony menu will be operating on a STRICT mode
 * - the Refit screen will be operating in a LENIENT mode
 *
 * @see STRICT
 * @see LENIENT
 */
enum class HullmodExoticHandlerWorkMode {
    /**
     * "Strict" work mode, where only expected variants will be installed on, and only installed on variants will be
     * uninstalled from. Anything non-conforming to the actual expected/installed instances will be discarded.
     *
     * Used only from "exotica technologies" colony screen.
     */
    STRICT,

    /**
     * "Lenient" work mode, where typically 'expected' variants will be looked and matched by their type, or
     * 'installed' variants won't be really respected so strictly.
     *
     * Used only from "refit" fleet screen.
     */
    LENIENT
}
