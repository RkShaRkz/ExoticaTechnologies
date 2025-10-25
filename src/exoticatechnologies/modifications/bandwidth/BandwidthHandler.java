package exoticatechnologies.modifications.bandwidth;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import exoticatechnologies.ETModPlugin;
import exoticatechnologies.modifications.ShipModLoader;
import exoticatechnologies.modifications.ShipModifications;
import org.magiclib.util.MagicSettings;

import java.util.Map;

public class BandwidthHandler {
    private static final int UPGRADE_OPTION_ORDER = 0;

    public static boolean canUpgrade(ShipModifications buff, FleetMemberAPI selectedShip) {
        return buff == null || buff.canUpgradeBandwidth(selectedShip);
    }

    /**
     * Utility method that just checks whether a fleet can afford a certain cost
     *
     * @param fleet the {@link CampaignFleetAPI} fleet to check
     * @param cost the cost that has to be covered
     * @return whether the fleet has enough credits in cargo to cover the cost or not
     */
    public static boolean isAbleToPayForBandwidthUpgrade(CampaignFleetAPI fleet, float cost) {
        return cost <= fleet.getCargo().getCredits().get() || ETModPlugin.isDebugUpgradeCosts();
    }

    /**
     * Utility method that checks whether the member can afford the cost of next bandwidth upgrade.
     *
     * @param member the {@link FleetMemberAPI} member to check
     * @param mods the {@link ShipModifications}'s current bandwidth to use to calculate the next upgrade's price. See {@link ShipModifications#getBaseBandwidth()}
     * @param market the {@link MarketAPI} hosting the upgrading. See {@link #getMarketBandwidthMult(MarketAPI)}
     * @return whether the member's fleet has enough money to afford the next bandwidth upgrade
     */
    public static boolean isAbleToPayForNextBandwidthUpgrade(FleetMemberAPI member, ShipModifications mods, MarketAPI market) {
        float marketMult = BandwidthHandler.getMarketBandwidthMult(market);
        float upgradePrice = BandwidthHandler.getBandwidthUpgradePrice(member, mods.getBaseBandwidth(), marketMult);

        return isAbleToPayForBandwidthUpgrade(member.getFleetData().getFleet(), upgradePrice);
    }

    /**
     * Utility / shortcut method that performs the next bandwidth upgrade on member's mods, hosted at market.
     * Forced upgrades forgo whether the fleet can afford it and just deduce the cost, nonforced upgrades will first check
     * whether the member's fleet has enough credits in the cargo before performing the upgrade and deducing money.
     *
     * @param member the {@link FleetMemberAPI} member whose bandwidth we want to upgrade
     * @param mods the {@link ShipModifications} wrapping the bandwidth we want to upgrade
     * @param market the {@link MarketAPI} market hosting the upgrading process. See {@link #getMarketBandwidthMult(MarketAPI)}
     * @param variant the member's {@link ShipVariantAPI} variant
     * @param forceUpgrade whether we should force the upgrade regardless of whether the fleet can afford it or not. See {@link #isAbleToPayForBandwidthUpgrade(CampaignFleetAPI, float)}
     * @return whether the upgrade was performed successfully or not
     */
    public static boolean performNextBandwidthUpgrade(FleetMemberAPI member, ShipModifications mods, MarketAPI market, ShipVariantAPI variant, boolean forceUpgrade) {
        boolean retVal;

        float marketMult = BandwidthHandler.getMarketBandwidthMult(market);
        float increase = Bandwidth.BANDWIDTH_STEP * marketMult;
        float upgradePrice = BandwidthHandler.getBandwidthUpgradePrice(member, mods.getBaseBandwidth(), marketMult);

        CampaignFleetAPI membersFleet = member.getFleetData().getFleet();
        if (isAbleToPayForBandwidthUpgrade(membersFleet, upgradePrice) || forceUpgrade) {
            // If we can afford, deduce the money and perform the upgrade
            membersFleet.getCargo().getCredits().subtract(upgradePrice);

            float newBandwidth = Math.min(mods.getBaseBandwidth() + increase, Bandwidth.MAX_BANDWIDTH);
            mods.setBandwidth(newBandwidth);
            ShipModLoader.set(member, variant, mods);

            retVal = true;
        } else {
            // If we can't afford, do nothing and return false
            retVal = false;
        }

        return retVal;
    }

    public static float getMarketBandwidthMult(MarketAPI currMarket) {
        Map<String, Float> marketBonuses = MagicSettings.getFloatMap("exoticatechnologies", "industryBandwidthPurchaseBonuses");

        float bandwidthMult = 1;
        for (Industry industry : currMarket.getIndustries()) {
            if (marketBonuses.containsKey(industry.getId())) {
                bandwidthMult += marketBonuses.get(industry.getId());
            }
        }
        return bandwidthMult;
    }

    public static float getBandwidthUpgradePrice(FleetMemberAPI selectedShip, float shipBandwidth, float upgradeBandwidthMult) {
        float deployCost = selectedShip.getBaseDeployCost();
        float shipBaseValue = selectedShip.getBaseValue();
        if (shipBaseValue > 450000) {
            shipBaseValue = 225000;
        } else {
            shipBaseValue = (float) (shipBaseValue - (1d / 900000d) * Math.pow(shipBaseValue, 2));
        }
        float bandwidthMultFactor = 1 - (upgradeBandwidthMult / (upgradeBandwidthMult + 10));

        return Math.round(shipBaseValue * (float) Math.pow(shipBandwidth / 70f, 2) / (2f + 6f * bandwidthMultFactor) * 100f) / 100f;
    }
}
