package dev.frosthaven.havenconsolidated.mixin;

import dev.frosthaven.havenconsolidated.calm.Calm;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pufferfish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The puff goal starts for any scary living thing within 2 blocks; a player the pufferfish ignores ({@link Calm}) is not one. */
@Mixin(targets = "net.minecraft.world.entity.animal.Pufferfish$PufferfishPuffGoal")
abstract class PufferfishPuffMixin {
    @Shadow @Final private Pufferfish fish;

    @Redirect(method = "canUse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private <T extends LivingEntity> List<T> haven$notCalmed(Level level, Class<T> type, AABB area, Predicate<? super T> scary) {
        return level.getEntitiesOfClass(type, area, e -> scary.test(e) && !(e instanceof Player player && Calm.ignores(fish, player)));
    }
}
