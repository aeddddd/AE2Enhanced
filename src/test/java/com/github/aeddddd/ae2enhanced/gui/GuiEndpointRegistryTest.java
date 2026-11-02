package com.github.aeddddd.ae2enhanced.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.Test;
import com.github.aeddddd.ae2enhanced.guiapi.GuiCatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GuiEndpointRegistryTest {
    @Test
    void legacyHandlerDelegatesToTheEndpointRegisteredForEachSide() {
        IGuiHandler previousServer = GuiEndpointRegistry.endpoint(Side.SERVER);
        IGuiHandler previousClient = GuiEndpointRegistry.endpoint(Side.CLIENT);
        IGuiHandler server = new StubEndpoint("server");
        IGuiHandler client = new StubEndpoint("client");
        GuiEndpointRegistry.register(Side.SERVER, server);
        GuiEndpointRegistry.register(Side.CLIENT, client);
        try {
            GuiHandler handler = new GuiHandler();
            EntityPlayer player = mock(EntityPlayer.class);
            World world = mock(World.class);
            assertThat(handler.getServerGuiElement(GuiCatalog.GUI_RING_CONFIG, player, world, 1, 2, 3))
                    .isEqualTo("server:33:1:2:3");
            assertThat(handler.getClientGuiElement(GuiCatalog.GUI_RING_CONFIG, player, world, 4, 5, 6))
                    .isEqualTo("client:33:4:5:6");
            assertThat(GuiEndpointRegistry.endpoint(Side.SERVER)).isSameAs(server);
            assertThat(GuiEndpointRegistry.endpoint(Side.CLIENT)).isSameAs(client);
        } finally {
            if (previousServer != null) {
                GuiEndpointRegistry.register(Side.SERVER, previousServer);
            }
            if (previousClient != null) {
                GuiEndpointRegistry.register(Side.CLIENT, previousClient);
            }
        }
    }

    private static final class StubEndpoint implements IGuiHandler {
        private final String name;

        private StubEndpoint(String name) {
            this.name = name;
        }

        @Override
        public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
            return name + ":" + id + ":" + x + ":" + y + ":" + z;
        }

        @Override
        public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
            return name + ":" + id + ":" + x + ":" + y + ":" + z;
        }
    }
}
