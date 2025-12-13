package exoticatechnologies.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import exoticatechnologies.modifications.exotics.Exotic;
import exoticatechnologies.modifications.exotics.ExoticsHandler;
import exoticatechnologies.modifications.exotics.types.ExoticType;
import exoticatechnologies.util.Utilities;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class et_additem implements BaseCommand {
    @Override
    public CommandResult runCommand(String argsString, CommandContext context) {
        if (context.isInCampaign() || context.isInMarket()) {
            String[] args = argsString.split(" ");
            if (args.length < 1) {
                Console.showMessage("et_additem <itemId> [quantity (as number)]");
                return CommandResult.BAD_SYNTAX;
            }

            try {
                String itemKey = args[0];
                String quantityString;
                if (args.length > 1) {
                    quantityString = args[1];
                } else {
                    quantityString = "1";
                }

                int quantity = Integer.parseInt(quantityString);
                CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

                Utilities.addItem(fleet, itemKey, quantity);

                return CommandResult.SUCCESS;
            } catch (Throwable ex) {
                Console.showMessage("Caught exception, see starsector.log");
                ex.printStackTrace();
                return CommandResult.ERROR;
            }
        } else {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
    }
}
