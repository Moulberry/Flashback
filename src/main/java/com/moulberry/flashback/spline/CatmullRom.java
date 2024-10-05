package com.moulberry.flashback.spline;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.Interpolation;
import net.minecraft.util.Mth;
import org.joml.Vector3d;

public class CatmullRom {

    private static double positionCentripetalTj(Vector3d p1, Vector3d p2) {
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;
        double dz = p2.z - p1.z;
        return Math.pow(dx*dx + dy*dy + dz*dz, 0.25f);
    }

    public static Vector3d position(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3, float time1, float time2, float time3, float amount) {
        double tj1 = positionCentripetalTj(p0, p1);
        double tj2 = positionCentripetalTj(p1, p2);
        double tj3 = positionCentripetalTj(p2, p3);
        double averageTj = (tj1 + tj2 + tj3) / 3f;

        if (averageTj == 0.0) {
            return new Vector3d(p1);
        }

        double averageTime = time3 / 3f;
        double relation = averageTime / averageTj;

        double deltaTime1 = 0.0f;
        if (time1 > 0.0f) {
            double factor1 = (tj1 * relation) / time1;
            factor1 = Math.max(0.4f, Math.min(2.5f, factor1));
            deltaTime1 = (tj1 * relation) / factor1;
        }

        double deltaTime2 = 0.0f;
        if (time2 - time1 > 0.0f) {
            double factor2 = (tj2 * relation) / (time2 - time1);
            factor2 = Math.max(0.4f, Math.min(2.5f, factor2));
            deltaTime2 = (tj2 * relation) / factor2;
        }

        double deltaTime3 = 0.0f;
        if (time3 - time2 > 0.0f) {
            double factor3 = (tj3 * relation) / (time3 - time2);
            factor3 = Math.max(0.4f, Math.min(2.5f, factor3));
            deltaTime3 = (tj3 * relation) / factor3;
        }

        double t0 = 0;
        double t1 = t0 + deltaTime1;
        double t2 = t1 + deltaTime2;
        double t3 = t2 + deltaTime3;

        double t = t1 + (t2 - t1) * amount;

        Vector3d a1;
        if (t0 == t1) {
            a1 = p0.lerp(p1, 0.5f, new Vector3d());
        } else {
            a1 = p0.lerp(p1, (t - t0)/(t1 - t0), new Vector3d());
        }

        Vector3d a2;
        if (t1 == t2) {
            a2 = p1.lerp(p2, 0.5f, new Vector3d());
        } else {
            a2 = p1.lerp(p2, (t - t1)/(t2 - t1), new Vector3d());
        }

        Vector3d a3;
        if (t2 == t3) {
            a3 = p2.lerp(p3, 0.5f, new Vector3d());
        } else {
            a3 = p2.lerp(p3, (t - t2)/(t3 - t2), new Vector3d());
        }

        Vector3d b1;
        if (t0 == t2) {
            b1 = a1.lerp(a2, 0.5f, new Vector3d());
        } else {
            b1 = a1.lerp(a2, (t - t0)/(t2 - t0), new Vector3d());
        }

        Vector3d b2;
        if (t1 == t3) {
            b2 = a2.lerp(a3, 0.5f, new Vector3d());
        } else {
            b2 = a2.lerp(a3, (t - t1)/(t3 - t1), new Vector3d());
        }

        if (t1 == t2) {
            return b1.lerp(b2, 0.5f, new Vector3d());
        } else {
            return b1.lerp(b2, (t - t1)/(t2 - t1), new Vector3d());
        }
    }

    private static float valueCentripetalTj(float p1, float p2) {
        return (float) Math.sqrt(Math.abs(p1 - p2));
    }

