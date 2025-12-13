package exoticatechnologies.modifications.stats.impl.shipmisc

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.MutableStat
import exoticatechnologies.modifications.stats.UpgradeMutableStatEffect

class EccmChanceEffect : UpgradeMutableStatEffect() {
    override val key: String
        get() = "eccmChance"

    override fun getStat(stats: MutableShipStatsAPI): MutableStat {
        return stats.eccmChance
    }
}
