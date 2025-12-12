package com.moulberry.flashback.editor.ui.windows;

import imgui.moulberry90.type.ImBoolean;

@FunctionalInterface
public interface ImGuiWindowRenderer {

    void render(ImBoolean open, boolean newlyOpened);

}
