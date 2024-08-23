package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class Shapes {

    public static void line(BufferBuilder bufferBuilder, PoseStack.Pose pose, Vec3 from, Vec3 to) {
        line(bufferBuilder, pose, 1f, 1f, 1f, 1f,
            (float) from.x, (float) from.y, (float) from.z, (float) to.x, (float) to.y, (float) to.z);
    }
    public static void line(BufferBuilder bufferBuilder, PoseStack.Pose pose, Vec3 from, Vec3 to, float red, float green, float blue, float alpha) {
        line(bufferBuilder, pose, red, green, blue, alpha,
            (float) from.x, (float) from.y, (float) from.z, (float) to.x, (float) to.y, (float) to.z);
    }

    public static void line(BufferBuilder bufferBuilder, PoseStack.Pose pose,
            float red, float green, float blue, float alpha,
            float fromX, float fromY, float fromZ,
            float toX, float toY, float toZ) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        float dz = toZ - fromZ;
        float distanceInv = 1f / (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
        dx *= distanceInv;
        dy *= distanceInv;
        dz *= distanceInv;

        Matrix4f transform = pose.pose();
        bufferBuilder.addVertex(transform, fromX, fromY, fromZ).setColor(red, green, blue, alpha).setNormal(pose, dx, dy, dz);
        bufferBuilder.addVertex(transform, toX, toY, toZ).setColor(red, green, blue, alpha).setNormal(pose, dx, dy, dz);
    }

}
