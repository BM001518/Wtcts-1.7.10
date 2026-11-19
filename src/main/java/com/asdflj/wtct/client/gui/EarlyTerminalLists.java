package com.asdflj.wtct.client.gui;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.asdflj.wtct.common.Config;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.IInterfaceTerminalPostUpdate;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate;

/**
 * Holds the terminal's list packets that arrive while the window is still opening.
 *
 * <p>
 * Every list a terminal needs - the network's items, its fluids, the pattern providers - is pushed by
 * the server the moment the container opens, and each push is gated on
 * {@code Minecraft.currentScreen} already being the terminal. The screen swap happens on the client
 * thread while these packets are applied on the network thread, so a push that loses that race used to
 * be dropped outright: the terminal opened with an empty grid and stayed that way until something in
 * the network happened to move (a few tenths of a second later).
 *
 * <p>
 * Instead of dropping, the handlers now park the late arrivals here; {@link #applyTo} is called from
 * the GUI's {@code initGui} - at which point the screen is guaranteed to be the terminal - so the very
 * first frame already draws the full lists. A stash is only trusted for a moment ({@link #MAX_AGE_MS});
 * anything older is garbage from an abandoned window and is discarded rather than shown.
 *
 * <p>
 * The optimistic open puts a screen up before the server has answered, so there is a short window with
 * no data at all; {@link #remember} closes it by keeping what the last terminal was showing. That copy
 * is only ever painted on the optimistic screen, which is thrown away when the real window arrives.
 *
 * <p>
 * Written from the network thread, read from the client thread - hence the synchronization.
 */
public final class EarlyTerminalLists {

    /** A stash older than this no longer belongs to a window that is about to open. */
    private static final long MAX_AGE_MS = 2000L;

    private static long s_stashedAt;
    /**
     * The item and fluid lists are pushed as two separate packets; they must not share one slot, or
     * whichever arrives second overwrites the first and the grid comes up missing half of its
     * contents - the rest then trickles in a moment later and re-sorts everything under the player.
     */
    @Nullable
    private static List<? extends IAEStack<?>> s_stacks;
    @Nullable
    private static List<? extends IAEStack<?>> s_fluidStacks;
    /** Essentia is pushed by its own packet as well, so it needs its own slot for the same reason. */
    @Nullable
    private static List<? extends IAEStack<?>> s_essentiaStacks;
    private static boolean s_hasPlayerItem;
    @Nullable
    private static ItemStack s_playerItem;
    @Nullable
    private static List<PacketInterfaceTerminalUpdate.PacketEntry> s_interfaceCommands;
    private static int s_interfaceFlags;
    private static boolean s_hasInterfaceUpdate;
    /**
     * What the terminal last had on display, kept across closes.
     *
     * <p>
     * The optimistic screen is on screen before the server has answered (that is the whole point of
     * it), so without this it would draw an empty grid for the first ~150 ms. Painting the previous
     * contents instead - then letting the real lists overwrite them a moment later - is what makes
     * the open feel instant. Distinct from the stashes above: this one survives {@link #applyTo} and
     * is only replaced by the next {@link #remember}.
     */
    @Nullable
    private static List<IAEStack<?>> s_lastStacks;
    /**
     * The same contents in the order the terminal was showing them.
     *
     * <p>
     * Rebuilding that order costs a full sort (~17 ms on a large network) - acceptable once, when the
     * real lists arrive, but far too much for the first frame of a screen that is only standing in for
     * the server's answer. Keeping the order means the optimistic screen can paint immediately.
     */
    @Nullable
    private static List<IAEStack<?>> s_lastView;

    private EarlyTerminalLists() {}

    /** Parks the item list (the carriers the terminal merges into its repo). */
    public static synchronized void stashStacks(final List<? extends IAEStack<?>> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        s_stacks = list;
        touch();
    }

    /** Parks the fluid list; it is pushed by its own packet and must not clobber the item stash. */
    public static synchronized void stashFluidStacks(final List<? extends IAEStack<?>> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        s_fluidStacks = list;
        touch();
    }

