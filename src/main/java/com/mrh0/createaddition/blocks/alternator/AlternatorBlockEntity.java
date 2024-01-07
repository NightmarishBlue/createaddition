package com.mrh0.createaddition.blocks.alternator;

import java.util.List;

import com.mrh0.createaddition.CreateAddition;
import com.mrh0.createaddition.config.Config;
import com.mrh0.createaddition.energy.InternalEnergyStorage;
import com.mrh0.createaddition.index.CABlocks;
import com.mrh0.createaddition.util.Util;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.Lang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.DistExecutor;

public class AlternatorBlockEntity extends KineticBlockEntity {

	protected final InternalEnergyStorage energy;
	private LazyOptional<IEnergyStorage> lazyEnergy;

	public AlternatorBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
		energy = new InternalEnergyStorage(Config.ALTERNATOR_CAPACITY.get(), 0, Config.ALTERNATOR_MAX_OUTPUT.get());
		lazyEnergy = LazyOptional.of(() -> energy);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		tooltip.add(Component.literal(spacing).append(Component.translatable(CreateAddition.MODID + ".tooltip.energy.production").withStyle(ChatFormatting.GRAY)));
		tooltip.add(Component.literal(spacing).append(Component.literal(" " + Util.format(getEnergyProductionRate((int) (isSpeedRequirementFulfilled() ? getSpeed() : 0))) + "fe/t ") // fix
				.withStyle(ChatFormatting.AQUA)).append(Lang.translateDirect("gui.goggles.at_current_speed").withStyle(ChatFormatting.DARK_GRAY)));
		return true;
	}

	@Override
	public float calculateStressApplied() {
		float impact = Config.MAX_STRESS.get()/256f;
		this.lastStressApplied = impact;
		return impact;
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
		if(cap == ForgeCapabilities.ENERGY) return lazyEnergy.cast();
		return super.getCapability(cap, side);
	}

	public boolean isEnergyInput(Direction side) {
		return false;
	}

	public boolean isEnergyOutput(Direction side) {
		return true; //side != getBlockState().getValue(AlternatorBlock.FACING);
	}

	@Override
	public void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);
		energy.read(compound);
	}

	@Override
	public void write(CompoundTag compound, boolean clientPacket) {
		super.write(compound, clientPacket);
		energy.write(compound);
	}

	private boolean firstTickState = true;

	@Override
	public void tick() {
		super.tick();
		if(level.isClientSide()) {
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::tickParticles);
			return;
		}
		if(firstTickState) firstTick();
		firstTickState = false;

		if(Math.abs(getSpeed()) > 0 && isSpeedRequirementFulfilled())
			energy.internalProduceEnergy(getEnergyProductionRate((int)getSpeed()));

		for(Direction d : Direction.values()) {
			if(!isEnergyOutput(d)) continue;
			IEnergyStorage ies = getCachedEnergy(d);
			if(ies == null) continue;
			int ext = energy.extractEnergy(ies.receiveEnergy(Config.ALTERNATOR_MAX_OUTPUT.get(), true), false);
			ies.receiveEnergy(ext, false);
		}
	}

	int particleTicks = 30;
	int particlesSpawned = 0;
	@OnlyIn(Dist.CLIENT)
	public void tickParticles() {
		if(!(Math.abs(getSpeed()) > 0 && isSpeedRequirementFulfilled())) return;
		if (particleTicks != 0) {
			particleTicks--;
			return;
		}
		if (particlesSpawned > 1) {
			particlesSpawned--;
			particleTicks = level.random.nextInt(4, 6 + particlesSpawned);
		} else {
			int density = IRotate.SpeedLevel.of(getSpeed()).ordinal();
			particlesSpawned = level.random.nextInt(0, density) + 2;
			particleTicks = level.random.nextInt(60, 20 * (10 - density));
		}
		ParticleUtils.spawnParticlesOnBlockFaces(level, worldPosition, ParticleTypes.ELECTRIC_SPARK, UniformInt.of(1, particlesSpawned));
	}

	public static int getEnergyProductionRate(int rpm) {
		rpm = Math.abs(rpm);
		return (int)((double)Config.FE_RPM.get() * ((double)Math.abs(rpm) / 256d) * Config.ALTERNATOR_EFFICIENCY.get());//return (int)((double)Config.FE_TO_SU.get() * ((double)Math.abs(rpm)/256d) * EFFICIENCY);
	}

	@Override
	protected Block getStressConfigKey() {
		return CABlocks.ALTERNATOR.get();
	}

	@Override
	public void remove() {
		lazyEnergy.invalidate();
		super.remove();
	}

	public void firstTick() {
		updateCache();
	};

	public void updateCache() {
		if(level.isClientSide()) return;
		for(Direction side : Direction.values()) {
			BlockEntity te = level.getBlockEntity(worldPosition.relative(side));
			if(te == null) {
				setCache(side, LazyOptional.empty());
				continue;
			}
			LazyOptional<IEnergyStorage> le = te.getCapability(ForgeCapabilities.ENERGY, side.getOpposite());
			setCache(side, le);
		}
	}

	private LazyOptional<IEnergyStorage> escacheUp = LazyOptional.empty();
	private LazyOptional<IEnergyStorage> escacheDown = LazyOptional.empty();
	private LazyOptional<IEnergyStorage> escacheNorth = LazyOptional.empty();
	private LazyOptional<IEnergyStorage> escacheEast = LazyOptional.empty();
	private LazyOptional<IEnergyStorage> escacheSouth = LazyOptional.empty();
	private LazyOptional<IEnergyStorage> escacheWest = LazyOptional.empty();

	public void setCache(Direction side, LazyOptional<IEnergyStorage> storage) {
		switch (side) {
			case DOWN -> escacheDown = storage;
			case EAST -> escacheEast = storage;
			case NORTH -> escacheNorth = storage;
			case SOUTH -> escacheSouth = storage;
			case UP -> escacheUp = storage;
			case WEST -> escacheWest = storage;
		}
	}

	public IEnergyStorage getCachedEnergy(Direction side) {
		return switch (side) {
			case DOWN -> escacheDown.orElse(null);
			case EAST -> escacheEast.orElse(null);
			case NORTH -> escacheNorth.orElse(null);
			case SOUTH -> escacheSouth.orElse(null);
			case UP -> escacheUp.orElse(null);
			case WEST -> escacheWest.orElse(null);
		};
	}
}
