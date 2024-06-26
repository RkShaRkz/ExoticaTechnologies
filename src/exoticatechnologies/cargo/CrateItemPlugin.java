package exoticatechnologies.cargo;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exoticatechnologies.modifications.exotics.ExoticSpecialItemPlugin;
import exoticatechnologies.modifications.upgrades.UpgradeSpecialItemPlugin;
import exoticatechnologies.util.PluginStringifier;
import exoticatechnologies.util.StringUtils;
import exoticatechnologies.util.Utilities;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.*;

public class CrateItemPlugin extends BaseSpecialItemPlugin {
    private static final Color TEXT_COLOR = Misc.getTextColor();
    private static final Color UPGRADE_COLOR = new Color(118, 214, 217);
    private static final Color EXOTIC_COLOR = new Color(239, 218, 120);
    private static final Color OTHER_COLOR = new Color(144, 213, 122);

    private static final Logger log = Logger.getLogger(CrateItemPlugin.class);

    public CrateSpecialData getData() {
        return (CrateSpecialData) stack.getSpecialDataIfSpecial();
    }

    public CargoAPI getCargo() {
        CargoAPI globalCargo = CrateGlobalData.getInstance().getCargo();
        if (globalCargo.isEmpty()) {
            return getData().getCargo();
        }
        return globalCargo;
    }

    @Override
    public boolean hasRightClickAction() {
        return true;
    }

    @Override
    public boolean shouldRemoveOnRightClickAction() {
        return false;
    }

    @SneakyThrows
    @Override
    public void performRightClickAction() {
        //fix for campaign post-combat crash
        if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null) return;

