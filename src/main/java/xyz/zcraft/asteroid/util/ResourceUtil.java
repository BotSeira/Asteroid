package xyz.zcraft.asteroid.util;

import java.io.IOException;
import java.io.InputStream;

public class ResourceUtil {
    public static InputStream getResourceAsStream(String name) throws IOException {
        return ResourceUtil.class.getResourceAsStream(name);
    }

    public static byte[] getResourceAsBytes(String name) throws IOException {
        try (InputStream is = getResourceAsStream(name)) {
            return is.readAllBytes();
        }
    }
}