    /** Parks the essentia list; it has its own packet and must not clobber the other two stashes. */
    public static synchronized void stashEssentiaStacks(final List<? extends IAEStack<?>> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        s_essentiaStacks = list;
        touch();
    }

    /** Parks the single "this is what the player is holding" stack; {@code null} clears it too. */
    public static synchronized void stashPlayerItem(final ItemStack is) {
        s_playerItem = is;
        s_hasPlayerItem = true;
        touch();
    }

    /** Parks AE2's pattern-provider list so the management area is filled on the first frame. */
    public static synchronized void stashInterfaceUpdate(final List<PacketInterfaceTerminalUpdate.PacketEntry> commands,
        final int statusFlags) {
        s_interfaceCommands = commands;
        s_interfaceFlags = statusFlags;
        s_hasInterfaceUpdate = true;
        touch();
    }

    /**
     * Keeps the list a terminal was showing when it closed, so the next optimistic open can paint it
     * immediately - both the contents and the order they were in. Called from {@code onGuiClosed}; an
     * empty list is ignored, because an empty terminal must not erase a cache a different one filled.
     */
    public static synchronized void remember(final Iterable<IAEStack<?>> list,
        final List<? extends IAEStack<?>> sortedView) {
        if (list == null) {
            return;
        }
        final List<IAEStack<?>> copy = new ArrayList<>();
        for (final IAEStack<?> stack : list) {
            if (stack != null) {
                copy.add(stack);
            }
        }
        if (copy.isEmpty()) {
            return;
        }
        s_lastStacks = copy;
        s_lastView = sortedView == null || sortedView.isEmpty() ? null : new ArrayList<>(sortedView);
    }

    /**
     * Applies everything a terminal's {@code initGui} finds stashed, then empties the holder. Only a
     * fresh stash is applied; a stale one is dropped without touching the GUI.
     */
    public static synchronized void applyTo(final IGuiMonitorTerminal gui) {
        final long now = System.currentTimeMillis();
        final boolean fresh = s_stashedAt != 0 && now - s_stashedAt <= MAX_AGE_MS;
        // Cached contents first: the optimistic open has nothing else to draw for the first frames and
        // a terminal that opens after a close should not show an empty grid while the push travels.
        // The snapshot carries its own order, so paint it without sorting; whatever the server is about
        // to send merges on top of it in postUpdate (same keys win), so a stale entry can only survive
        // for the moment it takes that push to arrive.
        if (Config.optimisticTerminalOpen && s_lastStacks != null
            && gui instanceof final GuiComprehensiveWorkTerminal optimistic
            && optimistic.isAwaitingContainer()) {
            gui.restoreCachedSnapshot(s_lastStacks, s_lastView != null ? s_lastView : s_lastStacks);
        }
        if (fresh) {
            // One pass for all three lists: they share a repo, so applying them separately rebuilt and
            // re-sorted the whole view once per list (three sorts before the first frame could draw).
            final List<IAEStack<?>> merged = new ArrayList<>();
            if (s_stacks != null) {
                merged.addAll(s_stacks);
            }
            if (s_fluidStacks != null) {
                merged.addAll(s_fluidStacks);
            }
            if (s_essentiaStacks != null) {
                merged.addAll(s_essentiaStacks);
            }
            if (!merged.isEmpty()) {
                gui.postStackUpdateBatched(merged);
            }
            if (s_hasPlayerItem) {
                gui.setPlayerInv(s_playerItem);
            }
            if (s_hasInterfaceUpdate && gui instanceof final IInterfaceTerminalPostUpdate post) {
                post.postUpdate(s_interfaceCommands, s_interfaceFlags);
            }
        }
        clear();
    }

    private static void touch() {
        s_stashedAt = System.currentTimeMillis();
    }

    private static void clear() {
        s_stacks = null;
        s_fluidStacks = null;
        s_essentiaStacks = null;
        s_playerItem = null;
        s_hasPlayerItem = false;
        s_interfaceCommands = null;
        s_hasInterfaceUpdate = false;
        s_stashedAt = 0;
    }
}
