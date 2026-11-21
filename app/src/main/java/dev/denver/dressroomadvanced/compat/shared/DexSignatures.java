package dev.denver.dressroomadvanced;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

/** Reads only the code needed for anchored bindings. Nothing runs in a draw hook. */
final class DexSignatures {
    private static final int MAX_DEX = 128 * 1024 * 1024;

    record Member(String owner, String name, String type) {
        Field field(ClassLoader loader) throws ReflectiveOperationException {
            Field field = load(owner, loader).getDeclaredField(name);
            if (!descriptor(field.getType()).equals(type)
                    || java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                throw new NoSuchFieldException("DEX field contract changed: " + this);
            field.setAccessible(true);
            return field;
        }
    }

    interface Access {
        int offset();

        Member field();
    }

    record Read(int offset, Member field) implements Access {}

    record Write(int offset, Member field) implements Access {}

    record Call(
            int offset,
            String owner,
            String name,
            List<String> parameters,
            String result,
            boolean isStatic) {
        Method method(ClassLoader loader) throws ReflectiveOperationException {
            Class<?>[] args = new Class<?>[parameters.size()];
            for (int i = 0; i < args.length; i++) args[i] = load(parameters.get(i), loader);
            Method method = load(owner, loader).getDeclaredMethod(name, args);
            if (!descriptor(method.getReturnType()).equals(result)
                    || java.lang.reflect.Modifier.isStatic(method.getModifiers()) != isStatic)
                throw new NoSuchMethodException("DEX call contract changed: " + owner);
            method.setAccessible(true);
            return method;
        }
    }

    record Body(
            String owner,
            String name,
            List<String> parameters,
            String result,
            int access,
            Map<String, Integer> anchors,
            List<Read> reads,
            List<Write> writes,
            List<Call> calls) {
        boolean matches(String... required) {
            if ((access & 8) != 0 || !parameters.isEmpty() || !result.equals("V")) return false;
            for (String anchor : required) if (!anchors.containsKey(anchor)) return false;
            return true;
        }

        Method method(ClassLoader loader) throws ReflectiveOperationException {
            Class<?>[] arguments = new Class<?>[parameters.size()];
            for (int i = 0; i < arguments.length; i++)
                arguments[i] = load(parameters.get(i), loader);
            Method method = load(owner, loader).getDeclaredMethod(name, arguments);
            if (!descriptor(method.getReturnType()).equals(result)
                    || java.lang.reflect.Modifier.isStatic(method.getModifiers())
                            != ((access & 8) != 0))
                throw new NoSuchMethodException("DEX method contract changed: " + owner);
            method.setAccessible(true);
            return method;
        }

        Class<?> loadOwner(ClassLoader loader) throws ClassNotFoundException {
            return load(owner, loader);
        }

        Member uniqueRead(String declaringType, String fieldType, String before)
                throws NoSuchFieldException {
            return uniqueAccess(reads, declaringType, fieldType, before, "read");
        }

        Member uniqueWrite(String declaringType, String fieldType, String before)
                throws NoSuchFieldException {
            return uniqueAccess(writes, declaringType, fieldType, before, "write");
        }

        private Member uniqueAccess(
                List<? extends Access> accesses,
                String declaringType,
                String fieldType,
                String before,
                String kind)
                throws NoSuchFieldException {
            int limit = before == null ? Integer.MAX_VALUE : anchors.getOrDefault(before, -1);
            Set<Member> candidates = new LinkedHashSet<>();
            for (Access entry : accesses) {
                Member field = entry.field();
                if (entry.offset() < limit
                        && field.owner.equals(declaringType)
                        && field.type.equals(fieldType)) candidates.add(field);
            }
            if (candidates.size() != 1)
                throw new NoSuchFieldException(
                        "Expected one "
                                + fieldType
                                + " "
                                + kind
                                + " in "
                                + owner
                                + ", found "
                                + candidates.size());
            return candidates.iterator().next();
        }
    }

    private record Target(
            String owner, String name, List<String> parameters, String result, boolean callers) {}

    static Body body(Method method) throws ReflectiveOperationException, IOException {
        return body(method, Set.of());
    }

    static Body body(Method method, Set<String> anchors)
            throws ReflectiveOperationException, IOException {
        Target target = methodTarget(method, false);
        List<Body> matches = find(method.getDeclaringClass().getClassLoader(), anchors, target);
        if (matches.size() != 1)
            throw new NoSuchMethodException("Missing or duplicate DEX method: " + method);
        return matches.get(0);
    }

    static Body constructor(java.lang.reflect.Constructor<?> ctor)
            throws ReflectiveOperationException, IOException {
        Target target = constructorTarget(ctor);
        List<Body> matches = find(ctor.getDeclaringClass().getClassLoader(), Set.of(), target);
        if (matches.size() != 1)
            throw new NoSuchMethodException("Missing or duplicate DEX constructor: " + ctor);
        return matches.get(0);
    }

    /**
     * Maps static {@code int} field names read inside {@code ctor} to the unique instance field
     * subsequently assigned on {@code declaringType} before the next static {@code int} read.
     */
    static Map<String, Member> staticReadWrites(
            java.lang.reflect.Constructor<?> ctor, Class<?> declaringType)
            throws ReflectiveOperationException, IOException {
        Target target = constructorTarget(ctor);
        String ownerDesc = descriptor(declaringType);
        Map<String, Member> result = new LinkedHashMap<>();
        boolean found = false;
        for (String source : dexSources(ctor.getDeclaringClass().getClassLoader())) {
            Map<String, Member> partial = readStaticReadWrites(new File(source), target, ownerDesc);
            if (partial != null) {
                if (found) throw new NoSuchMethodException("Duplicate DEX constructor: " + ctor);
                result.putAll(partial);
                found = true;
            }
        }
        if (!found) throw new NoSuchMethodException("Missing DEX constructor: " + ctor);
        return Map.copyOf(result);
    }

