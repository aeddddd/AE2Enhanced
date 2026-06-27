package com.github.aeddddd.ae2enhanced.mixin.late.projecte;

import com.github.aeddddd.ae2enhanced.client.util.BigEmcFormatter;
import com.github.aeddddd.ae2enhanced.integration.projecte.ProjectEBigEmcHelper;
import moze_intel.projecte.api.ProjectEAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.text.TextFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 原版 GuiIngame 的 scoreboard sidebar 渲染只能显示 int 分数，
 * ProjectE 的 EMC 记分板因此无法显示超过 long/int 上限的真实余额。
 * 当检测到 projecte:emc_score 目标时，改为读取玩家 KnowledgeProvider 的 BigInteger EMC 并绘制。
 */
@Mixin(value = GuiIngame.class)
public class MixinGuiIngame {

    private static final String EMC_SCORE_CRITERIA = "projecte:emc_score";

    @Inject(method = "renderScoreboard", at = @At("HEAD"), cancellable = true)
    private void ae2e$renderBigEmcScoreboard(ScoreObjective objective, ScaledResolution resolution, CallbackInfo ci) {
        if (objective == null) {
            return;
        }
        if (objective.getCriteria() == null || !EMC_SCORE_CRITERIA.equals(objective.getCriteria().getName())) {
            return;
        }
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            return;
        }
        Object provider = player.getCapability(ProjectEAPI.KNOWLEDGE_CAPABILITY, null);
        BigInteger emc = provider != null ? ProjectEBigEmcHelper.getEmcBig(provider) : BigInteger.ZERO;
        renderProjectEScoreboard(objective, resolution, player.getName(), emc);
        ci.cancel();
    }

    private void renderProjectEScoreboard(ScoreObjective objective, ScaledResolution resolution,
                                          String localPlayerName, BigInteger emc) {
        Scoreboard scoreboard = objective.getScoreboard();
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        @SuppressWarnings("unchecked")
        Collection<Score> rawScores = scoreboard.getSortedScores(objective);
        List<Score> scores = new ArrayList<>();
        for (Score score : rawScores) {
            if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) {
                scores.add(score);
            }
        }
        if (scores.size() > 15) {
            scores = scores.subList(scores.size() - 15, scores.size());
        }

        String title = objective.getDisplayName();
        int maxWidth = fr.getStringWidth(title);
        for (Score score : scores) {
            String name = ScorePlayerTeam.formatPlayerName(scoreboard.getPlayersTeam(score.getPlayerName()), score.getPlayerName());
            String value = formatScore(score, localPlayerName, emc);
            String line = name + ": " + TextFormatting.RED + value;
            maxWidth = Math.max(maxWidth, fr.getStringWidth(line));
        }

        int lineCount = scores.size();
        int totalHeight = lineCount * fr.FONT_HEIGHT;
        int yBase = resolution.getScaledHeight() / 2 + totalHeight / 3;
        int xLeft = resolution.getScaledWidth() - maxWidth - 3;
        int xRight = resolution.getScaledWidth() - 3 + 2;

        int drawn = 0;
        for (Score score : scores) {
            drawn++;
            String name = ScorePlayerTeam.formatPlayerName(scoreboard.getPlayersTeam(score.getPlayerName()), score.getPlayerName());
            String value = formatScore(score, localPlayerName, emc);
            String valueColored = TextFormatting.RED + value;
            int y = yBase - drawn * fr.FONT_HEIGHT;
            GuiIngame.drawRect(xLeft - 2, y, xRight, y + fr.FONT_HEIGHT, 0x50000000);
            fr.drawString(name, xLeft, y, 0x20FFFFFF);
            fr.drawString(valueColored, xRight - fr.getStringWidth(valueColored), y, 0x20FFFFFF);

            if (drawn == lineCount) {
                int titleY = y - fr.FONT_HEIGHT - 1;
                GuiIngame.drawRect(xLeft - 2, titleY, xRight, titleY + fr.FONT_HEIGHT + 1, 0x60000000);
                GuiIngame.drawRect(xLeft - 2, y - 1, xRight, y, 0x50000000);
                fr.drawString(title, xLeft + maxWidth / 2 - fr.getStringWidth(title) / 2, titleY + 1, 0x20FFFFFF);
            }
        }
    }

    private String formatScore(Score score, String localPlayerName, BigInteger emc) {
        if (score.getPlayerName().equals(localPlayerName)) {
            return BigEmcFormatter.format(emc);
        }
        return String.valueOf(score.getScorePoints());
    }
}
