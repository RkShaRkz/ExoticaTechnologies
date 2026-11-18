package exoticatechnologies.util.tests.utils

import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Builds in-memory [FleetMemberAPI] instances for unit tests, without the game.
 *
 * Like [FakeVariantTree] these are [Proxy] instances: they answer the few methods the code under
 * test really uses and fail loudly on anything else, so they neither have to implement the whole
 * interface nor break when a game update adds methods to it.
 *
 * Supported methods: `getId`, `getShipName`, `getHullId`, `getVariant`, `setVariant`,
 * `getFleetData` (always null - the fake belongs to no fleet), plus `equals` / `hashCode` /
 * `toString`. Equality is identity, as for the real fleet member class.
 */
object FakeFleetMember {

    /** The hull id reported by fakes created without one. */
    const val DEFAULT_HULL_ID: String = "fake_hull"

    /**
     * Creates a fake fleet member.
     *
     * @param id the id returned by [FleetMemberAPI.getId]
     * @param shipName the ship name; a ship root has one, the transient station-module members
     * the refit screen creates do not (pass null for those)
     * @param variant the member's own variant
     * @return a new fake fleet member
     */
    fun member(id: String, shipName: String?, variant: ShipVariantAPI): FleetMemberAPI {
        return member(id, shipName, variant, DEFAULT_HULL_ID)
    }

    /**
     * Creates a fake fleet member with a specific hull id.
     *
     * @param id the id returned by [FleetMemberAPI.getId]
     * @param shipName the ship name, null for a transient station-module member
     * @param variant the member's own variant
     * @param hullId the id returned by [FleetMemberAPI.getHullId]
     * @return a new fake fleet member
     */
    fun member(id: String, shipName: String?, variant: ShipVariantAPI, hullId: String): FleetMemberAPI {
        return Proxy.newProxyInstance(
            FleetMemberAPI::class.java.classLoader,
            arrayOf<Class<*>>(FleetMemberAPI::class.java),
            FakeMemberHandler(id, shipName, hullId, variant)
        ) as FleetMemberAPI
    }

    /**
     * Answers the [FleetMemberAPI] calls of one fake member.
     *
     * @param id the member id
     * @param shipName the ship name, or null
     * @param hullId the hull id
     * @param variant the member's current variant; replaced by `setVariant`
     */
    private class FakeMemberHandler(
        private val id: String,
        private val shipName: String?,
        private val hullId: String,
        private var variant: ShipVariantAPI
    ) : InvocationHandler {

        /**
         * Answers one call made on the fake member.
         *
         * @param proxy the fake member the call was made on
         * @param method the intercepted method
         * @param args the arguments of the call, null when the method takes none
         * @return what the method returns
         */
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            return when (method.name) {
                "getId" -> id
                "getShipName" -> shipName
                "getHullId" -> hullId
                "getVariant" -> variant
                "setVariant" -> {
                    variant = firstArgument(args) as ShipVariantAPI
                    null
                }
                "getFleetData" -> null
                "equals" -> proxy === firstArgument(args)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "FakeFleetMember(" + id + ")"
                else -> throw UnsupportedOperationException(
                    "FakeFleetMember does not implement FleetMemberAPI." + method.name
                            + "() - add it to FakeMemberHandler if the code under test needs it"
                )
            }
        }

        /**
         * Picks the first argument of an intercepted call, failing clearly when it is missing.
         *
         * @param args the raw proxy arguments (null when the intercepted method takes none)
         * @return the first argument of the intercepted call
         */
        private fun firstArgument(args: Array<out Any?>?): Any? {
            if (args == null || args.isEmpty()) {
                throw IllegalArgumentException("expected at least one argument")
            }
            return args[0]
        }
    }
}
