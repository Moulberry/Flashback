package com.moulberry.flashback;

public class FreezeSlowdownFormula {

    public static double calculateFreezeClientTickDerivative(double serverTicks, int frozenDelay, double freezePowerBase) {
        double freezePowerSq = freezePowerBase * freezePowerBase;
        double freezePower4 = freezePowerSq * freezePowerSq;
        return -4 * Math.log(freezePowerBase) * Math.pow(freezePowerBase, 4 * serverTicks / frozenDelay) / (frozenDelay - frozenDelay*freezePower4);
    }

    public static double calculateFreezeClientTick(double serverTicks, int frozenDelay, double freezePowerBase) {
        return (1 - Math.pow(freezePowerBase, 4 * serverTicks/frozenDelay)) / (1 - Math.pow(freezePowerBase, 4));
    }

    private static final double[] freezePowers = new double[11];

    public static double getFreezePowerBase(int freezeTicks, double targetDerivative) {
        if (freezeTicks < 0) {
            freezeTicks = 0;
        }
        if (freezeTicks > 10) {
            freezeTicks = 10;
        }

        double freezePowerBase = freezePowers[freezeTicks];
        if (freezePowerBase == 0) {
            freezePowerBase = calculateFreezePower(freezeTicks, targetDerivative);
            freezePowers[freezeTicks] = freezePowerBase;
        }
        return freezePowerBase;
    }

    private static double calculateFreezePower(int freezeTicks, double targetDerivative) {
        double freezePowerBaseMin = 0.05;
        double freezePowerBaseMax = 0.95;

        if (computeDerivativeAtZero(freezeTicks, freezePowerBaseMin) < targetDerivative) {
            return freezePowerBaseMin;
        }

        if (computeDerivativeAtZero(freezeTicks, freezePowerBaseMax) > targetDerivative) {
            return freezePowerBaseMax;
        }

        double freezePowerBaseMid = (freezePowerBaseMin + freezePowerBaseMax) / 2.0;

        for (int i = 0; i < 100; i++) {
            if (computeDerivativeAtZero(freezeTicks, freezePowerBaseMid) > targetDerivative) {
                freezePowerBaseMin = freezePowerBaseMid;
            } else {
                freezePowerBaseMax = freezePowerBaseMid;
            }
            freezePowerBaseMid = (freezePowerBaseMin + freezePowerBaseMax) / 2.0;
        }

        return freezePowerBaseMid;
    }

    private static double computeDerivativeAtZero(int freezeTicks, double freezePower) {
        double freezePowerSq = freezePower * freezePower;
        double freezePower4 = freezePowerSq * freezePowerSq;
        return -4 * Math.log(freezePower) / (freezeTicks - freezeTicks*freezePower4);
    }

}
