package nl.knokko.customitems.plugin.multisupport.powers;

import nl.knokko.customitems.item.KciItem;
import nl.knokko.customitems.plugin.CustomItemsPlugin;
import nl.knokko.customitems.plugin.set.ItemSetWrapper;
import nl.knokko.customitems.plugin.yaml.YamlPowersDefinition;
import nl.knokko.customitems.plugin.yaml.YamlPowersReader;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

public class PowersSupport {

    private static final String TEST_CLASS = "me.weezard12.powers.api.PowersApi";
    private static final String CLASS_POWERS_API = "me.weezard12.powers.api.PowersApi";
    private static final String CLASS_ABILITY = "me.weezard12.powers.api.Ability";
    private static final String CLASS_POWER = "me.weezard12.powers.api.Power";
    private static final String CLASS_PASSIVE_ABILITY = "me.weezard12.powers.api.PassiveAbility";
    private static final String CLASS_POWER_ALIGNMENT = "me.weezard12.powers.api.PowerAlignment";
    private static final String CLASS_POWER_RARITY = "me.weezard12.powers.api.PowerRarity";
    private static final String CLASS_ABILITY_CONDITION = "me.weezard12.powers.api.conditions.AbilityCondition";
    private static final String CLASS_CONDITION_RULE = "me.weezard12.powers.api.conditions.ConditionRule";
    private static final String CLASS_CONDITION_ACTION = "me.weezard12.powers.api.conditions.ConditionAction";
    private static final String CLASS_CONDITIONED_PASSIVE_ABILITY = "me.weezard12.powers.api.conditions.ConditionedPassiveAbility";
    private static final String CLASS_PASSIVE_POTION_ABILITY =
            "me.weezard12.powers.api.builtin.PotionEffectPassiveAbility";

    private static final String TRIGGER_RIGHT_CLICK = "RIGHT_CLICK";
    private static final String TRIGGER_SHIFT_RIGHT_CLICK = "SHIFT_RIGHT_CLICK";
    private static final String TRIGGER_ANY_RIGHT_CLICK = "ANY_RIGHT_CLICK";
    private static final String TRIGGER_LEFT_CLICK = "LEFT_CLICK";
    private static final String TRIGGER_SHIFT_LEFT_CLICK = "SHIFT_LEFT_CLICK";
    private static final String TRIGGER_ANY_LEFT_CLICK = "ANY_LEFT_CLICK";
    private static final String TRIGGER_START_HOLD_HAND = "START_HOLD_HAND";
    private static final String TRIGGER_START_HOLD_OFF_HAND = "START_HOLD_OFF_HAND";
    private static final String TRIGGER_START_HOLD_ANY_HAND = "START_HOLD_ANY_HAND";
    private static final String TRIGGER_FINISH_HOLD_HAND = "FINISH_HOLD_HAND";
    private static final String TRIGGER_FINISH_HOLD_OFF_HAND = "FINISH_HOLD_OFF_HAND";
    private static final String TRIGGER_FINISH_HOLD_ANY_HAND = "FINISH_HOLD_ANY_HAND";
    private static final String TRIGGER_HOLD_INVENTORY = "HOLD_INVENTORY";
    private static final String TRIGGER_DROP = "DROP";
    private static final String TRIGGER_PICK_UP = "PICK_UP";
    private static final String SYNTHETIC_ITEM_POWER_PREFIX = "kci_item:";

    private static CustomItemsPlugin plugin;
    private static int syncTaskId = -1;

    private static YamlPowersDefinition definitions = YamlPowersDefinition.empty();
    private static long definitionRevision = 0L;
    private static long builtRevision = -1L;

    private static final Map<String, Object> powerObjectsById = new HashMap<>();
    private static final Map<UUID, Set<String>> assignedPowerIdsByPlayer = new HashMap<>();

    private static Object lastApiInstance;
    private static Class<?> powersApiClass;
    private static boolean classMissingLogged;
    private static boolean waitingForServiceLogged;

    public static void onEnable(CustomItemsPlugin customItemsPlugin) {
        plugin = customItemsPlugin;
        powersApiClass = null;
        classMissingLogged = false;
        waitingForServiceLogged = false;

        if (syncTaskId != -1) {
            Bukkit.getScheduler().cancelTask(syncTaskId);
            syncTaskId = -1;
        }
        syncTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, PowersSupport::syncPowers, 40L, 20L);

