package exoticatechnologies.util

import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import exoticatechnologies.campaign.listeners.CampaignEventListener.Companion.activeFleets
import exoticatechnologies.refit.RefitButtonAdder
import exoticatechnologies.refit.checkRefitVariant
import exoticatechnologies.util.FleetMemberUtils.findFleetForVariant
import org.apache.log4j.Level
import org.apache.log4j.Logger

object FleetMemberUtils {
    @JvmField
    val moduleMap: MutableMap<String, FleetMemberAPI> = HashMap()
    private val logger: Logger = Logger.getLogger(FleetMemberUtils::class.java)

    @JvmStatic
    fun findMemberFromShip(ship: ShipAPI): FleetMemberAPI? {
        val id = ship.variant.hullVariantId
        if (moduleMap.containsKey(id)) {
            return moduleMap[id]
        }
        if (ship.parentStation != null) {
            return findMemberFromShip(ship.parentStation)
        }
        return if (ship.fleetMember != null) {
            ship.fleetMember
        } else {
            findMemberForStats(ship.mutableStats)
        }
    }

    @JvmStatic
    fun findMemberForStats(stats: MutableShipStatsAPI): FleetMemberAPI? {
        val id = stats.variant.hullVariantId
        if (moduleMap.containsKey(id)) {
            return moduleMap[id]
        }

        // Leaf-module stats must resolve to the ROOT member, mirroring findMemberFromShip's
        // parentStation climb. Without this, module stats fall through to stats.fleetMember
        // (the module's own leaf FM), so getAllDataFromStatsAPI's variant-tree walk starts
        // from a leaf variant, never discovers sibling/root modules, and module-owned
        // exotics get skipped.
        // Synthetic statsForOpCosts objects (from propagateFromVariantTree)
        // have no entity and bypass this branch unchanged.
        if (stats.entity is ShipAPI) {
            val ship = stats.entity as ShipAPI
            if (ship.parentStation != null) {
                findMemberFromShip(ship.parentStation)?.let { return it }
            } else if (ship.fleetMember != null) {
                return ship.fleetMember
            }
        }

        if (stats.fleetMember != null) {
            return stats.fleetMember
        }

        //note: this looks awful, but it actually doesn't go into this loop all that often.
        for (fleet in activeFleets) {
            val fm = searchFleetForStats(fleet, stats)
            if (fm != null) {
                return fm
            }
        }
        return null
    }

    private fun searchFleetForStats(fleet: CampaignFleetAPI?, stats: MutableShipStatsAPI): FleetMemberAPI? {
        if (fleet == null) {
            return null
        }
        for (member in fleet.fleetData.membersListCopy) {
            if (member.isFighterWing) continue
            val memberStats = member.stats
            if (memberStats === stats) {
                return member
            } else if (stats.entity != null && memberStats.entity === stats.entity) {
                return member
            } else if (stats.fleetMember != null && stats.fleetMember === member) {
                return member
            } else if (stats.variant != null && member.variant === stats.variant) {
                return member
            } else {
                var opStats: MutableShipStatsAPI? = null
                try {
                    opStats = member.variant.statsForOpCosts
                } catch (ex: Throwable) {
                    // do nothing
                }
                if (opStats != null) {
                    if (opStats === stats) {
                        return member
                    } else if (stats.entity != null && opStats.entity === stats.entity) {
                        return member
                    } else if (stats.fleetMember != null && opStats.fleetMember === stats.fleetMember) {
                        return member
                    } else if (stats.variant != null && opStats.variant === stats.variant) {
                        return member
                    }
                }
            }
            val shipVariant = member.variant
            for (moduleVariantId in shipVariant.stationModules.keys) {
                val moduleVariant = shipVariant.getModuleVariant(moduleVariantId)
                var moduleStats: MutableShipStatsAPI?
                moduleStats = try {
                    moduleVariant.statsForOpCosts
                } catch (ex: Throwable) {
                    continue
                }
                if (moduleStats != null) {
                    if (moduleStats === stats) {
                        return member
                    } else if (stats.entity != null && stats.entity === moduleStats.entity) {
                        return member
                    } else if (stats.fleetMember != null && moduleStats.fleetMember === stats.fleetMember) {
                        return member
                    }
                }
            }
        }
        return null
    }

