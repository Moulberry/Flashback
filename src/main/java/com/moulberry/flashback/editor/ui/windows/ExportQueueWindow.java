package com.moulberry.flashback.editor.ui.windows;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportJobQueue;
import com.moulberry.flashback.exporting.ExportSettings;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;

public class ExportQueueWindow {

    private static boolean open = false;

    public static void open() {
        open = true;
    }

    public static void render() {
        if (open) {
            ImGui.openPopup("###ExportQueue");
            open = false;
        }

        if (ImGuiHelper.beginPopupModalCloseable("Export Queue###ExportQueue", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGuiHelper.pushStyleColor(ImGuiCol.Border, 0xFF808080);

            boolean canStartJob = !ExportJobQueue.queuedJobs.isEmpty() && Flashback.EXPORT_JOB == null;
            boolean canRemoveJob = !ExportJobQueue.queuedJobs.isEmpty();

            ImGui.text("Jobs");
            if (ImGui.beginChild("##Jobs", 300, 150, true)) {
                if (ImGui.beginTable("##JobTable", 3, ImGuiTableFlags.SizingFixedFit)) {
                    ImGui.tableSetupColumn("Name", ImGuiTableColumnFlags.WidthStretch);

                    int startJob = -1;
                    int removeJob = -1;

                    for (int i = 0; i < ExportJobQueue.queuedJobs.size(); i++) {
                        ExportSettings queuedJob = ExportJobQueue.queuedJobs.get(i);
                        String name = queuedJob.name() == null ? "Job #" + (i+1) : queuedJob.name();

                        ImGui.tableNextColumn();
                        ImGui.text(name);
                        ImGui.tableNextColumn();
                        if (ImGui.smallButton("Start")) {
                            startJob = i;
                        }
                        ImGui.tableNextColumn();
                        if (ImGui.smallButton("Remove")) {
                            removeJob = i;
                        }
                    }

                    if (startJob >= 0 && canStartJob) {
                        ExportSettings settings = ExportJobQueue.queuedJobs.remove(startJob);
                        Flashback.EXPORT_JOB = new ExportJob(settings);
                    } else if (removeJob >= 0 && canRemoveJob) {
                        ExportJobQueue.queuedJobs.remove(removeJob);
                    }

                    ImGui.endTable();
                }
                ImGui.endChild();
            }

            ImGuiHelper.popStyleColor();


            if (!canStartJob) ImGui.beginDisabled();
            if (ImGui.button("Start All") && canStartJob) {
                ExportJobQueue.drainingQueue = true;
            }
            if (!canStartJob) ImGui.endDisabled();

            ImGui.sameLine();

            if (!canRemoveJob) ImGui.beginDisabled();
            if (ImGui.button("Remove All") && canRemoveJob) {
                ExportJobQueue.queuedJobs.clear();
            }
            if (!canRemoveJob) ImGui.endDisabled();

            ImGuiHelper.endPopupModalCloseable();
        }
    }

}
