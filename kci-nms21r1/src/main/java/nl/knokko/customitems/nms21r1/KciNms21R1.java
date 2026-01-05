package nl.knokko.customitems.nms21r1;

import nl.knokko.customitems.nms16plus.KciNms16Plus;
import nl.knokko.customitems.nms21plus.KciNmsEntities21Plus;

@SuppressWarnings("unused")
public class KciNms21R1 extends KciNms16Plus {

    public static final String NMS_VERSION_STRING = "1_21_R1";

    public KciNms21R1() {
        super(new KciNmsEntities21Plus(), new KciNmsItems21R1());
    }
}
