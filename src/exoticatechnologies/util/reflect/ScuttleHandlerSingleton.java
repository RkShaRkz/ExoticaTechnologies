package exoticatechnologies.util.reflect;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import exoticatechnologies.util.OnScuttleListener;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;

public class ScuttleHandlerSingleton implements OnScuttleListener {
    private final Logger logger = Logger.getLogger(ScuttleHandlerSingleton.class);
    private static volatile ScuttleHandlerSingleton instance;

    @Override
    public void onPreScuttle(@Nullable FleetMemberAPI fleetMember) {
        logger.info("--> onPreScuttle()");
    }

    @Override
    public void onPostScuttle(@Nullable FleetMemberAPI fleetMember) {
        logger.info("--> onPostScuttle()");
    }

    public static ScuttleHandlerSingleton getInstance() {
        if (instance == null) {
            synchronized (ScuttleHandlerSingleton.class) {
                if (instance == null) {
                    instance = new ScuttleHandlerSingleton();
                }
            }
        }

        return instance;
    }
}
