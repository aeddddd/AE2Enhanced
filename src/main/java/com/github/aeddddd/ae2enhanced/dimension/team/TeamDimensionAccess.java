package com.github.aeddddd.ae2enhanced.dimension.team;

import java.util.Optional;
import java.util.UUID;

/** 维度核心使用的 FTB 中性端口；实现仅存在于条件加载的 integration/ftb。 */
public final class TeamDimensionAccess {
    private static volatile Provider provider = new UnavailableProvider();

    private TeamDimensionAccess() {
    }

    public static void setProvider(Provider newProvider) {
        provider = newProvider == null ? new UnavailableProvider() : newProvider;
    }

    public static boolean isAvailable() {
        return provider.isAvailable();
    }

    public static Optional<TeamInfo> currentTeam(UUID playerId) {
        return provider.currentTeam(playerId);
    }

    public static boolean isMember(String teamId, UUID playerId) {
        return provider.isMember(teamId, playerId);
    }

    public static boolean canManage(String teamId, UUID playerId) {
        return provider.canManage(teamId, playerId);
    }

    public static boolean isBindingReady(String teamId) {
        return provider.isBindingReady(teamId);
    }

    public static boolean hasValidBinding(String teamId, String bindingId) {
        return provider.hasValidBinding(teamId, bindingId);
    }

    public static void bind(String teamId, String bindingId) {
        provider.bind(teamId, bindingId);
    }

    public interface Provider {
        boolean isAvailable();
        Optional<TeamInfo> currentTeam(UUID playerId);
        boolean isMember(String teamId, UUID playerId);
        boolean canManage(String teamId, UUID playerId);
        boolean isBindingReady(String teamId);
        boolean hasValidBinding(String teamId, String bindingId);
        void bind(String teamId, String bindingId);
    }

    private static final class UnavailableProvider implements Provider {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public Optional<TeamInfo> currentTeam(UUID playerId) {
            return Optional.empty();
        }

        @Override
        public boolean isMember(String teamId, UUID playerId) {
            return false;
        }

        @Override
        public boolean canManage(String teamId, UUID playerId) {
            return false;
        }

        @Override
        public boolean isBindingReady(String teamId) {
            return false;
        }

        @Override
        public boolean hasValidBinding(String teamId, String bindingId) {
            return false;
        }

        @Override
        public void bind(String teamId, String bindingId) {
            throw new IllegalStateException("FTB team integration is unavailable");
        }
    }
}
