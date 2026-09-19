package com.moulberry.flashback.editor.ui;

public enum MouseHandledBy {
    EDITOR_GRABBED,
    IMGUI,
    GAME,
    BOTH;

    public boolean allowImgui() {
        return this == IMGUI || this == BOTH;
    }

    public boolean allowGame() {
        return this == GAME || this == BOTH;
    }
}
