package exoticatechnologies.modifications;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import exoticatechnologies.ETModSettings;
import exoticatechnologies.ETModPlugin;
import exoticatechnologies.modifications.exotics.ETExotics;
import exoticatechnologies.modifications.exotics.Exotic;
import exoticatechnologies.modifications.exotics.ExoticsGenerator;
import exoticatechnologies.modifications.exotics.ExoticsHandler;
import exoticatechnologies.modifications.bandwidth.Bandwidth;
import exoticatechnologies.modifications.upgrades.ETUpgrades;
import exoticatechnologies.modifications.upgrades.Upgrade;
import exoticatechnologies.modifications.upgrades.UpgradesGenerator;
import exoticatechnologies.modifications.upgrades.UpgradesHandler;
import lombok.extern.log4j.Log4j;

import java.util.Random;

@Log4j
public class ShipModifications {
    private static Random random = new Random();
    //per fleet member!
    private static float CHANCE_OF_UPGRADES = 0.4f;

    ShipModifications(long bandwidthSeed) {
        if (upgrades == null) {
            this.upgrades = new ETUpgrades();
        }

        if (exotics == null) {
            this.exotics = new ETExotics();
        }

        if (bandwidth == -1) {
            this.bandwidth = Bandwidth.generate(bandwidthSeed).getRandomInRange();
        }
    }

    ShipModifications(FleetMemberAPI fm) {
        if (upgrades == null) {
            this.upgrades = new ETUpgrades();
        }

        if (exotics == null) {
            this.exotics = new ETExotics();
        }

        if (bandwidth == -1) {
            this.bandwidth = ShipModFactory.generateBandwidth(fm);
        }
    }

    public boolean shouldApplyHullmod() {
        return this.upgrades.hasUpgrades()
                || this.exotics.hasAnyExotic();
    }

    public void save(FleetMemberAPI fm) {
        ETModPlugin.saveData(fm.getId(), this);

        if (fm.getHullId().contains("ziggurat")) {
            if (ETModPlugin.getZigguratDuplicateId() == null) {
                ETModPlugin.setZigguratDuplicateId(fm.getId());
            } else {
                ETModPlugin.saveData(ETModPlugin.getZigguratDuplicateId(), this);
            }
        }
    }

    /**
     * for ships without a fleet member.
     * @param seed
     * @param var
     * @param faction
     */
    public void generate(long seed, ShipVariantAPI var, String faction) {
        this.exotics = ExoticsGenerator.generate(var, seed, faction, this.getBandwidth());
        this.upgrades = UpgradesGenerator.generate(var, seed, faction, this.getBandwidth());
    }

    /**
     * for a fleet member
     * @param seed
     * @param faction
     */
    public void generate(FleetMemberAPI fm, long seed, String faction) {
        this.exotics = ExoticsGenerator.generate(fm, seed, faction, this.getBandwidth(fm));
        this.upgrades = UpgradesGenerator.generate(fm, seed, faction, this.getBandwidthWithExotics(fm));
    }

    //bandwidth
    private float bandwidth = -1f;

    public void putBandwidth(float bandwidth) {
        this.bandwidth = bandwidth;
    }

    /**
     * Use this only if bandwidth has already been generated. The Exotica dialog WILL generate bandwidth.
     * @return
     */
    public float getBandwidth() {
        return Math.max(this.bandwidth, 0f);
    }

    /**
     * Will generate bandwidth if not already generated.
     * @param fm
     * @return
     */
    public float getBandwidth(FleetMemberAPI fm) {
        if(bandwidth < 0f) {
            log.info(String.format("Bandwidth was below zero for fm [%s]", fm.getId()));
            bandwidth = ShipModFactory.generateBandwidth(fm);
        }

        return Math.max(this.bandwidth, 0f);
    }

    public float getBandwidthWithExotics(FleetMemberAPI fm) {
        if(bandwidth < 0f) {
            log.info(String.format("Bandwidth with exotics was below zero for fm [%s]", fm.getId()));
            bandwidth = ShipModFactory.generateBandwidth(fm);
        }

        float returnedBandwidth = bandwidth;

        for(Exotic exotic : ExoticsHandler.EXOTIC_LIST) {
            if(this.hasExotic(exotic)) {
                returnedBandwidth += exotic.getExtraBandwidth(fm, this);
            }
        }

        return returnedBandwidth;
    }

    public boolean canUpgradeBandwidth(FleetMemberAPI fm) {
        float maxBandwidth = ETModSettings.getFloat(ETModSettings.MAX_BANDWIDTH);
        for(Exotic exotic : ExoticsHandler.EXOTIC_LIST) {
            if(this.hasExotic(exotic)) {
                maxBandwidth += exotic.getExtraBandwidthPurchaseable(fm, this);
            }
        }
        return maxBandwidth > getBandwidth(fm);
    }

    public float getUsedBandwidth() {
        float usedBandwidth = 0f;
        for(Upgrade upgrade : UpgradesHandler.UPGRADES_LIST) {
            usedBandwidth += upgrade.getBandwidthUsage() * this.getUpgrade(upgrade);
        }

        return usedBandwidth;
    }

    //exotics
    protected ETExotics exotics = null;

    protected ETExotics getExotics() {
        return exotics;
    }

    public boolean hasExotic(String key) {
        return exotics.hasExotic(key);
    }

    public boolean hasExotic(Exotic exotic) {
        return hasExotic(exotic.getKey());
    }

    public boolean hasExotics() {
        return exotics.hasAnyExotic();
    }

    public void putExotic(Exotic exotic) {
        exotics.putExotic(exotic);
    }

    public void removeExotic(Exotic exotic) {
        exotics.removeExotic(exotic);
    }

    //upgrades
    private ETUpgrades upgrades = null;
    protected ETUpgrades getUpgrades() {
        return upgrades;
    }

    public void putUpgrade(Upgrade upgrade) {
        upgrades.putUpgrade(upgrade);
    }

    public void putUpgrade(Upgrade upgrade, int level) {
        upgrades.putUpgrade(upgrade, level);
    }

    public int getUpgrade(String key) {
        return upgrades.getUpgrade(key);
    }

    public int getUpgrade(Upgrade upgrade) {
        return getUpgrade(upgrade.getKey());
    }

    public void removeUpgrade(Upgrade upgrade) {
        upgrades.removeUpgrade(upgrade);
    }

    public boolean hasUpgrade(Upgrade upgrade){
        return getUpgrade(upgrade) > 0;
    }

    public boolean hasUpgrades() {
        return this.upgrades.hasUpgrades();
    }

    public float getHullSizeFactor(ShipAPI.HullSize hullSize) {
        return this.upgrades.getHullSizeFactor(hullSize);
    }

    public boolean isMaxLevel(FleetMemberAPI shipSelected, Upgrade upgrade) {
        return this.getUpgrade(upgrade) >= upgrade.getMaxLevel(shipSelected.getHullSpec().getHullSize());
    }

    @Override
    public String toString() {
        return "ShipModifications{" +
                "bandwidth=" + bandwidth +
                ", modules=" + exotics +
                ", upgrades=" + upgrades +
                '}';
    }
}