    /**
     * Resolves the unique constant integer literal passed to {@code argIndex} (0-based excluding
     * {@code this}) when {@code caller} invokes {@code <init>} of {@code targetOwner}.
     */
    static int constructorIntArgument(
            Method caller, Class<?> targetOwner, int paramCount, int argIndex)
            throws ReflectiveOperationException, IOException {
        Target target = methodTarget(caller, false);
        String ownerDesc = descriptor(targetOwner);
        Integer matched = null;
        for (String source : dexSources(caller.getDeclaringClass().getClassLoader())) {
            Integer found =
                    readConstructorIntArgument(
                            new File(source), target, ownerDesc, paramCount, argIndex);
            if (found == null) continue;
            if (matched != null)
                throw new NoSuchMethodException("Ambiguous constructor call in " + caller);
            matched = found;
        }
        if (matched == null)
            throw new NoSuchMethodException(
                    "Missing constructor constant int argument in " + caller + " -> " + ownerDesc);
        return matched;
    }

    /** Resolves the DEX switch case integer whose basic block contains {@code anchor}. */
    static int switchCase(Method method, String anchor)
            throws ReflectiveOperationException, IOException {
        Target target = methodTarget(method, false);
        Integer matched = null;
        for (String source : dexSources(method.getDeclaringClass().getClassLoader())) {
            Integer found = readSwitchCase(new File(source), anchor, target);
            if (found == null) continue;
            if (matched != null)
                throw new NoSuchMethodException("Duplicate DEX switch target for " + method);
            matched = found;
        }
        if (matched == null)
            throw new NoSuchMethodException(
                    "Missing switch case for anchor in " + method + ": " + anchor);
        return matched;
    }

    private static Target methodTarget(Method method, boolean callers) {
        return memberTarget(
                method.getDeclaringClass(),
                method.getName(),
                method.getParameterTypes(),
                method.getReturnType(),
                callers);
    }

    private static Target constructorTarget(java.lang.reflect.Constructor<?> ctor) {
        return memberTarget(
                ctor.getDeclaringClass(), "<init>", ctor.getParameterTypes(), void.class, false);
    }

    private static Target memberTarget(
            Class<?> owner,
            String name,
            Class<?>[] parameterTypes,
            Class<?> returnType,
            boolean callers) {
        var args = new ArrayList<String>();
        for (Class<?> parameter : parameterTypes) args.add(descriptor(parameter));
        return new Target(
                descriptor(owner), name, List.copyOf(args), descriptor(returnType), callers);
    }

    static List<Body> callers(Method method) throws ReflectiveOperationException, IOException {
        return find(method.getDeclaringClass().getClassLoader(), Set.of(), methodTarget(method, true));
    }

    static List<Body> callers(
            ClassLoader loader,
            Class<?> owner,
            String name,
            Class<?> result,
            Class<?>... parameters)
            throws ReflectiveOperationException, IOException {
        Method method = owner.getMethod(name, parameters);
        if (method.getReturnType() != result)
            throw new NoSuchMethodException("Call result changed: " + method);
        var args = new ArrayList<String>();
        for (Class<?> parameter : parameters) args.add(descriptor(parameter));
        return find(
                loader,
                Set.of(),
                new Target(descriptor(owner), name, List.copyOf(args), descriptor(result), true));
    }

    static List<Body> find(ClassLoader loader, Set<String> anchors)
            throws ReflectiveOperationException, IOException {
        return find(loader, anchors, null);
    }

    private static Set<String> dexSources(ClassLoader loader) throws ReflectiveOperationException {
        Object elements = Reflect.field(Reflect.field(loader, "pathList"), "dexElements");
        Set<String> sources = new LinkedHashSet<>();
        for (int i = 0; i < Array.getLength(elements); i++) {
            Object dex = Reflect.field(Array.get(elements, i), "dexFile");
            if (dex instanceof dalvik.system.DexFile file) sources.add(file.getName());
        }
        return sources;
    }

    private static List<Body> find(ClassLoader loader, Set<String> anchors, Target target)
            throws ReflectiveOperationException, IOException {
        List<Body> result = new ArrayList<>();
        for (String source : dexSources(loader)) result.addAll(read(new File(source), anchors, target));
        return List.copyOf(result);
    }

    static List<Body> read(File source, Set<String> anchors) throws IOException {
        return read(source, anchors, null);
    }

    interface Work<T> {
        T run() throws Exception;
    }

    interface ReflectiveWork {
        void run() throws ReflectiveOperationException;
    }

    /**
     * Keeps decoded DEX files for the duration of {@code work} on this thread only, so a burst
     * of {@link #body} lookups opens each APK once instead of once per method.
     */
    static <T> T cached(Work<T> work) throws Exception {
        if (session.get() != null) return work.run();
        session.set(new Session());
        try {
            return work.run();
        } finally {
            session.remove();
        }
    }

