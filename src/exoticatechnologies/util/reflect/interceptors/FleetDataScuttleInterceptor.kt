package exoticatechnologies.util.reflect.interceptors

import exoticatechnologies.util.log
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import org.apache.log4j.Level
import org.apache.log4j.Logger

object FleetDataScuttleInterceptor {
    private val log = Logger.getLogger(FleetDataScuttleInterceptor::class.java)

    private fun stringifyMethodArray(methodArray: Array<out CtMethod>): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("[ \n")
        for (method in methodArray) {
            stringBuilder
                    .append(method.name).append("\t").append(method.signature).append("\n")
        }
        stringBuilder.append("]")

        return stringBuilder.toString()
    }

    fun setupInterceptor() {
        debugFleetDataMethods()
        debugScuttleHandlerMethods()
        try {
            log("Setting up FleetData scuttle interceptor...", log, Level.DEBUG)

            val pool = ClassPool.getDefault()
            val fleetDataClass: CtClass = pool.get("com.fs.starfarer.campaign.fleet.FleetData")

            // Get the scuttle method that takes FleetMemberAPI parameter
            val methods = fleetDataClass.declaredMethods
            log("declared methods on class: ${stringifyMethodArray(methods)}", log)
            val scuttleMethod: CtMethod = fleetDataClass.getDeclaredMethod(
                    "scuttle",
                    arrayOf(pool.get("com.fs.starfarer.api.fleet.FleetMemberAPI"))
//                    arrayOf(pool.get("com.fs.starfarer.campaign.fleet.FleetData"))
            )

            log("scuttleMethod: ${scuttleMethod}", log, Level.DEBUG)

            // Insert our custom logic at the beginning of the scuttle method
            // $1 refers to the first parameter (FleetMemberAPI)
            scuttleMethod.insertBefore(
                    """
                {
                    try {
                        exoticatechnologies.util.ScuttleHandler.INSTANCE.onPreScuttle($1);
                    } catch (Exception e) {
                        // Don't let our code break the game
                        System.err.println("Error in scuttle interceptor: " + e.getMessage());
                    }
                }
                """.trimIndent()
            )

            scuttleMethod.insertAfter(
                    """
                {
                    try {
                        exoticatechnologies.util.ScuttleHandler.INSTANCE.onPostScuttle($1);
                    } catch (Exception e) {
                        // Don't let our code break the game
                        System.err.println("Error in scuttle interceptor: " + e.getMessage());
                    }
                }
                """.trimIndent()
            )

            // Apply the transformation
            fleetDataClass.toClass()

            log("Successfully intercepted FleetData.scuttle(FleetMemberAPI) method", log, Level.DEBUG)

        } catch (e: Exception) {
            log("Failed to setup scuttle interceptor: ${e.message}", e, log, Level.ERROR)
            e.printStackTrace()
        }
    }

    fun debugFleetDataMethods() {
        log("--> debugFleetDataMethods()", log)
        try {
            val pool = ClassPool.getDefault()
            val fleetDataClass: CtClass = pool.get("com.fs.starfarer.campaign.fleet.FleetData")

            log("=== All methods in FleetData ===", log, Level.DEBUG)
            val methods = fleetDataClass.declaredMethods
            methods.forEach { method ->
                log("Method: ${method.name} - Signature: ${method.signature}", log, Level.DEBUG)
                log("\tParameter types: ${method.parameterTypes.joinToString { it.name }}", log, Level.DEBUG)
                log("\tReturn type: ${method.returnType.name}", log, Level.DEBUG)
            }

        } catch (e: Exception) {
            log("Failed to debug methods: ${e.message}", log, Level.ERROR)
        }
        log("<-- debugFleetDataMethods()", log)
    }

    fun debugScuttleHandlerMethods() {
        log("--> debugScuttleHandlerMethods()", log)
        try {
            val pool = ClassPool.getDefault()
            val fleetDataClass: CtClass = pool.get("exoticatechnologies.util.ScuttleHandler")

            log("=== All methods in ScuttleHandler ===", log, Level.DEBUG)
            val methods = fleetDataClass.declaredMethods
            methods.forEach { method ->
                log("Method: ${method.name} - Signature: ${method.signature}", log, Level.DEBUG)
                log("\tParameter types: ${method.parameterTypes.joinToString { it.name }}", log, Level.DEBUG)
                log("\tReturn type: ${method.returnType.name}", log, Level.DEBUG)
            }

        } catch (e: Exception) {
            log("Failed to debug methods: ${e.message}", log, Level.ERROR)
        }
        log("<-- debugScuttleHandlerMethods()", log)
    }
}
