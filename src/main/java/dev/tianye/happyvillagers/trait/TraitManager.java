package dev.tianye.happyvillagers.trait;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.tianye.happyvillagers.HappyConfig;
import dev.tianye.happyvillagers.HappyVillagers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

/** Loads traits from {@code data/<namespace>/happyvillagers/traits/*.json} and hands them out to villagers. */
public class TraitManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<ResourceLocation, Trait> traits = Map.of();

    private TraitManager() {
        super(GSON, "happyvillagers/traits");
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new TraitManager());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Trait> loaded = new LinkedHashMap<>();
        files.forEach((id, json) -> Trait.Definition.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> HappyVillagers.LOGGER.error("Ignoring trait {}: {}", id, error))
                .ifPresent(definition -> loaded.put(id, definition.withId(id))));
        traits = Map.copyOf(loaded);
        HappyVillagers.LOGGER.info("Loaded {} villager traits", loaded.size());
    }

    public static Map<ResourceLocation, Trait> all() {
        return traits;
    }

    @Nullable
    public static Trait get(ResourceLocation id) {
        return traits.get(id);
    }

    /** 1 to maxTraits random traits, weighted, never two that conflict. Empty if no traits are loaded. */
    public static List<ResourceLocation> roll(RandomSource random) {
        return fill(new ArrayList<>(), targetCount(random), random);
    }

    /** Each parent trait has a 50% chance to be passed on; remaining slots are rolled randomly. */
    public static List<ResourceLocation> inherit(List<ResourceLocation> parentA, List<ResourceLocation> parentB, RandomSource random) {
        int target = targetCount(random);
        List<ResourceLocation> inherited = new ArrayList<>(parentA);
        for (ResourceLocation id : parentB) {
            if (!inherited.contains(id)) {
                inherited.add(id);
            }
        }
        Collections.shuffle(inherited, new java.util.Random(random.nextLong()));
        List<Trait> chosen = new ArrayList<>();
        for (ResourceLocation id : inherited) {
            Trait trait = traits.get(id);
            if (chosen.size() < target && trait != null && random.nextBoolean() && chosen.stream().noneMatch(trait::conflictsWith)) {
                chosen.add(trait);
            }
        }
        return fill(new ArrayList<>(chosen.stream().map(Trait::id).toList()), target, random);
    }

    private static int targetCount(RandomSource random) {
        return 1 + random.nextInt(HappyConfig.MAX_TRAITS.get());
    }

    /** Adds weighted random compatible traits until {@code chosenIds} has {@code target} entries (or none are left). */
    private static List<ResourceLocation> fill(List<ResourceLocation> chosenIds, int target, RandomSource random) {
        List<Trait> chosen = new ArrayList<>();
        for (ResourceLocation id : chosenIds) {
            Trait trait = traits.get(id);
            if (trait != null) {
                chosen.add(trait);
            }
        }
        while (chosen.size() < target) {
            List<Trait> candidates = traits.values().stream()
                    .filter(t -> t.weight() > 0 && !chosen.contains(t) && chosen.stream().noneMatch(t::conflictsWith))
                    .toList();
            int total = candidates.stream().mapToInt(Trait::weight).sum();
            if (total <= 0) {
                break;
            }
            int pick = random.nextInt(total);
            for (Trait candidate : candidates) {
                pick -= candidate.weight();
                if (pick < 0) {
                    chosen.add(candidate);
                    break;
                }
            }
        }
        return chosen.stream().map(Trait::id).toList();
    }
}
