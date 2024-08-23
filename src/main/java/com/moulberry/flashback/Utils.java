package com.moulberry.flashback;

import it.unimi.dsi.fastutil.objects.Object2FloatFunction;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Random;

public class Utils {

    /*
     *  I love Utils.java, very cool, thank you
     */

    public enum ClosestElement {
        LEFT,
        RIGHT,
        NONE
    }

    public static <T> T chooseClosest(float position, float leftPosition, T left, float rightPosition, T right, float threshold) {
        if (left == null) {
            if (Math.abs(rightPosition - position) <= threshold) {
                return right;
            } else {
                return null;
            }
        }
        if (right == null) {
            if (Math.abs(leftPosition - position) <= threshold) {
                return left;
            } else {
                return null;
            }
        }

        return switch (findClosest(position, leftPosition, rightPosition, threshold)) {
            case LEFT -> left;
            case RIGHT -> right;
            case NONE -> null;
        };
    }

    public static Utils.ClosestElement findClosest(float position, float leftPosition, float rightPosition, float threshold) {
        boolean leftValid = Math.abs(leftPosition - position) <= threshold;
        boolean rightValid = Math.abs(rightPosition - position) <= threshold;

        if (leftValid && rightValid) {
            if (position <= leftPosition) {
                return ClosestElement.LEFT;
            } else {
                return ClosestElement.RIGHT;
            }
        } else if (leftValid) {
            return ClosestElement.LEFT;
        } else if (rightValid) {
            return ClosestElement.RIGHT;
        } else {
            return ClosestElement.NONE;
        }
    }

    public static float fovToFocalLength(float fov) {
        return 1.0f / (float) Math.tan(Math.toRadians(fov) * 0.5);
    }

    public static float focalLengthToFov(float focalLength) {
        return (float) Math.toDegrees(Math.atan(1.0 / focalLength) * 2.0f);
    }

    public static Random getInternalMathRandom() {
        for (Class<?> declaredClass : Math.class.getDeclaredClasses()) {
            try {
                if (declaredClass.getSimpleName().endsWith("RandomNumberGeneratorHolder")) {
                    Field field = declaredClass.getDeclaredField("randomNumberGenerator");

                    if (field.trySetAccessible()) {
                        return (Random) field.get(null);
                    } else {
                        return (Random) UnsafeWrapper.UNSAFE.getObject(UnsafeWrapper.UNSAFE.staticFieldBase(field),
                            UnsafeWrapper.UNSAFE.staticFieldOffset(field));
                    }
                }
            } catch (Exception e) {
                Flashback.LOGGER.error("Failed to get internal Math random", e);
            }
        }
        return null;
    }

    private static final int SECOND_TO_TICKS = 20;
    private static final int MINUTE_TO_TICKS = SECOND_TO_TICKS * 60;
    private static final int HOUR_TO_TICKS = MINUTE_TO_TICKS * 60;

    public static int stringToTime(String string) {
        int number = 0;
        int modifier = 1;
        int total = 0;

        for (char c : string.toCharArray()) {
            if (c >= '0' && c <= '9') {
                if (modifier > 1) {
                    total += number * modifier;
                    number = 0;
                    modifier = 1;
                }

                number *= 10;
                number += c - '0';
            } else if (c == 's') {
                modifier *= SECOND_TO_TICKS;
            } else if (c == 'm') {
                modifier *= MINUTE_TO_TICKS;
            } else if (c == 'h') {
                modifier *= HOUR_TO_TICKS;
            }
        }

        total += number * modifier;

        return total;
    }

    public static String timeToString(int ticks) {
        StringBuilder builder = new StringBuilder();
        timeToString(builder, ticks);
        if (builder.isEmpty()) {
            return "0s";
        }
        return builder.toString();
    }

    private static void timeToString(StringBuilder builder, int ticks) {
        if (ticks >= HOUR_TO_TICKS) {
            int hours = ticks / HOUR_TO_TICKS;
            builder.append(hours).append('h');
            timeToString(builder, ticks - hours*HOUR_TO_TICKS);
        } else if (ticks >= MINUTE_TO_TICKS) {
            int minutes = ticks / MINUTE_TO_TICKS;
            builder.append(minutes).append('m');
            timeToString(builder, ticks - minutes*MINUTE_TO_TICKS);
        } else if (ticks >= SECOND_TO_TICKS) {
            int seconds = ticks / SECOND_TO_TICKS;
            builder.append(seconds).append('s');
            timeToString(builder, ticks - seconds*SECOND_TO_TICKS);
        } else if (ticks > 0) {
            builder.append(ticks).append('t');
        }
    }

}
