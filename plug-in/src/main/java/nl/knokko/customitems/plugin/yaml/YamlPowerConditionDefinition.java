package nl.knokko.customitems.plugin.yaml;

public class YamlPowerConditionDefinition {

    public final String trigger;
    public final String action;
    public final String customItemInternalName;

    YamlPowerConditionDefinition(String trigger, String action, String customItemInternalName) {
        this.trigger = trigger;
        this.action = action;
        this.customItemInternalName = customItemInternalName;
    }
}
