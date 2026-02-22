package exoticatechnologies.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exoticatechnologies.util.ShipStatsRegistry;
import exoticatechnologies.modifications.Modification;
import exoticatechnologies.modifications.ShipModFactory;
import exoticatechnologies.modifications.ShipModLoader;
import exoticatechnologies.modifications.ShipModifications;
import exoticatechnologies.modifications.exotics.Exotic;
import exoticatechnologies.modifications.exotics.ExoticData;
import exoticatechnologies.modifications.exotics.ExoticsHandler;
import exoticatechnologies.modifications.upgrades.Upgrade;
import exoticatechnologies.modifications.upgrades.UpgradesHandler;
import exoticatechnologies.util.ExtensionsKt;
import exoticatechnologies.util.FleetMemberUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Class representing the "exoticatech" hullmod placed on every ship/module after installing an {@link Modification}
 * on it. This hullmod is actually in charge of 'installing' exoticas/upgrades and calling their relevant methods
 */
public class ExoticaTechHM extends BaseHullMod {
    public static final String HULLMOD_ID = "exoticatech";
    private static final Color hullmodColor = new Color(94, 206, 226);
    private static final Logger log = Logger.getLogger(ExoticaTechHM.class);
    private static final Level MIN_LOG_LEVEL = Level.WARN;

    public static void addToFleetMember(FleetMemberAPI member, ShipVariantAPI variant) {
        if (variant == null) {
            return;
        }

        ShipModifications mods = ShipModFactory.generateForFleetMember(member);

        if (variant.hasHullMod(HULLMOD_ID)) {
            variant.removePermaMod(HULLMOD_ID);
        }

        if (mods.shouldApplyHullmod()) {

            ExtensionsKt.fixVariant(member);
            variant.addPermaMod(HULLMOD_ID);

            member.updateStats();
        }
    }

    public static void addToFleetMember(FleetMemberAPI member) {
        addToFleetMember(member, member.getVariant());
    }

    public static void removeFromFleetMember(FleetMemberAPI member) {
        if (member.getVariant() == null) {
            return;
        }

        ShipVariantAPI shipVariant = member.getVariant();
        if (shipVariant.hasHullMod(HULLMOD_ID)) {
            shipVariant.removePermaMod(HULLMOD_ID);
        }
    }

    @Override
    public boolean affectsOPCosts() {
        return false;
    }

    @Override
    public Color getNameColor() {
        return hullmodColor;
    }

    @Override
    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        ShipModifications mods = ShipModLoader.get(member, member.getVariant());
        if (mods == null) {
            member.getVariant().removePermaMod(HULLMOD_ID);
            return;
        }

        for (Upgrade upgrade : UpgradesHandler.UPGRADES_LIST) {
            int level = mods.getUpgrade(upgrade);
            if (level <= 0) continue;
            upgrade.advanceInCampaign(member, mods, amount);
        }

