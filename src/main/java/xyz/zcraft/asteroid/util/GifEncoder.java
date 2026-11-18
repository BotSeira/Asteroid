
package xyz.zcraft.asteroid.util;

import com.microsoft.playwright.Page;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;

public final class GifEncoder {
    public static byte[] capture(Page page, int fps, int frames) {
        if (fps < 1 || fps > 50 || frames < 1 || frames > 300) {
            throw new IllegalArgumentException(
                    "Invalid GIF parameters"
            );
        }

        page.waitForFunction("""
                    () => window.burning?.ready === true
                """);

        page.evaluate("window.burning.stop()");

        var canvas = page.locator("canvas");

        Iterator<ImageWriter> writers =
                ImageIO.getImageWritersByFormatName("gif");

        if (!writers.hasNext()) {
            throw new IllegalStateException(
                    "No GIF ImageWriter available"
            );
        }

        ImageWriter writer = writers.next();

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);

            ImageWriteParam params = writer.getDefaultWriteParam();
            writer.prepareWriteSequence(null);

            int delay = Math.max(
                    2,
                    (int) Math.round(100.0 / fps)
            );

            for (int i = 0; i < frames; i++) {
                if (i > 0) {
                    page.evaluate("window.burning.nextFrame()");
                }

                byte[] png = canvas.screenshot();

                BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));

                if (source == null) {
                    throw new IOException("Cannot decode PNG frame");
                }

                BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);

                Graphics2D g = image.createGraphics();
                try {
                    g.setColor(Color.BLACK);
                    g.fillRect(
                            0, 0,
                            image.getWidth(),
                            image.getHeight()
                    );
                    g.drawImage(source, 0, 0, null);
                } finally {
                    g.dispose();
                }

                ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(image);

                IIOMetadata metadata = writer.getDefaultImageMetadata(type, params);

                String format = metadata.getNativeMetadataFormatName();

                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);

                IIOMetadataNode control = getNode(root, "GraphicControlExtension");

                control.setAttribute("disposalMethod", "none");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", String.valueOf(delay));
                control.setAttribute("transparentColorIndex", "0");

                if (i == 0) {
                    IIOMetadataNode extensions = getNode(
                            root,
                            "ApplicationExtensions"
                    );

                    IIOMetadataNode extension =
                            new IIOMetadataNode("ApplicationExtension");

                    extension.setAttribute("applicationID", "NETSCAPE");
                    extension.setAttribute("authenticationCode", "2.0");
                    extension.setUserObject(new byte[]{1, 0, 0});

                    extensions.appendChild(extension);
                }

                metadata.setFromTree(format, root);

                writer.writeToSequence(
                        new IIOImage(image, null, metadata),
                        params
                );
            }

            writer.endWriteSequence();
            output.flush();

            return bytes.toByteArray();

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
    }

    private static IIOMetadataNode getNode(IIOMetadataNode root, String name) {
        for (var node = root.getFirstChild();
             node != null;
             node = node.getNextSibling()) {

            if (name.equals(node.getNodeName())) {
                return (IIOMetadataNode) node;
            }
        }

        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);

        return node;
    }
}
