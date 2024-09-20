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
import exoticatechnologies.modifications.Modification;
import exoticatechnologies.modifications.ShipModFactory;
import exoticatechnologies.modifications.ShipModLoader;
import exoticatechnologies.modifications.ShipModifications;
import exoticatechnologies.modifications.exotics.Exotic;
import exoticatechnologies.modifications.exotics.ExoticsHandler;
import exoticatechnologies.modifications.upgrades.Upgrade;
import exoticatechnologies.modifications.upgrades.UpgradesHandler;
import exoticatechnologies.util.ExtensionsKt;
import exoticatechnologies.util.FleetMemberUtils;
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
    private static final Color hullmodColor = new Color(94, 206, 226);
    private static final Logger log = Logger.getLogger(ExoticaTechHM.class);

    public static void addToFleetMember(FleetMemberAPI member, ShipVariantAPI variant) {
        if (variant == null) {
            return;
        }

        ShipModifications mods = ShipModFactory.generateForFleetMember(member);

        if (variant.hasHullMod("exoticatech")) {
            variant.removePermaMod("exoticatech");
        }

        if (mods.shouldApplyHullmod()) {

            ExtensionsKt.fixVariant(member);
            variant.addPermaMod("exoticatech");

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
        if (shipVariant.hasHullMod("exoticatech")) {
            shipVariant.removePermaMod("exoticatech");
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
            member.getVariant().removePermaMod("exoticatech");
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
     * @return whether it should be skipped or not, dependant on {@link Modification#shouldAffectModule(ShipAPI, ShipAPI)} and {@link Modification#shouldShareEffectToOtherModules(ShipAPI, ShipAPI)}
     */
    public boolean shouldSkipModification(ShipAPI ship, Modification mod) {
        boolean modAppliesToModules = mod.shouldAffectModule(ship.getParentStation(), ship);
        boolean modSharesEffectsWithAllModules = mod.shouldShareEffectToOtherModules(ship.getParentStation(), ship);

        if (cachedCheckIsModule(ship)) {
            // if doesn't apply to modules - skip
            if (!modAppliesToModules) {
                return true;
            } else {
                // If applies to modules - check if effects are shared, if not - skip
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
     */
    public boolean shouldSkipModification(MutableShipStatsAPI stats, Modification mod) {
        boolean fleetMemberNonNull = stats.getFleetMember() != null;
        // lets just default to 'false' if fleetmember is null - it won't go into the if() anyways
        // since the first condition is for the fleetmember to be non-null
        boolean fleetMemberShipNameIsNull = (stats.getFleetMember() != null) ? stats.getFleetMember().getShipName() == null : false;
        boolean modAppliesToModules = mod.shouldAffectModule(stats);
        boolean modSharesEffectsWithAllModules = mod.shouldShareEffectToOtherModules(null, null);

        if (fleetMemberNonNull && fleetMemberShipNameIsNull) {
            // if doesn't apply to modules - skip
            if (!modAppliesToModules) {
                return true;
            } else {
                // If applies to modules - check if effects are shared, if not - skip
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

        for (Exotic exotic : ExoticsHandler.INSTANCE.getEXOTIC_LIST()) {
            if (!mods.hasExotic(exotic)) continue;
            if (shouldSkipModification(ship, exotic)) continue;

            exotic.advanceInCombatUnpaused(ship, amount, member, mods, Objects.requireNonNull(mods.getExoticData(exotic)));
        }

        for (Upgrade upgrade : UpgradesHandler.UPGRADES_LIST) {
            if (!mods.hasUpgrade(upgrade)) continue;
            if (shouldSkipModification(ship, upgrade)) continue;

            upgrade.advanceInCombatUnpaused(ship, amount, member, mods);
        }
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        log.debug("--> applyEffectsBeforeShipCreation(hullSize=" + hullSize + ", stats=" + stats + ", id=" + id + ")");
        FleetMemberAPI member = FleetMemberUtils.findMemberForStats(stats);
        log.debug("applyEffectsBeforeShipCreation()\tmember = " + member);
        if (member == null) {
//            return;
            log.debug("applyEffectsBeforeShipCreation()\tmember == null, not returning just to see what happens... ");
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
            log.info("Failed to get modules", e);
        }

        ShipModifications mods = ShipModLoader.get(member, stats.getVariant());

        if (mods == null) {
            member.getVariant().removePermaMod("exoticatech");
            return;
        }

        for (Exotic exotic : ExoticsHandler.INSTANCE.getEXOTIC_LIST()) {
            if (!mods.hasExotic(exotic)) continue;
            log.debug("applyEffectsBeforeShipCreation()\tshouldSkipModification(stats="+stats+", exotic="+exotic+") = " + shouldSkipModification(stats, exotic));
            if (shouldSkipModification(stats, exotic)) continue;

            log.debug("applyEffectsBeforeShipCreation()\t--> exotic.applyExoticToStats()\texotic: "+exotic+", id="+id+", stats="+stats+", member="+member+", mods="+mods+", exoticData="+mods.getExoticData(exotic));

            exotic.applyExoticToStats(id, stats, member, mods, Objects.requireNonNull(mods.getExoticData(exotic)));
        }

        for (Upgrade upgrade : UpgradesHandler.UPGRADES_LIST) {
            if (!mods.hasUpgrade(upgrade)) continue;
            if (shouldSkipModification(stats, upgrade)) continue;

            upgrade.applyUpgradeToStats(stats, member, mods, mods.getUpgrade(upgrade));
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        FleetMemberAPI member = FleetMemberUtils.findMemberFromShip(ship);
        if (member == null) return;

        ShipModifications mods = ShipModLoader.get(member, ship.getVariant());
        if (mods == null) return;

        for (Exotic exotic : ExoticsHandler.INSTANCE.getEXOTIC_LIST()) {
            if (!mods.hasExotic(exotic)) continue;
            if (shouldSkipModification(ship, exotic)) continue;
            exotic.applyToShip(id, member, ship, mods, Objects.requireNonNull(mods.getExoticData(exotic)));
        }

        for (Upgrade upgrade : UpgradesHandler.UPGRADES_LIST) {
            if (!mods.hasUpgrade(upgrade)) continue;
            if (shouldSkipModification(ship, upgrade)) continue;
            upgrade.applyToShip(member, ship, mods);
        }
    }

    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {
        FleetMemberAPI member = FleetMemberUtils.findMemberFromShip(ship);
        if (member == null) return;

        ShipModifications mods = ShipModLoader.get(member, ship.getVariant());
        if (mods == null) return;

        for (Exotic exotic : ExoticsHandler.INSTANCE.getEXOTIC_LIST()) {
            if (!mods.hasExotic(exotic)) continue;
            if (shouldSkipModification(ship, exotic)) continue;
            exotic.applyToFighters(member, ship, fighter, mods);
        }
        for (Upgrade upgrade : UpgradesHandler.UPGRADES_LIST) {
            if (!mods.hasUpgrade(upgrade)) continue;
            if (shouldSkipModification(ship, upgrade)) continue;
            upgrade.applyToFighters(member, ship, fighter, mods);
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

    public static void removeHullModFromVariant(ShipVariantAPI v) {
        v.removePermaMod("exoticatech");
        v.removeMod("exoticatech");
        v.removeSuppressedMod("exoticatech");
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