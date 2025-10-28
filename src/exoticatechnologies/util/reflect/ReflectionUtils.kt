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
//            currentClass.declaredFields.forEach { field: Any ->
//                val fieldName = 
//                fieldMap[field.name] = ReflectedField(field)
//            }
            val instancesOfFields: Array<out Any> = currentClass.getDeclaredFields()
            for (i in instancesOfFields.indices) {
                val fieldName = getFieldNameHandle.invoke(instancesOfFields[i]).toString()
                fieldMap[fieldName] = ReflectedField(instancesOfFields[i])
            }
            currentClass = currentClass.superclass
        }
        return fieldMap
    }

    // Get field map for a specific class (useful when you don't have an instance)
    fun getAllFieldsMap(clazz: Class<*>): Map<String, ReflectedField> {
        val fieldMap = mutableMapOf<String, ReflectedField>()
        var currentClass: Class<*>? = clazz

        while (currentClass != null && currentClass != Any::class.java) {
//            currentClass.declaredFields.forEach { field ->
//                fieldMap[field.name] = ReflectedField(field)
//            }
            val instancesOfFields: Array<out Any> = currentClass.getDeclaredFields()
            for (i in instancesOfFields.indices) {
                val fieldName = getFieldNameHandle.invoke(instancesOfFields[i]).toString()
                fieldMap[fieldName] = ReflectedField(instancesOfFields[i])
            }
            currentClass = currentClass.superclass
        }

        return fieldMap
    }

    // Copy only specific fields
    fun copySelectedFields(source: Any, destination: Any, fieldNames: Set<String>) {
        val sourceFields = getAllFieldsMap(source)
        val destFields = getAllFieldsMap(destination)

        for (fieldName in fieldNames) {
            try {
                val sourceField = sourceFields[fieldName]
                val destField = destFields[fieldName]
                if (sourceField != null && destField != null) {
                    val value = sourceField.get(source)
                    destField.set(destination, value)
                }
            } catch (e: Exception) {
                // Ignore fields that can't be copied
            }
        }
    }

    // Check if two objects have compatible field structures
    fun haveCompatibleFields(obj1: Any, obj2: Any): Boolean {
        val fields1 = getAllFieldsMap(obj1).keys
        val fields2 = getAllFieldsMap(obj2).keys
        return fields1 == fields2
    }

    /**
     * A practical, generic "copy constructor" kind of method that takes all field values from [source] and copies
     * them into [destination]. Source and destination should be of the same type and contain the same fields.
     */
    fun copyAllFields(source: Any, destination: Any) {
        val sourceFields = getAllFieldsMap(source)
        val destFields = getAllFieldsMap(destination)

        for ((fieldName, sourceField) in sourceFields) {
            try {
                val destField = destFields[fieldName]
                if (destField != null) {
                    val value = sourceField.get(source)
                    destField.set(destination, value)
                }
            } catch (e: Exception) {
                // Ignore fields that can't be copied
            }
        }
    }

    /**
     * Copies all fields of the same name and type from [source] to [destinaton]
     *
     * @see copyAllFields
     */
    fun copyAllFieldsWithTypeCheck(source: Any, destination: Any) {
        val sourceFields = getAllFieldsMap(source)
        val destFields = getAllFieldsMap(destination)

        for ((fieldName, sourceField) in sourceFields) {
            try {
                val destField = destFields[fieldName]
                if (destField != null) {
                    // Get field types for type checking
                    val sourceType = getFieldTypeHandle.invoke(sourceField.field) as Class<*>
                    val destType = getFieldTypeHandle.invoke(destField.field) as Class<*>

                    // Check if types are compatible
                    if (isAssignableFrom(destType, sourceType)) {
                        val value = sourceField.get(source)
                        destField.set(destination, value)
                    }
                    // Optional: log or handle incompatible types
                }
            } catch (e: Exception) {
                // Ignore fields that can't be copied (final fields, access issues, etc.)
            }
        }
    }

    // Helper method to handle type compatibility checking
    private fun isAssignableFrom(destType: Class<*>, sourceType: Class<*>): Boolean {
        // Handle primitive type compatibility
        if (destType.isPrimitive && sourceType.isPrimitive) {
            return destType == sourceType
        } else if (destType.isPrimitive) {
            // Check if source type is wrapper for dest primitive
            return primitiveToWrapperMap[destType] == sourceType
        } else if (sourceType.isPrimitive) {
            // Check if dest type is wrapper for source primitive
            return primitiveToWrapperMap[sourceType] == destType
        } else {
            // Regular class hierarchy check
            return destType.isAssignableFrom(sourceType)
        }
    }

    // Map for primitive to wrapper class conversions
    private val primitiveToWrapperMap: Map<Class<out Any>?, Class<out Any>> = mapOf(
            Boolean::class.javaPrimitiveType to Boolean::class.java,
            Byte::class.javaPrimitiveType to Byte::class.java,
            Char::class.javaPrimitiveType to Char::class.java,
            Short::class.javaPrimitiveType to Short::class.java,
            Int::class.javaPrimitiveType to Int::class.java,
            Long::class.javaPrimitiveType to Long::class.java,
            Float::class.javaPrimitiveType to Float::class.java,
            Double::class.javaPrimitiveType to Double::class.java,
            Void::class.javaPrimitiveType to Void::class.java
    )

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
