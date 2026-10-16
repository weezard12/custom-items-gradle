package nl.knokko.customitems.item.model;

import com.github.cliftonlabs.json_simple.JsonObject;

import java.util.List;

import static nl.knokko.customitems.MCVersions.VERSION26_3;

final class ModelFormatHelper {

    private ModelFormatHelper() {}

    static void adapt(JsonObject model, int mcVersion) {
        if (mcVersion < VERSION26_3) return;
        Object elements = model.get("elements");
        if (!(elements instanceof List<?>)) return;
        for (Object element : (List<?>) elements) {
            if (!(element instanceof JsonObject)) continue;
            JsonObject cube = (JsonObject) element;
            // 26.3 removed shade; pointing shading up preserves shade:false.
            if (Boolean.FALSE.equals(cube.remove("shade")) && !cube.containsKey("shade_direction_override")) {
                cube.put("shade_direction_override", "up");
            }
        }
    }
}
