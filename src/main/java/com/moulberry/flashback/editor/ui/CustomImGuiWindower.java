package com.moulberry.flashback.editor.ui;

import net.minecraft.world.phys.Vec2;

import java.util.List;

public interface CustomImGuiWindower {

    void init(long windowId);
    void newFrame();

    float getContentScale();
    MouseHandledBy getMouseHandledBy();
    void setReleaseAllKeys(boolean release);

    boolean isGrabbed();
    void ungrab();
    void setGrabbed(boolean passthroughToGame, int grabLinkedKey, double x, double y);
    double getGrabbedMouseDeltaX();
    double getGrabbedMouseDeltaY();

    float getRawMouseX();
    float getRawMouseY();

}
