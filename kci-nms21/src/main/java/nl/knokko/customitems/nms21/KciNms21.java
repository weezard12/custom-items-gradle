package nl.knokko.customitems.nms21;

import nl.knokko.customitems.nms16plus.KciNms16Plus;
import nl.knokko.customitems.nms21plus.KciNmsEntities21Plus;

@SuppressWarnings("unused")
public class KciNms21 extends KciNms16Plus {

    public static final String[] NMS_VERSION_STRINGS = {
            "1_21_R7",
            "1_21_R6",
            "1_21_R5",
            "1_21_R4",
            "1_21_R3",
            "1_21_R2"
    };

    public KciNms21() {
        super(new KciNmsEntities21Plus(), new KciNmsItems21());
    }
}
