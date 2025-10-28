package exoticatechnologies.util.reflect

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.net.URL
import java.net.URLClassLoader

object ReflectionUtils {
    private val fieldClass = Class.forName("java.lang.reflect.Field", false, Class::class.java.classLoader)
    private val setFieldHandle = MethodHandles.lookup()
        .findVirtual(fieldClass, "set", MethodType.methodType(Void.TYPE, Any::class.java, Any::class.java))
    private val getFieldHandle =
        MethodHandles.lookup().findVirtual(fieldClass, "get", MethodType.methodType(Any::class.java, Any::class.java))
    private val getFieldNameHandle =
        MethodHandles.lookup().findVirtual(fieldClass, "getName", MethodType.methodType(String::class.java))
    private val setFieldAccessibleHandle = MethodHandles.lookup()
        .findVirtual(fieldClass, "setAccessible", MethodType.methodType(Void.TYPE, Boolean::class.javaPrimitiveType))
    private val getFieldTypeHandle = MethodHandles.lookup()
        .findVirtual(fieldClass, "getType", MethodType.methodType(Class::class.java))

    private val methodClass = Class.forName("java.lang.reflect.Method", false, Class::class.java.classLoader)
    private val getMethodNameHandle =
        MethodHandles.lookup().findVirtual(methodClass, "getName", MethodType.methodType(String::class.java))
    private val invokeMethodHandle = MethodHandles.lookup().findVirtual(
        methodClass,
        "invoke",
        MethodType.methodType(Any::class.java, Any::class.java, Array<Any>::class.java)
    )
    private val getMethodReturnHandle =
        MethodHandles.lookup().findVirtual(methodClass, "getReturnType", MethodType.methodType(Class::class.java))
    private val getMethodParametersHandle =
        MethodHandles.lookup()
            .findVirtual(methodClass, "getParameterTypes", MethodType.methodType(arrayOf<Class<*>>().javaClass))

    fun getMethodOfReturnType(instance: Any, clazz: Class<*>): String? {
        val instancesOfMethods: Array<out Any> = instance.javaClass.declaredMethods

        return instancesOfMethods.firstOrNull { getMethodReturnHandle.invoke(it) == clazz }
            ?.let { getMethodNameHandle.invoke(it) as String }
    }

    fun findFieldWithMethodName(instance: Any, methodName: String): ReflectedField? {
        val instancesOfFields: Array<out Any> = instance.javaClass.declaredFields

        return instancesOfFields
            .map { fieldObj -> fieldObj to getFieldTypeHandle.invoke(fieldObj) }
            .firstOrNull { (fieldObj, fieldClass) ->
                hasMethodOfNameInClass(methodName, fieldClass as Class<Any>)
            }
            ?.let { (fieldObj, fieldClass) ->
                return ReflectedField(fieldObj)
            }
    }

    fun getMethodArguments(method: String, instance: Any): Array<Class<*>>? {
        val instancesOfMethods: Array<out Any> = instance.javaClass.declaredMethods
        instancesOfMethods.firstOrNull { getMethodNameHandle.invoke(it) == method }?.let {
            return getMethodParametersHandle.invoke(it) as Array<Class<*>>
        }
        return null
    }

    fun findFieldWithMethodReturnType(instance: Any, clazz: Class<*>): ReflectedField? {
        val instancesOfFields: Array<out Any> = instance.javaClass.declaredFields

        return instancesOfFields
            .map { fieldObj -> fieldObj to getFieldTypeHandle.invoke(fieldObj) }
            .firstOrNull { (fieldObj, fieldClass) ->
                ((fieldClass!! as Class<Any>).declaredMethods as Array<Any>)
                    .any { methodObj -> getMethodReturnHandle.invoke(methodObj) == clazz }
            }?.let { (fieldObj, fieldClass) ->
                return ReflectedField(fieldObj)
            }
    }

    fun findFieldsOfType(instance: Any, clazz: Class<*>): List<ReflectedField> {
        val instancesOfFields: Array<out Any> = instance.javaClass.declaredFields

        return instancesOfFields
            .map { fieldObj -> fieldObj to getFieldTypeHandle.invoke(fieldObj) }
            .filter { (fieldObj, fieldClass) ->
                fieldClass == clazz
            }
            .map { (fieldObj, fieldClass) -> ReflectedField(fieldObj) }
    }

