package exoticatechnologies.util

import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.campaign.fleet.FleetData
import exoticatechnologies.util.reflect.ReflectionUtils

/**
 * An "extension" class on top of [FleetData] that fires two callbacks during [FleetData.scuttle] -
 * the [OnScuttleListener.onPreScuttle] before calling super.scuttle() and [OnScuttleListener.onPostScuttle] afterwards.
 */
class ETFleetData(@Transient val originalFleetData: FleetData): FleetData(
        ReflectionUtils.get("namePrefix", originalFleetData).toString(),
        ReflectionUtils.get("nameSourceFactionId", originalFleetData).toString(),
) {

//    init {
//        ReflectionUtils.copyAllFields(originalFleetData, this)
//    }

    override fun scuttle(p0: FleetMemberAPI?) {
        scuttleListener?.onPreScuttle(p0)
        super.scuttle(p0)
        originalFleetData.scuttle(p0)
        scuttleListener?.onPostScuttle(p0)
    }


    companion object {
        var scuttleListener: OnScuttleListener? = null

        @JvmStatic
        fun setOnScuttleListener(onScuttleListener: OnScuttleListener?) {
            scuttleListener = onScuttleListener
        }
    }

    interface OnScuttleListener {
        fun onPreScuttle(fleetMember: FleetMemberAPI?)
        fun onPostScuttle(fleetMember: FleetMemberAPI?)
    }
}
