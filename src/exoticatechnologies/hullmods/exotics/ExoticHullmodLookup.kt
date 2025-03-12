package exoticatechnologies.hullmods.exotics

import exoticatechnologies.util.AnonymousLogger
import exoticatechnologies.util.datastructures.Optional

object ExoticHullmodLookup {

    private val lookupMap: HashMap<String, ExoticHullmod> = hashMapOf()

    fun addToLookupMap(exoticHullmod: ExoticHullmod) {
        lookupMap[exoticHullmod.hullModId] = exoticHullmod
        AnonymousLogger.log("<-- addToLookupMap()\tadded ExoticHullmod: ${exoticHullmod} to lookup map with ID: ${exoticHullmod.hullModId}\t\tlookupMap: ${lookupMap}", "ExoticHullmodLookup")
    }

    fun getFromMap(hullmodId: String): Optional<ExoticHullmod> {
        return if (lookupMap.contains(hullmodId)) {
//            lookupMap[hullmodId]?.let {
//                Optional.of(it)
//            }

            // Ugh, lets do the ugly thing...
            Optional.of(lookupMap[hullmodId]!!)
        } else {
            AnonymousLogger.log("Looking up unknown key ${hullmodId}, returning NOTHING since it's not in the lookup map.", "ExoticHullmodLookup")
            Optional.empty()
        }
    }
}
