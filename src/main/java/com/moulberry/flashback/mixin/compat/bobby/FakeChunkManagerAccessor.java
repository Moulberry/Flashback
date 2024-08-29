package com.moulberry.flashback.mixin.compat.bobby;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import de.johni0702.minecraft.bobby.FakeChunkManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@IfModLoaded("bobby")
@Mixin(FakeChunkManager.class)
public interface FakeChunkManagerAccessor {

    @Invoker(value = "getCurrentWorldOrServerName", remap = false)
    static String getCurrentWorldOrServerName(ClientPacketListener clientPacketListener) {
        throw new RuntimeException();
    }

}
