package exoticatechnologies.util

import com.fs.starfarer.api.fleet.FleetMemberAPI

object ScuttleHandler: OnScuttleListener {
    override fun onPreScuttle(fleetMember: FleetMemberAPI?) {
        AnonymousLogger.log("--> onPreScuttle()", "ScuttleHandler")
    }

    override fun onPostScuttle(fleetMember: FleetMemberAPI?) {
        AnonymousLogger.log("--> onPostScuttle()", "ScuttleHandler")
    }
}

interface OnScuttleListener {
    fun onPreScuttle(fleetMember: FleetMemberAPI?)
    fun onPostScuttle(fleetMember: FleetMemberAPI?)
}
