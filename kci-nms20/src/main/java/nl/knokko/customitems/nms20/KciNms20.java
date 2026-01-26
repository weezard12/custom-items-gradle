package nl.knokko.customitems.nms20;

import nl.knokko.customitems.nms16plus.KciNms16Plus;

@SuppressWarnings("unused")
public class KciNms20 extends KciNms16Plus {

    public static final String[] NMS_VERSION_STRINGS = { "1_20_R4", "1_20_R3", "1_20_R2", "1_20_R1" };

    public KciNms20() {
        super(new KciNmsEntities20(), new KciNmsItems20());
    }
}
