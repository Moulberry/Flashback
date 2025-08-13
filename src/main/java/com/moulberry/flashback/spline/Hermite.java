package com.moulberry.flashback.spline;

import net.minecraft.util.Mth;
import org.apache.commons.math3.analysis.interpolation.HermiteInterpolator;
import org.joml.Vector3d;

import java.util.Map;

public class Hermite {

    public static Vector3d position(Map<Float, Vector3d> map, float amount) {
        HermiteInterpolator hermiteInterpolator = new HermiteInterpolator();

        double[] array = new double[3];
        for (Map.Entry<Float, Vector3d> entry : map.entrySet()) {
            array[0] = entry.getValue().x;
            array[1] = entry.getValue().y;
            array[2] = entry.getValue().z;

            hermiteInterpolator.addSamplePoint(entry.getKey(), array);
        }

        var values = hermiteInterpolator.value(amount);
        return new Vector3d(values[0], values[1], values[2]);
    }

    public static double value(Map<Float, Double> map, float amount) {
        HermiteInterpolator hermiteInterpolator = new HermiteInterpolator();

        double[] array = new double[1];
        for (Map.Entry<Float, Double> entry : map.entrySet()) {
            array[0] = entry.getValue();
            hermiteInterpolator.addSamplePoint(entry.getKey(), array);
        }

        return hermiteInterpolator.value(amount)[0];
    }

    public static double degrees(Map<Float, Double> map, float amount) {
        HermiteInterpolator hermiteInterpolator = new HermiteInterpolator();

        double lastAngle = 0.0;

        double[] array = new double[1];
        for (Map.Entry<Float, Double> entry : map.entrySet()) {
            double angle = lastAngle + Mth.wrapDegrees(entry.getValue() - lastAngle);
            array[0] = angle;

            hermiteInterpolator.addSamplePoint(entry.getKey(), array);

            lastAngle = angle;
        }

        return Mth.wrapDegrees(hermiteInterpolator.value(amount)[0]);
    }

}
