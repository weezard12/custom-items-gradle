package nl.knokko.customitems.nms16;

import nl.knokko.customitems.nms16plus.KciNms16Plus;

@SuppressWarnings("unused")
public class KciNms16 extends KciNms16Plus {

    public static final String[] NMS_VERSION_STRINGS = { "1_16_R3", "1_16_R2", "1_16_R1" };

    public KciNms16() {
        super(new KciNmsEntities16(), new KciNmsItems16());
    }
}
