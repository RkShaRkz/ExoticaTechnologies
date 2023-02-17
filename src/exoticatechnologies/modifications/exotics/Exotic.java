package exoticatechnologies.modifications.exotics;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import exoticatechnologies.modifications.Modification;
import exoticatechnologies.modifications.ShipModifications;
import exoticatechnologies.util.StringUtils;
import lombok.Getter;
import lombok.Setter;
import org.json.JSONObject;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public abstract class Exotic extends Modification {
    public static final String ITEM = "et_exotic";
    @Getter
    @Setter
    protected String loreDescription;

    public Exotic(String key, JSONObject settings) {
        super(key, settings);
    }

    public static Exotic get(String exoticKey) {
        return ExoticsHandler.EXOTICS.get(exoticKey);
    }

    public abstract Color getColor();

    public String getTextDescription() {
        return getLoreDescription() + "\n\n" + getDescription();
    }

    public String getIcon() {
        return "graphics/icons/exotics/" + getKey().toLowerCase() + ".png";
    }

    public String getBuffId() {
        return "ET_" + getKey();
    }

    protected boolean isNPC(FleetMemberAPI fm) {
        return fm.getFleetData() == null
                || fm.getFleetData().getFleet() == null
                || !fm.getFleetData().getFleet().equals(Global.getSector().getPlayerFleet());
    }

    public void onInstall(FleetMemberAPI fm) {

    }

    public void onDestroy(FleetMemberAPI fm) {

    }

    public boolean canAfford(CampaignFleetAPI fleet, MarketAPI market) {
        return false;
    }

    public boolean removeItemsFromFleet(CampaignFleetAPI fleet, FleetMemberAPI fm, MarketAPI market) {
        return false;
    }

    public void printDescriptionToTooltip(FleetMemberAPI fm, TooltipMakerAPI tooltip) {
        StringUtils.getTranslation(this.getKey(), "description")
                .addToTooltip(tooltip);
    }

    public void printStatInfoToTooltip(FleetMemberAPI fm, TooltipMakerAPI tooltip) {
        StringUtils.getTranslation(this.getKey(), "longDescription")
                .addToTooltip(tooltip);
    }

    public void printDescriptionToTooltip(TooltipMakerAPI tooltip, FleetMemberAPI member) {
        StringUtils.getTranslation(this.getKey(), "description")
                .addToTooltip(tooltip);
    }

    public void printStatInfoToTooltip(TooltipMakerAPI tooltip, FleetMemberAPI member) {
        StringUtils.getTranslation(this.getKey(), "longDescription")
                .addToTooltip(tooltip);
    }

    public Map<String, Float> getResourceCostMap(FleetMemberAPI fm, ShipModifications mods, MarketAPI market) {
        return new HashMap<>();
    }


    /**
     * extra bandwidth allowed to be installed.
     *
     * @param fm
     * @param mods
     * @param data
     * @return
     */
    public float getExtraBandwidthPurchaseable(FleetMemberAPI fm, ShipModifications mods, ExoticData data) {
        return 0f;
    }

    /**
     * extra bandwidth added directly to ship.
     *
     * @param fm
     * @param mods
     * @param data
     * @return
     */
    public float getExtraBandwidth(FleetMemberAPI fm, ShipModifications mods, ExoticData data) {
        return 0f;
    }

    public void applyExoticToStats(String id, FleetMemberAPI fm, MutableShipStatsAPI stats, ExoticData data) {

    }

    public void applyExoticToShip(String id, FleetMemberAPI fm, ShipAPI ship, ExoticData data) {

    }

    public void advanceInCombatUnpaused(ShipAPI ship, float amount, ExoticData data) {

    }

    public void advanceInCombatAlways(ShipAPI ship, ExoticData data) {

    }

    public void advanceInCampaign(FleetMemberAPI member, ShipModifications mods, Float amount, ExoticData data) {

    }

    public boolean canDropFromFleets() {
        return true;
    }

    public float getSalvageChance(float chanceMult) {
        return 0.2f * chanceMult;
    }

    public SpecialItemData getNewSpecialItemData(ExoticType exoticType) {
        return new SpecialItemData(Exotic.ITEM, String.format("%s,%s", this.getKey(), exoticType.name()));
    }

    public SpecialItemData getNewSpecialItemData() {
        return new SpecialItemData(Exotic.ITEM, this.getKey());
    }

    public abstract void modifyToolTip(TooltipMakerAPI tooltip, UIComponentAPI title, FleetMemberAPI fm, ShipModifications mods, boolean expand);
}
