package exoticatechnologies.util

import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.InteractionDialogPlugin
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * Safe wrapper around [FireAll.fire] that will not fail when called with dialog.plugin.memoryMap
 * or the memory map coming from [InteractionDialogPlugin.getMemoryMap].
 *
 * If [memoryMap] is null, it just logs an error and returns false
 *
 * @param ruleId the ruleId
 * @param dialog the dialog
 * @param memoryMap the dialog plugin's memoryMap
 * @param params the params
 *
 * @return the return value of [FireAll.fire] or false if [memoryMap] was null
 */
fun FireAll.fire_safe(ruleId: String?, dialog: InteractionDialogAPI, memoryMap: Map<String, MemoryAPI>?, params: String): Boolean {
    return if (memoryMap != null) {
        FireAll.fire(ruleId, dialog, memoryMap, params)
    } else {
        // Log and return false
        AnonymousLogger.log("Not calling FireAll.fire(...) due to memoryMap being null!!!", "FireAll.fire_safe", Level.ERROR)

        false
    }
}

/**
 * This is just a kotlin-safe wrapper around [FireAll] and [FireAll.fire] so that it treats nullability as it should
 *
 * However, due to the nullability not being a thing in java, you **should prefer** using the three-parameter [fire] method
 * instead
 */
object FireAllKotlin : FireAll() {
    val logger = Logger.getLogger(FireAllKotlin::class.java)

    /**
     * Method that *actually* safely calls into [FireAll.fire] through the four-parameter [fire] method **only**
     * if it's memoryMap is non-null; otherwise logs an error
     *
     * @return whether the [FireAll.fire] matched anything and executed, otherwise false
     */
    @JvmStatic
    fun fireSafe(ruleId: String?, dialog: InteractionDialogAPI, params: String): Boolean {
        val memoryMap = getMemoryMap(dialog.plugin)
        return if (memoryMap != null) {
            fire(
                ruleId = ruleId,
                dialog = dialog,
                memoryMap = memoryMap,
                params = params
            )
        } else {
            log(
                logMsg = "Not calling FireAllKotlin.fireSafe() due to memoryMap being null!",
                logger = logger,
                logLevel = Level.ERROR
            )

            // And return false
            false
        }
    }
    /**
     * "Safe" wrapper around [FireAll.fire] however you should **not call this with dialog.plugin.memoryMap**
     * (memory map coming from [InteractionDialogPlugin.getMemoryMap]) since it comes from java land and it might just be null
     *
     * Calling it with that will most likely result in a NPE on the method invocation itself, and not in
     * com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin.getEntityMemory(BaseCommandPlugin.java:21)
     *
     * You should prefer using the other three-parameter [fireSafe] method instead. Uses [fire_safe] internally.
     *
     * @param ruleId the ruleId
     * @param dialog the dialog
     * @param memoryMap the [InteractionDialogPlugin]'s memoryMap
     * @param params the params
     * @param fallbackToEmptyMap whether we should fallback to an empty map in case memoryMap turns out null or not
     *
     * @return the return value of [FireAll.fire] if the rule matched anything
     */
    @JvmStatic
    fun fire(ruleId: String?, dialog: InteractionDialogAPI, memoryMap: Map<String, MemoryAPI>?, params: String, fallbackToEmptyMap: Boolean = false): Boolean {
        // This whole round-about way of doing things is because I cannot add static extension methods to FireAll ...
        return if (memoryMap != null) {
            // If non-null, return whatever the fire_safe returns
            fire_safe(
                ruleId = ruleId,
                dialog = dialog,
                memoryMap = memoryMap,
                params = params
            )
        } else {
            // if memory map was null, either fallback or just return false
            fire_safe(
                ruleId = ruleId,
                dialog = dialog,
                memoryMap = if (fallbackToEmptyMap) {
                    // if we need to fallback, just use empty map and see whats there to see
                    mapOf()
                } else {
                    // If we're not falling back, just send the null and let fire_safe(...) handle it ...
                    memoryMap
                },
                params = params
            )
        }
    }

    /**
     * Returns a nullable memoryMap from the [interactionDialogPlugin].
     * This method exists solely that the kotlin code recognizes it as nullable.
     *
     * @param interactionDialogPlugin the [InteractionDialogPlugin] whose memory map should be returned.
     *
     * @return result of [InteractionDialogPlugin.getMemoryMap] if plugin was non-null or null if plugin was null.
     */
    private fun getMemoryMap(interactionDialogPlugin: InteractionDialogPlugin?): Map<String, MemoryAPI>? {
        // If 'interactionDialog' or it's memory map is null - return null
        // otherwise, return it's memory map
        return if (interactionDialogPlugin != null) {
            // now return the nullable memory map
            interactionDialogPlugin.memoryMap
        } else {
            // interaction dialog was null, it can't have a memory map - return null
            null
        }
    }
}
