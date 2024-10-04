package dev.draven.builder;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import dev.draven.builder.data.CharacterBuildData;
import dev.draven.builder.data.CharacterBuildData.*;
import emu.lunarcore.LunarCore;
import emu.lunarcore.command.Command;
import emu.lunarcore.command.CommandArgs;
import emu.lunarcore.command.CommandHandler;
import emu.lunarcore.data.GameData;
import emu.lunarcore.game.avatar.GameAvatar;
import emu.lunarcore.game.inventory.GameItem;
import emu.lunarcore.game.inventory.GameItemSubAffix;
import emu.lunarcore.game.inventory.tabs.InventoryTabType;
import emu.lunarcore.game.player.Player;
import emu.lunarcore.util.JsonUtils;
import emu.lunarcore.util.Utils;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

@Command(
    label = "build",
    aliases = { "b" },
    permission = "player.give", 
    requireTarget = true, 
    desc = "/build [all/nickname/id] (build name) (-max)"
)
public class BuilderCommand implements CommandHandler {

    public void execute(CommandArgs args) {
        if (!hasInventorySpace(args)) {
            args.sendMessage("Error: The targeted player does not have enough space in their relic/lightcone inventory.");
            return;
        }

        String message = parseBuildData(args);
        args.getSender().sendMessage(message);
    }

    private boolean hasInventorySpace(CommandArgs args) {
        var inventory = args.getTarget().getInventory();
        return inventory.getTab(InventoryTabType.RELIC).getAvailableCapacity() > 0 &&
               inventory.getTab(InventoryTabType.EQUIPMENT).getAvailableCapacity() > 0;
    }

    private String parseBuildData(CommandArgs args) {
        String input = args.get(0).toLowerCase();
        String buildName = args.get(1).isEmpty() ? "normal" : args.get(1).toLowerCase();
        List<CharacterBuildData> buildInformation = loadBuildInformation();

        if (buildInformation.isEmpty()) {
            return "Don't have any builds. Please define one.";
        }

        return switch (input) {
            case "all", "a" -> processAllBuilds(args, buildInformation, buildName);
            default -> processSpecificBuild(args, buildInformation, input, buildName);
        };
    }

