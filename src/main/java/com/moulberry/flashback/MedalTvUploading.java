package com.moulberry.flashback;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.exporting.ExportSettings;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MedalTvUploading {

    public enum CannotUploadReason {
        UNSUPPORTED_FORMAT,
        FILE_MISSING,
        ERROR_CHECKING_FILE,
        OVER_2GB
    }

    public static @Nullable CannotUploadReason checkCanUpload(ExportSettings exportSettings) {
        switch (exportSettings.container()) {
            case MP4, MKV, WEBP, GIF -> {
            }
            default -> {
                return CannotUploadReason.UNSUPPORTED_FORMAT;
            }
        }

        try {
            Path output = exportSettings.output();
            if (!Files.exists(output) || !Files.isRegularFile(output)) {
                return CannotUploadReason.FILE_MISSING;
            }

            long size = Files.size(output);

            if (size <= 0) {
                return CannotUploadReason.ERROR_CHECKING_FILE;
            }
            if (size > 2_000_000_000) { // 2GB
                return CannotUploadReason.OVER_2GB;
            }
        } catch (IOException e) {
            return CannotUploadReason.ERROR_CHECKING_FILE;
        }

        return null;
    }

    public static class UploadStatus {
        public long startTime = System.currentTimeMillis();
        public int progressPercentage = 0;
        public boolean finished = false;
        public boolean shouldCancel = false;
        public String errorMessage = null;
        public Throwable throwable = null;
        public URI shareUrl = null;
    }

    public static UploadStatus upload(ExportSettings exportSettings) {
        UploadStatus uploadStatus = new UploadStatus();

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(exportSettings.output());
        } catch (IOException e) {
            uploadStatus.throwable = e;
            uploadStatus.finished = true;
            return uploadStatus;
        }

        String title = exportSettings.name();
        if (title == null) {
            title = exportSettings.output().getFileName().toString();
        }
        final String titleF = title;

        Util.ioPool().execute(() -> {
            try (var httpClient = HttpClients.custom()
                    .setUserAgent("Flashback (+https://modrinth.com/mod/flashback)")
                    .build()) {
                Gson gson = new Gson();

                String uploadUrl = createUploadRequest(gson, httpClient, uploadStatus, titleF, bytes.length, exportSettings);
                if (uploadUrl == null) {
                    return;
                }

                if (uploadStatus.shouldCancel) {
                    return;
                }

                sendContent(httpClient, uploadStatus, uploadUrl, bytes, exportSettings);
            } catch (Throwable t) {
                uploadStatus.throwable = t;
            } finally {
                uploadStatus.finished = true;
            }
        });

        return uploadStatus;
    }

    private static @Nullable String createUploadRequest(Gson gson, CloseableHttpClient httpClient, UploadStatus uploadStatus,
                                                        String title, long length, ExportSettings exportSettings) throws IOException, URISyntaxException {
        var post = new HttpPost("https://medal.tv/flashback-upload");

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("contentLength", length);
        jsonObject.addProperty("contentType", exportSettings.container().mimeType());
        jsonObject.addProperty("contentTitle", title);
        String requestContent = gson.toJson(jsonObject);
        post.setEntity(new StringEntity(requestContent, ContentType.APPLICATION_JSON));

        var response = httpClient.execute(post);

        JsonObject resultJson;

        try {
            String resultStr = EntityUtils.toString(response.getEntity(), "UTF-8");
            resultJson = gson.fromJson(resultStr, JsonObject.class);
        } catch (JsonSyntaxException e) {
            uploadStatus.errorMessage = "FlashbackUpload did not return a valid JSON response";
            return null;
        }

        if (resultJson.has("error")) {
            uploadStatus.errorMessage = "FlashbackUpload Error: " + resultJson.get("error").getAsString();
            return null;
        }

        if (!resultJson.has("shareUrl")) {
            uploadStatus.errorMessage = "FlashbackUpload Error: Missing shareUrl";
            return null;
        }

        if (!resultJson.has("signedUrl")) {
            uploadStatus.errorMessage = "FlashbackUpload Error: Missing signedUrl";
            return null;
        }

        uploadStatus.progressPercentage = 10;
        uploadStatus.shareUrl = Util.parseAndValidateUntrustedUri(resultJson.get("shareUrl").getAsString());
        return resultJson.get("signedUrl").getAsString();
    }

    private static void sendContent(CloseableHttpClient httpClient, UploadStatus uploadStatus, String contentUrl, byte[] data, ExportSettings exportSettings) throws IOException {
        var put = new HttpPut(contentUrl);

        String contentType = exportSettings.container().mimeType();

        put.setEntity(new HttpEntity() {
            @Override
            public boolean isRepeatable() {
                return false;
            }

            @Override
            public boolean isChunked() {
                return false;
            }

            @Override
            public long getContentLength() {
                return data.length;
            }

            @Override
            public Header getContentType() {
                return new BasicHeader(HTTP.CONTENT_TYPE, contentType);
            }

            @Override
            public Header getContentEncoding() {
                return null;
            }

            @Override
            public InputStream getContent() {
                return new ByteArrayInputStream(data);
            }

            @Override
            public void writeTo(OutputStream outputStream) throws IOException {
                final int minChunkSize = 1000*1000;

                if (uploadStatus.shouldCancel) {
                    throw new RuntimeException("Upload cancelled");
                }

                int position = 0;
                int limit = data.length;
                while (uploadStatus.progressPercentage < 89) {
                    int remainingProgress = 90 - uploadStatus.progressPercentage;
                    int fractionalBytes = ((limit - position) + (remainingProgress - 1)) / remainingProgress;

                    int toWrite = Math.min(limit - position, Math.max(minChunkSize, fractionalBytes));
                    outputStream.write(data, position, toWrite);
                    position += toWrite;

                    uploadStatus.progressPercentage = (int)(10L + 80L*position/limit);
                }

                if (position < limit) {
                    outputStream.write(data, position, limit - position);
                    uploadStatus.progressPercentage = 90;
                }
            }

            @Override
            public boolean isStreaming() {
                return false;
            }

            @Override
            public void consumeContent() {
            }
        });

        var response = httpClient.execute(put);

        if (response.getStatusLine().getStatusCode() != 200) {
            uploadStatus.errorMessage = "SendContent: Error while sending content to Google Cloud Storage";
            return;
        }

        uploadStatus.progressPercentage = 100;

    }

}
