package nl.knokko.customitems.nms19;

import net.minecraft.server.level.WorldServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.projectile.EntityTippedArrow;
import nl.knokko.customitems.nms16plus.KciNmsEntities16Plus;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.lang.reflect.Method;

class KciNmsEntities19 extends KciNmsEntities16Plus {

    private static final Method DAMAGE_SOURCES_METHOD = findDamageSourcesMethod();
    private static final Method DAMAGE_METHOD = findDamageMethod();
    private static volatile Method PROJECTILE_DAMAGE_METHOD;
    private static final Method SET_OWNER_METHOD = findSetOwnerMethod();

    @Override
    public void causeFakeProjectileDamage(
            Entity toDamage, LivingEntity responsibleShooter, float damage,
            double projectilePositionX, double projectilePositionY, double projectilePositionZ,
            double projectileMotionX, double projectileMotionY, double projectileMotionZ
    ) {

        Object worldHandle = getHandle(toDamage.getWorld());
        Object shooterHandle = getHandle(responsibleShooter);
        Object targetHandle = getHandle(toDamage);

        EntityTippedArrow fakeArrow = new EntityTippedArrow((WorldServer) worldHandle,
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
            DamageSources sources = (DamageSources) DAMAGE_SOURCES_METHOD.invoke(targetHandle);
            Method method = PROJECTILE_DAMAGE_METHOD;
            if (method == null) {
                method = findProjectileDamageMethod(sources.getClass(), projectileHandle.getClass(), ownerHandle.getClass());
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
        Method method = findZeroArgMethodReturning(net.minecraft.world.entity.Entity.class, DamageSources.class);
        if (method == null) {
            throw new IllegalStateException("Can't find DamageSources accessor for 1.19.x");
        }
        return method;
    }

    private static Method findDamageMethod() {
        Method method = findDamageMethodIn(net.minecraft.world.entity.Entity.class);
        if (method == null) method = findDamageMethodIn(net.minecraft.world.entity.EntityLiving.class);
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
        Class<?> livingClass = net.minecraft.world.entity.EntityLiving.class;
        String[] candidateNames = { "b", "setOwner", "setShooter", "a" };
        for (String candidateName : candidateNames) {
            try {
                return EntityTippedArrow.class.getMethod(candidateName, livingClass);
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (String candidateName : candidateNames) {
            try {
                Method method = EntityTippedArrow.class.getDeclaredMethod(candidateName, livingClass);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }

        for (Method method : EntityTippedArrow.class.getMethods()) {
            if (method.getParameterCount() == 1 && method.getReturnType() == void.class) {
                Class<?> param = method.getParameterTypes()[0];
                if (param.isAssignableFrom(livingClass)) {
                    return method;
                }
            }
        }

        for (Method method : EntityTippedArrow.class.getMethods()) {
            if (method.getParameterCount() == 1 && method.getReturnType() == void.class) {
                Class<?> param = method.getParameterTypes()[0];
                if (net.minecraft.world.entity.Entity.class.isAssignableFrom(param)) {
                    return method;
                }
            }
        }
        for (Method method : EntityTippedArrow.class.getDeclaredMethods()) {
            if (method.getParameterCount() == 1 && method.getReturnType() == void.class) {
                Class<?> param = method.getParameterTypes()[0];
                if (param.isAssignableFrom(livingClass)
                        || net.minecraft.world.entity.Entity.class.isAssignableFrom(param)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }

        throw new IllegalStateException("Can't find projectile owner method for 1.19.x");
    }
}
