package com.asdflj.wtct.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.asdflj.wtct.client.gui.EarlyTerminalLists;
import com.asdflj.wtct.client.gui.IGuiMonitorTerminal;

import appeng.api.storage.data.IAEStack;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/**
 * Essentia updates for the comprehensive work terminal.
 *
 * <p>
 * Thaumcraft essentia is a first class {@link IAEStack} in the AE2 fork this mod builds against
 * ({@code AEEssentiaStack}), so the terminal's list is fed by exactly the same kind of packet the item
 * and fluid lists use - the base class already serialises any {@code IAEStack} type through
 * {@code IAEStack.writeToPacketGeneric}, which resolves the type through {@code AEStackTypeRegistry}.
 * The only reason this is a class of its own is that it must land in the fluid/Essentia branch of the
 * terminal's stash: a shared packet would let the three lists overwrite each other while a window is
 * still opening.
 */
public class SPacketMEEssentiaInvUpdate extends SPacketMEBaseInvUpdate implements IMessage {

    public SPacketMEEssentiaInvUpdate() {}

    public void appendEssentia(final IAEStack<?> is) {
        this.list.add(is);
    }

    public static class Handler implements IMessageHandler<SPacketMEEssentiaInvUpdate, IMessage> {

        @Override
        public IMessage onMessage(SPacketMEEssentiaInvUpdate message, MessageContext ctx) {
            final GuiScreen gs = Minecraft.getMinecraft().currentScreen;
            if (gs instanceof IGuiMonitorTerminal gpt) {
                gpt.postStackUpdate(message.list);
                // Parked for the screen that replaces this one (optimistic open), like the item list.
                EarlyTerminalLists.stashEssentiaStacks(message.list);
            } else {
                // Same race as the item and fluid lists: the essentia push that loses the window-opening
                // race is parked for the GUI's initGui instead of being dropped.
                EarlyTerminalLists.stashEssentiaStacks(message.list);
            }
            return null;
        }
    }
}
