package com.github.aeddddd.ae2enhanced.build;

import net.minecraftforge.fart.relocated.net.minecraftforge.srgutils.IMappingFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ComposeOfficialSrg {
    public static void main(String[] args) throws Exception {
        IMappingFile officialToObf = IMappingFile.load(new File(args[0]));
        IMappingFile obfToSrg = IMappingFile.load(new File(args[1]));
        File out = new File(args[2]);

        Map<String, String> obfToOfficialClass = new HashMap<>();
        for (IMappingFile.IClass cls : officialToObf.getClasses()) {
            obfToOfficialClass.put(cls.getMapped(), cls.getOriginal());
        }

        Map<String, FieldLookup> fieldLookups = new HashMap<>();
        Map<String, MethodLookup> methodLookups = new HashMap<>();
        for (IMappingFile.IClass cls : officialToObf.getClasses()) {
            String officialClass = cls.getOriginal();
            FieldLookup fl = new FieldLookup();
            MethodLookup ml = new MethodLookup();
            for (IMappingFile.IField f : cls.getFields()) {
                fl.put(f.getMapped(), f.getOriginal());
            }
            for (IMappingFile.IMethod m : cls.getMethods()) {
                ml.put(m.getMappedDescriptor(), m.getMapped(), m.getOriginal());
            }
            fieldLookups.put(officialClass, fl);
            methodLookups.put(officialClass, ml);
        }

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(out.toPath()), "UTF-8"))) {
            pw.println("tsrg2 official srg");
            for (IMappingFile.IClass cls : obfToSrg.getClasses()) {
                String obfClass = cls.getOriginal();
                String officialClass = obfToOfficialClass.get(obfClass);
                if (officialClass == null) continue;
                pw.println(officialClass + " " + officialClass);
                FieldLookup fl = fieldLookups.get(officialClass);
                MethodLookup ml = methodLookups.get(officialClass);
                if (fl == null || ml == null) continue;
                for (IMappingFile.IField f : cls.getFields()) {
                    String obfName = f.getOriginal();
                    String srgName = f.getMapped();
                    String officialName = fl.getName(obfName);
                    if (officialName == null) continue;
                    pw.println("\t" + officialName + " " + srgName);
                }
                for (IMappingFile.IMethod m : cls.getMethods()) {
                    String obfDesc = m.getDescriptor();
                    String obfName = m.getOriginal();
                    String srgName = m.getMapped();
                    String officialDesc = remapDesc(obfDesc, obfToOfficialClass);
                    String officialName = ml.get(obfDesc, obfName);
                    if (officialName == null) continue;
                    pw.println("\t" + officialName + " " + officialDesc + " " + srgName);
                }
            }
        }
        System.out.println("[ComposeOfficialSrg] Wrote " + out);
    }

    static String remapDesc(String desc, Map<String, String> obfToOfficial) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < desc.length()) {
            char c = desc.charAt(i);
            if (c == 'L') {
                int end = desc.indexOf(';', i);
                if (end == -1) {
                    sb.append(desc.substring(i));
                    break;
                }
                String obf = desc.substring(i + 1, end);
                String official = obfToOfficial.getOrDefault(obf, obf);
                sb.append('L').append(official).append(';');
                i = end + 1;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    static class FieldLookup {
        Map<String, String> byName = new HashMap<>();

        void put(String obfName, String officialName) {
            byName.put(obfName, officialName);
        }

        String getName(String obfName) {
            return byName.get(obfName);
        }
    }

    static class MethodLookup {
        Map<String, Map<String, String>> byDesc = new HashMap<>();
        void put(String desc, String obfName, String officialName) {
            byDesc.computeIfAbsent(desc, k -> new HashMap<>()).put(obfName, officialName);
        }
        String get(String desc, String obfName) {
            Map<String, String> m = byDesc.get(desc);
            return m == null ? null : m.get(obfName);
        }
    }
}
