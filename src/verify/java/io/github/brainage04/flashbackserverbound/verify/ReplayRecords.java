package io.github.brainage04.flashbackserverbound.verify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;

/**
 * Reads a Flashback replay's chunk files on its own (not through this mod's code) and tallies the serverbound
 * records: per kind, packets per state and id, the input flags, slots, clicks and turns seen, and where the records
 * sit relative to Flashback's ticks.
 */
final class ReplayRecords {
    static final String ACTION = "flashback_serverbound:action/serverbound_optional";
    private static final String NEXT_TICK = "flashback:action/next_tick";
    private static final int MAGIC = 0xD780E884;

    int chunks;
    int ticks;
    int headers;
    String header = "";
    int tickStarts;
    int inputs;
    int packets;
    int unknownKinds;
    int inSnapshots;
    int blocksWithTwoInputs;
    int inputFlags;
    final Map<Integer, Integer> slots = new TreeMap<>();
    int attackClicks;
    int useClicks;
    double turnYaw;
    double turnPitch;
    final Map<String, Integer> packetIds = new TreeMap<>();

    static ReplayRecords read(Path replay) throws IOException {
        ReplayRecords records = new ReplayRecords();
        try (ZipFile zip = new ZipFile(replay.toFile())) {
            JsonObject meta = JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("metadata.json"))
                    .readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (String chunk : meta.getAsJsonObject("chunks").keySet()) {
                records.chunk(ByteBuffer.wrap(zip.getInputStream(zip.getEntry(chunk)).readAllBytes()));
            }
        }
        return records;
    }

    private void chunk(ByteBuffer buf) {
        chunks++;
        if (buf.getInt() != MAGIC) {
            throw new AssertionError("not a Flashback chunk");
        }
        int count = varInt(buf);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            byte[] name = new byte[varInt(buf)];
            buf.get(name);
            names.add(new String(name, StandardCharsets.UTF_8));
        }
        int snapshotEnd = buf.getInt() + buf.position();
        int ours = names.indexOf(ACTION);
        int nextTick = names.indexOf(NEXT_TICK);
        int inputsInBlock = 0;
        while (buf.hasRemaining()) {
            boolean snapshot = buf.position() < snapshotEnd;
            int id = varInt(buf);
            int size = buf.getInt();
            int end = buf.position() + size;
            if (id == nextTick && !snapshot) {
                ticks++;
                inputsInBlock = 0;
            } else if (id == ours) {
                if (snapshot) {
                    inSnapshots++;
                }
                if (record(buf.slice(buf.position(), size))) {
                    inputsInBlock++;
                    if (inputsInBlock == 2) {
                        blocksWithTwoInputs++;
                    }
                }
            }
            buf.position(end);
        }
    }

    /** True for an INPUT record. */
    private boolean record(ByteBuffer record) {
        switch (record.get()) {
            case 0 -> {
                headers++;
                int version = record.get();
                int protocol = varInt(record);
                byte[] generator = new byte[varInt(record)];
                record.get(generator);
                header = "layout " + version + ", protocol " + protocol + ", generator "
                        + new String(generator, StandardCharsets.UTF_8);
            }
            case 1 -> {
                packets++;
                int state = record.get();
                int id = varInt(record);
                packetIds.merge((state == 0 ? "play" : state == 1 ? "configuration" : "login") + " 0x"
                        + Integer.toHexString(id), 1, Integer::sum);
            }
            case 2 -> {
                inputs++;
                inputFlags |= record.getInt();
                slots.merge(record.get() & 0xFF, 1, Integer::sum);
                attackClicks += record.get() & 0xFF;
                useClicks += record.get() & 0xFF;
                record.position(record.position() + 16);
                turnYaw += Math.abs(record.getFloat());
                turnPitch += Math.abs(record.getFloat());
                return true;
            }
            case 3 -> tickStarts++;
            default -> unknownKinds++;
        }
        return false;
    }

    /** The records a recorded session must have produced. */
    void check() {
        List<String> missing = new ArrayList<>();
        if (headers != 1) {
            missing.add("one header");
        }
        if (inputs < 100 || tickStarts < 100 || inputs > ticks) {
            missing.add("an input and a tick start per tick");
        }
        if (packets < 100) {
            missing.add("packets");
        }
        if (inSnapshots != 0 || blocksWithTwoInputs != 0 || unknownKinds != 0) {
            missing.add("records only in ticks, at most one input per tick");
        }
        int keys = 1 | 1 << 2 | 1 << 4 | 1 << 5 | 1 << 6;   // forward, left, jump, sneak, sprint keys
        if ((inputFlags & keys) != keys) {
            missing.add("the keys pressed (flags 0x" + Integer.toHexString(inputFlags) + ")");
        }
        if (attackClicks < 3 || useClicks < 1 || !slots.containsKey(3)) {
            missing.add("the clicks and the hotbar slot");
        }
        if (!missing.isEmpty()) {
            throw new AssertionError("Missing " + String.join("; ", missing) + "\n" + this);
        }
    }

    @Override
    public String toString() {
        return "chunks " + chunks + "\nticks " + ticks + "\nheader " + header + " (" + headers + ")\ntick starts "
                + tickStarts + "\ninputs " + inputs + "\npackets " + packets + "\nunknown kinds " + unknownKinds
                + "\nrecords in snapshots " + inSnapshots + "\nticks with two inputs " + blocksWithTwoInputs
                + "\ninput flags seen 0x" + Integer.toHexString(inputFlags) + "\nslots " + slots
                + "\nattack clicks " + attackClicks + "\nuse clicks " + useClicks + "\nturn |yaw| " + turnYaw
                + "\nturn |pitch| " + turnPitch + "\npackets by id " + packetIds + "\n";
    }

    private static int varInt(ByteBuffer buf) {
        int value = 0;
        int shift = 0;
        byte b;
        do {
            b = buf.get();
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }
}
