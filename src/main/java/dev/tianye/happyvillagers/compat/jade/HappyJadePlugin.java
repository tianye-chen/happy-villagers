package dev.tianye.happyvillagers.compat.jade;

import net.minecraft.world.entity.npc.Villager;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Discovered by Jade through the annotation; never loaded when Jade is absent. */
@WailaPlugin
public class HappyJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(HappinessJadeProvider.INSTANCE, Villager.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(HappinessJadeProvider.INSTANCE, Villager.class);
        registration.addConfig(HappinessJadeProvider.DETAILS, true);
    }
}
