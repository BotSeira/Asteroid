package xyz.zcraft.asteroid.util;

public record UploadedImage(String url, int width, int height) {
    public String toMarkdown() {
        return "![image #" + width + "px #" + height + "px](" + url + ")";
    }

    public String toMarkdown(int width, int height) {
        return "![image #" + width + "px #" + height + "px](" + url + ")";
    }
}
