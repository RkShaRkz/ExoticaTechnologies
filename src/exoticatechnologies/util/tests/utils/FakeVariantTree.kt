package exoticatechnologies.util.tests.utils

import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.loading.VariantSource
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Builds in-memory [ShipVariantAPI] trees for unit tests, without the game.
 *
 * The fakes are [Proxy] instances rather than anonymous `object : ShipVariantAPI` implementations
 * on purpose: [ShipVariantAPI] has well over a hundred methods and gains more with every game
 * version, so an anonymous implementation only compiles against the one API version it was
 * written for. A proxy answers the handful of methods the code under test really uses and fails
 * loudly on anything else, which also documents exactly what that code depends on.
 *
 * Supported methods: `getHullVariantId`, `getStationModules`, `getModuleVariant`,
 * `setModuleVariant`, `getTags`, `addTag`, `removeTag`, `hasTag`, `getSource`, plus
 * `equals` / `hashCode` / `toString`. Equality is identity, like the real variant class, so two
 * fakes with the same hullVariantId are still two different modules.
 */
object FakeVariantTree {

    /**
     * Creates a fake variant without station modules.
     *
     * @param hullVariantId the id returned by [ShipVariantAPI.getHullVariantId]
     * @return a new fake variant
     */
    fun variant(hullVariantId: String): ShipVariantAPI {
        return variant(hullVariantId, LinkedHashMap())
    }

    /**
     * Creates a fake variant that owns station modules.
     *
     * @param hullVariantId the id returned by [ShipVariantAPI.getHullVariantId]
     * @param modules slot id -> the module variant mounted in that slot, in slot order
     * @return a new fake variant
     */
    fun variant(hullVariantId: String, modules: Map<String, ShipVariantAPI>): ShipVariantAPI {
        val handler = FakeVariantHandler(hullVariantId, LinkedHashMap(modules))
        return Proxy.newProxyInstance(
            ShipVariantAPI::class.java.classLoader,
            arrayOf<Class<*>>(ShipVariantAPI::class.java),
            handler
        ) as ShipVariantAPI
    }

    /**
     * Creates a train-like ship: a root variant with the SAME module variant id mounted in every
     * one of [slotIds]. Each slot still gets its own module instance, as in the game.
     *
     * @param rootId the hullVariantId of the root variant
     * @param moduleId the hullVariantId shared by every module
     * @param slotIds the station-module slot ids, in slot order
     * @return the root variant of the new ship
     */
    fun shipWithIdenticalModules(rootId: String, moduleId: String, slotIds: List<String>): ShipVariantAPI {
        val modules = LinkedHashMap<String, ShipVariantAPI>()
        for (slotId in slotIds) {
            modules[slotId] = variant(moduleId)
        }
        return variant(rootId, modules)
    }

    /**
     * Creates an independent copy of a whole fake tree, the way the game clones a ship for the
     * refit screen: new instances everywhere, same slots, same tags.
     *
     * @param source the root of the tree to copy
     * @param renameId maps every hullVariantId of the source tree to the id its copy carries; use
     * it to imitate refit clones being renamed (`left_Start` -> `left_0`)
     * @return the root of the copy
     */
    fun copyOf(source: ShipVariantAPI, renameId: (String) -> String = { it }): ShipVariantAPI {
        val modules = LinkedHashMap<String, ShipVariantAPI>()
        for (slotId in source.stationModules.keys) {
            val child: ShipVariantAPI? = source.getModuleVariant(slotId)
            if (child != null) {
                modules[slotId] = copyOf(child, renameId)
            }
        }
        val copy = variant(renameId(source.hullVariantId), modules)
        for (tag in source.tags) {
            copy.addTag(tag)
        }
        return copy
    }

    /**
     * Answers the [ShipVariantAPI] calls of one fake variant.
     *
     * @param hullVariantId the id of the fake variant
     * @param modules slot id -> module variant mounted there
     */
    private class FakeVariantHandler(
        private val hullVariantId: String,
        private val modules: MutableMap<String, ShipVariantAPI>
    ) : InvocationHandler {
        private val tags: MutableCollection<String> = LinkedHashSet()

        /**
         * Answers one call made on the fake variant.
         *
         * @param proxy the fake variant the call was made on
         * @param method the intercepted method
         * @param args the arguments of the call, null when the method takes none
         * @return what the method returns
         */
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            return when (method.name) {
                "getHullVariantId" -> hullVariantId
                "getStationModules" -> stationModuleIds()
                "getModuleVariant" -> modules[argument(args, 0) as String]
                "setModuleVariant" -> {
                    modules[argument(args, 0) as String] = argument(args, 1) as ShipVariantAPI
                    null
                }
                "getTags" -> tags
                "addTag" -> {
                    tags.add(argument(args, 0) as String)
                    null
                }
                "removeTag" -> {
                    tags.remove(argument(args, 0) as String)
                    null
                }
                "hasTag" -> tags.contains(argument(args, 0) as String)
                // REFIT, so code that normalises stock variants (fixVariant) leaves the fake alone.
                "getSource" -> VariantSource.REFIT
                "equals" -> proxy === argument(args, 0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "FakeVariant(" + hullVariantId + ")@" + Integer.toHexString(System.identityHashCode(proxy))
                else -> throw UnsupportedOperationException(
                    "FakeVariantTree does not implement ShipVariantAPI." + method.name
                            + "() - add it to FakeVariantHandler if the code under test needs it"
                )
            }
        }

        /**
         * The game's `getStationModules()` maps slot id -> module variant id; rebuilt per call so
         * it always reflects the current modules.
         *
         * @return slot id -> hullVariantId of the module mounted there, in slot order
         */
        private fun stationModuleIds(): Map<String, String> {
            val ids = LinkedHashMap<String, String>()
            for ((slotId, module) in modules) {
                ids[slotId] = module.hullVariantId
            }
            return ids
        }

        /**
         * Picks one argument of an intercepted call, failing clearly when it is missing.
         *
         * @param args the raw proxy arguments (null when the intercepted method takes none)
         * @param index which argument to return
         * @return the argument at [index] of the intercepted call
         */
        private fun argument(args: Array<out Any?>?, index: Int): Any? {
            if (args == null || args.size <= index) {
                throw IllegalArgumentException("expected an argument at index " + index)
            }
            return args[index]
        }
    }
}
