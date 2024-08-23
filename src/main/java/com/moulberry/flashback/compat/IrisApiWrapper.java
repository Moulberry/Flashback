package com.moulberry.flashback.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import org.jetbrains.annotations.Nullable;

public class IrisApiWrapper {

    public static boolean isIrisAvailable() {
        return FabricLoader.getInstance().isModLoaded("iris");
    }

    public static boolean isShaderPackInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

}
