package com.moulberry.flashback;

import it.unimi.dsi.fastutil.objects.Object2FloatFunction;
import org.jetbrains.annotations.Nullable;

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

}
