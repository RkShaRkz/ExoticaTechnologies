package exoticatechnologies.campaign.rulecmd

import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin
import com.fs.starfarer.api.util.Misc
import exoticatechnologies.util.AnonymousLogger
import exoticatechnologies.util.StarsectorAPIInteractor

class ETOnDockedListener : BaseCommandPlugin() {
    override fun execute(ruleId: String?, dialog: InteractionDialogAPI?, params: MutableList<Misc.Token>?, memoryMap: MutableMap<String, MemoryAPI>?): Boolean {
        AnonymousLogger.log("ETOnDockedListener::execute() called, reacting to player landing!", "ETOnDockedListener")

        return false
    }
}
