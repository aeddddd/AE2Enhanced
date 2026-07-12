package com.github.aeddddd.ae2enhanced.build;

import com.google.gson.*;
import net.minecraftforge.fart.relocated.net.minecraftforge.srgutils.IMappingFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.*;

/**
 * 将 AE2 的 mixin refmap 中的 SRG 方法/字段名替换为官方名，
 * 以便在反混淆后的开发环境中使用。
 */
public class FixAe2Refmap {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: FixAe2Refmap <inputJar> <srgToOfficialTsrg> <outputJar>");
            System.exit(1);
        }
        File inputJar = new File(args[0]);
        File mappingFile = new File(args[1]);
        File outputJar = new File(args[2]);

        IMappingFile map = IMappingFile.load(mappingFile);
        Map<String, IMappingFile.IClass> classes = new HashMap<>();
        for (IMappingFile.IClass cls : map.getClasses()) {
            classes.put(cls.getOriginal(), cls);
        }

        outputJar.getParentFile().mkdirs();
        try (JarInputStream jin = new JarInputStream(new FileInputStream(inputJar));
             JarOutputStream jout = new JarOutputStream(new FileOutputStream(outputJar))) {
            JarEntry entry;
            while ((entry = jin.getNextJarEntry()) != null) {
                byte[] data = readAll(jin);
                if ("ae2.mixins.refmap.json".equals(entry.getName())) {
                    data = transformRefmap(data, classes);
                }
                JarEntry outEntry = new JarEntry(entry.getName());
                outEntry.setTime(entry.getTime());
                jout.putNextEntry(outEntry);
                jout.write(data);
                jout.closeEntry();
            }
        }
        System.out.println("[FixAe2Refmap] Wrote " + outputJar);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private static byte[] transformRefmap(byte[] data, Map<String, IMappingFile.IClass> classes) {
        JsonObject root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject mappings = root.getAsJsonObject("mappings");
        if (mappings != null) {
            transformSection(mappings, classes);
        }
        JsonObject dataObj = root.getAsJsonObject("data");
        if (dataObj != null) {
            JsonObject searge = dataObj.getAsJsonObject("searge");
            if (searge != null) {
                transformSection(searge, classes);
            }
        }
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                .toJson(root).getBytes(StandardCharsets.UTF_8);
    }

    private static void transformSection(JsonObject section, Map<String, IMappingFile.IClass> classes) {
        for (Map.Entry<String, JsonElement> mixinEntry : section.entrySet()) {
            if (!mixinEntry.getValue().isJsonObject()) continue;
            JsonObject mixinMap = mixinEntry.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> refEntry : mixinMap.entrySet()) {
                JsonElement value = refEntry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    String original = value.getAsString();
                    String fixed = fixReference(original, classes);
                    if (!fixed.equals(original)) {
                        refEntry.setValue(new JsonPrimitive(fixed));
                    }
                }
            }
        }
    }

    private static String fixReference(String ref, Map<String, IMappingFile.IClass> classes) {
        // Method/field references always start with 'L' in refmaps.
        if (!ref.startsWith("L")) return ref;
        int semi = ref.indexOf(';');
        if (semi <= 0) return ref;
        String owner = ref.substring(1, semi);
        String tail = ref.substring(semi + 1);
        IMappingFile.IClass cls = classes.get(owner);
        if (cls == null) return ref;

        int paren = tail.indexOf('(');
        if (paren >= 0) {
            String srgName = tail.substring(0, paren);
            String desc = tail.substring(paren);
            for (IMappingFile.IMethod m : cls.getMethods()) {
                if (m.getOriginal().equals(srgName) && m.getDescriptor().equals(desc)) {
                    return "L" + owner + ";" + m.getMapped() + desc;
                }
            }
        } else {
            int colon = tail.indexOf(':');
            String srgName = colon >= 0 ? tail.substring(0, colon) : tail;
            String desc = colon >= 0 ? tail.substring(colon) : "";
            for (IMappingFile.IField f : cls.getFields()) {
                if (f.getOriginal().equals(srgName)) {
                    return "L" + owner + ";" + f.getMapped() + desc;
                }
            }
        }
        return ref;
    }
}
