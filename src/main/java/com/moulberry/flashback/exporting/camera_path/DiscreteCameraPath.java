package com.moulberry.flashback.exporting.camera_path;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class DiscreteCameraPath {
    private List<Vector3f> positions;
    private List<Quaternionf> rotations;
    private List<Float> fovs;

    public DiscreteCameraPath() {
        this.positions = new ArrayList<>();
        this.rotations = new ArrayList<>();
        this.fovs = new ArrayList<>();
    }

    public List<Vector3f> getPositions() {
        return positions;
    }

    public List<Quaternionf> getRotations() {
        return rotations;
    }

    public List<Float> getFovs() {
        return fovs;
    }

    public void addPosition(Vector3f position) {
        this.positions.add(position);
    }

    public void addRotation(Quaternionf rotation) {
        this.rotations.add(rotation);
    }

    public void addFov(float fov){
        fovs.add(fov);
    }

    public void addPositionAndRotation(Vector3f position, Quaternionf rotation) {
        this.positions.add(position);
        this.rotations.add(new Quaternionf(rotation));
    }
}