    public static float value(float p0, float p1, float p2, float p3, float time1, float time2, float time3, float amount) {
        float tj1 = valueCentripetalTj(p0, p1);
        float tj2 = valueCentripetalTj(p1, p2);
        float tj3 = valueCentripetalTj(p2, p3);
        float averageTj = (tj1 + tj2 + tj3) / 3f;

        if (averageTj == 0.0) {
            return p1;
        }

        float averageTime = time3 / 3f;
        float relation = averageTime / averageTj;

        float deltaTime1 = 0.0f;
        if (time1 > 0.0f) {
            float factor1 = (tj1 * relation) / time1;
            factor1 = Math.max(0.4f, Math.min(2.5f, factor1));
            deltaTime1 = (tj1 * relation) / factor1;
        }

        float deltaTime2 = 0.0f;
        if (time2 - time1 > 0.0f) {
            float factor2 = (tj2 * relation) / (time2 - time1);
            factor2 = Math.max(0.4f, Math.min(2.5f, factor2));
            deltaTime2 = (tj2 * relation) / factor2;
        }

        float deltaTime3 = 0.0f;
        if (time3 - time2 > 0.0f) {
            float factor3 = (tj3 * relation) / (time3 - time2);
            factor3 = Math.max(0.4f, Math.min(2.5f, factor3));
            deltaTime3 = (tj3 * relation) / factor3;
        }

        float t0 = 0;
        float t1 = t0 + deltaTime1;
        float t2 = t1 + deltaTime2;
        float t3 = t2 + deltaTime3;

        float t = t1 + (t2 - t1) * amount;

        float a1;
        if (t0 == t1) {
            a1 = Interpolation.linear(p0, p1, 0.5f);
        } else {
            a1 = Interpolation.linear(p0, p1, (t - t0)/(t1 - t0));
        }

        float a2;
        if (t1 == t2) {
            a2 = Interpolation.linear(p1, p2, 0.5f);
        } else {
            a2 = Interpolation.linear(p1, p2, (t - t1)/(t2 - t1));
        }

        float a3;
        if (t2 == t3) {
            a3 = Interpolation.linear(p2, p3, 0.5f);
        } else {
            a3 = Interpolation.linear(p2, p3, (t - t2)/(t3 - t2));
        }

        float b1;
        if (t0 == t2) {
            b1 = Interpolation.linear(a1, a2, 0.5f);
        } else {
            b1 = Interpolation.linear(a1, a2, (t - t0)/(t2 - t0));
        }

        float b2;
        if (t1 == t3) {
            b2 = Interpolation.linear(a2, a3, 0.5f);
        } else {
            b2 = Interpolation.linear(a2, a3, (t - t1)/(t3 - t1));
        }

        if (t1 == t2) {
            return Interpolation.linear(b1, b2, 0.5f);
        } else {
            return Interpolation.linear(b1, b2, (t - t1)/(t2 - t1));
        }
    }

    private static float degreesCentripetalTj(float p1, float p2) {
        return (float) Math.sqrt(Math.abs(Mth.wrapDegrees(p1 - p2)));
    }

    public static float degrees(float p0, float p1, float p2, float p3, float time1, float time2, float time3, float amount) {
        float delta1 = Mth.wrapDegrees(p1 - p0);
        float delta2 = Mth.wrapDegrees(p2 - p1);
        float delta3 = Mth.wrapDegrees(p3 - p2);
        p0 = Mth.wrapDegrees(p0);
        p1 = p0 + delta1;
        p2 = p1 + delta2;
        p3 = p2 + delta3;

        float tj1 = degreesCentripetalTj(p0, p1);
        float tj2 = degreesCentripetalTj(p1, p2);
        float tj3 = degreesCentripetalTj(p2, p3);
        float averageTj = (tj1 + tj2 + tj3) / 3f;

        if (averageTj == 0.0) {
            return p1;
        }

        float averageTime = time3 / 3f;
        float relation = averageTime / averageTj;

        final float minFactor = 0.4f;
        final float maxFactor = 2.5f;

        float deltaTime1 = 0.0f;
        if (time1 > 0.0f) {
            float factor1 = (tj1 * relation) / time1;
            factor1 = Math.max(minFactor, Math.min(maxFactor, factor1));
            deltaTime1 = (tj1 * relation) / factor1;
        }

        float deltaTime2 = 0.0f;
        if (time2 - time1 > 0.0f) {
            float factor2 = (tj2 * relation) / (time2 - time1);
            factor2 = Math.max(minFactor, Math.min(maxFactor, factor2));
            deltaTime2 = (tj2 * relation) / factor2;
        }

        float deltaTime3 = 0.0f;
        if (time3 - time2 > 0.0f) {
            float factor3 = (tj3 * relation) / (time3 - time2);
            factor3 = Math.max(minFactor, Math.min(maxFactor, factor3));
            deltaTime3 = (tj3 * relation) / factor3;
        }

        float t0 = 0;
        float t1 = t0 + deltaTime1;
        float t2 = t1 + deltaTime2;
        float t3 = t2 + deltaTime3;

        float t = t1 + (t2 - t1) * amount;

        float a1;
        if (t0 == t1) {
            a1 = Interpolation.linear(p0, p1, 0.5f);
        } else {
            a1 = Interpolation.linear(p0, p1, (t - t0)/(t1 - t0));
        }

        float a2;
        if (t1 == t2) {
            a2 = Interpolation.linear(p1, p2, 0.5f);
        } else {
            a2 = Interpolation.linear(p1, p2, (t - t1)/(t2 - t1));
        }

        float a3;
        if (t2 == t3) {
            a3 = Interpolation.linear(p2, p3, 0.5f);
        } else {
            a3 = Interpolation.linear(p2, p3, (t - t2)/(t3 - t2));
        }

        float b1;
        if (t0 == t2) {
            b1 = Interpolation.linear(a1, a2, 0.5f);
        } else {
            b1 = Interpolation.linear(a1, a2, (t - t0)/(t2 - t0));
        }

        float b2;
        if (t1 == t3) {
            b2 = Interpolation.linear(a2, a3, 0.5f);
        } else {
            b2 = Interpolation.linear(a2, a3, (t - t1)/(t3 - t1));
        }

        return Interpolation.linear(b1, b2, amount);
    }

}
