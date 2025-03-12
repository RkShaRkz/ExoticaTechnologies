package exoticatechnologies.hullmods.exotics

import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import exoticatechnologies.modifications.exotics.impl.HullmodExotic
import exoticatechnologies.util.AnonymousLogger
import exoticatechnologies.util.datastructures.Optional

object HullmodExoticHandler {
    private val lookupMap: HashMap<HullmodExoticKey, HullmodExoticInstallData> = hashMapOf()

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
        AnonymousLogger.log("--> shouldInstallHullmodExoticToVariant()\thullmodExotic: ${hullmodExotic}, variant: ${variant}", "HullmodExoticHandler")
        // First things first, check if we have this in the map
        // we will do this by forming up a [HullmodExotica, FleetMemberAPI] pair as a key
        val hullmodExoticKey = HullmodExoticKey(
                hullmodExotic = hullmodExotic,
                parentFleetMember = parentFleetMember
        )
        val currentInstallData = lookupMap[hullmodExoticKey]

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
                    .map { variant -> variant.hullSpec.hullId }
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
            lookupMap[hullmodExoticKey] = HullmodExoticInstallData(
                    parentFleetMemberAPI = parentFleetMember,
                    listOfExpectedVariants =  variantList.get(),
                    listOfVariantsWeInstalledOn = emptyList()
            )

            // And obviously return true
            true
        }

        AnonymousLogger.log("<-- shouldInstallHullmodExoticToVariant()\treturning ${retVal}", "HullmodExoticHandler")
        return retVal
    }


    fun installHullmodExoticToVariant(hullmodExotic: HullmodExotic, parentFleetMember: FleetMemberAPI, variant: ShipVariantAPI): Boolean {
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
                parentFleetMember = parentFleetMember
        )

        val currentInstallData = lookupMap[hullmodExoticKey]
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
        lookupMap[hullmodExoticKey] = newKeyValue

        AnonymousLogger.log("<-- installHullmodExoticToVariant()\t\treturning true", "HullmodExoticHandler")
        return true
    }

    fun removeHullmodExoticFromFleetMember(exoticHullmod: ExoticHullmod, fleetMember: FleetMemberAPI) {
        val keysToRemove = mutableSetOf<HullmodExoticKey>()
        val allKeys = grabAllKeysForParticularFleetMember(fleetMember)
        for (key in allKeys) {
            val exoticHandlerDataOptional = getDataForKey(key)
            if (exoticHandlerDataOptional.isPresent()) {
                val exoticHandlerData = exoticHandlerDataOptional.get()
                for (variant in exoticHandlerData.listOfVariantsWeInstalledOn) {
                    val variantHullSize = variant.hullSpec.hullSize
                    exoticHullmod.removeEffectsBeforeShipCreation(variantHullSize, variant.statsForOpCosts, exoticHullmod.hullModId)
                    // Lets keep track of 'removed' keys here because why not
                    keysToRemove.add(key)
                }
            }
        }
        // Log before
        AnonymousLogger.log("removeHullmodExoticFromFleetMember()\tlookupMap before cleaning: ${lookupMap}", "HullmodExoticHandler")

        // Now, cleanup the lookupMap from all the to-remove keys
        for(key in keysToRemove) {
            lookupMap.remove(key)
        }
        // Log after
        AnonymousLogger.log("<-- removeHullmodExoticFromFleetMember()\tlookupMap AFTER cleaning: ${lookupMap}", "HullmodExoticHandler")
    }

    fun doesEntryExist(hullmodExotic: HullmodExotic, fleetMember: FleetMemberAPI): Boolean {
        val hullmodExoticKey = HullmodExoticKey(
                hullmodExotic = hullmodExotic,
                parentFleetMember = fleetMember
        )

        return lookupMap.contains(hullmodExoticKey)
    }

    private fun grabAllKeysForParticularFleetMember(fleetMemberAPI: FleetMemberAPI): List<HullmodExoticKey> {
        val retVal = mutableListOf<HullmodExoticKey>()
        for(key in lookupMap.keys) {
            if (key.parentFleetMember == fleetMemberAPI) retVal.add(key)
        }

        return retVal.toList()
    }

    private fun getDataForKey(hullmodExoticKey: HullmodExoticKey): Optional<HullmodExoticInstallData> {
        return Optional.ofNullable(lookupMap[hullmodExoticKey])
    }
}

data class HullmodExoticInstallData(
        val parentFleetMemberAPI: FleetMemberAPI,
        val listOfExpectedVariants: List<ShipVariantAPI>,
        val listOfVariantsWeInstalledOn: List<ShipVariantAPI>
)

data class HullmodExoticKey(
        val hullmodExotic: HullmodExotic,
        val parentFleetMember: FleetMemberAPI
)
