package com.asdflj.wtct.client.gui.widget;

import java.util.List;

import com.asdflj.wtct.client.me.AdvItemRepo;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.widgets.ISortSource;

public interface IGuiMonitor extends ISortSource {

    @Deprecated
    default void postFluidUpdate(List<IAEFluidStack> list) {
        postStackUpdate(list);
    }

    @Deprecated
    default void postUpdate(List<IAEItemStack> list) {
        postStackUpdate(list);
    }

    void postStackUpdate(List<? extends IAEStack<?>> list);

    /**
     * Applies one window's worth of lists in a single pass.
     *
     * <p>
     * The item, fluid and essentia lists arrive in separate packets but all land in the same repo, so
     * a terminal that applies them one at a time rebuilds and re-sorts its view once per list. This
     * entry point lets an implementation merge every stack first and rebuild once; the default keeps
     * the old per-list behaviour for the simpler screens.
     */
    default void postStackUpdateBatched(List<? extends IAEStack<?>> list) {
        if (list != null && !list.isEmpty()) {
            postStackUpdate(list);
        }
    }

    /**
     * Paints an already sorted snapshot (what the previous session showed) without rebuilding the view.
     * Only the optimistic screen uses this; see {@link AdvItemRepo#adoptSnapshot}.
     */
    default void restoreCachedSnapshot(List<? extends IAEStack<?>> stacks, List<? extends IAEStack<?>> sortedView) {
        if (stacks != null && !stacks.isEmpty()) {
            postStackUpdate(stacks);
        }
    }

    void setScrollBar();

    AdvItemRepo getRepo();

    void handleKeyboardInput();
}