    fun set(fieldName: String, instanceToModify: Any, newValue: Any?) {
        var field: Any? = null
        try {
            field = instanceToModify.javaClass.getField(fieldName)
        } catch (e: Throwable) {
            try {
                field = instanceToModify.javaClass.getDeclaredField(fieldName)
            } catch (e: Throwable) {
            }
        }

        setFieldAccessibleHandle.invoke(field, true)
        setFieldHandle.invoke(field, instanceToModify, newValue)
    }

    fun get(fieldName: String, instanceToGetFrom: Any): Any? {
        var field: Any? = null
        try {
            field = instanceToGetFrom.javaClass.getField(fieldName)
        } catch (e: Throwable) {
            try {
                field = instanceToGetFrom.javaClass.getDeclaredField(fieldName)
            } catch (e: Throwable) {
            }
        }

        setFieldAccessibleHandle.invoke(field, true)
        return getFieldHandle.invoke(field, instanceToGetFrom)
    }

    fun hasMethodOfNameInClass(name: String, instance: Class<Any>, contains: Boolean = false): Boolean {
        val instancesOfMethods: Array<out Any> = instance.getDeclaredMethods()

        if (!contains) {
            return instancesOfMethods.any { getMethodNameHandle.invoke(it) == name }
        } else {
            return instancesOfMethods.any { (getMethodNameHandle.invoke(it) as String).contains(name) }
        }
    }

    fun hasMethodOfName(name: String, instance: Any, contains: Boolean = false): Boolean {
        val instancesOfMethods: Array<out Any> = instance.javaClass.getDeclaredMethods()

        if (!contains) {
            return instancesOfMethods.any { getMethodNameHandle.invoke(it) == name }
        } else {
            return instancesOfMethods.any { (getMethodNameHandle.invoke(it) as String).contains(name) }
        }
    }

    fun hasVariableOfName(name: String, instance: Any): Boolean {

        val instancesOfFields: Array<out Any> = instance.javaClass.getDeclaredFields()
        return instancesOfFields.any { getFieldNameHandle.invoke(it) == name }
    }

    fun instantiate(clazz: Class<*>, vararg arguments: Any?): Any? {
        val args = arguments.map { it!!::class.javaPrimitiveType ?: it!!::class.java }
        val methodType = MethodType.methodType(Void.TYPE, args)

        val constructorHandle = MethodHandles.lookup().findConstructor(clazz, methodType)
        val instance = constructorHandle.invokeWithArguments(arguments.toList())

        return instance
    }

    fun invoke(methodName: String, instance: Any, vararg arguments: Any?, declared: Boolean = false): Any? {
        var method: Any? = null

        val clazz = instance.javaClass
        val args = arguments.map { it!!::class.javaPrimitiveType ?: it::class.java }
        val methodType = MethodType.methodType(Void.TYPE, args)

        if (!declared) {
            method = clazz.getMethod(methodName, *methodType.parameterArray())
        } else {
            method = clazz.getDeclaredMethod(methodName, *methodType.parameterArray())
        }

        return invokeMethodHandle.invoke(method, instance, arguments)
    }

    fun getField(fieldName: String, instanceToGetFrom: Any): ReflectedField? {
        var field: Any? = null
        try {
            field = instanceToGetFrom.javaClass.getField(fieldName)
        } catch (e: Throwable) {
            try {
                field = instanceToGetFrom.javaClass.getDeclaredField(fieldName)
            } catch (e: Throwable) {
            }
        }

        if (field == null) return null

        return ReflectedField(field)
    }

    fun getMethod(methodName: String, instance: Any, vararg arguments: Any?): ReflectedMethod? {
        var method: Any? = null

        val clazz = instance.javaClass
        val args = arguments.map { it!!::class.javaPrimitiveType ?: it::class.java }
        val methodType = MethodType.methodType(Void.TYPE, args)

        try {
            method = clazz.getMethod(methodName, *methodType.parameterArray())
        } catch (e: Throwable) {
            try {
                method = clazz.getDeclaredMethod(methodName, *methodType.parameterArray())
            } catch (e: Throwable) {
            }
        }

        if (method == null) return null
        return ReflectedMethod(method)
    }

