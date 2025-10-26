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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BandwidthHandler {
    private static final int UPGRADE_OPTION_ORDER = 0;

    public static synchronized boolean canUpgrade(ShipModifications buff, FleetMemberAPI selectedShip) {
        return buff == null || buff.canUpgradeBandwidth(selectedShip);
    }

    /**
     * Utility method that just checks whether a fleet can afford a certain cost
     *
     * @param fleet the {@link CampaignFleetAPI} fleet to check
     * @param cost the cost that has to be covered
     * @return whether the fleet has enough credits in cargo to cover the cost or not
     */
    public static synchronized boolean isAbleToPayForBandwidthUpgrade(CampaignFleetAPI fleet, float cost) {
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
    public static synchronized boolean isAbleToPayForNextBandwidthUpgrade(FleetMemberAPI member, ShipModifications mods, MarketAPI market) {
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
//    public static synchronized boolean performNextBandwidthUpgrade(FleetMemberAPI member, ShipModifications mods, MarketAPI market, ShipVariantAPI variant, boolean forceUpgrade) {
    public static synchronized BandwidthUpgradeResult performNextBandwidthUpgrade(FleetMemberAPI member, ShipModifications mods, MarketAPI market, ShipVariantAPI variant, boolean forceUpgrade) {
        BandwidthUpgradeResult retVal;

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

//            retVal = true;
            retVal = new BandwidthUpgradeResult(true, upgradePrice);
        } else {
            // If we can't afford, do nothing and return false
            retVal = new BandwidthUpgradeResult(false, -1);
        }

        return retVal;
    }

//    public static float getCostPrognosisToMaxBandwidth(FleetMemberAPI member, ShipModifications mods, MarketAPI market) {
    public static synchronized float getCostPrognosisToMaxBandwidth(FleetMemberAPI member, MarketAPI market) {
        //TODO the mods aren't necessary, use ShipModLoader to get them from the FMAPI
        // the ship i'm testing with should return 14,221,032
        // the projected cost returned 14,221,023

        // The idea is - we are going to grab the bandwidth from the ship,
        // then we're going to run an accumulator on the price and fake upgrading it and store that in the bandwidth accumulator
        // once it reaches max, the returned value should be the one we're expecting with the test ship
        ShipModifications mods = ShipModLoader.get(member, member.getVariant());
        float bandwidthAccumulator = mods.getBaseBandwidth();
        float priceAccumulator = 0;
        List<Float> priceList = new ArrayList<>();

        // Due to the fact that the actual upgrading actually does min(currentBandwidth + increase, MAX_BANDWIDTH)
        // we will not care if it goes over the limit
        while (bandwidthAccumulator < Bandwidth.MAX_BANDWIDTH) {
            // Now just fake upgrading until we hit max
            float marketMult = BandwidthHandler.getMarketBandwidthMult(market);
            float increase = Bandwidth.BANDWIDTH_STEP * marketMult;
            float upgradePrice = BandwidthHandler.getBandwidthUpgradePrice(member, bandwidthAccumulator, marketMult);

            bandwidthAccumulator = bandwidthAccumulator + increase;
            priceAccumulator = priceAccumulator + upgradePrice;
            priceList.add(upgradePrice);
        }

        // Finally, return the accumulated price
        return priceAccumulator;
    }

    public static synchronized float getMarketBandwidthMult(MarketAPI currMarket) {
        Map<String, Float> marketBonuses = MagicSettings.getFloatMap("exoticatechnologies", "industryBandwidthPurchaseBonuses");

        float bandwidthMult = 1;
        for (Industry industry : currMarket.getIndustries()) {
            if (marketBonuses.containsKey(industry.getId())) {
                bandwidthMult += marketBonuses.get(industry.getId());
            }
        }
        return bandwidthMult;
    }

    public static synchronized float getBandwidthUpgradePrice(FleetMemberAPI selectedShip, float shipBandwidth, float upgradeBandwidthMult) {
        float retVal;
        float deployCost = selectedShip.getBaseDeployCost();
        float shipBaseValue = selectedShip.getBaseValue();
        if (shipBaseValue > 450000) {
            shipBaseValue = 225000;
        } else {
            shipBaseValue = (float) (shipBaseValue - (1d / 900000d) * Math.pow(shipBaseValue, 2));
        }
        float bandwidthMultFactor = 1 - (upgradeBandwidthMult / (upgradeBandwidthMult + 10));

        retVal = Math.round(shipBaseValue * (float) Math.pow(shipBandwidth / 70f, 2) / (2f + 6f * bandwidthMultFactor) * 100f) / 100f;

        return retVal;
    }

    public static class BandwidthUpgradeResult {
        private boolean success;
        private float upgradeCost;

        public BandwidthUpgradeResult(boolean isSuccess, float cost) {
            this.success = isSuccess;
            this.upgradeCost = cost;
        }

        public boolean isSuccess() { return success; }
        public float getUpgradeCost() { return upgradeCost; }
    }
}
