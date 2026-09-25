package dev.tianye.happyvillagers.trade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import dev.tianye.happyvillagers.HappyVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Loads bonus trades from {@code data/<namespace>/happyvillagers/bonus_trades/*.json} on every (re)load. */
public class BonusTradeManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile List<BonusTrade> trades = List.of();

    private final HolderLookup.Provider registries;

    private BonusTradeManager(HolderLookup.Provider registries) {
        super(GSON, "happyvillagers/bonus_trades");
        this.registries = registries;
    }

    public static List<BonusTrade> trades() {
        return trades;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new BonusTradeManager(event.getRegistryAccess()));
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        RegistryOps<JsonElement> ops = registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        List<BonusTrade> loaded = new ArrayList<>();
        files.forEach((id, json) -> BonusTrade.Definition.CODEC.parse(ops, json)
                .resultOrPartial(error -> HappyVillagers.LOGGER.error("Ignoring bonus trade {}: {}", id, error))
                .ifPresent(definition -> loaded.add(definition.withKey(id))));
        trades = List.copyOf(loaded);
        HappyVillagers.LOGGER.info("Loaded {} bonus trades", loaded.size());
    }
}
