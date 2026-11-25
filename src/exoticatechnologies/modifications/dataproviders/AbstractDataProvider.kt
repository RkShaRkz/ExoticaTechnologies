package exoticatechnologies.modifications.dataproviders

import com.fs.starfarer.api.fleet.FleetMemberAPI
import exoticatechnologies.modifications.ShipModLoader
import exoticatechnologies.modifications.ShipModifications
import org.json.JSONException
import org.json.JSONObject

abstract class AbstractDataProvider : ShipModLoader.Provider {
    protected fun convertFromJson(json: String): ShipModifications {
        try {
            val modsObj = JSONObject(json)
            return ShipModifications(modsObj)
        } catch (ex: JSONException) {
            throw RuntimeException("Failed to load Exotica modifications object from variant tags.", ex)
        }
    }

    protected fun convertToJson(member: FleetMemberAPI, mods: ShipModifications): String {
        try {
            return mods.toJson(member).toString()
        } catch (ex: JSONException) {
            throw RuntimeException("Failed to save Exotica object.")
        }
    }
}
