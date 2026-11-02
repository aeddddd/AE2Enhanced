package com.github.aeddddd.ae2enhanced.config;

import com.github.aeddddd.ae2enhanced.runtime.ModProcessScope;
import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;
import com.github.aeddddd.ae2enhanced.runtime.ServerScope;
import com.github.aeddddd.ae2enhanced.specialcrafting.RequestConfigSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigLifecycleTest {

    @AfterEach
    void closeServer() {
        RuntimeScopes.currentServer().ifPresent(scope -> {
            scope.close();
            RuntimeScopes.releaseServer();
        });
    }

    @Test
    void publishedServerConfigIsVersionedAndOldRequestDoesNotChange() {
        int originalBudget = AE2EnhancedConfig.crafting.nativeCalcBudgetMs;
        boolean originalSpecial = AE2EnhancedConfig.crafting.specialCrafting;
        try {
            RuntimeScopes.startServer();
            ServerConfig first = ServerConfig.current();
            RequestConfigSnapshot request = RequestConfigSnapshot.capture(first);

            AE2EnhancedConfig.crafting.nativeCalcBudgetMs = originalBudget + 1;
            AE2EnhancedConfig.crafting.specialCrafting = !originalSpecial;
            ServerConfig second = ServerConfig.publish();

            assertThat(second.version).isEqualTo(first.version + 1);
            assertThat(ServerConfig.current()).isSameAs(second);
            assertThat(request.serverConfigVersion).isEqualTo(first.version);
            assertThat(request.nativeCalcBudgetMs).isEqualTo(originalBudget);
            assertThat(request.specialCrafting).isEqualTo(originalSpecial);
            assertThat(RequestConfigSnapshot.capture().serverConfigVersion).isEqualTo(second.version);
        } finally {
            AE2EnhancedConfig.crafting.nativeCalcBudgetMs = originalBudget;
            AE2EnhancedConfig.crafting.specialCrafting = originalSpecial;
        }
    }

    @Test
    void stoppedScopeReadsLastSnapshotWithoutCreatingNewState() {
        ServerScope scope = new ServerScope();
        ServerConfig published = ServerConfig.current(scope);
        scope.stopAcceptingWork();

        assertThat(ServerConfig.current(scope)).isSameAs(published);
        assertThat(scope.serviceCount()).isEqualTo(1);
        scope.close();
        assertThat(ServerConfig.current(scope).version).isZero();
        assertThat(scope.serviceCount()).isZero();
    }

    @Test
    void independentServerScopesDoNotShareConfigVersions() {
        ServerScope first = new ServerScope();
        ServerScope second = new ServerScope();
        ServerConfig firstConfig = ServerConfig.current(first);
        ServerConfig secondConfig = ServerConfig.current(second);

        assertThat(firstConfig).isNotSameAs(secondConfig);
        assertThat(firstConfig.version).isEqualTo(1L);
        assertThat(secondConfig.version).isEqualTo(1L);
        first.close();
        second.close();
    }

    @Test
    void bootAndClientPreferencesAreFrozenOutsideServerConfig() {
        String originalSort = AE2EnhancedConfig.craftingDisplay.confirmSortMode;
        try {
            BootConfig boot = new BootConfig(true, false, true, false, true);
            ModProcessScope process = new ModProcessScope("ae2enhanced", "test", false, boot);
            AE2EnhancedConfig.craftingDisplay.confirmSortMode = "TOTAL_DESC";
            ClientPreferences preferences = new ClientPreferences(7L);
            ServerConfig server = new ServerConfig(3L);

            assertThat(process.bootConfig(BootConfig.class)).isSameAs(boot);
            assertThat(preferences.craftingDisplay.confirmSortMode).isEqualTo("TOTAL_DESC");
            assertThat(server.version).isEqualTo(3L);
            assertThat(server.crafting).isNotNull();
        } finally {
            AE2EnhancedConfig.craftingDisplay.confirmSortMode = originalSort;
        }
    }
}
