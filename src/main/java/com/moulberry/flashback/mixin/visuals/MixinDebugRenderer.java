package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.FlashbackEntityHighlightDebugRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Objects;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {

    @Shadow
    @Final
    private List<DebugRenderer.SimpleDebugRenderer> renderers;

    @Inject(method = "refreshRendererList", at = @At("RETURN"))
    public void refreshRendererList(CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState == null) {
                return;
            }

            if (Flashback.isExporting() || !ReplayUI.isActive()) {
                return;
            }

            if (ReplayUI.selectedEntity != null) {
                this.renderers.add(new FlashbackEntityHighlightDebugRenderer(Minecraft.getInstance(), ReplayUI.selectedEntity, 0xFFFFFF00));
            }
            if (editorState.audioSourceEntity != null && !Objects.equals(editorState.audioSourceEntity, ReplayUI.selectedEntity)) {
                this.renderers.add(new FlashbackEntityHighlightDebugRenderer(Minecraft.getInstance(), editorState.audioSourceEntity, 0xFF00FFFF));
            }
        }
    }

}
