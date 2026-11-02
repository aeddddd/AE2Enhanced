package com.github.aeddddd.ae2enhanced.crafting.compat;

import appeng.api.networking.crafting.ICraftingPatternDetails;

import java.lang.reflect.Field;
import java.util.List;

/** MMCE-CE 2.2.2 List 与 2.3.2+ 数组样板详情存储的隔离访问层。 */
public final class MMCEPatternProviderReflect {

    private static final Field DETAILS = findDetails();

    private MMCEPatternProviderReflect() {
    }

    private static Field findDetails() {
        try {
            ClassLoader loader = MMCEPatternProviderReflect.class.getClassLoader();
            Class<?> provider = Class.forName("github.kasuminova.mmce.common.tile.MEPatternProvider", false, loader);
            return provider.getDeclaredField("details");
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static DetailsAccess details(Object provider) {
        if (DETAILS == null || provider == null || !DETAILS.getDeclaringClass().isInstance(provider)) {
            throw new IllegalStateException("Unsupported MMCE MEPatternProvider details storage");
        }
        return new DetailsAccess(provider, DETAILS);
    }

    public static final class DetailsAccess {
        private final Object owner;
        private final Field field;
        private final boolean list;

        public DetailsAccess(Object owner, Field field) {
            if (owner == null || field == null || !field.getDeclaringClass().isInstance(owner)) {
                throw new IllegalArgumentException("Invalid details owner");
            }
            Class<?> type = field.getType();
            if (!List.class.isAssignableFrom(type)
                    && (!type.isArray() || !ICraftingPatternDetails.class.isAssignableFrom(type.getComponentType()))) {
                throw new IllegalArgumentException("Unsupported details field type: " + type.getName());
            }
            this.owner = owner;
            this.field = field;
            this.list = List.class.isAssignableFrom(type);
            this.field.setAccessible(true);
        }

        public ICraftingPatternDetails get(int slot) {
            try {
                Object value = field.get(owner);
                if (list) {
                    return ((List<ICraftingPatternDetails>) value).get(slot);
                }
                return ((ICraftingPatternDetails[]) value)[slot];
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot read MMCE pattern details", exception);
            }
        }

        public void set(int slot, ICraftingPatternDetails detail) {
            try {
                Object value = field.get(owner);
                if (list) {
                    ((List<ICraftingPatternDetails>) value).set(slot, detail);
                } else {
                    ((ICraftingPatternDetails[]) value)[slot] = detail;
                }
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot write MMCE pattern details", exception);
            }
        }

        public int indexOf(ICraftingPatternDetails pattern) {
            try {
                Object value = field.get(owner);
                if (list) {
                    return ((List<ICraftingPatternDetails>) value).indexOf(pattern);
                }
                ICraftingPatternDetails[] details = (ICraftingPatternDetails[]) value;
                for (int slot = 0; slot < details.length; slot++) {
                    if (pattern.equals(details[slot])) {
                        return slot;
                    }
                }
                return -1;
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot scan MMCE pattern details", exception);
            }
        }
    }
}
