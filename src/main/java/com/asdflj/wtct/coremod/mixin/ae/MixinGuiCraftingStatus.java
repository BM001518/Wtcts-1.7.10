package com.asdflj.wtct.coremod.mixin.ae;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.asdflj.wtct.client.gui.CraftingStatusTabBlend;
import com.asdflj.wtct.inventory.InventoryHandler;
import com.asdflj.wtct.inventory.gui.GuiType;
import com.asdflj.wtct.inventory.item.WirelessDualInterfaceTerminalInventory;
import com.asdflj.wtct.util.Ae2ReflectClient;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.implementations.GuiCraftingStatus;
import appeng.container.implementations.ContainerCraftingStatus;

/**
 * Keeps the two customisations this mod needs on AE2's crafting-status page, now that the page is opened as
 * AE2's own {@code GuiCraftingStatus} instead of a subclass.
 *
 * <p>
 * The subclass approach was what the crafting status screen used before, and it silently disabled the ECO
 * (NeoEcoAE) crafting-status page: {@code ECOCraftingStatusClient#open} only swaps in
 * {@code ECOCraftingStatusGui} when {@code event.gui.getClass() == GuiCraftingStatus.class} - an exact class
 * check - so a WTCT subclass kept its own screen and the ECO cpu panel never appeared. The screen therefore
 * has to be the plain AE2 class, and these two behaviours have to follow it to a mixin so they also run on the
 * ECO page (which extends {@code GuiCraftingStatus} and therefore inherits this injection).
 *
 * <p>
 * The first behaviour is the "back to terminal" tab. AE2 answers it with {@code PacketSwitchGuis()} (no gui),
 * which reopens whatever the container's {@code PrimaryGui} points at; for the terminals of this mod that is
 * an item hosted inventory, possibly in a Baubles slot, which the {@code PrimaryGui} cannot address. Opening
 * the comprehensive work terminal through {@link InventoryHandler#switchGui} instead is what the previous
 * subclass did, and the host class check below is exactly the condition it used.
 *
 * <p>
 * The second is the GL blend wrap for the tab plate; see {@link CraftingStatusTabBlend}.
 */
@Mixin(GuiCraftingStatus.class)
public abstract class MixinGuiCraftingStatus extends AEBaseGui {

    @Unique
    private boolean wtct$blendWasEnabled;

    public MixinGuiCraftingStatus(final Container container) {
        super(container);
    }

    /**
     * Both the comprehensive work terminal and the wireless dual-interface terminal host their inventory
     * through {@link WirelessDualInterfaceTerminalInventory}, so this one check covers every screen of that
     * family; anything else (a terminal mounted on a block, for example) keeps AE2's own handling.
     */
    @Inject(method = "actionPerformed", at = @At(value = "HEAD"), cancellable = true)
    private void wtct$backToComprehensiveTerminal(final GuiButton btn, final CallbackInfo ci) {
        final GuiCraftingStatus self = (GuiCraftingStatus) (Object) this;
        if (btn == null || btn != Ae2ReflectClient.getOriginalGuiButton(self)) {
            return;
        }
        if (!(this.inventorySlots instanceof final ContainerCraftingStatus status)) {
            return;
        }
        if (!(status.getTarget() instanceof WirelessDualInterfaceTerminalInventory)) {
            return;
        }
        InventoryHandler.switchGui(GuiType.COMPREHENSIVE_WORK_TERMINAL);
        ci.cancel();
    }

    @Inject(method = "drawScreen", at = @At(value = "HEAD"))
    private void wtct$blendOn(final int mouseX, final int mouseY, final float partialTicks, final CallbackInfo ci) {
        this.wtct$blendWasEnabled = CraftingStatusTabBlend.enable();
    }

    @Inject(method = "drawScreen", at = @At(value = "RETURN"))
    private void wtct$blendRestore(final int mouseX, final int mouseY, final float partialTicks,
        final CallbackInfo ci) {
        CraftingStatusTabBlend.restore(this.wtct$blendWasEnabled);
    }
}
