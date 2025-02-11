package com.moulberry.flashback;

import com.moulberry.flashback.playback.ReplayServer;
import it.unimi.dsi.fastutil.objects.Object2FloatFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
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

    public static ClosestElement findClosest(float position, float leftPosition, float rightPosition, float threshold) {
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
        int numberStart = 0;
        int total = 0;

        char[] characters = string.toCharArray();
        for (int i = 0; i < characters.length; i++) {
            char c = characters[i];

            if (c == 't' || c == 's' || c == 'm' || c == 'h') {
                try {
                    double value = Double.parseDouble(string.substring(numberStart, i));
                    int multiplier = switch (c) {
                        case 't' -> 1;
                        case 's' -> SECOND_TO_TICKS;
                        case 'm' -> MINUTE_TO_TICKS;
                        case 'h' -> HOUR_TO_TICKS;
                        default -> throw new IllegalStateException("Unexpected value: " + c);
                    };
                    total += (int)(value * multiplier);
                    i += 1;
                    numberStart = i;
                } catch (NumberFormatException ignored) {}
            }
        }

        if (numberStart < characters.length) {
            try {
                double value = Double.parseDouble(string.substring(numberStart));
                total += (int)(value * SECOND_TO_TICKS);
            } catch (NumberFormatException ignored) {}
        }

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
            if (ticks % SECOND_TO_TICKS == 0) {
                int seconds = ticks / SECOND_TO_TICKS;
                builder.append(seconds).append('s');
            } else {
                double seconds = (double) ticks / SECOND_TO_TICKS;
                builder.append(seconds).append('s');
            }
        } else if (ticks > 0) {
            builder.append(ticks).append('t');
        }
    }

    public static boolean isComponentEmpty(Component component) {
        if (component == CommonComponents.EMPTY) {
            return true;
        }

        boolean isSelfEmpty = false;

        if (component.getContents() == PlainTextContents.EMPTY) {
            isSelfEmpty = true;
        } else if (component.getContents() instanceof PlainTextContents plainTextContents) {
            isSelfEmpty = ChatFormatting.stripFormatting(plainTextContents.text()).isEmpty();
        }

        if (isSelfEmpty) {
            for (Component sibling : component.getSiblings()) {
                if (!isComponentEmpty(sibling)) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    public static int exportSequenceCount = 1;
    public static String resolveFilenameTemplate(String template) {
        LocalDateTime dateTime = LocalDateTime.now();
        dateTime = dateTime.withNano(0);
        String date = dateTime.toLocalDate().toString();
        String time = dateTime.toLocalTime().toString();

        String replay = "unknown";
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
            replay = replayServer.getMetadata().name;
        }

        return template
            .replace("%date%", date)
            .replace("%time%", time)
            .replace("%replay%", replay)
            .replace("%seq%", ""+exportSequenceCount);
    }

}
