package io.github.brainage04.flashbackserverbound.verify;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
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
    private static final int TIMEOUT_TICKS = 2400;

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
        context.runOnClient(client -> Flashback.openReplayWorld(replay));
        context.waitFor(client -> {
            ReplayServer server = Flashback.getReplayServer();
            return server != null && server.isReady() && client.player != null && client.level != null;
        }, TIMEOUT_TICKS);
        int total = context.computeOnClient(client -> Flashback.getReplayServer().getTotalReplayTicks());
        context.runOnClient(client -> Flashback.getReplayServer().replayPaused = false);
        context.waitTicks(60);
        int played = context.computeOnClient(client -> Flashback.getReplayServer().getReplayTick());
        context.runOnClient(client -> Flashback.getReplayServer().goToReplayTick(total));
        context.waitFor(client -> Flashback.getReplayServer() != null
                && Flashback.getReplayServer().getReplayTick() >= total, TIMEOUT_TICKS);
        boolean inReplay = context.computeOnClient(client -> Flashback.isInReplay());
        int end = context.computeOnClient(client -> Flashback.getReplayServer().getReplayTick());
        Path screenshot = context.takeScreenshot(report.getFileName().toString().replace(".txt", ""));
        Files.writeString(report, "replay " + replay + "\ntotal ticks " + total + "\ntick after 60 played ticks "
                + played + "\ntick after jumping to the end " + end + "\nin replay " + inReplay + "\nscreenshot "
                + screenshot + "\n");
        if (played < 20 || end < total || !inReplay) {
            throw new AssertionError("Playback stalled: " + Files.readString(report));
        }
        context.runOnClient(client -> {
            if (client.level != null) {
                client.level.disconnect(Component.empty());
            }
            client.disconnectWithProgressScreen();
            client.gui.setScreen(new TitleScreen());
        });
        context.waitFor(client -> client.level == null && !Flashback.isInReplay(), TIMEOUT_TICKS);
    }
}