    static void cachedReflective(ReflectiveWork work) throws ReflectiveOperationException {
        if (session.get() != null) {
            work.run();
            return;
        }
        session.set(new Session());
        try {
            work.run();
        } finally {
            session.remove();
        }
    }

    static void cachedRun(Runnable work) {
        if (session.get() != null) {
            work.run();
            return;
        }
        session.set(new Session());
        try {
            work.run();
        } finally {
            session.remove();
        }
    }

    private static final ThreadLocal<Session> session = new ThreadLocal<>();

    private static final class Session {
        final Map<String, List<Reader>> readers = new HashMap<>();

        List<Reader> readers(File source) throws IOException {
            String key = source.getPath();
            List<Reader> cached = readers.get(key);
            if (cached != null) return cached;
            List<Reader> loaded = new ArrayList<>();
            visitDex(source, bytes -> {
                try {
                    for (Reader reader : Reader.readers(bytes)) {
                        reader.indexed = true;
                        loaded.add(reader);
                    }
                } catch (IndexOutOfBoundsException | IllegalArgumentException error) {
                    throw new IOException("Invalid DEX while caching editor signatures", error);
                }
                return false;
            });
            readers.put(key, loaded);
            return loaded;
        }
    }

    private static List<Body> read(File source, Set<String> anchors, Target target)
            throws IOException {
        List<Body> result = new ArrayList<>();
        Session active = session.get();
        if (active != null) {
            for (Reader reader : active.readers(source)) {
                try {
                    result.addAll(reader.find(anchors, target));
                } catch (IndexOutOfBoundsException
                        | IllegalArgumentException
                        | ArithmeticException error) {
                    throw new IOException("Invalid DEX while resolving editor signatures", error);
                }
            }
            return result;
        }
        visitDex(source, bytes -> {
            result.addAll(inspect(bytes, anchors, target));
            return false;
        });
        return result;
    }

    static Integer readSwitchCase(File source, String anchor, Target target) throws IOException {
        Integer[] matched = new Integer[1];
        forReaders(source, "switch case", reader -> {
            Integer found = reader.findSwitchCase(anchor, target);
            if (found != null) {
                if (matched[0] != null)
                    throw new IllegalStateException("Duplicate DEX switch target for " + target.name);
                matched[0] = found;
            }
        });
        return matched[0];
    }

    static Map<String, Member> readStaticReadWrites(
            File source, String ctorOwnerDesc, List<String> ctorParams, String declaringType)
            throws IOException {
        return readStaticReadWrites(
                source, new Target(ctorOwnerDesc, "<init>", ctorParams, "V", false), declaringType);
    }

    static Integer readConstructorIntArgument(
            File source,
            String callerOwnerDesc,
            String callerName,
            List<String> callerParams,
            String callerResult,
            String targetOwnerDesc,
            int paramCount,
            int argIndex)
            throws IOException {
        return readConstructorIntArgument(
                source,
                new Target(callerOwnerDesc, callerName, callerParams, callerResult, false),
                targetOwnerDesc,
                paramCount,
                argIndex);
    }

    private static Map<String, Member> readStaticReadWrites(
            File source, Target target, String declaringType) throws IOException {
        List<Map<String, Member>> matched = new ArrayList<>(1);
        forReaders(source, "constructor view bindings", reader -> {
            Map<String, Member> found = reader.findStaticReadWrites(target, declaringType);
            if (found != null) {
                if (!matched.isEmpty())
                    throw new IllegalStateException("Duplicate DEX constructor for " + target.owner);
                matched.add(found);
            }
        });
        return matched.isEmpty() ? null : matched.get(0);
    }

    private static Integer readConstructorIntArgument(
            File source, Target target, String ownerDesc, int paramCount, int argIndex)
            throws IOException {
        Integer[] matched = new Integer[1];
        forReaders(source, "constructor int argument", reader -> {
            Integer found =
                    reader.findConstructorIntArgument(target, ownerDesc, paramCount, argIndex);
            if (found != null) {
                if (matched[0] != null)
                    throw new IllegalStateException("Ambiguous DEX constructor call in " + target.name);
                matched[0] = found;
            }
        });
        return matched[0];
    }

    private interface ReaderConsumer {
        void accept(Reader reader);
    }

    private static void forReaders(File source, String context, ReaderConsumer consumer)
            throws IOException {
        Session active = session.get();
        if (active != null) {
            for (Reader reader : active.readers(source)) {
                try {
                    consumer.accept(reader);
                } catch (IndexOutOfBoundsException | IllegalArgumentException | ArithmeticException error) {
                    throw new IOException("Invalid DEX while resolving " + context, error);
                }
            }
            return;
        }
        visitDex(source, bytes -> {
            try {
                for (Reader reader : Reader.readers(bytes)) consumer.accept(reader);
                return false;
            } catch (IndexOutOfBoundsException | IllegalArgumentException | ArithmeticException error) {
                throw new IOException("Invalid DEX while resolving " + context, error);
            }
        });
    }

    private interface DexVisitor {
        boolean accept(byte[] bytes) throws IOException;
    }

