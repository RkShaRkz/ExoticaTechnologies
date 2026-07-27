package exoticatechnologies.modifications

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import org.apache.log4j.Logger

class PersistentDataProvider: VariantTagProvider() {
    fun getFromId(id: String): ShipModifications? {
        return shipModificationMap[id]
    }

    override fun get(member: FleetMemberAPI, variant: ShipVariantAPI): ShipModifications? {
        val mods: ShipModifications? = getFromId(member.id)
        diagnosticLog("PersistentDataProvider.get | member=${member.id} variant=${variant.hullVariantId} " +
            "persistentKeyExists=${mods != null} " +
            "variantTagsBeforeSuperSet=${variant.tags.size}"
        )
        if (mods != null) {
            super.set(member, variant, mods) //set variant tag
            diagnosticLog("PersistentDataProvider.get | after super.set variantTags=${variant.tags.size} " +
                "result=UPGRADES: ${mods.getUpgradeMap()}, EXOTICS: ${mods.getExoticSet()}"
            )
        }
        return mods
    }

    override fun set(member: FleetMemberAPI, variant: ShipVariantAPI, mods: ShipModifications) {
        throw RuntimeException("This provider is not intended to be used.")

        shipModificationMap[member.id] = mods
    }

    override fun remove(member: FleetMemberAPI, variant: ShipVariantAPI) {
        shipModificationMap.remove(member.id)
    }

    companion object {
        private val log = Logger.getLogger(PersistentDataProvider::class.java)

        private fun diagnosticLog(message: String) {
            log.info("[DIAG] $message")
        }

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