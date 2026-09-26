package dev.tianye.happyvillagers.trade;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.tianye.happyvillagers.HappyConfig;
import dev.tianye.happyvillagers.HappyVillagers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;

/**
 * Happiness-10 special trades, defined per profession in the {@code [specialTrades]} config section.
 * Entry format: {@code costA | costB or - | result | maxUses | xp}, items in /give syntax.
 */
public final class SpecialTrades {
    private static final Set<String> REPORTED = new HashSet<>();

    private SpecialTrades() {}

    /** The configured special trades for {@code profession}, parsed now (invalid entries are logged once and skipped). */
    public static List<BonusTrade> forProfession(VillagerProfession profession, HolderLookup.Provider registries) {
        if (!HappyConfig.SPECIAL_TRADES_ENABLED.get()) {
            return List.of();
        }
        ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        var configured = HappyConfig.SPECIAL_TRADES.get(id.getPath());
        if (configured == null || !id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
            return List.of();
        }
        List<BonusTrade> trades = new ArrayList<>();
        for (String entry : configured.get()) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                trades.add(parse(profession, id.getPath(), entry, registries));
            } catch (Exception e) {
                if (REPORTED.add(entry)) {
                    HappyVillagers.LOGGER.error("Ignoring invalid special trade for {} '{}': {}", id.getPath(), entry, e.getMessage());
                }
            }
        }
        return trades;
    }

    static BonusTrade parse(VillagerProfession profession, String professionName, String entry, HolderLookup.Provider registries)
            throws CommandSyntaxException {
        String[] parts = entry.split("\\|");
        if (parts.length != 5) {
            throw new IllegalArgumentException("expected 5 fields separated by '|', found " + parts.length);
        }
        ItemCost costA = parseCost(parts[0], registries);
        String costBText = parts[1].trim();
        Optional<ItemCost> costB = costBText.equals("-") || costBText.isEmpty()
                ? Optional.empty() : Optional.of(parseCost(costBText, registries));
        ItemStack result = parseStack(parts[2], registries);
        int maxUses = Integer.parseInt(parts[3].trim());
        int xp = Integer.parseInt(parts[4].trim());
        if (maxUses < 1 || xp < 0) {
            throw new IllegalArgumentException("maxUses must be at least 1 and xp at least 0");
        }
        // Keyed by the entry's text so each configured trade keeps its own use count.
        ResourceLocation key = HappyVillagers.id("special/" + professionName + "/" + Integer.toHexString(entry.trim().hashCode()));
        return new BonusTrade(key, profession, HappyConfig.SPECIAL_TRADES_MIN_HAPPINESS.get(),
                HappyConfig.SPECIAL_TRADES_MIN_LEVEL.get(), costA, costB, result, maxUses, xp);
    }

    private static ItemCost parseCost(String text, HolderLookup.Provider registries) throws CommandSyntaxException {
        ItemStack stack = parseStack(text, registries);
        return new ItemCost(stack.getItemHolder(), stack.getCount(), DataComponentPredicate.EMPTY);
    }

    /** "[count ]item[components]" using the same item syntax as /give. */
    private static ItemStack parseStack(String text, HolderLookup.Provider registries) throws CommandSyntaxException {
        String trimmed = text.trim();
        int count = 1;
        int space = trimmed.indexOf(' ');
        if (space > 0 && trimmed.substring(0, space).chars().allMatch(Character::isDigit)) {
            count = Integer.parseInt(trimmed.substring(0, space));
            trimmed = trimmed.substring(space + 1).trim();
        }
        ItemParser.ItemResult parsed = new ItemParser(registries).parse(new StringReader(trimmed));
        ItemStack stack = new ItemStack(parsed.item(), count);
        stack.applyComponents(parsed.components());
        return stack;
    }
}
