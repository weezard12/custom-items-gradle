package nl.knokko.customitems.nms19;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.EntityTippedArrow;
import nl.knokko.customitems.nms16plus.KciNmsEntities16Plus;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

class KciNmsEntities19 extends KciNmsEntities16Plus {

    private static final Method DAMAGE_SOURCES_METHOD = findDamageSourcesMethod();
    private static final Method DAMAGE_METHOD = findDamageMethod();
    private static volatile Method PROJECTILE_DAMAGE_METHOD;
    private static final Method SET_OWNER_METHOD = findSetOwnerMethod();
    private static volatile Constructor<?> ARROW_CONSTRUCTOR_SIMPLE;
    private static volatile Constructor<?> ARROW_CONSTRUCTOR_WITH_ITEM;

    @Override
    public void causeFakeProjectileDamage(
            Entity toDamage, LivingEntity responsibleShooter, float damage,
            double projectilePositionX, double projectilePositionY, double projectilePositionZ,
            double projectileMotionX, double projectileMotionY, double projectileMotionZ
    ) {

        Object worldHandle = getHandle(toDamage.getWorld());
        Object shooterHandle = getHandle(responsibleShooter);
        Object targetHandle = getHandle(toDamage);

        EntityTippedArrow fakeArrow = createFakeArrow(worldHandle,
                projectilePositionX, projectileMotionY, projectileMotionZ);
        setOwner(fakeArrow, shooterHandle);
        fakeArrow.projectileSource = responsibleShooter;

        DamageSource damageSource = createProjectileDamageSource(targetHandle, fakeArrow, shooterHandle);
        applyDamage(targetHandle, damageSource, damage);
    }

    @Override
    public void causeCustomPhysicalAttack(Entity attacker, Entity target, float damage, String damageCauseName, boolean ignoresArmor, boolean isFire) {
        throw new UnsupportedOperationException("Custom physical attacks are only supported in MC 1.18 and earlier");
    }

    private static Object getHandle(Object craftObject) {
        try {
            Method method = craftObject.getClass().getMethod("getHandle");
            return method.invoke(craftObject);
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException("Failed to resolve NMS handle for " + craftObject.getClass().getName(), failure);
        }
    }

    private static void setOwner(EntityTippedArrow arrow, Object ownerHandle) {
        try {
            SET_OWNER_METHOD.invoke(arrow, ownerHandle);
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException("Failed to set projectile owner in 1.19.x", failure);
        }
    }

    private static DamageSource createProjectileDamageSource(Object targetHandle, Object projectileHandle, Object ownerHandle) {
        try {
            Object sources = DAMAGE_SOURCES_METHOD.invoke(targetHandle);
            Method method = PROJECTILE_DAMAGE_METHOD;
            if (method == null) {
                method = findProjectileDamageMethod(
                        sources.getClass(), projectileHandle.getClass(), ownerHandle.getClass()
                );
                PROJECTILE_DAMAGE_METHOD = method;
            }
            return (DamageSource) method.invoke(sources, projectileHandle, ownerHandle);
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException("Failed to create projectile DamageSource for 1.19.x", failure);
        }
    }

    private static void applyDamage(Object targetHandle, DamageSource damageSource, float damage) {
        try {
            if (DAMAGE_METHOD.getParameterTypes()[1] == double.class) {
                DAMAGE_METHOD.invoke(targetHandle, damageSource, (double) damage);
            } else {
                DAMAGE_METHOD.invoke(targetHandle, damageSource, damage);
            }
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException("Failed to apply damage for 1.19.x", failure);
        }
    }

    private static Method findDamageSourcesMethod() {
        Class<?> entityClass = net.minecraft.world.entity.Entity.class;
        Class<?> damageSourcesClass = loadNmsClass("net.minecraft.world.damagesource.DamageSources");
        Method method = null;
        if (damageSourcesClass != null) {
            method = findZeroArgMethodReturning(entityClass, damageSourcesClass);
        }
        if (method != null) return method;

        method = findZeroArgMethodReturning(entityClass, "net.minecraft.world.damagesource.");
        if (method == null) {
            throw new IllegalStateException("Can't find DamageSources accessor for 1.19.x");
        }
        return method;
    }

