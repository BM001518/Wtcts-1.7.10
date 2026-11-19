package com.asdflj.wtct.network;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.asdflj.wtct.common.item.card.CardTicker;
import com.asdflj.wtct.common.item.card.ItemWcwtUpgradeCard;
import com.asdflj.wtct.util.BaublesUtil;
import com.asdflj.wtct.util.Util;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * The magnet keybinding's server half: flips the magnet mode on the terminal the player carries.
 *
 * <p>
 * The mode is stored on the terminal's NBT (see {@link CardTicker.MagnetMode}), so only the server can change it -
 * the client's copy is thrown away at the next slot sync. The packet carries the bridge-encoded coordinate the client
 * resolved so both sides flip the very same stack even when the terminal sits in a Baubles slot, with {@code -1}
 * meaning "look it up again" for a client that could not name one.
 *
 * <p>
 * The client flips its own copy before sending, so the tooltip and the toast answer the key immediately; the server
 * starts from the same old mode, so its write lands on the same value instead of drifting a step ahead.
 */
public class CPacketCycleMagnetMode implements IMessage {

    /** Bridge-encoded inventory coordinate of the terminal, or -1 to let the server find it. */
    private int slot = -1;

    public CPacketCycleMagnetMode() {}

    public CPacketCycleMagnetMode(final int slot) {
        this.slot = slot;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.slot = buf.readInt();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.slot);
    }

    public static class Handler implements IMessageHandler<CPacketCycleMagnetMode, IMessage> {

        @Nullable
        @Override
        public IMessage onMessage(final CPacketCycleMagnetMode message, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            final int slot = message.slot >= 0 ? message.slot : Util.findMagnetTerminal(player);
            if (slot < 0) {
                return null;
            }
            final ItemStack terminal = BaublesUtil.getStackFromBridge(player, slot);
            if (terminal == null || !ItemWcwtUpgradeCard.isInstalled(terminal, ItemWcwtUpgradeCard.Kind.MAGNET)) {
                // The client named a slot that no longer holds a magnet terminal (the stack moved between the
                // key being pressed and the packet arriving), so there is nothing to flip.
                return null;
            }
            CardTicker.MagnetMode.set(
                terminal,
                CardTicker.MagnetMode.of(terminal)
                    .next());
            // Writing the stack back is what makes the new mode visible: main-inventory slots are re-synced by
            // the player's own container every tick, while Baubles slots have to be pushed to the client by hand.
            BaublesUtil.setStackFromBridge(player, slot, terminal);
            player.inventory.markDirty();
            return null;
        }
    }
}
