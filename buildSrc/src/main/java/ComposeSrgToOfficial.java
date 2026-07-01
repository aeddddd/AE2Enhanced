import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 将 ComposeOfficialSrg 生成的 official->srg TSRG2 反转为 srg->official。
 * 由于原文件中的字段行可能不带描述符，IMappingFile 可能丢弃这些行，
 * 因此这里手动解析并反转，保留所有字段/方法。
 */
public class ComposeSrgToOfficial {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: ComposeSrgToOfficial <officialToSrg.tsrg> <output.tsrg>");
            System.exit(1);
        }
        File input = new File(args[0]);
        File output = new File(args[1]);

        List<String> lines = Files.readAllLines(input.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            throw new IllegalStateException("Empty mapping file: " + input);
        }
        String header = lines.get(0).trim();
        if (!header.startsWith("tsrg2")) {
            throw new IllegalStateException("Not a TSRG2 file: " + header);
        }

        Map<String, RevClass> classes = new LinkedHashMap<>();
        RevClass current = null;
        for (int i = 1; i < lines.size(); i++) {
            String raw = lines.get(i);
            if (raw.isEmpty()) continue;
            String line = raw.replaceFirst("^\\t", "");
            boolean isMember = raw.startsWith("\t");
            if (!isMember) {
                String[] parts = line.split(" ");
                if (parts.length != 2) continue;
                current = new RevClass(parts[0], parts[1]);
                classes.put(parts[0], current);
            } else if (current != null) {
                String[] parts = line.split(" ");
                if (parts.length == 2) {
                    // field without descriptor: official srg
                    current.fields.add(new RevField(null, parts[0], parts[1]));
                } else if (parts.length == 3) {
                    if (parts[1].startsWith("(")) {
                        // method: official desc srg
                        current.methods.add(new RevMethod(parts[0], parts[1], parts[2]));
                    } else {
                        // field with descriptor: desc official srg
                        current.fields.add(new RevField(parts[0], parts[1], parts[2]));
                    }
                }
            }
        }

        output.getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(output.toPath()), "UTF-8"))) {
            pw.println("tsrg2 srg official");
            for (RevClass cls : classes.values()) {
                pw.println(cls.srg + " " + cls.official);
                for (RevField f : cls.fields) {
                    if (f.descriptor != null) {
                        pw.println("\t" + f.descriptor + " " + f.srg + " " + f.official);
                    } else {
                        pw.println("\t" + f.srg + " " + f.official);
                    }
                }
                for (RevMethod m : cls.methods) {
                    pw.println("\t" + m.srg + " " + m.descriptor + " " + m.official);
                }
            }
        }
        System.out.println("[ComposeSrgToOfficial] Wrote " + output);
    }

    static class RevClass {
        final String official;
        final String srg;
        final List<RevField> fields = new ArrayList<>();
        final List<RevMethod> methods = new ArrayList<>();

        RevClass(String official, String srg) {
            this.official = official;
            this.srg = srg;
        }
    }

    static class RevField {
        final String descriptor;
        final String official;
        final String srg;

        RevField(String descriptor, String official, String srg) {
            this.descriptor = descriptor;
            this.official = official;
            this.srg = srg;
        }
    }

    static class RevMethod {
        final String official;
        final String descriptor;
        final String srg;

        RevMethod(String official, String descriptor, String srg) {
            this.official = official;
            this.descriptor = descriptor;
            this.srg = srg;
        }
    }
}
