package com.asdflj.wtct.coremod;

import javax.annotation.Nullable;

/**
 * One-shot slot between the mod code and the {@code Minecraft.displayGuiScreen} mixin.
 *
 * <p>
 * The mixin targets {@code net.minecraft.client.Minecraft}, a class the game loads before any mod is
 * constructed, so it lives in the early mixin config. Early mixin classes are prepared while the game
 * is still starting, so they must not name a mod class in a way that would drag that class (and its
 * static initialisers) into the class loader too soon. This holder is that indirection: it only ever
 * touches {@link Object} and {@link Runnable}, and it owns nothing but the identity of the screen that
 * is waiting for its container and the runnable that finishes the hand-over.
 *
 * <p>
 * The mod arms it right before returning the already-shown screen to FML, so the very next
 * {@code displayGuiScreen} call with that screen is the one to keep; every other call, and the call
 * after the hand-over finished, is left to vanilla.
 */
public final class HandOver {

    @Nullable
    private static Object armedScreen;
    @Nullable
    private static Runnable completion;

    private HandOver() {}

    /**
     * Remembers that {@code screen} is waiting for its container and what to do when the game is about
     * to show it again.
     */
    public static synchronized void arm(final Object screen, final Runnable onShown) {
        armedScreen = screen;
        completion = onShown;
    }

    /** Whether {@code screen} is the one armed in this slot. */
    public static synchronized boolean matches(@Nullable final Object screen) {
        return screen != null && screen == armedScreen;
    }

    /** Runs the armed completion once and empties the slot. Safe to call with nothing armed. */
    public static synchronized void fire() {
        final Runnable run = completion;
        armedScreen = null;
        completion = null;
        if (run != null) {
            run.run();
        }
    }

    /** Drops the slot when {@code screen} is closed before the hand-over ever happened. */
    public static synchronized void disarm(@Nullable final Object screen) {
        if (screen != null && screen == armedScreen) {
            armedScreen = null;
            completion = null;
        }
    }
}
