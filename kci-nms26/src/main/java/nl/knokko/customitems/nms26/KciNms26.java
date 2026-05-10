package nl.knokko.customitems.nms26;

import nl.knokko.customitems.nms16plus.KciNms16Plus;
import nl.knokko.customitems.nms21plus.KciNmsEntities21Plus;

@SuppressWarnings("unused")
public class KciNms26 extends KciNms16Plus {

    public static final String[] CRAFT_ITEM_STACK_CLASS_NAMES = {
            "org.bukkit.craftbukkit.inventory.CraftItemStack"
    };

    public KciNms26() {
        super(new KciNmsEntities21Plus(), new KciNmsItems26());
    }
}
