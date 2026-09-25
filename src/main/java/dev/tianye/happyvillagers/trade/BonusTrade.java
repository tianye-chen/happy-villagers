package dev.tianye.happyvillagers.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * A datapack-defined trade that only appears while a villager is happy enough.
 * Loaded from {@code data/<namespace>/happyvillagers/bonus_trades/<name>.json}; the file id is the trade's key.
 */
public record BonusTrade(ResourceLocation key, VillagerProfession profession, double minHappiness, int minLevel,
                         ItemCost costA, Optional<ItemCost> costB, ItemStack result, int maxUses, int xp) {

    /** JSON body; the key comes from the file name. */
    public record Definition(VillagerProfession profession, double minHappiness, int minLevel, ItemCost costA,
                             Optional<ItemCost> costB, ItemStack result, int maxUses, int xp) {
        public static final Codec<Definition> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.VILLAGER_PROFESSION.byNameCodec().fieldOf("profession").forGetter(Definition::profession),
                Codec.doubleRange(0.0, 10.0).fieldOf("min_happiness").forGetter(Definition::minHappiness),
                Codec.intRange(1, 5).optionalFieldOf("min_level", 1).forGetter(Definition::minLevel),
                ItemCost.CODEC.fieldOf("cost_a").forGetter(Definition::costA),
                ItemCost.CODEC.optionalFieldOf("cost_b").forGetter(Definition::costB),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(Definition::result),
                ExtraCodecs.POSITIVE_INT.fieldOf("max_uses").forGetter(Definition::maxUses),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("xp", 0).forGetter(Definition::xp)
        ).apply(i, Definition::new));

        public BonusTrade withKey(ResourceLocation key) {
            return new BonusTrade(key, profession, minHappiness, minLevel, costA, costB, result, maxUses, xp);
        }
    }

    public boolean appliesTo(VillagerProfession villagerProfession, int level, double happiness) {
        return profession == villagerProfession && level >= minLevel && happiness >= minHappiness;
    }

    public MerchantOffer createOffer(int uses) {
        return new MerchantOffer(costA, costB, result.copy(), Math.min(uses, maxUses), maxUses, xp, 0.05F);
    }
}
