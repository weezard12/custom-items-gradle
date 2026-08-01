package nl.knokko.customitems.plugin.resourcepack;

import nl.knokko.customitems.MCVersions;
import nl.knokko.customitems.util.ProgrammingValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestResourcepackVersionHelper {

    @Test
    public void testYearBasedPackFormats() throws ProgrammingValidationException {
        assertEquals(84, ResourcepackVersionHelper.getPackFormat(MCVersions.VERSION26_1_2));
        assertEquals(88, ResourcepackVersionHelper.getPackFormat(MCVersions.VERSION26_2));
    }
}
