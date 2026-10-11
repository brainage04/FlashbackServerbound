package io.github.brainage04.flashbackserverbound.verify;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Drives Flashback in a real client. {@code -Dflashbackserverbound.verify=record} (this mod installed): plays a short
 * singleplayer session while Flashback records, checks the serverbound records in the saved replay, plays the
 * replay back and keeps it as {@code <out>/sample.zip}. {@code play} (this mod not installed): plays
 * {@code <out>/sample.zip} back. Playback must reach the replay's last tick without Flashback failing.
 */
public final class VerifyGameTest implements FabricClientGameTest {
    private static final int TIMEOUT_TICKS = 1200;

    @Override
    public void runTest(ClientGameTestContext context) {
        Path out = Path.of(System.getProperty("flashbackserverbound.verify.out"));
        String phase = System.getProperty("flashbackserverbound.verify");
        try {
            Files.createDirectories(out);
            Path sample = out.resolve("sample.zip");
            if ("record".equals(phase)) {
                Files.copy(record(context), sample, StandardCopyOption.REPLACE_EXISTING);
                ReplayRecords records = ReplayRecords.read(sample);
                Files.writeString(out.resolve("record-summary.txt"), records.toString());
                records.check();
                play(context, sample, out.resolve("playback-with-companion.txt"));
            } else if ("play".equals(phase)) {
                play(context, sample, out.resolve("playback-stock.txt"));
            } else {
                throw new AssertionError("unknown phase " + phase);
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static Path record(ClientGameTestContext context) throws IOException {
        context.runOnClient(client -> {
            var controls = Flashback.getConfig().recordingControls;
            controls.quicksave = true;
            controls.showRecordingToasts = false;
            controls.automaticallyStart = false;
        });
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("give @a minecraft:snowball 16");
            context.runOnClient(client -> Flashback.startRecordingReplay());
            context.waitFor(client -> Flashback.RECORDER != null && Flashback.RECORDER.readyToWrite(), TIMEOUT_TICKS);
            context.waitTicks(10);

            TestInput input = context.getInput();
            input.holdKeyFor(options -> options.keyUp, 15);
            input.holdKey(options -> options.keySprint);
            input.holdKeyFor(options -> options.keyUp, 15);
            input.releaseKey(options -> options.keySprint);
            // pressKey's press can be released before the tick ends; held keys are what the input records hold
            input.holdKeyFor(options -> options.keyJump, 3);
            context.waitTicks(10);
            input.holdKeyFor(options -> options.keyLeft, 10);
            input.holdKeyFor(options -> options.keyShift, 10);
            for (int turn = 0; turn < 10; turn++) {
                input.moveCursor(40, 10);
                context.waitTick();
            }
            for (int click = 0; click < 3; click++) {
                input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                context.waitTicks(4);
            }
            input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(5);
            input.pressKey(options -> options.keyHotbarSlots[3]);
            context.waitTicks(20);

            context.runOnClient(client -> Flashback.finishRecordingReplay());
            context.waitTicks(5);
        }
        try (Stream<Path> replays = Files.list(Flashback.getReplayFolder())) {
            return replays.filter(path -> path.toString().endsWith(".zip"))
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                    .orElseThrow(() -> new AssertionError("Flashback saved no replay"));
        }
    }

    private static void play(ClientGameTestContext context, Path replay, Path report) throws IOException {
        // From the client's own task queue: inside a GameTest task the framework defers world loading until the
        // task ends, after Flashback's replay server settings for that load are gone, and a plain server starts
        context.runOnClient(client -> client.schedule(() -> Flashback.openReplayWorld(replay)));
        waitStep(context, report, "the replay server", client -> Flashback.getReplayServer() != null);
        waitStep(context, report, "the replay server's first tick", client -> Flashback.getReplayServer().isReady());
        waitStep(context, report, "the replay viewer in the world", client -> client.player != null && client.level != null);
        int total = context.computeOnClient(client -> Flashback.getReplayServer().getTotalReplayTicks());
        context.runOnClient(client -> Flashback.getReplayServer().replayPaused = false);
        context.waitTicks(60);
        int played = context.computeOnClient(client -> Flashback.getReplayServer().getReplayTick());
        context.runOnClient(client -> Flashback.getReplayServer().goToReplayTick(total));
        waitStep(context, report, "the replay's last tick", client -> Flashback.getReplayServer() != null
                && Flashback.getReplayServer().getReplayTick() >= total);
        boolean inReplay = context.computeOnClient(client -> Flashback.isInReplay());
        int end = context.computeOnClient(client -> Flashback.getReplayServer().getReplayTick());
        Path screenshot = context.takeScreenshot(report.getFileName().toString().replace(".txt", ""));
        Files.writeString(report, "replay " + replay + "\ntotal ticks " + total + "\ntick after 60 played ticks "
                + played + "\ntick after jumping to the end " + end + "\nin replay " + inReplay + "\nscreenshot "
                + screenshot + "\n");
        if (played < 20 || end < total || !inReplay) {
            throw new AssertionError("Playback stalled: " + Files.readString(report));
        }
        // Leave as Flashback's own "Exit replay" does, also from the client's task queue (see openReplayWorld above)
        context.runOnClient(client -> client.schedule(() -> {
            if (client.level != null) {
                client.level.disconnect(Component.empty());
            }
            client.disconnectWithProgressScreen();
            client.gui.setScreen(new TitleScreen());
        }));
        waitStep(context, report, "the title screen", client -> client.level == null && !Flashback.isInReplay());
    }

    /** Waits for {@code done}; on a timeout, adds what the client and the replay server were doing to the report. */
    private static void waitStep(ClientGameTestContext context, Path report, String step, Predicate<Minecraft> done)
            throws IOException {
        try {
            context.waitFor(done, TIMEOUT_TICKS);
        } catch (AssertionError timeout) {
            String state = context.computeOnClient(VerifyGameTest::describe);
            String screenshot;
            try {
                screenshot = context.takeScreenshot("timeout").toString();
            } catch (RuntimeException | AssertionError e) {
                screenshot = "failed: " + e;
            }
            String text = "timed out waiting for " + step + "\n" + state + "screenshot " + screenshot + "\n";
            Files.writeString(report, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            throw new AssertionError(text, timeout);
        }
    }

    private static String describe(Minecraft client) {
        ReplayServer server = Flashback.getReplayServer();
        return "screen " + (client.gui.screen() == null ? "none" : client.gui.screen().getClass().getName())
                + "\nclient paused " + client.isPaused()
                + "\nsingleplayer server " + client.getSingleplayerServer()
                + "\nreplay server " + (server == null ? "none" : "ready " + server.isReady() + ", running "
                        + server.isRunning() + ", server ticks " + server.getTickCount() + ", replay tick "
                        + server.getReplayTick() + " of " + server.getTotalReplayTicks() + ", replay paused "
                        + server.replayPaused)
                + "\nplayer " + client.player + "\nlevel " + client.level + "\nin replay " + Flashback.isInReplay()
                + "\n";
    }
}
