package exoticatechnologies.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lazywizard.console.commands.AddShip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class et_listmultimoduleships implements BaseCommand {
    @Override
    public CommandResult runCommand(@NotNull String argsString, CommandContext context) {
        // first, the 'should add ships' argument
        String[] argsArray = argsString.split(" ");
        boolean shouldAddShips = (argsArray.length == 1 && argsArray[0].equalsIgnoreCase("true"));
        if (context.isInCampaign() || context.isInMarket()) {
            List<String> variantIds = new ArrayList<String>(Global.getSettings().getAllVariantIds());
            Collections.sort(variantIds);

            int count = 0;
            Console.showMessage("Multimodule ship variants:");
            for (String variantId : variantIds) {
                ShipVariantAPI variant = Global.getSettings().getVariant(variantId);
                if (variant == null) continue;
                if (variant.getStationModules() == null || variant.getStationModules().isEmpty()) continue;

                ShipHullSpecAPI hullSpec = variant.getHullSpec();
                String hullId = hullSpec != null ? hullSpec.getHullId() : "<unknown>";
                Console.showMessage(String.format("%s -- %s", variant.getHullVariantId(), hullId));
                // if we should add these ships, just run the AddShip with it
                if (shouldAddShips) {
                    new AddShip().runCommand(variant.getHullVariantId(), context);
                }
                count++;
            }
            Console.showMessage(String.format("Total: %d", count));

            return CommandResult.SUCCESS;
        } else {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
    }
}