    private static void visitDex(File source, DexVisitor visitor) throws IOException {
        if (source.getName().endsWith(".dex")) {
            if (source.length() > MAX_DEX) throw new IOException("DEX is too large");
            visitor.accept(Files.readAllBytes(source.toPath()));
            return;
        }
        try (ZipFile zip = new ZipFile(source)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().matches("classes[0-9]*\\.dex")) continue;
                if (entry.getSize() > MAX_DEX) throw new IOException("DEX is too large");
                try (var input = zip.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes(MAX_DEX + 1);
                    if (bytes.length > MAX_DEX) throw new IOException("DEX is too large");
                    if (visitor.accept(bytes)) return;
                }
            }
        }
    }

    static List<Body> inspect(byte[] bytes, Set<String> anchors) throws IOException {
        return inspect(bytes, anchors, null);
    }

    static Body inspect(byte[] bytes, Method method) throws IOException {
        List<Body> found = inspect(bytes, Set.of(), methodTarget(method, false));
        if (found.size() != 1)
            throw new IllegalStateException("Expected 1 DEX body for " + method + ", found " + found.size());
        return found.get(0);
    }

    private static List<Body> inspect(byte[] bytes, Set<String> anchors, Target target)
            throws IOException {
        try {
            List<Reader> sections = Reader.readers(bytes);
            if (sections.size() == 1) return sections.get(0).find(anchors, target);
            List<Body> found = new ArrayList<>();
            for (Reader reader : sections) found.addAll(reader.find(anchors, target));
            return found;
        } catch (IndexOutOfBoundsException | IllegalArgumentException | ArithmeticException error) {
            throw new IOException("Invalid DEX while resolving editor signatures", error);
        }
    }

    private static final class Reader {
        final byte[] data;
        final int headerOffset;
        final int stringCount, strings, types, protos, fields, methodCount, methods, classCount, classDefs;
        final Map<Set<String>, Map<Integer, String>> anchorCache;
        boolean indexed;
        private Map<String, Integer> classIndex;

        static List<Reader> readers(byte[] data) {
            Reader first = new Reader(data, 0, null);
            int headerSize = first.u32(36);
            if (headerSize < 120 || data.length < 120) return List.of(first);
            int containerSize = first.u32(112);
            int declaredOffset = first.u32(116);
            int firstSize = first.u32(32);
            if (declaredOffset != 0 || containerSize > data.length || containerSize <= firstSize) {
                return List.of(first);
            }
            Map<Long, Map<Set<String>, Map<Integer, String>>> sharedAnchors = new HashMap<>();
            List<Reader> sections = new ArrayList<>();
            int offset = 0;
            while (offset < containerSize) {
                if (containerSize - offset < 120)
                    throw new IllegalArgumentException("Truncated DEX container section");
                Reader section = new Reader(data, offset, sharedAnchors);
                int sectionHeader = section.u32(offset + 36);
                int sectionSize = section.u32(offset + 32);
                int sectionOffset = section.u32(offset + 116);
                if (sectionHeader < 120
                        || sectionSize <= 0
                        || sectionOffset != offset
                        || sectionSize > containerSize - offset) {
                    throw new IllegalArgumentException(
                            "Invalid DEX container section at " + offset);
                }
                sections.add(section);
                offset += sectionSize;
            }
            return sections;
        }

        int classDef(String owner) {
            if (classIndex == null) {
                Map<String, Integer> index = new HashMap<>();
                for (int i = 0; i < classCount; i++)
                    index.putIfAbsent(type(u32(classDefs + i * 32)), i);
                classIndex = index;
            }
            return classIndex.getOrDefault(owner, -1);
        }

        Reader(
                byte[] data,
                int headerOffset,
                Map<Long, Map<Set<String>, Map<Integer, String>>> sharedAnchors) {
            this.data = data;
            this.headerOffset = headerOffset;
            if (headerOffset < 0
                    || data.length - headerOffset < 112
                    || data[headerOffset] != 'd'
                    || data[headerOffset + 1] != 'e'
                    || data[headerOffset + 2] != 'x'
                    || data[headerOffset + 3] != '\n'
                    || u32(headerOffset + 40) != 0x12345678)
                throw new IllegalArgumentException("Unsupported DEX header");
            stringCount = u32(headerOffset + 56);
            strings = u32(headerOffset + 60);
            types = u32(headerOffset + 68);
            protos = u32(headerOffset + 76);
            fields = u32(headerOffset + 84);
            methodCount = u32(headerOffset + 88);
            methods = u32(headerOffset + 92);
            classCount = u32(headerOffset + 96);
            classDefs = u32(headerOffset + 100);
            if (sharedAnchors != null) {
                long tableKey = (((long) strings) << 32) | (stringCount & 0xffffffffL);
                anchorCache = sharedAnchors.computeIfAbsent(tableKey, ignored -> new HashMap<>());
            } else {
                anchorCache = null;
            }
        }

        int u16(int offset) {
            return (data[offset] & 255) | ((data[offset + 1] & 255) << 8);
        }

        int u32(int offset) {
            int value = s32(offset);
            if (value < 0) throw new IllegalArgumentException("DEX offset exceeds limit");
            return value;
        }

        int s32(int offset) {
            return u16(offset) | (u16(offset + 2) << 16);
        }

        int leb(int[] cursor) {
            int value = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                int next = data[cursor[0]++] & 255;
                if (shift == 28 && (next & 0xf8) != 0)
                    throw new IllegalArgumentException("Invalid DEX ULEB128");
                value |= (next & 127) << shift;
                if ((next & 128) == 0) return value;
            }
            throw new IllegalArgumentException("Invalid DEX ULEB128");
        }

        int textOffset(int index) {
            int[] cursor = {u32(strings + index * 4)};
            leb(cursor);
            return cursor[0];
        }

        String text(int index) {
            int start = textOffset(index), end = start;
            while (data[end] != 0) end++;
            return new String(data, start, end - start, StandardCharsets.UTF_8);
        }

        String type(int index) {
            return text(u32(types + index * 4));
        }

        boolean matchesText(int index, String expected) {
            int start = textOffset(index), len = expected.length();
            for (int p = 0; p < len; p++) {
                char c = expected.charAt(p);
                if (c >= 0x80) return text(index).equals(expected);
                if (data[start + p] != (byte) c) return false;
            }
            return data[start + len] == 0;
        }

        boolean matchesType(int index, String expected) {
            return matchesText(u32(types + index * 4), expected);
        }

        Map<Integer, String> anchorIds(Set<String> anchors) {
            if (anchors.isEmpty()) return Map.of();
            if (anchorCache != null) {
                Map<Integer, String> cached = anchorCache.get(anchors);
                if (cached != null) return cached;
            }
            Map<Integer, String> ids = new HashMap<>();
            for (int i = 0; i < stringCount; i++) {
                for (String anchor : anchors) {
                    if (matchesText(i, anchor)) ids.put(i, anchor);
                }
            }
            if (anchorCache != null) anchorCache.put(Set.copyOf(anchors), ids);
            return ids;
        }

        boolean matchesMethod(int index, Target target) {
            int method = methods + index * 8, proto = protos + u16(method + 2) * 12;
            int args = u32(proto + 8), size = args == 0 ? 0 : u32(args);
            if (size != target.parameters.size()
                    || !matchesText(u32(method + 4), target.name)
                    || !matchesType(u32(proto + 4), target.result)) return false;
            for (int a = 0; a < size; a++)
                if (!matchesType(u16(args + 4 + a * 2), target.parameters.get(a))) return false;
            return true;
        }

        int findMethodCode(Target target) {
            int first = 0, count = classCount, base = classDefs;
            if (indexed) {
                int def = classDef(target.owner);
                if (def < 0) return -1;
                first = def;
                count = def + 1;
            }
            for (int i = first; i < count; i++) {
                if (!matchesType(u32(base + i * 32), target.owner)) continue;
                int offset = u32(base + i * 32 + 24);
                if (offset == 0) continue;
                int[] cursor = {offset};
                int staticFields = leb(cursor), instanceFields = leb(cursor);
                int direct = leb(cursor), virtual = leb(cursor);
                for (int n = 0; n < staticFields + instanceFields; n++) {
                    leb(cursor);
                    leb(cursor);
                }
                for (int countMethods : new int[] {direct, virtual}) {
                    int index = 0;
                    for (int n = 0; n < countMethods; n++) {
                        index += leb(cursor);
                        leb(cursor);
                        int code = leb(cursor);
                        if (code != 0 && matchesMethod(index, target)) return code;
                    }
                }
            }
            return -1;
        }

        Integer findSwitchCase(String anchor, Target target) {
            Map<Integer, String> ids = anchorIds(Set.of(anchor));
            if (ids.isEmpty()) return null;
            int code = findMethodCode(target);
            return code < 0 ? null : resolveSwitchCase(code, ids);
        }

        Map<String, Member> findStaticReadWrites(Target target, String declaringType) {
            int code = findMethodCode(target);
            if (code < 0) return null;
            Map<String, Member> assigned = new LinkedHashMap<>();
            Map<Integer, String> staticIntIds = new HashMap<>();
            Map<Integer, String> derivedViewIds = new HashMap<>();
            String pendingInvokeId = null;
            int units = u32(code + 12), base = code + 16;
            for (int pc = 0; pc < units; ) {
                int word = u16(base + pc * 2), op = word & 255;
                if (op >= 0x60 && op <= 0x66) {
                    int dst = word >>> 8;
                    Member m = fieldMember(u16(base + pc * 2 + 2));
                    if (m.type.equals("I")) staticIntIds.put(dst, m.name);
                    else staticIntIds.remove(dst);
                    derivedViewIds.remove(dst);
                    pendingInvokeId = null;
                } else if (op == 0x01 || op == 0x02 || op == 0x03) {
                    int dst = op == 0x01 ? (word >>> 8) & 15
                            : op == 0x02 ? word >>> 8 : u16(base + pc * 2 + 2);
                    int src = op == 0x01 ? (word >>> 12) & 15
                            : op == 0x02 ? u16(base + pc * 2 + 2) : u16(base + pc * 2 + 4);
                    copyTrackingEntry(staticIntIds, src, dst);
                    derivedViewIds.remove(dst);
                } else if (op == 0x07 || op == 0x08 || op == 0x09) {
                    int dst = op == 0x07 ? (word >>> 8) & 15
                            : op == 0x08 ? word >>> 8 : u16(base + pc * 2 + 2);
                    int src = op == 0x07 ? (word >>> 12) & 15
                            : op == 0x08 ? u16(base + pc * 2 + 2) : u16(base + pc * 2 + 4);
                    copyTrackingEntry(derivedViewIds, src, dst);
                    staticIntIds.remove(dst);
                } else if (op >= 0x6e && op <= 0x72 || op >= 0x74 && op <= 0x78) {
                    pendingInvokeId = invokedStaticId(word, op, base, pc, staticIntIds);
                } else if (op == 0x0c) {
                    int dst = word >>> 8;
                    if (pendingInvokeId != null) derivedViewIds.put(dst, pendingInvokeId);
                    else derivedViewIds.remove(dst);
                    staticIntIds.remove(dst);
                    pendingInvokeId = null;
                } else if (op == 0x1f) {
                    staticIntIds.remove(word >>> 8);
                    pendingInvokeId = null;
                } else if (op >= 0x59 && op <= 0x5f) {
                    int valReg = (word >>> 8) & 15;
                    String idName = derivedViewIds.get(valReg);
                    if (idName == null) idName = staticIntIds.get(valReg);
                    Member w = fieldMember(u16(base + pc * 2 + 2));
                    if (idName != null && w.owner.equals(declaringType)) {
                        assigned.putIfAbsent(idName, w);
                    }
                    pendingInvokeId = null;
                } else {
                    invalidateWrittenRegister(staticIntIds, word, op, base, pc);
                    invalidateWrittenRegister(derivedViewIds, word, op, base, pc);
                    pendingInvokeId = null;
                }
                int width = width(base, pc);
                if (width <= 0 || width > units - pc)
                    throw new IllegalArgumentException("Invalid DEX instruction span");
                pc += width;
            }
            return assigned;
        }

        private static void copyTrackingEntry(Map<Integer, String> map, int src, int dst) {
            String value = map.get(src);
            if (value != null) map.put(dst, value);
            else map.remove(dst);
        }

        private String invokedStaticId(
                int word, int op, int base, int pc, Map<Integer, String> staticIntIds) {
            if (op >= 0x6e && op <= 0x72) {
                int count = Math.min((word >>> 12) & 15, 5);
                int word2 = u16(base + pc * 2 + 4);
                for (int slot = 0; slot < count; slot++) {
                    int reg = slot == 4 ? ((word >>> 8) & 15) : ((word2 >>> (slot * 4)) & 15);
                    String id = staticIntIds.get(reg);
                    if (id != null) return id;
                }
            } else {
                int count = word >>> 8;
                int firstReg = u16(base + pc * 2 + 4);
                for (int slot = 0; slot < count; slot++) {
                    String id = staticIntIds.get(firstReg + slot);
                    if (id != null) return id;
                }
            }
            return null;
        }

        Integer findConstructorIntArgument(
                Target target, String ownerDesc, int paramCount, int argIndex) {
            int code = findMethodCode(target);
            if (code < 0) return null;
            Map<Integer, Integer> consts = new HashMap<>();
            Integer matched = null;
            int units = u32(code + 12), base = code + 16;
            for (int pc = 0; pc < units; ) {
                int word = u16(base + pc * 2), op = word & 255;
                if (op == 0x12) {
                    consts.put((word >>> 8) & 15, ((short) word) >> 12);
                } else if (op == 0x13) {
                    consts.put(word >>> 8, (int) (short) u16(base + pc * 2 + 2));
                } else if (op == 0x14) {
                    consts.put(word >>> 8, s32(base + pc * 2 + 2));
                } else if (op == 0x15) {
                    consts.put(word >>> 8, ((short) u16(base + pc * 2 + 2)) << 16);
                } else if (op == 0x01 || op == 0x02 || op == 0x03) {
                    int dst = op == 0x01 ? (word >>> 8) & 15
                            : op == 0x02 ? word >>> 8 : u16(base + pc * 2 + 2);
                    int src = op == 0x01 ? (word >>> 12) & 15
                            : op == 0x02 ? u16(base + pc * 2 + 2) : u16(base + pc * 2 + 4);
                    Integer v = consts.get(src);
                    if (v != null) consts.put(dst, v);
                    else consts.remove(dst);
                } else if (op == 0x70 || op == 0x76) {
                    Call invoked = call(u16(base + pc * 2 + 2), pc, false);
                    if (invoked.owner.equals(ownerDesc)
                            && invoked.name.equals("<init>")
                            && invoked.parameters.size() == paramCount) {
                        int slot = 1 + argIndex;
                        int reg;
                        if (op == 0x70) {
                            if (slot < 0 || slot > 4) continue;
                            int word2 = u16(base + pc * 2 + 4);
                            reg = slot == 4 ? ((word >>> 8) & 15) : ((word2 >>> (slot * 4)) & 15);
                        } else {
                            reg = u16(base + pc * 2 + 4) + slot;
                        }
                        Integer val = consts.get(reg);
                        if (val != null) {
                            if (matched != null && !matched.equals(val))
                                throw new IllegalStateException(
                                        "Multiple constructor int literals for " + ownerDesc);
                            matched = val;
                        }
                    }
                } else {
                    invalidateWrittenRegister(consts, word, op, base, pc);
                }
                int width = width(base, pc);
                if (width <= 0 || width > units - pc)
                    throw new IllegalArgumentException("Invalid DEX instruction span");
                pc += width;
            }
            return matched;
        }

        private void invalidateWrittenRegister(
                Map<Integer, ?> regs, int word, int op, int base, int pc) {
            if (op == 0x04 || op == 0x07 || op == 0x12 || op == 0x20 || op == 0x21
                    || op >= 0x52 && op <= 0x58 || op >= 0x7b && op <= 0x8f
                    || op >= 0xb0 && op <= 0xd7) {
                int dst = (word >>> 8) & 15;
                regs.remove(dst);
                if (op == 0x04) regs.remove(dst + 1);
            } else if (op == 0x05 || op == 0x08 || op >= 0x0a && op <= 0x0d
                    || op >= 0x13 && op <= 0x1f || op == 0x22 || op == 0x23
                    || op >= 0x44 && op <= 0x4a || op >= 0x60 && op <= 0x66
                    || op >= 0x90 && op <= 0xaf || op >= 0xd8 && op <= 0xe2) {
                int dst = word >>> 8;
                regs.remove(dst);
                if (op == 0x05 || op == 0x0b || op >= 0x16 && op <= 0x19) regs.remove(dst + 1);
            } else if (op == 0x06 || op == 0x09) {
                int dst = u16(base + pc * 2 + 2);
                regs.remove(dst);
                if (op == 0x06) regs.remove(dst + 1);
            }
        }

        private Integer resolveSwitchCase(int code, Map<Integer, String> ids) {
            int units = u32(code + 12), base = code + 16;
            List<Integer> anchorPcs = new ArrayList<>();
            List<int[]> targets = new ArrayList<>();
            int switchCount = 0;
            for (int pc = 0; pc < units; ) {
                int op = u16(base + pc * 2) & 255;
                if (op == 0x1a || op == 0x1b) {
                    int id = op == 0x1a ? u16(base + pc * 2 + 2) : u32(base + pc * 2 + 2);
                    if (ids.containsKey(id)) anchorPcs.add(pc);
                } else if (op == 0x2b || op == 0x2c) {
                    switchCount++;
                    int payloadPc = pc + s32(base + pc * 2 + 2);
                    int payload = base + payloadPc * 2;
                    int ident = u16(payload);
                    int size = u16(payload + 2);
                    if (ident == 0x0100) {
                        int firstKey = s32(payload + 4);
                        for (int k = 0; k < size; k++)
                            targets.add(new int[] {firstKey + k, pc + s32(payload + 8 + k * 4)});
                    } else if (ident == 0x0200) {
                        for (int k = 0; k < size; k++)
                            targets.add(new int[] {
                                s32(payload + 4 + k * 4),
                                pc + s32(payload + 4 + size * 4 + k * 4)
                            });
                    }
                }
                int width = width(base, pc);
                if (width <= 0 || width > units - pc)
                    throw new IllegalArgumentException("Invalid DEX instruction span");
                pc += width;
            }
            if (anchorPcs.isEmpty()) return null;
            if (anchorPcs.size() != 1 || switchCount != 1)
                throw new IllegalArgumentException("Ambiguous DEX switch structure for anchor");
            int anchorPc = anchorPcs.get(0);
            int bestTarget = -1;
            for (int[] entry : targets)
                if (entry[1] <= anchorPc && entry[1] > bestTarget) bestTarget = entry[1];
            if (bestTarget < 0) return null;
            Integer key = null;
            for (int[] entry : targets) {
                if (entry[1] == bestTarget) {
                    if (key != null)
                        throw new IllegalArgumentException("Merged DEX switch case for anchor");
                    key = entry[0];
                }
            }
            return key;
        }

        List<Body> find(Set<String> anchors, Target target) {
            Map<Integer, String> ids = anchorIds(anchors);
            List<Body> found = new ArrayList<>();
            Set<Integer> callIds = new LinkedHashSet<>();
            if (target != null && target.callers) {
                for (int i = 0; i < methodCount; i++) {
                    if (matchesType(u16(methods + i * 8), target.owner)
                            && matchesMethod(i, target)) callIds.add(i);
                }
                if (callIds.isEmpty()) return found;
            }
            if (ids.isEmpty() && target == null) return found;
            int first = 0, count = classCount, base = classDefs;
            if (indexed && target != null && !target.callers) {
                int def = classDef(target.owner);
                if (def < 0) return found;
                first = def;
                count = def + 1;
            }
            for (int i = first; i < count; i++) {
                if (target != null
                        && !target.callers
                        && !matchesType(u32(base + i * 32), target.owner)) continue;
                int offset = u32(base + i * 32 + 24);
                if (offset == 0) continue;
                int[] cursor = {offset};
                int staticFields = leb(cursor), instanceFields = leb(cursor);
                int direct = leb(cursor), virtual = leb(cursor);
                for (int n = 0; n < staticFields + instanceFields; n++) {
                    leb(cursor);
                    leb(cursor);
                }
                for (int countMethods : new int[] {direct, virtual}) {
                    int index = 0;
                    for (int n = 0; n < countMethods; n++) {
                        index += leb(cursor);
                        int access = leb(cursor), code = leb(cursor);
                        if (code == 0) continue;
                        if (target != null && !target.callers && !matchesMethod(index, target))
                            continue;
                        if (!callIds.isEmpty()) {
                            boolean calls = false;
                            for (int pc = 0, units = u32(code + 12), instructions = code + 16;
                                    pc < units;
                                    pc += width(instructions, pc)) {
                                int op = u16(instructions + pc * 2) & 255;
                                if ((op >= 0x6e && op <= 0x72 || op >= 0x74 && op <= 0x78)
                                        && callIds.contains(u16(instructions + pc * 2 + 2))) {
                                    calls = true;
                                    break;
                                }
                            }
                            if (!calls) continue;
                        }
                        scan(index, access, code, ids, found, target != null);
                    }
                }
            }
            return found;
        }

        void scan(
                int index,
                int access,
                int code,
                Map<Integer, String> ids,
                List<Body> found,
                boolean unanchored) {
            int units = u32(code + 12), base = code + 16;
            Map<String, Integer> anchors = new HashMap<>();
            for (int pc = 0; pc < units; ) {
                int op = u16(base + pc * 2) & 255;
                if (op == 0x1a || op == 0x1b) {
                    int id = op == 0x1a ? u16(base + pc * 2 + 2) : u32(base + pc * 2 + 2);
                    String anchor = ids.get(id);
                    if (anchor != null) anchors.putIfAbsent(anchor, pc);
                }
                int width = width(base, pc);
                if (width <= 0 || width > units - pc)
                    throw new IllegalArgumentException("Invalid DEX instruction span");
                pc += width;
            }
            if (anchors.isEmpty() && !unanchored) return;
            List<Read> reads = new ArrayList<>();
            List<Write> writes = new ArrayList<>();
            List<Call> calls = new ArrayList<>();
            for (int pc = 0; pc < units; pc += width(base, pc)) {
                int op = u16(base + pc * 2) & 255;
                if (op >= 0x6e && op <= 0x72 || op >= 0x74 && op <= 0x78)
                    calls.add(call(u16(base + pc * 2 + 2), pc, op == 0x71 || op == 0x77));
                if (op >= 0x52 && op <= 0x58)
                    reads.add(new Read(pc, fieldMember(u16(base + pc * 2 + 2))));
                else if (op >= 0x59 && op <= 0x5f)
                    writes.add(new Write(pc, fieldMember(u16(base + pc * 2 + 2))));
            }
            int method = methods + index * 8, proto = protos + u16(method + 2) * 12;
            List<String> parameters = new ArrayList<>();
            int args = u32(proto + 8);
            if (args != 0)
                for (int p = 0, size = u32(args); p < size; p++)
                    parameters.add(type(u16(args + 4 + p * 2)));
            found.add(
                    new Body(
                            type(u16(method)),
                            text(u32(method + 4)),
                            List.copyOf(parameters),
                            type(u32(proto + 4)),
                            access,
                            Map.copyOf(anchors),
                            List.copyOf(reads),
                            List.copyOf(writes),
                            List.copyOf(calls)));
        }

        private Member fieldMember(int index) {
            int field = fields + index * 8;
            return new Member(type(u16(field)), text(u32(field + 4)), type(u16(field + 2)));
        }

        Call call(int index, int pc, boolean isStatic) {
            int method = methods + index * 8, proto = protos + u16(method + 2) * 12;
            List<String> parameters = new ArrayList<>();
            int args = u32(proto + 8);
            if (args != 0)
                for (int p = 0, size = u32(args); p < size; p++)
                    parameters.add(type(u16(args + 4 + p * 2)));
            return new Call(
                    pc,
                    type(u16(method)),
                    text(u32(method + 4)),
                    List.copyOf(parameters),
                    type(u32(proto + 4)),
                    isStatic);
        }

        int width(int base, int pc) {
            int word = u16(base + pc * 2), op = word & 255;
            if (op == 0 && word != 0) {
                int size = u16(base + pc * 2 + 2);
                return switch (word >>> 8) {
                    case 1 -> Math.addExact(4, Math.multiplyExact(size, 2));
                    case 2 -> Math.addExact(2, Math.multiplyExact(size, 4));
                    case 3 -> Math.toIntExact(4 + ((long) size * u32(base + pc * 2 + 4) + 1) / 2);
                    default -> throw new IllegalArgumentException("Unknown DEX payload");
                };
            }
            if (op == 0x18) return 5;
            if (op == 0xfa || op == 0xfb) return 4;
            if (op == 3
                    || op == 6
                    || op == 9
                    || op == 0x14
                    || op == 0x17
                    || op == 0x1b
                    || op >= 0x24 && op <= 0x26
                    || op >= 0x2a && op <= 0x2c
                    || op >= 0x6e && op <= 0x78
                    || op == 0xfc
                    || op == 0xfd) return 3;
            if (op == 2
                    || op == 5
                    || op == 8
                    || op >= 0x13 && op <= 0x1c
                    || op == 0x1f
                    || op == 0x20
                    || op == 0x22
                    || op == 0x23
                    || op == 0x29
                    || op >= 0x2d && op <= 0x6d
                    || op >= 0x90 && op <= 0xaf
                    || op >= 0xd0 && op <= 0xe2
                    || op >= 0xfe) return 2;
            if (op >= 0xe3) throw new IllegalArgumentException("Unsupported DEX opcode");
            return 1;
        }
    }

    static String descriptor(Class<?> type) {
        if (type == void.class) return "V";
        if (type == int.class) return "I";
        if (type == boolean.class) return "Z";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        if (type == double.class) return "D";
        if (type == byte.class) return "B";
        if (type == short.class) return "S";
        if (type == char.class) return "C";
        return type.isArray()
                ? type.getName().replace('.', '/')
                : "L" + type.getName().replace('.', '/') + ";";
    }

    private static Class<?> load(String descriptor, ClassLoader loader)
            throws ClassNotFoundException {
        return switch (descriptor) {
            case "V" -> void.class;
            case "I" -> int.class;
            case "Z" -> boolean.class;
            case "J" -> long.class;
            case "F" -> float.class;
            case "D" -> double.class;
            case "B" -> byte.class;
            case "S" -> short.class;
            case "C" -> char.class;
            default ->
                    Class.forName(
                            descriptor.startsWith("[")
                                    ? descriptor.replace('/', '.')
                                    : descriptor
                                            .substring(1, descriptor.length() - 1)
                                            .replace('/', '.'),
                            false,
                            loader);
        };
    }
}
