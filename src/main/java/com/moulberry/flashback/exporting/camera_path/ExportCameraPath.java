package com.moulberry.flashback.exporting.camera_path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.handler.KeyframeHandler;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.KeyframeTrack;
import net.minecraft.client.Minecraft;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;

public class ExportCameraPath {
    private ExportGLTF exportGLTF;
    private DiscreteCameraPath discreteCameraPath;

    public ExportCameraPath(ExportGLTF exportGLTF){
        this.exportGLTF = exportGLTF;
        this.discreteCameraPath = new DiscreteCameraPath();
    }

    public void export(){
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        JsonObject gltfFile = generateGLTF(discreteCameraPath);

        String outputPath = exportGLTF.output().toString().substring(0,
                exportGLTF.output().toString().lastIndexOf('\\')) + "/" + exportGLTF.name() + "_camera_path.gltf";

        String jsonString = gson.toJson(gltfFile);

        try (FileWriter fileWriter = new FileWriter(outputPath)) {
            fileWriter.write(jsonString);
            System.out.println("GLTF file exported successfully to " + outputPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private JsonObject generateGLTF(DiscreteCameraPath cameraPath){
        if(cameraPath.getPositions().isEmpty() || cameraPath.getRotations().isEmpty()){
            System.out.println("CameraPath is empty.");
            return null;
        }

        Vector3f initialLocation = cameraPath.getPositions().getFirst();
        Quaternionf initialRotation = cameraPath.getRotations().getFirst();

        JsonObject gltfFile = new JsonObject();

        // asset
        JsonObject assetSection = new JsonObject();
        assetSection.addProperty("generator", "Flashback Camera Path to glTF (by Gilan)");
        assetSection.addProperty("version", "2.0");
        gltfFile.add("asset", assetSection);

        // scene
        gltfFile.addProperty("scene", 0);

        // scenes
        JsonArray scenesArray = new JsonArray();
        JsonObject scene = new JsonObject();
        scene.addProperty("name", "Scene");
        JsonArray sceneNodesArray = new JsonArray();
        sceneNodesArray.add(0);
        scene.add("nodes", sceneNodesArray);
        scenesArray.add(scene);
        gltfFile.add("scenes", scenesArray);

        // nodes
        JsonArray nodesArray = new JsonArray();
        JsonObject camera = new JsonObject();
        camera.addProperty("camera", 0);
        camera.addProperty("name", "Camera");
        JsonArray rotation = new JsonArray();
        rotation.add(initialRotation.x);
        rotation.add(initialRotation.y);
        rotation.add(initialRotation.z);
        rotation.add(initialRotation.w);
        camera.add("rotation", rotation);
        JsonArray translation = new JsonArray();
        translation.add(initialLocation.x);
        translation.add(initialRotation.z); // switch to z up
        translation.add(initialRotation.y);
        camera.add("translation", translation);
        nodesArray.add(camera);
        gltfFile.add("nodes", nodesArray);

        // cameras
        JsonArray cameraArray = new JsonArray();
        JsonObject cameraObject = new JsonObject();
        cameraObject.addProperty("name", "Camera");
        JsonObject perspective = new JsonObject();
        perspective.addProperty("aspectRatio", exportGLTF.editorState().replayVisuals.changeAspectRatio.aspectRatio());
        perspective.addProperty("yfov", (float) Math.toRadians(exportGLTF.editorState().replayVisuals.overrideFovAmount));
        perspective.addProperty("zfar", 1000);
        perspective.addProperty("znear", 0.1);
        cameraObject.add("perspective", perspective);
        cameraObject.addProperty("type", "perspective");
        cameraArray.add(cameraObject);
        gltfFile.add("cameras", cameraArray);

        // animations
        JsonArray animationsArray = new JsonArray();
        JsonObject animationObject = new JsonObject();

        JsonArray channelsArray = new JsonArray();
        JsonObject channelTranslation = new JsonObject();
        JsonObject channelRotation = new JsonObject();
        JsonObject channelFov = new JsonObject();

        JsonObject targetTranslation = new JsonObject();
        JsonObject targetRotation = new JsonObject();
        JsonObject targetFov = new JsonObject();

        targetTranslation.addProperty("node", 0);
        targetTranslation.addProperty("path", "translation");

        targetRotation.addProperty("node", 0);
        targetRotation.addProperty("path", "rotation");

        targetFov.addProperty("node", 0);
        targetFov.addProperty("path", "perspective/yfov");

        channelTranslation.addProperty("sampler", 0);
        channelTranslation.add("target", targetTranslation);

        channelRotation.addProperty("sampler", 1);
        channelRotation.add("target", targetRotation);

        channelFov.addProperty("sampler", 2);
        channelFov.add("target", targetFov);

        channelsArray.add(channelTranslation);
        channelsArray.add(channelRotation);
        channelsArray.add(channelFov);

        animationObject.add("channels", channelsArray);
        animationObject.addProperty("name", "CameraAction");

        JsonArray samplersArray = new JsonArray();

        JsonObject sampler0 = new JsonObject();
        sampler0.addProperty("input", 0);
        sampler0.addProperty("interpolation", "LINEAR");
        sampler0.addProperty("output", 1);

        JsonObject sampler1 = new JsonObject();
        sampler1.addProperty("input", 0);
        sampler1.addProperty("interpolation", "LINEAR");
        sampler1.addProperty("output", 2);

        JsonObject sampler2 = new JsonObject();
        sampler2.addProperty("input", 0);
        sampler2.addProperty("interpolation", "LINEAR");
        sampler2.addProperty("output", 3);

        samplersArray.add(sampler0);
        samplersArray.add(sampler1);
        samplersArray.add(sampler2);

        animationObject.add("samplers", samplersArray);

        animationsArray.add(animationObject);

        gltfFile.add("animations", animationsArray);

        // accessors
        JsonArray accessorsArray = new JsonArray();

        JsonObject accessor0 = new JsonObject();
        accessor0.addProperty("bufferView", 0);
        accessor0.addProperty("componentType", 5126);
        accessor0.addProperty("count", cameraPath.getPositions().size());
        JsonArray maxArray0 = new JsonArray();
        maxArray0.add(cameraPath.getPositions().size() / exportGLTF.frameRate());
        accessor0.add("max", maxArray0);
        JsonArray minArray0 = new JsonArray();
        minArray0.add(0);
        accessor0.add("min", minArray0);
        accessor0.addProperty("type", "SCALAR");

        JsonObject accessor1 = new JsonObject();
        accessor1.addProperty("bufferView", 1);
        accessor1.addProperty("componentType", 5126);
        accessor1.addProperty("count", cameraPath.getPositions().size());
        accessor1.addProperty("type", "VEC3");

        JsonObject accessor2 = new JsonObject();
        accessor2.addProperty("bufferView", 2);
        accessor2.addProperty("componentType", 5126);
        accessor2.addProperty("count", cameraPath.getPositions().size());
        accessor2.addProperty("type", "VEC4");

        JsonObject accessor3 = new JsonObject();
        accessor3.addProperty("bufferView", 3);
        accessor3.addProperty("componentType", 5126); // FLOAT
        accessor3.addProperty("count", cameraPath.getFovs().size());
        JsonArray maxArray3 = new JsonArray();
        maxArray3.add(2.0); // max FOV value in radians (115 degrees)
        accessor3.add("max", maxArray3);
        JsonArray minArray3 = new JsonArray();
        minArray3.add(0.5); // min FOV value in radians (28 degrees)
        accessor3.add("min", minArray3);
        accessor3.addProperty("type", "SCALAR");

        accessorsArray.add(accessor0);
        accessorsArray.add(accessor1);
        accessorsArray.add(accessor2);
        accessorsArray.add(accessor3);

        gltfFile.add("accessors", accessorsArray);

        // bufferViews
        JsonArray bufferViews = new JsonArray();

        // time buffer
        int timeBufferOffset = 0;
        int timeBufferSize = 4 * cameraPath.getPositions().size();
        JsonObject timeBuffer = new JsonObject();
        timeBuffer.addProperty("buffer", 0);
        timeBuffer.addProperty("byteLength", timeBufferSize);
        timeBuffer.addProperty("byteOffset", timeBufferOffset);

        // translation buffer
        int translationBufferOffset = timeBufferSize;
        int translationBufferSize = 4 * 3 * cameraPath.getPositions().size();
        JsonObject translationBuffer = new JsonObject();
        translationBuffer.addProperty("buffer", 0);
        translationBuffer.addProperty("byteLength", translationBufferSize);
        translationBuffer.addProperty("byteOffset", translationBufferOffset);

        // rotation buffer byteLength = 4 * 4 * cameraPath.rotations().size
        int rotationBufferOffset = timeBufferSize + translationBufferSize;
        int rotationBufferSize = 4 * 4 * cameraPath.getRotations().size();
        JsonObject rotationBuffer = new JsonObject();
        rotationBuffer.addProperty("buffer", 0);
        rotationBuffer.addProperty("byteLength", rotationBufferSize);
        rotationBuffer.addProperty("byteOffset", rotationBufferOffset);

        // FOV buffer
        int fovBufferOffset = rotationBufferOffset + rotationBufferSize;
        int fovBufferSize = 4 * cameraPath.getFovs().size(); // 4 bytes for each float
        JsonObject fovBuffer = new JsonObject();
        fovBuffer.addProperty("buffer", 0);
        fovBuffer.addProperty("byteLength", fovBufferSize);
        fovBuffer.addProperty("byteOffset", fovBufferOffset);

        bufferViews.add(timeBuffer);
        bufferViews.add(translationBuffer);
        bufferViews.add(rotationBuffer);
        bufferViews.add(fovBuffer);

        gltfFile.add("bufferViews", bufferViews);

        // Create base64 data
        String data = createBase64Data(cameraPath);

        // Buffers
        JsonArray buffers = new JsonArray();
        JsonObject buffer = new JsonObject();
        buffer.addProperty("byteLength", timeBufferSize + translationBufferSize + rotationBufferSize + fovBufferSize);
        buffer.addProperty("uri", data);
        buffers.add(buffer);

        gltfFile.add("buffers", buffers);

        return gltfFile;
    }

    private String createBase64Data(DiscreteCameraPath cameraPath){
        String header = "data:application/octet-stream;base64,";

        // time data = 0 -> end in seconds
        byte[] timeData = new byte[4 * (cameraPath.getPositions().size())];
        for(int i = 0;i<cameraPath.getPositions().size();i++){
            float time = (float) (((float) i) * (1 / exportGLTF.frameRate())); // step by 1 / frameRate

            byte[] timeBinary = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(time).array();
            System.arraycopy(timeBinary, 0, timeData, i * 4, 4);
        }

        // translation buffer
        byte[] translationData = new byte[4 * 3 * (cameraPath.getPositions().size())];
        for(int i = 0;i<cameraPath.getPositions().size();i++){
            float translationX = cameraPath.getPositions().get(i).x;
            float translationY = cameraPath.getPositions().get(i).y;
            float translationZ = cameraPath.getPositions().get(i).z;

            byte[] translationBinary = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                    .putFloat(translationX)
                    .putFloat(translationY)
                    .putFloat(translationZ)
                    .array();

            System.arraycopy(translationBinary, 0, translationData, i * 12, 12);
        }

        // rotation buffer
        byte[] rotationData = new byte[4 * 4 * (cameraPath.getRotations().size())];
        for(int i = 0;i<cameraPath.getRotations().size();i++){

            Quaternionf rolledQuaternion = new Quaternionf(cameraPath.getRotations().get(i));
            float rotationX = rolledQuaternion.x;
            float rotationY = rolledQuaternion.y;
            float rotationZ = rolledQuaternion.z;
            float rotationW = rolledQuaternion.w;

            byte[] rotationBinary = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                    .putFloat(rotationX)
                    .putFloat(rotationY)
                    .putFloat(rotationZ)
                    .putFloat(rotationW)
                    .array();

            System.arraycopy(rotationBinary, 0, rotationData, i * 16, 16);
        }

        // FOV buffer
        byte[] fovData = new byte[4 * (cameraPath.getFovs().size())];
        for(int i = 0; i < cameraPath.getFovs().size(); i++){
            float fov = cameraPath.getFovs().get(i);

            byte[] fovBinary = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                    .putFloat(fov)
                    .array();

            System.arraycopy(fovBinary, 0, fovData, i * 4, 4);
        }

        int totalLength = timeData.length + translationData.length + rotationData.length + fovData.length;
        byte[] combinedData = new byte[totalLength];

        int currentPos = 0;
        System.arraycopy(timeData, 0, combinedData, currentPos, timeData.length);
        currentPos += timeData.length;

        System.arraycopy(translationData, 0, combinedData, currentPos, translationData.length);
        currentPos += translationData.length;

        System.arraycopy(rotationData, 0, combinedData, currentPos, rotationData.length);
        currentPos += rotationData.length;

        System.arraycopy(fovData, 0, combinedData, currentPos, fovData.length);

        return header + Base64.getEncoder().encodeToString(combinedData);
    }

    public void addToPath(Vector3f position, Quaternionf rotation, float fov){
        discreteCameraPath.addPositionAndRotation(position, rotation);
        discreteCameraPath.addFov(fov);
    }

}
