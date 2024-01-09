package com.mrh0.createaddition.blocks.tesla_coil;

import com.mrh0.createaddition.config.Config;
import com.mrh0.createaddition.energy.BaseElectricBlockEntity;
import com.mrh0.createaddition.index.CABlocks;
import com.mrh0.createaddition.index.CAEffects;
import com.mrh0.createaddition.index.CARecipes;
import com.mrh0.createaddition.recipe.charging.ChargingRecipe;
import com.mrh0.createaddition.util.Util;
import com.simibubi.create.content.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class TeslaCoilBlockEntity extends BaseElectricBlockEntity implements IHaveGoggleInformation {

	private Optional<ChargingRecipe> recipeCache = Optional.empty();

	private final ItemStackHandler inputInv;
	public ItemStack chargedItem = ItemStack.EMPTY; // sync the charged item between client and server for particle effects
	private int chargeAccumulator;
	protected int poweredTimer = 0;

	private static final DamageSource DMG_SOURCE = new DamageSource("tesla_coil");

	public TeslaCoilBlockEntity(BlockEntityType<?> tileEntityTypeIn, BlockPos pos, BlockState state) {
		super(tileEntityTypeIn, pos, state);
		inputInv = new ItemStackHandler(1);
	}

	@Override
	public int getCapacity() {
		return Util.max(Config.TESLA_COIL_CAPACITY.get(), Config.TESLA_COIL_CHARGE_RATE.get(), Config.TESLA_COIL_RECIPE_CHARGE_RATE.get());
	}

	@Override
	public int getMaxIn() {
		return Config.TESLA_COIL_MAX_INPUT.get();
	}

	@Override
	public int getMaxOut() {
		return 0;
	}

	public BeltProcessingBehaviour processingBehaviour;

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		processingBehaviour =
			new BeltProcessingBehaviour(this).whenItemEnters((s, i) -> TeslaCoilBeltCallbacks.onItemReceived(s, i, this))
				.whileItemHeld((s, i) -> TeslaCoilBeltCallbacks.whenItemHeld(s, i, this));
		behaviours.add(processingBehaviour);
	}

	@Override
	public boolean isEnergyInput(Direction side) {
		return side != getBlockState().getValue(TeslaCoilBlock.FACING).getOpposite();
	}

	@Override
	public boolean isEnergyOutput(Direction side) {
		return false;
	}

	public int getConsumption() {
		return Config.TESLA_COIL_CHARGE_RATE.get();
	}

	protected float getItemCharge(IEnergyStorage energy) {
		if (energy == null) return 0f;
		return (float) energy.getEnergyStored() / (float) energy.getMaxEnergyStored();
	}

	protected BeltProcessingBehaviour.ProcessingResult onCharge(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler) {
		BeltProcessingBehaviour.ProcessingResult res = chargeCompundAndStack(transported, handler);
		return res;
	}

	public AABB getTargetingBox() {
		Direction point = getBlockState().getValue(TeslaCoilBlock.FACING).getOpposite();
		BlockPos origin = getBlockPos().relative(point);
		double scaleAmount = Config.TESLA_COIL_HURT_RANGE.get() / 2f;
		Vec3 scaledNormal = Vec3.atLowerCornerOf(point.getNormal()).scale(scaleAmount);
		AABB box = new AABB(origin)
				.inflate(Config.TESLA_COIL_HURT_RANGE.get())
				.move(scaledNormal)
				.contract(scaledNormal.x, scaledNormal.y, scaledNormal.z);
		return box;
	}

	private void targetNearby(Consumer<LivingEntity> callback, boolean simulate) {
		if (simulate && !level.isClientSide) localEnergy.internalConsumeEnergy(Config.TESLA_COIL_HURT_ENERGY_REQUIRED.get());
		List<LivingEntity> ents = getLevel().getEntitiesOfClass(LivingEntity.class, getTargetingBox());
		for(LivingEntity e : ents) {
			if(e == null) return;

			boolean allChain = true;
			for(ItemStack armor : e.getArmorSlots()) {
				if(armor.is(Items.CHAINMAIL_BOOTS)) continue;
				if(armor.is(Items.CHAINMAIL_LEGGINGS)) continue;
				if(armor.is(Items.CHAINMAIL_CHESTPLATE)) continue;
				if(armor.is(Items.CHAINMAIL_HELMET)) continue;
				allChain = false;
				break;
			}
			if(allChain) continue;

			callback.accept(e);
		}
	}

	public void shockEntity(LivingEntity entity) {
		int dmg = Config.TESLA_COIL_HURT_DMG_MOB.get();
		int time = Config.TESLA_COIL_HURT_EFFECT_TIME_MOB.get();
		if(entity instanceof Player) {
			dmg = Config.TESLA_COIL_HURT_DMG_PLAYER.get();
			time = Config.TESLA_COIL_HURT_EFFECT_TIME_PLAYER.get();
		}

		if(dmg > 0) entity.hurt(DMG_SOURCE, dmg);
		if(time > 0) entity.addEffect(new MobEffectInstance(CAEffects.SHOCKING.get(), time + 1, 0, false, false));
	}



	int dmgTick = 0;
	int soundTimeout = 0;

	@Override
	public void tick() {
		super.tick();
		if(level == null) return;

		if(level.isClientSide()) {
			if(isPoweredState() && soundTimeout++ > 20) {
				//level.playLocalSound(getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(), SoundEvents.BEE_LOOP, SoundSource.BLOCKS, 1f, 16f, false);
				soundTimeout = 0;
			}
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::tickParticles);
			return;
		}
		int signal = getLevel().getBestNeighborSignal(getBlockPos());
		if(signal > 0 && localEnergy.getEnergyStored() >= Config.TESLA_COIL_HURT_ENERGY_REQUIRED.get())
			poweredTimer = 10;

		dmgTick++;
		if((dmgTick%=Config.TESLA_COIL_HURT_FIRE_COOLDOWN.get()) == 0 && localEnergy.getEnergyStored() >= Config.TESLA_COIL_HURT_ENERGY_REQUIRED.get() && signal > 0)
			targetNearby(this::shockEntity, false);

		if(poweredTimer > 0) {
			if(!isPoweredState())
				CABlocks.TESLA_COIL.get().setPowered(level, getBlockPos(), true);
			poweredTimer--;
		}
		else
			if(isPoweredState())
				CABlocks.TESLA_COIL.get().setPowered(level, getBlockPos(), false);
	}

	int particleTicks = 30;
	int particlesSpawned = 0;
	@OnlyIn(Dist.CLIENT)
	public void tickParticles() {
		if (!isPoweredState()) return;
		Direction pointing = getBlockState().getValue(TeslaCoilBlock.FACING).getOpposite();
		ParticleUtils.spawnParticlesAlongAxis(pointing.getAxis(), level, worldPosition.relative(pointing), level.random.nextDouble(), ParticleTypes.ELECTRIC_SPARK, UniformInt.of(0, 3));
		// spawn processing particles when the tesla is charging an item
		if (getBlockState().getValue(TeslaCoilBlock.FACING) == Direction.UP) {
			spawnChargingParticles();
		}
		// spawn zaps and an aura otherwise
		else {
			targetNearby(this::spawnZapParticles, true);
			spawnElectricFieldParticles(3);
		}
		if (particleTicks != 0) {
			particleTicks--;
			return;
		}
		// looks complex but it's just a decaying loop of sparks - when a burst finishes, a bigger delay is used
		if (particlesSpawned > 1) {
			particlesSpawned--;
			particleTicks = level.random.nextInt(4, 6 + particlesSpawned);
		} else {
			particlesSpawned = level.random.nextInt(2, 5);
			particleTicks = level.random.nextInt(30, 60);
		}
		ParticleUtils.spawnParticlesOnBlockFaces(level, worldPosition, ParticleTypes.ELECTRIC_SPARK, UniformInt.of(1, particlesSpawned));
	}

	@OnlyIn(Dist.CLIENT)
	public void spawnChargingParticles() {
		if (level.random.nextInt(8) != 0) return;
		Vec3 itemPos = Vec3.atCenterOf(worldPosition.below(2));
		level.addParticle(ParticleTypes.ELECTRIC_SPARK, itemPos.x + (level.random.nextFloat() - .5f) * .5f,
				itemPos.y + .5f, itemPos.z + (level.random.nextFloat() - .5f) * .5f, 0, 1 / 8f, 0);
	}

	@OnlyIn(Dist.CLIENT)
	public void spawnItemChips(Vec3 pos, ItemStack stack, int amount) {
		if (level == null || !level.isClientSide)
			return;
		for (int i = 0; i < amount; i++) {
			Vec3 motion = VecHelper.offsetRandomly(Vec3.ZERO, level.random, .1f)
					.multiply(1, 0, 1);
			motion = motion.add(0, amount != 1 ? 0.125f : 1 / 16f, 0);
			level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, stack), pos.x, pos.y - .25f, pos.z, motion.x,
					motion.y, motion.z);
		}
	}

	@OnlyIn(Dist.CLIENT)
	public void spawnZapParticles(LivingEntity entity) {
		if (level.random.nextInt(5) != 0) return;
		Vec3 targetPos = Util.randomPointInBox(entity.getBoundingBox(), level.random);
		BlockPos origin = worldPosition.relative(getBlockState().getValue(TeslaCoilBlock.FACING).getOpposite());
		Vec3 startPos = Vec3.atCenterOf(origin);

		Vec3 dist = targetPos.subtract(startPos);
		double points = 12.0;

		for(int i = 0; (double)i < points; ++i) {
			Vec3 sub = startPos.add(dist.x / points * (double)i, dist.y / points * (double)i, dist.z / points * (double)i);
			double fixPointDist = ((double)i - points / 2.0) / (points / 2.0);
			double mod = 1.0 - 0.75 * Math.abs(fixPointDist);
			double offX = (level.random.nextDouble() - 0.5) * mod;
			double offY = (level.random.nextDouble() - 0.5) * mod;
			double offZ = (level.random.nextDouble() - 0.5) * mod;
			if (fixPointDist < 0.0) {
				offY += 0.75 * mod * (0.75 + fixPointDist);
				offX = sub.x - startPos.x < 0.0 ? -Math.abs(offX) : Math.abs(offX);
				offZ = sub.z - startPos.z < 0.0 ? -Math.abs(offZ) : Math.abs(offZ);
			} else {
				offY = Math.min(targetPos.y + 1.0 * (1.0 - fixPointDist) * -Math.signum(dist.y), offY);
				offX = Math.abs(offX) * (targetPos.x - sub.x);
				offZ = Math.abs(offZ) * (targetPos.z - sub.z);
			}

			Util.spawnParticle(level, ParticleTypes.ELECTRIC_SPARK, sub.add(offX, offY, offZ), Vec3.ZERO);
		}
//		for (int i = 1; i < 10; i++) {
//			Vec3 curPos = startPos.lerp(VecHelper.offsetRandomly(targetPos, level.random, 1f), i / 10d);
//			Vec3 offset = VecHelper.offsetRandomly(Vec3.ZERO, level.random, 0.5f).multiply(0.5f, 1f + i / 10f, 0.5f);
//			Util.spawnParticle(level, ParticleTypes.ELECTRIC_SPARK, curPos.add(offset), Vec3.ZERO);
//		}
	}

	@OnlyIn(Dist.CLIENT)
	public void spawnElectricFieldParticles(int numParticles) {
		if (level.random.nextInt(8) != 0) return;
		AABB box = getTargetingBox();
		for (int i = 0; i < numParticles; i++) {
			Vec3 spawnPos = Util.randomPointInBox(box, level.random);
			Util.spawnParticle(level, ParticleTypes.ELECTRIC_SPARK, spawnPos, Util.randomVec(0.2f, level.random));
		}
	}

	public boolean isPoweredState() {
		return getBlockState().getValue(TeslaCoilBlock.POWERED);
	}

	protected BeltProcessingBehaviour.ProcessingResult chargeCompundAndStack(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler) {

		ItemStack stack = transported.stack;
		if(stack == null)
			return BeltProcessingBehaviour.ProcessingResult.PASS;
		if(chargeStack(stack, transported, handler)) {
			poweredTimer = 10;
			return BeltProcessingBehaviour.ProcessingResult.HOLD;
		}
		else if(chargeRecipe(stack, transported, handler)) {
			poweredTimer = 10;
			return BeltProcessingBehaviour.ProcessingResult.HOLD;
		}
		return BeltProcessingBehaviour.ProcessingResult.PASS;
	}

	protected boolean chargeStack(ItemStack stack, TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler) {
		if(!stack.getCapability(ForgeCapabilities.ENERGY).isPresent()) return false;
		IEnergyStorage es = stack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
		if(es.receiveEnergy(1, true) != 1) return false;
		if(localEnergy.getEnergyStored() < stack.getCount()) return false;
		localEnergy.internalConsumeEnergy(es.receiveEnergy(Math.min(getConsumption(), localEnergy.getEnergyStored()), false));
		return true;
	}

	private boolean chargeRecipe(ItemStack stack, TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler) {
		if (this.getLevel() == null) return false;
		if(!inputInv.getStackInSlot(0).sameItem(stack)) {
			inputInv.setStackInSlot(0, stack);
			recipeCache = find(new RecipeWrapper(inputInv), this.getLevel());
			chargeAccumulator = 0;
		}
		if(recipeCache.isPresent()) {
			ChargingRecipe recipe = recipeCache.get();
			int energyRemoved = localEnergy.internalConsumeEnergy(Util.min(Config.TESLA_COIL_RECIPE_CHARGE_RATE.get(), recipe.getEnergy() - chargeAccumulator, recipe.getMaxChargeRate()));
			chargeAccumulator += energyRemoved;
			if(chargeAccumulator >= recipe.getEnergy()) {
				TransportedItemStack remainingStack = transported.copy();
				TransportedItemStack result = transported.copy();
				result.stack = recipe.getResultItem().copy();
				remainingStack.stack.shrink(1);
				List<TransportedItemStack> outList = new ArrayList<>();
				outList.add(result);
				handler.handleProcessingOnItem(transported, TransportedItemStackHandlerBehaviour.TransportedResult.convertToAndLeaveHeld(outList, remainingStack));
				chargeAccumulator = 0;
				chargedItem = stack;
				sendData();
			}
			return true;
		}
		return false;
	}

	public Optional<ChargingRecipe> find(RecipeWrapper wrapper, Level world) {
		return world.getRecipeManager().getRecipeFor(CARecipes.CHARGING_TYPE.get(), wrapper, world);
	}

	@Override
	public void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);
		if (!clientPacket) return;
		chargedItem = ItemStack.of(compound.getCompound("ChargedItem"));
		finishedChargingItem();
	}

	@Override
	public void write(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);
		if (!clientPacket) return;
		compound.put("ChargedItem", chargedItem.serializeNBT());
		chargedItem = ItemStack.EMPTY;
	}

	void finishedChargingItem() {
		if (!chargedItem.isEmpty()) {
			Vec3 pos = VecHelper.getCenterOf(worldPosition.below(2)).add(0f, 8f / 16f, 0f);
			spawnItemChips(pos, chargedItem, 8);
		}
		chargedItem = ItemStack.EMPTY;
	}
}
