package exoticatechnologies.modifications

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI

class PersistentDataProvider: VariantTagProvider() {
    fun getFromId(id: String): ShipModifications? {
        return shipModificationMap[id]
    }

    override fun get(member: FleetMemberAPI, variant: ShipVariantAPI): ShipModifications? {
        val mods: ShipModifications? = getFromId(member.id)
        if (mods != null) {
            super.set(member, variant, mods) //set variant tag
        }
        return mods
    }

    /**
     * The slot-path read of this provider. Its data is kept per fleet member id, not per module,
     * so the slot path makes no difference to it. The override is still needed: without it this
     * call would run [VariantTagProvider]'s slot-path read and answer from the variant tags
     * instead of from this provider's own store.
     *
     * @param member the member the read is made for
     * @param variant the variant to read
     * @param slotPath the slot path of [variant] on the ship, unused here
     * @return the same as [get] without a slot path
     */
    override fun get(member: FleetMemberAPI, variant: ShipVariantAPI, slotPath: String): ShipModifications? {
        return get(member, variant)
    }

    override fun set(member: FleetMemberAPI, variant: ShipVariantAPI, mods: ShipModifications) {
        throw RuntimeException("This provider is not intended to be used.")

        shipModificationMap[member.id] = mods
    }

    override fun remove(member: FleetMemberAPI, variant: ShipVariantAPI) {
        shipModificationMap.remove(member.id)
    }

    companion object {
        @JvmStatic
        var inst: PersistentDataProvider = PersistentDataProvider()

        val ET_PERSISTENTUPGRADEMAP = "ET_MODMAP"

        @JvmStatic
        val shipModificationMap: MutableMap<String, ShipModifications>
            get() {
                if (Global.getSector().persistentData[ET_PERSISTENTUPGRADEMAP] == null) {
                    Global.getSector().persistentData[ET_PERSISTENTUPGRADEMAP] = HashMap<String, ShipModifications>()
                }
                return Global.getSector().persistentData[ET_PERSISTENTUPGRADEMAP] as MutableMap<String, ShipModifications>
            }
    }
}