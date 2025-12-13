package exoticatechnologies.util

import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll

fun FireAll.fire_safe(ruleId: String?, dialog: InteractionDialogAPI, memoryMap: Map<String, MemoryAPI>, params: String) {
    FireAll.fire(ruleId, dialog, memoryMap, params)
}

/**
 * This is just a kotlin-safe wrapper around [FireAll] and [FireAll.fire] so that it treats nullability as it should
 */
object FireAllKotlin : FireAll() {

    @JvmStatic
    fun fire(ruleId: String?, dialog: InteractionDialogAPI, memoryMap: Map<String, MemoryAPI>, params: String) {
        // This whole round-about way of doing things is because I cannot add static extension methods to FireAll ...
        fire_safe(
            ruleId = ruleId,
            dialog = dialog,
            memoryMap = memoryMap,
            params = params
        )
    }
}
