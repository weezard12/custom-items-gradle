package nl.knokko.customitems;

import nl.knokko.customitems.item.VMaterial;
import nl.knokko.customitems.particle.VParticle;
import nl.knokko.customitems.sound.VSoundType;
import org.junit.jupiter.api.Test;

import static nl.knokko.customitems.MCVersions.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestMCVersions {

    @Test
    public void testMinecraft2612ParsingAndFormatting() {
        assertEquals(VERSION26_1_2, parseVersion("26.1.2"));
        assertEquals(VERSION26_1_2, parseVersion("git-Paper-61 (MC: 26.1.2)"));
        assertEquals("26.1.2", createString(VERSION26_1_2));
        assertEquals(26, getMajor(VERSION26_1_2));
        assertEquals(1, getMinor(VERSION26_1_2));
        assertEquals(2, getPatch(VERSION26_1_2));
    }

    @Test
    public void testLatestBoundsIncludeMinecraft2612() {
        assertEquals(VERSION26_1_2, LAST_VERSION);
        assertEquals(VERSION26_1_2, normalizeUpperBound(VERSION1_21));
        assertTrue(isSupported(VERSION26_1_2));
        assertFalse(isSupported(version(26, 2, 0)));
    }

    @Test
    public void testMinecraft2612RegistryEntries() {
        assertEquals(VERSION26_1_0, VMaterial.GOLDEN_DANDELION.firstVersion);
        assertEquals(VERSION26_1_2, VMaterial.GOLDEN_DANDELION.lastVersion);
        assertEquals(VERSION26_1_0, VMaterial.POTTED_GOLDEN_DANDELION.firstVersion);
        assertEquals(VERSION26_1_0, VParticle.PAUSE_MOB_GROWTH.firstVersion);
        assertEquals(VERSION26_1_0, VParticle.RESET_MOB_GROWTH.firstVersion);
        assertEquals(VERSION26_1_0, VSoundType.ITEM_GOLDEN_DANDELION_USE.firstVersion);
        assertEquals(VERSION26_1_2, VMaterial.STONE.lastVersion);
    }
}
