package com.moulberry.flashback.screen;

import com.mojang.blaze3d.platform.cursor.CursorType;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ext.ScreenExt;
import com.moulberry.flashback.povstate.CursorSprites;
import com.moulberry.flashback.povstate.PovScreenType;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.CrashReportDetail;
import net.minecraft.ReportedException;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class FakeScreenHudElement implements HudElement {

    private Screen fakeScreen = null;
    private PovScreenType screenType;
    private int lastScreenWidth = 0;
    private int lastScreenHeight = 0;
    private boolean rendering = false;
    private boolean initializedFakeScreen = false;
    private float mouseOffsetX;
    private float mouseOffsetY;
    private float nextMouseOffsetX;
    private float nextMouseOffsetY;

    public AbstractContainerMenu failedContainer = null;
    public PovScreenType failedScreen = null;

    public void close() {
        this.fakeScreen = null;
        this.screenType = null;
    }

    public void open(Screen screen, PovScreenType screenType) {
        ((ScreenExt)screen).flashback$setFake(true);
        this.failedScreen = screenType;

        this.mouseOffsetX = this.nextMouseOffsetX;
        this.mouseOffsetY = this.nextMouseOffsetY;

        if (this.lastScreenWidth > 0 && this.lastScreenHeight > 0) {
            screen.init(this.lastScreenWidth, this.lastScreenHeight);
            this.initializedFakeScreen = true;
        } else {
            this.initializedFakeScreen = false;
        }

        this.fakeScreen = screen;
        this.screenType = screenType;
        this.failedScreen = null;
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Screen fakeScreen = this.fakeScreen;
        if (fakeScreen != null && this.initializedFakeScreen && player != null) {
            AbstractContainerMenu previousContainerMenu = player.containerMenu;
            try {
                if (fakeScreen instanceof AbstractContainerScreen<?> containerScreen) {
                    player.containerMenu = containerScreen.getMenu();
                }

                fakeScreen.tick();
            } finally {
                player.containerMenu = previousContainerMenu;
            }
        }
        this.mouseOffsetX = this.nextMouseOffsetX;
        this.mouseOffsetY = this.nextMouseOffsetY;
    }

    public void setMouseScreenOffset(float mouseOffsetX, float mouseOffsetY) {
        this.nextMouseOffsetX = mouseOffsetX;
        this.nextMouseOffsetY = mouseOffsetY;
    }

    public Screen fakeScreen() {
        return this.fakeScreen;
    }

    public PovScreenType fakeScreenType() {
        return this.screenType;
    }

    public boolean hasScreenFailed(PovScreenType screenType) {
        return this.failedScreen != null && this.failedScreen == screenType;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (this.rendering || (this.failedScreen != null && this.failedScreen == this.screenType)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (this.fakeScreen == null || player == null) {
            this.lastScreenWidth = graphics.guiWidth();
            this.lastScreenHeight = graphics.guiHeight();
            return;
        }

        Screen fakeScreen = this.fakeScreen;
        PovScreenType fakeScreenType = this.screenType;

        this.rendering = true;
        try {
            if (this.lastScreenWidth != graphics.guiWidth() || this.lastScreenHeight != graphics.guiHeight()) {
                if (!this.initializedFakeScreen) {
                    this.fakeScreen.init(graphics.guiWidth(), graphics.guiHeight());
                    this.initializedFakeScreen = true;
                } else {
                    this.fakeScreen.resize(graphics.guiWidth(), graphics.guiHeight());
                }
                this.lastScreenWidth = graphics.guiWidth();
                this.lastScreenHeight = graphics.guiHeight();
            }

            AbstractContainerMenu previousContainerMenu = player.containerMenu;
            CursorType previousPendingCursor = graphics.pendingCursor;
            try {
                if (fakeScreen instanceof AbstractContainerScreen<?> containerScreen) {
                    player.containerMenu = containerScreen.getMenu();
                }

                float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
                float mouseOriginX = fakeScreenType.mouseOriginX(fakeScreen);
                float mouseOriginY = fakeScreenType.mouseOriginY(fakeScreen);
                float offsetX = this.mouseOffsetX + (this.nextMouseOffsetX - this.mouseOffsetX) * partialTicks;
                float offsetY = this.mouseOffsetY + (this.nextMouseOffsetY - this.mouseOffsetY) * partialTicks;
                float mouseX = offsetX + mouseOriginX;
                float mouseY = offsetY + mouseOriginY;
                fakeScreen.extractRenderStateWithTooltipAndSubtitles(graphics, (int) mouseX, (int) mouseY, deltaTracker.getGameTimeDeltaTicks());

                int scale = mc.getWindow().getGuiScale();
                Identifier sprite = CursorSprites.fromCursorType(graphics.pendingCursor);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, (int) mouseX - 30/scale, (int) mouseY - 30/scale, 60/scale, 60/scale);
            } finally {
                player.containerMenu = previousContainerMenu;
                graphics.pendingCursor = previousPendingCursor;
            }

            this.failedScreen = null;
        } catch (Throwable t) {
            CrashReport report = CrashReport.forThrowable(t, "Rendering fake screen (flashback)");
            CrashReportCategory category = report.addCategory("Screen render details");
            category.setDetail("Screen name", () -> this.fakeScreen.getClass().getCanonicalName());
            category.setDetail("Fake screen data", () -> this.screenType.toString());
            Minecraft.getInstance().mouseHandler.fillMousePositionDetails(category, Minecraft.getInstance().getWindow());
            throw new ReportedException(report);
        } finally {
            this.rendering = false;
        }
    }

}
