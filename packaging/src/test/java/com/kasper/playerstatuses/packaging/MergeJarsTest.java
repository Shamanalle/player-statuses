package com.kasper.playerstatuses.packaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class MergeJarsTest {

    @TempDir
    Path dir;

    private static byte[] emptyClass(String name, int version) {
        ClassWriter w = new ClassWriter(0);
        w.visit(version, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        w.visitEnd();
        return w.toByteArray();
    }

    private Path folder(String name, Map<String, byte[]> files) throws IOException {
        Path root = dir.resolve(name);
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            Path p = root.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        return root;
    }

    private static byte[] text(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void jabelCompileOnlyStubIsStrippedAndJpmsCoexistsCleanly() throws IOException {
        Map<String, byte[]> commonFiles = new LinkedHashMap<>();
        commonFiles.put("com/kasper/playerstatuses/Common.class", emptyClass("com/kasper/playerstatuses/Common", Opcodes.V1_8));
        commonFiles.put("com/github/bsideup/jabel/Desugar.class", emptyClass("com/github/bsideup/jabel/Desugar", Opcodes.V1_8));
        Path common = folder("commonWithJabel", commonFiles);
        Path base = folder("baseWithJabel", Map.of("playerstatuses-bands.properties", text("bands=mc1_21\n")));
        Path bandDir = folder("bandWithJabel", Map.of(
                "com/kasper/playerstatuses/Entry.class", emptyClass("com/kasper/playerstatuses/Entry", Opcodes.V21),
                "com/github/bsideup/jabel/Desugar.class", emptyClass("com/github/bsideup/jabel/Desugar", Opcodes.V21),
                "fabric.mod.json", text("{}")));

        Path out = dir.resolve("player-statuses-clean.jar");
        new MergeJars().run(out, List.of(common), List.of(base), Map.of("mc1_21", bandDir),
                new HashMap<>(), Integer.MAX_VALUE, Integer.MAX_VALUE, null);

        try (ZipFile zip = new ZipFile(out.toFile())) {
            assertNull(zip.getEntry("com/github/bsideup/jabel/Desugar.class"));
            assertNull(zip.getEntry("com/github/"));
            zip.stream().filter(e -> e.getName().endsWith(".class")).forEach(e ->
                    assertTrue(e.getName().startsWith("com/kasper/playerstatuses/"), "Unexpected class: " + e.getName()));
        }

        Path companionVp = dir.resolve("voice-physics-clean.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(companionVp))) {
            zos.putNextEntry(new ZipEntry("com/kasper/vcdistance/Common.class"));
            zos.write(emptyClass("com/kasper/vcdistance/Common", Opcodes.V1_8));
            zos.closeEntry();
        }
        var finder = ModuleFinder.of(out, companionVp);
        var resolved = ModuleLayer.boot().configuration().resolve(
                finder, ModuleFinder.of(), Set.of("player.statuses.clean", "voice.physics.clean"));
        var pkgs = resolved.findModule("player.statuses.clean").orElseThrow().reference().descriptor().packages();
        assertEquals(Set.of("com.kasper.playerstatuses", "com.kasper.playerstatuses.b.mc1_21"), pkgs);

        Path pollutedCommon = folder("pollutedCommon", Map.of(
                "com/kasper/playerstatuses/Common.class", emptyClass("com/kasper/playerstatuses/Common", Opcodes.V1_8),
                "org/example/Foreign.class", emptyClass("org/example/Foreign", Opcodes.V1_8)));
        IllegalStateException err = assertThrows(IllegalStateException.class, () -> new MergeJars().run(
                dir.resolve("bad.jar"), List.of(pollutedCommon), List.of(base),
                Map.of("mc1_21", bandDir), new HashMap<>(), Integer.MAX_VALUE, Integer.MAX_VALUE, null));
        assertTrue(err.getMessage().contains("org/example/Foreign.class"), err.getMessage());
    }
}
