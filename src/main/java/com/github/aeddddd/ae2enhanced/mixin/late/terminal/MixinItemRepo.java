package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import appeng.api.config.ViewItems;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.me.ItemRepo;
import appeng.util.prioritylist.IPartitionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 优化 ItemRepo：
 * 1. addIAE() 空搜索快速路径：在方法 HEAD 注入,跳过 getDisplayName / Pattern.compile / 字符串匹配等过滤逻辑
 * 2. Pattern.compile 缓存：通过 Redirect 替换调用,同一 tick 内只编译一次正则
 * 3. updateView() 并行排序：用 Arrays.parallelSort 替代 ArrayList.sort,加速 50 万+ 物品的排序
 *
 * <p><b>关键兼容性说明</b>：不得使用 @Overwrite 重写 addIAE(),因为 jecharacters(拼音搜索模组)
 * 通过 ASM 修改了原生 addIAE() 中的 String.contains 调用,将其替换为支持拼音搜索的实现.
 * @Overwrite 会完全绕过 jecharacters 的 ASM 修改,导致终端中无法拼音搜索.
 */
@Mixin(value = ItemRepo.class, remap = false)
public class MixinItemRepo {

    @Shadow
    private String searchString;

    @Shadow
    private List<IAEItemStack> view;

    @Shadow
    private IPartitionList<IAEItemStack> myPartitionList;

    @Unique
    private Pattern ae2enhanced$cachedPattern;

    @Unique
    private String ae2enhanced$cachedPatternString;

    /**
     * 空搜索快速路径：当搜索框为空时,直接跳过所有过滤逻辑(getDisplayName、toLowerCase、
     * Pattern.compile、split/contains 遍历),只保留 viewMode / partitionList 的基础过滤.
     * 这是 50 万+ 物品场景下最显著的优化,因为大部分时候玩家不输入搜索词.
     */
    @Inject(method = "addIAE", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$fastPathEmptySearch(IAEItemStack is, Enum<?> viewMode, CallbackInfo ci) {
        if (this.searchString == null || this.searchString.isEmpty()) {
            if (this.myPartitionList != null && !this.myPartitionList.isListed(is)) {
                ci.cancel();
                return;
            }
            if (viewMode == ViewItems.CRAFTABLE && !is.isCraftable()) {
                ci.cancel();
                return;
            }
            if (viewMode == ViewItems.STORED && is.getStackSize() == 0L) {
                ci.cancel();
                return;
            }
            if (viewMode == ViewItems.CRAFTABLE) {
                is = is.copy();
                is.setStackSize(0L);
            }
            this.view.add(is);
            ci.cancel();
        }
    }

    /**
     * Pattern.compile 缓存：通过 Redirect 替换原生的 Pattern.compile 调用.
     * 当 innerSearch 不变时,直接返回缓存的 Pattern 实例,避免同一 tick 内重复编译正则.
     */
    @Redirect(
            method = "addIAE",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/regex/Pattern;compile(Ljava/lang/String;I)Ljava/util/regex/Pattern;"
            )
    )
    private Pattern ae2enhanced$cachedPatternCompile(String regex, int flags) {
        if (ae2enhanced$cachedPattern != null && regex.equals(ae2enhanced$cachedPatternString)) {
            return ae2enhanced$cachedPattern;
        }
        Pattern p = Pattern.compile(regex, flags);
        ae2enhanced$cachedPatternString = regex;
        ae2enhanced$cachedPattern = p;
        return p;
    }

    /**
     * 用并行排序替代 ArrayList.sort,加速 50 万+ 物品的排序.
     * view 在 updateView() 中会被完全替换,因此使用 Arrays.asList 包装排序后的数组是安全的.
     */
    @Redirect(
            method = "updateView",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"
            )
    )
    private void ae2enhanced$parallelSort(List<IAEItemStack> list, Comparator<IAEItemStack> c) {
        IAEItemStack[] array = list.toArray(new IAEItemStack[0]);
        Arrays.parallelSort(array, c);
        this.view = Arrays.asList(array);
    }
}
