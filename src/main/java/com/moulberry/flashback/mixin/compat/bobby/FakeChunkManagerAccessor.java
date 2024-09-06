package com.moulberry.flashback.mixin.compat.bobby;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import de.johni0702.minecraft.bobby.FakeChunkManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.jetbrains.annotations.Contract;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@IfModLoaded("bobby")
@Pseudo
@Mixin(value = FakeChunkManager.class, remap = false)
public interface FakeChunkManagerAccessor {

    @Invoker(value = "getCurrentWorldOrServerName")
    @Contract
    static String getCurrentWorldOrServerName(ClientPacketListener clientPacketListener) {
        throw new RuntimeException();
    }

}
