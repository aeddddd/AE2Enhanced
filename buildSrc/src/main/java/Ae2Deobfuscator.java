import com.google.gson.*;
import net.minecraftforge.fart.relocated.net.minecraftforge.srgutils.IMappingFile;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.*;

/**
 * 使用 srg->official 映射反混淆 AE2 jar：
 * 1. 将类字节码中的 SRG 字段/方法引用替换为官方名。
 * 2. 将 ae2.mixins.refmap.json 中的 SRG 目标引用替换为官方名。
 * 3. 额外修正 lambda 的 invokedynamic 方法名（ClassRemapper 不会改 call-site 名）。
 * 4. 重命名类定义中的桥接/覆写方法，使其与父类官方名一致。
 */
public class Ae2Deobfuscator {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: Ae2Deobfuscator <inputAe2Jar> <srgToOfficial.tsrg> <outputJar>");
            System.exit(1);
        }
        File inputJar = new File(args[0]);
        File mappingFile = new File(args[1]);
        File outputJar = new File(args[2]);

        IMappingFile map = IMappingFile.load(mappingFile);
        Map<String, ClassMapping> mappings = new HashMap<>();
        Map<String, ClassMapping> officialClassMappings = new HashMap<>();
        for (IMappingFile.IClass cls : map.getClasses()) {
            ClassMapping cm = new ClassMapping();
            for (IMappingFile.IField f : cls.getFields()) {
                String desc = f.getDescriptor();
                if (desc != null) {
                    cm.fields.put(f.getOriginal() + " " + desc, f.getMapped());
                    cm.fieldsByName.put(f.getOriginal(), f.getMapped());
                } else {
                    cm.fields.put(f.getOriginal(), f.getMapped());
                    cm.fieldsByName.put(f.getOriginal(), f.getMapped());
                }
            }
            for (IMappingFile.IMethod m : cls.getMethods()) {
                cm.methods.put(m.getOriginal() + " " + m.getDescriptor(), m.getMapped());
                cm.methodsByName.put(m.getOriginal(), m.getMapped());
            }
            mappings.put(cls.getOriginal(), cm);
            officialClassMappings.put(cls.getMapped(), cm);
        }

        Ae2Remapper remapper = new Ae2Remapper(mappings, officialClassMappings);

        outputJar.getParentFile().mkdirs();
        try (JarInputStream jin = new JarInputStream(new FileInputStream(inputJar));
             JarOutputStream jout = new JarOutputStream(new FileOutputStream(outputJar))) {
            JarEntry entry;
            while ((entry = jin.getNextJarEntry()) != null) {
                byte[] data = readAll(jin);
                String name = entry.getName();
                if ("ae2.mixins.refmap.json".equals(name)) {
                    data = remapRefmap(data, mappings);
                } else if (name.endsWith(".class") && name.startsWith("appeng/")) {
                    ClassReader cr = new ClassReader(data);
                    ClassWriter cw = new ClassWriter(0);
                    PostRemapFixer fixer = new PostRemapFixer(cw, remapper);
                    cr.accept(new AnnotationAwareRemapper(fixer, remapper, name.substring(0, name.length() - 6)), ClassReader.EXPAND_FRAMES);
                    data = cw.toByteArray();
                }
                JarEntry outEntry = new JarEntry(name);
                outEntry.setTime(entry.getTime());
                jout.putNextEntry(outEntry);
                jout.write(data);
                jout.closeEntry();
            }
        }
        System.out.println("[Ae2Deobfuscator] Wrote " + outputJar);
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

    private static byte[] remapRefmap(byte[] data, Map<String, ClassMapping> mappings) {
        JsonObject root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject section = root.getAsJsonObject("mappings");
        if (section != null) remapRefmapSection(section, mappings);
        JsonObject dataObj = root.getAsJsonObject("data");
        if (dataObj != null) {
            JsonObject searge = dataObj.getAsJsonObject("searge");
            if (searge != null) remapRefmapSection(searge, mappings);
        }
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                .toJson(root).getBytes(StandardCharsets.UTF_8);
    }

    private static void remapRefmapSection(JsonObject section, Map<String, ClassMapping> mappings) {
        for (Map.Entry<String, JsonElement> mixinEntry : section.entrySet()) {
            if (!mixinEntry.getValue().isJsonObject()) continue;
            JsonObject mixinMap = mixinEntry.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> refEntry : mixinMap.entrySet()) {
                JsonElement value = refEntry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    String original = value.getAsString();
                    String fixed = fixReference(original, mappings);
                    if (!fixed.equals(original)) {
                        refEntry.setValue(new JsonPrimitive(fixed));
                    }
                }
            }
        }
    }

    private static String fixReference(String ref, Map<String, ClassMapping> mappings) {
        if (!ref.startsWith("L")) return ref;
        int semi = ref.indexOf(';');
        if (semi <= 0) return ref;
        String owner = ref.substring(1, semi);
        String tail = ref.substring(semi + 1);
        ClassMapping cm = mappings.get(owner);
        if (cm == null) return ref;

        int paren = tail.indexOf('(');
        if (paren >= 0) {
            String srgName = tail.substring(0, paren);
            String desc = tail.substring(paren);
            String official = cm.methods.get(srgName + " " + desc);
            if (official != null) {
                return "L" + owner + ";" + official + desc;
            }
        } else {
            int colon = tail.indexOf(':');
            String srgName = colon >= 0 ? tail.substring(0, colon) : tail;
            String desc = colon >= 0 ? tail.substring(colon) : "";
            String official = cm.fields.get(srgName + " " + desc);
            if (official != null) {
                return "L" + owner + ";" + official + desc;
            }
            official = cm.fieldsByName.get(srgName);
            if (official != null) {
                return "L" + owner + ";" + official + desc;
            }
        }
        return ref;
    }

    static class ClassMapping {
        final Map<String, String> fields = new HashMap<>();
        final Map<String, String> fieldsByName = new HashMap<>();
        final Map<String, String> methods = new HashMap<>();
        final Map<String, String> methodsByName = new HashMap<>();
    }

    static class Ae2Remapper extends Remapper {
        final Map<String, ClassMapping> mappings;
        final Map<String, ClassMapping> officialClassMappings;
        final Map<String, String> globalFields = new HashMap<>();
        final Map<String, String> globalFieldsByName = new HashMap<>();
        final Map<String, String> globalMethods = new HashMap<>();

        Ae2Remapper(Map<String, ClassMapping> mappings, Map<String, ClassMapping> officialClassMappings) {
            this.mappings = mappings;
            this.officialClassMappings = officialClassMappings;
            for (ClassMapping cm : mappings.values()) {
                for (Map.Entry<String, String> e : cm.fields.entrySet()) {
                    globalFields.put(e.getKey(), e.getValue());
                }
                for (Map.Entry<String, String> e : cm.fieldsByName.entrySet()) {
                    globalFieldsByName.put(e.getKey(), e.getValue());
                }
                for (Map.Entry<String, String> e : cm.methods.entrySet()) {
                    globalMethods.put(e.getKey(), e.getValue());
                }
            }
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            ClassMapping cm = mappings.get(owner);
            if (cm != null) {
                String mapped = cm.fields.get(name + " " + descriptor);
                if (mapped != null) return mapped;
                mapped = cm.fieldsByName.get(name);
                if (mapped != null) return mapped;
            }
            String mapped = globalFields.get(name + " " + descriptor);
            if (mapped != null) return mapped;
            mapped = globalFieldsByName.get(name);
            return mapped != null ? mapped : name;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            ClassMapping cm = mappings.get(owner);
            if (cm != null) {
                String mapped = cm.methods.get(name + " " + descriptor);
                if (mapped != null) return mapped;
            }
            String mapped = globalMethods.get(name + " " + descriptor);
            return mapped != null ? mapped : name;
        }

        String mapInvokeDynamicName(String name, String descriptor) {
            if (name == null || name.isEmpty()) return name;
            if (!name.startsWith("m_") && !name.startsWith("func_") && !name.startsWith("f_")) {
                return name;
            }
            Type returnType = Type.getReturnType(descriptor);
            if (returnType.getSort() == Type.OBJECT) {
                String cls = returnType.getInternalName();
                ClassMapping cm = officialClassMappings.get(cls);
                if (cm != null) {
                    String mapped = cm.methodsByName.get(name);
                    if (mapped != null) return mapped;
                }
            }
            // 如果返回类型不是接口/没有命中，尝试全局方法名（按描述符精确匹配）
            Type methodType = Type.getMethodType(descriptor);
            String desc = methodType.getDescriptor();
            String mapped = globalMethods.get(name + " " + desc);
            if (mapped != null) return mapped;
            return name;
        }
    }

    /**
     * 在 ClassRemapper 之后做二次修正：
     * 1. 把类定义中的方法/字段名改为官方名（处理桥接方法、被覆写的方法）。
     * 2. 把 invokedynamic 的 call-site 方法名改为官方名（lambda 的 SAM）。
     */
    static class PostRemapFixer extends ClassVisitor {
        final Ae2Remapper remapper;
        String className;

        PostRemapFixer(ClassVisitor cv, Ae2Remapper remapper) {
            super(Opcodes.ASM9, cv);
            this.remapper = remapper;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            String mapped = remapper.mapFieldName(className, name, descriptor);
            return super.visitField(access, mapped, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            String mapped = name;
            if (!"<init>".equals(name) && !"<clinit>".equals(name)) {
                mapped = remapper.mapMethodName(className, name, descriptor);
            }
            MethodVisitor mv = super.visitMethod(access, mapped, descriptor, signature, exceptions);
            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                public void visitInvokeDynamicInsn(String indyName, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                    String newName = remapper.mapInvokeDynamicName(indyName, descriptor);
                    super.visitInvokeDynamicInsn(newName, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
                }
            };
        }
    }

    /**
     * 在 ClassRemapper 的基础上，对当前 AE2 mixin 类中可能出现 SRG 名的字符串注解值做二次映射。
     */
    static class AnnotationAwareRemapper extends ClassRemapper {
        final String className;
        final Ae2Remapper ae2Remapper;

        AnnotationAwareRemapper(ClassVisitor classVisitor, Ae2Remapper remapper, String className) {
            super(classVisitor, remapper);
            this.className = className;
            this.ae2Remapper = remapper;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            AnnotationValueRemapper.currentClass = this.className;
            super.visit(version, access, name, signature, superName, interfaces);
            AnnotationValueRemapper.currentClass = null;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            AnnotationVisitor av = super.visitAnnotation(descriptor, visible);
            return new AnnotationValueRemapper(av, descriptor, className, ae2Remapper);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            FieldVisitor fv = super.visitField(access, name, descriptor, signature, value);
            return new FieldVisitor(Opcodes.ASM9, fv) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    AnnotationVisitor av = super.visitAnnotation(descriptor, visible);
                    return new AnnotationValueRemapper(av, descriptor, className, ae2Remapper);
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    AnnotationVisitor av = super.visitAnnotation(descriptor, visible);
                    return new AnnotationValueRemapper(av, descriptor, className, ae2Remapper);
                }
            };
        }
    }

    static class AnnotationValueRemapper extends AnnotationVisitor {
        static String currentClass;
        final String annotationDesc;
        final String className;
        final Ae2Remapper remapper;

        AnnotationValueRemapper(AnnotationVisitor av, String annotationDesc, String className, Ae2Remapper remapper) {
            super(Opcodes.ASM9, av);
            this.annotationDesc = annotationDesc;
            this.className = className;
            this.remapper = remapper;
        }

        @Override
        public void visit(String name, Object value) {
            if (value instanceof String) {
                String s = (String) value;
                if ((s.startsWith("f_") || s.startsWith("m_") || s.startsWith("func_")) && className != null) {
                    ClassMapping cm = remapper.mappings.get(className);
                    if (cm != null) {
                        String mapped = cm.fieldsByName.get(s);
                        if (mapped == null) mapped = cm.methodsByName.get(s);
                        if (mapped != null) {
                            super.visit(name, mapped);
                            return;
                        }
                    }
                }
            }
            super.visit(name, value);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            AnnotationVisitor av = super.visitAnnotation(name, descriptor);
            return new AnnotationValueRemapper(av, descriptor, className, remapper);
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            AnnotationVisitor av = super.visitArray(name);
            return new AnnotationValueRemapper(av, annotationDesc, className, remapper);
        }
    }
}
