package com.moulberry.flashback.spline;

import com.moulberry.flashback.Interpolation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class CatmullRom {

    private static float positionCentripetalTj(Vector3f p1, Vector3f p2) {
        float dx = p2.x - p1.x;
        float dy = p2.y - p1.y;
        float dz = p2.z - p1.z;
        return (float) Math.pow(dx*dx + dy*dy + dz*dz, 0.25f);
    }

    public static Vector3f position(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float time1, float time2, float time3, float amount) {
        float tj1 = positionCentripetalTj(p0, p1);
        float tj2 = positionCentripetalTj(p1, p2);
        float tj3 = positionCentripetalTj(p2, p3);
        float averageTj = (tj1 + tj2 + tj3) / 3f;
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

        Vector3f a1;
        if (t0 == t1) {
            a1 = p0.lerp(p1, 0.5f, new Vector3f());
        } else {
            a1 = p0.lerp(p1, (t - t0)/(t1 - t0), new Vector3f());
        }

        Vector3f a2;
        if (t1 == t2) {
            a2 = p1.lerp(p2, 0.5f, new Vector3f());
        } else {
            a2 = p1.lerp(p2, (t - t1)/(t2 - t1), new Vector3f());
        }

        Vector3f a3;
        if (t2 == t3) {
            a3 = p2.lerp(p3, 0.5f, new Vector3f());
        } else {
            a3 = p2.lerp(p3, (t - t2)/(t3 - t2), new Vector3f());
        }

        Vector3f b1;
        if (t0 == t2) {
            b1 = a1.lerp(a2, 0.5f, new Vector3f());
        } else {
            b1 = a1.lerp(a2, (t - t0)/(t2 - t0), new Vector3f());
        }

        Vector3f b2;
        if (t1 == t3) {
            b2 = a2.lerp(a3, 0.5f, new Vector3f());
        } else {
            b2 = a2.lerp(a3, (t - t1)/(t3 - t1), new Vector3f());
        }

        if (t1 == t2) {
            return b1.lerp(b2, 0.5f, new Vector3f());
        } else {
            return b1.lerp(b2, (t - t1)/(t2 - t1), new Vector3f());
        }
    }

    private static float rotationCentripetalTj(Quaternionf p1, Quaternionf p2) {
        Quaternionf delta = p1.invert(new Quaternionf()).mul(p2);

        float lengthSq = delta.x*delta.x + delta.y*delta.y + delta.z*delta.z;
        float length = (float) Math.sqrt(lengthSq);

        float angleBetween = (float) Math.abs(2 * Math.atan2(length, delta.w));
        angleBetween = (float) Math.max(0, Math.min(Math.PI, angleBetween));
        return (float) Math.pow(angleBetween, 0.5f);
    }

    public static Quaternionf rotation(Quaternionf p0, Quaternionf p1, Quaternionf p2, Quaternionf p3, float time1, float time2, float time3, float amount) {
        float tj1 = rotationCentripetalTj(p0, p1);
        float tj2 = rotationCentripetalTj(p1, p2);
        float tj3 = rotationCentripetalTj(p2, p3);
        float averageTj = (tj1 + tj2 + tj3) / 3f;
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

        Quaternionf a1;
        if (t0 == t1) {
            a1 = p0.nlerp(p1, 0.5f, new Quaternionf()).normalize();
        } else {
            a1 = p0.nlerp(p1, (t - t0)/(t1 - t0), new Quaternionf()).normalize();
        }

        Quaternionf a2;
        if (t1 == t2) {
            a2 = p1.nlerp(p2, 0.5f, new Quaternionf()).normalize();
        } else {
            a2 = p1.nlerp(p2, (t - t1)/(t2 - t1), new Quaternionf()).normalize();
        }

        Quaternionf a3;
        if (t2 == t3) {
            a3 = p2.nlerp(p3, 0.5f, new Quaternionf()).normalize();
        } else {
            a3 = p2.nlerp(p3, (t - t2)/(t3 - t2), new Quaternionf()).normalize();
        }

        Quaternionf b1;
        if (t0 == t2) {
            b1 = a1.nlerp(a2, 0.5f, new Quaternionf()).normalize();
        } else {
            b1 = a1.nlerp(a2, (t - t0)/(t2 - t0), new Quaternionf()).normalize();
        }

        Quaternionf b2;
        if (t1 == t3) {
            b2 = a2.nlerp(a3, 0.5f, new Quaternionf()).normalize();
        } else {
            b2 = a2.nlerp(a3, (t - t1)/(t3 - t1), new Quaternionf()).normalize();
        }

        if (t1 == t2) {
            return b1.nlerp(b2, 0.5f, new Quaternionf()).normalize();
        } else {
            return b1.nlerp(b2, (t - t1)/(t2 - t1), new Quaternionf()).normalize();
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
            b2 =Interpolation.linear(a2, a3, (t - t1)/(t3 - t1));
        }

        if (t1 == t2) {
            return Interpolation.linear(b1, b2, 0.5f);
        } else {
            return Interpolation.linear(b1, b2, (t - t1)/(t2 - t1));
        }
    }

}
