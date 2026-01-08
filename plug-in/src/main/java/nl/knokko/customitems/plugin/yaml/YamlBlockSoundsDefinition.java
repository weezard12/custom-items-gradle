package nl.knokko.customitems.plugin.yaml;

class YamlBlockSoundsDefinition {

    final YamlSoundDefinition leftClick;
    final YamlSoundDefinition rightClick;
    final YamlSoundDefinition breakSound;
    final YamlSoundDefinition step;

    YamlBlockSoundsDefinition(
            YamlSoundDefinition leftClick,
            YamlSoundDefinition rightClick,
            YamlSoundDefinition breakSound,
            YamlSoundDefinition step
    ) {
        this.leftClick = leftClick;
        this.rightClick = rightClick;
        this.breakSound = breakSound;
        this.step = step;
    }
}