        for (Exotic exotic : ExoticsHandler.INSTANCE.getEXOTIC_LIST()) {
            if (mods.hasExotic(exotic)) {
                exotic.advanceInCampaign(member, mods, amount, Objects.requireNonNull(mods.getExoticData(exotic)));
            }
        }
    }

    /**
     * Method for checking whether a {@link Modification} should be skipped before processing (calling it's callbacks on it)
     * <br>
     * Called in:<br>
     * - {@link ExoticaTechHM#advanceInCombat(ShipAPI, float)}<br>
     * - {@link ExoticaTechHM#applyEffectsAfterShipCreation(ShipAPI, String)}<br>
     * - {@link ExoticaTechHM#applyEffectsToFighterSpawnedByShip(ShipAPI, ShipAPI, String)}<br>
     *
     * @param ship the ship/module on which the modification is installed
     * @param mod the modification in question
     * @param thisModuleOwnsIt whether this module (ship) owns the modification (mod)
     * @param presentSomewhereOnShip whether this modification is present somewhere on the ship, in case it has to share it's effects
     * @return whether it should be skipped or not, dependant on {@link Modification#shouldAffectModule(ShipAPI, ShipAPI)} and {@link Modification#shouldShareEffectToOtherModules(ShipAPI, ShipAPI)}
     * @see Modification#shouldAffectModulesToShareEffectsToOtherModules()
     */
    public boolean shouldSkipModification_NEW(
            ShipAPI ship,
            Modification mod,
            boolean thisModuleOwnsIt,
            boolean presentSomewhereOnShip
    ) {
        boolean modAppliesToModules = mod.shouldAffectModule(ship.getParentStation(), ship);
        boolean modSharesEffectsWithAllModules = mod.shouldShareEffectToOtherModules(ship.getParentStation(), ship);
        boolean modShouldAffectModulesToShareEffectsToOtherModules = mod.shouldAffectModulesToShareEffectsToOtherModules();

        boolean skip = false;

        if (!presentSomewhereOnShip) {
            // Modifications that are not on any part of the ship should be skipped
            skip = true;
        } else if (thisModuleOwnsIt) {
            // Modifications owned by this ship/module should always be applied
            skip = false;
        } else {
            // Modifications owner by other modules should be checked for sharing (cross‑module application)
            if (!modSharesEffectsWithAllModules) {
                // If it does not share with modules, then we skip it
                skip = true;
            } else if (cachedCheckIsModule(ship)) {
                // Skip only if the mod cannot affect modules AND the override flag is true
                skip = (!modAppliesToModules && modShouldAffectModulesToShareEffectsToOtherModules);
            } else {
                // Target is root so allow as we did before
                skip = false;
            }
        }
        return skip;
    }