        Global.getSector().addTransientScript(new CrateDisplayScript(stack.getCargo()));
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, CargoTransferHandlerAPI transferHandler, Object stackSource, boolean useGray) {
        float opad = 10f;

        tooltip.addTitle(getName());

        String design = getDesignType();
        Misc.addDesignTypePara(tooltip, design, opad);

        if (!spec.getDesc().isEmpty()) {
            Color c = Misc.getTextColor();
            if (useGray) c = Misc.getGrayColor();
            tooltip.addPara(spec.getDesc(), c, opad);
        }
        tooltip.addPara(StringUtils.getString("CrateText", "ContentsText"), Misc.getTextColor(), opad);

        List<CargoStackAPI> cargoStacks = getCargo().getStacksCopy();

        if (cargoStacks.isEmpty()) return;


        List<Pair<StringUtils.Translation, Color>> upgradeStrings = new ArrayList<>();
        List<Pair<StringUtils.Translation, Color>> exoticStrings = new ArrayList<>();
        List<Pair<String, Color>> otherStrings = new ArrayList<>();

        Iterator<CargoStackAPI> stackIterator = cargoStacks.iterator();
        while (stackIterator.hasNext()) {
            CargoStackAPI newStack = stackIterator.next();

            if (newStack.isSpecialStack()) {
                if (newStack.getPlugin() instanceof UpgradeSpecialItemPlugin) {
                    stackIterator.remove();

                    UpgradeSpecialItemPlugin newPlugin = (UpgradeSpecialItemPlugin) newStack.getPlugin();
                    int maxLevel = newPlugin.getUpgradeLevel();

                    Map<Integer, Integer> levelQuantities = new HashMap<>();
                    levelQuantities.put(((UpgradeSpecialItemPlugin) newStack.getPlugin()).getUpgradeLevel(), (int) newStack.getSize());

                    //FIXME RKZ added this - skip newPlugin nulls
                    if (newPlugin.getUpgrade() == null) {
                        log.error("CreateItemPlugin::createTooltip()\tProblematic area around plugin: "+newPlugin);
                        PluginStringifier.logPlugin(newPlugin, log);
                        continue;
                    }

                    //iterate through all stacks
                    while (stackIterator.hasNext()) {
                        CargoStackAPI upgStack = stackIterator.next();

                        if (upgStack.isSpecialStack()) {
                            if (upgStack.getPlugin() instanceof UpgradeSpecialItemPlugin) {

                                //find similar stacks
                                UpgradeSpecialItemPlugin upgPlugin = (UpgradeSpecialItemPlugin) upgStack.getPlugin();

                                // While this can be useful, the amount of spam it spits out makes it drown out
                                // the stuff that's *really* useful for debugging. We'll leave the super-useful stuff
                                // and comment out this informational overloading garbage
//                                PluginStringifier.logPlugins(upgPlugin, newPlugin, log);

                                // Since 'newPlugin' tends to be null, the following (old) approach can't really work
                                // due to throwing alot of NPEs. So since either the plugins or their upgrades end up
                                // being null, the whole equality/null-check was extracted into a utility method
//                                if (newPlugin.getUpgrade().equals(upgPlugin.getUpgrade())) {
                                if (Utilities.checkUpgradesForEquality(newPlugin, upgPlugin)) {

                                    //remove similar stacks so they don't get counted again
                                    stackIterator.remove();

                                    int level = upgPlugin.getUpgradeLevel();
                                    int quantity = (int) upgStack.getSize();
                                    if (levelQuantities.containsKey(level)) {
                                        quantity += levelQuantities.get(level);
                                    }

                                    levelQuantities.put(level, quantity);

                                    if (level > maxLevel) {
                                        maxLevel = level;
                                    }
                                }
                            }
                        }
                    }

                    stackIterator = cargoStacks.iterator(); //reset iterator

                    //create display string
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i <= maxLevel; i++) {
                        if (levelQuantities.containsKey(i)) {
                            sb.append(String.format("Lv%s", i));

                            int quantity = levelQuantities.get(i);
                            if (quantity > 1) {
                                sb.append(String.format("x%s", levelQuantities.get(i)));
                            }

                            if (i != maxLevel) {
                                sb.append(", ");
                            }
                        }
                    }

                    Pair<StringUtils.Translation, Color> disp = new Pair<>();
                    disp.one = StringUtils.getTranslation("CrateText","UpgradeText")
                            .format("upgradeItemName", newPlugin.getUpgrade().getName(), UPGRADE_COLOR)
                            .format("levelQuantities", sb.toString(), TEXT_COLOR);
                    disp.two = UPGRADE_COLOR;

                    upgradeStrings.add(disp);
                } else if (newStack.getPlugin() instanceof ExoticSpecialItemPlugin) {
                    stackIterator.remove();

                    ExoticSpecialItemPlugin newPlugin = (ExoticSpecialItemPlugin) newStack.getPlugin();
                    int quantity = (int) newStack.getSize();
                    //iterate through all stacks
                    while (stackIterator.hasNext()) {
                        CargoStackAPI exoStack = stackIterator.next();

                        if (exoStack.isSpecialStack()) {
                            if (exoStack.getPlugin() instanceof ExoticSpecialItemPlugin) {
                                //find similar stacks
                                ExoticSpecialItemPlugin exoPlugin = (ExoticSpecialItemPlugin) exoStack.getPlugin();
                                if (newPlugin.getExoticData().getExotic().equals(exoPlugin.getExoticData().getExotic())) {
                                    stackIterator.remove();
                                    quantity += exoStack.getSize();
                                }
                            }
                        }
                    }

                    stackIterator = cargoStacks.iterator(); //reset iterator

                    Pair<StringUtils.Translation, Color> disp = new Pair<>();
                    disp.one = StringUtils.getTranslation("CrateText", "ExoticText")
                            .format("exoticItemName", newPlugin.getExoticData().getExotic().getName(), EXOTIC_COLOR)
                            .format("quantity", quantity, TEXT_COLOR);
                    disp.two = EXOTIC_COLOR;

                    exoticStrings.add(disp);
                }
            } else {
                Pair<String, Color> disp = new Pair<>();
                disp.one = newStack.getDisplayName();
                disp.two = OTHER_COLOR;

                otherStrings.add(disp);
            }
        }

        for (Pair<StringUtils.Translation, Color> disp : upgradeStrings) {
            disp.one.addToTooltip(tooltip, 1);
        }
        for (Pair<StringUtils.Translation, Color> disp : exoticStrings) {
            disp.one.addToTooltip(tooltip, 1);
        }
        for (Pair<String, Color> disp : otherStrings) {
            tooltip.addPara(" " + disp.one, disp.two, 1);
        }
    }
}
