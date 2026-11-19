package com.asdflj.wtct.nei;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.asdflj.wtct.nei.object.OrderStack;

public class NEIUtils {

    public static List<OrderStack<?>> compress(List<OrderStack<?>> list) {
        List<OrderStack<?>> comp = new LinkedList<>();
        for (OrderStack<?> orderStack : list) {
            if (orderStack == null) continue;
            if (orderStack.getStack() instanceof FluidStack) {
                comp.add(orderStack);
                continue;
            }
            ItemStack currentStack = (ItemStack) orderStack.getStack();
            fillZeroSized(orderStack);
            boolean find = false;
            for (OrderStack<?> storedStack : comp) {
                if (storedStack == null || !(storedStack.getStack() instanceof ItemStack firstStack)) continue;
                boolean areItemStackEqual = firstStack.isItemEqual(currentStack)
                    && ItemStack.areItemStackTagsEqual(firstStack, currentStack);
                if (areItemStackEqual
                    && (firstStack.stackSize + currentStack.stackSize) <= firstStack.getMaxStackSize()) {
                    find = true;
                    ((ItemStack) storedStack.getStack()).stackSize = firstStack.stackSize + currentStack.stackSize;
                }
            }
            if (!find) {
                comp.add(orderStack);
            }
        }
        return comp.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public static List<OrderStack<?>> clearNull(List<OrderStack<?>> list) {
        AtomicInteger i = new AtomicInteger(0);
        return list.stream()
            .filter(Objects::nonNull)
            .peek(NEIUtils::fillZeroSized)
            .peek(orderStack -> orderStack.setIndex(i.getAndIncrement()))
            .collect(Collectors.toList());
    }

    /**
     * A GregTech recipe hands its non-consumed inputs over as a zero-sized stack - {@code
     * GTUtility.copyAmount(0, ...)} is how the programmed circuit, a lens in the laser engraver or a
     * mould is added, because the machine does not use the item up. A pattern cannot ask for zero of
     * something: AE2 stores no cell for it and the encoding grid slot is left empty, which is why a
     * pulled assembler recipe arrived without its programmed circuit. The recipe transfer therefore
     * promotes such a stack to one, which is also how the player would place it by hand.
     */
    private static void fillZeroSized(final OrderStack<?> orderStack) {
        if (orderStack.getStack() instanceof final ItemStack stack && stack.stackSize <= 0) {
            stack.stackSize = 1;
        }
    }
}
