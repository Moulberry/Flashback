package com.moulberry.flashback.editor.ui.windows;

import imgui.flashback.type.ImBoolean;

@FunctionalInterface
public interface ImGuiWindowRenderer {

    void render(ImBoolean open, boolean newlyOpened);

}
