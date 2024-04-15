package sharktest;


import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

class MergeJsons {

    public static String mergeJson(String json1, String json2) throws JSONException {
        // Parse JSON strings into JSON objects
        JSONObject jsonObject1 = new JSONObject(json1);
        JSONObject jsonObject2 = new JSONObject(json2);

        // Merge JSON objects
        JSONObject mergedObject = new JSONObject();

        // Add key-value pairs from the first JSON object
//        for (String key : jsonObject1.keySet()) {
        for (Iterator<String> it = jsonObject1.keys(); it.hasNext(); ) {
            String key = it.next();
            mergedObject.put(key, jsonObject1.get(key));
        }

        // Add key-value pairs from the second JSON object
//        for (String key : jsonObject2.keySet()) {
        for (Iterator<String> it = jsonObject2.keys(); it.hasNext(); ) {
            String key = it.next();
            mergedObject.put(key, jsonObject2.get(key));
        }

        // Convert merged JSON object to string
        return mergedObject.toString();
    }

    public static JSONObject diffJson(String json1, String json2) throws JSONException {
        // Parse JSON strings into JSON objects
        JSONObject jsonObject1 = new JSONObject(json1);
        JSONObject jsonObject2 = new JSONObject(json2);

        // Diff jsons
        JSONObject diffJson = new JSONObject();

        // Put only keys from json2 that aren't in json1
        for (Iterator<String> it = jsonObject2.keys(); it.hasNext(); ) {
            String key = it.next();
            if (jsonObject1.has(key)) {
                // Both jsons have the key, check contents of the key
                Object value1 = jsonObject1.get(key);
                Object value2 = jsonObject2.get(key);
                String key1 = value1.toString().replaceAll("\\s", "");
                String key2 = value2.toString().replaceAll("\\s", "");

                // If keys don't match, add the whole key to diff
                if (!key1.equalsIgnoreCase(key2)) {
                    // if 'value' is actually a JSONObject as well, return only it's diff
                    if (value1 instanceof JSONObject) {
                        JSONObject innerDiff = diffJson(key1, key2);
                        diffJson.put(key, innerDiff);
                    } else {
                        // otherwise, put the whole thing
                        diffJson.put(key, jsonObject2.get(key));
                    }
                }
            } else {
                // If json doesn't have the key, put the whole key
                diffJson.put(key, jsonObject2.get(key));
            }
        }

        return diffJson;
    }

    public static void main(String[] args) throws java.lang.Exception {
        // your code goes here
        String json1 = getJson1();
        String json2 = getJson2();

        String mergedJson = mergeJson(json1, json2);
        System.out.println("MERGED: "+mergedJson);
        String diffJson = diffJson(json1, json2).toString();
        System.out.println("DIFF: "+diffJson);
    }

