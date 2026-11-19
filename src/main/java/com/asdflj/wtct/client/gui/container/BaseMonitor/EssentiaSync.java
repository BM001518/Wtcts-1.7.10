package com.asdflj.wtct.client.gui.container.BaseMonitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ICrafting;

import com.asdflj.wtct.Wtct;
import com.asdflj.wtct.network.SPacketMEEssentiaInvUpdate;
import com.asdflj.wtct.util.AspectUtil;

import appeng.api.storage.IMEMonitor;
import appeng.util.Platform;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.common.storage.AEEssentiaStack;

/**
 * Keeps the client's essentia list in step with the network's essentia monitor.
 *
 * <p>
 * Essentia has no listener hook as convenient as {@code ItemMonitor}'s: the network posts changes per
 * storage type, and the type is only known here as "everything that is not an item or a fluid". So
 * instead of chasing change events this compares the whole essentia list against what was last sent and
 * ships the difference - the list is one entry per aspect, i.e. a few dozen entries at most, and the
 * container already runs this once per tick for the item and fluid monitors.
 *
 * <p>
 * The amount sent is absolute, and zero means "gone". That is the same convention AE2's item list uses,
 * and the client's repo hides zero-sized entries in the terminal's default (stored-only) view.
 */
public class EssentiaSync {

    @Nullable
    private IMEMonitor<AEEssentiaStack> monitor;
    /** Aspect tag to the amount the client was last told about. */
    private final Map<String, Long> sent = new HashMap<>();

    public void setMonitor(@Nullable final IMEMonitor<AEEssentiaStack> monitor) {
        if (this.monitor != monitor) {
            this.sent.clear();
        }
        this.monitor = monitor;
    }

    public boolean isActive() {
        return this.monitor != null;
    }

    /** Sends the aspects that appeared, moved or vanished since the previous tick. */
    public void processItemList(final Iterable<?> crafters) {
        if (!Platform.isServer() || this.monitor == null || crafters == null) {
            return;
        }
        final Map<String, Long> now = new HashMap<>();
        final List<AEEssentiaStack> changed = new ArrayList<>();
        for (final AEEssentiaStack is : this.monitor.getStorageList()) {
            if (is == null || is.getAspect() == null) {
                continue;
            }
            final String tag = is.getAspect()
                .getTag();
            now.put(tag, is.getStackSize());
            final Long before = this.sent.get(tag);
            if (before == null || before.longValue() != is.getStackSize()) {
                changed.add(is.copy());
            }
        }
        for (final String tag : this.sent.keySet()) {
            if (now.containsKey(tag)) {
                continue;
            }
            final Aspect aspect = Aspect.getAspect(tag);
            if (aspect == null) {
                continue;
            }
            // Gone: the absolute amount is zero, and the client drops it from the view.
            changed.add(AspectUtil.newEssentiaStack(aspect, 0));
        }
        this.sent.clear();
        this.sent.putAll(now);
        if (!changed.isEmpty()) {
            send(changed, crafters);
        }
    }

    /** Pushes the whole essentia list, used when a window opens (the diff alone would start empty). */
    public void queueInventory(final ICrafting crafter) {
        if (!Platform.isServer() || this.monitor == null || !(crafter instanceof EntityPlayerMP)) {
            return;
        }
        final List<AEEssentiaStack> list = new ArrayList<>();
        for (final AEEssentiaStack is : this.monitor.getStorageList()) {
            if (is != null && is.getStackSize() > 0) {
                list.add(is.copy());
            }
        }
        if (list.isEmpty()) {
            return;
        }
        final List<ICrafting> single = new ArrayList<>(1);
        single.add(crafter);
        send(list, single);
    }

    private void send(final List<AEEssentiaStack> list, final Iterable<?> crafters) {
        final SPacketMEEssentiaInvUpdate packet = new SPacketMEEssentiaInvUpdate();
        for (final AEEssentiaStack is : list) {
            packet.appendEssentia(is);
        }
        for (final Object c : crafters) {
            if (c instanceof EntityPlayer) {
                Wtct.proxy.netHandler.sendTo(packet, (EntityPlayerMP) c);
            }
        }
    }
}
