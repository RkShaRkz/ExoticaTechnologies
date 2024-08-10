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
import exoticatechnologies.modifications.exotics.ExoticsHandler
import exoticatechnologies.modifications.upgrades.UpgradesHandler

class ExoticaEveryFramePlugin :
        BaseEveryFrameCombatPlugin() {

    var listOfShips: MutableList<ShipAPI> = mutableListOf()

    override fun init(engine: CombatEngineAPI) {
        ParticleController.INSTANCE.PARTICLES.clear()
        engine.addLayeredRenderingPlugin(ParticleController.INSTANCE)
    }

    override fun advance(amount: Float, events: List<InputEventAPI>) {
        val engine = Global.getCombatEngine()
        val ships: List<ShipAPI> = engine.ships

        // First, do a check and note which ships are new in combat, and which have left
        // While doing that, also initialize their exotics/upgrades
        // Then, go through each currently-present ship and call their advanceInCombatAlways() method

        // check to see which ships are new and which are no longer there
        val engineShipsContainWholeListOfShips = ships.containsAll(listOfShips)
        val listOfShipsContainWholeEngineShips = listOfShips.containsAll(ships)
        if (!engineShipsContainWholeListOfShips) {
            // Some ships were removed from the fight
            val removedShips = ships.subtract(listOfShips.toSet())
            for (ship in removedShips) {
                handleRemovedShip(ship)
            }
        }
        if (!listOfShipsContainWholeEngineShips) {
            // Some ships have entered the fight
            val newShips = listOfShips.subtract(ships)
            for (ship in newShips) {
                handleNewShip(ship)
            }
        }

        for (ship in ships) {
            val member = ship.fleetMember
            if (ship.fleetMember != null) {
                val mods = ShipModLoader.get(ship.fleetMember, ship.fleetMember.variant)
                if (mods != null) {
                    for (upgrade in UpgradesHandler.UPGRADES_LIST) {
                        if (!hasInitialized(upgrade, engine)) {
                            initialize(upgrade, engine)
                        }

                        if (mods.getUpgrade(upgrade) <= 0) continue
                        upgrade.advanceInCombatAlways(ship, member, mods)
                    }

                    for (exotic in ExoticsHandler.EXOTIC_LIST) {
                        if (!hasInitialized(exotic, engine)) {
                            initialize(exotic, engine)
                        }

                        if (!mods.hasExotic(exotic)) continue
                        exotic.advanceInCombatAlways(ship, member, mods, mods.getExoticData(exotic)!!)
                    }
                }
            }
        }


        // Update our list of ships
        listOfShips = ships.toMutableList()
    }


    private fun handleRemovedShip(ship: ShipAPI) {
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
                    // Figure out reason
                    val reason = figureOutRemovalReason(ship)
                    upgrade.onOwnerShipRemovedFromCombat(ship, member, mods, reason)
                }

                for (exotic in ExoticsHandler.EXOTIC_LIST) {
                    if (!hasInitialized(exotic, engine)) {
                        initialize(exotic, engine)
                    }

                    if (!mods.hasExotic(exotic)) continue
                    // Figure out reason
                    val reason = figureOutRemovalReason(ship)
                    exotic.onOwnerShipRemovedFromCombat(ship, member, mods, mods.getExoticData(exotic)!!, reason)
                }
            }
        }
    }

    private fun figureOutRemovalReason(ship: ShipAPI) : ExoticaShipRemovalReason {
        val enemyRetreatedShips: List<FleetMemberAPI?> = Global.getCombatEngine().getFleetManager(FleetSide.ENEMY).retreatedCopy
        val playerRetreatedShips: List<FleetMemberAPI?> = Global.getCombatEngine().getFleetManager(FleetSide.PLAYER).retreatedCopy
        val shipRetreated = enemyRetreatedShips.contains<Any?>(ship) || playerRetreatedShips.contains<Any?>(ship)

        // Assume that the ship died if it's at less-or-equal to 1% of it's health
        val reason = if (shipRetreated) {
            ExoticaShipRemovalReason.RETREAT
        } else if (ship.hullLevel <= 0.01f) {
            ExoticaShipRemovalReason.DEATH
        } else {
            // This might happen when the combat finishes ?
            ExoticaShipRemovalReason.NONE
        }

        return reason
    }

    private fun handleNewShip(ship: ShipAPI) {
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
                    upgrade.onOwnerShipEnteredCombat(ship, member, mods)
                }

                for (exotic in ExoticsHandler.EXOTIC_LIST) {
                    if (!hasInitialized(exotic, engine)) {
                        initialize(exotic, engine)
                    }

                    if (!mods.hasExotic(exotic)) continue
                    exotic.onOwnerShipEnteredCombat(ship, member, mods, mods.getExoticData(exotic)!!)
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