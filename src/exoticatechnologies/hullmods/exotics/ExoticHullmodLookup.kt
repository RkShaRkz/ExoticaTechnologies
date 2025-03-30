package exoticatechnologies.hullmods.exotics

import exoticatechnologies.util.datastructures.Optional
import exoticatechnologies.util.shouldLog
import org.apache.log4j.Level
import org.apache.log4j.Logger

object ExoticHullmodLookup {
    private val logger: Logger = Logger.getLogger(ExoticHullmodLookup::class.java)
    private val MIN_LOG_LEVEL: Level = Level.WARN

    private val lookupMap: HashMap<String, ExoticHullmod> = hashMapOf()

    fun addToLookupMap(exoticHullmod: ExoticHullmod) {
        lookupMap[exoticHullmod.hullModId] = exoticHullmod
        logIfOverMinLogLevel("<-- addToLookupMap()\tadded ExoticHullmod: ${exoticHullmod} to lookup map with ID: ${exoticHullmod.hullModId}\t\tlookupMap: ${lookupMap}", Level.INFO)
    }

    fun getFromMap(hullmodId: String): Optional<ExoticHullmod> {
        return if (lookupMap.contains(hullmodId)) {
            // Ugh, lets do the ugly thing... but should be fine since it's contained in the map.
            Optional.of(lookupMap[hullmodId]!!)
        } else {
            logIfOverMinLogLevel("Looking up unknown key ${hullmodId}, returning NOTHING since it's not in the lookup map.", Level.ERROR)
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
}
