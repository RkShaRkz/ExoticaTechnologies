package exoticatechnologies.modifications.dataproviders

import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import exoticatechnologies.modifications.ShipModLoader
import exoticatechnologies.modifications.ShipModifications

/**
 * Marks a provider that can distinguish modules by slot.
 *
 * The base [ShipModLoader.Provider] contract is deliberately unchanged; only providers with one
 * record per module can honor a slot path, so slot-aware lookup is opt-in.
 */
interface ModuleSlotDataProvider : ShipModLoader.Provider {
    /**
     * Reads modifications for a module whose slot is already known.
     *
     * Tree walks pay for the path once and must not have a provider rediscover it by hull id,
     * because identical modules share ids.
     */
    fun getAtSlotPath(member: FleetMemberAPI, variant: ShipVariantAPI, slotPath: String): ShipModifications?
}
