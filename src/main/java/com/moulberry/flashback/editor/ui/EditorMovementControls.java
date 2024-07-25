package com.moulberry.flashback.editor.ui;

import imgui.ImGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Intersectionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

public abstract sealed class EditorMovementControls permits EditorMovementControls.None, EditorMovementControls.Rotate, EditorMovementControls.Pan, EditorMovementControls.Arcball {

    // Interface
    public abstract boolean allowGameInputWhileCaptureKeyboard();
    public abstract boolean shouldStop(boolean isGrabbed);
    public abstract void update(double mouseDeltaX, double mouseDeltaY);

    // Static constructors
    public static EditorMovementControls none() {
        return None.INSTANCE;
    }

    public static EditorMovementControls rotate() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return None.INSTANCE;

        return new Rotate(ReplayUI.getMouseForwardsVector(), new Vec2(
            Minecraft.getInstance().player.getXRot(),
            Minecraft.getInstance().player.getYRot()
        ));
    }

//    public static EditorMovementControls pan() {
//        LocalPlayer player = Minecraft.getInstance().player;
//        if (player == null) return None.INSTANCE;
//
//        RayCaster.RaycastResult result = Tool.raycastBlock();
//        if (result == null) return None.INSTANCE;
//
//        Vec3 planePoint = result.getLocation();
//        return new Pan(player.position(), planePoint.toVector3f(), player.getLookAngle().toVector3f().negate());
//    }

