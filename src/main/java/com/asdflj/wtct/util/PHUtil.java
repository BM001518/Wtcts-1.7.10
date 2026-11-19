package com.asdflj.wtct.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import net.minecraft.item.ItemStack;

import com.asdflj.wtct.integration.Mods;
import com.asdflj.wtct.nei.object.OrderStack;

import gregtech.common.items.ItemIntegratedCircuit;
import reobf.proghatches.item.ItemProgrammingCircuit;
import reobf.proghatches.item.ItemProgrammingToolkit;

public class PHUtil {

    /**
     * True for the circuit a processing pattern carries to pick the machine's recipe (编程器电路).
     *
     * <p>
     * Two items play that role and the "circuit always one" switch has to recognise both. When the
     * player holds programmablehatches' programming toolkit, {@link #transfer(List)} rewrites the
     * circuit slot into {@code ItemProgrammingCircuit}. A plain recipe pull writes GregTech's own
     * programmed circuit instead ({@code ItemList.Circuit_Integrated}), which is what the NEI recipe
     * hands over as a zero-sized, non-consumed input. Both are soft dependencies, so each class is only
     * touched after its mod loaded check - otherwise a pack without that mod would fail to link here.
     */
    public static boolean isProgrammingCircuit(final ItemStack stack) {
        if (stack == null) {
            return false;
        }
        if (Mods.PROGRAMMABLE_HATCHES.isModLoaded() && stack.getItem() instanceof ItemProgrammingCircuit) {
            return true;
        }
        return Mods.GREGTECH.isModLoaded() && stack.getItem() instanceof ItemIntegratedCircuit;
    }

    public static List<OrderStack<?>> transfer(List<OrderStack<?>> inputs) {
        AtomicBoolean circuit = new AtomicBoolean(false);
        if (!ItemProgrammingToolkit.holding()) {
            return inputs;
        }
        AtomicInteger i = new AtomicInteger(0);
        ArrayList<OrderStack<?>> spec = new ArrayList<>();
        List<OrderStack<?>> ret = inputs.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingInt(OrderStack::getIndex))
            .filter(orderStack -> {
                boolean regular = !(orderStack.getStack() != null && orderStack.getStack() instanceof ItemStack
                    && ((ItemStack) orderStack.getStack()).stackSize == 0);
                if (!regular) {
                    circuit.set(true);
                    spec.add(
                        new OrderStack<>(
                            ItemProgrammingCircuit.wrap(((ItemStack) orderStack.getStack())),
                            orderStack.getIndex()));
                    return false;
                }

                return true;
            })
            .collect(Collectors.toList());

        if (!circuit.get() && ItemProgrammingToolkit.addEmptyProgCiruit()) {
            spec.add(0, new OrderStack<>(ItemProgrammingCircuit.wrap(null), 0));
        }

        spec.addAll(ret);
        spec.forEach((orderStack -> orderStack.setIndex(i.getAndIncrement())));

        return spec;
    }
}
