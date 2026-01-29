package nl.knokko.customitems.nms16;

import nl.knokko.customitems.nms16plus.KciNmsEntities16Plus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.projectiles.ProjectileSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class KciNmsEntities16 extends KciNmsEntities16Plus {

    private static final String CRAFT_VERSION = resolveCraftVersion();
    private static final Class<?> NMS_ENTITY_CLASS = loadNmsClass("Entity");
    private static final Class<?> NMS_ENTITY_LIVING_CLASS = loadOptionalNmsClass("EntityLiving");
    private static final Class<?> NMS_DAMAGE_SOURCE_CLASS = loadNmsClass("DamageSource");

    private static final Method DAMAGE_METHOD = findDamageMethod();
    private static final Method SET_SHOOTER_METHOD = findSetShooterMethod();
    private static final Constructor<?> FIREBALL_CONSTRUCTOR = findFireballConstructor();
    private static final Constructor<?> DAMAGE_SOURCE_INDIRECT_CONSTRUCTOR = findDamageSourceIndirectConstructor();
    private static final Constructor<?> ENTITY_DAMAGE_SOURCE_CONSTRUCTOR = findEntityDamageSourceConstructor();
    private static final Method SET_IGNORE_ARMOR_METHOD = findOptionalMethod(NMS_DAMAGE_SOURCE_CLASS, "setIgnoreArmor");
    private static final Method SET_FIRE_METHOD = findOptionalMethod(NMS_DAMAGE_SOURCE_CLASS, "setFire");

    @Override
    public void causeFakeProjectileDamage(
            Entity toDamage, LivingEntity responsibleShooter, float damage,
            double projectilePositionX, double projectilePositionY, double projectilePositionZ,
            double projectileMotionX, double projectileMotionY, double projectileMotionZ
    ) {

        Object worldHandle = getHandle(toDamage.getWorld());
        Object shooterHandle = getHandle(responsibleShooter);
        Object targetHandle = getHandle(toDamage);

        Object fakeProjectile = createFakeFireball(
                worldHandle, projectilePositionX, projectilePositionY, projectilePositionZ,
                projectileMotionX, projectileMotionY, projectileMotionZ
        );
        setShooter(fakeProjectile, shooterHandle);
        setProjectileSource(fakeProjectile, responsibleShooter);

        Object damageSource = createIndirectDamageSource("thrown", fakeProjectile, shooterHandle);
        applyDamage(targetHandle, damageSource, damage);
    }

    @Override
    public void causeCustomPhysicalAttack(
            Entity attacker, Entity target, float damage,
            String damageCauseName, boolean ignoresArmor, boolean isFire
    ) {
        Object attackerHandle = getHandle(attacker);
        Object targetHandle = getHandle(target);
        Object damageSource = createEntityDamageSource(damageCauseName, attackerHandle);
        if (ignoresArmor) invokeIfPresent(damageSource, SET_IGNORE_ARMOR_METHOD);
        if (isFire) invokeIfPresent(damageSource, SET_FIRE_METHOD);
        applyDamage(targetHandle, damageSource, damage);
    }

    private static Object getHandle(Object craftObject) {
        try {
            Method method = craftObject.getClass().getMethod("getHandle");
            return method.invoke(craftObject);
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException("Failed to resolve NMS handle for " + craftObject.getClass().getName(), failure);
        }
    }

    private static Object createFakeFireball(
            Object worldHandle, double x, double y, double z, double motX, double motY, double motZ
    ) {
        try {
            return FIREBALL_CONSTRUCTOR.newInstance(worldHandle, x, y, z, motX, motY, motZ);
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException("Failed to construct fake projectile for 1.16.x", failure);
        }
    }

    private static void setShooter(Object projectileHandle, Object shooterHandle) {
        try {
            SET_SHOOTER_METHOD.invoke(projectileHandle, shooterHandle);
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException("Failed to set projectile shooter for 1.16.x", failure);
        }
    }

    private static void setProjectileSource(Object projectileHandle, ProjectileSource source) {
        Field field = findField(projectileHandle.getClass(), "projectileSource");
        if (field == null) return;
        try {
            field.set(projectileHandle, source);
        } catch (IllegalAccessException ignored) {
        }
    }

    private static Object createIndirectDamageSource(String name, Object projectileHandle, Object shooterHandle) {
        try {
            return DAMAGE_SOURCE_INDIRECT_CONSTRUCTOR.newInstance(name, projectileHandle, shooterHandle);
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException("Failed to create indirect DamageSource for 1.16.x", failure);
        }
    }

    private static Object createEntityDamageSource(String name, Object attackerHandle) {
        try {
            return ENTITY_DAMAGE_SOURCE_CONSTRUCTOR.newInstance(name, attackerHandle);
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException("Failed to create entity DamageSource for 1.16.x", failure);
        }
    }

    private static void applyDamage(Object targetHandle, Object damageSource, float damage) {
        try {
            if (DAMAGE_METHOD.getParameterTypes()[1] == double.class) {
                DAMAGE_METHOD.invoke(targetHandle, damageSource, (double) damage);
            } else {
                DAMAGE_METHOD.invoke(targetHandle, damageSource, damage);
            }
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException("Failed to apply damage for 1.16.x", failure);
        }
    }

    private static Method findDamageMethod() {
        Method method = findDamageMethodIn(NMS_ENTITY_CLASS);
        if (method == null && NMS_ENTITY_LIVING_CLASS != null) {
            method = findDamageMethodIn(NMS_ENTITY_LIVING_CLASS);
        }
        if (method == null) {
            throw new IllegalStateException("Can't find damage method for 1.16.x");
        }
        return method;
    }

    private static Method findDamageMethodIn(Class<?> owner) {
        for (Method method : owner.getMethods()) {
            if (isDamageMethod(method)) return method;
        }
        for (Method method : owner.getDeclaredMethods()) {
            if (isDamageMethod(method)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static boolean isDamageMethod(Method method) {
        if (method.getParameterCount() != 2) return false;
        Class<?>[] params = method.getParameterTypes();
        if (!params[0].isAssignableFrom(NMS_DAMAGE_SOURCE_CLASS) && !NMS_DAMAGE_SOURCE_CLASS.isAssignableFrom(params[0])) {
            return false;
        }
        return params[1] == float.class || params[1] == double.class;
    }

    private static Method findSetShooterMethod() {
        Class<?> preferredOwner = NMS_ENTITY_LIVING_CLASS != null ? NMS_ENTITY_LIVING_CLASS : NMS_ENTITY_CLASS;
        Class<?> projectileClass = loadNmsClass("EntitySmallFireball");
        String[] candidateNames = { "setShooter", "a", "b", "setOwner" };
        for (String candidateName : candidateNames) {
            Method method = findMethod(projectileClass, candidateName, preferredOwner);
            if (method != null) return method;
        }
        for (Method method : projectileClass.getMethods()) {
            if (method.getParameterCount() == 1 && method.getReturnType() == void.class) {
                Class<?> param = method.getParameterTypes()[0];
                if (param.isAssignableFrom(preferredOwner)) {
                    return method;
                }
            }
        }
        for (Method method : projectileClass.getDeclaredMethods()) {
            if (method.getParameterCount() == 1 && method.getReturnType() == void.class) {
                Class<?> param = method.getParameterTypes()[0];
                if (param.isAssignableFrom(preferredOwner)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        for (Method method : projectileClass.getMethods()) {
            if (method.getParameterCount() == 1 && method.getReturnType() == void.class) {
                Class<?> param = method.getParameterTypes()[0];
                if (param.isAssignableFrom(NMS_ENTITY_CLASS)) {
                    return method;
                }
            }
        }
        throw new IllegalStateException("Can't find setShooter method for 1.16.x");
    }

    private static Constructor<?> findFireballConstructor() {
        Class<?> projectileClass = loadNmsClass("EntitySmallFireball");
        Class<?> worldClass = loadNmsClass("World");
        for (Constructor<?> constructor : projectileClass.getConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length == 7
                    && params[0].isAssignableFrom(worldClass)
                    && params[1] == double.class && params[2] == double.class && params[3] == double.class
                    && params[4] == double.class && params[5] == double.class && params[6] == double.class) {
                return constructor;
            }
        }
        for (Constructor<?> constructor : projectileClass.getDeclaredConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length == 7
                    && params[0].isAssignableFrom(worldClass)
                    && params[1] == double.class && params[2] == double.class && params[3] == double.class
                    && params[4] == double.class && params[5] == double.class && params[6] == double.class) {
                constructor.setAccessible(true);
                return constructor;
            }
        }
        throw new IllegalStateException("Can't find EntitySmallFireball constructor for 1.16.x");
    }

    private static Constructor<?> findDamageSourceIndirectConstructor() {
        Class<?> damageSourceIndirect = loadNmsClass("EntityDamageSourceIndirect");
        for (Constructor<?> constructor : damageSourceIndirect.getConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length == 3 && params[0] == String.class
                    && params[1].isAssignableFrom(NMS_ENTITY_CLASS)
                    && params[2].isAssignableFrom(NMS_ENTITY_CLASS)) {
                return constructor;
            }
        }
        for (Constructor<?> constructor : damageSourceIndirect.getDeclaredConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length == 3 && params[0] == String.class
                    && params[1].isAssignableFrom(NMS_ENTITY_CLASS)
                    && params[2].isAssignableFrom(NMS_ENTITY_CLASS)) {
                constructor.setAccessible(true);
                return constructor;
            }
        }
        throw new IllegalStateException("Can't find EntityDamageSourceIndirect constructor for 1.16.x");
    }

    private static Constructor<?> findEntityDamageSourceConstructor() {
        Class<?> damageSourceClass = loadNmsClass("EntityDamageSource");
        for (Constructor<?> constructor : damageSourceClass.getConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length == 2 && params[0] == String.class && params[1].isAssignableFrom(NMS_ENTITY_CLASS)) {
                return constructor;
            }
        }
        for (Constructor<?> constructor : damageSourceClass.getDeclaredConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length == 2 && params[0] == String.class && params[1].isAssignableFrom(NMS_ENTITY_CLASS)) {
                constructor.setAccessible(true);
                return constructor;
            }
        }
        throw new IllegalStateException("Can't find EntityDamageSource constructor for 1.16.x");
    }

    private static Method findOptionalMethod(Class<?> owner, String name) {
        try {
            return owner.getMethod(name);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            Method method = owner.getDeclaredMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static void invokeIfPresent(Object target, Method method) {
        if (method == null) return;
        try {
            method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?> paramType) {
        try {
            return owner.getMethod(name, paramType);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            Method method = owner.getDeclaredMethod(name, paramType);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> owner, String name) {
        Class<?> current = owner;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Class<?> loadNmsClass(String simpleName) {
        return loadNmsClass(simpleName, true);
    }

    private static Class<?> loadOptionalNmsClass(String simpleName) {
        return loadNmsClass(simpleName, false);
    }

    private static Class<?> loadNmsClass(String simpleName, boolean required) {
        try {
            return Class.forName("net.minecraft.server." + CRAFT_VERSION + "." + simpleName);
        } catch (ClassNotFoundException failure) {
            if (required) {
                throw new IllegalStateException("Can't find NMS class " + simpleName + " for 1.16.x", failure);
            }
            return null;
        }
    }

    private static String resolveCraftVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }
}
