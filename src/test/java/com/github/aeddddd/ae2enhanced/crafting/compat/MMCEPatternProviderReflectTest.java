package com.github.aeddddd.ae2enhanced.crafting.compat;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MMCEPatternProviderReflectTest {

    private static ICraftingPatternDetails detail(String name) {
        return (ICraftingPatternDetails) Proxy.newProxyInstance(
                MMCEPatternProviderReflectTest.class.getClassLoader(),
                new Class<?>[]{ICraftingPatternDetails.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return name;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    @Test
    void listDetailsSupportReadWriteAndIndexOf() throws Exception {
        ICraftingPatternDetails first = detail("first");
        ICraftingPatternDetails second = detail("second");
        ListHolder holder = new ListHolder(first, second);
        MMCEPatternProviderReflect.DetailsAccess access = new MMCEPatternProviderReflect.DetailsAccess(
                holder, ListHolder.class.getDeclaredField("details"));

        assertThat(access.get(0)).isSameAs(first);
        assertThat(access.indexOf(second)).isEqualTo(1);
        access.set(1, first);
        assertThat(access.get(1)).isSameAs(first);
        assertThat(access.indexOf(second)).isEqualTo(-1);
    }

    @Test
    void arrayDetailsSupportReadWriteAndIndexOf() throws Exception {
        ICraftingPatternDetails first = detail("first");
        ICraftingPatternDetails second = detail("second");
        ArrayHolder holder = new ArrayHolder(first, second);
        MMCEPatternProviderReflect.DetailsAccess access = new MMCEPatternProviderReflect.DetailsAccess(
                holder, ArrayHolder.class.getDeclaredField("details"));

        assertThat(access.get(0)).isSameAs(first);
        assertThat(access.indexOf(second)).isEqualTo(1);
        access.set(1, first);
        assertThat(access.get(1)).isSameAs(first);
        assertThat(access.indexOf(second)).isEqualTo(-1);
    }

    @Test
    void unsupportedDetailsShapeIsRejected() throws Exception {
        UnsupportedHolder holder = new UnsupportedHolder();
        assertThatThrownBy(() -> new MMCEPatternProviderReflect.DetailsAccess(
                holder, UnsupportedHolder.class.getDeclaredField("details")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported details field type");
    }

    private static final class ListHolder {
        private final List<ICraftingPatternDetails> details;

        private ListHolder(ICraftingPatternDetails... details) {
            this.details = new ArrayList<>(Arrays.asList(details));
        }
    }

    private static final class ArrayHolder {
        private final ICraftingPatternDetails[] details;

        private ArrayHolder(ICraftingPatternDetails... details) {
            this.details = details;
        }
    }

    private static final class UnsupportedHolder {
        private final String details = "unsupported";
    }
}
