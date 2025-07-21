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
import net.minecraft.client.resources.language.I18n;

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

        String title = I18n.get("flashback.export_queue");
        if (ImGuiHelper.beginPopupModalCloseable(title + "###ExportQueue", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGuiHelper.pushStyleColor(ImGuiCol.Border, 0xFF808080);

            boolean canStartJob = !ExportJobQueue.queuedJobs.isEmpty() && Flashback.EXPORT_JOB == null;
            boolean canRemoveJob = !ExportJobQueue.queuedJobs.isEmpty();

            ImGui.text(I18n.get("flashback.export_jobs"));
            if (ImGui.beginChild("##Jobs", 300, 150, true)) {
                if (ImGui.beginTable("##JobTable", 3, ImGuiTableFlags.SizingFixedFit)) {
                    ImGui.tableSetupColumn(I18n.get("flashback.name"), ImGuiTableColumnFlags.WidthStretch);

                    int startJob = -1;
                    int removeJob = -1;

                    for (int i = 0; i < ExportJobQueue.queuedJobs.size(); i++) {
                        ExportSettings queuedJob = ExportJobQueue.queuedJobs.get(i);
                        String name = queuedJob.name() == null ? I18n.get("flashback.job_n", (i+1)) : queuedJob.name();

                        ImGui.tableNextColumn();
                        ImGui.text(name);
                        ImGui.tableNextColumn();
                        if (ImGui.smallButton(I18n.get("flashback.start"))) {
                            startJob = i;
                        }
                        ImGui.tableNextColumn();
                        if (ImGui.smallButton(I18n.get("flashback.remove"))) {
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
            if (ImGui.button(I18n.get("flashback.start_all")) && canStartJob) {
                ExportJobQueue.drainingQueue = true;
            }
            if (!canStartJob) ImGui.endDisabled();

            ImGui.sameLine();

            if (!canRemoveJob) ImGui.beginDisabled();
            if (ImGui.button(I18n.get("flashback.remove_all")) && canRemoveJob) {
                ExportJobQueue.queuedJobs.clear();
            }
            if (!canRemoveJob) ImGui.endDisabled();

            ImGuiHelper.endPopupModalCloseable();
        }
    }

}
