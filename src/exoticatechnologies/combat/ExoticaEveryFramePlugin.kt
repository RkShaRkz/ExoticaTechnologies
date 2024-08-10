package exoticatechnologies.combat

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.mission.FleetSide
import exoticatechnologies.combat.particles.ParticleController
import exoticatechnologies.modifications.Modification
import exoticatechnologies.modifications.ShipModLoader
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.modifications.exotics.ExoticsHandler
import exoticatechnologies.modifications.upgrades.Upgrade
import exoticatechnologies.modifications.upgrades.UpgradesHandler
import exoticatechnologies.util.AnonymousLogger
import exoticatechnologies.util.getNamesOfShipsInListOfShips

class ExoticaEveryFramePlugin :
        BaseEveryFrameCombatPlugin() {

    var listOfShips: MutableList<ShipAPI?> = mutableListOf()

    override fun init(engine: CombatEngineAPI) {
        ParticleController.INSTANCE.PARTICLES.clear()
        engine.addLayeredRenderingPlugin(ParticleController.INSTANCE)
    }

    override fun advance(amount: Float, events: List<InputEventAPI>) {
        val engine = Global.getCombatEngine()
        val ships: List<ShipAPI?> = engine.ships

        // First, do a check and note which ships are new in combat, and which have left
        // While doing that, also initialize their exotics/upgrades
        // Then, go through each currently-present ship and call their advanceInCombatAlways() method

        // check to see which ships are new and which are no longer there
        if (ships.size != listOfShips.size) {
            // Some ships were removed from the fight if they were present in previous state but not in current state
            val removedShips = listOfShips.filterNotNull().filter { it !in ships }
            for (ship in removedShips) {
                handleRemovedShip(ship)
            }

            // Some ships have entered the fight if they were not present in previous state but are in current state
            val newShips = ships.filterNotNull().filter { it !in listOfShips }
            for (ship in newShips) {
                handleNewShip(ship)
            }
        }

        for (ship in ships) {
            ship?.let {
                handleShipActionsAfterInitializing(
                        it,
                        { upgrade: Upgrade, _: ShipAPI, member: FleetMemberAPI, mods: ShipModifications ->
                            upgrade.advanceInCombatAlways(it, member, mods)
                        },
                        { exotic: Exotic, _: ShipAPI, member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData ->
                            exotic.advanceInCombatAlways(it, member, mods, exoticData)
                        }
                )
            }
        }


        // Update our list of ships
        listOfShips = ships.filterNotNull().toMutableList()
    }

    private fun handleRemovedShip(ship: ShipAPI) {
        handleShipActionsAfterInitializing(
                ship,
                { upgrade: Upgrade, _: ShipAPI, member: FleetMemberAPI, mods: ShipModifications ->
                    val reason = figureOutRemovalReason(ship)
                    upgrade.onOwnerShipRemovedFromCombat(ship, member, mods, reason)
                },
                { exotic: Exotic, _: ShipAPI, member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData ->
                    val reason = figureOutRemovalReason(ship)
                    exotic.onOwnerShipRemovedFromCombat(ship, member, mods, exoticData, reason)
                }
        )
    }

    private fun figureOutRemovalReason(ship: ShipAPI): ExoticaShipRemovalReason {
        val enemyRetreatedShips: List<FleetMemberAPI?> = Global.getCombatEngine().getFleetManager(FleetSide.ENEMY).retreatedCopy
        val playerRetreatedShips: List<FleetMemberAPI?> = Global.getCombatEngine().getFleetManager(FleetSide.PLAYER).retreatedCopy
        val shipRetreated = enemyRetreatedShips.contains<Any?>(ship) || playerRetreatedShips.contains<Any?>(ship)

        // Assume that the ship died if it's at less-or-equal to 1% of it's health
        val reason = if (shipRetreated) {
            ExoticaShipRemovalReason.RETREAT
        } else if (ship.hullLevel <= 0.01f || ship.isAlive.not()) {
            ExoticaShipRemovalReason.DEATH
        } else {
            // This might happen when the combat finishes ?
            ExoticaShipRemovalReason.NONE_OR_UNKNOWN
        }

        return reason
    }

    private fun handleNewShip(ship: ShipAPI) {
        handleShipActionsAfterInitializing(
                ship,
                { upgrade: Upgrade, shipAPI: ShipAPI, member: FleetMemberAPI, mods: ShipModifications ->
                    upgrade.onOwnerShipEnteredCombat(ship, member, mods)
                },
                { exotic: Exotic, shipAPI: ShipAPI, member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData ->
                    exotic.onOwnerShipEnteredCombat(ship, member, mods, exoticData)
                }
        )
    }

    private fun handleShipActionsAfterInitializing(
            ship: ShipAPI,
            upgradeAction: (upgrade: Upgrade, P1: ShipAPI, P2: FleetMemberAPI, P3: ShipModifications) -> Unit,
            exoticAction: (exotic: Exotic, P1: ShipAPI, P2: FleetMemberAPI, P3: ShipModifications, exoticData: ExoticData) -> Unit
    ) {
        val engine = Global.getCombatEngine()
        val member = ship.fleetMember
        if (ship.fleetMember != null) {
            val mods = ShipModLoader.get(ship.fleetMember, ship.fleetMember.variant)
            if (mods != null) {
                for (upgrade in UpgradesHandler.UPGRADES_LIST) {
                    if (!hasInitialized(upgrade, engine)) {
                        initialize(upgrade, engine)
                    }

                    if (mods.getUpgrade(upgrade) <= 0) continue
                    upgradeAction.invoke(upgrade, ship, member, mods)
                }

                for (exotic in ExoticsHandler.EXOTIC_LIST) {
                    if (!hasInitialized(exotic, engine)) {
                        initialize(exotic, engine)
                    }

                    if (!mods.hasExotic(exotic)) continue
                    exoticAction.invoke(exotic, ship, member, mods, mods.getExoticData(exotic)!!)
                }
            }
        }
    }

    companion object {
        private fun hasInitialized(mod: Modification, engine: CombatEngineAPI = Global.getCombatEngine()): Boolean {
            return engine.customData.containsKey(mod.key)
        }

        private fun initialize(mod: Modification, engine: CombatEngineAPI = Global.getCombatEngine()) {
            engine.customData[mod.key] = true
            mod.init(engine)
        }
    }
}