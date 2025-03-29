package exoticatechnologies.hullmods.exotics

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import exoticatechnologies.modifications.ShipModLoader
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.impl.HullmodExotic
import exoticatechnologies.util.datastructures.Optional
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
     * @param hullmodExotic the HullmodExotic in question
     * @param parentFleetMember the [FleetMemberAPI] of the root module, or the whole ship's if it's a single-module ship
     * @param variant the variant to check
     * @param variantList list of module variants belonging to the parent module (optional)
     */
    private fun shouldInstallHullmodExoticToVariant(hullmodExotic: HullmodExotic, parentFleetMember: FleetMemberAPI, variant: ShipVariantAPI, variantList: Optional<List<ShipVariantAPI>>, workMode: HullmodExoticHandlerWorkMode): Boolean {
        logIfOverMinLogLevel("--> shouldInstallHullmodExoticToVariant()\tvariant: ${variant}, runningFromRefit: ${runningFromRefitScreen()}", Level.INFO)
        // Fail fast if the variant already has the hullmod
        val hullmodId = hullmodExotic.getHullmodId()
        val alreadyHasHullmod = variant.hasHullMod(hullmodId)
        if (alreadyHasHullmod) {
            logIfOverMinLogLevel("shouldInstallHullmodExoticToVariant()\tVariant ${variant} already had hullmod with the ${hullmodId} ID. Nothing to do here. Bailing out !!!", Level.WARN)
//            return false
        }
        // Otherwise, prepare the key in the lookup map
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
                            listOfVariantsWeInstalledOn = emptyList()
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
     */
    private fun installHullmodExoticToVariant(hullmodExotic: HullmodExotic, parentFleetMember: FleetMemberAPI, variant: ShipVariantAPI, workMode: HullmodExoticHandlerWorkMode): Boolean {
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
                // Actually, lets not throw just yet
                // TODO change to throwing
                logIfOverMinLogLevel("installHullmodExoticToVariant()\t\t !!!!! Trying to install on a variant that's already been installed on, returning false!!!!!", Level.WARN)
                return false
            }

            // Now, the difference between LENIENT and STRICT will essentially fall down to the strict version
            // installing *only* on the expected variants, where the lenient one won't even look at the expected list,
            // as well as *not* reducing the 'expected' list because we'll get many more than originally expected ...

            if (workMode == HullmodExoticHandlerWorkMode.STRICT) {
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
                    return true
                } else {
                    logIfOverMinLogLevel("installHullmodExoticToVariant()\t\t !!!!! Trying to install on a variant that's NOT expected, returning false!!!!!", Level.WARN)
                    return false
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

                return true
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
     */
    private fun shouldRemoveHullmodExoticFromVariant(hullmodExotic: HullmodExotic, parentFleetMember: FleetMemberAPI, variant: ShipVariantAPI, workMode: HullmodExoticHandlerWorkMode): Boolean {
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

            //TODO update documentation so that it refers to both LENIENT and STRICT
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

    private fun removeHullmodExoticFromVariant(hullmodExotic: HullmodExotic, parentFleetMember: FleetMemberAPI, variant: ShipVariantAPI, workMode: HullmodExoticHandlerWorkMode): Boolean {
        // STRICT vs LENIENT - for STRICT we should be reducing the 'installed on' list; for LENIENT - meh, lets try not to.
        synchronized(lookupMap) {
            // Now, we get the install data, check whether the 'variant' is in 'installed variant' list, if yes, we "uninstall" it
            // and finally unset that member from the list of installed variants
            val hullmodExoticKey = HullmodExoticKey(
                    hullmodExotic = hullmodExotic,
                    parentFleetMemberId = parentFleetMember.id
            )
            val currentInstallData = getDataForKey(hullmodExoticKey)

            if (currentInstallData.isPresent()) {
                val installData = currentInstallData.get()
                val installedOnVariants = installData.listOfVariantsWeInstalledOn

                // If we're in the list, remove and unset from the list
                if (installedOnVariants.contains(variant)) {
                    // Grab the ExoticHullmod by it's key and uninstall from this variant
                    val hullmodId = hullmodExotic.getHullmodId()
                    val hullmodOptional = ExoticHullmodLookup.getFromMap(hullmodId = hullmodId)
                    if (hullmodOptional.isPresent()) {
                        val exoticHullmodInstance = hullmodOptional.get()

                        exoticHullmodInstance.removeEffectsBeforeShipCreation(
                                hullSize = variant.hullSpec.hullSize,
                                stats = variant.statsForOpCosts,
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

                        return true
                    } else {
                        logIfOverMinLogLevel("No ExoticHullmod with id ${hullmodId} was found !!!", Level.ERROR)

                        return false
                    }
                } else {
                    logIfOverMinLogLevel("The 'variant' was not in the 'installedOnVariants' list", Level.ERROR)

                    return false
                }
            } else {
                logIfOverMinLogLevel("There was no installed data for key: ${hullmodExoticKey}", Level.ERROR)

                return false
            }
        }
    }

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
                            exoticHullmod.removeEffectsBeforeShipCreation(variantHullSize, variant.statsForOpCosts, exoticHullmod.hullModId)
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
//            lookupMap.remove(keyToRemove)
                // Somehow, lookupMap.remove(keyToRemove) doesn't quite work here.... so lets try something else instead
                synchronized(lookupMap) {
                    val mapIterator = lookupMap.entries.iterator()
                    while (mapIterator.hasNext()) {
                        val mapEntry = mapIterator.next()
                        if (mapEntry.key.equals(keyToRemove)) {
                            mapIterator.remove()
                        }
                    }

                    lookupMap.entries.removeAll { entry -> entry.key in keysToRemove }
                }
            }
            // Log after
            logIfOverMinLogLevel("<-- removeHullmodExoticFromFleetMember()\tlookupMap.size(): ${lookupMap.size}\tlookupMap AFTER cleaning: ${lookupMap}", Level.INFO)
        }
    }

    private fun doesEntryExist(hullmodExotic: HullmodExotic, fleetMember: FleetMemberAPI): Boolean {
        synchronized(lookupMap) {
            val hullmodExoticKey = HullmodExoticKey(
                    hullmodExotic = hullmodExotic,
                    parentFleetMemberId = fleetMember.id
            )

            return lookupMap.contains(hullmodExoticKey)
        }
    }

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

    private fun getDataForKey(hullmodExoticKey: HullmodExoticKey): Optional<HullmodExoticInstallData> {
        return Optional.ofNullable(synchronized(lookupMap) { lookupMap.get(hullmodExoticKey) })
    }

    internal fun areHullmodExoticsEqual(hm1: HullmodExotic, hm2: HullmodExotic): Boolean {
        // NOTE: keep this in-sync with the HullmodExoticKey::equals()
        return hm1.getHullmodId() == hm2.getHullmodId()
    }

    private fun areHullmodIDsEqual(id1: String, id2: String): Boolean {
        return id1 == id2
    }

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
        val runningFromRefitScreen = Global.getSector().campaignUI.currentCoreTab == CoreUITabId.REFIT
        return runningFromRefitScreen
    }

    class Flows {
        /**
         * For each child module of [fleetMember] or rather, the fleetMember's [ShipVariantAPI] variant,
         * the method will first call [shouldInstallHullmodExoticToVariant] followed by [installHullmodExoticToVariant],
         * with their respective callbacks in proper places
         */
        companion object {
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
                val workMode = if (isFromRefitScreen) {
                    HullmodExoticHandlerWorkMode.LENIENT
                } else {
                    HullmodExoticHandlerWorkMode.STRICT
                }

                // Carry on
                val moduleSlotList = fleetMemberVariant.moduleSlots
                logIfOverMinLogLevel("onInstall()\tmoduleSlots: ${moduleSlotList}", Level.INFO)
                val moduleSlotsNullOrEmpty = moduleSlotList == null || moduleSlotList.isEmpty()
                if (moduleSlotsNullOrEmpty.not()) {
                    // Print out the module slot list, and generate a small map of
                    // <this exotic>, <parent FleetMember> + <list of expected variants> + <list of variants we installed on>
                    val mutableVariantList = moduleSlotList
                            .map { slot -> fleetMemberVariant.getModuleVariant(slot) }
                            .toMutableList()
                    // Obviously, add the 'member' variant to the list as well
                    mutableVariantList.add(fleetMemberVariant)
                    // Now forget about the mutable version and use a immutable version
                    val variantList = mutableVariantList.toList()
                    logIfOverMinLogLevel("onInstall()\tvariantList: ${variantList}", Level.INFO)

                    // Iterate through each slot, getting their variant and trying to install there
                    for (slot in moduleSlotList) {
                        val moduleVariant = fleetMemberVariant.getModuleVariant(slot)
                        logIfOverMinLogLevel("onInstall()\tslot: ${slot}\tmoduleVariant: ${moduleVariant}", Level.INFO)
                        if (moduleVariant == null) continue
                        val mods = ShipModLoader.get(fleetMember, moduleVariant)
                        logIfOverMinLogLevel("onInstall()\tmods: ${mods}", Level.INFO)
                        mods?.let { nonNullMods ->
                            val shouldInstallOnModuleVariant = HullmodExoticHandler.shouldInstallHullmodExoticToVariant(
                                    hullmodExotic = hullmodExotic,
                                    parentFleetMember = fleetMember,
                                    variant = moduleVariant,
                                    variantList = Optional.of(variantList),
                                    workMode = workMode
                            )
                            onShouldCallback.execute(shouldInstallOnModuleVariant, moduleVariant)
                            logIfOverMinLogLevel("onInstall()\tshouldInstallOnModuleVariant: ${shouldInstallOnModuleVariant}, variant: ${moduleVariant}", Level.INFO)
                            if (shouldInstallOnModuleVariant) {
                                // Lets try starting from the HullmodExoticHandler installation
                                val installResult = HullmodExoticHandler.installHullmodExoticToVariant(
                                        hullmodExotic = hullmodExotic,
                                        parentFleetMember = fleetMember,     //oh lets hope this one works out ...
                                        variant = moduleVariant,
                                        workMode = workMode
                                )

                                onInstallToChildModuleCallback.execute(installResult, moduleVariant, nonNullMods)
                            }
                        }
                    }
                }
            }

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
                val workMode = if (isFromRefitScreen) {
                    HullmodExoticHandlerWorkMode.LENIENT
                } else {
                    HullmodExoticHandlerWorkMode.STRICT
                }

                // Carry on

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
                    val moduleSlotList = memberVariant.moduleSlots
                    val variants = if(moduleSlotList == null || moduleSlotList.isEmpty()) {
                        // It's just this variant
                        listOf(memberVariant)
                    } else {
                        // Otherwise, it's all of them
                        val mutableVariantList = moduleSlotList
                                .map { slot -> memberVariant.getModuleVariant(slot) }
                                .toMutableList()
                        // Obviously, add the 'member' variant to the list as well
                        mutableVariantList.add(memberVariant)

                        mutableVariantList.toList()
                    }

                    // Return the optional of the list
                    Optional.of(variants)
                }

                val shouldInstallOnMemberVariant = HullmodExoticHandler.shouldInstallHullmodExoticToVariant(
                        hullmodExotic = hullmodExotic,
                        parentFleetMember = member,
                        variant = memberVariant,
                        variantList = variantListOptional,
                        workMode = workMode
                )
                onShouldCallback.execute(shouldInstallOnMemberVariant, memberVariant)
                logIfOverMinLogLevel("onInstall()\tshouldInstallOnMemberVariant: ${shouldInstallOnMemberVariant}, variant: ${memberVariant}", Level.INFO)
                if (shouldInstallOnMemberVariant) {
                    val installResult = HullmodExoticHandler.installHullmodExoticToVariant(
                            hullmodExotic = hullmodExotic,
                            parentFleetMember = member,
                            variant = memberVariant,
                            workMode = workMode
                    )
                    onInstallCallback.execute(installResult, memberVariant)
                }
            }

            @JvmStatic
            fun CheckAndRemoveFromAllChildModulesVariants(
                    fleetMember: FleetMemberAPI,
                    hullmodExotic: HullmodExotic,
                    onShouldCallback: OnShouldCallback,
                    onRemoveFromChildModuleCallback: OnRemoveFromChildModuleCallback
            ) {
                // First things first, figure out whether we're running from Refit or Exotica screen
                val isFromRefitScreen = runningFromRefitScreen()
                val workMode = if (isFromRefitScreen) {
                    HullmodExoticHandlerWorkMode.LENIENT
                } else {
                    HullmodExoticHandlerWorkMode.STRICT
                }

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
                    val mods = ShipModLoader.get(fleetMember, installedOnVariant)
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

            @JvmStatic
            fun CheckAndRemoveFromMemberModule(
                    fleetMember: FleetMemberAPI,
                    fleetMemberVariant: ShipVariantAPI,
                    hullmodExotic: HullmodExotic,
                    onShouldCallback: OnShouldCallback,
                    onRemoveFromMemberModuleCallback: OnRemoveFromMemberCallback
            ) {
                // First things first, figure out whether we're running from Refit or Exotica screen
                val isFromRefitScreen = runningFromRefitScreen()
                val workMode = if (isFromRefitScreen) {
                    HullmodExoticHandlerWorkMode.LENIENT
                } else {
                    HullmodExoticHandlerWorkMode.STRICT
                }

                // Carry on
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
                            moduleVariant = fleetMemberVariant
                    )
                }
            }
        }

        interface OnShouldCallback {
            fun execute(onShouldResult: Boolean, moduleVariant: ShipVariantAPI)
        }

        interface OnInstallToChildModuleCallback {
            fun execute(onInstallResult: Boolean, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications)
        }

        interface OnInstallToMemberCallback {
            fun execute(onInstallResult: Boolean, moduleVariant: ShipVariantAPI)
        }

        interface OnRemoveFromChildModuleCallback {
            fun execute(onRemoveResult: Boolean, moduleVariant: ShipVariantAPI, moduleVariantMods: ShipModifications)
        }

        interface OnRemoveFromMemberCallback {
            fun execute(onRemoveResult: Boolean, moduleVariant: ShipVariantAPI)
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

data class HullmodExoticInstallData(
        val parentFleetMemberAPI: FleetMemberAPI,
        val listOfAllModuleVariants: List<ShipVariantAPI>,
        val listOfExpectedVariants: List<ShipVariantAPI>,
        val listOfVariantsWeInstalledOn: List<ShipVariantAPI>
)

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

enum class HullmodExoticHandlerWorkMode { STRICT, LENIENT }
