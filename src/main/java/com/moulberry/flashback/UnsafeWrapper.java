package com.moulberry.flashback;

import org.lwjgl.system.Pointer;
import org.lwjgl.system.jni.JNINativeInterface;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.LongPredicate;

public class UnsafeWrapper {

    public static final Unsafe UNSAFE;

    static {
        UNSAFE = getUnsafeInstance();
    }

    private static Unsafe getUnsafeInstance() {
        // Try getting theUnsafe
        try {
            final Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(null);
        } catch (Exception ignored) {}

        // Try searching for fields
        Field[] fields = Unsafe.class.getDeclaredFields();
        for (Field field : fields) {
            if (!field.getType().equals(Unsafe.class)) {
                continue;
            }

            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
                continue;
            }

            try {
                field.setAccessible(true);
                return (Unsafe) field.get(null);
            } catch (Exception ignored) {}
            break;
        }

        // Unavailable! :(
        throw new Error("Flashback requires sun.misc.Unsafe to be available.");
    }

}
