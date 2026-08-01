package nl.knokko.customitems.nms26;

import nl.knokko.customitems.nms26plus.KciNms26Plus;

@SuppressWarnings("unused")
public class KciNms26 extends KciNms26Plus {

    public static final String[] CRAFT_ITEM_STACK_CLASS_NAMES = {
            "org.bukkit.craftbukkit.inventory.CraftItemStack"
    };

    public KciNms26() {
        super(new KciNmsItems26());
    }
}
