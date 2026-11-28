package com.moulberry.flashback.povstate;

import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.moulberry.flashback.Flashback;
import net.minecraft.resources.Identifier;

public class CursorSprites {

    public static Identifier ARROW = Flashback.createIdentifier("cursor/arrow");
    public static Identifier CROSSHAIR = Flashback.createIdentifier("cursor/crosshair");
    public static Identifier E_RESIZE = Flashback.createIdentifier("cursor/e-resize");
    public static Identifier EW_RESIZE = Flashback.createIdentifier("cursor/ew-resize");
    public static Identifier MOVE = Flashback.createIdentifier("cursor/move");
    public static Identifier N_RESIZE = Flashback.createIdentifier("cursor/n-resize");
    public static Identifier NE_RESIZE = Flashback.createIdentifier("cursor/ne-resize");
    public static Identifier NESW_RESIZE = Flashback.createIdentifier("cursor/nesw-resize");
    public static Identifier NOT_ALLOWED = Flashback.createIdentifier("cursor/not-allowed");
    public static Identifier NS_RESIZE = Flashback.createIdentifier("cursor/ns-resize");
    public static Identifier NW_RESIZE = Flashback.createIdentifier("cursor/nw-resize");
    public static Identifier NWSE_RESIZE = Flashback.createIdentifier("cursor/nwse-resize");
    public static Identifier POINTER = Flashback.createIdentifier("cursor/pointer");
    public static Identifier S_RESIZE = Flashback.createIdentifier("cursor/s-resize");
    public static Identifier SE_RESIZE = Flashback.createIdentifier("cursor/se-resize");
    public static Identifier SW_RESIZE = Flashback.createIdentifier("cursor/sw-resize");
    public static Identifier TEXT = Flashback.createIdentifier("cursor/text");
    public static Identifier W_RESIZE = Flashback.createIdentifier("cursor/w-resize");

    public static Identifier fromCursorType(CursorType cursorType) {
        if (cursorType == CursorTypes.IBEAM) {
            return TEXT;
        } else if (cursorType == CursorTypes.CROSSHAIR) {
            return CROSSHAIR;
        } else if (cursorType == CursorTypes.POINTING_HAND) {
            return POINTER;
        } else if (cursorType == CursorTypes.RESIZE_NS) {
            return NS_RESIZE;
        } else if (cursorType == CursorTypes.RESIZE_EW) {
            return EW_RESIZE;
        } else if (cursorType == CursorTypes.RESIZE_ALL) {
            return MOVE;
        } else if (cursorType == CursorTypes.NOT_ALLOWED) {
            return NOT_ALLOWED;
        } else if (cursorType == CursorTypes.ARROW) {
            return ARROW;
        } else {
            return ARROW;
        }
    }

}
