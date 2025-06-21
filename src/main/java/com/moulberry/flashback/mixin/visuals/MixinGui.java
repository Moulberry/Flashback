package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.editor.ui.CustomImGuiImplGlfw;
import com.moulberry.flashback.editor.ui.ReplayUI;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Gui.class)
public abstract class MixinGui {

    @Shadow
    @Nullable
    protected abstract Player getCameraPlayer();

    @Unique
    private GameType cameraGameType = null;

    @Unique
    private boolean shouldHideElements = false;

    @Inject(method = "render", at = @At("HEAD"))
    public void render_updateCameraGameType(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        this.shouldHideElements = false;
        this.cameraGameType = null;
        if (Flashback.isInReplay()) {
            this.shouldHideElements = ReplayUI.isActive() || Flashback.isExporting();
            this.cameraGameType = Minecraft.getInstance().gameMode.getPlayerMode();

            Player player = this.getCameraPlayer();
            if (player != null) {
                var connection = Minecraft.getInstance().getConnection();
                if (connection != null) {
                    var playerInfo = connection.getPlayerInfo(player.getUUID());
                    if (playerInfo != null) {
                        this.cameraGameType = playerInfo.getGameMode();
                    }
                }
            }
        }
    }

    @Inject(method = "renderChat", at = @At("HEAD"), cancellable = true, require = 0)
    public void renderChat(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (this.shouldHideElements) {
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState != null && !editorState.replayVisuals.showChat) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "renderTitle", at = @At("HEAD"), cancellable = true, require = 0)
    public void renderTitle(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (this.shouldHideElements) {
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState != null && !editorState.replayVisuals.showTitleText) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 0)
    public void renderScoreboardSidebar(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (this.shouldHideElements) {
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState != null && !editorState.replayVisuals.showScoreboard) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true, require = 0)
    public void renderOverlayMessage(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (this.shouldHideElements) {
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState != null && !editorState.replayVisuals.showActionBar) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true, require = 0)
    public void renderHotbarAndDecorations(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (this.shouldHideElements) {
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState != null && !editorState.replayVisuals.showHotbar) {
                ci.cancel();
            }
        }
    }

    @WrapOperation(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"), require = 0)
    public GameType renderHotbarAndDecorations_getPlayerMode(MultiPlayerGameMode instance, Operation<GameType> original) {
        if (this.cameraGameType != null) {
            return this.cameraGameType;
        }
        return original.call(instance);
    }

    @WrapOperation(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;canHurtPlayer()Z"), require = 0)
    public boolean renderHotbarAndDecorations_canHurtPlayer(MultiPlayerGameMode instance, Operation<Boolean> original) {
        if (this.cameraGameType != null) {
            return this.cameraGameType.isSurvival();
        }
        return original.call(instance);
    }

    @Inject(method = "isExperienceBarVisible", at = @At("HEAD"), cancellable = true, require = 0)
    public void isExperienceBarVisible(CallbackInfoReturnable<Boolean> cir) {
        if (this.cameraGameType != null) {
            cir.setReturnValue(this.cameraGameType.isSurvival());
        }
    }

    @WrapOperation(method = "renderExperienceBar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXpNeededForNextLevel()I"), require = 0)
    public int renderExperienceBar_getXpNeededForNextLevel(LocalPlayer instance, Operation<Integer> original) {
        if (Flashback.isInReplay()) {
            Player player = this.getCameraPlayer();
            if (player != null) {
                return player.getXpNeededForNextLevel();
            }
        }
        return original.call(instance);
    }

    @WrapOperation(method = "renderExperienceBar", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;experienceProgress:F"), require = 0)
    public float renderExperienceBar_experienceProgress(LocalPlayer instance, Operation<Float> original) {
        if (Flashback.isInReplay()) {
            Player player = this.getCameraPlayer();
            if (player != null) {
                return player.experienceProgress;
            }
        }
        return original.call(instance);
    }

    @WrapOperation(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"), require = 0)
    public float renderCrosshair_getAttackStrengthScale(LocalPlayer instance, float partialTick, Operation<Float> original) {
        if (Flashback.isInReplay()) {
            Player player = this.getCameraPlayer();
            if (player != null) {
                return player.getAttackStrengthScale(partialTick);
            }
        }
        return original.call(instance, partialTick);
    }

    @WrapOperation(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getCurrentItemAttackStrengthDelay()F"), require = 0)
    public float renderCrosshair_getCurrentItemAttackStrengthDelay(LocalPlayer instance, Operation<Float> original) {
        if (Flashback.isInReplay()) {
            Player player = this.getCameraPlayer();
            if (player != null) {
                return player.getCurrentItemAttackStrengthDelay();
            }
        }
        return original.call(instance);
    }

    @WrapOperation(method = "renderExperienceLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I"), require = 0)
    public int renderExperienceLevel_experienceLevel(LocalPlayer instance, Operation<Integer> original) {
        if (Flashback.isInReplay()) {
            Player player = this.getCameraPlayer();
            if (player != null) {
                return player.experienceLevel;
            }
        }
        return original.call(instance);
    }

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    public void renderVignette(GuiGraphics guiGraphics, Entity entity, CallbackInfo ci) {
        // The vignette ruins the transparency when trying to export with alpha
        // The vignette is also probably unwanted in general when trying to record, so lets just get rid of it
        if (Flashback.isInReplay()) {
            ci.cancel();
        }
    }

    @Inject(method = "canRenderCrosshairForSpectator", at = @At("HEAD"), cancellable = true, require = 0)
    public void canRenderCrosshairForSpectator(HitResult hitResult, CallbackInfoReturnable<Boolean> cir) {
        if (Flashback.isInReplay()) {
            if (!Flashback.isExporting() && ReplayUI.isActive() && ReplayUI.imguiGlfw.getMouseHandledBy() == CustomImGuiImplGlfw.MouseHandledBy.GAME) {
                cir.setReturnValue(true);
                return;
            }
            cir.setReturnValue(Flashback.getSpectatingPlayer() != null);
        }
    }

}
