package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.sound.VSoundType;

class YamlSoundDefinition {

    final VSoundType soundType;
    final float volume;
    final float pitch;

    YamlSoundDefinition(VSoundType soundType, float volume, float pitch) {
        this.soundType = soundType;
        this.volume = volume;
        this.pitch = pitch;
    }
}
