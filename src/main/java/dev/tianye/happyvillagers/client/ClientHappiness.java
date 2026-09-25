package dev.tianye.happyvillagers.client;

import dev.tianye.happyvillagers.network.HappinessPayload;
import org.jetbrains.annotations.Nullable;

/** Latest happiness info received for the open trade screen. */
public final class ClientHappiness {
    @Nullable
    private static HappinessPayload current;

    private ClientHappiness() {}

    public static void set(HappinessPayload payload) {
        current = payload;
    }

    @Nullable
    public static HappinessPayload forContainer(int containerId) {
        return current != null && current.containerId() == containerId ? current : null;
    }
}
