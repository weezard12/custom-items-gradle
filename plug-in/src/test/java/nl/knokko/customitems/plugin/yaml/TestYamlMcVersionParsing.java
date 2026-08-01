package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.MCVersions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestYamlMcVersionParsing {

    private static final File SOURCE = new File("test.yml");

    @Test
    public void testParseMinecraft2612() {
        List<String> warnings = new ArrayList<>();
        assertEquals(
                MCVersions.VERSION26_1_2,
                YamlParseUtils.parseMcVersion("26.1.2", "condition.mc", SOURCE, warnings)
        );
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void testParseMinecraft262() {
        List<String> warnings = new ArrayList<>();
        assertEquals(
                MCVersions.VERSION26_2,
                YamlParseUtils.parseMcVersion("26.2", "condition.mc", SOURCE, warnings)
        );
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void testParseLegacyMinorShorthand() {
        List<String> warnings = new ArrayList<>();
        assertEquals(
                MCVersions.VERSION1_21_0,
                YamlParseUtils.parseMcVersion("1.21", "condition.mc", SOURCE, warnings)
        );
        assertEquals(
                MCVersions.VERSION1_21_0,
                YamlParseUtils.parseMcVersion("21", "condition.mc", SOURCE, warnings)
        );
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void testRejectUnknownFutureVersion() {
        List<String> warnings = new ArrayList<>();
        assertNull(YamlParseUtils.parseMcVersion("26.3", "condition.mc", SOURCE, warnings));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("Unsupported condition.mc '26.3'"));
    }
}
