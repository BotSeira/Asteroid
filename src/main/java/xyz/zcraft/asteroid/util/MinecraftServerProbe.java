package xyz.zcraft.asteroid.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MinecraftServerProbe {
    private static final Gson GSON = new Gson();

    public static Result probe(String host, int port) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(5000);

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // Handshake
            ByteArrayOutputStream handshakeData = new ByteArrayOutputStream();
            DataOutputStream handshake = new DataOutputStream(handshakeData);

            writeVarInt(handshake, 0x00); // packet id

            // Protocol version isn't important for a status request in practice.
            writeVarInt(handshake, -1);

            writeString(handshake, host);
            handshake.writeShort(port);

            writeVarInt(handshake, 1); // next state = status

            writePacket(out, handshakeData.toByteArray());

            // Status Request
            ByteArrayOutputStream requestData = new ByteArrayOutputStream();
            DataOutputStream request = new DataOutputStream(requestData);

            writeVarInt(request, 0x00);

            writePacket(out, requestData.toByteArray());

            // Status Response
            int packetLength = readVarInt(in);
            int packetId = readVarInt(in);

            if (packetId != 0x00) {
                throw new IOException(
                        "Unexpected packet id: " + packetId
                );
            }

            int jsonLength = readVarInt(in);
            byte[] jsonBytes = in.readNBytes(jsonLength);

            if (jsonBytes.length != jsonLength) {
                throw new EOFException("Incomplete status response");
            }

            String json = new String(jsonBytes, StandardCharsets.UTF_8);

            JsonObject status = JsonParser.parseString(json).getAsJsonObject();

            // Ping
            long payload = System.currentTimeMillis();

            ByteArrayOutputStream pingData = new ByteArrayOutputStream();
            DataOutputStream ping = new DataOutputStream(pingData);

            writeVarInt(ping, 0x01);
            ping.writeLong(payload);

            long start = System.nanoTime();

            writePacket(out, pingData.toByteArray());

            // Pong
            readVarInt(in); // packet length

            int pongPacketId = readVarInt(in);

            if (pongPacketId != 0x01) {
                throw new IOException(
                        "Unexpected pong packet id: " + pongPacketId
                );
            }

            DataInputStream dataIn = new DataInputStream(in);
            long returnedPayload = dataIn.readLong();

            long latencyMs =
                    (System.nanoTime() - start) / 1_000_000;

            if (returnedPayload != payload) {
                throw new IOException("Invalid pong payload");
            }

            return new Result(host, port, latencyMs, GSON.fromJson(status, Status.class));
        }
    }

    private static void writePacket(OutputStream out, byte[] data) throws IOException {
        writeVarInt(out, data.length);
        out.write(data);
        out.flush();
    }

    private static void writeString(OutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeVarInt(OutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.write(value);
                return;
            }

            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        int position = 0;

        while (true) {
            int currentByte = in.read();

            if (currentByte == -1) {
                throw new EOFException();
            }

            value |= (currentByte & 0x7F) << position;

            if ((currentByte & 0x80) == 0) {
                break;
            }

            position += 7;

            if (position >= 32) {
                throw new IOException("VarInt is too big");
            }
        }

        return value;
    }

    public record Result(
            String host,
            int port,
            long latencyMs,
            Status status
    ) {
    }

    public record Status(
            Version version,
            Players players,
            JsonElement description,
            String favicon
    ) {
        private static String extractText(JsonElement element) {
            if (element == null || element.isJsonNull()) {
                return "";
            }

            if (element.isJsonPrimitive()) {
                return element.getAsString();
            }

            if (element.isJsonArray()) {
                StringBuilder result = new StringBuilder();

                for (JsonElement child : element.getAsJsonArray()) {
                    result.append(extractText(child));
                }

                return result.toString();
            }

            JsonObject object = element.getAsJsonObject();

            StringBuilder result = new StringBuilder();

            if (object.has("text")) {
                result.append(extractText(object.get("text")));
            }

            if (object.has("extra")) {
                result.append(extractText(object.get("extra")));
            }

            return result.toString();
        }

        public String descriptionText() {
            return extractText(description).replaceAll("§.", "");
        }
    }

    public record Version(
            String name,
            Integer protocol
    ) {
    }

    public record Players(
            Long max,
            Long online,
            List<Sample> samples
    ) {
        public record Sample(
                String id,
                String name
        ) {
        }
    }
}