package nl.knokko.customitems.resourcepack;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import nl.knokko.customitems.bithelper.BitInputStream;
import nl.knokko.customitems.editor.resourcepack.ResourcepackGenerator;
import nl.knokko.customitems.itemset.ItemSet;
import nl.knokko.customitems.settings.ExportSettings;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static nl.knokko.customitems.MCVersions.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestResourcepackGenerator {

    @Test
    public void testMinecraft26PackMetadata() throws Exception {
        for (int version : new int[] { VERSION26_1_2, VERSION26_2, VERSION26_3 }) {
            ItemSet set = new ItemSet(ItemSet.Side.EDITOR);
            ExportSettings settings = new ExportSettings(true);
            settings.setMcVersion(version);
            settings.setMode(ExportSettings.Mode.MANUAL);
            set.setExportSettings(settings);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            new ResourcepackGenerator(set).write(bytes, null, null, true);
            boolean found = false;
            try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                ZipEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    if (!entry.getName().equals("pack.mcmeta")) continue;
                    found = true;
                    JsonObject metadata = (JsonObject) Jsoner.deserialize(new InputStreamReader(input));
                    JsonObject pack = (JsonObject) metadata.get("pack");
                    int format = version == VERSION26_3 ? 97 : version == VERSION26_2 ? 88 : 84;
                    assertEquals(format, ((Number) pack.get("pack_format")).intValue());
                    if (version == VERSION26_3) {
                        assertEquals(Jsoner.deserialize("[97,1]"), pack.get("min_format"));
                        assertEquals(Jsoner.deserialize("[97,1]"), pack.get("max_format"));
                    } else {
                        assertEquals(format, ((Number) pack.get("min_format")).intValue());
                        assertEquals(format, ((Number) pack.get("max_format")).intValue());
                    }
                }
            }
            assertTrue(found);
        }
    }

    @Test
    public void testBasicResourcepackValidity() {
        assertValidResourcepack("item-sets/backward12old.cisb", VERSION1_12);

        int[] newVersions = { VERSION1_14, VERSION1_15, VERSION1_16, VERSION1_17, VERSION1_18, VERSION1_19, VERSION1_20 };
        for (int version : newVersions) assertValidResourcepack("item-sets/backward12new.cisb", version);
    }

    private void assertValidResourcepack(String path, int mcVersion) {
        try {
            InputStream setInput = TestResourcepackGenerator.class.getClassLoader().getResourceAsStream(path);
            ItemSet itemSet = new ItemSet(new BitInputStream(setInput), ItemSet.Side.EDITOR, false);
            setInput.close();

            ExportSettings exportSettings = itemSet.getExportSettings().copy(true);
            exportSettings.setMcVersion(mcVersion);
            exportSettings.setMode(ExportSettings.Mode.MANUAL);
            itemSet.setExportSettings(exportSettings);

            ByteArrayOutputStream rememberOutput = new ByteArrayOutputStream();
            new ResourcepackGenerator(itemSet).write(rememberOutput, new byte[0], null, true);

            int jsonCounter = 0;
            int pngCounter = 0;
            int packCounter = 0;
            ZipInputStream resourceInput = new ZipInputStream(new ByteArrayInputStream(rememberOutput.toByteArray()));

            ZipEntry entry = resourceInput.getNextEntry();
            while (entry != null) {
                if (entry.getName().contains("pack.mcmeta")) {
                    packCounter += 1;
                    assertInstanceOf(JsonObject.class, Jsoner.deserialize(new InputStreamReader(resourceInput)));
                } else if (entry.getName().endsWith(".png")) {
                    pngCounter += 1;
                    assertNotNull(ImageIO.read(resourceInput));
                } else if (entry.getName().endsWith(".json")) {
                    jsonCounter += 1;
                    assertInstanceOf(JsonObject.class, Jsoner.deserialize(new InputStreamReader(resourceInput)));
                }
                entry = resourceInput.getNextEntry();
            }

            resourceInput.close();

            assertEquals(1, packCounter);
            assertTrue(jsonCounter > 2);
            assertTrue(pngCounter > 2);
        } catch (Exception exception) {
            fail(exception);
        }
    }
}