    fun findFleetForVariant(variant: ShipVariantAPI, member: FleetMemberAPI): CampaignFleetAPI? {
        val id = variant.hullVariantId
        if (moduleMap.containsKey(id)) {
            return moduleMap[id]?.fleetData?.fleet
        }

        member.fleetData?.fleet?.let {
            return it
        }

        member.fleetCommander?.fleet?.let {
            return it
        }

        member.captain?.fleet?.let {
            return it
        }

        return activeFleets //do not remove the filterNotNull
            .filterNotNull()
            .firstOrNull { fleet ->
            fleet.membersWithFightersCopy?.any { member ->
                member.variant.stationModules.keys.any {
                    member.variant.getModuleVariant(it) == variant }
            } ?: false
        }
    }

    /**
     * Gets commander from [member] by either doing [FleetMemberAPI.getFleetCommander]
     * and if that is null, it tries going through [CampaignFleetAPI]. Bails out if
     * [CampaignFleetAPI.getFleetData] is null to avoid NPE.
     *
     * @param member the member to look up the Fleet Commander for
     * @return the fleet commander if found (and has fleet data) or null
     */
    fun getFleetCommander(member: FleetMemberAPI): PersonAPI? {
        var fleetCommander = member.fleetCommander
        if (member.fleetCommander == null) {
            fleetCommander = if (member.fleetData != null) {
                member.fleetData.commander
            } else {
                // Since '.commander' or `.getCommander()` goes through fleet data, check again
//                findFleetForVariant(member.variant, member)?.commander
                // relevant code from CampaignFleetAPI:
                // public Person getCommander() {
                //     return this.fleetData.getCommander();
                // }
                val campaignFleet = findFleetForVariant(member.variant, member)

                if (campaignFleet?.fleetData != null) {
                    campaignFleet.commander
                } else {
                    null
                }
            }
        }

        return fleetCommander
    }

    /**
     * Resolves the [FleetMemberAPI] backing a child module [variant], or null when none is reachable.
     * [ShipVariantAPI.statsForOpCosts] can throw for variants the engine hasn't resolved yet, so it is
     * swallowed here; callers on the cold install/remove path never need to see it.
     */
    @JvmStatic
    fun findModuleMember(variant: ShipVariantAPI): FleetMemberAPI? {
        val stats = runCatching { variant.statsForOpCosts }.getOrNull() ?: return null
        return findMemberForStats(stats)
    }

    /**
     * Returns the root variant of the variant tree that [variant] belongs to, resolving via stable
     * hullVariantId keys only — identity comparisons break after fixVariant churn and refit cloning.
     *
     * A variant that owns station modules IS the root of its tree. Otherwise [variant] is a leaf
     * (child module or single-module ship): we scan the member's own variant tree first, then the
     * member's fleet, for a root whose station-module tree fuzzy-contains [variant]. Falls back to
     * [variant] itself when no owner can be found (single-module ship or unreachable tree).
     *
     * @param member the [FleetMemberAPI] to anchor the fleet scan on
     * @param variant the variant whose owning tree root we want
     * @return the root [ShipVariantAPI] of the tree containing [variant]
     */
    @JvmStatic
    fun findRootVariant(member: FleetMemberAPI, variant: ShipVariantAPI): ShipVariantAPI {
        // A root owns its station modules; a leaf with modules would itself be a root.
        if (variant.stationModules.isNotEmpty()) return variant
        val targetId = variant.hullVariantId

        // The member's own variant is the overwhelmingly common owner.
        val memberVariant = member.variant
        if (memberVariant.stationModules.isNotEmpty() && treeFuzzyContains(memberVariant, targetId)) {
            return memberVariant
        }

        // Fallback: scan the member's fleet for the owning tree (child FMs have null fleetData, so
        // also check the active campaign fleets like findRootVariantByHullId does).
        member.fleetData?.fleet?.let { fleet ->
            for (fm in fleet.membersWithFightersCopy) {
                val v = fm.variant
                if (v.stationModules.isNotEmpty() && treeFuzzyContains(v, targetId)) return v
            }
        }
        for (fleet in activeFleets) {
            if (fleet == null) continue
            for (fm in fleet.membersWithFightersCopy) {
                val v = fm.variant
                if (v.stationModules.isNotEmpty() && treeFuzzyContains(v, targetId)) return v
            }
        }
        return variant
    }

