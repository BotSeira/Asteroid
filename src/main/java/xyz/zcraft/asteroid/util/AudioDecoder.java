package xyz.zcraft.asteroid.util;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Arrays;

public class AudioDecoder {
    public static float[] decode(Path mp3File) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-hide_banner",
                "-loglevel", "error",
                "-i", mp3File.toAbsolutePath().toString(),
                "-t", "10",
                "-ac", "1",
                "-ar", "8000",
                "-f", "f32le",
                "pipe:1"
        );

        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        Process process = pb.start();

        byte[] bytes;
        try (var stream = process.getInputStream()) {
            bytes = stream.readAllBytes();
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("FFmpeg failed with exit code " + exitCode);
        }

        if (bytes.length % Float.BYTES != 0) {
            throw new IOException("Invalid PCM data length");
        }

        float[] samples = new float[bytes.length / Float.BYTES];

        ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asFloatBuffer()
                .get(samples);

        return Arrays.copyOf(samples, 80000);
    }
}
