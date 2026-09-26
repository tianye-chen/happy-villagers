package dev.tianye.happyvillagers.compat.jade;

import dev.tianye.happyvillagers.HappyConfig;
import dev.tianye.happyvillagers.HappyVillagers;
import dev.tianye.happyvillagers.happiness.HappinessData;
import dev.tianye.happyvillagers.happiness.HappinessFactor;
import dev.tianye.happyvillagers.happiness.HappinessLevel;
import dev.tianye.happyvillagers.happiness.HappinessManager;
import java.util.Comparator;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

/** Shows "♥ 7.3 Happy" on the Jade tooltip of villagers, plus the trend and top factors while Shift is held. */
public enum HappinessJadeProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = HappyVillagers.id("happiness");
    /** Jade plugin option: show the trend and factors while Jade's details key is held. */
    public static final ResourceLocation DETAILS = HappyVillagers.id("happiness_details");
    private static final ResourceLocation HEART = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final String KEY_HAPPINESS = "happyvillagers:happiness";
    private static final String KEY_TARGET = "happyvillagers:target";
    private static final String KEY_QUIT = "happyvillagers:quit";
    private static final String KEY_FACTORS = "happyvillagers:factors";
    private static final String KEY_TRAITS = "happyvillagers:traits";

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        if (!(accessor.getEntity() instanceof Villager villager)) {
            return;
        }
        HappinessData happiness = HappinessManager.get(villager);
        data.putDouble(KEY_HAPPINESS, happiness.happiness());
        data.putDouble(KEY_TARGET, happiness.target());
        data.putBoolean(KEY_QUIT, happiness.hasQuit());

        // Biggest influences first; "missing" bonuses (value 0) come after real contributions.
        ListTag factors = new ListTag();
        happiness.factors().stream()
                .sorted(Comparator.comparingDouble((HappinessFactor f) -> -Math.abs(f.value())))
                .limit(HappyConfig.JADE_MAX_FACTORS.get())
                .forEach(f -> {
                    CompoundTag tag = new CompoundTag();
                    tag.putString("id", f.id());
                    tag.putDouble("value", f.value());
                    tag.putInt("detail", f.detail());
                    tag.putBoolean("positive", f.positive());
                    tag.putString("trait", f.trait());
                    factors.add(tag);
                });
        data.put(KEY_FACTORS, factors);

        ListTag traits = new ListTag();
        happiness.traits().forEach(id -> traits.add(StringTag.valueOf(id.toString())));
        data.put(KEY_TRAITS, traits);
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

        ListTag factors = data.getList(KEY_FACTORS, Tag.TAG_COMPOUND);
        if (!config.get(DETAILS)) {
            return;
        }
        if (!accessor.showDetails()) {
            if (!factors.isEmpty()) {
                tooltip.add(Component.translatable("happyvillagers.jade.details_hint").withStyle(ChatFormatting.DARK_GRAY));
            }
            return;
        }
        ListTag traits = data.getList(KEY_TRAITS, Tag.TAG_STRING);
        if (!traits.isEmpty()) {
            tooltip.add(dev.tianye.happyvillagers.trait.Trait.traitsLine(
                    traits.stream().map(Tag::getAsString).toList()));
        }
        double target = data.getDouble(KEY_TARGET);
        if (target != happiness) {
            String key = target > happiness ? "happyvillagers.tooltip.rising" : "happyvillagers.tooltip.falling";
            tooltip.add(Component.translatable(key, String.format(Locale.ROOT, "%.1f", target)).withStyle(ChatFormatting.GRAY));
        }
        for (int i = 0; i < factors.size(); i++) {
            CompoundTag f = factors.getCompound(i);
            tooltip.add(new HappinessFactor(f.getString("id"), f.getDouble("value"), f.getInt("detail"), f.getBoolean("positive"),
                    f.getString("trait")).describe());
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
