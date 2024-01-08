package com.mrh0.createaddition.blocks.tesla_coil;

import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

public class TeslaCoilBeltCallbacks {
	public static BeltProcessingBehaviour.ProcessingResult onItemReceived(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler, TeslaCoilBlockEntity te) {
		if(te.getBlockState().getValue(TeslaCoilBlock.FACING) == Direction.UP) {
			return BeltProcessingBehaviour.ProcessingResult.HOLD;
		}
		return BeltProcessingBehaviour.ProcessingResult.PASS;
	}

	public static BeltProcessingBehaviour.ProcessingResult whenItemHeld(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler, TeslaCoilBlockEntity te) {
		te.chargedItem = ItemStack.EMPTY;
		return te.onCharge(transported, handler);
	}
}