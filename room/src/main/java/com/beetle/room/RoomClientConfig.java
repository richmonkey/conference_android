package com.beetle.room;

import java.util.Objects;

/**
 * Immutable configuration for a room session. Defaults preserve the legacy
 * client behaviour while making all protocol and capture choices explicit.
 */
public final class RoomClientConfig {
    private final VideoCaptureSpec videoCapture;
    private final VideoCodecOptions videoCodec;
    private final AudioCodecOptions audioCodec;
    private final DeviceInfo deviceInfo;

    private RoomClientConfig(Builder builder) {
        videoCapture = builder.videoCapture;
        videoCodec = builder.videoCodec;
        audioCodec = builder.audioCodec;
        deviceInfo = builder.deviceInfo;
    }

    public static RoomClientConfig defaultConfig() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public VideoCaptureSpec getVideoCapture() { return videoCapture; }
    public VideoCodecOptions getVideoCodec() { return videoCodec; }
    public AudioCodecOptions getAudioCodec() { return audioCodec; }
    public DeviceInfo getDeviceInfo() { return deviceInfo; }

    public static final class Builder {
        private VideoCaptureSpec videoCapture = new VideoCaptureSpec(640, 480, 30);
        private VideoCodecOptions videoCodec = new VideoCodecOptions(1000);
        private AudioCodecOptions audioCodec = new AudioCodecOptions(true, true);
        private DeviceInfo deviceInfo = new DeviceInfo("android-native", "android", "113");

        public Builder videoCapture(VideoCaptureSpec value) { videoCapture = require(value, "videoCapture"); return this; }
        public Builder videoCodec(VideoCodecOptions value) { videoCodec = require(value, "videoCodec"); return this; }
        public Builder audioCodec(AudioCodecOptions value) { audioCodec = require(value, "audioCodec"); return this; }
        public Builder deviceInfo(DeviceInfo value) { deviceInfo = require(value, "deviceInfo"); return this; }
        public RoomClientConfig build() { return new RoomClientConfig(this); }
    }

    public static final class VideoCaptureSpec {
        private final int width;
        private final int height;
        private final int fps;
        public VideoCaptureSpec(int width, int height, int fps) {
            if (width <= 0 || height <= 0 || fps <= 0) throw new IllegalArgumentException("Video capture dimensions and fps must be positive");
            this.width = width; this.height = height; this.fps = fps;
        }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getFps() { return fps; }
    }

    public static final class VideoCodecOptions {
        private final int googleStartBitrateKbps;
        public VideoCodecOptions(int googleStartBitrateKbps) {
            if (googleStartBitrateKbps <= 0) throw new IllegalArgumentException("Video bitrate must be positive");
            this.googleStartBitrateKbps = googleStartBitrateKbps;
        }
        public int getGoogleStartBitrateKbps() { return googleStartBitrateKbps; }
    }

    public static final class AudioCodecOptions {
        private final boolean opusStereo;
        private final boolean opusDtx;
        public AudioCodecOptions(boolean opusStereo, boolean opusDtx) { this.opusStereo = opusStereo; this.opusDtx = opusDtx; }
        public boolean isOpusStereo() { return opusStereo; }
        public boolean isOpusDtx() { return opusDtx; }
    }

    public static final class DeviceInfo {
        private final String flag;
        private final String name;
        private final String version;
        public DeviceInfo(String flag, String name, String version) {
            this.flag = require(flag, "flag"); this.name = require(name, "name"); this.version = require(version, "version");
        }
        public String getFlag() { return flag; }
        public String getName() { return name; }
        public String getVersion() { return version; }
    }

    private static <T> T require(T value, String name) { return Objects.requireNonNull(value, name); }
}
