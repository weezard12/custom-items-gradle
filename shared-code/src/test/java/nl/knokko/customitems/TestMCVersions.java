package nl.knokko.customitems;

import nl.knokko.customitems.item.VMaterial;
import nl.knokko.customitems.drops.VBiome;
import nl.knokko.customitems.drops.VBlockType;
import nl.knokko.customitems.drops.VEntityType;
import nl.knokko.customitems.damage.VRawDamageSource;
import nl.knokko.customitems.particle.VParticle;
import nl.knokko.customitems.sound.VSoundType;
import org.junit.jupiter.api.Test;

import static nl.knokko.customitems.MCVersions.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestMCVersions {

    @Test
    public void testYearBasedVersionParsingAndFormatting() {
        assertEquals(VERSION26_1_2, parseVersion("26.1.2"));
        assertEquals(VERSION26_1_2, parseVersion("git-Paper-61 (MC: 26.1.2)"));
        assertEquals("26.1.2", createString(VERSION26_1_2));
        assertEquals(26, getMajor(VERSION26_1_2));
        assertEquals(1, getMinor(VERSION26_1_2));
        assertEquals(2, getPatch(VERSION26_1_2));
        assertEquals(VERSION26_2, parseVersion("26.2"));
        assertEquals(VERSION26_2, parseVersion("git-Paper-87 (MC: 26.2)"));
        assertEquals("26.2", createString(VERSION26_2));
    }

    @Test
    public void testLatestBoundsIncludeMinecraft262() {
        assertEquals(VERSION26_2, LAST_VERSION);
        assertEquals(VERSION1_21_11, normalizeUpperBound(VERSION1_21));
        assertEquals(VERSION26_2, normalizeUpperBound(VERSION26));
        assertTrue(isSupported(VERSION26_1_2));
        assertTrue(isSupported(VERSION26_2));
        assertFalse(isSupported(version(26, 3, 0)));
    }

    @Test
    public void testMinecraft2612RegistryEntries() {
        assertEquals(VERSION26_1_0, VMaterial.GOLDEN_DANDELION.firstVersion);
        assertEquals(VERSION26_2, VMaterial.GOLDEN_DANDELION.lastVersion);
        assertEquals(VERSION26_1_0, VMaterial.POTTED_GOLDEN_DANDELION.firstVersion);
        assertEquals(VERSION26_1_0, VParticle.PAUSE_MOB_GROWTH.firstVersion);
        assertEquals(VERSION26_1_0, VParticle.RESET_MOB_GROWTH.firstVersion);
        assertEquals(VERSION26_1_0, VSoundType.item_golden_dandelion_use.firstVersion);
        assertEquals(VERSION26_2, VMaterial.STONE.lastVersion);
    }

    @Test
    public void testMinecraft262RegistryEntries() {
        assertEquals(VERSION26_2, VMaterial.CINNABAR.firstVersion);
        assertEquals(VERSION26_2, VMaterial.MUSIC_DISC_BOUNCE.firstVersion);
        assertEquals(VERSION26_2, VBlockType.SULFUR_SPIKE.firstVersion);
        assertEquals(VERSION26_2, VEntityType.SULFUR_CUBE.firstVersion);
        assertEquals(VERSION26_2, VBiome.sulfur_caves.firstVersion);
        assertEquals(VERSION26_2, VParticle.NOXIOUS_GAS.firstVersion);
        assertEquals(VERSION26_2, VRawDamageSource.SULFUR_CUBE_HOT.minVersion);
        assertEquals("sulfur_cube_hot", VRawDamageSource.SULFUR_CUBE_HOT.rawName);
        assertEquals(VERSION26_2, VSoundType.entity_sulfur_cube_bounce.firstVersion);
        assertEquals("entity.sulfur_cube.bounce", VSoundType.entity_sulfur_cube_bounce.key);
    }
}
