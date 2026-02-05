package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.nms.KciNms;

/**
 * Provides the Minecraft version context for YAML parsing outside of a running server.
 * Defaults to {@link KciNms#mcVersion}, but CLI tools can override this.
 */
public final class YamlVersionContext {

    private YamlVersionContext() {
    }

    public static volatile int mcVersion = KciNms.mcVersion;
}
