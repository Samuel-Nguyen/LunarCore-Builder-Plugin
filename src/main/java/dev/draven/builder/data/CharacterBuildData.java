package dev.draven.builder.data;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CharacterBuildData {
    private int avatarId;
    private String avatarName;
    private String fullName;
    private int rarity;
    private Map<String, Integer> skillLevel;

    private List<Relic> defaultRelics;
    private List<BuildDetail> builds;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Relic {
        private int itemId;
        private Integer primaryAffixId;
        private String subAffixes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BuildDetail {
        private String buildName;
        private int eidolonLevel;
        private Equipment equipment;
        private List<Relic> relics;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Equipment {
        private int itemId;
        private int enhancementLevel;
    }
}
