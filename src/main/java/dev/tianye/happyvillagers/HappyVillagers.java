package dev.tianye.happyvillagers;

import com.mojang.logging.LogUtils;
import dev.tianye.happyvillagers.happiness.HappinessManager;
import dev.tianye.happyvillagers.network.HappinessPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(HappyVillagers.MOD_ID)
public class HappyVillagers {
    public static final String MOD_ID = "happyvillagers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HappyVillagers(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(HappyVillagers::registerPayloads);
        modContainer.registerConfig(ModConfig.Type.COMMON, HappyConfig.SPEC);

        NeoForge.EVENT_BUS.register(HappinessManager.class);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Optional so that vanilla clients (or clients without the mod) can still join a server running it.
        event.registrar("1").optional()
                .playToClient(HappinessPayload.TYPE, HappinessPayload.STREAM_CODEC, HappinessPayload::handle);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