//    public static EditorMovementControls arcball() {
//        LocalPlayer player = Minecraft.getInstance().player;
//        if (player == null) return None.INSTANCE;
//
//        var look = ReplayUI.getMouseLookVector();
//        if (look == null) return None.INSTANCE;
//
//        RayCaster.RaycastResult result = RayCaster.raycast(Minecraft.getInstance().level,
//            player.getEyePosition().toVector3f(), look.toVector3f(), false, false);
//
//        Vec3 point;
//        if (result != null) {
//            point = result.getLocation();
//        } else {
//            return None.INSTANCE;
//        }
//
//        float distance = (float) point.distanceTo(player.getEyePosition());
//        return new Arcball(point, distance, true);
//    }

    // Implementations
    protected static final class None extends EditorMovementControls {
        private static final None INSTANCE = new None();

        @Override
        public boolean allowGameInputWhileCaptureKeyboard() {
            return false;
        }

        @Override
        public boolean shouldStop(boolean isGrabbed) {
            return false;
        }

        @Override
        public void update(double mouseDeltaX, double mouseDeltaY) {
        }
    }

    protected static final class Rotate extends EditorMovementControls {
        private final Vec3 originalForwards;
        private final Vec2 originalAngles;

        public Rotate(Vec3 originalForwards, Vec2 originalAngles) {
            this.originalForwards = originalForwards;
            this.originalAngles = originalAngles;
        }

        @Override
        public boolean allowGameInputWhileCaptureKeyboard() {
            return true;
        }

        @Override
        public boolean shouldStop(boolean isGrabbed) {
            return !ImGui.isMouseDown(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            // todo: keybinds
//            return !Keybinds.ROTATE_CAMERA.isDownIgnoreMods();
        }

        @Override
        public void update(double mouseDeltaX, double mouseDeltaY) {
            Vec3 forwards = ReplayUI.getMouseForwardsVector();
            LocalPlayer player = Minecraft.getInstance().player;
            if (forwards == null || player == null) return;

            float pitch = this.originalAngles.x;
            float yaw = this.originalAngles.y;
            Vec3 lastVector = this.originalForwards;
            for (int i=1; i<=10; i++) {
                Vec3 thisVector = this.originalForwards.lerp(forwards, i/10f);

                // Calculate pitch
                double differenceY = lastVector.y - thisVector.y;
                double pitchDelta = Math.toDegrees(Math.asin(differenceY / 2f)) * 2;
                pitch = Mth.wrapDegrees(pitch - (float) pitchDelta);
                pitch = Mth.clamp(pitch, -90, 90);

                // Calculate change in yaw
                double yaw1 = Math.toDegrees(Math.atan2(lastVector.x, lastVector.z));
                double yaw2 = Math.toDegrees(Math.atan2(thisVector.x, thisVector.z));
                double yawDelta = Mth.wrapDegrees(yaw1 - yaw2);

                // Calculate scaling factor (radius of circle on unit sphere at current pitch)
                Vector3f forwards3f = lastVector.lerp(thisVector, 0.5f).toVector3f();
                forwards3f.rotateX((float) Math.toRadians(pitch));
                forwards3f.normalize();
                float radius = (float) Math.sqrt(1 - forwards3f.y() * forwards3f.y()) * 1.08f;
                if (radius < 0.2f) radius = 0.2f;

                yaw = Mth.wrapDegrees(yaw - (float) yawDelta / radius);

                lastVector = thisVector;
            }

            // Set values
            player.setXRot(pitch);
            player.setYRot(yaw);
        }
    }

    protected static final class Pan extends EditorMovementControls {
        private final Vec3 originalPos;
        private final Vector3f planePoint;
        private final Vector3f planeNormal;

        public Pan(Vec3 originalPos, Vector3f planePoint, Vector3f planeNormal) {
            this.originalPos = originalPos;
            this.planePoint = planePoint;
            this.planeNormal = planeNormal;
        }

        @Override
        public boolean allowGameInputWhileCaptureKeyboard() {
            return false;
        }

        @Override
        public boolean shouldStop(boolean isGrabbed) {
            return !ImGui.isMouseDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
//            return !Keybinds.PAN_CAMERA.isDownIgnoreMods();
        }

        @Override
        public void update(double mouseDeltaX, double mouseDeltaY) {
            Vec3 forwards = ReplayUI.getMouseForwardsVector();
            LocalPlayer player = Minecraft.getInstance().player;
            if (forwards == null || player == null) return;

            var origin = this.originalPos.add(0, player.getEyeHeight(), 0).toVector3f();
            var view = ReplayUI.getMouseLookVectorFromForwards(forwards).toVector3f();

            var distance = Intersectionf.intersectRayPlane(origin, view, this.planePoint, this.planeNormal, 1E-5f);
            if (distance < 0) return;

            Vector3f targetPoint = origin.add(new Vector3f(view).mul(distance));
            Vector3f targetDelta = new Vector3f(targetPoint).sub(this.planePoint);

            var finalPos = this.originalPos.subtract(targetDelta.x, targetDelta.y, targetDelta.z);
            player.setPos(finalPos);
            player.setOldPosAndRot();
        }
    }

    protected static final class Arcball extends EditorMovementControls {
        private final Vec3 point;
        private float distance;
        private boolean snapLook;

        public Arcball(Vec3 point, float distance, boolean snapLook) {
            this.point = point;
            this.distance = Math.max(1, distance);
            this.snapLook = snapLook;
        }

        @Override
        public boolean allowGameInputWhileCaptureKeyboard() {
            return false;
        }

        @Override
        public boolean shouldStop(boolean isGrabbed) {
            return !isGrabbed;
        }

        @Override
        public void update(double mouseDeltaX, double mouseDeltaY) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;

            if (this.snapLook) {
                this.snapLook = false;

                var look = ReplayUI.getMouseLookVector();
                double horizontal = Math.sqrt(look.x*look.x + look.z*look.z);

                float newXRot = Mth.wrapDegrees((float)(Math.toDegrees(-(Mth.atan2(look.y, horizontal)))));
                float newYRot = Mth.wrapDegrees((float)(Math.toDegrees(Mth.atan2(look.z, look.x))) - 90.0f);

                player.setXRot(newXRot);
                player.setYRot(newYRot);

                return;
            }

            double baseSensitivity = Minecraft.getInstance().options.sensitivity().get() * (double)0.6f + (double)0.2f;
            double sensitivity = baseSensitivity * baseSensitivity * baseSensitivity * 8.0;
            int invert = Minecraft.getInstance().options.invertYMouse().get() ? -1 : 1;

            player.turn(mouseDeltaX * sensitivity, mouseDeltaY * sensitivity * invert);

            if (ImGui.getIO().getMouseWheel() != 0) {
                float wheel = Math.signum(ImGui.getIO().getMouseWheel());

                float move = this.distance * 0.05f;
                if (move > 4) move = 4 + (float)Math.sqrt(move - 4);
                move *= wheel;
                if (ReplayUI.isMoveQuickDown()) move *= 2;

                this.distance = Math.max(1, this.distance - move);
            }

            Vec3 position = this.point.subtract(player.getLookAngle().scale(this.distance));

            Minecraft.getInstance().player.setPos(position.subtract(0, player.getEyeHeight(), 0));
            Minecraft.getInstance().player.setOldPosAndRot();
        }
    }

}