    private List<CharacterBuildData> loadBuildInformation() {
        try (FileReader fileReader = new FileReader(LunarCore.getConfig().getDataDir() + "/BuildDetails.json")) {
            return JsonUtils.loadToList(fileReader, CharacterBuildData.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String processAllBuilds(CommandArgs args, List<CharacterBuildData> buildInformation, String buildName) {
        int total = 0;

        for (CharacterBuildData buildInfo : buildInformation) {
            if (generateBuild(args, buildInfo, buildName)) {
                total++;
            }
        }

        return "Gave " + total + " characters relics for " + buildName.toUpperCase() + " build.";
    }

    
    private static boolean isNumeric(String str) {
        return str != null && str.matches("\\d+");
    }

    private String processSpecificBuild(CommandArgs args, List<CharacterBuildData> buildInformation, String input, String buildName) {
        Optional<CharacterBuildData> buildInfoOpt = isNumeric(input)
            ? buildInformation.stream().filter(b -> b.getAvatarId() == Utils.parseSafeInt(input)).findFirst()
            : buildInformation.stream().filter(b -> b.getAvatarName().equalsIgnoreCase(input)).findFirst();

        if (buildInfoOpt.isPresent()) {
            CharacterBuildData buildInfo = buildInfoOpt.get();
            generateBuild(args, buildInfo, buildName);
            return "Gave " + buildInfo.getFullName() + " relics for " + buildName.toUpperCase() + " build.";
        }

        return "Build not found for input: " + input;
    }

    public Boolean generateBuild(CommandArgs args, CharacterBuildData buildInfo, String buildName) {
        Player player = args.getTarget();
        GameAvatar avatar = getOrCreateAvatar(buildInfo.getAvatarId(), player);
        if (avatar == null) return false;

        setupAvatar(avatar, buildInfo);
        applyBuild(avatar, buildInfo, buildName);
        avatar.save();

        return true;
    }

    private GameAvatar getOrCreateAvatar(int id, Player player) {
        GameAvatar avatar = player.getAvatarById(id);
        if (avatar != null) {
            unequipAvatarItems(avatar, player);
            return avatar;
        }

        var excel = GameData.getAvatarExcelMap().get(id);
        avatar = new GameAvatar(excel);
        player.addAvatar(avatar);

        return avatar;
    }

    private void unequipAvatarItems(GameAvatar avatar, Player player) {
        List<GameItem> unequipList = new ArrayList<>();
        for (int slot : avatar.getEquips().keySet().toIntArray()) {
            var item = avatar.unequipItem(slot);
            if (item != null) {
                unequipList.add(item);
            }
        }
        player.getInventory().removeItems(unequipList);
    }

    private void setupAvatar(GameAvatar avatar, CharacterBuildData buildInfo) {
        avatar.setLevel(80);
        avatar.setExp(0);
        avatar.setPromotion(6);
        avatar.setRewards(0b00101010);

        for (int pointId : avatar.getExcel().getSkillTreeIds()) {
            var skillTree = GameData.getAvatarSkillTreeExcel(pointId, 1);
            if (skillTree != null) {
                int pointLevel = Math.max(Math.min(buildInfo.getSkillLevel(), skillTree.getMaxLevel()), skillTree.isDefaultUnlock() ? 1 : 0);
                avatar.getSkills().put(pointId, pointLevel);
            }
        }
    }

    private void applyBuild(GameAvatar avatar, CharacterBuildData buildInfo, String buildName) {
        BuildDetail buildDetail = getBuildDetail(buildInfo, buildName);
        avatar.setRank(buildDetail.getEidolonLevel());
        equipItem(avatar, buildDetail.getEquipment());
        equipRelics(avatar, buildDetail.getRelics());
    }

    private BuildDetail getBuildDetail(CharacterBuildData buildInfo, String buildName) {
        return buildInfo.getBuilds().stream()
                .filter(detail -> detail.getBuildName().equalsIgnoreCase(buildName))
                .findFirst()
                .orElse(buildInfo.getBuilds().get(0));
    }

    private void equipItem(GameAvatar avatar, EquipmentData equipmentData) {
        Player player = avatar.getOwner();
        if (equipmentData != null) {
            var excel = GameData.getItemExcelMap().get(equipmentData.getItemId());
            if (excel != null) {
                var equipment = new GameItem(excel);
                equipment.setLevel(80);
                equipment.setExp(0);
                equipment.setPromotion(6);
                equipment.setRank(equipmentData.getEnhancementLevel());

                player.getInventory().addItem(equipment);
                avatar.equipItem(equipment);
            }
        }
    }

    private void equipRelics(GameAvatar avatar, List<RelicData> relicList) {
        Player player = avatar.getOwner();
        for (var relicData : relicList) {
            var excel = GameData.getItemExcelMap().get(relicData.getItemId());
            if (excel == null) continue;
            var relic = new GameItem(excel);
            setupRelic(relic, relicData);

            player.getInventory().addItem(relic);
            avatar.equipItem(relic);
        }
    }

    private void setupRelic(GameItem relic, RelicData relicData) {
        relic.setLevel(15);
        relic.setExp(0);
        relic.setMainAffix(relicData.getPrimaryAffixId() != null ? relicData.getPrimaryAffixId() : 1);
        relic.resetSubAffixes();

        Int2IntMap subAffixMap = parseSubAffixes(relicData.getSubAffixes());
        applySubAffixes(relic, subAffixMap);
    }

    private Int2IntMap parseSubAffixes(String subAffixData) {
        Int2IntMap subAffixMap = new Int2IntOpenHashMap();
        for (String subAffix : subAffixData.split(" ")) {
            String[] split = subAffix.split("[:,]");
            if (split.length >= 2) {
                subAffixMap.put(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
            }
        }

        return subAffixMap;
    }

    private void applySubAffixes(GameItem relic, Int2IntMap subAffixMap) {
        for (var entry : subAffixMap.int2IntEntrySet()) {
            if (entry.getIntValue() <= 0) continue;

            var subAffix = GameData.getRelicSubAffixExcel(
                    relic.getExcel().getRelicExcel().getSubAffixGroup(),
                    entry.getIntKey());
            if (subAffix != null) {
                int count = Math.min(entry.getIntValue(), 6);
                relic.getSubAffixes().add(new GameItemSubAffix(subAffix, count));
            }
        }

        int upgrades = relic.getMaxNormalSubAffixCount() - relic.getCurrentSubAffixCount();
        if (upgrades > 0) {
            relic.addSubAffixes(upgrades);
        }
    }
}
