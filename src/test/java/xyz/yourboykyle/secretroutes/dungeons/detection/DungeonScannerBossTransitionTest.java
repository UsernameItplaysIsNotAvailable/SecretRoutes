package xyz.yourboykyle.secretroutes.dungeons.detection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DungeonScannerBossTransitionTest {
    @TempDir
    static Path directory;
    private static Method transition;

    @BeforeAll
    static void compileBossTransition() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        Path relativeSource = Path.of("src/main/java/xyz/yourboykyle/secretroutes/dungeons/detection/DungeonScanner.java");
        while (!Files.exists(root.resolve(relativeSource))) {
            root = root.getParent();
            assertNotNull(root, "Cannot locate DungeonScanner.java");
        }
        String scanner = Files.readString(root.resolve(relativeSource));
        String fixture = """
                import java.util.*;
                public class BossTransitionFixture {
                    static Object currentRoom, scanRoomCentre;
                    static long visualGraceDeadlineNanos = -1L, nextScanAttemptNanos;
                    static class Room {
                        String name;
                        boolean invalidated;
                        Room(String name) { this.name = name; }
                        void invalidatePersonalBest() { invalidated = true; }
                    }
                    static class Main { static Room currentRoom; }
                    static class SRMConfig {
                        static final SRMConfig INSTANCE = new SRMConfig();
                        boolean pdRoutesEnabled, pdHideAfterPhase2;
                        static SRMConfig get() { return INSTANCE; }
                    }
                    static class DungeonUtil {
                        static boolean closed;
                        static boolean isF7() { return true; }
                        static boolean isPredevWindowClosed() { return closed; }
                    }
                    static class OnEnterNewRoom { static void onEnterNewRoom(Room room) {} }
                    static class SecretUtils {
                        static Object secrets;
                        static List<Object> secretLocations;
                        static boolean reset;
                        static void resetValues() { reset = true; }
                    }
                    static boolean isInsideF7BossBounds() { return true; }
                    static void bossTick() { %s }
                    %s
                    public static void check(boolean enabled, boolean closed, boolean hide, String previous, String expected) {
                        SRMConfig.INSTANCE.pdRoutesEnabled = enabled;
                        SRMConfig.INSTANCE.pdHideAfterPhase2 = hide;
                        DungeonUtil.closed = closed;
                        Room old = new Room(previous);
                        Main.currentRoom = old;
                        currentRoom = new Object();
                        scanRoomCentre = new Object();
                        visualGraceDeadlineNanos = -1L;
                        nextScanAttemptNanos = 100L;
                        SecretUtils.secrets = new Object();
                        SecretUtils.secretLocations = List.of(new Object());
                        SecretUtils.reset = false;
                        for (int tick = 0; tick < 2; tick++) {
                            bossTick();
                            String actual = Main.currentRoom == null ? null : Main.currentRoom.name;
                            if (!Objects.equals(expected.equals("none") ? null : expected, actual))
                                throw new AssertionError("Boss entry kept route: " + actual);
                            if (currentRoom != null || scanRoomCentre != null
                                    || visualGraceDeadlineNanos != -1L || nextScanAttemptNanos != 0L)
                                throw new AssertionError("Previous scanner state survived boss entry");
                            if (expected.equals("none") && (!old.invalidated || !SecretUtils.reset
                                    || SecretUtils.secrets != null || !SecretUtils.secretLocations.isEmpty()))
                                throw new AssertionError("Previous secret/PB state survived cleanup");
                        }
                    }
                }
                """.formatted(
                block(scanner, "if (DungeonUtil.isF7() && isInsideF7BossBounds())"),
                block(scanner, "public static boolean shouldShowPredevRoute()") + "\n"
                        + block(scanner, "private static void clearScannerState()") + "\n"
                        + block(scanner, "private static void invalidateActiveRoom()") + "\n"
                        + block(scanner, "private static void clearTransitionState()"));

        Path source = directory.resolve("BossTransitionFixture.java");
        Files.writeString(source, fixture);
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, errors,
                "-d", directory.toString(), source.toString()), errors::toString);
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()})) {
            Class<?> type = Class.forName("BossTransitionFixture", true, loader);
            try (var classes = Files.list(directory)) {
                for (Path file : classes.filter(p -> p.getFileName().toString().endsWith(".class")).toList()) {
                    Class.forName(file.getFileName().toString().replace(".class", ""), true, loader);
                }
            }
            transition = type.getMethod("check", boolean.class, boolean.class, boolean.class, String.class, String.class);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "false,false,true,unfinished-room,none",
            "true,true,true,unfinished-room,none",
            "true,false,true,unfinished-room,f7boss",
            "true,true,false,unfinished-room,f7boss",
            "false,false,true,f7boss,none"
    })
    void enteringBossRemovesPreviousRoomOrSelectsPredev(boolean enabled, boolean closed, boolean hide,
                                                       String previous, String expected) throws Exception {
        transition.invoke(null, enabled, closed, hide, previous, expected);
    }

    private static String block(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing scanner code: " + signature);
        int end = source.indexOf('{', start);
        int depth = 0;
        do {
            char c = source.charAt(end++);
            if (c == '{') depth++;
            if (c == '}') depth--;
        } while (depth > 0);
        return source.substring(start, end);
    }
}
