package nl.knokko.customitems.plugin.yaml;

import java.io.File;
import java.util.List;

class YamlProjectileDefinition {

    final String fullId;
    final String internalName;
    final String idName;
    final File packDirectory;
    final File sourceFile;
    final Float damage;
    final Float minLaunchAngle;
    final Float maxLaunchAngle;
    final Float minLaunchSpeed;
    final Float maxLaunchSpeed;
    final Float gravity;
    final Float launchKnockback;
    final Float impactKnockback;
    final List<YamlPotionEffectDefinition> impactPotionEffects;
    final Integer maxLifetime;
    final Integer maxPiercedEntities;
    final List<YamlProjectileEffectsDefinition> inFlightEffects;
    final List<YamlProjectileEffectDefinition> impactEffects;
    final Boolean applyImpactEffectsAtExpiration;
    final Boolean applyImpactEffectsAtPierce;
    final String coverInternalName;
    final String customDamageSource;

    YamlProjectileDefinition(
            String fullId,
            String internalName,
            String idName,
            File packDirectory,
            File sourceFile,
            Float damage,
            Float minLaunchAngle,
            Float maxLaunchAngle,
            Float minLaunchSpeed,
            Float maxLaunchSpeed,
            Float gravity,
            Float launchKnockback,
            Float impactKnockback,
            List<YamlPotionEffectDefinition> impactPotionEffects,
            Integer maxLifetime,
            Integer maxPiercedEntities,
            List<YamlProjectileEffectsDefinition> inFlightEffects,
            List<YamlProjectileEffectDefinition> impactEffects,
            Boolean applyImpactEffectsAtExpiration,
            Boolean applyImpactEffectsAtPierce,
            String coverInternalName,
            String customDamageSource
    ) {
        this.fullId = fullId;
        this.internalName = internalName;
        this.idName = idName;
        this.packDirectory = packDirectory;
        this.sourceFile = sourceFile;
        this.damage = damage;
        this.minLaunchAngle = minLaunchAngle;
        this.maxLaunchAngle = maxLaunchAngle;
        this.minLaunchSpeed = minLaunchSpeed;
        this.maxLaunchSpeed = maxLaunchSpeed;
        this.gravity = gravity;
        this.launchKnockback = launchKnockback;
        this.impactKnockback = impactKnockback;
        this.impactPotionEffects = impactPotionEffects;
        this.maxLifetime = maxLifetime;
        this.maxPiercedEntities = maxPiercedEntities;
        this.inFlightEffects = inFlightEffects;
        this.impactEffects = impactEffects;
        this.applyImpactEffectsAtExpiration = applyImpactEffectsAtExpiration;
        this.applyImpactEffectsAtPierce = applyImpactEffectsAtPierce;
        this.coverInternalName = coverInternalName;
        this.customDamageSource = customDamageSource;
    }
}
