package com.asdflj.wtct.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import com.asdflj.wtct.common.Config;
import com.asdflj.wtct.coremod.HandOver;
import com.asdflj.wtct.inventory.item.IItemInventory;
import com.asdflj.wtct.util.BaublesUtil;
import com.asdflj.wtct.util.Util;

import appeng.api.storage.ITerminalHost;

/**
 * Opens the comprehensive work terminal's screen before the server answers.
 *
 * <p>
 * The measured open path spends most of its wall time waiting: the client sends the open packet, the
 * server validates the grid and builds the container, the window packet travels back, and only then the
 * screen is constructed. None of that work is avoided here - the screen is built from a container created
 * on the client (the same host resolution the server uses), shown immediately, and then rebound when the
 * real container arrives. Input is swallowed in the meantime, because the temporary container carries a
 * window id the server does not know.
 *
 * <p>
 * Anything that can go wrong (no terminal carried, out of range, no encryption key) simply leaves the
 * screen closed: the normal open path still runs and reports the problem the way it always did.
 */
public final class OptimisticTerminalOpen {

    private OptimisticTerminalOpen() {}

    /** Shows the terminal screen right away when the player carries a usable terminal. */
    public static void tryOpen() {
        if (!Config.optimisticTerminalOpen) {
            return;
        }
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) {
            return;
        }
        final EntityClientPlayerMP player = mc.thePlayer;
        final int coordinate = Util.findComprehensiveWorkTerminal(player);
        if (coordinate < 0) {
            return;
        }
        tryOpenStack(player, coordinate);
    }

    /**
     * Shows the screen for one known terminal slot.
     *
     * @param coordinate bridge-encoded inventory coordinate, the same value the server resolves
     */
    public static void tryOpenStack(final EntityClientPlayerMP player, final int coordinate) {
        if (!Config.optimisticTerminalOpen) {
            return;
        }
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || player == null || mc.theWorld == null || mc.currentScreen != null) {
            return;
        }
        final ItemStack stack = BaublesUtil.getStackFromBridge(player, coordinate);
        if (stack == null || !(stack.getItem() instanceof IItemInventory)) {
            return;
        }
        try {
            final Object host = ((IItemInventory) stack.getItem())
                .getInventory(stack, mc.theWorld, coordinate, 0, 0, player);
            if (!(host instanceof ITerminalHost terminalHost)) {
                return;
            }
            final GuiComprehensiveWorkTerminal gui = new GuiComprehensiveWorkTerminal(player.inventory, terminalHost);
            gui.setAwaitingContainer();
            mc.displayGuiScreen(gui);
        } catch (final Throwable ignored) {
            // The client-side host could not be built (out of range, no key, ...): stay quiet and let the
            // server's own open path decide and report.
        }
    }

    /**
     * Hands the server's container to the stand-in screen that is already on display.
     *
     * <p>
     * FML builds a fresh screen from the window packet through {@code IGuiHandler}. That screen and its
     * container are correct, but showing it would close the stand-in screen and run {@code initGui()}
     * again - 39–57 ms of widget construction and a full re-sort for a screen that is already drawn.
     * Instead the fresh container is moved into the existing screen, which returns to FML so that
     * {@code displayGuiScreen} is called with it; the mixin on that method sees the pending hand-over
     * and lets it finish without rebuilding anything.
     *
     * @param freshGui the screen FML just built for the server's window
     * @return the screen to display - the stand-in one when the hand-over happened
     */
    public static Object handOver(final Object freshGui) {
        if (!(freshGui instanceof GuiComprehensiveWorkTerminal built)) {
            return freshGui;
        }
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return freshGui;
        }
        final GuiScreen current = mc.currentScreen;
        if (!(current instanceof GuiComprehensiveWorkTerminal standIn) || standIn == built) {
            return freshGui;
        }
        if (!standIn.isAwaitingContainer()) {
            return freshGui;
        }
        standIn.rebindContainer(built.inventorySlots);
        // The mixin that drops the redundant displayGuiScreen call runs on a Minecraft loaded before mods,
        // so it only knows this slot; the completion is armed here, where the mod classes are at hand.
        HandOver.arm(standIn, () -> {
            standIn.finishHandOver();
            if (mc.thePlayer != null) {
                mc.thePlayer.openContainer = standIn.inventorySlots;
            }
        });
        return standIn;
    }
}
