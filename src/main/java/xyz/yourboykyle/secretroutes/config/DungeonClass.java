//#if FABRIC
package xyz.yourboykyle.secretroutes.config;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

public enum DungeonClass implements NameableEnum {
    HEALER("Healer", "h"),
    BERSERK("Berserk", "b"),
    ARCHER("Archer", "a"),
    TANK("Tank", "t"),
    MAGE("Mage", "m");

    private final String label;
    private final String routeSuffix;

    DungeonClass(String label, String routeSuffix) {
        this.label = label;
        this.routeSuffix = routeSuffix;
    }

    public String routeSuffix() {
        return routeSuffix;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(label);
    }
}
//#endif
