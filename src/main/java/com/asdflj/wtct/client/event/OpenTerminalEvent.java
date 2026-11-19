package com.asdflj.wtct.client.event;

import com.asdflj.wtct.Wtct;
import com.asdflj.wtct.api.adapter.terminal.item.TerminalItems;
import com.asdflj.wtct.network.CPacketOpenTerminal;

import cpw.mods.fml.common.eventhandler.Event;

public class OpenTerminalEvent extends Event {

    private final TerminalItems items;

    public OpenTerminalEvent(TerminalItems terminalItems) {
        items = terminalItems;
    }

    public void openTerminal() {
        com.asdflj.wtct.util.OpenTimer.request();
        // Only the comprehensive terminal can be shown ahead of time; the other terminals are built from
        // their own items and keep the server-first path.
        if (this.items != null && this.items.getTargetItem() != null
            && this.items.getTargetItem()
                .getItem() instanceof com.asdflj.wtct.common.item.ItemComprehensiveWorkTerminal) {
            com.asdflj.wtct.client.gui.OptimisticTerminalOpen.tryOpen();
        }
        Wtct.proxy.netHandler.sendToServer(new CPacketOpenTerminal(items));
    }
}