    fun getAllFields(instance: Any): List<ReflectedField> {
        val fields = mutableListOf<ReflectedField>()
        var currentClass: Class<*>? = instance.javaClass

        while (currentClass != null && currentClass != Any::class.java) {
            val instancesOfFields: Array<out Any> = currentClass.declaredFields
            instancesOfFields.mapTo(fields) { ReflectedField(it) }
            currentClass = currentClass.superclass
        }

        return fields
    }

    fun getAllFieldsMap(instance: Any): Map<String, ReflectedField> {
        val fieldMap = mutableMapOf<String, ReflectedField>()
        var currentClass: Class<*>? = instance.javaClass

        while (currentClass != null && currentClass != Any::class.java) {
            currentClass.declaredFields.forEach { field ->
                fieldMap[field.name] = ReflectedField(field)
            }
            currentClass = currentClass.superclass
        }

        return fieldMap
    }

    /**
     * A practical, generic "copy constructor" kind of method that takes all field values from [source] and copies
     * them into [destination]. Source and destination should be of the same type and contain the same fields.
     */
    fun copyAllFields(source: Any, destination: Any) {
        val sourceFields = getAllFields(source)

        for (sourceField in sourceFields) {
            try {
                // Get the field name to find the corresponding field in destination
                val fieldName = getFieldNameHandle.invoke(sourceField.field) as String

                // Try to find the same field in destination
                val destField = findFieldByName(destination, fieldName)
                if (destField != null) {
                    // Get value from source and set it in destination
                    val value = sourceField.get(source)
                    destField.set(destination, value)
                }
            } catch (e: Exception) {
                // Ignore fields that can't be copied (final fields, access issues, etc.)
                // You might want to log this in practice
            }
        }
    }

    /**
     * Copies all fields of the same name and type from [source] to [destinaton]
     *
     * @see copyAllFields
     */
    fun copyAllFieldsWithTypeCheck(source: Any, destination: Any) {
        val sourceFields = getAllFields(source)

        for (sourceField in sourceFields) {
            try {
                val fieldName = getFieldNameHandle.invoke(sourceField.field) as String
                val destField = findFieldByName(destination, fieldName)

                if (destField != null) {
                    // Get field types for type checking
                    val sourceType = getFieldTypeHandle.invoke(sourceField.field) as Class<*>
                    val destType = getFieldTypeHandle.invoke(destField.field) as Class<*>

                    // Only copy if types are compatible
                    if (destType.isAssignableFrom(sourceType)) {
                        val value = sourceField.get(source)
                        destField.set(destination, value)
                    }
                }
            } catch (e: Exception) {
                // Ignore fields that can't be copied
            }
        }
    }

    // Helper method to find a field by name in an object (including superclasses)
    private fun findFieldByName(instance: Any, fieldName: String): ReflectedField? {
        var currentClass: Class<*>? = instance.javaClass

        while (currentClass != null && currentClass != Any::class.java) {
            try {
                val field = currentClass.getDeclaredField(fieldName)
                return ReflectedField(field)
            } catch (e: NoSuchFieldException) {
                // Try superclass
                currentClass = currentClass.superclass
            }
        }
        return null
    }

    fun createClassThroughCustomLoader(claz: Class<*>): MethodHandle {
        var loader = this::class.java.classLoader
        val urls: Array<URL> = (loader as URLClassLoader).urLs
        val reflectionLoader: Class<*> = object : URLClassLoader(urls, ClassLoader.getSystemClassLoader()) {
        }.loadClass(claz.name)
        var handle = MethodHandles.lookup().findConstructor(reflectionLoader, MethodType.methodType(Void.TYPE))
        return handle
    }

    class ReflectedField(val field: Any) {
        fun get(instance: Any?): Any? {
            setFieldAccessibleHandle.invoke(field, true)
            return getFieldHandle.invoke(field, instance)
        }

        fun set(instance: Any?, value: Any?) {
            setFieldHandle.invoke(field, instance, value)
        }
    }

    class ReflectedMethod(val method: Any) {
        fun invoke(instance: Any?, vararg arguments: Any?): Any? =
            invokeMethodHandle.invoke(method, instance, arguments)
    }
}
