package nl.knokko.customitems.plugin.yaml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestYamlResourcepackCli26 {

    @TempDir
    Path tempDir;

    @Test
    public void testBothClisSupportMinecraft261And262() throws IOException {
        verifyVersion("embedded", YamlResourcepackCli::run, "26.1.2", "GOLDEN_DANDELION", "golden_dandelion", 84);
        verifyVersion("source", YamlSourceResourcepackCli::run, "26.1.2", "GOLDEN_DANDELION", "golden_dandelion", 84);

        verifyVersion("embedded", YamlResourcepackCli::run, "26.2", "CINNABAR", "cinnabar", 88);
        verifyVersion("source", YamlSourceResourcepackCli::run, "26.2", "CINNABAR", "cinnabar", 88);
    }

    @Test
    public void testBothClisDefaultToMinecraft262() throws IOException {
        verifyVersion("embedded-default", YamlResourcepackCli::run, null, "CINNABAR", "cinnabar", 88);
        verifyVersion("source-default", YamlSourceResourcepackCli::run, null, "CINNABAR", "cinnabar", 88);
    }

    private void verifyVersion(
            String runnerName, CliRunner runner, String mcVersion, String material, String itemName, int packFormat
    ) throws IOException {
        String versionName = mcVersion != null ? mcVersion.replace('.', '-') : "default";
        Path projectRoot = tempDir.resolve(runnerName + "-" + versionName);
        Path packRoot = projectRoot.resolve("src/main/resources/customitems/demo");
        Path texturePath = packRoot.resolve("assets/item/" + itemName + ".png");
        Files.createDirectories(texturePath.getParent());

        Files.writeString(packRoot.resolve("item.yml"), ""
                + "item:\n"
                + "  id: \"demo:" + itemName + "\"\n"
                + "  name: \"" + itemName + "\"\n"
                + "  material: \"" + material + "\"\n");
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "png", texturePath.toFile());

        Path outputPath = tempDir.resolve(runnerName + "-" + versionName + ".zip");
        String[] args = mcVersion != null
                ? new String[] { projectRoot.toString(), outputPath.toString(), "--mc-version", mcVersion }
                : new String[] { projectRoot.toString(), outputPath.toString() };

        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode;
        try (
                PrintStream stdout = new PrintStream(stdoutBytes, true, StandardCharsets.UTF_8);
                PrintStream stderr = new PrintStream(stderrBytes, true, StandardCharsets.UTF_8)
        ) {
            exitCode = runner.run(args, stdout, stderr);
        }

        String output = stdoutBytes.toString(StandardCharsets.UTF_8)
                + System.lineSeparator() + stderrBytes.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode, output);
        assertTrue(Files.isRegularFile(outputPath), output);

        try (ZipFile zip = new ZipFile(outputPath.toFile())) {
            ZipEntry mcMetaEntry = zip.getEntry("pack.mcmeta");
            assertNotNull(mcMetaEntry);
            String mcMeta = new String(zip.getInputStream(mcMetaEntry).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(mcMeta.contains("\"pack_format\": " + packFormat), mcMeta);
            assertNotNull(zip.getEntry("assets/minecraft/models/customitems/demo_" + itemName + ".json"));
            assertNotNull(zip.getEntry("assets/minecraft/textures/customitems/demo_" + itemName + ".png"));
        }
    }

    @FunctionalInterface
    private interface CliRunner {
        int run(String[] args, PrintStream out, PrintStream err);
    }
}
