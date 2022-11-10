package exoticatechnologies.modifications.stats.impl.logistics

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.MutableStat
import exoticatechnologies.modifications.stats.UpgradeMutableStatEffect

class SuppliesToRecoverEffect : UpgradeMutableStatEffect() {
    override var negativeIsBuff: Boolean = true

    override val key: String
        get() = "suppliesToRecover"

    override fun getStat(stats: MutableShipStatsAPI): MutableStat {
        return stats.suppliesToRecover
    }
}