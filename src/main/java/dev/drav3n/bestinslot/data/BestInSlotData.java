package dev.drav3n.bestinslot.data;

import java.util.List;

import lombok.Getter;

@Getter
public class BestInSlotData {
  private int avatarId;
  private String avatarName;
  private int rank;
  private EquipmentInfo equipment;
  private List<RelicInfo> relicList;

  @Getter
  public static class EquipmentInfo {
    private int tid;
    private int rank;
  }

  @Getter
  public static class RelicInfo {
    private int tid;
    private int type;
    private int mainAffixId;
    private List<RelicSubAffix> subAffixList;
  }

  @Getter
  public static class RelicSubAffix {
    private int affixId;
    private int cnt;
    private int step;
  }
}
