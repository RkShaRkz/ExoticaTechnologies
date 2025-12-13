package exoticatechnologies.config

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.util.Misc
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.types.ExoticType
import exoticatechnologies.modifications.exotics.ExoticsHandler
import exoticatechnologies.modifications.upgrades.Upgrade
import exoticatechnologies.modifications.upgrades.UpgradesHandler
import org.apache.log4j.Logger
import org.json.JSONException
import org.json.JSONObject
import org.lazywizard.lazylib.ext.json.getFloat
import org.lazywizard.lazylib.ext.json.optFloat
import java.io.IOException

class FactionConfig(var factionId: String, loadFromJson: Boolean) {
    constructor(factionId: String) : this(factionId, true)

    companion object {
        private val log: Logger = Logger.getLogger(FactionConfig::class.java)

        private const val SKILL_BEST_OF_THE_BEST = "best_of_the_best"
    }

    var exoticChance = 0.1
    var upgradeChance = 0.6
    var bandwidthMult = 1.0
    var allowedUpgrades: Map<Upgrade, Float> = FactionConfigLoader.getDefaultFactionUpgrades()
    var allowedExotics: Map<Exotic, Float> = FactionConfigLoader.getDefaultFactionExotics()
    var allowedExoticTypes: Map<ExoticType, Float> = FactionConfigLoader.getDefaultFactionExoticTypes()
    var exoticTypeChance: Float = FactionConfigLoader.getDefaultFactionExoticTypeChance()
    var maxExotics = FactionConfigLoader.getDefaultFactionMaxExotics()
    private var addToRngUpgrades = false
    private var addToRngExotics = false
    private var addToRngExoticTypes = false

    init {
        if (loadFromJson) {
            try {
                var settings: JSONObject = Global.getSettings()
                    .getMergedJSONForMod(FactionConfigLoader.SETTINGS_PATH.format(factionId), "exoticatechnologies")
                initSettings(settings)
                log.info("Loaded exotica faction config for faction $factionId")
            } catch (ex: IOException) {
                throw Exception("Failed to load exotica faction config for faction $factionId", ex)
            } catch (ex: JSONException) {
                throw Exception("The Exotica faction config for $factionId is incorrect.", ex)
            }
        }
    }

    fun initSettings(settings: JSONObject) {
        exoticChance = settings.optDouble("exoticChance", exoticChance)
        exoticTypeChance = settings.optFloat("exoticTypeChance", exoticTypeChance)
        maxExotics = settings.optInt("maxExotics", maxExotics)
        upgradeChance = settings.optDouble("upgradeChance", upgradeChance)
        bandwidthMult = settings.optDouble("bandwidthMult", bandwidthMult)
        addToRngUpgrades = settings.optBoolean("addToRngUpgrades", addToRngUpgrades)
        addToRngExotics = settings.optBoolean("addToRngExotics", addToRngExotics)
        addToRngExoticTypes = settings.optBoolean("addToRngExoticTypes", addToRngExoticTypes)

        val settingsUpgrades = settings.optJSONObject("allowedUpgrades")
        if (settingsUpgrades != null) {
            val newUpgrades: MutableMap<Upgrade, Float> = mutableMapOf()
            if (addToRngUpgrades) {
                newUpgrades.putAll(FactionConfigLoader.getDefaultFactionUpgrades())
            }

            settingsUpgrades.keys()
                .forEach {
                    val upgrade = UpgradesHandler.UPGRADES[it.toString()]
                    if (upgrade != null) {
                        newUpgrades[upgrade] = settingsUpgrades.getFloat(it.toString())
                    }
                }
            allowedUpgrades = newUpgrades
        }

        val settingsExotics = settings.optJSONObject("allowedExotics")
        if (settingsExotics != null) {
            val newExotics: MutableMap<Exotic, Float> = mutableMapOf()
            if (addToRngExotics) {
                newExotics.putAll(FactionConfigLoader.getDefaultFactionExotics())
            }

            settingsExotics.keys()
                .forEach {
                    val exotic = ExoticsHandler.EXOTICS[it.toString()]
                    if (exotic != null) {
                        newExotics[exotic] = settingsExotics.getFloat(it.toString())
                    }
                }
            allowedExotics = newExotics
        }

        val settingsExoticTypes = settings.optJSONObject("allowedExoticTypes")
        if (settingsExoticTypes != null) {
            val types: MutableMap<ExoticType, Float> = mutableMapOf()
            if (addToRngExoticTypes) {
                types.putAll(FactionConfigLoader.getDefaultFactionExoticTypes())
            }

            settingsExoticTypes.keys()
                .forEach {
                    val exoticType = ExoticType.valueOf(it.toString())
                    types[exoticType] = settingsExoticTypes.getFloat(it.toString())
                }

            allowedExoticTypes = types
        }
    }

    fun getMaxExotics(member: FleetMemberAPI): Int {
        return if (FactionConfigLoader.useTheMethodThatMakesHartleyverseStronger) {
            // If we're making hartleyverse (?!?) stronger, return the number of max permanent hullmods
            member.stats.dynamic.getStat(Stats.MAX_PERMANENT_HULLMODS_MOD).modifiedInt
        } else {
            // Otherwise, check the number of max exotics and increment by 1 if the fleet commander has the
            // "Best of the best" skill learned
            //
            // For player owned, just check the player fleet. Otherwise, do like we did before
            //
            // This is somewhat plaky and will cause inconsistent results with multimodule ships,
            // which is the whole reason this bit is being somewhat reworked,
            // but at the very least it'll fix it for the player and the player's side.
            // This might also cause problems with "special fleets" the player creates but oh well.
            //
            // Multimodule ships are *of course* causing problems, since child modules seem to be limited to just 2
            // while the main module can have 3 if the player has the "best_of_the_best" skill ... so for player owned
            // members (main modules and child modules) let's look at the commander of the player fleet rather than
            // member's fleetCommander

            var limit = maxExotics
            if (member.owner == Misc.OWNER_PLAYER) {
                // For player owned, grab the player's fleet
                val playerFleet = Global.getSector().playerFleet
                if (playerFleet != null && playerFleet.commander.stats.hasSkill(SKILL_BEST_OF_THE_BEST)) {
                    limit++
                }
            } else {
                if (member.fleetCommander != null && member.fleetCommander.stats.hasSkill(SKILL_BEST_OF_THE_BEST)) {
                    limit++
                }
            }

            limit
        }
    }
}
