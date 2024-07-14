package exoticatechnologies.util.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class JavaReflectionUtils {

    public static Object newArrayInstance(Class<?> clazz, int size) {
        try {
            Class<?> arrayClass = Class.forName("java.lang.reflect.Array", false, Class.class.getClassLoader());
            MethodHandle newInstanceHandle = MethodHandles.lookup()
                    .findStatic(arrayClass, "newInstance", MethodType.methodType(Object.class, Class.class, int.class));

            return newInstanceHandle.invoke(clazz, size);
        } catch (Exception e) {
            System.err.println("Exception " + e + " happened! message: " + e.getMessage());
        } catch (Throwable e) {
            System.err.println("Throwable " + e + " happened! message: " + e.getMessage());
        }

        System.err.println("Returning null because shit hit the fan");
        return null;
    }
}
