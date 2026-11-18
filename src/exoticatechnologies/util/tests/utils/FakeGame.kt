package exoticatechnologies.util.tests.utils

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.SettingsAPI
import com.fs.starfarer.api.campaign.CampaignClockAPI
import com.fs.starfarer.api.campaign.SectorAPI
import exoticatechnologies.util.StarsectorAPIInteractor
import java.awt.Color
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Stands in for the running game in unit tests that go through code which reads [Global]:
 * constructing a [exoticatechnologies.modifications.ShipModifications] (its tooltip colours come
 * from the settings), the variant-tag cache (its lifetime is measured on the campaign clock), the
 * persistent-data provider (it reads the sector's persistent data).
 *
 * Call [install] before and [uninstall] after each test (`@Before` / `@After`). Both also switch
 * [StarsectorAPIInteractor] in and out of its test mode.
 *
 * What is installed:
 *
 * - a [SettingsAPI] that answers every call with a neutral default of the right type (false, 0,
 *   "", white, null). Settings are static configuration and nothing under test depends on a
 *   particular value, so this one is lenient on purpose.
 * - a [SectorAPI] that is strict: it answers only `getClock`, `getPersistentData` and
 *   `getPlayerFleet` (there is none) and fails loudly on anything else, so a test notices when
 *   the code under test starts to depend on more of the campaign than it is supposed to.
 * - a [CampaignClockAPI] on which no time ever passes, so nothing cached expires during a test.
 */
object FakeGame {

    /**
     * Installs the fake settings and sector into [Global] and turns the test mode of
     * [StarsectorAPIInteractor] on.
     *
     * @param isInRefitScreen what [StarsectorAPIInteractor] should report for "are we in the
     * refit (or fleet) screen". The variant-tag cache is only filled from tag reads when this is
     * false, so tests of the cache pass false and tests of the uncached path pass true.
     */
    fun install(isInRefitScreen: Boolean) {
        Global.setSettings(proxyOf(SettingsAPI::class.java, LenientHandler("FakeSettings")))
        Global.setSector(proxyOf(SectorAPI::class.java, SectorHandler()))
        StarsectorAPIInteractor.setWorkingInTestMode(true, isInRefitScreen)
    }

    /** Removes everything [install] put in place. */
    fun uninstall() {
        StarsectorAPIInteractor.setWorkingInTestMode(false, false)
        Global.setSector(null)
        Global.setSettings(null)
    }

    /**
     * Creates a proxy for one interface.
     *
     * @param type the interface to fake
     * @param handler answers the calls
     * @return a proxy implementing [type]
     */
    private fun <T> proxyOf(type: Class<T>, handler: InvocationHandler): T {
        return type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf<Class<*>>(type), handler))
    }

    /**
     * Answers the methods every object has, so that a fake can be put in a map or printed.
     *
     * @param proxy the proxy the call was made on
     * @param method the intercepted method
     * @param args the raw proxy arguments
     * @param name what `toString` should report
     * @return the answer when [method] is one of `equals` / `hashCode` / `toString`, else
     * [NOT_AN_OBJECT_METHOD]
     */
    private fun objectMethod(proxy: Any, method: Method, args: Array<out Any?>?, name: String): Any? {
        return when (method.name) {
            "equals" -> args != null && args.isNotEmpty() && proxy === args[0]
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> name
            else -> NOT_AN_OBJECT_METHOD
        }
    }

    /** Marker returned by [objectMethod] for every method that is not an Object method. */
    private val NOT_AN_OBJECT_METHOD = Any()

    /**
     * Answers every call with a neutral default chosen by the method's return type.
     *
     * @param name what `toString` should report
     */
    private class LenientHandler(private val name: String) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            val objectAnswer = objectMethod(proxy, method, args, name)
            if (objectAnswer !== NOT_AN_OBJECT_METHOD) {
                return objectAnswer
            }
            val returnType = method.returnType
            return when {
                returnType == java.lang.Boolean.TYPE -> false
                returnType == java.lang.Integer.TYPE -> 0
                returnType == java.lang.Long.TYPE -> 0L
                returnType == java.lang.Float.TYPE -> 0f
                returnType == java.lang.Double.TYPE -> 0.0
                returnType == String::class.java -> ""
                returnType == Color::class.java -> Color.WHITE
                else -> null
            }
        }
    }

    /** The fake sector: a clock and an empty persistent-data map, nothing else. */
    private class SectorHandler : InvocationHandler {
        private val clock: CampaignClockAPI = proxyOf(CampaignClockAPI::class.java, ClockHandler())
        private val persistentData: MutableMap<String, Any> = HashMap()

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            val objectAnswer = objectMethod(proxy, method, args, "FakeSector")
            if (objectAnswer !== NOT_AN_OBJECT_METHOD) {
                return objectAnswer
            }
            return when (method.name) {
                "getClock" -> clock
                "getPersistentData" -> persistentData
                // No player fleet: fleet scans find nothing, so a test only sees the variants and
                // members it built itself.
                "getPlayerFleet" -> null
                else -> throw UnsupportedOperationException(
                    "FakeGame's sector does not implement SectorAPI." + method.name
                            + "() - add it to SectorHandler if the code under test needs it"
                )
            }
        }
    }

    /** The fake campaign clock: it always shows the same instant. */
    private class ClockHandler : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            val objectAnswer = objectMethod(proxy, method, args, "FakeClock")
            if (objectAnswer !== NOT_AN_OBJECT_METHOD) {
                return objectAnswer
            }
            return when (method.name) {
                "getTimestamp" -> 0L
                "getElapsedDaysSince" -> 0f
                else -> throw UnsupportedOperationException(
                    "FakeGame's clock does not implement CampaignClockAPI." + method.name
                            + "() - add it to ClockHandler if the code under test needs it"
                )
            }
        }
    }
}
