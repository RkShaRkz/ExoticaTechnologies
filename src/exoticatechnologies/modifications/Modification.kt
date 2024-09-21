package exoticatechnologies.modifications

import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import exoticatechnologies.hullmods.ExoticaTechHM
import exoticatechnologies.modifications.conditions.Condition
import exoticatechnologies.modifications.conditions.ConditionDict
import exoticatechnologies.modifications.conditions.toList
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.upgrades.Upgrade
import org.apache.log4j.Logger
import org.jetbrains.annotations.Nullable
import org.json.JSONObject
import org.lazywizard.lazylib.ext.json.optFloat
import java.awt.Color

/**
 * Base class for both [Exotic] systems and [Upgrade]able systems.
 */
abstract class Modification(val key: String, val settings: JSONObject) {
    companion object {
        @JvmStatic
        private val log = Logger.getLogger(Modification::class.java)

        const val DISJUNCT_LABEL_TEXT = "OR"
    }

    var name: String = settings.getString("name")
    var tags: List<String>
    var hints: List<String>

    open var color: Color = Color.white
    open var description: String = ""
    open var valueMult: Float = settings.optFloat("valueMult", 1.0f)
    protected abstract var icon: String
    val conditions: MutableList<Condition> = mutableListOf()
    val conditionsDisjunct: Boolean
    val conditionsNecessaryToSatify: Int
    open var canDropFromCombat = false
    val variantScales: MutableMap<String, Float> = mutableMapOf()

    init {
        if (settings.has("conditions")) {
            conditions.addAll(ConditionDict.getCondsFromJSONArray(settings.getJSONArray("conditions")))
        }

        if (settings.has("tag")) {
            tags = listOf(settings.getString("tag"))
        } else if (settings.has("tags")) {
            tags = settings.getJSONArray("tags").toList()
        } else {
            tags = listOf()
        }

        if (settings.has("hints")) {
            hints = settings.getJSONArray("hints").toList()
        } else {
            hints = listOf()
        }

        if (settings.has("minConditionsNeeded")) {
            conditionsNecessaryToSatify = settings.getInt("minConditionsNeeded")
            conditionsDisjunct = true
        } else {
            conditionsDisjunct = false
            conditionsNecessaryToSatify = -1
        }

        canDropFromCombat = settings.optBoolean("dropsFromCombat")
    }

    /**
     * Checks tag against all modifications in mods.
     * @return true if null tag, null mods, or no conflicting mod was found. false if a conflicting mod was found.
     */
    open fun checkTags(member: FleetMemberAPI?, mods: ShipModifications?, tags: List<String>): Boolean {
        if (mods == null || tags.isEmpty()) {
            return true
        }

        return mods.getModsThatConflict(tags).none { it.key != this.key }
    }

    open fun checkConditions(member: FleetMemberAPI?, mods: ShipModifications?): Boolean {
        try {
            val requiredConditions = conditions
                .filter { !it.weightOnly }

            return if (conditionsDisjunct) {
                val matchedConditions =
                        requiredConditions
                                .map { condition -> condition.compare(member!!, mods, key) }
                                .count { it }

                matchedConditions >= conditionsNecessaryToSatify
            } else {
                requiredConditions
                        .none { !it.compare(member!!, mods, key) }
            }
        } catch (ex: Exception) {
            log.error("$name threw exception while checking conditions", ex)
            throw ex
        }

    }

    open fun getCannotApplyReasons(member: FleetMemberAPI, mods: ShipModifications?): List<String> {
        return if (conditionsDisjunct) {
            val subconditions = conditions
                    .filter { condition -> condition.weightOnly.not() }                      // filter out 'weightOnly' conditions

            val reasonList = mutableListOf<String>()
            for (condition in subconditions) {
                if (condition.cannotApplyReason != null) {
                    reasonList.add(condition.cannotApplyReason!!)   // safe
                    reasonList.add(DISJUNCT_LABEL_TEXT)
                }
            }

            // delete the last item which is the extra 'OR'
            if (reasonList.isNotEmpty()) {
                reasonList.removeLast()
            }
            // Return the reason list
            reasonList
        } else {
            conditions
                    .filter { !it.weightOnly }
                    .filter { !it.compare(member, mods, key) }
                    .mapNotNull { it.cannotApplyReason }
        }
    }

    open fun getCalculatedWeight(member: FleetMemberAPI, mods: ShipModifications?): Float {
        var addedWeight = 1f

        if (conditions.isNotEmpty()) {
            addedWeight += conditions
                .map { it.calculateWeight(member, mods) }
                .sum()
        }

        if (member.variant.hullVariantId != null) {
            variantScales[member.variant.hullVariantId]?.let {
                addedWeight *= it
            }
        }

        return addedWeight
    }

