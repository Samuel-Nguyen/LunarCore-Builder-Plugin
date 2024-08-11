package dev.drav3n.bestinslot;

import java.io.FileReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import dev.drav3n.bestinslot.data.BestInSlotData;
import emu.lunarcore.LunarCore;
import emu.lunarcore.command.Command;
import emu.lunarcore.command.CommandArgs;
import emu.lunarcore.command.CommandHandler;
import emu.lunarcore.data.GameData;
import emu.lunarcore.game.avatar.GameAvatar;
import emu.lunarcore.game.inventory.GameItem;
import emu.lunarcore.game.inventory.GameItemSubAffix;
import emu.lunarcore.game.inventory.tabs.InventoryTabType;
import emu.lunarcore.util.JsonUtils;
import emu.lunarcore.util.Utils;

@Command(label = "bestinslot", aliases = {
    "bis " }, permission = "player.give", requireTarget = true, desc = "/bis [character id]")
public class BestInSlotCommand implements CommandHandler {
  @Override
  public void execute(CommandArgs args) {
    // Sanity check materials
    var inventory = args.getTarget().getInventory();
    if (inventory.getTab(InventoryTabType.RELIC).getAvailableCapacity() <= 0
        || inventory.getTab(InventoryTabType.EQUIPMENT).getAvailableCapacity() <= 0) {
      args.sendMessage("Error: The targeted player does not has enough space in their relic/lightcone inventory");
      return;
    }

    String name = parseUserData(args);

    args.getSender()
          .sendMessage("Give " + name + " best in slot relics.");
  }

  private String getBISData() {
    return LunarCore.getConfig().getDataDir() + "/BestInSlot.json";
  }

  @SuppressWarnings("deprecation")
  private String parseUserData(CommandArgs args) {
    String name = "";
    var player = args.getTarget();
    int id = Utils.parseSafeInt(args.get(0));

    try (FileReader fileReader = new FileReader(getBISData())) {
      List<BestInSlotData> bisData = JsonUtils.loadToList(fileReader, BestInSlotData.class);
      for (BestInSlotData bis : bisData) {
        if (bis.getAvatarId() == id) {
          GameAvatar avatar = player.getAvatarById(id);

          if (avatar != null) {
            // Delete old relics/equips
            List<GameItem> unequipList = new ArrayList<>();

            // Force unequip all items
            int[] slots = avatar.getEquips().keySet().toIntArray();
            for (int slot : slots) {
              var item = avatar.unequipItem(slot);
              if (item != null) {
                unequipList.add(item);
              }
            }
            player.getInventory().removeItems(unequipList);
          } else {
            // Validate avatar excel (In case we are using an older version of the server)
            var excel = GameData.getAvatarExcelMap().get(id);

            // Create avatar
            avatar = new GameAvatar(excel);

            // Set avatar basic data
            avatar.setLevel(80);
            avatar.setExp(0);
            avatar.setRank(0);
            avatar.setPromotion(6);
            avatar.setRewards(0b00101010);

            // Set avatar skills
            for (int pointId : avatar.getExcel().getSkillTreeIds()) {
              var skillTree = GameData.getAvatarSkillTreeExcel(pointId, 1);
              if (skillTree == null)
                continue;

              int minLevel = skillTree.isDefaultUnlock() ? 1 : 0;
              int pointLevel = Math.max(Math.min(8, skillTree.getMaxLevel()), minLevel);

              avatar.getSkills().put(pointId, pointLevel);
            }

            // Gives the avatar to player
            player.addAvatar(avatar);
          }

          // Set equipment
          var equipmentData = bis.getEquipment();
          if (equipmentData != null) {
            // Validate equipment excel (In case we are using an older version of the
            // server)
            var excel = GameData.getItemExcelMap().get(equipmentData.getTid());
            if (excel != null) {
              // Create equipment
              var equipment = new GameItem(excel);

              // Set equipment props
              equipment.setLevel(80);
              equipment.setExp(0);
              equipment.setPromotion(6);
              equipment.setRank(equipmentData.getRank());

              // Add to player
              player.getInventory().addItem(equipment);
              avatar.equipItem(equipment);
            }
          }

          // Set relics
          for (var relicData : bis.getRelicList()) {
            // Validate relic excel (In case we are using an older version of the server)
            var excel = GameData.getItemExcelMap().get(relicData.getTid());
            if (excel == null)
              continue;

            // Create relic
            var relic = new GameItem(excel);

            // Set relic props
            relic.setLevel(15);
            relic.setExp(0);
            relic.setMainAffix(relicData.getMainAffixId());
            relic.resetSubAffixes();

            for (var subAffixData : relicData.getSubAffixList()) {
              var subAffix = new GameItemSubAffix();
              subAffix.setCount(subAffixData.getCnt());
              // Max-steps the affix
              subAffix.setStep(subAffixData.getCnt() * 2);

              // Hacky way to set id field since its private
              try {
                Field field = subAffix.getClass().getDeclaredField("id");
                field.setAccessible(true);
                field.setInt(subAffix, subAffixData.getAffixId());
              } catch (Exception e) {
                // TODO handle
              }

              relic.getSubAffixes().add(subAffix);
            }

            // Add to player
            player.getInventory().addItem(relic);
            avatar.equipItem(relic);
          }

          // Save
          avatar.save();
          // Increment count
          name = bis.getAvatarName();
        }
      }
    } catch (Exception e) {
      // TODO: handle exception
    }

    return name;
  }
}
