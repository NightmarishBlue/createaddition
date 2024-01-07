package com.mrh0.createaddition.effect;

import com.mrh0.createaddition.util.Util;
import com.simibubi.create.foundation.utility.VecHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Supplier;


public class ShockingEffect extends MobEffect {
	public ShockingEffect() {
		super(MobEffectCategory.HARMFUL, 15453236);
		//setRegistryName(new ResourceLocation(CreateAddition.MODID, "shocking"));
	}

	@Override
	public void applyEffectTick(LivingEntity entity, int amplifier) {
		if (entity.level.isClientSide) {
			tickParticles(entity);
		}
	}

	@OnlyIn(Dist.CLIENT)
	private void tickParticles(LivingEntity entity) {
		AABB box = entity.getBoundingBox();
		for (int i = 0; i < entity.level.random.nextInt(10); i++) {
			Vec3 pos = Util.randomPointInBox(box, entity.level.random);
			Util.spawnParticle(entity.level, ParticleTypes.ELECTRIC_SPARK, pos, VecHelper.offsetRandomly(Vec3.ZERO, entity.level.random, 0.3f));
		}
	}

	@Override
	public boolean isDurationEffectTick(int duration, int amplifier) {
		return true;
	}
}
