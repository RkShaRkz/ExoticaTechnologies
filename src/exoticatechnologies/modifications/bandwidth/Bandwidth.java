package exoticatechnologies.modifications.bandwidth;

import com.fs.starfarer.api.Global;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j;
import org.lazywizard.lazylib.MathUtils;

import java.awt.Color;
import java.util.*;

@Log4j
@RequiredArgsConstructor
public enum Bandwidth {
    TERRIBLE(0f, "terrible", new Color(200,100,100), 50),
    CRUDE(50f, "crude", new Color(200,150,100), 60),
    POOR(75f, "poor", new Color(210,210,120), 95),
    NORMAL(95f, "normal", new Color(200,200,200), 150),
    GOOD(125f, "good", new Color(100,200,110), 110),
    SUPERIOR(150f, "superior", new Color(75,125,255), 16),
    PRISTINE(200f, "pristine", new Color(150,100,200), 8),
    ULTIMATE(250f, "ultimate", new Color(255,100,255), 4),
    DOMAIN(300f, "domain", new Color(200,255,255), 1);
    public static final String BANDWIDTH_RESOURCE = "Bandwidth";
    public static float MAX_BANDWIDTH = DOMAIN.bandwidth;
    private static Map<Float, Bandwidth> BANDWIDTH_MAP = null;
    private static List<Bandwidth> BANDWIDTH_LIST = Arrays.asList(values());
    @Getter private final float bandwidth;
    @Getter private final String key;
    @Getter private final Color color;
    @Getter private final int weight;

    public float getRandomInRange() {
        if(BANDWIDTH_LIST.indexOf(this) == BANDWIDTH_LIST.size() - 1) {
            return bandwidth;
        }
        float nextBandwidth = BANDWIDTH_LIST.get(BANDWIDTH_LIST.indexOf(this) + 1).getBandwidth();
        return Math.round(MathUtils.getRandomNumberInRange(bandwidth, nextBandwidth));
    }

    public static Bandwidth generate(long seed) {
        int highNumber = 0;
        for(Bandwidth b : values()) {
            highNumber += b.getWeight();
        }

        MathUtils.getRandom().setSeed(seed);
        int chosen = MathUtils.getRandomNumberInRange(0, highNumber);
        for(Bandwidth b : values()) {
            chosen -= b.getWeight();

            if(chosen <= 0) {
                return b;
            }
        }
        return DOMAIN;
    }

    public static Bandwidth generate(long seed, float mult) {
        int highNumber = 0;
        for(Bandwidth b : values()) {
            highNumber += b.getWeight();
        }

        MathUtils.getRandom().setSeed(seed);
        int chosen = (int) (MathUtils.getRandomNumberInRange(80 * (mult - 1), highNumber));
        for(Bandwidth b : values()) {
            chosen -= b.getWeight();

            if(chosen <= 0) {
                return b;
            }
        }
        return DOMAIN;
    }

    public static Map<Float, Bandwidth> getBandwidthMap() {
        if(BANDWIDTH_MAP == null) {
            BANDWIDTH_MAP = new LinkedHashMap<>();
            for(Bandwidth b : values()) {
                BANDWIDTH_MAP.put(b.getBandwidth(), b);
            }
        }
        return BANDWIDTH_MAP;
    }

    public static String getBandwidthName(float arg) {
        return Global.getSettings().getString("BandwidthName", getBandwidthDef(arg).getKey());
    }

    public static Color getBandwidthColor(float arg){
        return getBandwidthDef(arg).getColor();
    }

    private static Bandwidth getBandwidthDef(float bandwidth) {
        float winningBandwidth = 0f;
        Bandwidth returnedDef = TERRIBLE;

        for(Bandwidth b : values()) {
            float defBandwidth = b.getBandwidth();

            if(defBandwidth >= winningBandwidth && bandwidth >= defBandwidth) {
                returnedDef = b;
                winningBandwidth = defBandwidth;
            }
        }

        return returnedDef;
    }
}
