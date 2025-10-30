package exoticatechnologies.util;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class ScuttleAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(
                    ClassLoader loader,
                    String className,
                    Class<?> classBeingRedefined,
                    ProtectionDomain protectionDomain,
                    byte[] classfileBuffer
            ) {
                // Check for the specific class we want to transform
                if ("com/fs/starfarer/campaign/fleet/FleetData".equals(className)) {
                    AnonymousLogger.INSTANCE.log("[SHARK] found classname we were looking for!!! clasName:"+className);
                    try {
                        ClassPool pool = ClassPool.getDefault();
                        CtClass ctClass = pool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));
                        CtClass fleetMemberClass = pool.getCtClass("com.fs.starfarer.api.fleet.FleetMemberAPI");
                        CtClass methodParams[] = { fleetMemberClass };
                        CtMethod scuttleMethod = ctClass.getDeclaredMethod("scuttle", methodParams);

                        // Your insertion code here
                        scuttleMethod.insertBefore(
                            "{ exoticatechnologies.util.ScuttleHandler.INSTANCE.onPreScuttle($1); }".trim()
                        );

                        scuttleMethod.insertAfter(
                            "{ exoticatechnologies.util.ScuttleHandler.INSTANCE.onPostScuttle($1); }".trim()
                        );

                        byte[] byteCode = ctClass.toBytecode();
                        ctClass.detach();
                        return byteCode; // Return the modified bytecode
                    } catch (Exception e) {
                        AnonymousLogger.INSTANCE.log(
                                ">>> FAILED TO RETURN MODIFIED BYTECODE <<<\nexception stacktrace: " + StacktraceUtils.INSTANCE.unwindStacktraceFromException(e),
                                "ScuttleAgent"
                        );
                        e.printStackTrace();
                    }
                }
                return null; // Return null for classes you don't modify
            }
        });
    }
}
