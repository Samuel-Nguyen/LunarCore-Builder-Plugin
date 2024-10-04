package dev.draven.builder.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CharacterBuildData {
    private int avatarId;
    private String avatarName;
    private String fullName;
    private int rarity;
    private int skillLevel;
    private List<BuildDetail> builds;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BuildDetail {
        private String buildName;
        private int eidolonLevel;
        private EquipmentData equipment;
        private List<RelicData> relics;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EquipmentData {
        private int itemId;
        private int enhancementLevel;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelicData {
        private int itemId;
        private Integer primaryAffixId;
        private String subAffixes;
    }
}
