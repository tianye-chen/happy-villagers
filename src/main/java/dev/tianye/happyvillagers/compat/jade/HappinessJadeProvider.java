package dev.tianye.happyvillagers.compat.jade;

import dev.tianye.happyvillagers.HappyVillagers;
import dev.tianye.happyvillagers.happiness.HappinessData;
import dev.tianye.happyvillagers.happiness.HappinessLevel;
import dev.tianye.happyvillagers.happiness.HappinessManager;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

/** Shows "♥ 7.3 Happy" on the Jade tooltip of villagers. */
public enum HappinessJadeProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = HappyVillagers.id("happiness");
    private static final ResourceLocation HEART = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final String KEY_HAPPINESS = "happyvillagers:happiness";
    private static final String KEY_QUIT = "happyvillagers:quit";

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        if (accessor.getEntity() instanceof Villager villager) {
            HappinessData happiness = HappinessManager.get(villager);
            data.putDouble(KEY_HAPPINESS, happiness.happiness());
            data.putBoolean(KEY_QUIT, happiness.hasQuit());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(KEY_HAPPINESS)) {
            return;
        }
        double happiness = data.getDouble(KEY_HAPPINESS);
        HappinessLevel level = HappinessLevel.of(happiness);
        IElementHelper elements = IElementHelper.get();
        tooltip.add(elements.sprite(HEART, 9, 9));
        tooltip.append(elements.spacer(2, 0));
        tooltip.append(elements.text(Component.literal(String.format(Locale.ROOT, "%.1f ", happiness))
                .withStyle(ChatFormatting.WHITE).append(level.displayName())));
        if (data.getBoolean(KEY_QUIT)) {
            tooltip.add(Component.translatable("happyvillagers.jade.quit").withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
