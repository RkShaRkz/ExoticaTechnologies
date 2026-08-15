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
import exoticatechnologies.modifications.ShipModificationsKt;
import exoticatechnologies.modifications.exotics.Exotic;
import exoticatechnologies.modifications.exotics.ExoticData;
import exoticatechnologies.modifications.exotics.ExoticsHandler;
import exoticatechnologies.modifications.upgrades.Upgrade;
import exoticatechnologies.modifications.upgrades.UpgradesHandler;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    // refit screen clones variant IDs with numeric suffixes (e.g. "_0" instead of "_Start"),
    // so exact string comparison between stats variant and member variant can fail.
    // strip the last "_suffix" from both and compare the prefixes to determine root-variant identity.
    private static boolean fuzzyVariantMatch(ShipVariantAPI first, ShipVariantAPI second) {
        // First, get hullVariantIds of these variants
        String a = first.getHullVariantId();
        String b = second.getHullVariantId();
        // Then, strip everything after last '_'
        int aIdx = a.lastIndexOf('_');
        int bIdx = b.lastIndexOf('_');
        // If we managed to strip them both, compare them both without the last _suffix,
        // otherwise compare them raw as-is
        if (aIdx > 0 && bIdx > 0) {
            return a.substring(0, aIdx).contentEquals(b.substring(0, bIdx));
        }
        return a.contentEquals(b);
    }

    public static void addToFleetMember(FleetMemberAPI member, ShipVariantAPI variant) {
        if (variant == null) return;

        ShipModifications mods = ShipModFactory.generateForFleetMember(member);
        ShipVariantAPI memberRefitVarient = RefitButtonAdderKt.checkRefitVariant(member);

        // Root resolution is hoisted ABOVE the apply/remove decision: when this member is the root
        // of a module ship, the hullmod must be decided from the WHOLE ship's modifications, not this
        // member's own (its own variant carries no tags when the exotic lives on a child module).
        // FleetMemberHierarchy uses stable hullVariantId strings; returns null for root/single-module.
        String rootVariantId = FleetMemberHierarchy.findRootVariantId(variant.getHullVariantId());
        FleetMemberAPI rootMember = member;
        if (rootVariantId != null) {
            FleetMemberAPI foundRoot = findMemberByVariantId(member, rootVariantId);
            if (foundRoot != null) rootMember = foundRoot;
        }

        // Whole-ship modifications via a variant-tree walk (NOT the identity-based hierarchy, which
        // collapses children to rootFM after fixVariant). Includes the root's own mods.
        List<ShipModifications> wholeShipMods = ShipModLoader.getWholeShipMods(rootMember, rootMember.getVariant());

        // The hullmod is applied whenever ANY module in the ship tree has ANY Modification
        // (Exotic or Upgrade — shouldApplyHullmod covers both). A child member that owns the exotic
        // still lands here (its own mods are non-empty), so the APPLY branch below propagates to the
        // root; a root member whose exotics live on children now lands here too via wholeShipMods.
        if (consolidateHullmod(mods, wholeShipMods)) {
            // Child display variant needs the hullmod
            if (rootVariantId != null) {
                variant.addPermaMod(HULLMOD_ID);
            }

            // Add hullmod to every variant in the root's station module tree
            installHullmodRecursive(rootMember.getVariant());
            installHullmodRecursive(RefitButtonAdderKt.checkRefitVariant(rootMember));

            // Propagate hullmod to each child FleetMemberAPI's own .variant so the refit
            // screen (which reads each FM independently) shows the highlight on modules.
            if (!rootMember.getVariant().getStationModules().isEmpty()) {
                FleetMemberUtilsKt.propagateFromVariantTree(rootMember, HULLMOD_ID);
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

        } else {
            // consolidateHullmod == false => NO module anywhere in the ship has ANY Modification.
            // Strip the hullmod from the ENTIRE ship (root + all module variants + refit tree),
            // not just this member — otherwise the root/other modules keep a stale hullmod.
            removeHullmodEverywhere(rootMember);
            // Backstop (mirror of the APPLY branch's force-add at lines 146-150): the `variant`
            // handed to this call can be a refit display clone or the edited module FM's own variant
            // that no walked graph (root tree, refit tree, display tree) references by identity.
            // removeHullmodEverywhere's tree walks strip root+children, but a leaf FM's `.variant`
            // or a panel clone may only be reachable through the argument itself.
            if (variant.hasHullMod(HULLMOD_ID)) {
                removeHullModFromVariant(variant);
            }
            if (memberRefitVarient.hasHullMod(HULLMOD_ID)) {
                removeHullModFromVariant(memberRefitVarient);
            }
        }
    }

    // Recursively adds HULLMOD_ID to a variant and all its station module children.
    // Shared pre-order walk (ExtensionsKt.forEachModuleVariant) over the descendants; the lambda
    // captures only the compile-time HULLMOD_ID constant, so it is stateless and JVM-cached.
    private static void installHullmodRecursive(ShipVariantAPI v) {
        v.addPermaMod(HULLMOD_ID);
        ExtensionsKt.forEachModuleVariant(v, new ModuleVariantAction() {
            @Override
            public void accept(@NotNull ShipVariantAPI child) {
                child.addPermaMod(HULLMOD_ID);
            }
        });
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
        removeHullmodEverywhere(member);
    }

    // Strips HULLMOD_ID from the ENTIRE ship: the member's own variant, every station-module
    // child in the tree, the member's refit tree, AND the refit display working tree. Used
    // whenever no module anywhere on the ship has any Modification left — leaving a single stale
    // hullmod on any object graph would keep the refit highlight (or the campaign effects) alive
    // for a ship that no longer has any exotica.
    private static void removeHullmodEverywhere(FleetMemberAPI member) {
        if (member == null || member.getVariant() == null) {
            return;
        }

        ShipVariantAPI shipVariant = member.getVariant();
        stripTree(shipVariant);

        ShipVariantAPI refitVariant = RefitButtonAdderKt.checkRefitVariant(member);
        if (refitVariant != shipVariant) {
            stripTree(refitVariant);
        }

        // The refit display working tree (RefitButtonAdder.variant) is the tree the refit screen
        // renders AND re-binds to the fleet member on refit confirm. checkRefitVariant(member)
        // above returns it ONLY when `member` is the refit-selected member; while a CHILD module is
        // being edited, checkRefitVariant(root) falls back to the root's own variant, so the
        // display tree is skipped and its stale hullmod survives the uninstall via refit confirm.
        // getRefitDisplayVariant() reads RefitButtonAdder.variant for whatever member is selected,
        // so the display tree is stripped regardless of selection. Null-safe (no-op) when the refit
        // screen is closed, keeping the per-frame advanceInCampaign strip cheap.
        ShipVariantAPI displayVariant = RefitButtonAdderKt.getRefitDisplayVariant();
        if (displayVariant != null && displayVariant != shipVariant && displayVariant != refitVariant) {
            stripTree(displayVariant);
        }
    }

    // Strips HULLMOD_ID from one variant tree: the root and every station-module descendant.
    // Shared by all removeHullmodEverywhere graphs. The root is guarded on hasHullMod so already
    // clean trees skip the mod-map mutations; descendants are guarded inside removeHullmodRecursive.
    private static void stripTree(ShipVariantAPI v) {
        if (v.hasHullMod(HULLMOD_ID)) {
            removeHullModFromVariant(v);
        }
        removeHullmodRecursive(v);
    }

    // Removes HULLMOD_ID from all station module children of a variant (the caller removes it
    // from the root itself). Walker-visits every descendant via the shared pre-order walk.
    private static void removeHullmodRecursive(ShipVariantAPI v) {
        ExtensionsKt.forEachModuleVariant(v, new ModuleVariantAction() {
            @Override
            public void accept(@NotNull ShipVariantAPI child) {
                if (child.hasHullMod(HULLMOD_ID)) {
                    removeHullModFromVariant(child);
                }
            }
        });
    }

    // Whether the hullmod should be present on the ship: true when either this member's own
    // ShipModifications OR any module anywhere on the ship has any Upgrade/Exotic installed.
    // Exotic and Upgrade subclasses of Modification are both covered by shouldApplyHullmod().
    private static boolean consolidateHullmod(ShipModifications mods, List<ShipModifications> wholeShipMods) {
        if (mods != null && mods.shouldApplyHullmod()) return true;
        for (ShipModifications m : wholeShipMods) {
            if (m.shouldApplyHullmod()) return true;
        }
        return false;
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

        // A child module member must NOT run whole-ship campaign effects — the ROOT runs them once.
        // It must also keep its own hullmod while the ship still has modifications anywhere.
        boolean isRoot = member.getVariant().getStationModules().isEmpty()
            ? FleetMemberHierarchy.findRootVariantId(member.getVariant().getHullVariantId()) == null
            : true;
        if (!isRoot) {
            if (mods == null) {
                List<ShipModifications> wholeShipMods = ShipModLoader.getWholeShipMods(member, member.getVariant());
                if (ShipModificationsKt.isActuallyEmpty(wholeShipMods)) {
                    removeHullmodEverywhere(member);
                }
            }
            return;
        }

        // Root member: the hullmod must persist while ANY module has ANY Modification, and the whole
        // ship's campaign effects run once here (root-anchored). This is what lets an exotic that was
        // installed on a CHILD module still tick its campaign-layer effects.
        List<ShipModifications> wholeShipMods = ShipModLoader.getWholeShipMods(member, member.getVariant());
        if (ShipModificationsKt.isActuallyEmpty(wholeShipMods)) {
            removeHullmodEverywhere(member);
            return;
        }

        // wholeShipMods is deduped by hullVariantId in getWholeShipMods, so each installation is
        // iterated exactly once even when the same logical variant exists as stock/REFIT/combat clones.
        for (ShipModifications shipMods : wholeShipMods) {
            for (Upgrade upgrade : UpgradesHandler.UPGRADES_LIST) {
                int level = shipMods.getUpgrade(upgrade);
                if (level <= 0) continue;
                upgrade.advanceInCampaign(member, shipMods, amount);
            }

            for (Exotic exotic : ExoticsHandler.INSTANCE.getEXOTIC_LIST()) {
                if (shipMods.hasExotic(exotic)) {
                    exotic.advanceInCampaign(member, shipMods, amount, Objects.requireNonNull(shipMods.getExoticData(exotic)));
                }
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

        // We cannot use ShipModLoader.getAllForShipAPI(ship) here because ShipAPI
        // modules (childModulesCopy / parentStation) aren't reliably connected to
        // each other in refit and simulation contexts — parentStation can be null
        // on child ShipAPIs, so getAllShipSections fails to discover siblings/root.
        // ShipModLoader.getAllForStats(ship.getMutableStats()) piggybacks off the
        // stats → FleetMember resolution path (moduleMap + variant tree walk) which
        // correctly resolves all modules to the root FM's cached exotic data.
        List<ShipModifications> wholeShipsMods = ShipModLoader.getAllForStats(ship.getMutableStats());

        if (mods == null && wholeShipsMods.isEmpty()) {
            return;
        }

        for (Exotic exotic : ExoticsHandler.INSTANCE.getEXOTIC_LIST()) {
            boolean thisModuleOwnsIt = mods != null && mods.hasExotic(exotic);
            boolean presentSomewhereOnShip = false;
            ShipModifications thisExoticasMods = null;
            for (int i = 0; i < wholeShipsMods.size(); i++) {
                ShipModifications tempMods = wholeShipsMods.get(i);
                if (tempMods.hasExotic(exotic)) {
                    presentSomewhereOnShip = true;
                    thisExoticasMods = tempMods;
                }
            }

            boolean skip = shouldSkipModification(ship, exotic, thisModuleOwnsIt, presentSomewhereOnShip);
            if (skip) {
                continue;
            }
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            ExoticData exoticDataToUse = null;
            if (mods != null && mods.hasExotic(exotic)) {
                shipModsToUse = mods;
            } else if (thisExoticasMods != null && thisExoticasMods.hasExotic(exotic)) {
                shipModsToUse = thisExoticasMods;
            } else {
                throw new IllegalStateException("Somehow, neither this module's ShipModifications nor the ShipMods that have the exotica have it... exotic: " + exotic);
            }
            exoticDataToUse = shipModsToUse.getExoticData(exotic);

//            exotic.advanceInCombatUnpaused(ship, amount, member, mods, Objects.requireNonNull(exoticDataToUse));
            exotic.advanceInCombatUnpaused(ship, amount, member, shipModsToUse, Objects.requireNonNull(exoticDataToUse));
        }

        for (Upgrade upgrade : UpgradesHandler.UPGRADES_LIST) {
            boolean thisModuleOwnsIt = mods != null && mods.hasUpgrade(upgrade);
            boolean presentSomewhereOnShip = false;
            ShipModifications thisUpgradesMods = null;
            for (int i = 0; i < wholeShipsMods.size(); i++) {
                ShipModifications tempMods = wholeShipsMods.get(i);
                if (tempMods.hasUpgrade(upgrade)) {
                    presentSomewhereOnShip = true;
                    thisUpgradesMods = tempMods;
                }
            }
            boolean skip = shouldSkipModification(ship, upgrade, thisModuleOwnsIt, presentSomewhereOnShip);
            if (skip) {
                continue;
            }
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            if (mods != null && mods.hasUpgrade(upgrade)) {
                shipModsToUse = mods;
            } else if (thisUpgradesMods != null && thisUpgradesMods.hasUpgrade(upgrade)) {
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

        // Whole-ship modifications via the variant-tree walk (getWholeShipMods), which resolves
        // child modules to the root FM's tree — FleetMemberHierarchy cannot connect child-to-child.
        List<ShipModifications> wholeShipsMods = ShipModLoader.getWholeShipMods(member, stats.getVariant());

        // combined guard: strip whenever NO module anywhere on the ship has any exotic data.
        // isActuallyEmpty() (not isEmpty()) because child modules always carry placeholder
        // ShipModifications, so the whole-ship list is structurally non-empty for module ships.
        // Not gated on `mods == null` anymore: ShipModFactory/provider lookups return a non-null
        // EMPTY ShipModifications for a cleared ship, so the old guard let any surviving hullmod
        // variant (e.g. a leaf FM's `.variant` instance that escaped the remove-time strip) live
        // forever. Self-heals here instead: the next ship-creation pass strips it from everywhere.
        if (ShipModificationsKt.isActuallyEmpty(wholeShipsMods)) {
            if (fuzzyVariantMatch(stats.getVariant(), member.getVariant())) {
                // this is the root module's own stats variant with no Exotica data — the whole
                // ship is empty, so strip the hullmod from everywhere (root + children + refit).
                removeHullmodEverywhere(member);
            }
            return;
        }

        for (Exotic exotic : ExoticsHandler.INSTANCE.getEXOTIC_LIST()) {
//            if (!mods.hasExotic(exotic)) continue;
//            if (shouldSkipModification(stats, exotic)) continue;
//
//            exotic.applyExoticToStats(id, stats, member, mods, Objects.requireNonNull(mods.getExoticData(exotic)));
            boolean thisModuleOwnsIt = mods != null && mods.hasExotic(exotic);
            boolean presentSomewhereOnShip = false;
            ShipModifications thisExoticasMods = null;
            for (int i = 0; i < wholeShipsMods.size(); i++) {
                ShipModifications tempMods = wholeShipsMods.get(i);
                if (tempMods.hasExotic(exotic)) {
                    presentSomewhereOnShip = true;
                    thisExoticasMods = tempMods;
                }
            }

            boolean skip = shouldSkipModification(stats, exotic, thisModuleOwnsIt, presentSomewhereOnShip);
            if (skip) {
                continue;
            }
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            ExoticData exoticDataToUse = null;
            if (mods != null && mods.hasExotic(exotic)) {
                shipModsToUse = mods;
            } else if (thisExoticasMods != null && thisExoticasMods.hasExotic(exotic)) {
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
            boolean thisModuleOwnsIt = mods != null && mods.hasUpgrade(upgrade);
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
            if (mods != null && mods.hasUpgrade(upgrade)) {
                shipModsToUse = mods;
            } else if (thisUpgradesMods != null && thisUpgradesMods.hasUpgrade(upgrade)) {
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
        if (member == null) {
            return;
        }

        ShipModifications mods = ShipModLoader.get(member, ship.getVariant());
        // We cannot use ShipModLoader.getAllForShipAPI(ship) here because ShipAPI
        // modules (childModulesCopy / parentStation) aren't reliably connected to
        // each other in refit and simulation contexts — parentStation can be null
        // on child ShipAPIs, so getAllShipSections fails to discover siblings/root.
        // ShipModLoader.getAllForStats(ship.getMutableStats()) piggybacks off the
        // stats → FleetMember resolution path (moduleMap + variant tree walk) which
        // correctly resolves all modules to the root FM's cached exotic data.
        List<ShipModifications> wholeShipsMods = ShipModLoader.getAllForStats(ship.getMutableStats());

        if (mods == null && wholeShipsMods.isEmpty()) {
            return;   //FIXME obviously, in case of a module that has no exoticas, it can't be shared to...
            // I believe this new thing fixes the FIXME
        }


        for (Exotic exotic : ExoticsHandler.INSTANCE.getEXOTIC_LIST()) {
            boolean thisModuleOwnsIt = mods != null && mods.hasExotic(exotic);
            boolean presentSomewhereOnShip = false;
            ShipModifications thisExoticasMods = null;
            for (int i = 0; i < wholeShipsMods.size(); i++) {
                ShipModifications tempMods = wholeShipsMods.get(i);
                if (tempMods.hasExotic(exotic)) {
                    presentSomewhereOnShip = true;
                    thisExoticasMods = tempMods;
                }
            }
            boolean skip = shouldSkipModification(ship, exotic, thisModuleOwnsIt, presentSomewhereOnShip);
            if (skip) {
                continue;
            }
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = null;
            ExoticData exoticDataToUse = null;
            if (mods != null && mods.hasExotic(exotic)) {
                shipModsToUse = mods;
            } else if (thisExoticasMods != null && thisExoticasMods.hasExotic(exotic)) {
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
            boolean thisModuleOwnsIt = mods != null && mods.hasUpgrade(upgrade);
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
            ShipModifications shipModsToUse = null;
            if (mods != null && mods.hasUpgrade(upgrade)) {
                shipModsToUse = mods;
            } else if (thisUpgradesMods != null && thisUpgradesMods.hasUpgrade(upgrade)) {
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

        // We cannot use ShipModLoader.getAllForShipAPI(ship) here because ShipAPI
        // modules (childModulesCopy / parentStation) aren't reliably connected to
        // each other in refit and simulation contexts — parentStation can be null
        // on child ShipAPIs, so getAllShipSections fails to discover siblings/root.
        // ShipModLoader.getAllForStats(ship.getMutableStats()) piggybacks off the
        // stats → FleetMember resolution path (moduleMap + variant tree walk) which
        // correctly resolves all modules to the root FM's cached exotic data.
        List<ShipModifications> wholeShipsMods = ShipModLoader.getAllForStats(ship.getMutableStats());

        if (mods == null && wholeShipsMods.isEmpty()) {
            return;
        }

        for (Exotic exotic : ExoticsHandler.INSTANCE.getEXOTIC_LIST()) {
            boolean thisModuleOwnsIt = mods != null && mods.hasExotic(exotic);
            boolean presentSomewhereOnShip = false;
            ShipModifications thisExoticasMods = null;
            for (int i = 0; i < wholeShipsMods.size(); i++) {
                ShipModifications tempMods = wholeShipsMods.get(i);
                if (tempMods.hasExotic(exotic)) {
                    presentSomewhereOnShip = true;
                    thisExoticasMods = tempMods;
                }
            }
            boolean skip = shouldSkipModification(ship, exotic, thisModuleOwnsIt, presentSomewhereOnShip);
            if (skip) {
                continue;
            }
            // Now, determine which shipMods to use - if our mods contain data, lets call it with our mods;
            // otherwise, lets call it with the other one that we identified above
            ShipModifications shipModsToUse = mods;
            if (mods != null && mods.hasExotic(exotic)) {
                shipModsToUse = mods;
            } else if (thisExoticasMods != null && thisExoticasMods.hasExotic(exotic)) {
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
            boolean thisModuleOwnsIt = mods != null && mods.hasUpgrade(upgrade);
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
            if (mods != null && mods.hasUpgrade(upgrade)) {
                shipModsToUse = mods;
            } else if (thisUpgradesMods != null && thisUpgradesMods.hasUpgrade(upgrade)) {
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
