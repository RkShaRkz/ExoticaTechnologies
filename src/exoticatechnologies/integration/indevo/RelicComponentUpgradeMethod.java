package exoticatechnologies.integration.indevo;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import exoticatechnologies.hullmods.ExoticaTechHM;
import exoticatechnologies.modifications.upgrades.Upgrade;
import exoticatechnologies.ui.impl.shop.upgrades.methods.UpgradeMethod;
import exoticatechnologies.modifications.ShipModifications;
import exoticatechnologies.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class RelicComponentUpgradeMethod implements UpgradeMethod {
    private static final String OPTION = "ETShipExtraUpgradeApplyIndEvoRelics";

    @Override
    public String getOptionText(FleetMemberAPI fm, ShipModifications es, Upgrade upgrade, MarketAPI market) {
        return StringUtils.getTranslation("UpgradeMethods", "IndEvoRelicsOption")
                .format("relics", IndEvoUtil.getUpgradeRelicComponentPrice(fm, upgrade, es.getUpgrade(upgrade)))
                .toString();
    }

    @Override
    public String getOptionTooltip(FleetMemberAPI fm, ShipModifications es, Upgrade upgrade, MarketAPI market) {
        return StringUtils.getTranslation("UpgradeMethods", "IndEvoRelicsTooltip")
                .format("relics", getTotalComponents(fm.getFleetData().getFleet(), market))
                .toString();
    }

    @Override
    public boolean canUse(FleetMemberAPI fm, ShipModifications es, Upgrade upgrade, MarketAPI market) {
        int level = es.getUpgrade(upgrade);
        int upgradeCost = IndEvoUtil.getUpgradeRelicComponentPrice(fm, upgrade, level);
        int totalComponents = getTotalComponents(fm.getFleetData().getFleet(), market);

        return (totalComponents - upgradeCost) >= 0;
    }

    @Override
    public boolean canShow(FleetMemberAPI fm, ShipModifications es, Upgrade upgrade, MarketAPI market) {
        return true;
    }

    @Override
    public String apply(FleetMemberAPI fm, ShipModifications mods, Upgrade upgrade, MarketAPI market) {
        int level = mods.getUpgrade(upgrade);
        int upgradeCost = IndEvoUtil.getUpgradeRelicComponentPrice(fm, upgrade, level);

        if (market != null
                && market.getSubmarket(Submarkets.SUBMARKET_STORAGE) != null
                && market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo() != null) {

            CargoAPI storageCargo = market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo();

            upgradeCost = removeCommodityAndReturnRemainingCost(storageCargo, IndEvoUtil.RELIC_COMPONENT_ITEM_ID, upgradeCost);
        }

        CargoAPI fleetCargo = fm.getFleetData().getFleet().getCargo();
        if (upgradeCost > 0) {
            removeCommodity(fleetCargo, IndEvoUtil.RELIC_COMPONENT_ITEM_ID, upgradeCost);
        }

        mods.putUpgrade(upgrade);
        mods.save(fm);
        ExoticaTechHM.addToFleetMember(fm);

        Global.getSoundPlayer().playUISound("ui_char_increase_skill_new", 1f, 1f);
        return StringUtils.getTranslation("UpgradesDialog", "UpgradePerformedSuccessfully")
                .format("name", upgrade.getName())
                .format("level", mods.getUpgrade(upgrade))
                .toString();
    }

    @Override
    public Map<String, Float> getResourceCostMap(FleetMemberAPI fm, ShipModifications mods, Upgrade upgrade, MarketAPI market, boolean hovered) {
        Map<String, Float> resourceCosts = new HashMap<>();

        if (hovered) {
            float cost = IndEvoUtil.getUpgradeRelicComponentPrice(fm, upgrade, mods.getUpgrade(upgrade));
            resourceCosts.put(IndEvoUtil.RELIC_COMPONENT_ITEM_ID, cost);
        }

        return resourceCosts;
    }

    private Integer getTotalComponents(CampaignFleetAPI fleet, MarketAPI market) {
        return getComponentsFromFleetForUpgrade(fleet) + getComponentsFromStorageForUpgrade(market);
    }

    private int getComponentsFromFleetForUpgrade(CampaignFleetAPI fleet) {
        return Math.round(fleet.getCargo().getCommodityQuantity(IndEvoUtil.RELIC_COMPONENT_ITEM_ID));
    }

    private int getComponentsFromStorageForUpgrade(MarketAPI market) {
        int result = 0;

        boolean hasStorage = false;
        if (market != null
                && market.getSubmarket(Submarkets.SUBMARKET_STORAGE) != null
                && market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo() != null) {
            result = Math.round(market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().getCommodityQuantity(IndEvoUtil.RELIC_COMPONENT_ITEM_ID));
        }

        return result;
    }

    private void removeCommodity(CargoAPI cargo, String id, float cost) {
        cargo.removeCommodity(id, cost);
    }

    private int removeCommodityAndReturnRemainingCost(CargoAPI cargo, String id, float cost) {
        float current = cargo.getCommodityQuantity(id);
        float taken = Math.min(current, cost);
        cargo.removeCommodity(id, taken);
        return (int) (cost - taken);
    }

    @Override
    public boolean usesBandwidth() {
        return true;
    }

    @Override
    public boolean usesLevel() {
        return true;
    }
}