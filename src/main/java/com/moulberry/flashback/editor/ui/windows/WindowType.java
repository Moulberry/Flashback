package com.moulberry.flashback.editor.ui.windows;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.WindowOpenState;
import imgui.type.ImBoolean;

import java.util.Set;

public enum WindowType {

    PLAYER_LIST("player_list", PlayerListWindow::render),
    MOVEMENT("movement", MovementWindow::render);

    private final String windowId;
    private final ImGuiWindowRenderer renderMethod;
    private final ImBoolean open = new ImBoolean();
    private WindowOpenState openState = WindowOpenState.UNKNOWN;

    WindowType(String windowId, ImGuiWindowRenderer renderMethod) {
        this.windowId = windowId;
        this.renderMethod = renderMethod;
    }

    public static void renderAll() {
        Set<String> openWindows = Flashback.getConfig().openedWindows;

        for (WindowType windowType : values()) {
            if (openWindows.contains(windowType.windowId)) {
                boolean justOpened = windowType.openState == WindowOpenState.CLOSED;

                windowType.open.set(true);
                windowType.renderMethod.render(windowType.open, justOpened);

                if (!windowType.open.get()) {
                    openWindows.remove(windowType.windowId);
                }

                windowType.openState = WindowOpenState.OPEN;
            } else {
                windowType.openState = WindowOpenState.CLOSED;
            }
        }
    }

}
