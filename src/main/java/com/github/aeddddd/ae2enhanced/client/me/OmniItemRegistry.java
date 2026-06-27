package com.github.aeddddd.ae2enhanced.client.me;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Omni Terminal 客户端物品注册表。
 *
 * <p>维护服务端分配的 int ID 与本地 {@link IAEItemStack} 副本 + 数量的映射。
 * 同时缓存物品的显示名称分词结果和 modId，避免终端刷新时重复计算。
 * 生命周期与 Omni Terminal GUI Session 绑定，关闭 GUI 后由 GC 回收。
 */
public class OmniItemRegistry {

    private final Int2ObjectMap<IAEItemStack> idToStack = new Int2ObjectOpenHashMap<>();
    private final Int2LongMap idToCount = new Int2LongOpenHashMap();
    private final Int2ObjectMap<String[]> idToNameWords = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<String> idToModId = new Int2ObjectOpenHashMap<>();
    private final Object2IntOpenHashMap<IAEItemStack> stackToId = new Object2IntOpenHashMap<>();

    public OmniItemRegistry() {
        this.stackToId.defaultReturnValue(-1);
    }

    public void register(int id, IAEItemStack stack, long count) {
        IAEItemStack copy = stack.copy();
        copy.setStackSize(count);
        this.idToStack.put(id, copy);
        this.idToCount.put(id, count);
        this.stackToId.put(copy, id);

        // 预分词并缓存
        String name = Platform.getItemDisplayName(stack).toLowerCase();
        String modId = Platform.getModId(stack).toLowerCase();
        this.idToNameWords.put(id, splitWords(name).toArray(new String[0]));
        this.idToModId.put(id, modId);
    }

    public void updateCount(int id, long count) {
        this.idToCount.put(id, count);
        IAEItemStack stack = this.idToStack.get(id);
        if (stack != null) {
            stack.setStackSize(count);
        }
    }

    public IAEItemStack getStack(int id) {
        return this.idToStack.get(id);
    }

    public long getCount(int id) {
        return this.idToCount.get(id);
    }

    public String[] getNameWords(int id) {
        return this.idToNameWords.get(id);
    }

    public String getModId(int id) {
        return this.idToModId.get(id);
    }

    public int getId(IAEItemStack stack) {
        return this.stackToId.getInt(stack);
    }

    public void clear() {
        this.idToStack.clear();
        this.idToCount.clear();
        this.idToNameWords.clear();
        this.idToModId.clear();
        this.stackToId.clear();
    }

    public Collection<IAEItemStack> getAllStacks() {
        return this.idToStack.values();
    }

    /**
     * 将物品名称分词，支持：空格分隔、下划线分隔、驼峰拆分、中文单字
     */
    private static List<String> splitWords(String input) {
        List<String> words = new ArrayList<>();
        if (input == null || input.isEmpty()) return words;

        // 分离中文和非中文部分
        StringBuilder asciiPart = new StringBuilder();
        StringBuilder cnPart = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (isChinese(c)) {
                if (asciiPart.length() > 0) {
                    processAsciiPart(asciiPart.toString(), words);
                    asciiPart.setLength(0);
                }
                cnPart.append(c);
            } else {
                if (cnPart.length() > 0) {
                    processCnPart(cnPart.toString(), words);
                    cnPart.setLength(0);
                }
                asciiPart.append(c);
            }
        }

        if (asciiPart.length() > 0) {
            processAsciiPart(asciiPart.toString(), words);
        }
        if (cnPart.length() > 0) {
            processCnPart(cnPart.toString(), words);
        }

        return words;
    }

    private static boolean isChinese(char c) {
        return (c >= '\u4e00' && c <= '\u9fa5')
            || (c >= '\u3400' && c <= '\u4dbf')
            || (c >= '\u2e80' && c <= '\u2eff');
    }

    private static void processAsciiPart(String input, List<String> words) {
        String[] tokens = input.split("[^a-zA-Z0-9]+");
        for (String token : tokens) {
            if (token.length() <= 1) continue;
            String lower = token.toLowerCase();
            words.add(lower);

            // 驼峰拆分：IronSword → iron, sword
            if (token.length() > 2) {
                StringBuilder current = new StringBuilder();
                current.append(token.charAt(0));
                for (int i = 1; i < token.length(); i++) {
                    char c = token.charAt(i);
                    if (Character.isUpperCase(c)) {
                        String word = current.toString().toLowerCase();
                        if (word.length() > 1) {
                            words.add(word);
                        }
                        current = new StringBuilder();
                    }
                    current.append(Character.toLowerCase(c));
                }
                String last = current.toString().toLowerCase();
                if (last.length() > 1 && !last.equals(lower)) {
                    words.add(last);
                }
            }
        }
    }

    private static void processCnPart(String input, List<String> words) {
        if (input.length() <= 1) return;
        words.add(input); // 完整中文词
        // 每个字符也作为词（支持单字搜索）
        for (int i = 0; i < input.length(); i++) {
            words.add(String.valueOf(input.charAt(i)));
        }
    }
}
