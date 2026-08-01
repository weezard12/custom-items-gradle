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
import java.util.Locale;
import java.util.concurrent.TimeUnit;
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

    @Test
    public void testShadedJarsSupportMinecraft261And262() throws IOException, InterruptedException {
        Path embeddedJar = getJar("yamlResourcepackCliJar");
        Path sourceJar = getJar("yamlSourceResourcepackCliJar");

        verifyShadedJar("embedded-jar", embeddedJar, "26.1.2", "GOLDEN_DANDELION", "golden_dandelion", 84);
        verifyShadedJar("source-jar", sourceJar, "26.1.2", "GOLDEN_DANDELION", "golden_dandelion", 84);
        verifyShadedJar("embedded-jar", embeddedJar, "26.2", "CINNABAR", "cinnabar", 88);
        verifyShadedJar("source-jar", sourceJar, "26.2", "CINNABAR", "cinnabar", 88);
    }

    private Path getJar(String propertyName) {
        String rawPath = System.getProperty(propertyName);
        assertNotNull(rawPath, "Missing test system property " + propertyName);
        Path path = Path.of(rawPath);
        assertTrue(Files.isRegularFile(path), "Missing shaded CLI jar " + path);
        return path;
    }

    private void verifyShadedJar(
            String runnerName, Path jar, String mcVersion, String material, String itemName, int packFormat
    ) throws IOException, InterruptedException {
        Path projectRoot = createProject(runnerName, mcVersion, material, itemName);
        Path outputPath = tempDir.resolve(runnerName + "-" + mcVersion.replace('.', '-') + ".zip");
        String javaName = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe" : "java";
        Path javaPath = Path.of(System.getProperty("java.home"), "bin", javaName);

        Process process = new ProcessBuilder(
                javaPath.toString(), "-jar", jar.toString(), projectRoot.toString(), outputPath.toString(),
                "--mc-version", mcVersion
        ).redirectErrorStream(true).start();
        boolean completed = process.waitFor(60, TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(completed, "CLI timed out:\n" + output);
        assertEquals(0, process.exitValue(), output);
        verifyResourcePack(outputPath, packFormat, itemName);
    }

    private void verifyVersion(
            String runnerName, CliRunner runner, String mcVersion, String material, String itemName, int packFormat
    ) throws IOException {
        String versionName = mcVersion != null ? mcVersion.replace('.', '-') : "default";
        Path projectRoot = createProject(runnerName, versionName, material, itemName);
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
        verifyResourcePack(outputPath, packFormat, itemName);
    }

    private Path createProject(String runnerName, String versionName, String material, String itemName) throws IOException {
        Path projectRoot = tempDir.resolve(runnerName + "-" + versionName.replace('.', '-'));
        Path packRoot = projectRoot.resolve("src/main/resources/customitems/demo");
        Path texturePath = packRoot.resolve("assets/item/" + itemName + ".png");
        Files.createDirectories(texturePath.getParent());

        Files.writeString(packRoot.resolve("item.yml"), ""
                + "item:\n"
                + "  id: \"demo:" + itemName + "\"\n"
                + "  name: \"" + itemName + "\"\n"
                + "  material: \"" + material + "\"\n");
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "png", texturePath.toFile());
        return projectRoot;
    }

    private void verifyResourcePack(Path outputPath, int packFormat, String itemName) throws IOException {
        assertTrue(Files.isRegularFile(outputPath), "Missing generated resource pack " + outputPath);

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