//    public boolean shouldSkipModification(MutableShipStatsAPI stats, Modification mod) {
    public boolean shouldSkipModification_NEW(
            MutableShipStatsAPI stats,
            Modification mod,
            boolean thisModuleOwnsIt,
            boolean presentSomewhereOnShip
    ) {
        boolean modAppliesToModules = mod.shouldAffectModule(stats);
        boolean modSharesEffectsWithAllModules = mod.shouldShareEffectToOtherModules(null, null);
        boolean modShouldAffectModulesToShareEffectsToOtherModules = mod.shouldAffectModulesToShareEffectsToOtherModules();

        // Check whether these 'stats' belong to the root FleetMemberAPI (root module) or a child one
        FleetMemberAPI rootModule = FleetMemberUtils.findMemberForStats(stats);
        FleetMemberAPI moduleFMAPI = stats.getFleetMember();
        boolean isModuleStats = moduleFMAPI != rootModule;

        boolean skip = false;

        if (!presentSomewhereOnShip) {
            // Modifications that are not on any part of the ship should be skipped
            skip = true;
        } else if (thisModuleOwnsIt) {
            // Modifications owned by this ship/module should always be applied
            skip = false;
        } else {
            // Modifications owner by other modules should be checked for sharing (cross‑module application)
            if (!modSharesEffectsWithAllModules) {
                // If it does not share with modules, then we skip it
                skip = true;
//            } else if (cachedCheckIsModule(ship)) {
            } else if (isModuleStats) {
                // Skip only if the mod cannot affect modules AND the override flag is true
                skip = (!modAppliesToModules && modShouldAffectModulesToShareEffectsToOtherModules);
            } else {
                // Target is root so allow as we did before
                skip = false;
            }
        }
        return skip;
    }


    /**
     * Method for checking whether a {@link Modification} should be skipped before processing (calling it's callbacks on it)
     * <br>
     * Called in:<br>
     * - {@link ExoticaTechHM#advanceInCombat(ShipAPI, float)}<br>
     * - {@link ExoticaTechHM#applyEffectsAfterShipCreation(ShipAPI, String)}<br>
     * - {@link ExoticaTechHM#applyEffectsToFighterSpawnedByShip(ShipAPI, ShipAPI, String)}<br>
     *
     * @param ship the ship/module on which the modification is installed
     * @param mod the modification in question
     * @return whether it should be skipped or not, dependant on {@link Modification#shouldAffectModule(ShipAPI, ShipAPI)} and {@link Modification#shouldShareEffectToOtherModules(ShipAPI, ShipAPI)}
     * @see Modification#shouldAffectModulesToShareEffectsToOtherModules()
     */
    public boolean shouldSkipModification(ShipAPI ship, Modification mod) { //TODO delete his after second one proves working
        boolean modAppliesToModules = mod.shouldAffectModule(ship.getParentStation(), ship);
        boolean modSharesEffectsWithAllModules = mod.shouldShareEffectToOtherModules(ship.getParentStation(), ship);
        boolean modShouldAffectModulesToShareEffectsToOtherModules = mod.shouldAffectModulesToShareEffectsToOtherModules();

        if (cachedCheckIsModule(ship)) {
            // if should affect modules to share effects but doesn't apply to modules - skip
            if (modShouldAffectModulesToShareEffectsToOtherModules && !modAppliesToModules) {
                return true;
            } else {
                // If applies to modules or has special flag set - check if effects are shared, if not - skip
                if (!modSharesEffectsWithAllModules) {
                    return true;
                }
                // If it should share to all modules, we don't skip
            }
        }
        // If the ship that we're checking isn't a module, we don't skip either
        return false;
    }

    /**
     * Method for checking whether a {@link Modification} should be skipped before processing (calling it's callbacks on it)<br>
     * <br>
     * Called in:<br>
     * - {@link ExoticaTechHM#applyEffectsBeforeShipCreation(ShipAPI.HullSize, MutableShipStatsAPI, String)}<br>
     *
     * @param stats the {@link MutableShipStatsAPI} stats of the ship/module on which the modification is installed
     * @param mod the modification in question
     * @return whether it should be skipped or not, dependant on {@link Modification#shouldAffectModule(MutableShipStatsAPI)} and {@link Modification#shouldShareEffectToOtherModules(ShipAPI, ShipAPI)}
     * @see Modification#shouldAffectModulesToShareEffectsToOtherModules()
     */
    public boolean shouldSkipModification(MutableShipStatsAPI stats, Modification mod) { //TODO delete his after second one proves working
        boolean fleetMemberNonNull = stats.getFleetMember() != null;
        // lets just default to 'false' if fleetmember is null - it won't go into the if() anyways
        // since the first condition is for the fleetmember to be non-null
        boolean fleetMemberShipNameIsNull = (stats.getFleetMember() != null) ? stats.getFleetMember().getShipName() == null : false;
        boolean modAppliesToModules = mod.shouldAffectModule(stats);
        boolean modSharesEffectsWithAllModules = mod.shouldShareEffectToOtherModules(null, null);
        boolean modShouldAffectModulesToShareEffectsToOtherModules = mod.shouldAffectModulesToShareEffectsToOtherModules();

        if (fleetMemberNonNull && fleetMemberShipNameIsNull) {
            // if needs to apply to modules to share effects but doesn't apply to modules - skip
            if (modShouldAffectModulesToShareEffectsToOtherModules && !modAppliesToModules) {
                return true;
            } else {
                // If applies to modules or has special flag set - check if effects are shared, if not - skip
                if (!modSharesEffectsWithAllModules) {
                    return true;
                }
                // If it should share to all modules, we don't skip
            }
        }
        // If the ship that we're checking isn't a module, we don't skip either
        return false;
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        FleetMemberAPI member = FleetMemberUtils.findMemberFromShip(ship);
        if (member == null) return;

        ShipModifications mods = ShipModLoader.get(member, ship.getVariant());
        if (mods == null) return;

        // Now, lets try fetching all of ship's Modifications to derive/calculate the two new parameters
        List<ShipModifications> wholeShipsMods = ShipModLoader.getAllForShipAPI(ship);

        for (Exotic exotic : ExoticsHandler.INSTANCE.getEXOTIC_LIST()) {
            boolean thisModuleOwnsIt = mods.hasExotic(exotic);
            boolean presentSomewhereOnShip = false;
            ShipModifications thisExoticasMods = null;
            for (int i = 0; i < wholeShipsMods.size(); i++) {
                ShipModifications tempMods = wholeShipsMods.get(i);
                if (tempMods.hasExotic(exotic)) {
                    presentSomewhereOnShip = true;
                    thisExoticasMods = tempMods;
                }
            }

            if (shouldSkipModification_NEW(ship, exotic, thisModuleOwnsIt, presentSomewhereOnShip)) continue;
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            ExoticData exoticDataToUse = null;
            if (mods.hasExotic(exotic)) {
                shipModsToUse = mods;
            } else if (thisExoticasMods.hasExotic(exotic)) {
                shipModsToUse = thisExoticasMods;
            } else {
                throw new IllegalStateException("Somehow, neither this module's ShipModifications nor the ShipMods that have the exotica have it... exotic: "+exotic);
            }
            exoticDataToUse = shipModsToUse.getExoticData(exotic);

//            exotic.advanceInCombatUnpaused(ship, amount, member, mods, Objects.requireNonNull(exoticDataToUse));
            exotic.advanceInCombatUnpaused(ship, amount, member, shipModsToUse, Objects.requireNonNull(exoticDataToUse));
        }

        for (Upgrade upgrade : UpgradesHandler.UPGRADES_LIST) {
            boolean thisModuleOwnsIt = mods.hasUpgrade(upgrade);
            boolean presentSomewhereOnShip = false;
            ShipModifications thisUpgradesMods = null;
            for (int i = 0; i < wholeShipsMods.size(); i++) {
                ShipModifications tempMods = wholeShipsMods.get(i);
                if (tempMods.hasUpgrade(upgrade)) {
                    presentSomewhereOnShip = true;
                    thisUpgradesMods = tempMods;
                }
            }
            if (shouldSkipModification_NEW(ship, upgrade, thisModuleOwnsIt, presentSomewhereOnShip)) continue;
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            if (mods.hasUpgrade(upgrade)) {
                shipModsToUse = mods;
            } else if (thisUpgradesMods.hasUpgrade(upgrade)) {
                shipModsToUse = thisUpgradesMods;
            } else {
                throw new IllegalStateException("Somehow, neither this module's ShipModifications nor the ShipMods that have the upgrade have it... upgrade: "+upgrade);
            }

            upgrade.advanceInCombatUnpaused(ship, amount, member, shipModsToUse);
        }
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        FleetMemberAPI member = FleetMemberUtils.findMemberForStats(stats);
        if (member == null) {
            return;
        }

        try {
            if (!stats.getVariant().getStationModules().isEmpty()) {
                FleetMemberUtils.moduleMap.clear();

                for (Map.Entry<String, String> e : stats.getVariant().getStationModules().entrySet()) {
                    ShipVariantAPI module = stats.getVariant().getModuleVariant(e.getKey());

                    FleetMemberUtils.moduleMap.put(module.getHullVariantId(), member);
                }
            }
        } catch (Exception e) {
            log.error("Failed to get modules", e);
        }

        ShipModifications mods = ShipModLoader.get(member, stats.getVariant());

        if (mods == null) {
            member.getVariant().removePermaMod(HULLMOD_ID);
            return;
        }

        // Now, lets try fetching all of ship's Modifications to derive/calculate the two new parameters
        FleetMemberAPI rootModuleMember = FleetMemberUtils.findMemberForStats(stats);    //this is root module
        List<MutableShipStatsAPI> statsList = ShipStatsRegistry.getWholeShipsStatsFromSingleStats(stats);
        List<ShipModifications> wholeShipsMods = ShipModLoader.getAllForStats(stats);

        for (Exotic exotic : ExoticsHandler.INSTANCE.getEXOTIC_LIST()) {
//            if (!mods.hasExotic(exotic)) continue;
//            if (shouldSkipModification(stats, exotic)) continue;
//
//            exotic.applyExoticToStats(id, stats, member, mods, Objects.requireNonNull(mods.getExoticData(exotic)));
            boolean thisModuleOwnsIt = mods.hasExotic(exotic);
            boolean presentSomewhereOnShip = false;
            ShipModifications thisExoticasMods = null;
            for (int i = 0; i < wholeShipsMods.size(); i++) {
                ShipModifications tempMods = wholeShipsMods.get(i);
                if (tempMods.hasExotic(exotic)) {
                    presentSomewhereOnShip = true;
                    thisExoticasMods = tempMods;
                }
            }

            if (shouldSkipModification_NEW(stats, exotic, thisModuleOwnsIt, presentSomewhereOnShip)) continue;
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            ExoticData exoticDataToUse = null;
            if (mods.hasExotic(exotic)) {
                shipModsToUse = mods;
            } else if (thisExoticasMods.hasExotic(exotic)) {
                shipModsToUse = thisExoticasMods;
            } else {
                throw new IllegalStateException("Somehow, neither this module's ShipModifications nor the ShipMods that have the exotica have it... exotic: "+exotic);
            }
            exoticDataToUse = shipModsToUse.getExoticData(exotic);
            // We should still apply to *THIS* module's stats
            exotic.applyExoticToStats(id, stats, member, shipModsToUse, Objects.requireNonNull(exoticDataToUse));
        }

        for (Upgrade upgrade : UpgradesHandler.UPGRADES_LIST) {
//            if (!mods.hasUpgrade(upgrade)) continue;
//            if (shouldSkipModification(stats, upgrade)) continue;
//
//            upgrade.applyUpgradeToStats(stats, member, mods, mods.getUpgrade(upgrade));
            boolean thisModuleOwnsIt = mods.hasUpgrade(upgrade);
            boolean presentSomewhereOnShip = false;
            ShipModifications thisUpgradesMods = null;
            for (int i = 0; i < wholeShipsMods.size(); i++) {
                ShipModifications tempMods = wholeShipsMods.get(i);
                if (tempMods.hasUpgrade(upgrade)) {
                    presentSomewhereOnShip = true;
                    thisUpgradesMods = tempMods;
                }
            }

            if (shouldSkipModification_NEW(stats, upgrade, thisModuleOwnsIt, presentSomewhereOnShip)) continue;
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            if (mods.hasUpgrade(upgrade)) {
                shipModsToUse = mods;
            } else if (thisUpgradesMods.hasUpgrade(upgrade)) {
                shipModsToUse = thisUpgradesMods;
            } else {
                throw new IllegalStateException("Somehow, neither this module's ShipModifications nor the ShipMods that have the upgrade have it... upgrade: "+upgrade);
            }
            // We should still apply to *THIS* module's stats
            upgrade.applyUpgradeToStats(stats, member, shipModsToUse, shipModsToUse.getUpgrade(upgrade));
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        FleetMemberAPI member = FleetMemberUtils.findMemberFromShip(ship);
        if (member == null) return;

        ShipModifications mods = ShipModLoader.get(member, ship.getVariant());
        if (mods == null) return;   //FIXME obviously, in case of a module that has no exoticas, it can't be shared to...

        // Now, lets try fetching all of ship's Modifications to derive/calculate the two new parameters
        List<ShipModifications> wholeShipsMods = ShipModLoader.getAllForShipAPI(ship);

        for (Exotic exotic : ExoticsHandler.INSTANCE.getEXOTIC_LIST()) {
            boolean thisModuleOwnsIt = mods.hasExotic(exotic);
            boolean presentSomewhereOnShip = false;
            ShipModifications thisExoticasMods = null;
            for (int i = 0; i < wholeShipsMods.size(); i++) {
                ShipModifications tempMods = wholeShipsMods.get(i);
                if (tempMods.hasExotic(exotic)) {
                    presentSomewhereOnShip = true;
                    thisExoticasMods = tempMods;
                }
            }
            if (shouldSkipModification_NEW(ship, exotic, thisModuleOwnsIt, presentSomewhereOnShip)) continue;
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            ExoticData exoticDataToUse = null;
            if (mods.hasExotic(exotic)) {
                shipModsToUse = mods;
            } else if (thisExoticasMods.hasExotic(exotic)) {
                shipModsToUse = thisExoticasMods;
            } else {
                throw new IllegalStateException("Somehow, neither this module's ShipModifications nor the ShipMods that have the exotica have it... exotic: "+exotic);
            }
            exoticDataToUse = shipModsToUse.getExoticData(exotic);

            exotic.applyToShip(id, member, ship, shipModsToUse, Objects.requireNonNull(exoticDataToUse));
        }

        for (Upgrade upgrade : UpgradesHandler.UPGRADES_LIST) {
//            if (!mods.hasUpgrade(upgrade)) continue;
//            if (shouldSkipModification(ship, upgrade)) continue;
            boolean thisModuleOwnsIt = mods.hasUpgrade(upgrade);
            boolean presentSomewhereOnShip = false;
            ShipModifications thisUpgradesMods = null;
            for (int i = 0; i < wholeShipsMods.size(); i++) {
                ShipModifications tempMods = wholeShipsMods.get(i);
                if (tempMods.hasUpgrade(upgrade)) {
                    presentSomewhereOnShip = true;
                    thisUpgradesMods = tempMods;
                }
            }
            if (shouldSkipModification_NEW(ship, upgrade, thisModuleOwnsIt, presentSomewhereOnShip)) continue;
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            if (mods.hasUpgrade(upgrade)) {
                shipModsToUse = mods;
            } else if (thisUpgradesMods.hasUpgrade(upgrade)) {
                shipModsToUse = thisUpgradesMods;
            } else {
                throw new IllegalStateException("Somehow, neither this module's ShipModifications nor the ShipMods that have the upgrade have it... upgrade: "+upgrade);
            }

//            upgrade.applyToShip(member, ship, mods);
            upgrade.applyToShip(member, ship, shipModsToUse);
        }
    }

    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {
        FleetMemberAPI member = FleetMemberUtils.findMemberFromShip(ship);
        if (member == null) return;

        ShipModifications mods = ShipModLoader.get(member, ship.getVariant());
        if (mods == null) return;
        // Now, lets try fetching all of ship's Modifications to derive/calculate the two new parameters
        List<ShipModifications> wholeShipsMods = ShipModLoader.getAllForShipAPI(ship);

        for (Exotic exotic : ExoticsHandler.INSTANCE.getEXOTIC_LIST()) {
            //TODO SHARK HERE
//            if (!mods.hasExotic(exotic)) continue;
//            if (shouldSkipModification(ship, exotic)) continue;

            boolean thisModuleOwnsIt = mods.hasExotic(exotic);
            boolean presentSomewhereOnShip = false;
            ShipModifications thisExoticasMods = null;
            for (int i = 0; i < wholeShipsMods.size(); i++) {
                ShipModifications tempMods = wholeShipsMods.get(i);
                if (tempMods.hasExotic(exotic)) {
                    presentSomewhereOnShip = true;
                    thisExoticasMods = tempMods;
                }
            }
            if (shouldSkipModification_NEW(ship, exotic, thisModuleOwnsIt, presentSomewhereOnShip)) continue;
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            if (mods.hasExotic(exotic)) {
                shipModsToUse = mods;
            } else if (thisExoticasMods.hasExotic(exotic)) {
                shipModsToUse = thisExoticasMods;
            } else {
                throw new IllegalStateException("Somehow, neither this module's ShipModifications nor the ShipMods that have the exotica have it... exotic: "+exotic);
            }

//            exotic.applyToFighters(member, ship, fighter, mods);
            exotic.applyToFighters(member, ship, fighter, shipModsToUse);
        }
        for (Upgrade upgrade : UpgradesHandler.UPGRADES_LIST) {
//            if (!mods.hasUpgrade(upgrade)) continue;
//            if (shouldSkipModification(ship, upgrade)) continue;
//            upgrade.applyToFighters(member, ship, fighter, mods);
            boolean thisModuleOwnsIt = mods.hasUpgrade(upgrade);
            boolean presentSomewhereOnShip = false;
            ShipModifications thisUpgradesMods = null;
            for (int i = 0; i < wholeShipsMods.size(); i++) {
                ShipModifications tempMods = wholeShipsMods.get(i);
                if (tempMods.hasUpgrade(upgrade)) {
                    presentSomewhereOnShip = true;
                    thisUpgradesMods = tempMods;
                }
            }
            if (shouldSkipModification_NEW(ship, upgrade, thisModuleOwnsIt, presentSomewhereOnShip)) continue;
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            if (mods.hasUpgrade(upgrade)) {
                shipModsToUse = mods;
            } else if (thisUpgradesMods.hasUpgrade(upgrade)) {
                shipModsToUse = thisUpgradesMods;
            } else {
                throw new IllegalStateException("Somehow, neither this module's ShipModifications nor the ShipMods that have the upgrade have it... upgrade: "+upgrade);
            }

            upgrade.applyToFighters(member, ship, fighter, shipModsToUse);
        }
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize, ShipAPI ship) {
        FleetMemberAPI fm = FleetMemberUtils.findMemberFromShip(ship);
        if (fm == null) return "SHIP NOT FOUND";
        if (fm.getShipName() == null) {
            return "SHIP MODULE";
        }
        return fm.getShipName();
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI hullmodTooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        FleetMemberAPI member = FleetMemberUtils.findMemberFromShip(ship);
        if (member == null) return;

        ShipModifications mods = ShipModLoader.get(member, ship.getVariant());
        if (mods == null) return;


        mods.populateTooltip(member, ship.getMutableStats(), hullmodTooltip, width, 500f, false, false, false);
    }

    private void logIfOverMinLogLevel(String logMessage, Level logLevel) {
        ExtensionsKt.shouldLog(
                logMessage,
                log,
                logLevel,
                MIN_LOG_LEVEL
        );
    }

    public static void removeHullModFromVariant(ShipVariantAPI v) {
        v.removePermaMod(HULLMOD_ID);
        v.removeMod(HULLMOD_ID);
        v.removeSuppressedMod(HULLMOD_ID);
    }

    private static boolean checkIsModuleInternal(ShipAPI ship) {
        boolean isStationModule = ship.isStationModule();
        if (isStationModule) return true;

        boolean hasParentStation = ship.getParentStation() != null;
        if (hasParentStation) return true;

        boolean hasStationSlot = ship.getStationSlot() != null;
        if (hasStationSlot) return true;

        boolean isNameNull = false;
        FleetMemberAPI shipMember = ship.getFleetMember();
        if (shipMember != null) {
            isNameNull = shipMember.getShipName() == null;
        }
        if (isNameNull) return true;

        if (Global.getCombatEngine() == null) return false;

        String id = ship.getFleetMemberId();
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        BattleAPI battle = playerFleet.getBattle();
        if (battle != null) {
            List<CampaignFleetAPI> battleFleets = battle.getBothSides();

            for (CampaignFleetAPI fleet : battleFleets) {
                for (FleetMemberAPI member : fleet.getMembersWithFightersCopy()) {
                    if (member.getId().equals(id)) {
                        return false;
                    }
                }
            }
        } else { // just check player fleet, at least.
            for (FleetMemberAPI member : playerFleet.getMembersWithFightersCopy()) {
                if (member.getId().equals(id)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static String MODULE_DATA_HINT = "exotica_IsModule";

    public static boolean cachedCheckIsModule(ShipAPI ship) {
        Object isModuleData = ship.getCustomData().get(MODULE_DATA_HINT);
        if (isModuleData != null) {
            return (Boolean) isModuleData;
        }

        boolean isModuleInternal = checkIsModuleInternal(ship);
        ship.setCustomData(MODULE_DATA_HINT, isModuleInternal);
        return isModuleInternal;
    }
}
