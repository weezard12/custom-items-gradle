package nl.knokko.customitems;

import nl.knokko.customitems.item.VMaterial;
import nl.knokko.customitems.drops.VBiome;
import nl.knokko.customitems.drops.VBlockType;
import nl.knokko.customitems.drops.VEntityType;
import nl.knokko.customitems.damage.VRawDamageSource;
import nl.knokko.customitems.particle.VParticle;
import nl.knokko.customitems.sound.VSoundType;
import nl.knokko.customitems.recipe.VFuel;
import nl.knokko.customitems.recipe.VFurnaceInput;
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
        assertEquals(VERSION26_3, parseVersion("26.3"));
        assertEquals(VERSION26_3, parseVersion("26.3.0"));
        assertEquals(VERSION26_3, parseVersion("26.3-200-09ee222 (MC: 26.3)"));
        assertEquals("26.3", createString(VERSION26_3));
    }

    @Test
    public void testLatestBoundsIncludeMinecraft263() {
        assertEquals(VERSION26_3, LAST_VERSION);
        assertEquals(VERSION1_21_11, normalizeUpperBound(VERSION1_21));
        assertEquals(VERSION26_3, normalizeUpperBound(VERSION26));
        assertEquals(VERSION26_3, normalize(26));
        assertEquals(VERSION26_2, normalizeUpperBound(VERSION26_2));
        assertTrue(isSupported(VERSION26_1_2));
        assertTrue(isSupported(VERSION26_2));
        assertTrue(isSupported(VERSION26_3));
        assertFalse(isSupported(version(26, 4, 0)));
    }

    @Test
    public void testMinecraft2612RegistryEntries() {
        assertEquals(VERSION26_1_0, VMaterial.GOLDEN_DANDELION.firstVersion);
        assertEquals(VERSION26_3, VMaterial.GOLDEN_DANDELION.lastVersion);
        assertEquals(VERSION26_1_0, VMaterial.POTTED_GOLDEN_DANDELION.firstVersion);
        assertEquals(VERSION26_1_0, VParticle.PAUSE_MOB_GROWTH.firstVersion);
        assertEquals(VERSION26_1_0, VParticle.RESET_MOB_GROWTH.firstVersion);
        assertEquals(VERSION26_1_0, VSoundType.item_golden_dandelion_use.firstVersion);
        assertEquals(VERSION26_3, VMaterial.STONE.lastVersion);
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
        assertEquals(VERSION26_3, VMaterial.CINNABAR.lastVersion);
        assertEquals(VERSION26_3, VBlockType.SULFUR_SPIKE.lastVersion);
        assertEquals(VERSION26_3, VEntityType.SULFUR_CUBE.lastVersion);
        assertEquals(VERSION26_3, VBiome.sulfur_caves.lastVersion);
        assertEquals(VERSION26_3, VParticle.NOXIOUS_GAS.lastVersion);
        assertEquals(VERSION26_3, VRawDamageSource.SULFUR_CUBE_HOT.maxVersion);
        assertEquals(VERSION26_3, VSoundType.entity_sulfur_cube_bounce.lastVersion);
    }

    @Test
    public void testMinecraft263RegistryEntries() {
        assertEquals(VERSION26_3, VMaterial.POPLAR_PLANKS.firstVersion);
        assertEquals(VERSION26_3, VMaterial.WHITE_CUSHION.firstVersion);
        assertEquals(VERSION26_3, VMaterial.BURIED_TREASURE_MAP.firstVersion);
        assertEquals(1, VMaterial.POPLAR_BOAT.maxStackSize);
        assertEquals(16, VMaterial.POPLAR_SIGN.maxStackSize);
        assertEquals(16, VMaterial.STRAW_BED.maxStackSize);
        assertEquals(16, VMaterial.WHITE_CUSHION.maxStackSize);
        assertEquals(64, VMaterial.BURIED_TREASURE_MAP.maxStackSize);
        assertEquals(VERSION26_3, VBlockType.STRAW_BED.firstVersion);
        assertEquals(VERSION26_3, VBlockType.RED_WOOL_STAIRS.firstVersion);
        assertEquals(VERSION26_3, VBlockType.BLUE_CONCRETE_SLAB.firstVersion);
        assertEquals(VERSION26_3, VEntityType.CUSHION.firstVersion);
        assertEquals(VERSION26_3, VEntityType.POPLAR_BOAT.firstVersion);
        assertEquals(VERSION26_3, VBiome.dappled_forest.firstVersion);
        assertEquals(VERSION26_3, VParticle.RED_POPLAR_LEAVES.firstVersion);
        assertEquals(VERSION26_3, VSoundType.entity_cushion_sit.firstVersion);
        assertEquals("entity.cushion.sit", VSoundType.entity_cushion_sit.key);
        assertEquals(VERSION26_3, VFuel.POPLAR_PLANKS.firstVersion);
        assertEquals(VERSION26_3, VFuel.RED_WOOL_SLAB.firstVersion);
        assertEquals(VERSION26_3, VFurnaceInput.POPLAR_LOG.firstVersion);
        assertEquals(VERSION26_3, VFurnaceInput.RED_POPLAR_LEAVES.firstVersion);
    }
}
