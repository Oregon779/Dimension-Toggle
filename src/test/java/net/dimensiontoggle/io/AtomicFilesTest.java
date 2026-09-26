package net.dimensiontoggle.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AtomicFilesTest {

    @TempDir
    Path dir;

    @Test
    void writesAndReplacesWithoutLeavingTempFiles() throws IOException {
        Path target = dir.resolve("data.yml");

        AtomicFiles.write(target, "a: 1\n");
        assertEquals("a: 1\n", Files.readString(target));

        AtomicFiles.write(target, "a: 2\n");
        assertEquals("a: 2\n", Files.readString(target));

        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of(target), files.toList());
        }
    }

    @Test
    void lineVariantMatchesFilesWrite() throws IOException {
        Path atomic = dir.resolve("atomic.yml");
        Path plain = dir.resolve("plain.yml");
        List<String> lines = List.of("# comment", "key: value", "");

        AtomicFiles.write(atomic, lines);
        Files.write(plain, lines);

        assertEquals(Files.readString(plain), Files.readString(atomic));
    }

    @Test
    void createsMissingParentDirectories() throws IOException {
        Path target = dir.resolve("nested/deeper/file.yml");
        AtomicFiles.write(target, "x");
        assertEquals("x", Files.readString(target));
    }
}
