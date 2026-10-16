package nl.knokko.customitems.item.model;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import nl.knokko.customitems.block.model.CustomBlockModel;
import nl.knokko.customitems.projectile.cover.CustomProjectileCover;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static nl.knokko.customitems.MCVersions.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestModelFormat {

    private static final byte[] RAW_MODEL = ("{\"textures\":{},\"elements\":["
            + "{\"shade\":false},{\"shade\":true},{},"
            + "{\"shade\":false,\"shade_direction_override\":\"north\"}]}"
    ).getBytes(StandardCharsets.UTF_8);

    @Test
    public void testCustomItemsPreserveShadingAcrossVersions() throws Exception {
        ItemModel[] models = {
                new LegacyCustomItemModel(RAW_MODEL),
                new ModernCustomItemModel(RAW_MODEL, Collections.emptyList())
        };
        for (ItemModel model : models) {
            for (int version : new int[] { VERSION26_2, VERSION26_3 }) {
                assertShading(write(output -> model.write(output, "item", "texture", null, false, version)), version);
            }
        }
        assertTrue(new String(RAW_MODEL, StandardCharsets.UTF_8).contains("\"shade\":false"));
    }

    @Test
    public void testCustomBlocksAndProjectilesUseTargetFormat() throws Exception {
        ModernCustomItemModel model = new ModernCustomItemModel(RAW_MODEL, Collections.emptyList());
        CustomBlockModel block = new CustomBlockModel(model, null, null);
        CustomProjectileCover projectile = new CustomProjectileCover(true);
        projectile.setModel(model);
        for (int version : new int[] { VERSION26_2, VERSION26_3 }) {
            assertShading(write(output -> block.write(output, "block", version)), version);
            assertShading(write(output -> projectile.writeModel(output, version)), version);
        }
    }

    private JsonObject write(ModelWriter writer) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry("model.json"));
            writer.write(output);
        }
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertNotNull(input.getNextEntry());
            return (JsonObject) Jsoner.deserialize(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
    }

    private void assertShading(JsonObject model, int version) {
        JsonArray elements = (JsonArray) model.get("elements");
        JsonObject unshaded = (JsonObject) elements.get(0);
        JsonObject shaded = (JsonObject) elements.get(1);
        if (version >= VERSION26_3) {
            assertFalse(unshaded.containsKey("shade"));
            assertFalse(shaded.containsKey("shade"));
            assertEquals("up", unshaded.get("shade_direction_override"));
            assertFalse(shaded.containsKey("shade_direction_override"));
        } else {
            assertEquals(false, unshaded.get("shade"));
            assertEquals(true, shaded.get("shade"));
        }
        assertEquals("north", ((JsonObject) elements.get(3)).get("shade_direction_override"));
    }

    private interface ModelWriter {
        void write(ZipOutputStream output) throws Exception;
    }
}
