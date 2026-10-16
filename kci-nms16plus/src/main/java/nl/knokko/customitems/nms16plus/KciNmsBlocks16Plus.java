package nl.knokko.customitems.nms16plus;

import nl.knokko.customitems.nms13plus.KciNmsBlocks13Plus;
import org.bukkit.World;

import java.lang.reflect.Method;

public class KciNmsBlocks16Plus extends KciNmsBlocks13Plus {

    private static final Method GET_MIN_HEIGHT;

    static {
        Method method = null;
        try {
            method = World.class.getMethod("getMinHeight");
        } catch (NoSuchMethodException ignored) {
            // Worlds before 1.17 always start at y=0.
        }
        GET_MIN_HEIGHT = method;
    }

    @Override
    public int getMinHeight(World world) {
        if (GET_MIN_HEIGHT == null) return 0;
        try {
            return (Integer) GET_MIN_HEIGHT.invoke(world);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Can't determine world minimum height", failure);
        }
    }
}
