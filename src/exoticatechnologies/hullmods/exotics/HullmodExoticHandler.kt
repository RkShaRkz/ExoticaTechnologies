package exoticatechnologies.hullmods.exotics

import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import exoticatechnologies.modifications.exotics.impl.HullmodExotic
import exoticatechnologies.util.AnonymousLogger
import exoticatechnologies.util.datastructures.Optional
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object HullmodExoticHandler {
    private val lookupMap: MutableMap<HullmodExoticKey, HullmodExoticInstallData> = ConcurrentHashMap()//Collections.synchronizedMap(mutableMapOf())//hashMapOf()

    /**
     * Method for checking whether the [HullmodExotic] should be installed onto a [ShipVariantAPI] by checking
     * if we hadn't already installed it or not
     *
     * @param hullmodExotic the HullmodExotic in question
     * @param parentFleetMember the [FleetMemberAPI] of the root module, or the whole ship's if it's a single-module ship
     * @param variant the variant to check
     * @param variantList list of module variants belonging to the parent module (optional)
     */
    fun shouldInstallHullmodExoticToVariant(hullmodExotic: HullmodExotic, parentFleetMember: FleetMemberAPI, variant: ShipVariantAPI, variantList: Optional<List<ShipVariantAPI>>): Boolean {
        synchronized(lookupMap) {
            AnonymousLogger.log("--> shouldInstallHullmodExoticToVariant()\thullmodExotic: ${hullmodExotic}, variant: ${variant}", "HullmodExoticHandler")
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
                AnonymousLogger.log("shouldInstallHullmodExoticToVariant()\tEncountered parentFleetMember with shipName == null !!! Bailing out !!!", "HullmodExoticHandler")
                return false
            }

            // now, if we already have some install data, we are going to be checking the variant we're about to install
            // versus two different things:
            // 1. is it in the expected list?
            // 2. is it already NOT in the installed list?
            // we'll just do nothing if it fails either of these and return false.
            val retVal = if (currentInstallData != null) {
                AnonymousLogger.log("shouldInstallHullmodExoticToVariant()\talready had install data !!!", "HullmodExoticHandler")
                // now, check if this variant is in the expected list
                val expectedVariants = currentInstallData.listOfExpectedVariants
//            val isExpected: Boolean = expectedVariants.contains(variant)
                // obviously these don't match
                val isExpected: Boolean = expectedVariants
                        .map { expectedVariant -> expectedVariant.hullSpec.hullId }
                        .contains(variant.hullSpec.hullId)
                AnonymousLogger.log("shouldInstallHullmodExoticToVariant()\tisExpected: ${isExpected}", "HullmodExoticHandler")

                val variantsWeAlreadyInstalledOn = currentInstallData.listOfVariantsWeInstalledOn
                val hasNotInstalledOnThisVariantAlready = variantsWeAlreadyInstalledOn.contains(variant).not()
                AnonymousLogger.log("shouldInstallHullmodExoticToVariant()\thasNotInstalledOnThisAlready: ${hasNotInstalledOnThisVariantAlready}", "HullmodExoticHandler")

                // Since I don't know how to check anything with parentFleetMember yet, lets just leave it out of the equation for now
                isExpected && hasNotInstalledOnThisVariantAlready
            } else {
                AnonymousLogger.log("shouldInstallHullmodExoticToVariant()\tthere was no install data for this key ${hullmodExoticKey}", "HullmodExoticHandler")
                // If we don't have any install data in the map, this is going to be simple. The optional variant list must be present
                // If not present, just... do nothing for now, because we can't start off from a submodule I hope. So for now, throw
                if (variantList.isPresent().not()) {
                    throw IllegalStateException("Since there is no HullmodExoticInstallData for these parameters, the Optional must be present!")
                }

                // And now, add stuff to the map ...
                synchronized(lookupMap) {
                    lookupMap[hullmodExoticKey] = HullmodExoticInstallData(
                            parentFleetMemberAPI = parentFleetMember,
                            listOfExpectedVariants = variantList.get(),
                            listOfVariantsWeInstalledOn = emptyList()
                    )
                }

                // And obviously return true
                true
            }

            AnonymousLogger.log("<-- shouldInstallHullmodExoticToVariant()\treturning ${retVal}", "HullmodExoticHandler")
            return retVal
        }
    }


    fun installHullmodExoticToVariant(hullmodExotic: HullmodExotic, parentFleetMember: FleetMemberAPI, variant: ShipVariantAPI): Boolean {
        synchronized(lookupMap) {
            AnonymousLogger.log("--> installHullmodExoticToVariant()\thullmodExotic: ${hullmodExotic}, parentFleetMember: ${parentFleetMember}, variant: ${variant}", "HullmodExoticHandler")
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

            val currentInstallData = synchronized(lookupMap) { lookupMap[hullmodExoticKey] }
            if (currentInstallData == null) {
                throw IllegalStateException("We should have had this key in the lookup map!!! lookupMap: ${lookupMap}")
            }

            val alreadyInstalledList = currentInstallData.listOfVariantsWeInstalledOn
            // just check
            val alreadyIn = alreadyInstalledList.contains(variant)
            if (alreadyIn) {
                // Actually, lets not throw just yet
                // TODO change to throwing
                AnonymousLogger.log("installHullmodExoticToVariant()\t\t !!!!! Trying to install on a variant that's already been installed on, returning false!!!!!", "HullmodExoticHandler")
                return false
            }
            val mutableInstalledList = alreadyInstalledList.toMutableList()
            mutableInstalledList.add(variant)
            val newKeyValue = currentInstallData.copy(
                    parentFleetMemberAPI = currentInstallData.parentFleetMemberAPI,
                    listOfExpectedVariants = currentInstallData.listOfExpectedVariants,
                    listOfVariantsWeInstalledOn = mutableInstalledList.toList()
            )
            synchronized(lookupMap) {
                lookupMap[hullmodExoticKey] = newKeyValue
            }

            AnonymousLogger.log("<-- installHullmodExoticToVariant()\t\treturning true", "HullmodExoticHandler")
            return true
        }
    }

    fun removeHullmodExoticFromFleetMember(exoticHullmod: ExoticHullmod, fleetMember: FleetMemberAPI) {
        synchronized(lookupMap) {
            // Obviously, using a Set for this was a wrong idea that led to the ever-growing problem in the first place
            // Somehow, the keys were matched fine in the set so the keysToRemove was always of either size 1 or 0
            // However, many such "duplicate" keys show up in the map just fine. Dunno why but - they do.
            val keysToRemove = mutableListOf<HullmodExoticKey>()
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
            AnonymousLogger.log("removeHullmodExoticFromFleetMember()\tkeysToRemove.size: ${keysToRemove.size}\tkeysToRemove: ${keysToRemove}", "HullmodExoticHandler")
            AnonymousLogger.log("removeHullmodExoticFromFleetMember()\tlookupMap before cleaning: ${lookupMap}", "HullmodExoticHandler")
            AnonymousLogger.log("removeHullmodExoticFromFleetMember()\tlookupMap.size(): ${lookupMap.size}", "HullmodExoticHandler")


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
            AnonymousLogger.log("<-- removeHullmodExoticFromFleetMember()\tlookupMap AFTER cleaning: ${lookupMap}", "HullmodExoticHandler")
            AnonymousLogger.log("removeHullmodExoticFromFleetMember()\tlookupMap.size(): ${lookupMap.size}", "HullmodExoticHandler")
        }
    }

    fun doesEntryExist(hullmodExotic: HullmodExotic, fleetMember: FleetMemberAPI): Boolean {
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
            AnonymousLogger.log("--> grabAllKeysForParticularFleetMember()\tfleetMember.id: ${fleetMemberAPI.id}", "HullmodExoticHandler")
//        val retVal = mutableListOf<HullmodExoticKey>()
            val retVal = mutableSetOf<HullmodExoticKey>()
            for (key in lookupMap.keys) {
//            if (key.parentFleetMember == fleetMemberAPI) retVal.add(key)    //TODO fix this, just check ids
                if (areFleetMemberIDsEqual(key.parentFleetMemberId, fleetMemberAPI.id)) {
                    retVal.add(key)
                }
            }
            // TODO get rid of this
            val lookupMapFleetMemberIDs = lookupMap.keys.map { hullmodExoticKey -> hullmodExoticKey.parentFleetMemberId }
            val retValIDs = retVal.map { key -> key.parentFleetMemberId }
            AnonymousLogger.log("grabAllKeysForParticularFleetMember()\tlookupMap IDs: ${lookupMapFleetMemberIDs}", "HullmodExoticHandler")
            AnonymousLogger.log("grabAllKeysForParticularFleetMember()\tretVal IDs: ${retValIDs}", "HullmodExoticHandler")
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

    // test only methods here

    /**
     * Internally calls into [grabAllKeysForParticularFleetMember] - please, do not use this in production code
     */
    @VisibleForTesting
    @TestOnly
    internal fun testsOnly_grabAllKeysForParticularFleetMember(fleetMemberAPI: FleetMemberAPI): List<HullmodExoticKey> {
        return grabAllKeysForParticularFleetMember(fleetMemberAPI)
    }

    /**
     * Internally calls into [getDataForKey] - please, do not use this in production code
     */
    @VisibleForTesting
    @TestOnly
    internal fun testsOnly_getDataForKey(hullmodExoticKey: HullmodExoticKey): Optional<HullmodExoticInstallData> {
        return getDataForKey(hullmodExoticKey)
    }

    /**
     * Internally clears the [lookupMap] - please, do not use this in production code
     */
    @VisibleForTesting
    @TestOnly
    internal fun testsOnly_clearLookupMap() {
        lookupMap.clear()
    }
}

data class HullmodExoticInstallData(
        val parentFleetMemberAPI: FleetMemberAPI,
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
}
