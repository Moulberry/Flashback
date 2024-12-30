package com.moulberry.flashback.keyframe.types;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.change.KeyframeChange;
import com.moulberry.flashback.keyframe.change.KeyframeChangeCameraPosition;
import com.moulberry.flashback.keyframe.change.KeyframeChangeTickrate;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.impl.FOVKeyframe;
import com.moulberry.flashback.keyframe.impl.TickrateKeyframe;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import imgui.ImGui;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class SpeedKeyframeType implements KeyframeType<TickrateKeyframe> {

    public static SpeedKeyframeType INSTANCE = new SpeedKeyframeType();

    private SpeedKeyframeType() {
    }

    @Override
    public Class<? extends KeyframeChange> keyframeChangeType() {
        return KeyframeChangeTickrate.class;
    }

    @Override
    public @Nullable String icon() {
        return "\ue9e4";
    }

    @Override
    public String name() {
        return "Speed";
    }

    @Override
    public String id() {
        return "SPEED";
    }

    @Override
    public @Nullable TickrateKeyframe createDirect() {
        return null;
    }

    @Override
    public KeyframeCreatePopup<TickrateKeyframe> createPopup() {
        float[] speedKeyframeInput = new float[]{1.0f};
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
            speedKeyframeInput[0] = replayServer.getDesiredTickRate(false) / 20.0f;
        }

        return () -> {
            ImGui.sliderFloat("Speed", speedKeyframeInput, 0.1f, 10f);
            if (ImGui.button("Add")) {
                return new TickrateKeyframe(speedKeyframeInput[0] * 20.0f);
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }
            return null;
        };
    }
}
