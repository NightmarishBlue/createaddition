package com.mrh0.createaddition.effect;

import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;


public class ShockingEffect extends MobEffect {
	public ShockingEffect() {
		super(MobEffectCategory.HARMFUL, 15453236);
		//setRegistryName(new ResourceLocation(CreateAddition.MODID, "shocking"));
	}

	@Override
	public void applyEffectTick(@NotNull LivingEntity entity, int amplifier) {
		jostleEntity(entity, amplifier);
	}

	public void jostleEntity(LivingEntity entity, int amplifier) {
		float maxMove = 0.2f * (amplifier + 1);
		RandomSource random = entity.level.random;
		Supplier<Float> randFloat = () -> (random.nextFloat() - 0.5f);

		entity.setYBodyRot(entity.yBodyRot + randFloat.get() * maxMove * 25);
		entity.setXRot(entity.getXRot() + randFloat.get() * maxMove * 25);
		entity.setYRot(entity.getYRot() + randFloat.get() * maxMove * 25);
		entity.setYHeadRot(entity.yHeadRot + randFloat.get() * maxMove * 25);

		Vec3 delta = entity.getDeltaMovement();
		Vec3 randVec = new Vec3(randFloat.get() * maxMove, 0f, randFloat.get() * maxMove);

		if (entity.isOnGround() && random.nextInt(3) == 0)
			randVec = randVec.add(0f, randFloat.get() * maxMove * 5, 0f);
		entity.setDeltaMovement(delta.add(randVec));
	}

	@Override
	public boolean isDurationEffectTick(int duration, int amplifier) {
		return true;
	}
}
