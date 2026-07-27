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
import exoticatechnologies.campaign.listeners.CampaignEventListener;
import exoticatechnologies.refit.RefitButtonAdderKt;
import exoticatechnologies.util.*;
import exoticatechnologies.modifications.Modification;
import exoticatechnologies.modifications.ShipModFactory;
import exoticatechnologies.modifications.ShipModLoader;
import exoticatechnologies.modifications.ShipModifications;
import exoticatechnologies.modifications.exotics.Exotic;
import exoticatechnologies.modifications.exotics.ExoticData;
import exoticatechnologies.modifications.exotics.ExoticsHandler;
import exoticatechnologies.modifications.upgrades.Upgrade;
import exoticatechnologies.modifications.upgrades.UpgradesHandler;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
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

    private static void diagnosticLog(String message) {
        log.info("[DIAG] " + message);
    }

    public static void addToFleetMember(FleetMemberAPI member, ShipVariantAPI variant) {
        if (variant == null) return;

        ShipModifications mods = ShipModFactory.generateForFleetMember(member);
        ShipVariantAPI memberRefitVarient = RefitButtonAdderKt.checkRefitVariant(member);

        diagnosticLog("addToFleetMember | member=" + member.getId() + " variant=" + variant.getHullVariantId() +
            " mods=" + (mods == null ? "null" : "UPGRADES: "+ (mods.getUpgradeMap() + ", EXOTICS: " + mods.getExoticSet())) +
            " shouldApply=" + mods.shouldApplyHullmod() +
            " memberVariant.hasHM=" + member.getVariant().hasHullMod(HULLMOD_ID) +
            " refitVariant.hasHM=" + memberRefitVarient.hasHullMod(HULLMOD_ID) +
            " variant.hasHM=" + variant.hasHullMod(HULLMOD_ID) +
            " variant.source=" + variant.getSource() +
            " variant.tags=" + variant.getTags().size());

        if (mods.shouldApplyHullmod()) {
            // Determine the root member so we can operate on the full variant tree.
            // FleetMemberHierarchy uses hullVariantId strings (stable across combat/refit)
            // to cache parent→child relationships. Returns null for root or single-module ships.
            String rootVariantId = FleetMemberHierarchy.findRootVariantId(variant.getHullVariantId());
            FleetMemberAPI rootMember = member;
            if (rootVariantId != null) {
                // Child display variant needs the hullmod
                variant.addPermaMod(HULLMOD_ID);
                FleetMemberAPI foundRoot = findMemberByVariantId(member, rootVariantId);
                if (foundRoot != null) rootMember = foundRoot;
            }

            // Add hullmod to every variant in the root's station module tree
            installHullmodRecursive(rootMember.getVariant());
            installHullmodRecursive(RefitButtonAdderKt.checkRefitVariant(rootMember));

            // Propagate hullmod to each child FleetMemberAPI's own .variant so the refit
            // screen (which reads each FM independently) shows the highlight on modules.
            if (!rootMember.getVariant().getStationModules().isEmpty()) {
                FleetMemberUtilsKt.propagateFromVariantTree(rootMember, HULLMOD_ID);
            }

            // Diagnostic: check if child modules have Exotica data after hullmod propagation
            for (Map.Entry<String, String> e : rootMember.getVariant().getStationModules().entrySet()) {
                ShipVariantAPI childV = rootMember.getVariant().getModuleVariant(e.getKey());
                if (childV != null) {
//                    boolean hasExoticaTag = childV.getTags().stream().anyMatch(t -> t.startsWith("$$EXOTICA$$"));
                    boolean hasExoticaTag = checkTagsForExotica(childV.getTags());
                    ShipModifications childMods = hasExoticaTag ? ShipModLoader.getFromVariant(childV) : null;
                    diagnosticLog("addToFleetMember | child slot=" + e.getKey()
                        + " variantId=" + childV.getHullVariantId()
                        + " hasHM=" + childV.hasHullMod(HULLMOD_ID)
                        + " hasExoticaTag=" + hasExoticaTag
                        + " childMods=" + (childMods == null ? "null" : "present"));
                }
            }

            // Create REFIT clones of the fixed variants (clones inherit hullmods from originals).
            ExtensionsKt.fixVariant(member);

            // Refresh hierarchy cache so getAllModules can find the new REFIT clone variants.
            FleetMemberHierarchy.refreshFleetCache(rootMember.getVariant());
            FleetMemberHierarchy.refreshFleetCache(RefitButtonAdderKt.checkRefitVariant(rootMember));

            // Ensure the refit display variant has the hullmod for highlight to appear.
            ShipVariantAPI refitVariant = RefitButtonAdderKt.checkRefitVariant(member);
            if (!refitVariant.hasHullMod(HULLMOD_ID)) {
                refitVariant.addPermaMod(HULLMOD_ID);
            }
            // The variant parameter may also differ from member.getVariant() — backstop it too.
//            if (variant != refitVariant && variant != member.getVariant() && !variant.hasHullMod(HULLMOD_ID)) {
            if (!variant.hasHullMod(HULLMOD_ID)) {
                variant.addPermaMod(HULLMOD_ID);
            }

            member.updateStats();

            diagnosticLog("addToFleetMember | AFTER APPLY | member=" + member.getId() +
                " memberVariant.hasHM=" + member.getVariant().hasHullMod(HULLMOD_ID) +
                " refitVariant.hasHM=" + refitVariant.hasHullMod(HULLMOD_ID) +
                " variant.hasHM=" + variant.hasHullMod(HULLMOD_ID) +
                " rootMember.id=" + rootMember.getId() +
                " rootVariantId=" + rootVariantId);

        } else {
            diagnosticLog("addToFleetMember | REMOVING hullmod | member=" + member.getId() +
                " memberVariant.hasHM=" + member.getVariant().hasHullMod(HULLMOD_ID) +
                " refitVariant.hasHM=" + memberRefitVarient.hasHullMod(HULLMOD_ID));
            if (member.getVariant().hasHullMod(HULLMOD_ID)) {
                member.getVariant().removePermaMod(HULLMOD_ID);
            }
            if (memberRefitVarient.hasHullMod(HULLMOD_ID)) {
                memberRefitVarient.removePermaMod(HULLMOD_ID);
            }
        }
    }

    private static boolean checkTagsForExotica(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) return false;
        for (String tag : tags) {
            if (tag.startsWith("$$EXOTICA$$")) return true;
        }

        // We didn't find anything, return false
        return false;
    }

    // Recursively adds HULLMOD_ID to a variant and all its station module children.
    private static void installHullmodRecursive(ShipVariantAPI v) {
        v.addPermaMod(HULLMOD_ID);
        for (String slotId : v.getStationModules().keySet()) {
            ShipVariantAPI childV = v.getModuleVariant(slotId);
            if (childV != null) {
                installHullmodRecursive(childV);
            }
        }
    }

    // Finds a FleetMemberAPI whose variant's hullVariantId matches the target, scanning
    // the member's own fleet first, then falling back to active campaign fleets.
    private static @Nullable FleetMemberAPI findMemberByVariantId(FleetMemberAPI member, String targetVariantId) {
        // Try member's own fleet first (works in refit, campaign, and simulation)
        if (member.getFleetData() != null && member.getFleetData().getFleet() != null) {
            CampaignFleetAPI fleet = member.getFleetData().getFleet();
            for (FleetMemberAPI fm : fleet.getMembersWithFightersCopy()) {
                if (fm.getVariant() != null && targetVariantId.equals(fm.getVariant().getHullVariantId())) {
                    return fm;
                }
            }
        }
        // Fallback: check active campaign fleets
        for (CampaignFleetAPI fleet : CampaignEventListener.Companion.getActiveFleets()) {
            if (fleet == null) continue;
            for (FleetMemberAPI fm : fleet.getMembersWithFightersCopy()) {
                if (fm.getVariant() != null && targetVariantId.equals(fm.getVariant().getHullVariantId())) {
                    return fm;
                }
            }
        }
        return null;
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

        // Also remove from all child module variants in the tree
        removeHullmodRecursive(shipVariant);
        // And from any child FMAPIs reachable via statsForOpCosts (refit screen)
        removeFromChildFmsByStats(shipVariant);
    }

    // Recursively removes HULLMOD_ID from a variant and all its station module children.
    private static void removeHullmodRecursive(ShipVariantAPI v) {
        for (String slotId : v.getStationModules().keySet()) {
            ShipVariantAPI childV = v.getModuleVariant(slotId);
            if (childV != null) {
                if (childV.hasHullMod(HULLMOD_ID)) {
                    childV.removePermaMod(HULLMOD_ID);
                }
                removeHullmodRecursive(childV);
            }
        }
    }

    // Walks the variant tree via statsForOpCosts to remove the hullmod from child FMAPIs.
    private static void removeFromChildFmsByStats(ShipVariantAPI v) {
        for (String slotId : v.getStationModules().keySet()) {
            ShipVariantAPI childV = v.getModuleVariant(slotId);
            if (childV != null) {
                FleetMemberAPI childFM = null;
                try {
                    MutableShipStatsAPI childStats = childV.getStatsForOpCosts();
                    if (childStats != null) {
                        childFM = FleetMemberUtils.findMemberForStats(childStats);
                    }
                } catch (Exception e) {
                    // statsForOpCosts can throw if the variant hasn't been resolved yet
                }
                if (childFM != null && childFM.getVariant().hasHullMod(HULLMOD_ID)) {
                    childFM.getVariant().removePermaMod(HULLMOD_ID);
                }
                removeFromChildFmsByStats(childV);
            }
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
        diagnosticLog("advanceInCampaign | member=" + member.getId() +
            " variant=" + member.getVariant().getHullVariantId() +
            " mods=" + (mods == null ? "null" : "UPGRADES: "+ (mods.getUpgradeMap() + ", EXOTICS: " + mods.getExoticSet())) +
            " variant.hasHM=" + member.getVariant().hasHullMod(HULLMOD_ID) +
            " variant.tags=" + member.getVariant().getTags().size() +
            " variant.source=" + member.getVariant().getSource());
        if (mods == null) {
            member.getVariant().removePermaMod(HULLMOD_ID);
            diagnosticLog("advanceInCampaign | NULL mods => removed HM | member=" + member.getId());
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
     * @param ship                   the ship/module on which the modification is installed
     * @param mod                    the modification in question
     * @param thisModuleOwnsIt       whether this module (ship) owns the modification (mod)
     * @param presentSomewhereOnShip whether this modification is present somewhere on the ship, in case it has to share it's effects
     * @return whether it should be skipped or not, dependant on {@link Modification#shouldAffectModule(ShipAPI, ShipAPI)} and {@link Modification#shouldShareEffectToOtherModules(ShipAPI, ShipAPI)}
     * @see Modification#shouldAffectModulesToShareEffectsToOtherModules()
     */
    public boolean shouldSkipModification(
        ShipAPI ship,
        Modification mod,
        boolean thisModuleOwnsIt,
        boolean presentSomewhereOnShip
    ) {
        boolean modAppliesToModules = mod.shouldAffectModule(ship.getParentStation(), ship);
        boolean modSharesEffectsWithAllModules = mod.shouldShareEffectToOtherModules(ship.getParentStation(), ship);
        boolean modShouldAffectModulesToShareEffectsToOtherModules = mod.shouldAffectModulesToShareEffectsToOtherModules();
        boolean isModuleResult = cachedCheckIsModule(ship);

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
            } else if (isModuleResult) {
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
     * Method for checking whether a {@link Modification} should be skipped before processing (calling it's callbacks on it)<br>
     * <br>
     * Called in:<br>
     * - {@link ExoticaTechHM#applyEffectsBeforeShipCreation(ShipAPI.HullSize, MutableShipStatsAPI, String)}<br>
     *
     * @param stats                  the {@link MutableShipStatsAPI} stats of the ship/module on which the modification is installed
     * @param mod                    the modification in question
     * @param thisModuleOwnsIt       whether this module (stats) owns the modification (mod) or not
     * @param presentSomewhereOnShip whether this modification is present somewhere on the ship, in case it has to share it's effects
     * @return whether it should be skipped or not, dependant on {@link Modification#shouldAffectModule(MutableShipStatsAPI)} and {@link Modification#shouldShareEffectToOtherModules(ShipAPI, ShipAPI)}
     * @see Modification#shouldAffectModulesToShareEffectsToOtherModules()
     */
    public boolean shouldSkipModification(
        MutableShipStatsAPI stats,
        Modification mod,
        boolean thisModuleOwnsIt,
        boolean presentSomewhereOnShip
    ) {
        boolean modAppliesToModules = mod.shouldAffectModule(stats);
        boolean modSharesEffectsWithAllModules = mod.shouldShareEffectToOtherModules(null, null);
        boolean modShouldAffectModulesToShareEffectsToOtherModules = mod.shouldAffectModulesToShareEffectsToOtherModules();
        boolean isModuleStats = FleetMemberHierarchy.isChildStats(stats);

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

            if (shouldSkipModification(ship, exotic, thisModuleOwnsIt, presentSomewhereOnShip)) {
                continue;
            }
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            ExoticData exoticDataToUse = null;
            if (mods.hasExotic(exotic)) {
                shipModsToUse = mods;
            } else if (thisExoticasMods.hasExotic(exotic)) {
                shipModsToUse = thisExoticasMods;
            } else {
                throw new IllegalStateException("Somehow, neither this module's ShipModifications nor the ShipMods that have the exotica have it... exotic: " + exotic);
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
            if (shouldSkipModification(ship, upgrade, thisModuleOwnsIt, presentSomewhereOnShip)) {
                continue;
            }
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            if (mods.hasUpgrade(upgrade)) {
                shipModsToUse = mods;
            } else if (thisUpgradesMods.hasUpgrade(upgrade)) {
                shipModsToUse = thisUpgradesMods;
            } else {
                throw new IllegalStateException("Somehow, neither this module's ShipModifications nor the ShipMods that have the upgrade have it... upgrade: " + upgrade);
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

        diagnosticLog("applyEffectsBeforeShipCreation | member=" + member.getId() +
            " variant=" + stats.getVariant().getHullVariantId() +
            " mods=" + (mods == null ? "null" : "UPGRADES: "+ (mods.getUpgradeMap() + ", EXOTICS: " + mods.getExoticSet())) +
            " variant.hasHM=" + stats.getVariant().hasHullMod(HULLMOD_ID) +
            " variant.tags=" + stats.getVariant().getTags().size() +
            " variant.source=" + stats.getVariant().getSource());

        if (mods == null) {
            //TODO when removing HULLMOD_ID, remove it from everywhere if it's empty
            // because, if no modules have exoticas, none of them should have the hullmod.
            // if any module has at least one exotic - all ship's modules should have the hullmod.
            if (stats.getVariant().getHullVariantId().equals(member.getVariant().getHullVariantId())) {
                diagnosticLog("applyEffectsBeforeShipCreation | NULL mods => removed HM | member=" + member.getId() + " variant=" + stats.getVariant().getHullVariantId());
                // this is the root module's own stats variant with no Exotica data —
                // safe to strip the hullmod since there are no exotics on this ship.
                // we skip this for child module variants because:
                //   a) member.getVariant() always points to the ROOT FM's variant here
                //      (findMemberForStats returns root FM for children via moduleMap),
                //      so calling member.getVariant().removePermaMod() would incorrectly
                //      strip the hullmod from the root variant.
                //   b) children legitimately have no Exotica tag of their own — null is
                //      expected; exotics from the root should still share effects to
                //      children via shouldSkipModification. removing the child's hullmod
                //      would break that sharing and the refit highlight.
                member.getVariant().removePermaMod(HULLMOD_ID);
            }
            diagnosticLog("applyEffectsBeforeShipCreation | NULL mods | member=" + member.getId()
                + " variant=" + stats.getVariant().getHullVariantId()
                + " isRoot=" + stats.getVariant().getHullVariantId().equals(member.getVariant().getHullVariantId())
                + " hasHM=" + stats.getVariant().hasHullMod(HULLMOD_ID)
                + " tags=" + StringUtils.join(",", stats.getVariant().getTags()));
            return;
        }

        // Now, lets try fetching all of ship's Modifications to derive/calculate the two new parameters
        FleetMemberAPI rootModuleMember = FleetMemberUtils.findMemberForStats(stats);    //this is root module
//        List<MutableShipStatsAPI> statsList = ShipStatsRegistry.getWholeShipsStatsFromSingleStats(stats);
        List<MutableShipStatsAPI> statsList = FleetMemberHierarchy.getAllModulesStatsFromSingleStats(stats);
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

            if (shouldSkipModification(stats, exotic, thisModuleOwnsIt, presentSomewhereOnShip)) {
                continue;
            }
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            ExoticData exoticDataToUse = null;
            if (mods.hasExotic(exotic)) {
                shipModsToUse = mods;
            } else if (thisExoticasMods.hasExotic(exotic)) {
                shipModsToUse = thisExoticasMods;
            } else {
                throw new IllegalStateException("Somehow, neither this module's ShipModifications nor the ShipMods that have the exotica have it... exotic: " + exotic);
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

            if (shouldSkipModification(stats, upgrade, thisModuleOwnsIt, presentSomewhereOnShip)) {
                continue;
            }
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            if (mods.hasUpgrade(upgrade)) {
                shipModsToUse = mods;
            } else if (thisUpgradesMods.hasUpgrade(upgrade)) {
                shipModsToUse = thisUpgradesMods;
            } else {
                throw new IllegalStateException("Somehow, neither this module's ShipModifications nor the ShipMods that have the upgrade have it... upgrade: " + upgrade);
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
        diagnosticLog("applyEffectsAfterShipCreation | member=" + (member == null ? "null" : member.getId()) +
            " variant=" + ship.getVariant().getHullVariantId() +
            " mods=" + (mods == null ? "null" : "UPGRADES: "+ (mods.getUpgradeMap() + ", EXOTICS: " + mods.getExoticSet())) +
            " variant.hasHM=" + ship.getVariant().hasHullMod(HULLMOD_ID) +
            " variant.tags=" + ship.getVariant().getTags().size() +
            " isStationModule=" + ship.isStationModule() +
            " parentStation=" + (ship.getParentStation() == null ? "null" : "present"));
        if (mods == null)
            return;   //FIXME obviously, in case of a module that has no exoticas, it can't be shared to...

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
            if (shouldSkipModification(ship, exotic, thisModuleOwnsIt, presentSomewhereOnShip)) {
                continue;
            }
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            ExoticData exoticDataToUse = null;
            if (mods.hasExotic(exotic)) {
                shipModsToUse = mods;
            } else if (thisExoticasMods.hasExotic(exotic)) {
                shipModsToUse = thisExoticasMods;
            } else {
                throw new IllegalStateException("Somehow, neither this module's ShipModifications nor the ShipMods that have the exotica have it... exotic: " + exotic);
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
            if (shouldSkipModification(ship, upgrade, thisModuleOwnsIt, presentSomewhereOnShip)) {
                continue;
            }
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            if (mods.hasUpgrade(upgrade)) {
                shipModsToUse = mods;
            } else if (thisUpgradesMods.hasUpgrade(upgrade)) {
                shipModsToUse = thisUpgradesMods;
            } else {
                throw new IllegalStateException("Somehow, neither this module's ShipModifications nor the ShipMods that have the upgrade have it... upgrade: " + upgrade);
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
            if (shouldSkipModification(ship, exotic, thisModuleOwnsIt, presentSomewhereOnShip)) {
                continue;
            }
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            if (mods.hasExotic(exotic)) {
                shipModsToUse = mods;
            } else if (thisExoticasMods.hasExotic(exotic)) {
                shipModsToUse = thisExoticasMods;
            } else {
                throw new IllegalStateException("Somehow, neither this module's ShipModifications nor the ShipMods that have the exotica have it... exotic: " + exotic);
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
            if (shouldSkipModification(ship, upgrade, thisModuleOwnsIt, presentSomewhereOnShip)) {
                continue;
            }
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            if (mods.hasUpgrade(upgrade)) {
                shipModsToUse = mods;
            } else if (thisUpgradesMods.hasUpgrade(upgrade)) {
                shipModsToUse = thisUpgradesMods;
            } else {
                throw new IllegalStateException("Somehow, neither this module's ShipModifications nor the ShipMods that have the upgrade have it... upgrade: " + upgrade);
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
        log.info("addPostDescriptionSection | member=" + member.getId() +
            " variant=" + ship.getVariant().getHullVariantId() +
            " mods=" + (mods == null ? "null" : "UPGRADES: "+ (mods.getUpgradeMap() + ", EXOTICS: " + mods.getExoticSet())));
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