    // True when any station-module descendant of [root] fuzzy-matches [targetId] (same prefix before
    // the last '_suffix' — refit/combat clones differ only there, e.g. 'left_0' vs 'left_Start').
    private fun treeFuzzyContains(root: ShipVariantAPI, targetId: String): Boolean {
        var found = false
        root.forEachModuleVariant { childV ->
            if (!found && childV.hullVariantId.fuzzyVariantIdEquals(targetId)) found = true
        }
        return found
    }

    private fun String.fuzzyVariantIdEquals(other: String): Boolean {
        // Strip the last '_suffix' from both and compare the prefixes; ids without an underscore
        // degrade to a plain exact comparison (substringBeforeLast falls back to the whole string).
        return substringBeforeLast("_", this) == other.substringBeforeLast("_", other)
    }

    /**
     * Finds the [ShipVariantAPI] whose REFIT variant's hullVariantId equals [targetVariantId],
     * scanning the member's own fleet first, then falling back to the active campaign fleets.
     * Used to resolve a known root variant id (e.g. from [ModuleVariantHierarchy.findRootVariantId])
     * back to a concrete variant instance.
     *
     * @param member the [FleetMemberAPI] to anchor the fleet scan on
     * @param targetVariantId the exact hullVariantId to look up
     * @return the matching REFIT variant, or null if no fleet member carries it
     */
    @JvmStatic
    fun findRootVariantByHullId(member: FleetMemberAPI, targetVariantId: String): ShipVariantAPI? {
        member.fleetData?.fleet?.let { fleet ->
            for (fm in fleet.membersWithFightersCopy) {
                val v = fm.checkRefitVariant() ?: continue
                if (v.hullVariantId == targetVariantId) return v
            }
        }
        for (fleet in activeFleets) {
            if (fleet == null) continue
            for (fm in fleet.membersWithFightersCopy) {
                val v = fm.checkRefitVariant() ?: continue
                if (v.hullVariantId == targetVariantId) return v
            }
        }
        return null
    }

    /**
     * Resolves the root [FleetMemberAPI] of the module tree containing [member], used by whole-ship
     * (`installsOnWholeShip()`) install/remove flows so that bookkeeping always anchors on the ship's
     * root member regardless of which member entered the flow.
     * Returns [member] itself for single-module ships or when the root member cannot be reached,
     * preserving today's behavior in those cases.
     *
     * ## Stable markers, not variant identity
     *
     * ShipVariantAPI instances get recreated by Starsector across the stock -> REFIT -> combat clone
     * churn, so `===` on variants is NOT a dependable identity (see [checkRefitVariant]'s KDoc).
     * We therefore disambiguate with stable markers:
     *
     * - **Refit screen**: the refit edits exactly one ship at a time, so the last root
     *   [FleetMemberAPI] it displayed — [RefitButtonAdder.rootMember], cached from the display — is
     *   an unambiguous anchor for the ship being refitted, even when a transient station-module
     *   member entered the flow. FleetMember ids are stable across the clone churn.
     * - **Campaign/simulation**: variants do not churn there, so the candidate whose station-module
     *   tree instance-contains the triggering member's concrete variant is unambiguously the owner —
     *   ship B's tree never holds ship A's variant object — and the tie between identical hulls is
     *   broken deterministically.
     *
     * ## Refit screen fallback
     *
     * If the refit-root marker is not populated yet (refit cache empty on the very first frame),
     * we fall back to the first fuzzy-owner candidate, preserving today's behavior for the
     * single-ship case.
     */
    @JvmStatic
    fun findRootVariantMember(member: FleetMemberAPI): FleetMemberAPI {
        // A root owns its station modules; nothing to resolve.
        if (member.variant.stationModules.isNotEmpty()) return member

        val targetId = member.variant.hullVariantId
        val candidates = matchingRootMembers(member, targetId)

        log(
                "findRootVariantMember(): member=${member} (id=${member.id}, isChild=${member.shipName.isNullOrEmpty()})" +
                        ", targetId=${targetId}, refitScreen=${runningFromRefitScreen()}" +
                        ", refitRoot=${RefitButtonAdder.rootMember} (id=${RefitButtonAdder.rootMember?.id})" +
                        ", candidates=${candidates.map { it.id }}",
                logger, Level.WARN
        )

        // 1. Refit screen: the ship being refitted, anchored on the cached root FleetMember id (a
        //    stable marker, since the refit edits exactly one ship at a time). Resolves even when
        //    the entering member is a transient station-module member whose variant no candidate
        //    tree holds (the case that made instance-identity fail in the refit screen).
        if (runningFromRefitScreen()) {
            RefitButtonAdder.rootMember?.let {
                log("findRootVariantMember(): resolved 1-refit-root -> ${it.id}", logger, Level.WARN)
                return it
            }
        }

        // 2. The true owner in a stable (non-refit) graph: its tree holds the member's concrete
        //    variant instance. This is the only step that disambiguates two identical hulls there.
        candidates.firstOrNull { treeInstanceContains(it.variant, member.variant) }?.let {
            log("findRootVariantMember(): resolved 2-owner -> ${it.id}", logger, Level.WARN)
            return it
        }

        // 3. Status-quo: first fuzzy match (single ship -> exactly one candidate), else the old
        //    resolution.
        candidates.firstOrNull()?.let {
            log("findRootVariantMember(): resolved 3-first-match -> ${it.id}", logger, Level.WARN)
            return it
        }
        val rootVariant = findRootVariant(member, member.variant)
        if (rootVariant == member.variant) return member
        val fallback = findModuleMember(rootVariant) ?: member
        log("findRootVariantMember(): resolved 4-fallback -> ${fallback.id}", logger, Level.WARN)
        return fallback
    }

