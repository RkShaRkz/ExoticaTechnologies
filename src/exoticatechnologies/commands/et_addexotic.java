package exoticatechnologies.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import exoticatechnologies.modifications.exotics.Exotic;
import exoticatechnologies.modifications.exotics.ExoticsHandler;
import exoticatechnologies.modifications.exotics.types.ExoticType;
import exoticatechnologies.util.StacktraceUtils;
import exoticatechnologies.util.StringUtils;
import exoticatechnologies.util.Utilities;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class et_addexotic implements BaseCommand {
    @Override
    public CommandResult runCommand(@NotNull String argsString, CommandContext context) {
        if (context.isInCampaign() || context.isInMarket()) {
            String[] args = argsString.split(" ");
            String exoticKey = args[0];
            boolean containsValidExoticKey = ExoticsHandler.getExoticsKeys().contains(exoticKey);
            if (argsString.isEmpty() || !containsValidExoticKey) {
                // Tell the user exactly what went wrong
                if (argsString.isEmpty()) {
                    Console.showMessage("ERROR: Need to at least type in an exotic key following the command!");
                }
                if (!containsValidExoticKey) {
                    Console.showMessage("ERROR: Invalid exotic key '" + exoticKey + "' detected!");
                }

                // Remember to also add the new types to data/console/commands.csv
                printHelp();
                return CommandResult.BAD_SYNTAX;
            } else {
                try {
                    Exotic exotic = ExoticsHandler.EXOTICS.get(exoticKey);

                    ExoticType exoticType = ExoticType.Companion.getNORMAL();
                    if (args.length > 1) {
                        String exoticTypeString = args[1];
                        // Since the ExoticType keys are all uppercase, we need to ignore case while checking for user input
                        // or uppercase the user's input and then check whether the list contains it.
                        boolean containsValidExoticTypeKey = StringUtils.containsIgnoreCase(ExoticType.getTypesKeys(), exoticTypeString);
                        if (!containsValidExoticTypeKey) {
                            // Like earlier, tell the user what's wrong with his exotic type and fail
                            Console.showMessage("ERROR: Invalid exotic type key '" + exoticTypeString + "' detected!");
                            printHelp();
                            return CommandResult.BAD_SYNTAX;
                        }

                        exoticType = ExoticType.Companion.valueOf(exoticTypeString);
                    }

                    // If we already have a stack of these exotics, add one to it, otherwise create a new stack of these exotics
                    CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
                    CargoStackAPI stack = Utilities.getExoticChip(fleet.getCargo(), exotic.getKey(), exoticType.getNameKey());
                    if (stack != null) {
                        stack.add(1);
                    } else {
                        SpecialItemData data = exotic.getNewSpecialItemData(exoticType);
                        fleet.getCargo().addSpecial(data, 1);
                    }

                    return CommandResult.SUCCESS;
                } catch (Throwable ex) {
                    Console.showMessage("Caught exception " + ex + ", see starsector.log\nSTACKTRACE:\n" + StacktraceUtils.INSTANCE.unwindStacktrace(ex.getStackTrace(), true));
                    System.out.println(ex+"\n"+StacktraceUtils.INSTANCE.unwindStacktrace(ex.getStackTrace(), true));
                    return CommandResult.ERROR;
                }
            }
        } else {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
    }

    private void printHelp() {
        Console.showMessage("\nUsage:\net_addexotic <exoticId> [exoticType]\n\nSupported Exotics: "+ExoticsHandler.stringifyExotics()+"\n\nSupported Exotic Types: "+ExoticType.stringifyTypes());
    }
}
