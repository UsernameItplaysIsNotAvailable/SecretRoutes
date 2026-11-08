//#if FABRIC
package xyz.yourboykyle.secretroutes.config;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

/** Not based on distance this time like the rest :D */
public enum BossRouteMode implements NameableEnum {
    ADG("ADG", "ADG", "f7boss"),
    CLASSIC("Classic roles", "classic", "f7boss:1");

    private final String label;
    private final String routeSuffix;
    private final String legacyRouteKey;

    BossRouteMode(String label, String routeSuffix, String legacyRouteKey) {
        this.label = label;
        this.routeSuffix = routeSuffix;
        this.legacyRouteKey = legacyRouteKey;
    }

    public String label() {
        return label;
    }

    public String routeKey(DungeonClass dungeonClass) {
        return "f7boss:" + dungeonClass.routeSuffix() + "_" + routeSuffix;
    }

    public String legacyRouteKey() {
        return legacyRouteKey;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(label);
    }
}
//#endif
