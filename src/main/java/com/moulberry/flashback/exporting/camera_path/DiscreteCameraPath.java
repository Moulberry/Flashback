package com.moulberry.flashback.exporting.camera_path;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class DiscreteCameraPath {
    private final List<Vector3f> positions;
    private final List<Quaternionf> rotations;

    public DiscreteCameraPath() {
        this.positions = new ArrayList<>();
        this.rotations = new ArrayList<>();
    }

    public List<Vector3f> getPositions() {
        return positions;
    }

    public List<Quaternionf> getRotations() {
        return rotations;
    }

    public void addPosition(Vector3f position) {
        this.positions.add(position);
    }

    public void addRotation(Quaternionf rotation) {
        this.rotations.add(rotation);
    }

    public void addPositionAndRotation(Vector3f position, Quaternionf rotation) {
        this.positions.add(position);
        this.rotations.add(new Quaternionf(rotation));
    }
}
