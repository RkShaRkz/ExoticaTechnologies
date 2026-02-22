package exoticatechnologies.util;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import exoticatechnologies.util.AnonymousLogger;
import exoticatechnologies.util.FleetMemberUtils;
import org.apache.log4j.Level;

import java.util.*;

public class ShipStatsRegistry {
    // 1. The Storage
    private static final Map<FMAPIUUID, List<MutableShipStatsAPI>> globalStatsRegistry = new WeakHashMap<>();
    private static final Map<FleetMemberAPI, UUID> globalFMAPIUUIDmap = new WeakHashMap<>();

    // 2. The Logic
    public static List<MutableShipStatsAPI> getWholeShipsStatsFromSingleStats(MutableShipStatsAPI stats) {
        FleetMemberAPI member = stats.getFleetMember();
        FleetMemberAPI rootModuleMember = FleetMemberUtils.findMemberForStats(stats);    //this is root module
        AnonymousLogger.INSTANCE.log("[SHARK]\tgetWholeShipStatsFromSingleStats\tmember == rootModuleMember ? "+(member == rootModuleMember), Level.INFO);
        AnonymousLogger.INSTANCE.log("[SHARK]\tgetWholeShipStatsFromSingleStats\tmember: "+member, Level.INFO);
        AnonymousLogger.INSTANCE.log("[SHARK]\tgetWholeShipStatsFromSingleStats\trootModuleMember: "+rootModuleMember, Level.INFO);

        if (member == null) return Collections.singletonList(stats);

        // Reset session if this is the Main Hull (Root)
        if (stats == member.getStats()) {
            globalFMAPIUUIDmap.put(member, UUID.randomUUID());
        }

        UUID sessionID = globalFMAPIUUIDmap.get(member);
        if (sessionID == null) {
            sessionID = UUID.randomUUID();
            globalFMAPIUUIDmap.put(member, sessionID);
        }

        FMAPIUUID key = new FMAPIUUID(member, sessionID);

        if (!globalStatsRegistry.containsKey(key)) {
            globalStatsRegistry.put(key, new ArrayList<MutableShipStatsAPI>());
        }

        List<MutableShipStatsAPI> allStats = globalStatsRegistry.get(key);
        if (!allStats.contains(stats)) {
            allStats.add(stats);
        }

        return allStats;
    }

    // 3. The Key (Internal)
    public static class FMAPIUUID {
        public final FleetMemberAPI member;
        public final UUID uuid;
        public FMAPIUUID(FleetMemberAPI member, UUID uuid) { this.member = member; this.uuid = uuid; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof FMAPIUUID)) return false;
            FMAPIUUID that = (FMAPIUUID) o;
            return Objects.equals(member, that.member) && Objects.equals(uuid, that.uuid);
        }
        @Override public int hashCode() { return Objects.hash(member, uuid); }
    }
}
