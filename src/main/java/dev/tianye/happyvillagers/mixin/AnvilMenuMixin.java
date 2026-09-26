package dev.tianye.happyvillagers.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    /**
     * Vanilla clamps every combined enchantment to its maximum level, which would turn the Protection V book that
     * ecstatic armorers sell into Protection IV. Raise the cap to the highest level already present on either
     * input: above-max levels carry over, but combining two of them never goes higher (V + V stays V), and
     * ordinary combinations (III + III = IV) are unchanged.
     */
    @ModifyExpressionValue(
            method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;getMaxLevel()I"))
    private int happyvillagers$keepAboveMaxLevels(int maxLevel,
                                                  @Local Object2IntMap.Entry<Holder<Enchantment>> addition,
                                                  @Local ItemEnchantments.Mutable result) {
        int fromAddition = addition.getIntValue();
        int fromTarget = result.getLevel(addition.getKey());
        return Math.max(maxLevel, Math.max(fromAddition, fromTarget));
    }
}