        try {
            powersApiClass = Class.forName(CLASS_POWERS_API);
            Bukkit.getLogger().info("Enabled OPTIONAL Powers bridge integration");
        } catch (ClassNotFoundException missingPowers) {
            classMissingLogged = true;
            Bukkit.getLogger().info("Disabled OPTIONAL Powers bridge integration: can't find " + TEST_CLASS);
        }
    }

    public static void onDisable() {
        if (syncTaskId != -1) {
            Bukkit.getScheduler().cancelTask(syncTaskId);
            syncTaskId = -1;
        }

        Object api = findApi();
        if (api != null) {
            removeAllManagedPowers(api);
        }

        definitions = YamlPowersDefinition.empty();
        definitionRevision = 0L;
        builtRevision = -1L;
        powerObjectsById.clear();
        assignedPowerIdsByPlayer.clear();
        lastApiInstance = null;
        powersApiClass = null;
        plugin = null;
    }

    public static void reloadFromYaml(java.io.File dataFolder, Consumer<String> log) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        YamlPowersDefinition parsed = YamlPowersReader.readDefinitions(dataFolder, errors, warnings);
        YamlPowersDefinition previousDefinitions = definitions;

        if (!warnings.isEmpty()) {
            log.accept(ChatColor.YELLOW + "Powers YAML warnings:");
            for (String warning : warnings) {
                log.accept(ChatColor.YELLOW + "- " + warning);
            }
        }

        if (!errors.isEmpty()) {
            log.accept(ChatColor.RED + "Powers YAML errors:");
            for (String error : errors) {
                log.accept(ChatColor.RED + "- " + error);
            }
            if (previousDefinitions.isEmpty()) {
                log.accept(ChatColor.RED + "Powers bridge definitions were not loaded.");
            } else {
                log.accept(ChatColor.YELLOW + "Keeping previously loaded Powers YAML definitions. "
                        + "Powers declared outside YAML were not modified.");
            }
            return;
        }

        definitions = parsed;
        if (!parsed.isEmpty()) {
            log.accept(ChatColor.GREEN + "Loaded " + parsed.getPowersById().size()
                    + " power definition(s) and " + parsed.getAbilitiesById().size() + " ability definition(s).");
        } else if (!previousDefinitions.isEmpty()) {
            log.accept(ChatColor.YELLOW + "No YAML powers were loaded, so YAML-managed powers were cleared. "
                    + "Powers declared outside YAML were not modified.");
        }

        definitionRevision++;
        builtRevision = -1L;
        syncPowers();
    }

    private static void syncPowers() {
        if (plugin == null) return;

        try {
            Object api = findApi();
            if (api == null) {
                powerObjectsById.clear();
                assignedPowerIdsByPlayer.clear();
                builtRevision = -1L;
                return;
            }

            if (api != lastApiInstance) {
                powerObjectsById.clear();
                assignedPowerIdsByPlayer.clear();
                builtRevision = -1L;
                lastApiInstance = api;
            }

            ensureBuilt(api);
            synchronizePlayers(api);
        } catch (Exception unexpectedError) {
            Bukkit.getLogger().log(Level.SEVERE, "Powers bridge sync failed", unexpectedError);
        }
    }

    private static Object findApi() {
        if (powersApiClass == null) {
            if (!classMissingLogged) {
                Bukkit.getLogger().info("Disabled OPTIONAL Powers bridge integration: can't find " + TEST_CLASS);
                classMissingLogged = true;
            }
            return null;
        }

        Object api = Bukkit.getServicesManager().load(powersApiClass);
        if (api == null) {
            if (!waitingForServiceLogged && !definitions.isEmpty()) {
                Bukkit.getLogger().info("Powers bridge is waiting for PowersApi service registration");
                waitingForServiceLogged = true;
            }
            return null;
        }

        waitingForServiceLogged = false;
        return api;
    }

    private static void ensureBuilt(Object api) {
        if (builtRevision == definitionRevision) return;

        removeAllManagedPowers(api);
        powerObjectsById.clear();
        assignedPowerIdsByPlayer.clear();

        if (definitions.isEmpty()) {
            builtRevision = definitionRevision;
            return;
        }

        Map<String, Object> abilityObjectsById = buildAbilities();
        buildPowers(api, abilityObjectsById);
        builtRevision = definitionRevision;
    }

    private static Map<String, Object> buildAbilities() {
        Map<String, Object> abilityObjectsById = new HashMap<>();
        Class<?> passiveAbilityClass;
        try {
            passiveAbilityClass = Class.forName(CLASS_PASSIVE_POTION_ABILITY);
        } catch (ClassNotFoundException noPowersClass) {
            Bukkit.getLogger().severe("Powers bridge can't create abilities: missing " + CLASS_PASSIVE_POTION_ABILITY);
            return abilityObjectsById;
        }

        Constructor<?> constructor;
        try {
            constructor = passiveAbilityClass.getConstructor(
                    String.class, String.class, String.class, int.class, int.class,
                    boolean.class, boolean.class, boolean.class, long.class
            );
        } catch (NoSuchMethodException missingConstructor) {
            Bukkit.getLogger().log(Level.SEVERE, "Powers bridge can't find passive potion ability constructor", missingConstructor);
            return abilityObjectsById;
        }

        for (nl.knokko.customitems.plugin.yaml.YamlPowerAbilityDefinition ability : definitions.getAbilitiesById().values()) {
            try {
                Object abilityObject = constructor.newInstance(
                        ability.fullId,
                        ability.name,
                        ability.potionEffect,
                        ability.amplifier,
                        ability.durationTicks,
                        ability.ambient,
                        ability.particles,
                        ability.icon,
                        ability.tickIntervalTicks
                );
                if (!ability.conditions.isEmpty()) {
                    abilityObject = wrapConditionalAbility(
                            abilityObject, ability.conditions, ability.conditionStateEnabledByDefault, ability.fullId
                    );
                    if (abilityObject == null) {
                        Bukkit.getLogger().warning("Skipping ability " + ability.fullId
                                + " because its conditions could not be compiled");
                        continue;
                    }
                }
                abilityObjectsById.put(ability.fullId, abilityObject);
            } catch (Exception createFailed) {
                Throwable cause = createFailed.getCause() != null ? createFailed.getCause() : createFailed;
                Bukkit.getLogger().warning("Failed to build ability " + ability.fullId + ": " + cause.getMessage());
            }
        }

        return abilityObjectsById;
    }

    private static Object wrapConditionalAbility(
            Object abilityObject,
            List<nl.knokko.customitems.plugin.yaml.YamlPowerConditionDefinition> conditions,
            boolean defaultEnabled,
            String abilityId
    ) {
        try {
            Class<?> passiveAbilityInterface = Class.forName(CLASS_PASSIVE_ABILITY);
            if (!passiveAbilityInterface.isInstance(abilityObject)) {
                Bukkit.getLogger().warning("Ability " + abilityId
                        + " has conditions but is not a passive ability; conditions are ignored.");
                return abilityObject;
            }

            List<Object> ruleObjects = createConditionRules(conditions, abilityId);
            if (ruleObjects.isEmpty()) {
                Bukkit.getLogger().warning("Ability " + abilityId + " has condition entries but none were valid.");
                return null;
            }

            Class<?> conditionedClass = Class.forName(CLASS_CONDITIONED_PASSIVE_ABILITY);
            Constructor<?> constructor = conditionedClass.getConstructor(
                    passiveAbilityInterface, java.util.Collection.class, boolean.class
            );
            return constructor.newInstance(abilityObject, ruleObjects, defaultEnabled);
        } catch (Exception wrapFailed) {
            Throwable cause = wrapFailed.getCause() != null ? wrapFailed.getCause() : wrapFailed;
            Bukkit.getLogger().warning("Failed to wrap conditions for ability " + abilityId + ": " + cause.getMessage());
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Object> createConditionRules(
            List<nl.knokko.customitems.plugin.yaml.YamlPowerConditionDefinition> conditions,
            String abilityId
    ) {
        if (conditions == null || conditions.isEmpty()) return Collections.emptyList();

        Class<?> abilityConditionClass;
        Class<?> conditionActionClass;
        Class<?> conditionRuleClass;
        try {
            abilityConditionClass = Class.forName(CLASS_ABILITY_CONDITION);
            conditionActionClass = Class.forName(CLASS_CONDITION_ACTION);
            conditionRuleClass = Class.forName(CLASS_CONDITION_RULE);
        } catch (ClassNotFoundException missingConditionClass) {
            Bukkit.getLogger().log(Level.SEVERE, "Powers bridge condition classes are missing", missingConditionClass);
            return Collections.emptyList();
        }

        Constructor<?> ruleConstructor;
        try {
            ruleConstructor = conditionRuleClass.getConstructor(abilityConditionClass, conditionActionClass);
        } catch (NoSuchMethodException missingConstructor) {
            Bukkit.getLogger().log(Level.SEVERE, "Powers bridge can't bind ConditionRule constructor", missingConstructor);
            return Collections.emptyList();
        }

        List<Object> result = new ArrayList<>(conditions.size());
        for (nl.knokko.customitems.plugin.yaml.YamlPowerConditionDefinition condition : conditions) {
            try {
                InvocationHandler handler = new CustomItemsConditionHandler(condition.trigger, condition.customItemInternalName);
                Object conditionObject = Proxy.newProxyInstance(
                        abilityConditionClass.getClassLoader(),
                        new Class<?>[]{abilityConditionClass},
                        handler
                );
                Object actionObject = Enum.valueOf((Class<Enum>) conditionActionClass, condition.action);
                Object rule = ruleConstructor.newInstance(conditionObject, actionObject);
                result.add(rule);
            } catch (Exception createFailed) {
                Throwable cause = createFailed.getCause() != null ? createFailed.getCause() : createFailed;
                Bukkit.getLogger().warning("Failed to build condition rule for ability " + abilityId
                        + ": " + cause.getMessage());
            }
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void buildPowers(Object api, Map<String, Object> abilityObjectsById) {
        Class<?> powerClass;
        Class<?> powerBuilderClass;
        Class<?> abilityClass;
        Class<?> powerAlignmentClass;
        Class<?> powerRarityClass;
        try {
            powerClass = Class.forName(CLASS_POWER);
            powerBuilderClass = Class.forName(CLASS_POWER + "$Builder");
            abilityClass = Class.forName(CLASS_ABILITY);
            powerAlignmentClass = Class.forName(CLASS_POWER_ALIGNMENT);
            powerRarityClass = Class.forName(CLASS_POWER_RARITY);
        } catch (ClassNotFoundException missingClass) {
            Bukkit.getLogger().log(Level.SEVERE, "Powers bridge can't build powers", missingClass);
            return;
        }

        Method builderMethod;
        Method setRarityMethod;
        Method setAlignmentMethod;
        Method setIconMethod;
        Method setDescriptionMethod;
        Method addPassiveMethod;
        Method buildMethod;
        try {
            builderMethod = powerClass.getMethod("builder", String.class, String.class);
            setRarityMethod = powerBuilderClass.getMethod("setRarity", powerRarityClass);
            setAlignmentMethod = powerBuilderClass.getMethod("setAlignment", powerAlignmentClass);
            setIconMethod = powerBuilderClass.getMethod("setIconKey", String.class);
            setDescriptionMethod = powerBuilderClass.getMethod("setDescription", java.util.Collection.class);
            addPassiveMethod = powerBuilderClass.getMethod("addPassive", abilityClass);
            buildMethod = powerBuilderClass.getMethod("build");
        } catch (NoSuchMethodException missingMethod) {
            Bukkit.getLogger().log(Level.SEVERE, "Powers bridge found an unsupported Powers API version", missingMethod);
            return;
        }

        Object rarityRegistry = invokeNoArgs(api, "getPowerRarityRegistry");
        Method getRarityMethod = null;
        if (rarityRegistry != null) {
            try {
                getRarityMethod = rarityRegistry.getClass().getMethod("get", String.class);
            } catch (NoSuchMethodException ignored) {
                // Keep rarity optional when unsupported
            }
        }

        for (nl.knokko.customitems.plugin.yaml.YamlPowerDefinition power : definitions.getPowersById().values()) {

            List<Object> abilities = new ArrayList<>(power.abilityIds.size());
            boolean missingAbility = false;
            for (String abilityId : power.abilityIds) {
                Object abilityObject = abilityObjectsById.get(abilityId);
                if (abilityObject == null) {
                    missingAbility = true;
                    Bukkit.getLogger().warning("Skipping power " + power.fullId
                            + " because ability " + abilityId + " failed to load");
                } else {
                    abilities.add(abilityObject);
                }
            }
            if (missingAbility || abilities.isEmpty()) continue;

            try {
                Object builder = builderMethod.invoke(null, power.fullId, power.name);

                if (power.rarity != null && getRarityMethod != null && rarityRegistry != null) {
                    Object rarity = getRarityMethod.invoke(rarityRegistry, power.rarity);
                    if (rarity != null) {
                        setRarityMethod.invoke(builder, rarity);
                    } else {
                        Bukkit.getLogger().warning("Power " + power.fullId + " uses unknown rarity " + power.rarity);
                    }
                }

                if (power.alignment != null) {
                    Object alignment = Enum.valueOf((Class<Enum>) powerAlignmentClass, power.alignment);
                    setAlignmentMethod.invoke(builder, alignment);
                }
                if (power.iconKey != null) setIconMethod.invoke(builder, power.iconKey);
                if (!power.description.isEmpty()) setDescriptionMethod.invoke(builder, power.description);

                for (Object ability : abilities) addPassiveMethod.invoke(builder, ability);

                Object powerObject = buildMethod.invoke(builder);
                powerObjectsById.put(power.fullId, powerObject);
            } catch (Exception createFailed) {
                Throwable cause = createFailed.getCause() != null ? createFailed.getCause() : createFailed;
                Bukkit.getLogger().warning("Failed to build power " + power.fullId + ": " + cause.getMessage());
            }
        }
    }

    private static void synchronizePlayers(Object api) {
        Object playerPowerManager = invokeNoArgs(api, "getPlayerPowerManager");
        if (playerPowerManager == null) return;

        Class<?> powerClass;
        Method addPowerMethod;
        Method removePowerMethod;
        try {
            powerClass = Class.forName(CLASS_POWER);
            addPowerMethod = playerPowerManager.getClass().getMethod("addPower", Player.class, powerClass);
            removePowerMethod = playerPowerManager.getClass().getMethod("removePower", Player.class, powerClass);
        } catch (ClassNotFoundException | NoSuchMethodException reflectionFailed) {
            Bukkit.getLogger().log(Level.SEVERE, "Powers bridge failed to bind PlayerPowerManager", reflectionFailed);
            return;
        }

        if (powerObjectsById.isEmpty()) {
            removeAllManagedPowers(playerPowerManager, removePowerMethod);
            return;
        }

        Map<String, Set<String>> itemPowerBindings = definitions.getItemPowersByInternalName();
        ItemSetWrapper set = plugin.getSet();

        Set<UUID> onlinePlayers = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlinePlayers.add(player.getUniqueId());
            Set<String> desiredPowers = collectDesiredPowerIds(player, set, itemPowerBindings);
            Set<String> currentPowers = assignedPowerIdsByPlayer.computeIfAbsent(
                    player.getUniqueId(), ignored -> new LinkedHashSet<>()
            );

            List<String> toRemove = new ArrayList<>();
            for (String powerId : currentPowers) {
                if (!desiredPowers.contains(powerId)) toRemove.add(powerId);
            }
            for (String powerId : toRemove) {
                Object powerObject = powerObjectsById.get(powerId);
                if (powerObject != null) {
                    invokePlayerPowerChange(removePowerMethod, playerPowerManager, player, powerObject);
                }
                currentPowers.remove(powerId);
            }

            for (String powerId : desiredPowers) {
                if (currentPowers.contains(powerId)) continue;
                Object powerObject = powerObjectsById.get(powerId);
                if (powerObject == null) continue;

                if (invokePlayerPowerChange(addPowerMethod, playerPowerManager, player, powerObject)) {
                    currentPowers.add(powerId);
                }
            }

            if (currentPowers.isEmpty()) {
                assignedPowerIdsByPlayer.remove(player.getUniqueId());
            }
        }

        List<UUID> staleEntries = new ArrayList<>();
        for (UUID playerId : assignedPowerIdsByPlayer.keySet()) {
            if (!onlinePlayers.contains(playerId)) staleEntries.add(playerId);
        }
        for (UUID stalePlayerId : staleEntries) {
            assignedPowerIdsByPlayer.remove(stalePlayerId);
        }
    }

    private static Set<String> collectDesiredPowerIds(
            Player player,
            ItemSetWrapper set,
            Map<String, Set<String>> itemPowerBindings
    ) {
        LinkedHashSet<String> desired = new LinkedHashSet<>();
        PlayerInventory inventory = player.getInventory();
        if (inventory != null) {
            ItemStack[] contents = inventory.getContents();
            if (contents != null) {
                for (ItemStack stack : contents) {
                    addPowersForItemStack(set, stack, itemPowerBindings, desired);
                }
            }
            try {
                ItemStack[] armorContents = inventory.getArmorContents();
                if (armorContents != null) {
                    for (ItemStack stack : armorContents) {
                        addPowersForItemStack(set, stack, itemPowerBindings, desired);
                    }
                }
            } catch (Exception ignored) {
                // 1.8 compatibility path
            }
        }

        // Keep equipment fallback to handle edge versions where inventory arrays don't include all slots.
        EntityEquipment equipment = player.getEquipment();
        if (equipment != null) {
            addPowersForItemStack(set, equipment.getItemInMainHand(), itemPowerBindings, desired);
            addPowersForItemStack(set, equipment.getItemInOffHand(), itemPowerBindings, desired);
            addPowersForItemStack(set, equipment.getHelmet(), itemPowerBindings, desired);
            addPowersForItemStack(set, equipment.getChestplate(), itemPowerBindings, desired);
            addPowersForItemStack(set, equipment.getLeggings(), itemPowerBindings, desired);
            addPowersForItemStack(set, equipment.getBoots(), itemPowerBindings, desired);
        }

        return desired;
    }

    private static void addPowersForItemStack(
            ItemSetWrapper set,
            ItemStack itemStack,
            Map<String, Set<String>> itemPowerBindings,
            Set<String> output
    ) {
        KciItem customItem = set.getItem(itemStack);
        if (customItem == null) return;

        Set<String> itemPowers = itemPowerBindings.get(customItem.getName());
        if (itemPowers != null && !itemPowers.isEmpty()) output.addAll(itemPowers);
    }

    private static boolean matchesCustomItem(ItemStack itemStack, String customItemInternalName) {
        if (itemStack == null || customItemInternalName == null) return false;
        if (itemStack.getType() == Material.AIR) return false;

        KciItem customItem = plugin != null && plugin.getSet() != null ? plugin.getSet().getItem(itemStack) : null;
        return customItem != null && customItemInternalName.equals(customItem.getName());
    }

    private static boolean inventoryContainsCustomItem(Player player, String customItemInternalName) {
        if (player == null || customItemInternalName == null) return false;
        PlayerInventory inventory = player.getInventory();
        if (inventory == null) return false;

        ItemStack[] contents = inventory.getContents();
        if (contents != null) {
            for (ItemStack stack : contents) {
                if (matchesCustomItem(stack, customItemInternalName)) return true;
            }
        }
        return false;
    }

    private static String normalizeTrigger(String rawTrigger) {
        if (rawTrigger == null) return null;
        String normalized = rawTrigger.trim();
        if (normalized.isEmpty()) return null;

        normalized = normalized.replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ON_")) normalized = normalized.substring(3);
        if ("PICKUP".equals(normalized)) normalized = TRIGGER_PICK_UP;
        return normalized;
    }

    private static boolean isAnyClickTrigger(String trigger) {
        return TRIGGER_ANY_RIGHT_CLICK.equals(trigger) || TRIGGER_ANY_LEFT_CLICK.equals(trigger);
    }

    private static boolean isRightClickTrigger(String trigger) {
        return TRIGGER_RIGHT_CLICK.equals(trigger)
                || TRIGGER_SHIFT_RIGHT_CLICK.equals(trigger)
                || TRIGGER_ANY_RIGHT_CLICK.equals(trigger);
    }

    private static boolean isLeftClickTrigger(String trigger) {
        return TRIGGER_LEFT_CLICK.equals(trigger)
                || TRIGGER_SHIFT_LEFT_CLICK.equals(trigger)
                || TRIGGER_ANY_LEFT_CLICK.equals(trigger);
    }

    private static class CustomItemsConditionHandler implements InvocationHandler {

        private final String trigger;
        private final String customItemInternalName;

        private CustomItemsConditionHandler(String trigger, String customItemInternalName) {
            this.trigger = normalizeTrigger(trigger);
            this.customItemInternalName = customItemInternalName;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("supports".equals(methodName)) return supports(args);
            if ("matches".equals(methodName)) return matches(args);
            if ("toString".equals(methodName)) {
                return "CustomItemsCondition(" + trigger + "," + customItemInternalName + ")";
            }
            if ("hashCode".equals(methodName)) {
                int result = trigger != null ? trigger.hashCode() : 0;
                if (customItemInternalName != null) result = 31 * result + customItemInternalName.hashCode();
                return result;
            }
            if ("equals".equals(methodName)) {
                return proxy == (args != null && args.length > 0 ? args[0] : null);
            }
            return null;
        }

        private Boolean supports(Object[] args) {
            if (trigger == null || args == null || args.length != 1 || args[0] == null) return false;
            String incoming = normalizeTrigger(args[0].toString());
            return trigger.equals(incoming);
        }

        private Boolean matches(Object[] args) {
            if (trigger == null || args == null || args.length != 1 || args[0] == null) return false;
            Object context = args[0];
            String incomingTrigger = normalizeTrigger(invokeContext(context, "getTrigger"));
            if (incomingTrigger == null) {
                incomingTrigger = normalizeTrigger(invokeContext(context, "getTriggerKey"));
            }
            if (!trigger.equals(incomingTrigger)) return false;

            String matchingCustomItem = customItemInternalName;
            if (matchingCustomItem == null) {
                matchingCustomItem = inferSyntheticItemInternalName(context);
            }
            if (matchingCustomItem == null) return true;

            ItemStack mainBefore = toItemStack(invokeContextRaw(context, "getMainHandBefore"));
            ItemStack offBefore = toItemStack(invokeContextRaw(context, "getOffHandBefore"));
            ItemStack mainAfter = toItemStack(invokeContextRaw(context, "getMainHandAfter"));
            ItemStack offAfter = toItemStack(invokeContextRaw(context, "getOffHandAfter"));
            ItemStack eventItem = toItemStack(invokeContextRaw(context, "getEventItem"));
            Player player = toPlayer(invokeContextRaw(context, "getPlayer"));

            if (TRIGGER_START_HOLD_HAND.equals(trigger)) {
                return !matchesCustomItem(mainBefore, matchingCustomItem)
                        && matchesCustomItem(mainAfter, matchingCustomItem);
            }
            if (TRIGGER_START_HOLD_OFF_HAND.equals(trigger)) {
                return !matchesCustomItem(offBefore, matchingCustomItem)
                        && matchesCustomItem(offAfter, matchingCustomItem);
            }
            if (TRIGGER_START_HOLD_ANY_HAND.equals(trigger)) {
                boolean before = matchesCustomItem(mainBefore, matchingCustomItem)
                        || matchesCustomItem(offBefore, matchingCustomItem);
                boolean after = matchesCustomItem(mainAfter, matchingCustomItem)
                        || matchesCustomItem(offAfter, matchingCustomItem);
                return !before && after;
            }
            if (TRIGGER_FINISH_HOLD_HAND.equals(trigger)) {
                return matchesCustomItem(mainBefore, matchingCustomItem)
                        && !matchesCustomItem(mainAfter, matchingCustomItem);
            }
            if (TRIGGER_FINISH_HOLD_OFF_HAND.equals(trigger)) {
                return matchesCustomItem(offBefore, matchingCustomItem)
                        && !matchesCustomItem(offAfter, matchingCustomItem);
            }
            if (TRIGGER_FINISH_HOLD_ANY_HAND.equals(trigger)) {
                boolean before = matchesCustomItem(mainBefore, matchingCustomItem)
                        || matchesCustomItem(offBefore, matchingCustomItem);
                boolean after = matchesCustomItem(mainAfter, matchingCustomItem)
                        || matchesCustomItem(offAfter, matchingCustomItem);
                return before && !after;
            }
            if (TRIGGER_HOLD_INVENTORY.equals(trigger)) {
                return inventoryContainsCustomItem(player, matchingCustomItem);
            }
            if (TRIGGER_DROP.equals(trigger) || TRIGGER_PICK_UP.equals(trigger)) {
                return matchesCustomItem(eventItem, matchingCustomItem);
            }
            if (isAnyClickTrigger(trigger)) {
                return matchesCustomItem(mainAfter, matchingCustomItem)
                        || matchesCustomItem(offAfter, matchingCustomItem);
            }
            if (isRightClickTrigger(trigger) || isLeftClickTrigger(trigger)) {
                return matchesCustomItem(mainAfter, matchingCustomItem);
            }

            return false;
        }

        private static String invokeContext(Object context, String methodName) {
            Object raw = invokeContextRaw(context, methodName);
            return raw != null ? raw.toString() : null;
        }

        private static Object invokeContextRaw(Object context, String methodName) {
            try {
                return context.getClass().getMethod(methodName).invoke(context);
            } catch (Exception ignored) {
                return null;
            }
        }

        private static String inferSyntheticItemInternalName(Object context) {
            String powerId = invokeContext(context, "getPowerId");
            if (powerId == null) {
                Object power = invokeContextRaw(context, "getPower");
                powerId = invokeObject(power, "getId");
            }
            if (powerId == null || !powerId.startsWith(SYNTHETIC_ITEM_POWER_PREFIX)) return null;

            String internalName = powerId.substring(SYNTHETIC_ITEM_POWER_PREFIX.length());
            return internalName.isEmpty() ? null : internalName;
        }

        private static String invokeObject(Object object, String methodName) {
            if (object == null) return null;
            try {
                Object result = object.getClass().getMethod(methodName).invoke(object);
                return result != null ? result.toString() : null;
            } catch (Exception ignored) {
                return null;
            }
        }

        private static ItemStack toItemStack(Object raw) {
            return raw instanceof ItemStack ? (ItemStack) raw : null;
        }

        private static Player toPlayer(Object raw) {
            return raw instanceof Player ? (Player) raw : null;
        }
    }

    private static void removeAllManagedPowers(Object api) {
        Object playerPowerManager = invokeNoArgs(api, "getPlayerPowerManager");
        if (playerPowerManager == null) return;

        Method removePowerMethod;
        try {
            Class<?> powerClass = Class.forName(CLASS_POWER);
            removePowerMethod = playerPowerManager.getClass().getMethod("removePower", Player.class, powerClass);
        } catch (ClassNotFoundException | NoSuchMethodException reflectionFailed) {
            Bukkit.getLogger().log(Level.SEVERE, "Powers bridge failed to remove managed powers", reflectionFailed);
            return;
        }

        removeAllManagedPowers(playerPowerManager, removePowerMethod);
    }

    private static void removeAllManagedPowers(Object playerPowerManager, Method removePowerMethod) {
        for (Map.Entry<UUID, Set<String>> entry : assignedPowerIdsByPlayer.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;

            for (String powerId : entry.getValue()) {
                Object powerObject = powerObjectsById.get(powerId);
                if (powerObject != null) {
                    invokePlayerPowerChange(removePowerMethod, playerPowerManager, player, powerObject);
                }
            }
        }
        assignedPowerIdsByPlayer.clear();
    }

    private static boolean invokePlayerPowerChange(
            Method method, Object playerPowerManager, Player player, Object powerObject
    ) {
        try {
            Object result = method.invoke(playerPowerManager, player, powerObject);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Exception failed) {
            Throwable cause = failed.getCause() != null ? failed.getCause() : failed;
            Bukkit.getLogger().warning("Failed to call " + method.getName() + " for " + player.getName()
                    + ": " + cause.getMessage());
            return false;
        }
    }

    private static Object invokeNoArgs(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception failed) {
            Bukkit.getLogger().warning("Powers bridge failed to call " + methodName + ": " + failed.getMessage());
            return null;
        }
    }
}