    private static Method findDamageMethod() {
        Method method = findDamageMethodIn(net.minecraft.world.entity.Entity.class);
        if (method == null) {
            Class<?> livingClass = loadNmsClass("net.minecraft.world.entity.EntityLiving");
            if (livingClass == null) {
                livingClass = loadNmsClass("net.minecraft.world.entity.LivingEntity");
            }
            if (livingClass != null) {
                method = findDamageMethodIn(livingClass);
            }
        }
        if (method == null) {
            throw new IllegalStateException("Can't find damage method for 1.19.x");
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
        if (!DamageSource.class.isAssignableFrom(params[0])) return false;
        return params[1] == float.class || params[1] == double.class;
    }

    private static Method findZeroArgMethodReturning(Class<?> owner, Class<?> returnType) {
        for (Method method : owner.getMethods()) {
            if (method.getParameterCount() == 0 && returnType.isAssignableFrom(method.getReturnType())) {
                return method;
            }
        }
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && returnType.isAssignableFrom(method.getReturnType())) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Method findZeroArgMethodReturning(Class<?> owner, String returnTypePrefix) {
        for (Method method : owner.getMethods()) {
            if (method.getParameterCount() != 0) continue;
            Class<?> returnType = method.getReturnType();
            if (returnType.getName().startsWith(returnTypePrefix) && hasDamageSourceFactory(returnType)) {
                return method;
            }
        }
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getParameterCount() != 0) continue;
            Class<?> returnType = method.getReturnType();
            if (returnType.getName().startsWith(returnTypePrefix) && hasDamageSourceFactory(returnType)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static boolean hasDamageSourceFactory(Class<?> owner) {
        for (Method method : owner.getMethods()) {
            if (method.getParameterCount() == 2 && DamageSource.class.isAssignableFrom(method.getReturnType())) {
                return true;
            }
        }
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getParameterCount() == 2 && DamageSource.class.isAssignableFrom(method.getReturnType())) {
                return true;
            }
        }
        return false;
    }

