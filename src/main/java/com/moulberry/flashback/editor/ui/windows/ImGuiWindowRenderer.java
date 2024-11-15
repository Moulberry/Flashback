package com.moulberry.flashback.editor.ui.windows;

import imgui.type.ImBoolean;

@FunctionalInterface
public interface ImGuiWindowRenderer {

    void render(ImBoolean open, boolean newlyOpened);

}
