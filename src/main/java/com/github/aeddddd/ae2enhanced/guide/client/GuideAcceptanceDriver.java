package com.github.aeddddd.ae2enhanced.guide.client;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.dimension.dev.PrivateDimensionHarness;
import com.github.aeddddd.ae2enhanced.guide.GuideBook;
import com.github.aeddddd.ae2enhanced.guide.GuideManager;
import com.github.aeddddd.ae2enhanced.guide.GuidePage;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** 隔离客户端指南同步验收；系统属性未开启时不注册。 */
public final class GuideAcceptanceDriver {
    private int ticks;
    private boolean done;

    public static void register() {
        if (PrivateDimensionHarness.enabled()) {
            MinecraftForge.EVENT_BUS.register(new GuideAcceptanceDriver());
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done || Minecraft.getMinecraft().world == null) {
            return;
        }
        ticks++;
        GuideBook book = GuideManager.getInstance().getBook();
        GuidePage external = book.getPage("mymod/external.md");
        GuidePage inline = book.getPage("mymod/inline.md");
        if (external != null && inline != null && book.getPageForItem(Items.DIAMOND) != null) {
            boolean navigation = book.getNavRoots().stream().anyMatch(node ->
                    node.pageId.equals("mymod/external.md") || node.pageId.equals("mymod/inline.md"));
            if (navigation && external.getTitle().equals("External Guide") && inline.getTitle().equals("Inline Guide")) {
                AE2Enhanced.LOGGER.info("GUIDE_CLIENT_PASS user={} external=true inline=true navigation=true itemIndex=true",
                        Minecraft.getMinecraft().player.getName());
                AE2Enhanced.LOGGER.info("GUIDE_CLIENT_DONE user={}", Minecraft.getMinecraft().player.getName());
                done = true;
                return;
            }
        }
        if (ticks > 1200) {
            AE2Enhanced.LOGGER.error("GUIDE_HARNESS_FAILED user={} external={} inline={} navigationRoots={}",
                    Minecraft.getMinecraft().player.getName(), external != null, inline != null,
                    book.getNavRoots().size());
            done = true;
        }
    }
}