    private static Method findProjectileDamageMethod(Class<?> sourcesClass, Class<?> projectileClass, Class<?> ownerClass) {
        Class<?> entityClass = net.minecraft.world.entity.Entity.class;
        Method fallback = null;

        for (Method method : sourcesClass.getMethods()) {
            if (method.getParameterCount() != 2 || !DamageSource.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params[0].isAssignableFrom(projectileClass) && params[1].isAssignableFrom(ownerClass)) {
                return method;
            }
        }
        for (Method method : sourcesClass.getDeclaredMethods()) {
            if (method.getParameterCount() != 2 || !DamageSource.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params[0].isAssignableFrom(projectileClass) && params[1].isAssignableFrom(ownerClass)) {
                method.setAccessible(true);
                return method;
            }
        }

        for (Method method : sourcesClass.getMethods()) {
            if (method.getParameterCount() != 2 || !DamageSource.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params[0].isAssignableFrom(projectileClass)
                    && entityClass.isAssignableFrom(params[1])
                    && params[0] != entityClass) {
                return method;
            }
            if (params[0].isAssignableFrom(projectileClass) && entityClass.isAssignableFrom(params[1])) {
                fallback = method;
            }
        }
        for (Method method : sourcesClass.getDeclaredMethods()) {
            if (method.getParameterCount() != 2 || !DamageSource.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params[0].isAssignableFrom(projectileClass)
                    && entityClass.isAssignableFrom(params[1])
                    && params[0] != entityClass) {
                method.setAccessible(true);
                return method;
            }
            if (params[0].isAssignableFrom(projectileClass) && entityClass.isAssignableFrom(params[1])) {
                method.setAccessible(true);
                fallback = method;
            }
        }
        if (fallback != null) return fallback;

        for (Method method : sourcesClass.getMethods()) {
            if (method.getParameterCount() != 2 || !DamageSource.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (entityClass.isAssignableFrom(params[0]) && entityClass.isAssignableFrom(params[1])) {
                return method;
            }
        }
        for (Method method : sourcesClass.getDeclaredMethods()) {
            if (method.getParameterCount() != 2 || !DamageSource.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (entityClass.isAssignableFrom(params[0]) && entityClass.isAssignableFrom(params[1])) {
                method.setAccessible(true);
                return method;
            }
        }

        throw new IllegalStateException("Can't find projectile DamageSource method for 1.19.x");
    }

    private static Method findSetOwnerMethod() {
        Class<?> entityClass = net.minecraft.world.entity.Entity.class;
        Class<?> livingClass = loadNmsClass("net.minecraft.world.entity.EntityLiving");
        if (livingClass == null) {
            livingClass = loadNmsClass("net.minecraft.world.entity.LivingEntity");
        }
        Class<?> preferredOwnerClass = livingClass != null ? livingClass : entityClass;
        String[] candidateNames = { "b", "setOwner", "setShooter", "a" };
        for (String candidateName : candidateNames) {
            try {
                return EntityTippedArrow.class.getMethod(candidateName, preferredOwnerClass);
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (String candidateName : candidateNames) {
            try {
                Method method = EntityTippedArrow.class.getDeclaredMethod(candidateName, preferredOwnerClass);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }

        for (Method method : EntityTippedArrow.class.getMethods()) {
            if (method.getParameterCount() == 1 && method.getReturnType() == void.class) {
                Class<?> param = method.getParameterTypes()[0];
                if (param.isAssignableFrom(preferredOwnerClass)) {
                    return method;
                }
            }
        }

        for (Method method : EntityTippedArrow.class.getMethods()) {
            if (method.getParameterCount() == 1 && method.getReturnType() == void.class) {
                Class<?> param = method.getParameterTypes()[0];
                if (entityClass.isAssignableFrom(param)) {
                    return method;
                }
            }
        }
        for (Method method : EntityTippedArrow.class.getDeclaredMethods()) {
            if (method.getParameterCount() == 1 && method.getReturnType() == void.class) {
                Class<?> param = method.getParameterTypes()[0];
                if (param.isAssignableFrom(preferredOwnerClass)
                        || entityClass.isAssignableFrom(param)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }

        throw new IllegalStateException("Can't find projectile owner method for 1.19.x");
    }

    private static EntityTippedArrow createFakeArrow(Object worldHandle, double x, double y, double z) {
        Constructor<?> constructor = ARROW_CONSTRUCTOR_SIMPLE;
        if (constructor != null) {
            return constructArrow(constructor, worldHandle, x, y, z, null);
        }
        constructor = ARROW_CONSTRUCTOR_WITH_ITEM;
        if (constructor != null) {
            return constructArrow(constructor, worldHandle, x, y, z, createArrowStack());
        }

        Class<?> worldClass = worldHandle.getClass();
        for (Constructor<?> candidate : EntityTippedArrow.class.getConstructors()) {
            Class<?>[] params = candidate.getParameterTypes();
            if (params.length == 4 && params[0].isAssignableFrom(worldClass)
                    && params[1] == double.class && params[2] == double.class && params[3] == double.class) {
                ARROW_CONSTRUCTOR_SIMPLE = candidate;
                return constructArrow(candidate, worldHandle, x, y, z, null);
            }
            if (params.length == 5 && params[0].isAssignableFrom(worldClass)
                    && params[1] == double.class && params[2] == double.class && params[3] == double.class
                    && net.minecraft.world.item.ItemStack.class.isAssignableFrom(params[4])) {
                ARROW_CONSTRUCTOR_WITH_ITEM = candidate;
                return constructArrow(candidate, worldHandle, x, y, z, createArrowStack());
            }
        }

        for (Constructor<?> candidate : EntityTippedArrow.class.getDeclaredConstructors()) {
            Class<?>[] params = candidate.getParameterTypes();
            if (params.length == 4 && params[0].isAssignableFrom(worldClass)
                    && params[1] == double.class && params[2] == double.class && params[3] == double.class) {
                candidate.setAccessible(true);
                ARROW_CONSTRUCTOR_SIMPLE = candidate;
                return constructArrow(candidate, worldHandle, x, y, z, null);
            }
            if (params.length == 5 && params[0].isAssignableFrom(worldClass)
                    && params[1] == double.class && params[2] == double.class && params[3] == double.class
                    && net.minecraft.world.item.ItemStack.class.isAssignableFrom(params[4])) {
                candidate.setAccessible(true);
                ARROW_CONSTRUCTOR_WITH_ITEM = candidate;
                return constructArrow(candidate, worldHandle, x, y, z, createArrowStack());
            }
        }

        throw new IllegalStateException("Can't find EntityTippedArrow constructor for 1.19.x");
    }

    private static EntityTippedArrow constructArrow(Constructor<?> constructor, Object worldHandle,
                                                    double x, double y, double z,
                                                    net.minecraft.world.item.ItemStack stack) {
        try {
            if (constructor.getParameterCount() == 4) {
                return (EntityTippedArrow) constructor.newInstance(worldHandle, x, y, z);
            }
            return (EntityTippedArrow) constructor.newInstance(worldHandle, x, y, z, stack);
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException("Failed to construct fake arrow for 1.19.x", failure);
        }
    }

    private static net.minecraft.world.item.ItemStack createArrowStack() {
        ItemStack bukkitStack = new ItemStack(Material.ARROW, 1);
        return asNmsCopy(bukkitStack);
    }

    private static net.minecraft.world.item.ItemStack asNmsCopy(ItemStack stack) {
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            String craftVersion = packageName.substring(packageName.lastIndexOf('.') + 1);
            Class<?> craftItemStack = Class.forName(
                    "org.bukkit.craftbukkit." + craftVersion + ".inventory.CraftItemStack"
            );
            Method asNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
            Object nmsStack = asNmsCopy.invoke(null, stack);
            return (net.minecraft.world.item.ItemStack) nmsStack;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            throw new IllegalStateException("Failed to convert ItemStack to NMS for 1.19.x", ex);
        }
    }

    private static Class<?> loadNmsClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