    open fun shouldLoad(): Boolean {
        return true
    }

    open fun shouldShow(member: FleetMemberAPI, mods: ShipModifications, market: MarketAPI?): Boolean {
        return true
    }

    /**
     * Whether the modification is installable in non-main modules for multi-module ships,
     * and whether it should apply it's effects to all other modules.
     *
     * This version is called in [ExoticaTechHM]'s [BaseHullMod.applyEffectsAfterShipCreation]
     *
     * This call determines whether the following methods will be called on modules:
     * - [Exotic.advanceInCombatUnpaused]
     * - [Exotic.applyExoticToStats]
     * - [Exotic.applyToShip]
     * - [Exotic.applyToFighters]
     * - [Upgrade.advanceInCombatUnpaused]
     * - [Upgrade.applyUpgradeToStats]
     * - [Upgrade.applyToShip]
     * - [Upgrade.applyToFighters]
     *
     * **NOTE** A certain false-positive can be observed when playing in 'simulation' mode vs actual combat;
     * namely, while a mod installed on a module (in a multi-module environment) will affect only the module it's
     * installed on during simulation, in actual combat it depends on the return value of [shouldShareEffectToOtherModules]
     *
     * Suggestion is to use this just to limit whether the modification can be installed on modules.
     *
     * @see shouldAffectModule
     * @see shouldShareEffectToOtherModules
     */
    open fun shouldAffectModule(ship: ShipAPI?, module: ShipAPI?): Boolean {
        return true
    }

    /**
     * Whether the modification is installable in non-main modules for multi-module ships,
     * and whether it should apply it's effects to all other modules.
     *
     * This version is called in [ExoticaTechHM]'s [BaseHullMod.applyEffectsBeforeShipCreation],
     * and is used mainly to determine whether [Exotic.applyExoticToStats] and [Upgrade.applyUpgradeToStats]
     * are called on modules
     *
     * **NOTE** A certain false-positive can be observed when playing in 'simulation' mode vs actual combat;
     * namely, while a mod installed on a module (in a multi-module environment) will affect only the module it's
     * installed on during simulation, in actual combat it depends on the return value of [shouldShareEffectToOtherModules]
     *
     * Suggestion is to use this just to limit whether the modification can be installed on modules.
     *
     * @see shouldAffectModule
     * @see shouldShareEffectToOtherModules
     * @see shouldAffectModulesToShareEffectsToOtherModules
     */
    open fun shouldAffectModule(moduleStats: MutableShipStatsAPI): Boolean {
        return true
    }

    /**
     * Relevant only for multi-module ships; if [shouldAffectModule] is **[true]**, this determines whether the effects
     * of the modification should affect (or rather, replicate to) all other modules as well
     *
     * Unsetting (clearing) the special [shouldAffectModulesToShareEffectsToOtherModules] flag makes it disregard the
     * [shouldAffectModule] requirement
     */
    open fun shouldShareEffectToOtherModules(@Nullable ship: ShipAPI?, @Nullable module: ShipAPI?) = false

    /**
     * Special flag used to enforce/bypass the [shouldAffectModule] requirement for sharing effects to other modules
     * for multi-module ships; this allows for the usecase to limit installing the exotica/upgrade only on main module
     * but also have it work for (share effects to) the whole ship as well.
     *
     * When **[true]** then the [shouldAffectModule] also needs to be true for [shouldShareEffectToOtherModules] to work; or
     * for effect sharing to work at all.
     *
     * When **[false]** then the [shouldAffectModule] doesn't matter for [shouldShareEffectToOtherModules] to work.
     *
     * Defaults to **[true]**
     *
     * @see shouldShareEffectToOtherModules
     */
    open fun shouldAffectModulesToShareEffectsToOtherModules() = true

    open fun canApply(member: FleetMemberAPI, mods: ShipModifications?): Boolean {
        return canApply(member, member.variant, mods)
    }

    open fun canApply(member: FleetMemberAPI, variant: ShipVariantAPI, mods: ShipModifications?): Boolean {
        val condCheck = checkConditions(member, mods)
        val tagsCheck = checkTags(member, mods, tags)
        val varCheck = canApplyToVariant(member.variant)
        return condCheck && tagsCheck && varCheck
    }

    open fun canApplyToVariant(variant: ShipVariantAPI): Boolean {
        return true
    }

    open fun init(engine: CombatEngineAPI) {

    }

    @Deprecated("Replaced by canDropFromCombat variable. Unused.", ReplaceWith("canDropFromCombat"))
    open fun canDropFromFleets(): Boolean {
        return canDropFromCombat
    }

    private fun logIfKey(string: String, logIfKey: String) {
        if (key == logIfKey) log.info(string)
    }
}