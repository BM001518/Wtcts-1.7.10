package com.asdflj.wtct.util;

import java.util.LinkedHashMap;
import java.util.Map;

import com.asdflj.wtct.common.Config;

/**
 * Stage timings for opening the comprehensive work terminal, on both sides.
 *
 * <p>
 * Off by default ({@link Config#perfTiming}). With it off {@link #begin(String)} returns 0 and
 * {@link #end(String, long)} is a no-op, so nothing is measured and nothing is logged during normal
 * play. With it on, each stage is measured and the result is written to the log as one line per side,
 * e.g. {@code [wtct-perf] server.open: provider=12.3 ctor=45.6}.
 *
 * <p>
 * In a single-player world the client and the integrated server share this class, so keys are prefixed
 * by side and {@link #log(String, String...)} only prints and clears the keys it was given.
 */
public final class OpenTimer {

    private static final Map<String, Double> MS = new LinkedHashMap<>();
    private static final Map<String, Long> WALL = new LinkedHashMap<>();
    /** Wall clock of the last "open the terminal" request, so the round trip can be derived. */
    private static long requestMs;

    private OpenTimer() {}

    /** Notes that the player asked for the terminal (client side). */
    public static void request() {
        if (Config.perfTiming) {
            requestMs = System.currentTimeMillis();
        }
    }

    /** Start of a stage; 0 when timing is off, which {@link #end(String, long)} ignores. */
    public static long begin(final String key) {
        if (!Config.perfTiming) {
            return 0L;
        }
        WALL.put(key, System.currentTimeMillis());
        return System.nanoTime();
    }

    /** Records the stage that started at {@code start}. */
    public static void end(final String key, final long start) {
        if (start == 0L) {
            return;
        }
        MS.put(key, (System.nanoTime() - start) / 1_000_000.0D);
    }

    /** Adds an already-measured number of milliseconds to {@code key}. */
    public static void add(final String key, final double millis) {
        if (!Config.perfTiming) {
            return;
        }
        MS.put(key, millis);
    }

    /** Writes the given keys as one line and forgets them. */
    public static void log(final String tag, final String... keys) {
        if (!Config.perfTiming) {
            return;
        }
        final StringBuilder line = new StringBuilder("[wtct-perf] ").append(tag);
        long wall = 0L;
        boolean any = false;
        for (final String key : keys) {
            final Long started = WALL.remove(key);
            if (started != null && (wall == 0L || started < wall)) {
                wall = started;
            }
            final Double value = MS.remove(key);
            if (value == null) {
                continue;
            }
            any = true;
            line.append(' ')
                .append(key.substring(key.indexOf('.') + 1))
                .append('=')
                .append(String.format(java.util.Locale.ROOT, "%.1f", value))
                .append("ms");
        }
        if (any) {
            // The wall clock is included so the two sides can be ordered even in a single-player world,
            // where "how long before the server even started" is the round trip worth removing.
            line.append(" @")
                .append(wall);
            if (tag.startsWith("client.") && requestMs != 0L) {
                // How long the client waited before it even had a container to build: the request was
                // sent at requestMs, so wall - requestMs is the round trip the server cost.
                line.append(" req@")
                    .append(requestMs)
                    .append(" rtt=")
                    .append(wall - requestMs)
                    .append("ms");
            }
            org.apache.logging.log4j.LogManager.getLogger("wtct")
                .info(line.toString());
        }
    }
}
