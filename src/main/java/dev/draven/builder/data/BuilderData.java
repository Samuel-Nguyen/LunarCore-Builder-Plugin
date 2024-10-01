package dev.draven.builder.data;

import java.util.List;
import lombok.Getter;

@Getter
public class BuilderData {
    private int avatarId;
    private String avatarName;
    private String fullName;
    private int rarity;
    private int skill;
    private List<BuildDetail> buildList;

    @Getter
    public static class BuildDetail {
        private String buildName;
        private int eidolon;
        private EquipmentDetail equipment;
        private List<RelicDetail> relicList;
    }

    @Getter
    public static class EquipmentDetail {
        private int tid;
        private int imposition;
    }

    @Getter
    public static class RelicDetail {
        private int tid;
        private Integer mainAffixId;
        private String subAffix;
    }
}
