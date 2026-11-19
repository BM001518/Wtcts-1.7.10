package com.asdflj.wtct.loader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.asdflj.wtct.Wtct;
import com.asdflj.wtct.api.InventoryActionExtend;
import com.asdflj.wtct.api.WtctAPI;
import com.asdflj.wtct.client.render.HotbarHint;
import com.asdflj.wtct.common.item.card.CardTicker;
import com.asdflj.wtct.network.CPacketCycleMagnetMode;
import com.asdflj.wtct.network.CPacketInventoryActionExtend;
import com.asdflj.wtct.util.BaublesUtil;
import com.asdflj.wtct.util.BlockPos;
import com.asdflj.wtct.util.Util;

import appeng.util.item.AEItemStack;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class KeybindLoader implements Runnable {

    public static KeyBinding openTerminalMenu;
    public static KeyBinding openComprehensiveWorkTerminal;
    /** Cycles the magnet mode of the carried terminal, so the card does not need its screen to be opened. */
    public static KeyBinding cycleMagnetMode;
    /** The bindings' state on the previous input event, so only a fresh press fires - see onKeyInput. */
    private boolean menuWasDown;
    private boolean terminalWasDown;
    private boolean magnetWasDown;

    @Override
    public void run() {
        // The backpack terminal and the dual interface terminal are no longer part of this mod, so neither is
        // their keybinding; only the terminal menu, the comprehensive terminal itself and the magnet mode remain.
        openTerminalMenu = new KeyBinding(
            Wtct.MODID + ".key.open_terminal",
            Keyboard.CHAR_NONE,
            "itemGroup." + Wtct.MODID);
        ClientRegistry.registerKeyBinding(openTerminalMenu);
        openComprehensiveWorkTerminal = new KeyBinding(
            Wtct.MODID + ".key.open_comprehensive_work_terminal",
            Keyboard.CHAR_NONE,
            "itemGroup." + Wtct.MODID);
        ClientRegistry.registerKeyBinding(openComprehensiveWorkTerminal);
        cycleMagnetMode = new KeyBinding(
            Wtct.MODID + ".key.cycle_magnet_mode",
            Keyboard.CHAR_NONE,
            "itemGroup." + Wtct.MODID);
        ClientRegistry.registerKeyBinding(cycleMagnetMode);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent event) {
        if (Minecraft.getMinecraft().currentScreen != null) return;
        EntityClientPlayerMP p = Minecraft.getMinecraft().thePlayer;
        // middle click
        // The gesture asks the network for the block under the crosshair, and no longer insists on an
        // empty hand: carrying something used to make the click a silent no-op, so the block you were
        // looking at could not be ordered - nor offered for crafting - unless you first emptied your
        // hand. What the request actually needs is somewhere to put the stack, which is what
        // findReceivingSlot answers: the hand when it is free (unchanged for a player who is not
        // holding anything), otherwise the first empty slot. A full inventory still has nowhere to put
        // it, and the click stays a no-op there.
        if (Mouse.isButtonDown(2) && !p.capabilities.isCreativeMode) {
            // request item
            ItemStack block = getTargetBlock(p.getEntityWorld(), p);
            if (block != null && Util.findItemStack(p, block) == -1) {
                int slot = Util.findReceivingSlot(p);
                if (slot >= 0) {
                    Wtct.proxy.netHandler.sendToServer(
                        new CPacketInventoryActionExtend(
                            InventoryActionExtend.REQUEST_ITEM,
                            slot,
                            0,
                            AEItemStack.create(block)));
                }
            }
            return;
        }
        if (!(event instanceof InputEvent.KeyInputEvent) && !(event instanceof InputEvent.MouseInputEvent)) return;
        if (p.openContainer == null) {
            return;
        }
        // Edge-triggered on purpose: 1.7.10 reports a binding as pressed for as long as it is held, so a
        // key (or mouse button) that never came back up asked for a new terminal every tick. Each press
        // is remembered here and only the transition fires the request.
        final boolean menuDown = openTerminalMenu.isPressed();
        if (menuDown && !this.menuWasDown) {
            WtctAPI.instance()
                .openTerminalMenu();
        }
        this.menuWasDown = menuDown;
        final boolean terminalDown = openComprehensiveWorkTerminal.isPressed();
        if (terminalDown && !this.terminalWasDown) {
            WtctAPI.instance()
                .openComprehensiveWorkTerminal();
        }
        this.terminalWasDown = terminalDown;
        final boolean magnetDown = cycleMagnetMode.isPressed();
        if (magnetDown && !this.magnetWasDown) {
            cycleMagnetMode(p);
        }
        this.magnetWasDown = magnetDown;

    }

    /**
     * Cycles the magnet card's mode on the terminal the player carries.
     *
     * <p>
     * The mode sits on the terminal's NBT, so the server owns the write; this side flips its own copy first so the
     * tooltip and the hint answer the key immediately, and the server's write lands on the same value because it
     * starts from the same old mode. Without that local flip a terminal worn in a Baubles slot would keep showing the
     * previous mode until the next container sync, which is exactly the delay the magnet screen's own button avoids
     * the same way. The answer is a hotbar hint rather than a notification card: the card queue animates, and this key
     * is meant to be tapped repeatedly until the wanted mode comes up.
     */
    private static void cycleMagnetMode(final EntityClientPlayerMP player) {
        final int slot = Util.findMagnetTerminal(player);
        if (slot < 0) {
            return;
        }
        final ItemStack terminal = BaublesUtil.getStackFromBridge(player, slot);
        if (terminal == null) {
            return;
        }
        final CardTicker.MagnetMode mode = CardTicker.MagnetMode.of(terminal)
            .next();
        CardTicker.MagnetMode.set(terminal, mode);
        HotbarHint.show(I18n.format("wtct.magnet.cycle.hint", I18n.format(modeTranslationKey(mode))));
        Wtct.proxy.netHandler.sendToServer(new CPacketCycleMagnetMode(slot));
    }

    private static String modeTranslationKey(final CardTicker.MagnetMode mode) {
        switch (mode) {
            case INV:
                return "wtct.card.magnet.mode.inv";
            case ME:
                return "wtct.card.magnet.mode.me";
            default:
                return "wtct.card.magnet.mode.off";
        }
    }

    private static ItemStack getTargetBlock(World world, EntityPlayer player) {
        Vec3 position = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLookVec();
        Vec3 end = position.addVector(look.xCoord * 5.0, look.yCoord * 5.0, look.zCoord * 5.0);
        MovingObjectPosition hit = world.rayTraceBlocks(position, end);

        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return new BlockPos(hit, world).getPickBlock(hit, world, player);
        }
        return null;
    }
}
