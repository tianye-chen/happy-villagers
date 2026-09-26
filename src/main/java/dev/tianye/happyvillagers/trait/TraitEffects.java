package dev.tianye.happyvillagers.trait;

import dev.tianye.happyvillagers.happiness.HappinessFactor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Applies a villager's traits to its happiness factors. */
public final class TraitEffects {
    /** Factor id for a trait's flat baseline change. */
    public static final String DISPOSITION = "disposition";

    private TraitEffects() {}

    public static List<HappinessFactor> apply(List<ResourceLocation> traitIds, List<HappinessFactor> factors) {
        List<Trait> traits = new ArrayList<>();
        for (ResourceLocation id : traitIds) {
            Trait trait = TraitManager.get(id);
            if (trait != null) {
                traits.add(trait);
            }
        }
        if (traits.isEmpty()) {
            return factors;
        }
        List<HappinessFactor> result = new ArrayList<>(factors.size() + traits.size());
        for (HappinessFactor factor : factors) {
            double multiply = 1.0, add = 0.0;
            String source = "";
            for (Trait trait : traits) {
                Trait.Modifier modifier = trait.modifiers().get(factor.id());
                if (modifier != null) {
                    multiply *= modifier.multiply();
                    add += modifier.add();
                    source = source.isEmpty() ? trait.id().toString() : source;
                }
            }
            double value = factor.value() * multiply + add;
            if (source.isEmpty() || value == factor.value()) {
                result.add(factor);
            } else {
                boolean positive = value > 0 || (value == 0 && factor.positive());
                result.add(new HappinessFactor(factor.id(), value, factor.detail(), positive, source));
            }
        }
        for (Trait trait : traits) {
            if (trait.base() != 0) {
                result.add(new HappinessFactor(DISPOSITION, trait.base(), 0, trait.base() > 0, trait.id().toString()));
            }
        }
        return result;
    }
}
