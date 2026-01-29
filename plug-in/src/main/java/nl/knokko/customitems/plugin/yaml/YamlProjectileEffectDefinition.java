package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.particle.VParticle;
import nl.knokko.customitems.projectile.effect.PEExecuteCommand;

import java.util.List;

class YamlProjectileEffectDefinition {

    final YamlProjectileEffectType type;

    final Float explosionPower;
    final Boolean explosionDestroyBlocks;
    final Boolean explosionSetFire;

    final Integer redstoneMinRed;
    final Integer redstoneMinGreen;
    final Integer redstoneMinBlue;
    final Integer redstoneMaxRed;
    final Integer redstoneMaxGreen;
    final Integer redstoneMaxBlue;
    final Float redstoneMinRadius;
    final Float redstoneMaxRadius;
    final Integer redstoneAmount;

    final VParticle particle;
    final Float particleMinRadius;
    final Float particleMaxRadius;
    final Integer particleAmount;

    final Float accelerationMin;
    final Float accelerationMax;

    final String subProjectileInternalName;
    final Boolean subUseParentLifetime;
    final Integer subMinAmount;
    final Integer subMaxAmount;
    final Float subAngleToParent;

    final String command;
    final PEExecuteCommand.Executor commandExecutor;

    final Float pushStrength;
    final Float pushRadius;

    final YamlSoundDefinition sound;

    final List<YamlFireworkEffectDefinition> fireworkEffects;

    final Float potionAuraRadius;
    final List<YamlPotionEffectDefinition> potionAuraEffects;

    YamlProjectileEffectDefinition(
            YamlProjectileEffectType type,
            Float explosionPower,
            Boolean explosionDestroyBlocks,
            Boolean explosionSetFire,
            Integer redstoneMinRed,
            Integer redstoneMinGreen,
            Integer redstoneMinBlue,
            Integer redstoneMaxRed,
            Integer redstoneMaxGreen,
            Integer redstoneMaxBlue,
            Float redstoneMinRadius,
            Float redstoneMaxRadius,
            Integer redstoneAmount,
            VParticle particle,
            Float particleMinRadius,
            Float particleMaxRadius,
            Integer particleAmount,
            Float accelerationMin,
            Float accelerationMax,
            String subProjectileInternalName,
            Boolean subUseParentLifetime,
            Integer subMinAmount,
            Integer subMaxAmount,
            Float subAngleToParent,
            String command,
            PEExecuteCommand.Executor commandExecutor,
            Float pushStrength,
            Float pushRadius,
            YamlSoundDefinition sound,
            List<YamlFireworkEffectDefinition> fireworkEffects,
            Float potionAuraRadius,
            List<YamlPotionEffectDefinition> potionAuraEffects
    ) {
        this.type = type;
        this.explosionPower = explosionPower;
        this.explosionDestroyBlocks = explosionDestroyBlocks;
        this.explosionSetFire = explosionSetFire;
        this.redstoneMinRed = redstoneMinRed;
        this.redstoneMinGreen = redstoneMinGreen;
        this.redstoneMinBlue = redstoneMinBlue;
        this.redstoneMaxRed = redstoneMaxRed;
        this.redstoneMaxGreen = redstoneMaxGreen;
        this.redstoneMaxBlue = redstoneMaxBlue;
        this.redstoneMinRadius = redstoneMinRadius;
        this.redstoneMaxRadius = redstoneMaxRadius;
        this.redstoneAmount = redstoneAmount;
        this.particle = particle;
        this.particleMinRadius = particleMinRadius;
        this.particleMaxRadius = particleMaxRadius;
        this.particleAmount = particleAmount;
        this.accelerationMin = accelerationMin;
        this.accelerationMax = accelerationMax;
        this.subProjectileInternalName = subProjectileInternalName;
        this.subUseParentLifetime = subUseParentLifetime;
        this.subMinAmount = subMinAmount;
        this.subMaxAmount = subMaxAmount;
        this.subAngleToParent = subAngleToParent;
        this.command = command;
        this.commandExecutor = commandExecutor;
        this.pushStrength = pushStrength;
        this.pushRadius = pushRadius;
        this.sound = sound;
        this.fireworkEffects = fireworkEffects;
        this.potionAuraRadius = potionAuraRadius;
        this.potionAuraEffects = potionAuraEffects;
    }
}
