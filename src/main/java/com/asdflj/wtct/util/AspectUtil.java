package com.asdflj.wtct.util;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.common.integration.tc.EssentiaItemContainerHelper;
import thaumicenergistics.common.storage.AEEssentiaStack;
import thaumicenergistics.common.storage.AEEssentiaStackType;

/**
 * Compatibility helper that replaces the removed AE2FluidCraft AspectUtil. In newer ThaumicEnergistics essentia is no
 * longer modeled as a Forge fluid; it is the IAEStack type {@link AEEssentiaStack}. This helper centralizes the
 * aspect/essentia conversion and container queries against the new API so the rest of the mod depends on a single
 * Wtcts owned entry point.
 */
public final class AspectUtil {

    /**
     * Amount of essentia represented by a single item form unit (phial). Replaces the old fixed AspectUtil.R ratio.
     */
    public static final int R = AEEssentiaStackType.ESSENTIA_STACK_TYPE.getAmountPerUnit();

    public static final EssentiaItemContainerHelper HELPER = EssentiaItemContainerHelper.INSTANCE;

    private AspectUtil() {}

    public static boolean isEssentiaContainer(ItemStack is) {
        return is != null && AEEssentiaStackType.ESSENTIA_STACK_TYPE.isContainerItemForType(is);
    }

    public static boolean isEmptyEssentiaContainer(ItemStack is) {
        return is != null && HELPER.isContainerEmpty(is);
    }

    @Nullable
    public static Aspect getAspectFromJar(ItemStack is) {
        if (is == null) return null;
        return HELPER.getAspectInContainer(is);
    }

    @Nullable
    public static AEEssentiaStack getEssentiaFromContainer(ItemStack is) {
        if (is == null) return null;
        return AEEssentiaStackType.ESSENTIA_STACK_TYPE.getStackFromContainerItem(is);
    }

    public static AEEssentiaStack newEssentiaStack(Aspect aspect, long amount) {
        return new AEEssentiaStack(aspect, amount);
    }

    /**
     * The essentia a display item stands for, or null when the stack is not one.
     *
     * <p>
     * Recipes and NEI hand essentia out as an aspect item (AspectRecipeIndex's item, or the one the
     * Thaumcraft NEI plugin adds), never as a phial: those items carry the aspect in their NBT and the
     * essentia stack type knows how to read them. That conversion is what lets the encoding area turn a
     * transferred essentia ingredient into a real essentia pattern entry instead of writing the display
     * item itself into the pattern - a request for an item no machine can make.
     */
    @Nullable
    public static AEEssentiaStack essentiaFromDisplayItem(ItemStack is) {
        if (is == null || is.getItem() == null) return null;
        return AEEssentiaStackType.ESSENTIA_STACK_TYPE.convertStackFromItem(is);
    }

    /**
     * The same conversion with the amount the cell shows, ready to be written into a pattern. Returns
     * the generic stack so callers that only need to serialise it do not have to name the type.
     */
    @Nullable
    public static IAEStack<?> essentiaEntry(ItemStack is, long amount) {
        AEEssentiaStack essentia = essentiaFromDisplayItem(is);
        if (essentia == null) return null;
        essentia.setStackSize(amount);
        return essentia;
    }

    /**
     * The essentia ME monitor backing a network, or null when the grid has no storage cache.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static IMEMonitor<AEEssentiaStack> getEssentiaMonitor(IGrid grid) {
        if (grid == null) return null;
        IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
        if (storageGrid == null) return null;
        return (IMEMonitor<AEEssentiaStack>) storageGrid.getMEMonitor(AEEssentiaStackType.ESSENTIA_STACK_TYPE);
    }

    /**
     * Render an aspect amount in a GUI slot using the essentia stack's own renderer.
     */
    public static void drawAspect(EntityPlayer player, int x, int y, Aspect aspect, long amount) {
        if (aspect == null) return;
        new AEEssentiaStack(aspect, amount <= 0 ? 1 : amount).drawInGui(Minecraft.getMinecraft(), x, y);
    }
}
