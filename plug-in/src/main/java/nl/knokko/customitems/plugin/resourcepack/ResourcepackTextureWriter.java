package nl.knokko.customitems.plugin.resourcepack;

import nl.knokko.customitems.itemset.ItemSet;
import nl.knokko.customitems.texture.BowTexture;
import nl.knokko.customitems.texture.BowTextureEntry;
import nl.knokko.customitems.texture.CrossbowTexture;
import nl.knokko.customitems.texture.KciTexture;
import nl.knokko.customitems.texture.animated.AnimatedTexture;
import nl.knokko.customitems.texture.animated.AnimationFrame;
import nl.knokko.customitems.texture.animated.AnimationImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class ResourcepackTextureWriter {

    private final ItemSet itemSet;
    private final ZipOutputStream zipOutput;

    ResourcepackTextureWriter(ItemSet itemSet, ZipOutputStream zipOutput) {
        this.itemSet = itemSet;
        this.zipOutput = zipOutput;
    }

    private static class ScheduledResource {

        private final ZipEntry entry;
        private final Future<byte[]> data;

        ScheduledResource(ZipEntry entry, Future<byte[]> data) {
            this.entry = entry;
            this.data = data;
        }
    }

    private Future<byte[]> futureImage(ExecutorService threadPool, BufferedImage image) {
        return threadPool.submit(() -> {
            ByteArrayOutputStream memoryOutput = new ByteArrayOutputStream();
            try {
                ImageIO.write(image, "PNG", memoryOutput);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return memoryOutput.toByteArray();
        });
    }

    void writeBaseTextures() throws IOException {
        ExecutorService threadPool = Executors.newFixedThreadPool(20);
        List<ScheduledResource> resources = new ArrayList<>(itemSet.textures.size());

        for (KciTexture texture : itemSet.textures) {

            String baseTextureName = texture.getName();
            if (texture instanceof BowTexture || texture instanceof CrossbowTexture) {
                baseTextureName += "_standby";

                List<BowTextureEntry> pullTextures;
                if (texture instanceof BowTexture) {
                    pullTextures = ((BowTexture) texture).getPullTextures();
                } else {
                    pullTextures = ((CrossbowTexture) texture).getPullTextures();
                }

                for (int pullIndex = 0; pullIndex < pullTextures.size(); pullIndex++) {
                    ZipEntry entry = new ZipEntry("assets/minecraft/textures/customitems/" + texture.getName()
                            + "_pulling_" + pullIndex + ".png");
                    BufferedImage image = pullTextures.get(pullIndex).getImage();
                    resources.add(new ScheduledResource(entry, futureImage(threadPool, image)));
                }

                if (texture instanceof CrossbowTexture) {
                    CrossbowTexture cbt = (CrossbowTexture) texture;

                    ZipEntry arrowEntry = new ZipEntry("assets/minecraft/textures/customitems/" + cbt.getName()
                            + "_arrow.png");
                    resources.add(new ScheduledResource(arrowEntry, futureImage(threadPool, cbt.getArrowImage())));

                    ZipEntry fireworkEntry = new ZipEntry("assets/minecraft/textures/customitems/" + cbt.getName()
                            + "_firework.png");
                    resources.add(new ScheduledResource(fireworkEntry, futureImage(threadPool, cbt.getFireworkImage())));
                }
            }

            BufferedImage imageToExport;
            if (texture instanceof AnimatedTexture) {

                List<AnimationImage> images = ((AnimatedTexture) texture).copyImages(false);

                int baseWidth = images.get(0).getImageReference().getWidth();
                int baseHeight = images.get(0).getImageReference().getHeight();

                int totalHeight = images.size() * baseHeight;
                imageToExport = new BufferedImage(baseWidth, totalHeight, BufferedImage.TYPE_INT_ARGB);
                for (int x = 0; x < baseWidth; x++) {
                    for (int y = 0; y < totalHeight; y++) {
                        int imageIndex = y / baseHeight;
                        int baseY = y % baseHeight;
                        imageToExport.setRGB(x, y, images.get(imageIndex).getImageReference().getRGB(x, baseY));
                    }
                }

                Map<String, Integer> imageIndices = new HashMap<>(images.size());
                for (int index = 0; index < images.size(); index++) {
                    imageIndices.put(images.get(index).getLabel(), index);
                }

                ZipEntry entry = new ZipEntry("assets/minecraft/textures/customitems/" + baseTextureName + ".png.mcmeta");
                resources.add(new ScheduledResource(entry, threadPool.submit(() -> {
                    ByteArrayOutputStream memoryOutput = new ByteArrayOutputStream();
                    PrintWriter metaWriter = new PrintWriter(memoryOutput);
                    metaWriter.println("{");
                    metaWriter.println("    \"animation\": {");
                    metaWriter.println("        \"frames\": [");
                    List<AnimationFrame> frames = ((AnimatedTexture) texture).getFrames();
                    for (int index = 0; index < frames.size(); index++) {
                        AnimationFrame frame = frames.get(index);
                        metaWriter.print("            { \"index\": " + imageIndices.get(frame.getImageLabel()) + ", \"time\": " + frame.getDuration() + "}");
                        if (index != frames.size() - 1) {
                            metaWriter.print(",");
                        }
                        metaWriter.println();
                    }
                    metaWriter.println("        ]");
                    metaWriter.println("    }");
                    metaWriter.println("}");
                    metaWriter.flush();
                    return memoryOutput.toByteArray();
                })));
            } else {
                imageToExport = texture.getImage();
            }

            ZipEntry entry = new ZipEntry("assets/minecraft/textures/customitems/" + baseTextureName + ".png");
            resources.add(new ScheduledResource(entry, futureImage(threadPool, imageToExport)));
        }

        for (ScheduledResource resource : resources) {
            zipOutput.putNextEntry(resource.entry);
            try {
                zipOutput.write(resource.data.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new IOException(e);
            }
            zipOutput.closeEntry();
        }

        threadPool.shutdown();
    }
}
