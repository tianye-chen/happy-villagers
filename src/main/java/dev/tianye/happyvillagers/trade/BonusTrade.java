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
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * A config-defined trade that only appears while a villager is happy enough.
 * Format: {@code profession | minHappiness | minLevel | costA | costB or - | result | maxUses | xp}
 */
public record BonusTrade(String key, ResourceLocation profession, double minHappiness, int minLevel,
                         ItemCost costA, Optional<ItemCost> costB, ItemStack result, int maxUses, int xp) {
    private static final Set<String> REPORTED = new HashSet<>();

    public boolean appliesTo(VillagerProfession villagerProfession, int level, double happiness) {
        return profession.equals(BuiltInRegistries.VILLAGER_PROFESSION.getKey(villagerProfession))
                && level >= minLevel && happiness >= minHappiness;
    }

    public MerchantOffer createOffer(int uses) {
        return new MerchantOffer(costA, costB, result.copy(), Math.min(uses, maxUses), maxUses, xp, 0.05F);
    }

    /** Parses all configured bonus trades; broken entries are logged once and skipped. */
    public static List<BonusTrade> fromConfig(HolderLookup.Provider registries) {
        List<BonusTrade> trades = new ArrayList<>();
        for (String entry : HappyConfig.BONUS_TRADES.get()) {
            try {
                trades.add(parse(entry, registries));
            } catch (Exception e) {
                if (REPORTED.add(entry)) {
                    HappyVillagers.LOGGER.error("Ignoring invalid bonus trade '{}': {}", entry, e.getMessage());
                }
            }
        }
        return trades;
    }

    static BonusTrade parse(String entry, HolderLookup.Provider registries) throws CommandSyntaxException {
        String[] parts = entry.split("\\|");
        if (parts.length != 8) {
            throw new IllegalArgumentException("expected 8 fields separated by '|', found " + parts.length);
        }
        ResourceLocation profession = ResourceLocation.parse(parts[0].trim());
        if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(profession)) {
            throw new IllegalArgumentException("unknown profession " + profession);
        }
        double minHappiness = Double.parseDouble(parts[1].trim());
        int minLevel = Integer.parseInt(parts[2].trim());
        ItemCost costA = parseCost(parts[3], registries);
        String costBText = parts[4].trim();
        Optional<ItemCost> costB = costBText.equals("-") || costBText.isEmpty()
                ? Optional.empty() : Optional.of(parseCost(costBText, registries));
        ItemStack result = parseStack(parts[5], registries);
        int maxUses = Integer.parseInt(parts[6].trim());
        int xp = Integer.parseInt(parts[7].trim());
        return new BonusTrade(Integer.toHexString(entry.trim().hashCode()), profession, minHappiness, minLevel,
                costA, costB, result, maxUses, xp);
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
