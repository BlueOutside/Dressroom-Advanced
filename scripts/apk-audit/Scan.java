package dev.denver.dressroomadvanced;

import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.io.BufferedInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

/** Offline declaration and call-site checks. Never loads APK code into a JVM. */
public final class Scan {
    private record Rule(String id, String scope, String kind, String owner,
                        String name, String proto, String evidence, Set<String> hits) {
        boolean method(String cls, String method, String descriptor) {
            return matches(owner, cls) && matches(name, method) && matches(proto, descriptor);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("rules.tsv scope apk output.tsv");
        List<Rule> rules = new ArrayList<>();
        Set<String> anchors = new LinkedHashSet<>();
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] p = line.split("\t", -1);
            if (p.length != 7) throw new IllegalArgumentException("Invalid rule: " + line);
            if (!List.of(p[1].split(",")).contains(args[1])) continue;
            rules.add(new Rule(p[0], p[1], p[2], p[3], p[4], p[5], p[6], new LinkedHashSet<>()));
            if (p[2].equals("anchor")) anchors.addAll(List.of(p[6].split("\\|", -1)));
        }
        File apk = new File(args[2]);
        try (ZipFile zip = new ZipFile(apk)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().matches("classes[0-9]*\\.dex")) continue;
                try (var in = new BufferedInputStream(zip.getInputStream(entry))) {
                    var dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), in);
                    for (var cls : dex.getClasses()) {
                        List<Rule> selected = new ArrayList<>();
                        for (Rule rule : rules) {
                            if (rule.kind.equals("anchor") || rule.kind.equals("library")
                                    || !matches(rule.owner, cls.getType())) continue;
                            if (rule.kind.equals("class")) rule.hits.add(cls.getType());
                            else selected.add(rule);
                        }
                        if (selected.isEmpty()) continue;
                        for (Method method : cls.getMethods()) {
                            String proto = proto(method);
                            for (Rule rule : selected) {
                                if (!rule.method(cls.getType(), method.getName(), proto)) continue;
                                if (rule.kind.equals("static-method") && (method.getAccessFlags() & 8) == 0)
                                    continue;
                                if (rule.kind.equals("call") && !calls(method, rule.evidence)) continue;
                                rule.hits.add(cls.getType() + "->" + method.getName() + proto);
                            }
                        }
                    }
                }
            }
            for (Rule rule : rules)
                if (rule.kind.equals("library"))
                    zip.stream().map(java.util.zip.ZipEntry::getName)
                            .filter(name -> name.startsWith("lib/") && name.endsWith("/" + rule.evidence))
                            .forEach(rule.hits::add);
        }
        if (!anchors.isEmpty()) {
            for (var body : DexSignatures.read(apk, anchors)) {
                String proto = "(" + String.join("", body.parameters()) + ")" + body.result();
                for (Rule rule : rules)
                    if (rule.kind.equals("anchor") && rule.method(body.owner(), body.name(), proto)
                            && body.anchors().keySet().containsAll(List.of(rule.evidence.split("\\|", -1))))
                        rule.hits.add(body.owner() + "->" + body.name() + proto);
            }
        }
        List<String> out = new ArrayList<>();
        for (Rule rule : rules)
            out.add(rule.id + "\t" + rule.hits.size() + "\t" + String.join("; ", rule.hits));
        Files.write(Path.of(args[3]), out);
    }

    private static String proto(MethodReference method) {
        return "(" + String.join("", method.getParameterTypes()) + ")" + method.getReturnType();
    }

    private static boolean calls(Method method, String evidence) {
        var implementation = method.getImplementation();
        if (implementation == null) return false;
        for (var instruction : implementation.getInstructions())
            if (instruction instanceof ReferenceInstruction reference
                    && reference.getReference() instanceof MethodReference invoked
                    && matches(evidence, invoked.getDefiningClass() + "->" + invoked.getName() + proto(invoked)))
                return true;
        return false;
    }

    private static boolean matches(String pattern, String value) {
        if (pattern.equals("*")) return true;
        if (pattern.endsWith("*")) return value.startsWith(pattern.substring(0, pattern.length() - 1));
        return pattern.equals(value);
    }
}
