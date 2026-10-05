package com.beetle.room;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Typed payloads for requests and responses used by the room signaling protocol.
 */
public final class RoomProtocol {
    private RoomProtocol() {
    }

    public interface Payload {
        JSONObject toJson() throws JSONException;
    }

    public static final class CreateTransportRequest implements Payload {
        private final boolean producing;
        private final boolean consuming;

        public CreateTransportRequest(boolean producing, boolean consuming) {
            this.producing = producing;
            this.consuming = consuming;
        }

        @Override
        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("forceTcp", false);
            json.put("producing", producing);
            json.put("consuming", consuming);
            return json;
        }
    }

    public static final class TransportResponse {
        public final String id;
        public final String iceParameters;
        public final String iceCandidates;
        public final String dtlsParameters;

        private TransportResponse(JSONObject json) throws JSONException {
            id = json.getString("id");
            iceParameters = json.get("iceParameters").toString();
            iceCandidates = json.get("iceCandidates").toString();
            dtlsParameters = json.get("dtlsParameters").toString();
        }

        public static TransportResponse fromJson(String data) throws JSONException {
            return new TransportResponse(new JSONObject(data));
        }
    }

    public static final class ConnectTransportRequest implements Payload {
        private final String transportId;
        private final DtlsRole role;
        private final String algorithm;
        private final String fingerprint;

        public ConnectTransportRequest(String transportId, DtlsRole role, String algorithm, String fingerprint) {
            this.transportId = transportId;
            this.role = role;
            this.algorithm = algorithm;
            this.fingerprint = fingerprint;
        }

        @Override
        public JSONObject toJson() throws JSONException {
            JSONObject value = new JSONObject();
            value.put("algorithm", algorithm);
            value.put("value", fingerprint);

            JSONObject dtlsParameters = new JSONObject();
            dtlsParameters.put("role", role.wireValue());
            dtlsParameters.put("fingerprints", new JSONArray().put(value));

            JSONObject json = new JSONObject();
            json.put("transportId", transportId);
            json.put("dtlsParameters", dtlsParameters);
            return json;
        }
    }

    public static final class JoinRequest implements Payload {
        private final String displayName;
        private final RoomClientConfig.DeviceInfo device;
        private final JSONObject rtpCapabilities;

        public JoinRequest(String displayName, RoomClientConfig.DeviceInfo device, JSONObject rtpCapabilities) {
            this.displayName = displayName;
            this.device = device;
            this.rtpCapabilities = rtpCapabilities;
        }

        @Override
        public JSONObject toJson() throws JSONException {
            JSONObject deviceJson = new JSONObject();
            deviceJson.put("flag", device.getFlag());
            deviceJson.put("name", device.getName());
            deviceJson.put("version", device.getVersion());

            JSONObject json = new JSONObject();
            json.put("displayName", displayName);
            json.put("device", deviceJson);
            json.put("produceVideo", true);
            json.put("produceAudio", true);
            json.put("rtpCapabilities", rtpCapabilities);
            return json;
        }
    }

    public static final class JoinResponse {
        public final List<Peer> peers;

        private JoinResponse(JSONObject json) throws JSONException {
            JSONArray items = json.getJSONArray("peers");
            List<Peer> parsed = new ArrayList<>();
            for (int i = 0; i < items.length(); i++)
                parsed.add(Peer.fromJson(items.getJSONObject(i)));
            peers = Collections.unmodifiableList(parsed);
        }

        public static JoinResponse fromJson(String data) throws JSONException {
            return new JoinResponse(new JSONObject(data));
        }
    }

    public static final class Peer {
        public final String id;
        public final List<String> producerIds;

        private Peer(String id, List<String> producerIds) {
            this.id = id;
            this.producerIds = Collections.unmodifiableList(producerIds);
        }

        static Peer fromJson(JSONObject json) throws JSONException {
            JSONArray producers = json.getJSONArray("producers");
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < producers.length(); i++)
                ids.add(producers.getJSONObject(i).getString("id"));
            return new Peer(json.getString("id"), ids);
        }
    }

    public static final class ProduceRequest implements Payload {
        private final String transportId;
        private final MediaKind kind;
        private final JSONObject rtpParameters;

        public ProduceRequest(String transportId, MediaKind kind, JSONObject rtpParameters) {
            this.transportId = transportId;
            this.kind = kind;
            this.rtpParameters = rtpParameters;
        }

        @Override
        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("transportId", transportId);
            json.put("kind", kind.wireValue());
            json.put("rtpParameters", rtpParameters);
            return json;
        }
    }

    public static final class ProduceResponse {
        public final String id;

        private ProduceResponse(JSONObject json) throws JSONException {
            id = json.getString("id");
        }

        public static ProduceResponse fromJson(String data) throws JSONException {
            return new ProduceResponse(new JSONObject(data));
        }
    }

    public static JSONObject videoCodecOptions(RoomClientConfig.VideoCodecOptions options) throws JSONException {
        return new JSONObject().put("videoGoogleStartBitrate", options.getGoogleStartBitrateKbps());
    }

    public static JSONObject audioCodecOptions(RoomClientConfig.AudioCodecOptions options) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("opusStereo", options.isOpusStereo());
        json.put("opusDtx", options.isOpusDtx());
        return json;
    }
}
