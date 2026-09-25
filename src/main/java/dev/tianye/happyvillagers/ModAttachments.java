package dev.tianye.happyvillagers;

import dev.tianye.happyvillagers.happiness.HappinessData;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, HappyVillagers.MOD_ID);

    public static final Supplier<AttachmentType<HappinessData>> HAPPINESS = ATTACHMENT_TYPES.register(
            "happiness", () -> AttachmentType.builder(HappinessData::new).serialize(HappinessData.CODEC).build());

    private ModAttachments() {}
}