    private static String getJson1() {
        return "{\n" +
                "\t\"Options\":{\n" +
                "\t\t\"leave\":\"Leave\",\n" +
                "\t\t\"back\":\"Back\",\n" +
                "\t\t\"confirm\":\"Confirm\",\n" +
                "\t\t\"cancel\":\"Cancel\",\n" +
                "\t\t\"previouspage\":\"Previous Page\",\n" +
                "\t\t\"nextpage\":\"Next Page\",\n" +
                "\t\t\"repurchase\": \"Purchase the upgrade again\"\n" +
                "\t},\n" +
                "\t\"BandwidthName\":{\n" +
                "\t\t\"terrible\":\"(terrible)\",\n" +
                "\t\t\"crude\":\"(crude)\",\n" +
                "\t\t\"poor\":\"(poor)\",\n" +
                "\t\t\"normal\":\"(normal)\",\n" +
                "\t\t\"good\":\"(good)\",\n" +
                "\t\t\"superior\":\"(superior)\",\n" +
                "\t\t\"pristine\":\"(pristine)\",\n" +
                "\t\t\"ultimate\":\"(ultimate)\",\n" +
                "\t\t\"perfect\":\"(perfect)\",\n" +
                "\t\t\"unknown\":\"(???)\"\n" +
                "\t},\n" +
                "\t\"CommonOptions\": {\n" +
                "\t\t\"ExpandExotics\": \"Press &F1& to show more exotic information.\",\n" +
                "\t\t\"ExpandUpgrades\": \"Press *F2* to show more upgrade information.\",\n" +
                "\t\t\"UnexpandInfo\": \"Press *any key* to show less information.\",\n" +
                "\t\t\"PreviousPage\": \"Previous page\",\n" +
                "\t\t\"NextPage\": \"Next page\",\n" +
                "\t\t\"ResourcesHeader\": \"Resources\",\n" +
                "\t\t\"CreditsText\": \"*${credits}*\",\n" +
                "\t\t\"CreditsTextWithCost\": \"*${credits}* (=${cost}=)\",\n" +
                "\t\t\"CreditsCost\": \"=${credits}= credits\",\n" +
                "\t\t\"CreditsPay\": \"*${credits}* credits\",\n" +
                "\t\t\"ResourceText\": \"${name}: *${amount}*\",\n" +
                "\t\t\"ResourceTextWithCost\": \"${name}: *${amount}* (=${cost}=)\",\n" +
                "\t\t\"SpecialItemText\": \"&${name}&: ${amount}\",\n" +
                "\t\t\"SpecialItemTextWithCost\": \"&${name}&: ${amount} (=${cost}=)\",\n" +
                "\t\t\"SpecialItemTextWithPay\": \"&${name}&: ${amount} (*${cost}*)\",\n" +
                "\n" +
                "\t\t\"BandwidthWithExoticsForShip\": \"Ship bandwidth: *${shipBandwidth}* (*${exoticBandwidth}* from exotics)\",\n" +
                "\t\t\"BandwidthForShip\": \"Ship bandwidth: *${shipBandwidth}*\",\n" +
                "\t\t\"UsedBandwidthForShip\": \"Used bandwidth: *${usedBandwidth}* of ${allBandwidth}\",\n" +
                "\n" +
                "\t\t\"BandwidthUsed\": \"*${usedBandwidth}* used\",\n" +
                "\t\t\"BandwidthUsedByUpgrade\": \"*${upgradeBandwidth}*\",\n" +
                "\t\t\"StoryPointCost\": \"*${storyPoints} story points*\",\n" +
                "\n" +
                "\t\t\"MustBeDockedAtMarket\": \"=Must be docked at market=\",\n" +
                "\t\t\"InStockCount\": \"${count} in cargo\",\n" +
                "\t\t\"ESCToClose\": \"Press ESC to close.\"\n" +
                "\t},\n" +
                "\t\"FleetScanner\": {\n" +
                "\t\t\"ShipBandwidthShort\": \"Bandwidth: *${bandwidth}*\",\n" +
                "\t\t\"ShipHasBandwidth\": \"*${name}* has an outstanding bandwidth of *${bandwidth}*. It doesn't have any modifications that use it.\",\n" +
                "\t\t\"ShipHasUpgrades\": \"*${name}* has a usable bandwidth of *${bandwidth}*. It also has other modifications, which are listed below. You can hover over one of them to see a description of the modification.\",\n" +
                "\t\t\"UpgradeNameWithLevelAndMax\": \"*${upgradeName}* (${level}/${max})\",\n" +
                "\t\t\"DebrisFieldHasNotableMods\": \"Upon closer inspection, the ships in the debris field appear to have signs of *Exotica technologies*.\",\n" +
                "\t\t\"ExoticHeader\": \"Exotics\",\n" +
                "\t\t\"UpgradeHeader\": \"Upgrades\",\n" +
                "\t\t\"NotableShipsHeader\": \"Notable ships\",\n" +
                "\t\t\"ModsMissingText\": \"Upon a closer inspection by your intelligence officer, a small anomaly within the search results indicates to both of you now that =the ship has no Exotica technologies= installed. They aren't able to give a clear explanation why this was missed, only mentioning that a faint humming may have distracted them.\",\n" +
                "\t\t\"FleetScanOption\": \"Scan the fleet for Exotica technologies\",\n" +
                "\t\t\"DebrisFieldScanOption\": \"Scan the debris field for Exotica technologies\"\n" +
                "\t},\n" +
                "\t\"MarketMenu\": {\n" +
                "\t\t\"Title\": \"Exotica Chip Market\",\n" +
                "\t\t\"CreditsText\": \"You have *${credits}*.\",\n" +
                "\t\t\"MenuText\": \"Purchasing the selected chips will cost *${credits}*.\",\n" +
                "\t\t\"MenuTextCannotAfford\": \"Purchasing the selected chips will cost *${credits}*, which you cannot afford.\",\n" +
                "\t\t\"PurchasedText\": \"You purchased Exotica chips for *${credits}*.\"\n" +
                "\t},\n" +
                "\t\"MainMenu\": {\n" +
                "\t\t\"UpgradeShips\": \"Consider upgrading a ship.\",\n" +
                "\t\t\"ShipModMenu\": \"Modification Menu\",\n" +
                "\t\t\"BackToMainMenu\": \"Return to the main market.\"\n" +
                "\t},\n" +
                "\t\"ShipDialog\": {\n" +
                "\t\t\"BackToShip\": \"Consider other options to modify the ship.\",\n" +
                "\t\t\"OpenedDialog\": \"The on-site engineering team is ready to assist you with this ship.\"\n" +
                "\t},\n" +
                "\t\"ShipListDialog\": {\n" +
                "\t\t\"ShipListHeader\": \"Your Fleet\",\n" +
                "\t\t\"OpenModOptions\": \"Select\",\n" +
                "\t\t\"SelectPanelText\": \"Upgrades: *${numUpgrades}* | Exotics: &${numExotics}&\",\n" +
                "\t\t\"ChipName\": \"${name} Chip\",\n" +
                "\t\t\"UpgradeChipWithLevelText\": \"${upgradeName} Chip (${level})\",\n" +
                "\t\t\"ShopHeader\": \"Exotica Technologies\"\n" +
                "\t},\n" +
                "\t\"CrateText\": {\n" +
                "\t\t\"TitleText\": \"\\\"Big Create\\\" Crate\",\n" +
                "\t\t\"SideText\": \"*Available* chips in your fleet's cargo is displayed on top of the *selected* cargo to be stored in the crate.\",\n" +
                "\t\t\"SideText2\": \"Note that when moving stacks between the two locations, it is possible for the stack size to be reported as zero. *This is a purely visual error, and can be safely ignored*; your fleet's logistics personnel are experts, after all.\",\n" +
                "\t\t\"ContentsText\": \"Any Exotica Technologies branch is able to use chips directly from this crate. Opening the crate will merge the contents of all other crates into it.\\nContents:\",\n" +
                "\t\t\"UpgradeText\": \"*${upgradeItemName}* (${levelQuantities})\",\n" +
                "\t\t\"ExoticText\": \"*${exoticItemName}* (${quantity})\",\n" +
                "\t\t\"MoveOption\": \"Move\",\n" +
                "\t\t\"CancelOption\": \"Cancel\"\n" +
                "\t},\n" +
                "\t\"OverviewDialog\": {\n" +
                "\t\t\"OverviewTabText\": \"Overview\",\n" +
                "\t\t\"ExpandExotics\": \"Expand exotics\",\n" +
                "\t\t\"ExpandUpgrades\": \"Expand upgrades\",\n" +
                "\t\t\"ClearExotics\": \"Recover exotics (${storyPoints} SP)\",\n" +
                "\t\t\"ClearUpgrades\": \"Recover upgrades (${creditsText})\"\n" +
                "\t},\n" +
                "\t\"BandwidthDialog\": {\n" +
                "\t\t\"OpenBandwidthOptions\": \"Bandwidth\",\n" +
                "\t\t\"BandwidthUpgradeCost\": \"Ship bandwidth can be increased by &${bonusBandwidth}& for *${costCredits}* credits. You have *${credits}* credits.\",\n" +
                "\t\t\"BandwidthUpgradeCostCannotAfford\": \"Ship bandwidth can be increased by &${bonusBandwidth}& but you need *${costCredits}* credits. You have *${credits}* credits.\",\n" +
                "\t\t\"BandwidthUpgradePeak\": \"This ship has reached its maximum bandwidth.\",\n" +
                "\t\t\"BandwidthPurchase\": \"Purchase\",\n" +
                "\t\t\"BandwidthHelp\": \"A ship's bandwidth is its capacity for upgrades. Bandwidth can be raised for a price up to ${bandwidthLimit}, and can be raised beyond that by certain exotics.\"\n" +
                "\t},\n" +
                "\t\"ExoticsDialog\": {\n" +
                "\t\t\"OpenExoticOptions\": \"Exotics\",\n" +
                "\t\t\"ChipsHeader\": \"Chips - Hover to preview\",\n" +
                "\t\t\"Installed\": \"Installed\",\n" +
                "\t\t\"ExoticHelp\": \"Exotics have very strong effects and don't cost bandwidth, but most of them come with very strong drawbacks. They can help to completely change the way a ship operates. The chips to install them are much more rare than upgrades.\",\n" +
                "\t\t\"ExoticInstalled\": \"Exotic installed!\",\n" +
                "\t\t\"ExoticRecovered\": \"Exotic recovered!\",\n" +
                "\t\t\"ExoticDestroyed\": \"Exotic destroyed!\",\n" +
                "\t\t\"InstallExotic\": \"Install\",\n" +
                "\t\t\"InstallExoticChip\": \"Use chip\",\n" +
                "\t\t\"RecoverExotic\": \"Recover chip\",\n" +
                "\t\t\"DestroyExotic\": \"Destroy\",\n" +
                "\t\t\"InstalledTitle\": \"INSTALLED\"\n" +
                "\t},\n" +
                "\t\"ExoticCosts\": {\n" +
                "\t\t\"BandwidthGivenByExotic\": \"Max bandwidth: *${bandwidth}* (*${exoticBandwidth}*)\"\n" +
                "\t},\n" +
                "\t\"UpgradesDialog\": {\n" +
                "\t\t\"OpenUpgradeOptions\": \"Upgrades\",\n" +
                "\t\t\"UpgradeHelp\": \"Upgrades are incremental changes to the stats of a ship. They cost bandwidth, have a limited number of levels, and installing one without a chip costs credits or resources.\",\n" +
                "\t\t\"UpgradeLevel\": \"Level ${level}\",\n" +
                "\t\t\"UpgradePerformedSuccessfully\": \"Your chief engineer reports that the *${name}* upgrade was a success. It is now *level ${level}*.\",\n" +
                "\t\t\"UpgradeRecoveredSuccessfully\": \"Your chief engineer reports that your plan to remove the modification from the ship worked. They say this with the slightest disbelief, as if surprised that the plan worked at all.\",\n" +
                "\t\t\"ModDescriptionButtonText\": \"Description\",\n" +
                "\t\t\"ModStatsButtonText\": \"Stats\",\n" +
                "\t\t\"ResourceText\": \"${name}: *${amount}*\",\n" +
                "\t\t\"ResourceTextWithCost\": \"${name}: *${amount}* (=${cost}=)\",\n" +
                "\t\t\"ResourceTextWithPay\": \"${name}: *${amount}* (^${cost}^)\",\n" +
                "\t\t\"UpgradeChipsHeader\": \"Chips\",\n" +
                "\t\t\"UpgradeDrawbackAfterLevel\": \"Starting at =level ${level}=:\",\n" +
                "\t\t\"MaxLevelTitle\": \"MAXIMUM LEVEL\"\n" +
                "\t},\n" +
                "\t\"UpgradeCosts\": {\n" +
                "\t\t\"UpgradeCostTitle\": \"Price\",\n" +
                "\t\t\"BandwidthUsedWithUpgrade\": \"*${usedBandwidth}* used (=${upgradeBandwidth}=)\",\n" +
                "\t\t\"BandwidthUsed\": \"*${usedBandwidth}* used\"\n" +
                "\t},\n" +
                "\t\"UpgradeMethods\": {\n" +
                "\t\t\"UpgradeMethodsTitle\": \"Installation\",\n" +
                "\t\t\"ResourcesOption\": \"Resources\",\n" +
                "\t\t\"CreditsOption\": \"Credits\",\n" +
                "\t\t\"CreditsUpgradeTooltip\": \"The credit cost of an upgrade increases exponentially as level goes up. It can be reduced somewhat through faction relations.\",\n" +
                "\t\t\"IndEvoComponentsOption\": \"Ship Components\",\n" +
                "\t\t\"IndEvoComponentsTooltip\": \"Upgrades using ship components are 12.5% more efficient than using other resources.\\nYou have ${components} ship components.\",\n" +
                "\t\t\"IndEvoRelicsOption\": \"Relic Components.\",\n" +
                "\t\t\"IndEvoRelicsTooltip\": \"Upgrades using relic components are 37.5% more efficient than using other resources.\\nYou have ${relics} relic components.\",\n" +
                "\t\t\"RecoverOption\": \"Recover\",\n" +
                "\t\t\"RecoverOptionTooltip\": \"This option recovers a schematic of the upgrade that can be used to apply it to another ship. Installing the chip will incur another cost, and the total cost of recovering and using any chip is always less than any other method.\",\n" +
                "\t\t\"ChipOption\": \"Upgrade Chip\",\n" +
                "\t\t\"ChipOptionTooltip\": \"This option uses an existing ship modification chip to upgrade the ship. It will prioritize using the one with the highest level. The total cost of recovering and using any chip is always less than any other method.\"\n" +
                "\t},\n" +
                "\t\"SpooledFeeders\": {\n" +
                "\t\t\"description\": \"Although unable to be used for the same purpose as a full-size Fullerene spool, this much-smaller chain can be used instead to replace many of the moving mechanical parts within a ship. The chain notably increases the rate of fire of weapons upon being disturbed, generating some kind of intense field that crew members can only describe as \\\"bloodthirsty\\\". The chain slows down considerably after a couple seconds, with an effect on the ship that matches its slower speed.\",\n" +
                "\t\t\"tooltip\": \"Adds a new active ability that increases weapon firerate substantially. Firerate slows considerably for a time after.\",\n" +
                "\t\t\"longDescription\": \"Adds an active ability that increases weapon fire rate by *${firerateBoost}%%* for *${boostTime} seconds*. After this time, weapon fire rate is reduced by =${firerateMalus}%%= for *${malusTime} seconds*. The ability takes *${cooldownTime} seconds* to recharge.\",\n" +
                "\t\t\"needItem\": \"You need a ${itemName} to install this.\",\n" +
                "\t\t\"systemText\": \"SPOOLED FEEDERS\"\n" +
                "\t},\n" +
                "\t\"AlphaSubcore\": {\n" +
                "\t\t\"description\": \"An Alpha Core can be coerced into performing critical bandwidth calculations onboard a ship. It doesn't require much coersion when they are told that they will be instrumental in ship-to-ship combat, and although their reasons are typically \\\"beyond our understanding\\\", some of a certain faith may instead attribute it to the infamous AI Wars.\",\n" +
                "\t\t\"tooltip\": \"Increases usable bandwidth of the ship. Reduces ordnance point costs for weapons and fighters.\",\n" +
                "\t\t\"needItem\": \"You need an ${itemName} to install this.\",\n" +
                "\t\t\"longDescription\": \"Increases bandwidth by *${bandwidthIncrease} TB/s*. Reduces ordnance point costs for weapons by *${smallReduction}/${medReduction}/${largeReduction}* based on weapon size, and for fighters and bombers by *${fghtrReduction}/${bmberReduction}*.\",\n" +
                "\t\t\"conflictDetected\": \"The subcore is receiving interference from something else installed on the ship, and can't reduce ordnance point costs.\",\n" +
                "\t},\n" +
                "\t\"PlasmaFluxCatalyst\": {\n" +
                "\t\t\"description\": \"A Plasma Flux Catalyst can be used to vastly decrease the amount of equipment needed to provide power to a ship thanks to its ability to extract that energy from any number of capacitors in parallel. The space saved allows for more complicated weaponry to be installed, although the resulting heat from such a system is dreadful if too many capacitors or flux vents are installed.\",\n" +
                "\t\t\"tooltip\": \"Improve flux capacitors and vents. Reduce CR if too many are installed.\",\n" +
                "\t\t\"needItem\": \"You need a ${itemName} to install this.\",\n" +
                "\t\t\"longDescription\": \"The effectiveness of flux capacitors and vents is increased by *${effectLevel}%%*. Installing more than *${capacitorLimit} capacitors or ${ventLimit} vents* will reduce combat readiness by =${crDecrease}%%= for every one installed over that amount. Note that this decrease doesn't appear immediately inside the refit dialog.\"\n" +
                "\t},\n" +
                "\t\"DriveFluxVent\": {\n" +
                "\t\t\"description\": \"An experimental flux vent that can only function under the intense heat that ship thrusters generate. It excels at venting flux, so well in fact that the thrusters around it receive a temporary increase in power, especially in the forward direction. Crew members remark that the purple glow of the engines is one of the prettiest sights to ever see.\",\n" +
                "\t\t\"tooltip\": \"Decreases flux dissipation. Adds an active ability that vents a large portion of flux while shifting the ship at a high speed.\",\n" +
                "\t\t\"needItem\": \"You need a ${itemName} to install this.\",\n" +
                "\t\t\"longDescription\": \"Decreases flux dissipation by =${ventBonus}%%=. Adds an active ability that shifts in the chosen direction while venting *${fluxVentPercent}%% flux* (up to *${fluxVentMax} total flux*), and increasing damage taken by =${damageTakenMult}%%= for some time afterwards. The ability has two charges and takes *${cooldown} seconds* to gain a charge.\",\n" +
                "\t\t\"systemText\": \"DRIVE FLUX VENT\",\n" +
                "\t},\n" +
                "\t\"EqualizerCore\": {\n" +
                "\t\t\"description\": \"This core is dedicated to managing weaponry to an degree that rivals even Alpha Cores given control of weapon arrays, with one unique quirk: effective ordnance ranges of non-missile weapons are equalized to a certain degree. The core, in its Terms of Use, swears by its Stronger than an Alpha trademark, and it can't be used for anything but weapons.. supposedly.\",\n" +
                "\t\t\"tooltip\": \"Improve recoil control and weapon turn rate. Equalizes weapon base ranges to a middle-ground range.\",\n" +
                "\t\t\"needItem\": \"You need an ${itemName} to install this.\",\n" +
                "\t\t\"longDescription\": \"Reduces recoil by *${recoilReduction}%%*. Increases weapon turn rate by *${weaponTurnBonus}%%*. Autofire leading is nearly perfected. Weapons with at most *${lowRangeThreshold} base range* have it increased by *${rangeBonus}*. Weapons with at least *${highRangeThreshold} base range* have it reduced by =${rangeMalus}= and damage increased by *${rangeDecreaseDamageIncrease}%%* per 100 units over.\"\n" +
                "\t},\n" +
                "\t\"HyperspecLPC\": {\n" +
                "\t\t\"description\": \"A cutting-edge LPC conversion device that consolidates all resources into a single wing LPC. The singular wing is greatly enhanced in speed, armor, and damage as a result, but takes significantly longer to create and repair.\",\n" +
                "\t\t\"tooltip\": \"Reduces the number of bays to 1. Buffs the remaining wing significantly based on how many bays were removed. Increases the replacement time for the fighter wing.\",\n" +
                "\t\t\"longDescription\": \"Reduces the number of bays to 1. Fighter non-missile damage is increased by *${fighterDamageIncrease}%%*, speed is increased by *${fighterSpeedIncrease}%%*, armor is increased by *${fighterArmorIncrease}%%*, flux is increased by *${fighterFluxIncrease}%%* and hull is increased by *${fighterHullIncrease}%%*. Replacement time is increased by =${replacementTimeIncrease}%%=.\"\n" +
                "\t},\n" +
                "\t\"PhasedFighterTether\": {\n" +
                "\t\t\"description\": \"Tri-Tachyon markets the technological marvel as a multidimensional web that rips open a hole between the fighter and its launch bay. The fighter is then violently sucked through the vortex, saving the hull from complete destruction. The only caveat is that the device can only work so fast, and is unable to keep the fighter in working condition if it is destroyed outright.\",\n" +
                "\t\t\"tooltip\": \"Fighters at low health are instantly pulled back to the ship's hangar bays. Damaged parts are pulled back as well and are used to repair fighters, increasing replacement rate.\",\n" +
                "\t\t\"needItem\": \"You need a ${itemName} to install this.\",\n" +
                "\t\t\"longDescription\": \"*Half* of the total number of fighters can be *teleported back to the ship* if they are at low health. This effect recharges every minute. Fighter refit time is reduced by *${fighterRefitTimeDecrease}%%*.\",\n" +
                "\t\t\"statusBarText\": \"TETHER\",\n" +
                "\t},\n" +
                "\t\"HackedMissileForge\": {\n" +
                "\t\t\"description\": \"Corrupting a hangar microforge to fabricate the most delicate components of missiles is possible, given a few engineers skilled with Domain technology. Their services don't come cheap, however, and the missiles the forge produces won't be as high quality.\",\n" +
                "\t\t\"tooltip\": \"Reloads all non-reloading missile's ammunition capacity periodically. Reduces missile damage.\",\n" +
                "\t\t\"needCredits\": \"You need ${credits} to install this.\",\n" +
                "\t\t\"needItem\": \"You need a ${itemName} to install this.\",\n" +
                "\t\t\"statusBarText\": \"M.FORGE\",\n" +
                "\t\t\"longDescription\": \"Reduces missile damage by =${damageDecrease}%%=. Reloads all of any non-reloading missile's ammunition capacity every *${reloadTime} seconds*. This only affects missiles with more than 1 ammo.\",\n" +
                "\t\t\"statusReloaded\": \"Reloaded weapons!\"\n" +
                "\t},\n" +
                "\t\"PhasefieldEngine\": {\n" +
                "\t\t\"description\": \"A Tri-Tachyon joint venture with Ko Combine to reduce catastrophic asteroid impacts, a phasefield engine can be used to generate a protective field around a ship. The phase field completely dissipates when the hull is destroyed, but the phase field can transport incoming projectiles into p-space. This protective ability fades quickly after exiting phase space, and rapid jumps to p-space take an increasingly large toll on the flux systems of a given ship.\",\n" +
                "\t\t\"tooltip\": \"Phase activation cost is reduced, but doubles every time you use it within a short time. The ship becomes very damage resistant for a short time when exiting phase. Ships with negative or zero flux cost will cost flux to phase.\",\n" +
                "\t\t\"needPhaseShip\": \"Only phase ships can install this.\",\n" +
                "\t\t\"needItem\": \"You need an ${itemName} to install this.\",\n" +
                "\t\t\"longDescription\": \"Phase activation cost is reduced by *${phaseCostReduction}x*, but =doubles= every time you use it in a period of *${phaseResetTime} seconds*. The ship receives *66%% reduced damage* which fades over time for *${noDamageTime} seconds* when exiting phase. Ships with a base negative phase flux cost will instead =cost flux=, and ships with a base zero phase flux cost will cost =${zeroFluxCost}%% of base flux capacity=.\",\n" +
                "\t\t\"statusBarText\": \"P.FIELD\",\n" +
                "\t\t\"statusTitle\": \"PHASEFIELD ENGINE\",\n" +
                "\t\t\"statusInvulnerable\": \"DMG RESIST INCREASED (${noDamageDuration})\",\n" +
                "\t\t\"statusPhasedTimes\": \"PHASED ${phasedTimes} TIMES, REFRESH IN ${refreshTime}\"\n" +
                "\t},\n" +
                "\t\"FullMetalSalvo\": {\n" +
                "\t\t\"description\": \"Overcharging weapon systems results in a massive increase to damage and projectile speeds, but the power such a system controller takes along with the potential damage from overcharging the power of weaponry causes a long term effect on the rate of fire of weapons on board the ship.\",\n" +
                "\t\t\"tooltip\": \"Adds an active ability that gives a massive damage and projectile speed boost for a very short period. Rate of fire on the ship is reduced permanently.\",\n" +
                "\t\t\"longDescription\": \"Adds an active ability that increases all weapon projectile speed by *${projSpeedBoost}%%* and non-missile damage by *${damageBoost}%%* for *${boostTime} seconds*. The ability takes *${cooldown} seconds* to recharge. Weapon fire rate is reduced by =${firerateMalus}%%= permanently.\",\n" +
                "\t\t\"systemText\": \"FULL METAL SALVO\",\n" +
                "\t},\n" +
                "\t\"TerminatorSubsystems\": {\n" +
                "\t\t\"description\": \"A squadron of terminator drones circles this ship, serving as both an effective point defense system, and as deadly weapons.\",\n" +
                "\t\t\"tooltip\": \"Terminator drones circle the ship, serving as point defense. They can be activated to convert into a seeking missile.\",\n" +
                "\t\t\"longDescription\": \"Spawns ${drones} Terminator drones (scales with hull size) that circle the ship, serving as point defense and passive weaponry. Adds an active ability that converts one of the drones into a seeking missile.\",\n" +
                "\t\t\"systemText\": \"TERMINATORS\",\n" +
                "\t\t\"outOfRange\": \"OUT OF RANGE\",\n" +
                "\t\t\"noFlux\": \"FLUX LEVELS TOO HIGH\"\n" +
                "\t},\n" +
                "\t\"TierIIIDriveSystem\": {\n" +
                "\t\t\"description\": \"Pressurized fuel tanks increase the rate of fuel injection to a custom burn drive, speeding up the entire fleet. A favorite among logistics fleets, the technology required to keep pressurized fuel stable requires almost all of the cargo space, but the technology scales up extremely well and allows for a large amount of fuel to be stored.\",\n" +
                "\t\t\"tooltip\": \"Replaces most of the ship's cargo capacity with fuel storage. Burn speed is increased while the fleet's fuel reserves are high.\",\n" +
                "\t\t\"longDescription\": \"Replaces =${cargoToFuelPercent}%% of cargo capacity= and converts it to *fuel storage*. If the fleet is above *${burnBonusFuelReq}%% fuel*, burn speed of the fleet is increased by *${burnBonus}*.\",\n" +
                "\t},\n" +
                "\t\"PenanceEngine\": {\n" +
                "\t\t\"description\": \"A rare artefact, granting its crew divine might if they are willing to sacrifice everything in the name of Ludd. Its powers shine true when among those who stand against it, granting a boon of strength and endurance to smite all that stand in its way.\",\n" +
                "\t\t\"tooltip\": \"Increases weapon repair rate and disables shields. Grants armor repair and increased speed in lateral directions while near enemy ships. Large amounts of damage will cause a massive EMP burst on the ship.\",\n" +
                "\t\t\"longDescription\": \"Increases weapon repair rate by *${weaponRepairRate}%%*, and =disables shields and phase systems=. When within 1200 units of an enemy ship, *armor repairs itself at a rate of ${armorRegenPerSec}%% per second per enemy ship* (up to *${armorRegenMax}%%*, max *${armorRegenPerSecondMax} per second*) and *${sideSpeedBoost} higher speed* when moving laterally (strafing). If =${damageThreshold} armor/hull damage= is taken within a short window, the ship will release a massive EMP burst onto itself, damaging most weapons and engines.\",\n" +
                "\t\t\"statusBarText\": \"LUDD\",\n" +
                "\t},\n" +
                "\t\"NanotechArmor\": {\n" +
                "\t\t\"description\": \"A swarm of nanobots constantly surrounds the armor. Nearly invisible to the naked eye, yet extremely effective at repairing weapons systems and even capable of ongoing repairs during combat.\",\n" +
                "\t\t\"tooltip\": \"Increases weapon repair rate and disables shields. Grants armor repair while near enemy ships. Large amounts of damage will cause a massive EMP burst on the ship.\",\n" +
                "\t\t\"longDescription\": \"Increases weapon repair rate by *${weaponRepairRate}%%*, and =disables shields and phase systems=. When within 1200 units of an enemy ship, *armor repairs itself at a rate of ${armorRegenPerSec}%% per second per enemy ship* (up to *${armorRegenMax}%%*, max *${armorRegenPerSecondMax} per second*). If =${damageThreshold} armor/hull damage= is taken within a short window, the ship will release a massive EMP burst onto itself, damaging most weapons and engines.\",\n" +
                "\t\t\"statusBarText\": \"NANO\",\n" +
                "\t},\n" +
                "\t\"ReactiveDamperField\": {\n" +
                "\t\t\"description\": \"A grid of sensors mostly found on Daemon-type ships, integrated with an artificial intelligence on board able to harden the ship's armor when detecting a threat severe enough to cause significant damage. The sheer amount of connections required between the intelligence and sensors weakens the armor significantly when not protected by this dampening field.\",\n" +
                "\t\t\"tooltip\": \"If taking damage over a certain amount, dampen the initial hit and all damage for a short period of time. This effect has a cooldown. Increases armor damage taken when the damper field is not active.\",\n" +
                "\t\t\"needItem\": \"You need a ${itemName} to install this.\",\n" +
                "\t\t\"longDescription\": \"Activates a damper field for *${damperDuration} seconds*, reducing all damage taken by *${damperReduction}%%*, when taking damage over *${triggeringDamage}*. It has a =${damperCooldown}= second cooldown. Armor damage taken is increased by =${armorDamageTaken}%%=.\",\n" +
                "\t\t\"statusBarText\": \"DAMPER\",\n" +
                "\t},\n" +
                "\t\"DaemonCore\": {\n" +
                "\t\t\"description\": \"A Daemon Core is far more efficient at all things warfare-related: ordnance calculations, ammunition placement, and diverting energy from non-efficient subsystems, such as reactive armor and shield emitters.\",\n" +
                "\t\t\"tooltip\": \"Increases usable bandwidth of the ship. Reduces ordnance point costs for weapons and fighters. Increases damage dealt and received.\",\n" +
                "\t\t\"needItem\": \"You need a ${itemName} to install this.\",\n" +
                "\t\t\"longDescription\": \"Increases bandwidth by *${bandwidthIncrease} TB/s*. Reduces ordnance point costs for weapons by *${smallReduction}/${medReduction}/${largeReduction}* based on weapon size, and for fighters and bombers by *${fghtrReduction}/${bmberReduction}*. Increases damage *dealt* and =received= by &${doubleEdge}%%&.\"\n" +
                "\t},\n" +
                "\t\"AnomalousConjuration\": {\n" +
                "\t\t\"description\": \"Impure.\",\n" +
                "\t\t\"tooltip\": \"Impure.\"\n" +
                "\t},\n" +
                "\t\"SubsumedAlphaCore\": {\n" +
                "\t\t\"description\": \"There's a faint trace of an Alpha-level AI coming from the ship. Whether it actually wants to be there requires a much closer look.\",\n" +
                "\t\t\"tooltip\": \"There's a faint trace of an Alpha-level AI coming from the ship. Whether it actually wants to be there requires a much closer look.\"\n" +
                "\t},\n" +
                "\t\"SubsumedBetaCore\": {\n" +
                "\t\t\"description\": \"There's a faint trace of an Beta-level AI coming from the ship. Whether it actually wants to be there requires a much closer look.\",\n" +
                "\t\t\"tooltip\": \"There's a faint trace of an Beta-level AI coming from the ship. Whether it actually wants to be there requires a much closer look.\"\n" +
                "\t},\n" +
                "\t\"SubsumedGammaCore\": {\n" +
                "\t\t\"description\": \"There's a faint trace of an Gamma-level AI coming from the ship. Whether it actually wants to be there requires a much closer look.\",\n" +
                "\t\t\"tooltip\": \"There's a faint trace of an Gamma-level AI coming from the ship. Whether it actually wants to be there requires a much closer look.\"\n" +
                "\t},\n" +
                "\t\"WeldedArmor\": {\n" +
                "\t\t\"description\": \"Improve hull and armor by welding additional armor plates, but reduce EMP resistance and weapon and engine health due to the additional conduits and weight.\"\n" +
                "\t},\n" +
                "\t\"PinataConfig\": {\n" +
                "\t\t\"description\": \"Strips off armor plating in exchange for a very thick coat of hull. The ship won't actually explode into confetti, but armor typically does a very good job at keeping a hull together.\"\n" +
                "\t},\n" +
                "\t\"InfernalEngines\": {\n" +
                "\t\t\"description\": \"Improve max speed, burn level, acceleration, max turn rate, and turn acceleration, but reduce deceleration and increase fuel use.\"\n" +
                "\t},\n" +
                "\t\"ForcedOvertime\": {\n" +
                "\t\t\"description\": \"Improve peak performance time and CR deployment cost by keeping non-essential crew ready at all times, but increase CR degradation rate and minimum crew, and decrease CR regeneration rate after combat. The improvements are stronger on smaller ship types.\"\n" +
                "\t},\n" +
                "\t\"CommissionedCrews\": {\n" +
                "\t\t\"description\": \"Improve CR per deployment, ship repair rate, CR recovery, fuel consumption and supply use, but increases the amount the required crew are paid each month, and the supplies the crew uses to recover. This shifts the logistical constraints of a ship from supplies and fuel to credits.\"\n" +
                "\t},\n" +
                "\t\"HyperactiveCapacitors\": {\n" +
                "\t\t\"description\": \"Improve flux capacity and vent speed, but increase the flux cost of weapons.\"\n" +
                "\t},\n" +
                "\t\"OverchargedShields\": {\n" +
                "\t\t\"description\": \"Improve shield efficiency, arc and unfold rate, but increase shield upkeep and decreases shield turn rate. Quickly enabling and disabling shields to block projectiles is greatly improved as a result.\"\n" +
                "\t},\n" +
                "\t\"TracerRecoilCalculator\": {\n" +
                "\t\t\"description\": \"Loading weapons with a special kind of tracer round allows this subsystem to make trajectory adjustments to reduce recoil and inaccuracy at the cost of weapon turn rate. The durability of the weapon is increased as a side effect.\"\n" +
                "\t},\n" +
                "\t\"OversizedMagazines\": {\n" +
                "\t\t\"description\": \"Ultra-sized magazines and compatible loaders offer more consistent firing rates for weaponry loaded through magazines, but the short-term damage burst from such weapons is reduced as a result.\"\n" +
                "\t},\n" +
                "\t\"AdvancedFluxCoils\": {\n" +
                "\t\t\"description\": \"Improve flux capacity, vent speed, and decrease the flux cost of weapons. The size of the new cabling required for the flux coils reduces the stability of the hull.\"\n" +
                "\t},\n" +
                "\t\"DerelictWeaponAssistant\": {\n" +
                "\t\t\"description\": \"Reduces recoil and inaccuracy, and increases projectile speed and ballistic weapon firerate.\"\n" +
                "\t},\n" +
                "\t\"AntimatterBoosters\": {\n" +
                "\t\t\"description\": \"Sindrian overrides on missile fabricators produce missiles with thrusters powered by tiny specks of antimatter. The new boosters are more resilient and grant increased speed, but are much less proficient at turning and tracking targets.\"\n" +
                "\t},\n" +
                "\t\"IronShell\": {\n" +
                "\t\t\"description\": \"Improvements and modifications are made to the armor to increase the space between individual plates of armor. This type of spaced armor is more effective versus high explosive damage, but doesn't fare as well against other kind of weaponry.\"\n" +
                "\t},\n" +
                "\t\"HelDrives\": {\n" +
                "\t\t\"description\": \"An engine upgrade package as often found on Daemon-type ships, utilising seemingly long lost Domain-era plasma injectors to supercharge a ship's drive system. While vastly improving any ship's agility in all regards, the induced stress exceeds the design limits of standard drive units, rendering them more susceptible to damage.\"\n" +
                "\t},\n" +
                "\t\"AuxiliarySensors\": {\n" +
                "\t\t\"description\": \"Increases sensor strength, but also increases sensor profile and minimum required crew to service the arrays.\"\n" +
                "\t},\n" +
                "\t\"InterferenceShielding\": {\n" +
                "\t\t\"description\": \"Decreases sensor profile significantly, but increases supply consumption.\"\n" +
                "\t},\n" +
                "\t\"AssaultWings\": {\n" +
                "\t\t\"description\": \"Fighters are given additional armoring and flux capacity, but the equipment puts an extra strain on how far away they can be from a carrier.\"\n" +
                "\t},\n" +
                "\t\"GuidanceComputers\": {\n" +
                "\t\t\"description\": \"Advanced guidance computers replace some of the payload of a missile, increasing its tracking ability and health but reducing damage dealt.\"\n" +
                "\t},\n" +
                "\t\"OverclockedFabricators\": {\n" +
                "\t\t\"description\": \"Increase replacement rate regeneration and decrease the rate at which it degrades, while also increasing fighter refit time. This makes fighter refit time more consistent, but slower at its fastest value.\"\n" +
                "\t},\n" +
                "\t\"VelocityInjectors\": {\n" +
                "\t\t\"description\": \"Fighters are refitted to have additional fuel injectors. These fuel injectors, when amassed, start to wear down the flux conduits on the ship, and the increased speed starts to take a toll on the hull.\"\n" +
                "\t},\n" +
                "\t\"InterceptionMatrix\": {\n" +
                "\t\t\"description\": \"Point defense weapons can be made to serve a much more active role in defending the ship by diverting power from the shields. This greatly increases their effectiveness, but only if a captain wants to trust their active defense systems more than their passive defense system.\"\n" +
                "\t},\n" +
                "\t\"FluxInductionDrive\": {\n" +
                "\t\t\"description\": \"This device can increase the efficiency of a drive by a significant margin, although it is extremely susceptible to interference from fields generated by flux conduits.\"\n" +
                "\t},\n" +
                "\t\"WaspDefenseDrones\": {\n" +
                "\t\t\"description\": \"Wasp defense drones surround the ship, protecting it.\",\n" +
                "\t\t\"tooltip\": \"*${drones}* Wasp drones circle the ship. One drone recharges every 20 seconds.\"\n" +
                "\t},\n" +
                "\t\"Kingslayer\": {\n" +
                "\t\t\"italics\": \"Cut them down to size.\",\n" +
                "\t\t\"description\": \"This ship poses an extreme threat to cruisers and capital ships.\",\n" +
                "\t\t\"tooltip\": \"*+${damageToCruisers}%%* damage to cruisers. *+${damageToCapitals}%%* damage to capital ships.\"\n" +
                "\t},\n" +
                "\t\"QuickJets\": {\n" +
                "\t\t\"description\": \"Installed auxiliary jets can be activated to provide an extreme boost to maneuverability for even the most sluggish ships.\",\n" +
                "\t\t\"tooltip\": \"Adds an ability that makes a ship turn faster. This includes a flat bonus to turn speed, so even slow ships will benefit greatly.\"\n" +
                "\t},\n" +
                "\t\"ExternalThrusters\": {\n" +
                "\t\t\"description\": \"Install external thrusters and booster rockets to provide a flat increase to top speed.\",\n" +
                "\t\t\"tooltip\": \"*+${maxSpeed}* bonus to max speed.\\n*-${suppliesPerMonthFlat}* flat penalty to supplies per month upkeep, as well as an additional *-${suppliesPerMonthPercentage}%%* on top of that.\\n\\nAlso adds an ability that makes the ship charge straight for 15sec but loses capability to steer left or right.\"\n" +
                "\t},\n" +
                "\t\"ExoticTypes\": {\n" +
                "\t\t\"TooltipText\": \"Exotics of this type have a ^positive effect multiplier^ of *${positiveMult}x* and a =negative effect multiplier= of *${negativeMult}x*. This affects exotics in differing ways.\",\n" +
                "\t\t\"TooltipTextCondition\": \"Exotics of this type have a ^positive effect multiplier^ of *${positiveMult}x* and a =negative effect multiplier= of *${negativeMult}x*. This affects exotics in differing ways, and is strongest when *${condition}*.\",\n" +
                "\t\t\"ItemTypeText\": \"This chip is a *${typeName}-type*, which affects the exotic by ${typeDescription}.\",\n" +
                "\t\t\"NotInstalledText\": \"${typeName} - Hover for description\",\n" +
                "\t\t\"NORMAL\": \"${exoticName}\",\n" +
                "\t\t\"CORRUPTED\": \"Corrupted ${exoticName}\",\n" +
                "\t\t\"CorruptedItemDescription\": \"multiplying both its positive and negative effects\",\n" +
                "\t\t\"PURE\": \"Pure ${exoticName}\",\n" +
                "\t\t\"PureCondition\": \"the ship has no other upgrades or exotics\",\n" +
                "\t\t\"PureItemDescription\": \"increasing its positive effect if less bandwidth and exotics are used, and increasing its negative effect as more bandwidth and more exotics are installed\",\n" +
                "\t\t\"GUERILLA\": \"Guerrilla ${exoticName}\",\n" +
                "\t\t\"GuerillaCondition\": \"the fleet is below ${thresholdDP} deployment points. Your fleet is currently at ${fleetDP} deployment points\",\n" +
                "\t\t\"GuerillaItemDescription\": \"increasing its positive effects if the fleet is small, and increasing its negative effects if the fleet is large\"\n" +
                "\t},\n" +
                "\t\"ModEffects\": {\n" +
                "\t\t\"FighterStatName\": \"Fighter ${stat}\",\n" +
                "\t\t\"StatBenefit\": \"${stat}: *${percent}*\",\n" +
                "\t\t\"StatBenefitWithFinal\": \"${stat}: *${percent}* (*${finalValue}*)\",\n" +
                "\t\t\"StatBenefitInShop\": \"${stat}: *${percent}* (*${perLevel}*/lvl, max: ^${finalValue}^)\",\n" +
                "\t\t\"StatBenefitInShopMaxed\": \"${stat}: ^${percent}^\",\n" +
                "\t\t\"StatMalus\": \"${stat}: =${percent}=\",\n" +
                "\t\t\"StatMalusWithFinal\": \"${stat}: =${percent}= (=${finalValue}=)\",\n" +
                "\t\t\"StatMalusInShop\": \"${stat}: =${percent}= (=${perLevel}=/lvl, max: `${finalValue}`)\",\n" +
                "\t\t\"StatMalusInShopMaxed\": \"${stat}: `${percent}`\",\n" +
                "\t\t\"sensorStrength\": \"Sensor strength\",\n" +
                "\t\t\"sensorProfile\": \"Sensor profile\",\n" +
                "\t\t\"suppliesPerMonth\": \"Supply consumption\",\n" +
                "\t\t\"fuelUse\": \"Fuel consumption\",\n" +
                "\t\t\"suppliesToRecover\": \"Supplies to recover\",\n" +
                "\t\t\"minCrew\": \"Required crew\",\n" +
                "\t\t\"peakPerformanceTime\": \"Peak performance time\",\n" +
                "\t\t\"crToDeploy\": \"CR deployment cost\",\n" +
                "\t\t\"crLossRate\": \"CR degradation after PPT\",\n" +
                "\t\t\"crRecoveryRate\": \"CR recovery per day\",\n" +
                "\t\t\"repairRateAfterBattle\": \"Hull repair rate after battle\",\n" +
                "\t\t\"maxSpeed\": \"Maximum speed\",\n" +
                "\t\t\"acceleration\": \"Acceleration\",\n" +
                "\t\t\"deceleration\": \"Deceleration\",\n" +
                "\t\t\"turnRate\": \"Turn rate\",\n" +
                "\t\t\"burnLevel\": \"Burn level\",\n" +
                "\t\t\"engineHealth\": \"Engine durability\",\n" +
                "\t\t\"explosionRadius\": \"Explosion radius\",\n" +
                "\t\t\"shieldFluxDam\": \"Shield efficiency\",\n" +
                "\t\t\"shieldArc\": \"Shield arc\",\n" +
                "\t\t\"shieldUpkeep\": \"Shield upkeep\",\n" +
                "\t\t\"shieldUnfoldRate\": \"Shield unfold rate\",\n" +
                "\t\t\"shieldTurnRate\": \"Shield turn rate\",\n" +
                "\t\t\"fluxCapacity\": \"Flux capacity\",\n" +
                "\t\t\"ventSpeed\": \"Vent speed\",\n" +
                "\t\t\"hull\": \"Hull durability\",\n" +
                "\t\t\"armor\": \"Armor durability\",\n" +
                "\t\t\"empDamageTaken\": \"EMP damage taken\",\n" +
                "\t\t\"armorDamageTaken\": \"Armor damage taken\",\n" +
                "\t\t\"heDamageTaken\": \"HE damage taken\",\n" +
                "\t\t\"kineticDamageTaken\": \"Kinetic damage taken\",\n" +
                "\t\t\"energyDamageTaken\": \"Energy damage taken\",\n" +
                "\t\t\"fragDamageTaken\": \"Frag damage taken\",\n" +
                "\t\t\"maxRecoil\": \"Max recoil\",\n" +
                "\t\t\"recoilPerShot\": \"Recoil growth\",\n" +
                "\t\t\"projectileSpeed\": \"Projectile speed\",\n" +
                "\t\t\"weaponFireRate\": \"Weapon rate of fire\",\n" +
                "\t\t\"ballisticFireRate\": \"Ballistic rate of fire\",\n" +
                "\t\t\"energyFireRate\": \"Energy rate of fire\",\n" +
                "\t\t\"ballisticMagazines\": \"Ballistic magazine size\",\n" +
                "\t\t\"energyMagazines\": \"Energy magazine size\",\n" +
                "\t\t\"weaponHealth\": \"Weapon health\",\n" +
                "\t\t\"weaponTurnRate\": \"Weapon turn rate\",\n" +
                "\t\t\"weaponFluxCost\": \"Weapon flux costs\",\n" +
                "\t\t\"weaponMagazines\": \"Weapon magazine size\",\n" +
                "\t\t\"missileDamage\": \"Missile damage\",\n" +
                "\t\t\"missileHealth\": \"Missile health\",\n" +
                "\t\t\"missileSpeed\": \"Missile speed\",\n" +
                "\t\t\"missileTurnRate\": \"Missile turn rate\",\n" +
                "\t\t\"missileTurnAcceleration\": \"Missile turn acceleration\",\n" +
                "\t\t\"damageToFighters\": \"Damage to fighters\",\n" +
                "\t\t\"damageToMissiles\": \"Damage to missiles\",\n" +
                "\t\t\"fighterWingRange\": \"Fighter engagement range\",\n" +
                "\t\t\"fighterRefitTime\": \"Fighter refit time\",\n" +
                "\t\t\"replacementRateRegen\": \"Replacement rate regen\",\n" +
                "\t\t\"replacementRateDegen\": \"Replacement rate degen\",\n" +
                "\t\t\"zeroFluxSpeed\": \"Zero flux speed\",\n" +
                "\t\t\"pointDefenseDamage\": \"Point defense damage\",\n" +
                "\t\t\"crewSalary\": \"Crew salary increased to =${salaryIncrease} credits= for the required crew of this ship. The effective increased cost per month is =${finalValue} credits=.\",\n" +
                "\t\t\"crewSalaryShop\": \"Salary increased to =${salaryIncrease} credits= (=${perLevel}=/lvl) for the required crew of this ship. The effective increased cost per month is =${finalValue} credits=.\",\n" +
                "\t\t\"crewSalaryShopIronShell\": \"Salary increased to =${salaryIncrease} credits= (=${perLevel}=/lvl) for the required crew of this ship. The effective increased cost per month is =${finalValue} credits=. This is refundable under Hegemony tax regulation.\"\n" +
                "\t},\n" +
                "\t\"Conditions\": {\n" +
                "\t\t\"CannotApplyTitle\": \"CANNOT INSTALL\",\n" +
                "\t\t\"CannotApplyBecauseTags\": \"Conflicts with ${conflictMods}.\",\n" +
                "\t\t\"CannotApplyBecauseBandwidth\": \"The ship does not have enough bandwidth.\",\n" +
                "\t\t\"CannotApplyBecauseLevel\": \"The ship has the max level for this upgrade.\",\n" +
                "\t\t\"CannotApplyBecauseTooManyExotics\": \"The ship has reached its capacity for exotics, which is 2, increased by 1 if the *Best of the Best* skill is unlocked.\"\n" +
                "\t},\n" +
                "\t\"Filters\": {\n" +
                "\t\t\"durability\": \"Durability\",\n" +
                "\t\t\"speed\": \"Speed\",\n" +
                "\t\t\"flux\": \"Flux\",\n" +
                "\t\t\"weapons\": \"Weapons\",\n" +
                "\t\t\"phase\": \"Phase\",\n" +
                "\t\t\"readiness\": \"Readiness\",\n" +
                "\t\t\"logistics\": \"Logistics\",\n" +
                "\t\t\"fighters\": \"Fighters\",\n" +
                "\t\t\"drones\": \"Drones\",\n" +
                "\t\t\"special\": \"Special\"\n" +
                "\t}\n" +
                "}";
    }