    /**
     * All root FMs whose station-module tree fuzzy-contains [childId], across the member's own fleet
     * and then the active campaign fleets. Unlike [findRootVariant] this does NOT stop at the first
     * match: for identical hulls several roots can fuzzy-match the same child id, and the tie must be
     * broken higher up (ownership/refit), never by scan order.
     */
    private fun matchingRootMembers(member: FleetMemberAPI, childId: String): List<FleetMemberAPI> {
        val result = LinkedHashSet<FleetMemberAPI>()
        member.fleetData?.fleet?.let { fleet ->
            for (fm in fleet.membersWithFightersCopy) {
                val v = fm.variant
                if (v.stationModules.isNotEmpty() && treeFuzzyContains(v, childId)) result.add(fm)
            }
        }
        for (fleet in activeFleets) {
            if (fleet == null) continue
            for (fm in fleet.membersWithFightersCopy) {
                val v = fm.variant
                if (v.stationModules.isNotEmpty() && treeFuzzyContains(v, childId)) result.add(fm)
            }
        }
        return result.toList()
    }

    /** True when any station-module descendant of [root] is exactly [child] (instance equality). */
    private fun treeInstanceContains(root: ShipVariantAPI, child: ShipVariantAPI): Boolean {
        var found = false
        root.forEachModuleVariant { node ->
            if (!found && node === child) found = true
        }
        return found
    }

}

/**
 * Walks the root variant's station module tree (via the shared [ShipVariantAPI.forEachModuleVariant]
 * walker), adding [hullmodId] to each child variant AND to each child's backing [FleetMemberAPI]
 * variant, resolved through [FleetMemberUtils.findModuleMember]. The refit screen reads each FM's
 * variant independently — tagging the root variant tree alone is invisible to child FM instances, so
 * the highlight must be mirrored onto both object graphs.
 *
 * @param hullmodId the hullmod id to add to every module of the ship
 */
fun FleetMemberAPI.propagateFromVariantTree(hullmodId: String) {
    this.variant.forEachModuleVariant { childV ->
        childV.addPermaMod(hullmodId)
        FleetMemberUtils.findModuleMember(childV)?.let { childFM ->
            childFM.variant.addPermaMod(hullmodId)
        }
    }
}

fun FleetMemberAPI.getFleetModuleSafe(): CampaignFleetAPI? {
    return findFleetForVariant(this.variant, this)
}
