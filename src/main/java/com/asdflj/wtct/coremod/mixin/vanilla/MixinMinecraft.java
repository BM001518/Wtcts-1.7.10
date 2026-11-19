package com.asdflj.wtct.coremod.mixin.vanilla;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.asdflj.wtct.coremod.HandOver;

/**
 * Keeps the optimistic terminal screen instead of rebuilding it when the server's window arrives.
 *
 * <p>
 * FML handles the open-window packet by building a client GUI through {@code IGuiHandler} and handing
 * it to {@code Minecraft.displayGuiScreen}, which first closes the screen currently on show and then
 * runs {@code initGui()} on the new one. The comprehensive work terminal builds ~200 widgets and sorts
 * the whole item list in there (39–57 ms measured on a large network), and the optimistic screen that
 * is already on display is the same screen: same class, same size, same widgets, and its container has
 * just been replaced by the server's own (see {@code OptimisticTerminalOpen.handOver}).
 *
 * <p>
 * So when {@code displayGuiScreen} is about to show the screen that is already current, the mod arms
 * {@link HandOver} with that screen and the completion that finishes the hand-over; this injection then
 * runs it and drops the call. Anything else - a different screen, nothing armed - is left to vanilla.
 *
 * <p>
 * {@code Minecraft} is loaded long before mods are constructed, so this mixin has to be part of the
 * early config; naming a mod class here would make the early class loader resolve it while the game is
 * still starting. Everything mod-specific therefore travels through {@link HandOver}.
 *
 * <p>
 * Forge patches {@code displayGuiScreen} to point {@code thePlayer.openContainer} at the new screen's
 * container, and FML assigns the window id through {@code thePlayer.openContainer} right after the
 * open-window packet has displayed the screen. Cancelling the call skips that patch, so the armed
 * completion does the assignment by hand on the container that was just swapped in. The screen and the
 * container stay the same objects, so the window id FML writes afterwards lands on the right one.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Inject(method = "displayGuiScreen", at = @At("HEAD"), cancellable = true)
    private void wtct$keepOptimisticTerminal(final GuiScreen guiScreen, final CallbackInfo ci) {
        if (!HandOver.matches(guiScreen)) {
            return;
        }
        final Minecraft mc = Minecraft.getMinecraft();
        // Only a call that would replace this very screen is safe to drop: if anything else is on show
        // (a crash screen, another mod swapping screens in the same tick) the hand-over still has to be
        // completed, but the screen must be shown as asked.
        final boolean replacingThisScreen = mc.currentScreen == guiScreen;
        HandOver.fire();
        if (replacingThisScreen) {
            ci.cancel();
        }
    }
}
