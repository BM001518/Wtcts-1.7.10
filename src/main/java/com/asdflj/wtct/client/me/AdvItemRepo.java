package com.asdflj.wtct.client.me;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import com.asdflj.wtct.client.gui.widget.IGuiMonitor;
import com.asdflj.wtct.common.Config;
import com.asdflj.wtct.util.Ae2ReflectClient;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.me.ItemRepo;

public class AdvItemRepo extends ItemRepo implements Runnable {

    private static final int SIZE = 1;
    private static final int DELAY = 3;

    private static final BlockingQueue<Runnable> IN = new LinkedBlockingQueue<>(SIZE);
    private static final ThreadPoolExecutor pool = new ThreadPoolExecutor(
        SIZE,
        SIZE,
        60,
        TimeUnit.SECONDS,
        IN,
        r -> new Thread(r, "AE2 Thing repo sort thread"),
        new RejectedExecutionHandler() {

            private static final ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(SIZE);

            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                scheduledThreadPool.schedule(() -> pool.execute(r), DELAY, TimeUnit.SECONDS);
            }
        });

    protected final ArrayList<IAEStack<?>> view = Ae2ReflectClient.getView(this);
    protected final IItemList<IAEStack<?>> list = Ae2ReflectClient.getList(this);

    protected AdvItemRepo repo;
    protected final Set<IAEStack<?>> cache = Collections.synchronizedSet(new HashSet<>());
    protected IGuiMonitor gui;
    private static final Lock lock = new ReentrantLock();

    public AdvItemRepo(IScrollSource src, ISortSource sortSrc) {
        super(src, sortSrc);
    }

    public AdvItemRepo(ISortSource sortSrc) {
        this(null, sortSrc);
    }

    public Lock getLock() {
        return lock;
    }

    /**
     * The full client-side copy of the network's storage list - every synced stack, craftable-only
     * entries included (they carry {@code isCraftable}). The {@code view} is the filtered/sorted
     * page list; previews that need to know "does the network have or can it craft X" want this.
     */
    public IItemList<IAEStack<?>> getAllStacks() {
        return this.list;
    }

    public void setCache(IGuiMonitor gui) {
        if (Config.updateViewThread) {
            this.repo = new AdvItemRepo(gui);
            this.repo.setPowered(true);
            this.gui = gui;
        }
    }

    public boolean hasCache() {
        return repo != null;
    }

    @Override
    public void setSearchString(@Nonnull String searchString) {
        if (this.hasCache()) {
            repo.setSearchString(searchString);
        }
        super.setSearchString(searchString);
    }

    @Override
    public void setViewCell(ItemStack[] list) {
        if (this.hasCache()) {
            repo.setViewCell(list);
        }
        super.setViewCell(list);
    }

    @Override
    @Deprecated
    public void postUpdate(IAEItemStack is) {
        this.postUpdate((IAEStack<?>) is);
    }

    @Override
    public void postUpdate(IAEStack<?> is) {
        if (this.hasCache()) {
            lock.lock();
            this.cache.remove(is);
            this.cache.add(is);
            lock.unlock();
        }
        super.postUpdate(is);
    }

    @Override
    public void updateView() {
        if (this.hasCache()) {
            try {
                pool.execute(this);
            } catch (Exception ignored) {

            }
        } else {
            super.updateView();
        }
    }

    @Override
    public void run() {
        try {
            lock.lock();
            for (IAEStack<?> is : this.cache) {
                this.repo.postUpdate(is);
            }
            this.cache.clear();
        } finally {
            lock.unlock();
        }
        this.repo.updateView();
        try {
            lock.lock();
            this.view.clear();
            this.view.ensureCapacity(this.repo.view.size());
            this.view.addAll(this.repo.view);
            this.gui.setScrollBar();
        } finally {
            lock.unlock();
        }
    }

    /**
     * A copy of the order the terminal is currently showing. The optimistic screen paints this while
     * the server is still busy; see {@link #adoptSnapshot}.
     */
    public List<IAEStack<?>> snapshotView() {
        lock.lock();
        try {
            return new ArrayList<>(this.view);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Installs a list and an already sorted view without rebuilding the view.
     *
     * <p>
     * A full {@link #updateView()} costs ~17 ms for a large network (it resolves a sort key - display
     * name, mod id - for every stack before sorting), which is far too much to pay on the first frame
     * of a screen whose only job is to not look empty while the server answers. The snapshot taken by
     * {@link #snapshotView()} is already in display order, so it can be dropped straight into both
     * lists; the first real update replaces it wholesale a moment later.
     */
    public void adoptSnapshot(final List<? extends IAEStack<?>> stacks, final List<? extends IAEStack<?>> sorted) {
        final AdvItemRepo target = this.hasCache() ? this.repo : this;
        lock.lock();
        try {
            for (final IAEStack<?> stack : stacks) {
                if (stack != null) {
                    target.list.add(stack);
                }
            }
            target.view.clear();
            target.view.ensureCapacity(sorted.size());
            target.view.addAll(sorted);
            if (target != this) {
                this.view.clear();
                this.view.ensureCapacity(sorted.size());
                this.view.addAll(sorted);
            }
        } finally {
            lock.unlock();
        }
        if (this.gui != null) {
            this.gui.setScrollBar();
        }
    }

    @Override
    public void setPaused(boolean paused) {
        if (hasCache() && this.repo instanceof IDisplayRepoExtend dre) {
            dre.setAdvRepoPause(paused);
            if (!paused) {
                try {
                    pool.execute(this);
                } catch (Exception ignored) {

                }
            }
        } else {
            super.setPaused(paused);
        }
    }
}
