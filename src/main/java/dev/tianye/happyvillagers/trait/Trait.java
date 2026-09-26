package dev.tianye.happyvillagers.trait;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

/**
 * A personality trait: changes how much each happiness factor matters to a villager.
 * Loaded from {@code data/<namespace>/happyvillagers/traits/<name>.json}; the file id is the trait's id.
 *
 * @param modifiers factor id (e.g. {@code greenery}, {@code crowded}) -> how that factor's value is changed
 * @param base      flat change to the villager's baseline happiness
 */
public record Trait(ResourceLocation id, int weight, List<ResourceLocation> conflicts, Map<String, Modifier> modifiers, double base) {

    /** {@code value * multiply + add}; {@code add} also applies to "missing" factors, whose value is 0. */
    public record Modifier(double multiply, double add) {
        public static final Codec<Modifier> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("multiply", 1.0).forGetter(Modifier::multiply),
                Codec.DOUBLE.optionalFieldOf("add", 0.0).forGetter(Modifier::add)
        ).apply(i, Modifier::new));
    }

    /** JSON body; the id comes from the file name. */
    public record Definition(int weight, List<ResourceLocation> conflicts, Map<String, Modifier> modifiers, double base) {
        public static final Codec<Definition> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("weight", 10).forGetter(Definition::weight),
                ResourceLocation.CODEC.listOf().optionalFieldOf("conflicts", List.of()).forGetter(Definition::conflicts),
                Codec.unboundedMap(Codec.STRING, Modifier.CODEC).optionalFieldOf("modifiers", Map.of()).forGetter(Definition::modifiers),
                Codec.DOUBLE.optionalFieldOf("base", 0.0).forGetter(Definition::base)
        ).apply(i, Definition::new));

        public Trait withId(ResourceLocation id) {
            return new Trait(id, weight, conflicts, modifiers, base);
        }
    }

    public boolean conflictsWith(Trait other) {
        return conflicts.contains(other.id) || other.conflicts.contains(id);
    }

    /** "Traits: Night Owl, Glutton" for tooltips. */
    public static Component traitsLine(List<String> traitIds) {
        MutableComponent names = Component.empty();
        for (int i = 0; i < traitIds.size(); i++) {
            if (i > 0) {
                names.append(", ");
            }
            names.append(displayName(traitIds.get(i)));
        }
        return Component.translatable("happyvillagers.tooltip.traits", names.withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY);
    }

    /** Display name from {@code trait.<namespace>.<path>}, falling back to the id's path in title case. */
    public static Component displayName(String traitId) {
        ResourceLocation id = ResourceLocation.tryParse(traitId);
        if (id == null) {
            return Component.literal(traitId);
        }
        StringBuilder fallback = new StringBuilder();
        for (String word : id.getPath().split("_")) {
            if (!word.isEmpty()) {
                fallback.append(fallback.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return Component.translatableWithFallback("trait." + id.getNamespace() + "." + id.getPath(), fallback.toString());
    }
}