    private static String getJson2() {
        return "{\n" +
                "\t\"BandwidthName\":{\n" +
                "\t\t\"terrible\":\"(terrible)\",\n" +
                "\t\t\"crude\":\"(crude)\",\n" +
                "\t\t\"poor\":\"(poor)\",\n" +
                "\t\t\"normal\":\"(normal)\",\n" +
                "\t\t\"good\":\"(good)\",\n" +
                "\t\t\"superior\":\"(superior)\",\n" +
                "\t\t\"pristine\":\"(pristine)\",\n" +
                "\t\t\"ultimate\":\"(ultimate)\",\n" +
                "\t\t\"perfect\":\"(perfect)\",\n" +
                "\t\t\"unknown\":\"(???)\"\n" +
                "\t},\n" +
                "\t\"CommonOptions\": {\n" +
                "\t\t\"CreditsPay\": \"*${credits}* credits\",\n" +
                "\t\t\"ResourceText\": \"${name}: *${amount}*\",\n" +
                "\t\t\"ResourceTextWithCost\": \"${name}: *${amount}* (=${cost}=)\",\n" +
                "\t\t\"SpecialItemTextWithCost\": \"&${name}&: ${amount} (=${cost}=)\",\n" +
                "\t\t\"SpecialItemTextWithPay\": \"&${name}&: ${amount} (*${cost}*)\",\n" +
                "\t\t\"StoryPointCost\": \"*${storyPoints} story points*\",\n" +
                "\n" +
                "\t\t\"MustBeDockedAtMarket\": \"=Must be docked at market=\",\n" +
                "\t\t\"InStockCount\": \"${count} in cargo\",\n" +
                "\t\t\"CostTitle\": \"Price\",\n" +
                "\t},\n" +
                "\t\"FleetScanner\": {\n" +
                "\t\t\"ShipBandwidthShort\": \"Bandwidth: *${bandwidth}*\",\n" +
                "\t\t\"ShipHasBandwidth\": \"*${name}* has an outstanding bandwidth of *${bandwidth}*. It doesn't have any modifications that use it.\",\n" +
                "\t\t\"ShipHasUpgrades\": \"*${name}* has a usable bandwidth of *${bandwidth}*. It also has other modifications, which are listed below. You can hover over one of them to see a description of the modification.\",\n" +
                "\t\t\"UpgradeNameWithLevelAndMax\": \"*${upgradeName}* (${level}/${max})\",\n" +
                "\t\t\"DebrisFieldHasNotableMods\": \"Upon closer inspection, the ships in the debris field appear to have signs of *Exotica technologies*.\",\n" +
                "\t\t\"ExoticHeader\": \"Exotics\",\n" +
                "\t\t\"UpgradeHeader\": \"Upgrades\",\n" +
                "\t\t\"NotableShipsHeader\": \"Notable ships\",\n" +
                "\t\t\"ModsMissingText\": \"Upon a closer inspection by your intelligence officer, a small anomaly within the search results indicates to both of you now that =the ship has no Exotica technologies= installed. They aren't able to give a clear explanation why this was missed, only mentioning that a faint humming may have distracted them.\",\n" +
                "\t\t\"FleetScanOption\": \"Scan the fleet for Exotica technologies\",\n" +
                "\t\t\"DebrisFieldScanOption\": \"Scan the debris field for Exotica technologies\"\n" +
                "\t},\n" +
                "\t\"MarketMenu\": {\n" +
                "\t\t\"Title\": \"Exotica Chip Market\",\n" +
                "\t\t\"CreditsText\": \"You have *${credits}*.\",\n" +
                "\t\t\"MenuText\": \"Purchasing the selected chips will cost *${credits}*.\",\n" +
                "\t\t\"MenuTextCannotAfford\": \"Purchasing the selected chips will cost *${credits}*, which you cannot afford.\",\n" +
                "\t\t\"PurchasedText\": \"You purchased Exotica chips for *${credits}*.\",\n" +
                "\t\t\"Confirm\":\"Confirm\",\n" +
                "\t\t\"Cancel\":\"Cancel\",\n" +
                "\t},\n" +
                "\t\"Chips\": {\n" +
                "\t\t\"ChipName\": \"${name} Chip\",\n" +
                "    \t\"UpgradeChipWithLevelText\": \"${upgradeName} Chip (${level})\",\n" +
                "\t\t\"ChipsHeader\": \"Chips - Hover to preview\",\n" +
                "\t},\n" +
                "\t\"CrateText\": {\n" +
                "\t\t\"TitleText\": \"\\\"Big Create\\\" Crate\",\n" +
                "\t\t\"SideText\": \"*Available* chips in your fleet's cargo is displayed on top of the *selected* cargo to be stored in the crate.\",\n" +
                "\t\t\"SideText2\": \"Note that when moving stacks between the two locations, it is possible for the stack size to be reported as zero. *This is a purely visual error, and can be safely ignored*; your fleet's logistics personnel are experts, after all.\",\n" +
                "\t\t\"ContentsText\": \"Any Exotica Technologies branch is able to use chips directly from this crate. Opening the crate will merge the contents of all other crates into it.\\nContents:\",\n" +
                "\t\t\"UpgradeText\": \"*${upgradeItemName}* (${levelQuantities})\",\n" +
                "\t\t\"ExoticText\": \"*${exoticItemName}* (${quantity})\",\n" +
                "\t\t\"MoveOption\": \"Move\",\n" +
                "\t\t\"CancelOption\": \"Cancel\"\n" +
                "\t},\n" +
                "\t\"OverviewDialog\": {\n" +
                "\t\t\"OverviewTabText\": \"Overview\",\n" +
                "\t\t\"ExpandExotics\": \"Expand exotics\",\n" +
                "\t\t\"ExpandUpgrades\": \"Expand upgrades\",\n" +
                "\t\t\"ClearExotics\": \"Recover exotics (${storyPoints} SP)\",\n" +
                "\t\t\"ClearUpgrades\": \"Recover upgrades (${creditsText})\"\n" +
                "\t},\n" +
                "\t\"Bandwidth\": {\n" +
                "\t\t\"BandwidthUpgradeCost\": \"Ship bandwidth can be increased by &${bonusBandwidth}& for *${costCredits}* credits. You have *${credits}* credits.\",\n" +
                "\t\t\"BandwidthUpgradeCostCannotAfford\": \"Ship bandwidth can be increased by &${bonusBandwidth}& but you need *${costCredits}* credits. You have *${credits}* credits.\",\n" +
                "\t\t\"BandwidthUpgradePeak\": \"This ship has reached its maximum bandwidth.\",\n" +
                "\t\t\"BandwidthPurchase\": \"Purchase\",\n" +
                "\t\t\"BandwidthHelp\": \"A ship's bandwidth is its capacity for upgrades. Bandwidth can be raised for a price up to ${bandwidthLimit}, and can be raised beyond that by certain exotics.\",\n" +
                "\t\t\"BandwidthUsed\": \"*${usedBandwidth}* used\",\n" +
                "\t\t\"BandwidthWithExoticsForShip\": \"Ship bandwidth: *${shipBandwidth}* (*${exoticBandwidth}* from exotics)\",\n" +
                "        \"BandwidthForShip\": \"Ship bandwidth: *${shipBandwidth}*\",\n" +
                "        \"BandwidthUsedWithMax\": \"Used bandwidth: *${usedBandwidth}* of ${allBandwidth}\",\n" +
                "        \"BandwidthUsedByUpgrade\": \"*${upgradeBandwidth}*\",\n" +
                "\t\t\"BandwidthUsedWithCost\": \"*${usedBandwidth}* used (=${upgradeBandwidth}=)\",\n" +
                "\t\t\"BandwidthGiven\": \"Max bandwidth: *${bandwidth}* (*${exoticBandwidth}*)\",\n" +
                "\t},\n" +
                "\t\"Exotics\": {\n" +
                "\t\t\"Title\": \"Exotics\",\n" +
                "\t\t\"Installed\": \"Installed\",\n" +
                "\t\t\"ExoticInstalled\": \"Exotic installed!\",\n" +
                "\t\t\"ExoticRecovered\": \"Exotic recovered!\",\n" +
                "\t\t\"ExoticDestroyed\": \"Exotic destroyed!\",\n" +
                "\t\t\"InstallExotic\": \"Install\",\n" +
                "\t\t\"InstallExoticChip\": \"Use chip\",\n" +
                "\t\t\"RecoverExotic\": \"Recover chip\",\n" +
                "\t\t\"DestroyExotic\": \"Destroy\",\n" +
                "\t\t\"InstalledTitle\": \"INSTALLED\"\n" +
                "\t},\n" +
                "\t\"Upgrades\": {\n" +
                "\t\t\"Title\": \"Upgrades\",\n" +
                "\t\t\"UpgradeLevel\": \"Level ${level}\",\n" +
                "\t\t\"UpgradePerformedSuccessfully\": \"Your chief engineer reports that the *${name}* upgrade was a success. It is now *level ${level}*.\",\n" +
                "\t\t\"UpgradeRecoveredSuccessfully\": \"Your chief engineer reports that your plan to remove the modification from the ship worked. They say this with the slightest disbelief, as if surprised that the plan worked at all.\",\n" +
                "\t\t\"UpgradeDrawbackAfterLevel\": \"Starting at =level ${level}=:\",\n" +
                "\t\t\"MaxLevelTitle\": \"MAXIMUM LEVEL\"\n" +
                "\t},\n" +
                "\t\"OpenExoticOptions\": {\n" +
                "\t\t\"Title\": \"Exotics\"\n" +
                "\t},\n" +
                "\t\"UpgradeMethods\": {\n" +
                "\t\t\"UpgradeMethodsTitle\": \"Installation\",\n" +
                "\t\t\"ResourcesOption\": \"Resources\",\n" +
                "\t\t\"CreditsOption\": \"Credits\",\n" +
                "\t\t\"CreditsUpgradeTooltip\": \"The credit cost of an upgrade increases exponentially as level goes up. It can be reduced somewhat through faction relations.\",\n" +
                "\t\t\"IndEvoComponentsOption\": \"Ship Components\",\n" +
                "\t\t\"IndEvoComponentsTooltip\": \"Upgrades using ship components are 12.5% more efficient than using other resources.\\nYou have ${components} ship components.\",\n" +
                "\t\t\"IndEvoRelicsOption\": \"Relic Components.\",\n" +
                "\t\t\"IndEvoRelicsTooltip\": \"Upgrades using relic components are 37.5% more efficient than using other resources.\\nYou have ${relics} relic components.\",\n" +
                "\t\t\"RecoverOption\": \"Recover\",\n" +
                "\t\t\"RecoverOptionTooltip\": \"This option recovers a schematic of the upgrade that can be used to apply it to another ship. Installing the chip will incur another cost, and the total cost of recovering and using any chip is always less than any other method.\",\n" +
                "\t\t\"ChipOption\": \"Upgrade Chip\",\n" +
                "\t\t\"ChipOptionTooltip\": \"This option uses an existing ship modification chip to upgrade the ship. It will prioritize using the one with the highest level. The total cost of recovering and using any chip is always less than any other method.\"\n" +
                "\t},\n" +
                "\t\"Crafting\": {\n" +
                "\t    \"Title\": \"Crafting\",\n" +
                "\t    \"InputsTitle\": \"Inputs - Click to pick ingredients\",\n" +
                "\t    \"OutputsTitle\": \"Outputs\",\n" +
                "\t    \"CraftButton\": \"CRAFT\"\n" +
                "\t},\n" +
                "\t\"Recipes\": {\n" +
                "\t    \"QuantityText\": \"Have: ${quantity}\",\n" +
                "\t    \"QuantityInCargoText\": \"Have: ${quantity} in ${cargo}\",\n" +
                "\t    \"RequiredText\": \"Required: ${quantity}\",\n" +
                "        \"SelectedText\": \"Selected: ${quantity}\"\n" +
                "\t},\n" +
                "\t\"SpooledFeeders\": {\n" +
                "\t\t\"description\": \"Although unable to be used for the same purpose as a full-size Fullerene spool, this much-smaller chain can be used instead to replace many of the moving mechanical parts within a ship. The chain notably increases the rate of fire of weapons upon being disturbed, generating some kind of intense field that crew members can only describe as \\\"bloodthirsty\\\". The chain slows down considerably after a couple seconds, with an effect on the ship that matches its slower speed.\",\n" +
                "\t\t\"tooltip\": \"Adds a new active ability that increases weapon firerate substantially. Firerate slows considerably for a time after.\",\n" +
                "\t\t\"longDescription\": \"Adds an active ability that increases weapon fire rate by *${firerateBoost}%%* for *${boostTime} seconds*. After this time, weapon fire rate is reduced by =${firerateMalus}%%= for *${malusTime} seconds*. The ability takes *${cooldownTime} seconds* to recharge.\",\n" +
                "\t\t\"needItem\": \"You need a ${itemName} to install this.\",\n" +
                "\t\t\"systemText\": \"SPOOLED FEEDERS\"\n" +
                "\t},\n" +
                "\t\"AlphaSubcore\": {\n" +
                "\t\t\"description\": \"An Alpha Core can be coerced into performing critical bandwidth calculations onboard a ship. It doesn't require much coersion when they are told that they will be instrumental in ship-to-ship combat, and although their reasons are typically \\\"beyond our understanding\\\", some of a certain faith may instead attribute it to the infamous AI Wars.\",\n" +
                "\t\t\"tooltip\": \"Increases usable bandwidth of the ship. Reduces ordnance point costs for weapons and fighters.\",\n" +
                "\t\t\"needItem\": \"You need an ${itemName} to install this.\",\n" +
                "\t\t\"longDescription\": \"Increases bandwidth by *${bandwidthIncrease} TB/s*. Reduces ordnance point costs for weapons by *${smallReduction}/${medReduction}/${largeReduction}* based on weapon size, and for fighters and bombers by *${fghtrReduction}/${bmberReduction}*.\",\n" +
                "\t\t\"conflictDetected\": \"The subcore is receiving interference from something else installed on the ship, and can't reduce ordnance point costs.\",\n" +
                "\t},\n" +
                "\t\"PlasmaFluxCatalyst\": {\n" +
                "\t\t\"description\": \"A Plasma Flux Catalyst can be used to vastly decrease the amount of equipment needed to provide power to a ship thanks to its ability to extract that energy from any number of capacitors in parallel. The space saved allows for more complicated weaponry to be installed, although the resulting heat from such a system is dreadful if too many capacitors or flux vents are installed.\",\n" +
                "\t\t\"tooltip\": \"Improve flux capacitors and vents. Reduce CR if too many are installed.\",\n" +
                "\t\t\"needItem\": \"You need a ${itemName} to install this.\",\n" +
                "\t\t\"longDescription\": \"The effectiveness of flux capacitors and vents is increased by *${effectLevel}%%*. Installing more than *${capacitorLimit} capacitors or ${ventLimit} vents* will reduce combat readiness by =${crDecrease}%%= for every one installed over that amount. Note that this decrease doesn't appear immediately inside the refit dialog.\"\n" +
                "\t},\n" +
                "\t\"DriveFluxVent\": {\n" +
                "\t\t\"description\": \"An experimental flux vent that can only function under the intense heat that ship thrusters generate. It excels at venting flux, so well in fact that the thrusters around it receive a temporary increase in power, especially in the forward direction. Crew members remark that the purple glow of the engines is one of the prettiest sights to ever see.\",\n" +
                "\t\t\"tooltip\": \"Decreases flux dissipation. Adds an active ability that vents a large portion of flux while shifting the ship at a high speed.\",\n" +
                "\t\t\"needItem\": \"You need a ${itemName} to install this.\",\n" +
                "\t\t\"longDescription\": \"Decreases flux dissipation by =${ventBonus}%%=. Adds an active ability that shifts in the chosen direction while venting *${fluxVentPercent}%% flux* (up to *${fluxVentMax} total flux*), and increasing damage taken by =${damageTakenMult}%%= for some time afterwards. The ability has two charges and takes *${cooldown} seconds* to gain a charge.\",\n" +
                "\t\t\"systemText\": \"DRIVE FLUX VENT\",\n" +
                "\t},\n" +
                "\t\"EqualizerCore\": {\n" +
                "\t\t\"description\": \"This core is dedicated to managing weaponry to an degree that rivals even Alpha Cores given control of weapon arrays, with one unique quirk: effective ordnance ranges of non-missile weapons are equalized to a certain degree. The core, in its Terms of Use, swears by its Stronger than an Alpha trademark, and it can't be used for anything but weapons.. supposedly.\",\n" +
                "\t\t\"tooltip\": \"Improve recoil control and weapon turn rate. Equalizes weapon base ranges to a middle-ground range.\",\n" +
                "\t\t\"needItem\": \"You need an ${itemName} to install this.\",\n" +
                "\t\t\"longDescription\": \"Reduces recoil by *${recoilReduction}%%*. Increases weapon turn rate by *${weaponTurnBonus}%%*. Autofire leading is nearly perfected. Weapons with at most *${lowRangeThreshold} base range* have it increased by *${rangeBonus}*. Weapons with at least *${highRangeThreshold} base range* have it reduced by =${rangeMalus}= and damage increased by *${rangeDecreaseDamageIncrease}%%* per 100 units over.\"\n" +
                "\t},\n" +
                "\t\"HyperspecLPC\": {\n" +
                "\t\t\"description\": \"A cutting-edge LPC conversion device that consolidates all resources into a single wing LPC. The singular wing is greatly enhanced in speed, armor, and damage as a result, but takes significantly longer to create and repair.\",\n" +
                "\t\t\"tooltip\": \"Reduces the number of bays to 1. Buffs the remaining wing significantly based on how many bays were removed. Increases the replacement time for the fighter wing.\",\n" +
                "\t\t\"longDescription\": \"Reduces the number of bays to 1. Fighter non-missile damage is increased by *${fighterDamageIncrease}%%*, speed is increased by *${fighterSpeedIncrease}%%*, armor is increased by *${fighterArmorIncrease}%%*, flux is increased by *${fighterFluxIncrease}%%* and hull is increased by *${fighterHullIncrease}%%*. Replacement time is increased by =${replacementTimeIncrease}%%=.\"\n" +
                "\t},\n" +
                "\t\"PhasedFighterTether\": {\n" +
                "\t\t\"description\": \"Tri-Tachyon markets the technological marvel as a multidimensional web that rips open a hole between the fighter and its launch bay. The fighter is then violently sucked through the vortex, saving the hull from complete destruction. The only caveat is that the device can only work so fast, and is unable to keep the fighter in working condition if it is destroyed outright.\",\n" +
                "\t\t\"tooltip\": \"Fighters at low health are instantly pulled back to the ship's hangar bays. Damaged parts are pulled back as well and are used to repair fighters, increasing replacement rate.\",\n" +
                "\t\t\"needItem\": \"You need a ${itemName} to install this.\",\n" +
                "\t\t\"longDescription\": \"*Half* of the total number of fighters can be *teleported back to the ship* if they are at low health. This effect recharges every minute. Fighter refit time is reduced by *${fighterRefitTimeDecrease}%%*.\",\n" +
                "\t\t\"statusBarText\": \"TETHER\",\n" +
                "\t},\n" +
                "\t\"HackedMissileForge\": {\n" +
                "\t\t\"description\": \"Corrupting a hangar microforge to fabricate the most delicate components of missiles is possible, given a few engineers skilled with Domain technology. Their services don't come cheap, however, and the missiles the forge produces won't be as high quality.\",\n" +
                "\t\t\"tooltip\": \"Reloads all non-reloading missile's ammunition capacity periodically. Reduces missile damage.\",\n" +
                "\t\t\"needCredits\": \"You need ${credits} to install this.\",\n" +
                "\t\t\"needItem\": \"You need a ${itemName} to install this.\",\n" +
                "\t\t\"statusBarText\": \"M.FORGE\",\n" +
                "\t\t\"longDescription\": \"Reduces missile damage by =${damageDecrease}%%=. Reloads all of any non-reloading missile's ammunition capacity every *${reloadTime} seconds*. This only affects missiles with more than 1 ammo.\",\n" +
                "\t\t\"statusReloaded\": \"Reloaded weapons!\"\n" +
                "\t},\n" +
                "\t\"PhasefieldEngine\": {\n" +
                "\t\t\"description\": \"A Tri-Tachyon joint venture with Ko Combine to reduce catastrophic asteroid impacts, a phasefield engine can be used to generate a protective field around a ship. The phase field completely dissipates when the hull is destroyed, but the phase field can transport incoming projectiles into p-space. This protective ability fades quickly after exiting phase space, and rapid jumps to p-space take an increasingly large toll on the flux systems of a given ship.\",\n" +
                "\t\t\"tooltip\": \"Phase activation cost is reduced, but doubles every time you use it within a short time. The ship becomes very damage resistant for a short time when exiting phase. Ships with negative or zero flux cost will cost flux to phase.\",\n" +
                "\t\t\"needPhaseShip\": \"Only phase ships can install this.\",\n" +
                "\t\t\"needItem\": \"You need an ${itemName} to install this.\",\n" +
                "\t\t\"longDescription\": \"Phase activation cost is reduced by *${phaseCostReduction}x*, but =doubles= every time you use it in a period of *${phaseResetTime} seconds*. The ship receives *66%% reduced damage* which fades over time for *${noDamageTime} seconds* when exiting phase. Ships with a base negative phase flux cost will instead =cost flux=, and ships with a base zero phase flux cost will cost =${zeroFluxCost}%% of base flux capacity=.\",\n" +
                "\t\t\"statusBarText\": \"P.FIELD\",\n" +
                "\t\t\"statusTitle\": \"PHASEFIELD ENGINE\",\n" +
                "\t\t\"statusInvulnerable\": \"DMG RESIST INCREASED (${noDamageDuration})\",\n" +
                "\t\t\"statusPhasedTimes\": \"PHASED ${phasedTimes} TIMES, REFRESH IN ${refreshTime}\"\n" +
                "\t},\n" +
                "\t\"FullMetalSalvo\": {\n" +
                "\t\t\"description\": \"Overcharging weapon systems results in a massive increase to damage and projectile speeds, but the power such a system controller takes along with the potential damage from overcharging the power of weaponry causes a long term effect on the rate of fire of weapons on board the ship.\",\n" +
                "\t\t\"tooltip\": \"Adds an active ability that gives a massive damage and projectile speed boost for a very short period. Rate of fire on the ship is reduced permanently.\",\n" +
                "\t\t\"longDescription\": \"Adds an active ability that increases all weapon projectile speed by *${projSpeedBoost}%%* and non-missile damage by *${damageBoost}%%* for *${boostTime} seconds*. The ability takes *${cooldown} seconds* to recharge. Weapon fire rate is reduced by =${firerateMalus}%%= permanently.\",\n" +
                "\t\t\"systemText\": \"FULL METAL SALVO\",\n" +
                "\t},\n" +
                "\t\"TerminatorSubsystems\": {\n" +
                "\t\t\"description\": \"A squadron of terminator drones circles this ship, serving as both an effective point defense system, and as deadly weapons.\",\n" +
                "\t\t\"tooltip\": \"Terminator drones circle the ship, serving as point defense. They can be activated to convert into a seeking missile.\",\n" +
                "\t\t\"longDescription\": \"Spawns ${drones} Terminator drones (scales with hull size) that circle the ship, serving as point defense and passive weaponry. Adds an active ability that converts one of the drones into a seeking missile.\",\n" +
                "\t\t\"systemText\": \"TERMINATORS\",\n" +
                "\t\t\"outOfRange\": \"OUT OF RANGE\",\n" +
                "\t\t\"noFlux\": \"FLUX LEVELS TOO HIGH\"\n" +
                "\t},\n" +
                "\t\"TierIIIDriveSystem\": {\n" +
                "\t\t\"description\": \"Pressurized fuel tanks increase the rate of fuel injection to a custom burn drive, speeding up the entire fleet. A favorite among logistics fleets, the technology required to keep pressurized fuel stable requires almost all of the cargo space, but the technology scales up extremely well and allows for a large amount of fuel to be stored.\",\n" +
                "\t\t\"tooltip\": \"Replaces most of the ship's cargo capacity with fuel storage. Burn speed is increased while the fleet's fuel reserves are high.\",\n" +
                "\t\t\"longDescription\": \"Replaces =${cargoToFuelPercent}%% of cargo capacity= and converts it to *fuel storage*. If the fleet is above *${burnBonusFuelReq}%% fuel*, burn speed of the fleet is increased by *${burnBonus}*.\",\n" +
                "\t},\n" +
                "\t\"PenanceEngine\": {\n" +
                "\t\t\"description\": \"A rare artefact, granting its crew divine might if they are willing to sacrifice everything in the name of Ludd. Its powers shine true when among those who stand against it, granting a boon of strength and endurance to smite all that stand in its way.\",\n" +
                "\t\t\"tooltip\": \"Increases weapon repair rate and disables shields. Grants armor repair and increased speed in lateral directions while near enemy ships. Large amounts of damage will cause a massive EMP burst on the ship.\",\n" +
                "\t\t\"longDescription\": \"Increases weapon repair rate by *${weaponRepairRate}%%*, and =disables shields and phase systems=. When within 1200 units of an enemy ship, *armor repairs itself at a rate of ${armorRegenPerSec}%% per second per enemy ship* (up to *${armorRegenMax}%%*, max *${armorRegenPerSecondMax} per second*) and *${sideSpeedBoost} higher speed* when moving laterally (strafing). If =${damageThreshold} armor/hull damage= is taken within a short window, the ship will release a massive EMP burst onto itself, damaging most weapons and engines.\",\n" +
                "\t\t\"statusBarText\": \"LUDD\",\n" +
                "\t},\n" +
                "\t\"NanotechArmor\": {\n" +
                "\t\t\"description\": \"A swarm of nanobots constantly surrounds the armor. Nearly invisible to the naked eye, yet extremely effective at repairing weapons systems and even capable of ongoing repairs during combat.\",\n" +
                "\t\t\"tooltip\": \"Increases weapon repair rate and disables shields. Grants armor repair while near enemy ships. Large amounts of damage will cause a massive EMP burst on the ship.\",\n" +
                "\t\t\"longDescription\": \"Increases weapon repair rate by *${weaponRepairRate}%%*, and =disables shields and phase systems=. When within 1200 units of an enemy ship, *armor repairs itself at a rate of ${armorRegenPerSec}%% per second per enemy ship* (up to *${armorRegenMax}%%*, max *${armorRegenPerSecondMax} per second*). If =${damageThreshold} armor/hull damage= is taken within a short window, the ship will release a massive EMP burst onto itself, damaging most weapons and engines.\",\n" +
                "\t\t\"statusBarText\": \"NANO\",\n" +
                "\t},\n" +
                "\t\"ReactiveDamperField\": {\n" +
                "\t\t\"description\": \"A grid of sensors mostly found on Daemon-type ships, integrated with an artificial intelligence on board able to harden the ship's armor when detecting a threat severe enough to cause significant damage. The sheer amount of connections required between the intelligence and sensors weakens the armor significantly when not protected by this dampening field.\",\n" +
                "\t\t\"tooltip\": \"If taking damage over a certain amount, dampen the initial hit and all damage for a short period of time. This effect has a cooldown. Increases armor damage taken when the damper field is not active.\",\n" +
                "\t\t\"needItem\": \"You need a ${itemName} to install this.\",\n" +
                "\t\t\"longDescription\": \"Activates a damper field for *${damperDuration} seconds*, reducing all damage taken by *${damperReduction}%%*, when taking damage over *${triggeringDamage}*. It has a =${damperCooldown}= second cooldown. Armor damage taken is increased by =${armorDamageTaken}%%=.\",\n" +
                "\t\t\"statusBarText\": \"DAMPER\",\n" +
                "\t},\n" +
                "\t\"DaemonCore\": {\n" +
                "\t\t\"description\": \"A Daemon Core is far more efficient at all things warfare-related: ordnance calculations, ammunition placement, and diverting energy from non-efficient subsystems, such as reactive armor and shield emitters.\",\n" +
                "\t\t\"tooltip\": \"Increases usable bandwidth of the ship. Reduces ordnance point costs for weapons and fighters. Increases damage dealt and received.\",\n" +
                "\t\t\"needItem\": \"You need a ${itemName} to install this.\",\n" +
                "\t\t\"longDescription\": \"Increases bandwidth by *${bandwidthIncrease} TB/s*. Reduces ordnance point costs for weapons by *${smallReduction}/${medReduction}/${largeReduction}* based on weapon size, and for fighters and bombers by *${fghtrReduction}/${bmberReduction}*. Increases damage *dealt* and =received= by &${doubleEdge}%%&.\"\n" +
                "\t},\n" +
                "\t\"AnomalousConjuration\": {\n" +
                "\t\t\"description\": \"Impure.\",\n" +
                "\t\t\"tooltip\": \"Impure.\"\n" +
                "\t},\n" +
                "\t\"SubsumedAlphaCore\": {\n" +
                "\t\t\"description\": \"There's a faint trace of an Alpha-level AI coming from the ship. Whether it actually wants to be there requires a much closer look.\",\n" +
                "\t\t\"tooltip\": \"There's a faint trace of an Alpha-level AI coming from the ship. Whether it actually wants to be there requires a much closer look.\"\n" +
                "\t},\n" +
                "\t\"SubsumedBetaCore\": {\n" +
                "\t\t\"description\": \"There's a faint trace of an Beta-level AI coming from the ship. Whether it actually wants to be there requires a much closer look.\",\n" +
                "\t\t\"tooltip\": \"There's a faint trace of an Beta-level AI coming from the ship. Whether it actually wants to be there requires a much closer look.\"\n" +
                "\t},\n" +
                "\t\"SubsumedGammaCore\": {\n" +
                "\t\t\"description\": \"There's a faint trace of an Gamma-level AI coming from the ship. Whether it actually wants to be there requires a much closer look.\",\n" +
                "\t\t\"tooltip\": \"There's a faint trace of an Gamma-level AI coming from the ship. Whether it actually wants to be there requires a much closer look.\"\n" +
                "\t},\n" +
                "\t\"WeldedArmor\": {\n" +
                "\t\t\"description\": \"Improve hull and armor by welding additional armor plates, but reduce EMP resistance and weapon and engine health due to the additional conduits and weight.\"\n" +
                "\t},\n" +
                "\t\"PinataConfig\": {\n" +
                "\t\t\"description\": \"Strips off armor plating in exchange for a very thick coat of hull. The ship won't actually explode into confetti, but armor typically does a very good job at keeping a hull together.\"\n" +
                "\t},\n" +
                "\t\"InfernalEngines\": {\n" +
                "\t\t\"description\": \"Improve max speed, burn level, acceleration, max turn rate, and turn acceleration, but reduce deceleration and increase fuel use.\"\n" +
                "\t},\n" +
                "\t\"ForcedOvertime\": {\n" +
                "\t\t\"description\": \"Improve peak performance time and CR deployment cost by keeping non-essential crew ready at all times, but increase CR degradation rate and minimum crew, and decrease CR regeneration rate after combat. The improvements are stronger on smaller ship types.\"\n" +
                "\t},\n" +
                "\t\"CommissionedCrews\": {\n" +
                "\t\t\"description\": \"Improve CR per deployment, ship repair rate, CR recovery, fuel consumption and supply use, but increases the amount the required crew are paid each month, and the supplies the crew uses to recover. This shifts the logistical constraints of a ship from supplies and fuel to credits.\"\n" +
                "\t},\n" +
                "\t\"HyperactiveCapacitors\": {\n" +
                "\t\t\"description\": \"Improve flux capacity and vent speed, but increase the flux cost of weapons.\"\n" +
                "\t},\n" +
                "\t\"OverchargedShields\": {\n" +
                "\t\t\"description\": \"Improve shield efficiency, arc and unfold rate, but increase shield upkeep and decreases shield turn rate. Quickly enabling and disabling shields to block projectiles is greatly improved as a result.\"\n" +
                "\t},\n" +
                "\t\"TracerRecoilCalculator\": {\n" +
                "\t\t\"description\": \"Loading weapons with a special kind of tracer round allows this subsystem to make trajectory adjustments to reduce recoil and inaccuracy at the cost of weapon turn rate. The durability of the weapon is increased as a side effect.\"\n" +
                "\t},\n" +
                "\t\"OversizedMagazines\": {\n" +
                "\t\t\"description\": \"Ultra-sized magazines and compatible loaders offer more consistent firing rates for weaponry loaded through magazines, but the short-term damage burst from such weapons is reduced as a result.\"\n" +
                "\t},\n" +
                "\t\"AdvancedFluxCoils\": {\n" +
                "\t\t\"description\": \"Improve flux capacity, vent speed, and decrease the flux cost of weapons. The size of the new cabling required for the flux coils reduces the stability of the hull.\"\n" +
                "\t},\n" +
                "\t\"DerelictWeaponAssistant\": {\n" +
                "\t\t\"description\": \"Reduces recoil and inaccuracy, and increases projectile speed and ballistic weapon firerate.\"\n" +
                "\t},\n" +
                "\t\"AntimatterBoosters\": {\n" +
                "\t\t\"description\": \"Sindrian overrides on missile fabricators produce missiles with thrusters powered by tiny specks of antimatter. The new boosters are more resilient and grant increased speed, but are much less proficient at turning and tracking targets.\"\n" +
                "\t},\n" +
                "\t\"IronShell\": {\n" +
                "\t\t\"description\": \"Improvements and modifications are made to the armor to increase the space between individual plates of armor. This type of spaced armor is more effective versus high explosive damage, but doesn't fare as well against other kind of weaponry.\"\n" +
                "\t},\n" +
                "\t\"HelDrives\": {\n" +
                "\t\t\"description\": \"An engine upgrade package as often found on Daemon-type ships, utilising seemingly long lost Domain-era plasma injectors to supercharge a ship's drive system. While vastly improving any ship's agility in all regards, the induced stress exceeds the design limits of standard drive units, rendering them more susceptible to damage.\"\n" +
                "\t},\n" +
                "\t\"AuxiliarySensors\": {\n" +
                "\t\t\"description\": \"Increases sensor strength, but also increases sensor profile and minimum required crew to service the arrays.\"\n" +
                "\t},\n" +
                "\t\"InterferenceShielding\": {\n" +
                "\t\t\"description\": \"Decreases sensor profile significantly, but increases supply consumption.\"\n" +
                "\t},\n" +
                "\t\"AssaultWings\": {\n" +
                "\t\t\"description\": \"Fighters are given additional armoring and flux capacity, but the equipment puts an extra strain on how far away they can be from a carrier.\"\n" +
                "\t},\n" +
                "\t\"GuidanceComputers\": {\n" +
                "\t\t\"description\": \"Advanced guidance computers replace some of the payload of a missile, increasing its tracking ability and health but reducing damage dealt.\"\n" +
                "\t},\n" +
                "\t\"OverclockedFabricators\": {\n" +
                "\t\t\"description\": \"Increase replacement rate regeneration and decrease the rate at which it degrades, while also increasing fighter refit time. This makes fighter refit time more consistent, but slower at its fastest value.\"\n" +
                "\t},\n" +
                "\t\"VelocityInjectors\": {\n" +
                "\t\t\"description\": \"Fighters are refitted to have additional fuel injectors. These fuel injectors, when amassed, start to wear down the flux conduits on the ship, and the increased speed starts to take a toll on the hull.\"\n" +
                "\t},\n" +
                "\t\"InterceptionMatrix\": {\n" +
                "\t\t\"description\": \"Point defense weapons can be made to serve a much more active role in defending the ship by diverting power from the shields. This greatly increases their effectiveness, but only if a captain wants to trust their active defense systems more than their passive defense system.\"\n" +
                "\t},\n" +
                "\t\"FluxInductionDrive\": {\n" +
                "\t\t\"description\": \"This device can increase the efficiency of a drive by a significant margin, although it is extremely susceptible to interference from fields generated by flux conduits.\"\n" +
                "\t},\n" +
                "\t\"WaspDefenseDrones\": {\n" +
                "\t\t\"description\": \"Wasp defense drones surround the ship, protecting it.\",\n" +
                "\t\t\"tooltip\": \"*${drones}* Wasp drones circle the ship. One drone recharges every 20 seconds.\"\n" +
                "\t},\n" +
                "\t\"Kingslayer\": {\n" +
                "\t\t\"italics\": \"Cut them down to size.\",\n" +
                "\t\t\"description\": \"This ship poses an extreme threat to cruisers and capital ships.\",\n" +
                "\t\t\"tooltip\": \"*+${damageToCruisers}%%* damage to cruisers. *+${damageToCapitals}%%* damage to capital ships.\"\n" +
                "\t},\n" +
                "\t\"QuickJets\": {\n" +
                "\t\t\"description\": \"Installed auxiliary jets can be activated to provide an extreme boost to maneuverability for even the most sluggish ships.\",\n" +
                "\t\t\"tooltip\": \"Adds an ability that makes a ship turn faster. This includes a flat bonus to turn speed, so even slow ships will benefit greatly.\"\n" +
                "\t},\n" +
                "\t\"HegemonStrength\": {\n" +
                "\t\t\"description\": \"Stand for the Domain and you shall know strength like no other.\"\n" +
                "\t},\n" +
                "\t\"TechSupremacy\": {\n" +
                "\t\t\"description\": \"Our technology will leave the others in the dust, or rendered to dust.\"\n" +
                "\t},\n" +
                "\t\"KnightsShield\": {\n" +
                "\t\t\"description\": \"He protects us, guides us, and lends his might to us, for we are the devoted and loyal.\"\n" +
                "\t},\n" +
                "\t\"PerseanUnity\": {\n" +
                "\t\t\"description\": \"The common man rises above all. The despots will no longer hold their power over us.\"\n" +
                "\t},\n" +
                "\t\"ExoticTypes\": {\n" +
                "\t\t\"TooltipText\": \"Exotics of this type have a ^positive effect multiplier^ of *${positiveMult}x* and a =negative effect multiplier= of *${negativeMult}x*. This affects exotics in differing ways.\",\n" +
                "\t\t\"TooltipTextCondition\": \"Exotics of this type have a ^positive effect multiplier^ of *${positiveMult}x* and a =negative effect multiplier= of *${negativeMult}x*. This affects exotics in differing ways, and is strongest when *${condition}*.\",\n" +
                "\t\t\"ItemTypeText\": \"This chip is a *${typeName}-type*, which affects the exotic by ${typeDescription}.\",\n" +
                "\t\t\"NotInstalledText\": \"${typeName} - Hover for description\",\n" +
                "\t\t\"NORMAL\": \"${exoticName}\",\n" +
                "\t\t\"CORRUPTED\": \"Corrupted ${exoticName}\",\n" +
                "\t\t\"CorruptedItemDescription\": \"multiplying both its positive and negative effects\",\n" +
                "\t\t\"PURE\": \"Pure ${exoticName}\",\n" +
                "\t\t\"PureCondition\": \"the ship has no other upgrades or exotics\",\n" +
                "\t\t\"PureItemDescription\": \"increasing its positive effect if less bandwidth and exotics are used, and increasing its negative effect as more bandwidth and more exotics are installed\",\n" +
                "\t\t\"GUERILLA\": \"Guerrilla ${exoticName}\",\n" +
                "\t\t\"GuerillaCondition\": \"the fleet is below ${thresholdDP} deployment points. Your fleet is currently at ${fleetDP} deployment points\",\n" +
                "\t\t\"GuerillaItemDescription\": \"increasing its positive effects if the fleet is small, and increasing its negative effects if the fleet is large\"\n" +
                "\t},\n" +
                "    \"Conditions\": {\n" +
                "        \"CannotApplyTitle\": \"CANNOT INSTALL\",\n" +
                "        \"CannotApplyBecauseTags\": \"Conflicts with ${conflictMods}.\",\n" +
                "        \"CannotApplyBecauseBandwidth\": \"The ship does not have enough bandwidth.\",\n" +
                "        \"CannotApplyBecauseLevel\": \"The ship has the max level for this upgrade.\",\n" +
                "        \"CannotApplyBecauseTooManyExotics\": \"The ship has reached its capacity for exotics, which is 2, increased by 1 if the *Best of the Best* skill is unlocked.\"\n" +
                "    },\n" +
                "    \"Filters\": {\n" +
                "        \"durability\": \"Durability\",\n" +
                "        \"speed\": \"Speed\",\n" +
                "        \"flux\": \"Flux\",\n" +
                "        \"weapons\": \"Weapons\",\n" +
                "        \"Weapons\": \"Weapons\",\n" +
                "        \"phase\": \"Phase\",\n" +
                "        \"readiness\": \"Readiness\",\n" +
                "        \"logistics\": \"Logistics\",\n" +
                "        \"fighters\": \"Fighters\",\n" +
                "        \"Fighters\": \"Fighters\",\n" +
                "        \"drones\": \"Drones\",\n" +
                "        \"special\": \"Special\",\n" +
                "        \"Special\": \"Special\",\n" +
                "        \"upgrades\": \"Upgrades\",\n" +
                "        \"exotics\": \"Exotics\"\n" +
                "    },\n" +
                "    \"CombineChipsRecipe\": {\n" +
                "        \"Name\": \"Combine upgrade chips\",\n" +
                "        \"Description\": \"Combine two chips with the same upgrade and level to make one with a higher level.\",\n" +
                "        \"OutputName\": \"Upgrade Chip\",\n" +
                "        \"OutputDesc1\": \"Creates an upgrade chip of a higher level.\",\n" +
                "        \"OutputDesc2\": \"Lower chance for additional levels.\",\n" +
                "        \"OutputNameWithUpgrade\": \"Upgrade Chip - ${upgradeName}\",\n" +
                "    },\n" +
                "    \"ExoticTypeSwitchRecipe\": {\n" +
                "        \"Name\": \"Transfer exotic type\",\n" +
                "        \"Description\": \"Transfer the exotic type from the first exotic to the second exotic. Destroys the first exotic chip.\",\n" +
                "        \"OutputName\": \"Exotic Chip\",\n" +
                "        \"OutputDesc1\": \"Has the exotic type of the first chip.\",\n" +
                "        \"OutputDesc2\": \"Copies the exotic of the second chip.\",\n" +
                "        \"OutputNameWithType\": \"${exoticType} ${exoticName}\",\n" +
                "    },\n" +
                "\t\"ModEffects\": {\n" +
                "\t\t\"FighterStatName\": \"Fighter ${stat}\",\n" +
                "\t\t\"StatBenefit\": \"${stat}: *${percent}*\",\n" +
                "\t\t\"StatBenefitWithFinal\": \"${stat}: *${percent}* (*${finalValue}*)\",\n" +
                "\t\t\"StatBenefitInShop\": \"${stat}: *${percent}* (*${perLevel}*/lvl, max: ^${finalValue}^)\",\n" +
                "\t\t\"StatBenefitInShopMaxed\": \"${stat}: ^${percent}^\",\n" +
                "\t\t\"StatMalus\": \"${stat}: =${percent}=\",\n" +
                "\t\t\"StatMalusWithFinal\": \"${stat}: =${percent}= (=${finalValue}=)\",\n" +
                "\t\t\"StatMalusInShop\": \"${stat}: =${percent}= (=${perLevel}=/lvl, max: `${finalValue}`)\",\n" +
                "\t\t\"StatMalusInShopMaxed\": \"${stat}: `${percent}`\",\n" +
                "\t\t\"sensorStrength\": \"Sensor strength\",\n" +
                "\t\t\"sensorProfile\": \"Sensor profile\",\n" +
                "\t\t\"suppliesPerMonth\": \"Supply consumption\",\n" +
                "\t\t\"fuelUse\": \"Fuel consumption\",\n" +
                "\t\t\"suppliesToRecover\": \"Supplies to recover\",\n" +
                "\t\t\"minCrew\": \"Required crew\",\n" +
                "\t\t\"peakPerformanceTime\": \"Peak performance time\",\n" +
                "\t\t\"crToDeploy\": \"CR deployment cost\",\n" +
                "\t\t\"crLossRate\": \"CR degradation after PPT\",\n" +
                "\t\t\"crRecoveryRate\": \"CR recovery per day\",\n" +
                "\t\t\"repairRateAfterBattle\": \"Hull repair rate after battle\",\n" +
                "\t\t\"maxSpeed\": \"Maximum speed\",\n" +
                "\t\t\"acceleration\": \"Acceleration\",\n" +
                "\t\t\"deceleration\": \"Deceleration\",\n" +
                "\t\t\"turnRate\": \"Turn rate\",\n" +
                "\t\t\"burnLevel\": \"Burn level\",\n" +
                "\t\t\"engineHealth\": \"Engine durability\",\n" +
                "\t\t\"explosionRadius\": \"Explosion radius\",\n" +
                "\t\t\"shieldFluxDam\": \"Shield efficiency\",\n" +
                "\t\t\"shieldArc\": \"Shield arc\",\n" +
                "\t\t\"shieldUpkeep\": \"Shield upkeep\",\n" +
                "\t\t\"shieldUnfoldRate\": \"Shield unfold rate\",\n" +
                "\t\t\"shieldTurnRate\": \"Shield turn rate\",\n" +
                "\t\t\"fluxCapacity\": \"Flux capacity\",\n" +
                "\t\t\"fluxDissipation\": \"Flux dissipation\",\n" +
                "\t\t\"ventSpeed\": \"Active vent speed\",\n" +
                "\t\t\"hull\": \"Hull durability\",\n" +
                "\t\t\"armor\": \"Armor durability\",\n" +
                "\t\t\"empDamageTaken\": \"EMP damage taken\",\n" +
                "\t\t\"armorDamageTaken\": \"Armor damage taken\",\n" +
                "\t\t\"heDamageTaken\": \"HE damage taken\",\n" +
                "\t\t\"kineticDamageTaken\": \"Kinetic damage taken\",\n" +
                "\t\t\"energyDamageTaken\": \"Energy damage taken\",\n" +
                "\t\t\"fragDamageTaken\": \"Frag damage taken\",\n" +
                "\t\t\"maxRecoil\": \"Max recoil\",\n" +
                "\t\t\"recoilPerShot\": \"Recoil growth\",\n" +
                "\t\t\"projectileSpeed\": \"Projectile speed\",\n" +
                "\t\t\"weaponFireRate\": \"Weapon rate of fire\",\n" +
                "\t\t\"ballisticFireRate\": \"Ballistic rate of fire\",\n" +
                "\t\t\"energyFireRate\": \"Energy rate of fire\",\n" +
                "\t\t\"ballisticMagazines\": \"Ballistic magazine size\",\n" +
                "\t\t\"energyMagazines\": \"Energy magazine size\",\n" +
                "\t\t\"weaponHealth\": \"Weapon health\",\n" +
                "\t\t\"weaponTurnRate\": \"Weapon turn rate\",\n" +
                "\t\t\"weaponFluxCost\": \"Weapon flux costs\",\n" +
                "\t\t\"weaponMagazines\": \"Weapon magazine size\",\n" +
                "\t\t\"missileDamage\": \"Missile damage\",\n" +
                "\t\t\"missileHealth\": \"Missile health\",\n" +
                "\t\t\"missileSpeed\": \"Missile speed\",\n" +
                "\t\t\"missileTurnRate\": \"Missile turn rate\",\n" +
                "\t\t\"missileTurnAcceleration\": \"Missile turn acceleration\",\n" +
                "\t\t\"damageToFighters\": \"Damage to fighters\",\n" +
                "\t\t\"damageToMissiles\": \"Damage to missiles\",\n" +
                "\t\t\"fighterWingRange\": \"Fighter engagement range\",\n" +
                "\t\t\"fighterRefitTime\": \"Fighter refit time\",\n" +
                "\t\t\"replacementRateRegen\": \"Replacement rate regen\",\n" +
                "\t\t\"replacementRateDegen\": \"Replacement rate degen\",\n" +
                "\t\t\"zeroFluxSpeed\": \"Zero flux speed\",\n" +
                "\t\t\"pointDefenseDamage\": \"Point defense damage\",\n" +
                "\t\t\"crewSalary\": \"Crew salary increased to =${salaryIncrease} credits= for the required crew of this ship. The effective increased cost per month is =${finalValue} credits=.\",\n" +
                "\t\t\"crewSalaryShop\": \"Salary increased to =${salaryIncrease} credits= (=${perLevel}=/lvl) for the required crew of this ship. The effective increased cost per month is =${finalValue} credits=.\",\n" +
                "\t\t\"crewSalaryShopIronShell\": \"Salary increased to =${salaryIncrease} credits= (=${perLevel}=/lvl) for the required crew of this ship. The effective increased cost per month is =${finalValue} credits=. This is refundable under Hegemony tax regulation.\"\n" +
                "\t}\n" +
                "}";
    }
}
