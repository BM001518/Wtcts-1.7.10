package com.asdflj.wtct.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;

/**
 * One line of text right above the hotbar, the same slot vanilla uses for the "now playing" line.
 *
 * <p>
 * Used for feedback that has to answer a key press in the same frame. The mod's own notification cards in the
 * top right corner are queued and animated, which is the right trade for events the player has time to read, but
 * for a toggle you may hammer (the magnet card mode) it puts a visible delay between the press and the answer.
 * This overlay is drawn by {@link GuiIngame} itself, so it costs nothing and is replaced instantly by the next call.
 */
public final class HotbarHint {

    private HotbarHint() {}

    /** Shows {@code text} above the hotbar, replacing whatever hint was there. */
    public static void show(final String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        final Minecraft mc = Minecraft.getMinecraft();
        final GuiIngame gui = mc == null ? null : mc.ingameGUI;
        if (gui == null) {
            return;
        }
        // Second argument is the "record playing" flag: true would draw the note item next to the line.
        gui.func_110326_a(text, false);
    }
}
