package dev.draven.builder;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import dev.draven.builder.data.BuilderData;
import dev.draven.builder.data.BuilderData.BuildDetail;
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

@Command(label = "build", aliases = {
        "b" }, permission = "player.give", requireTarget = true, desc = "/build [character id]")
public class BuilderCommand implements CommandHandler {

    public void execute(CommandArgs args) {
        // Sanity check materials
        var inventory = args.getTarget().getInventory();
        if (inventory.getTab(InventoryTabType.RELIC).getAvailableCapacity() <= 0
                || inventory.getTab(InventoryTabType.EQUIPMENT).getAvailableCapacity() <= 0) {
            args.sendMessage("Error: The targeted player does not has enough space in their relic/lightcone inventory");
            return;
        }

        String message = parseBuildData(args);
        args.getSender()
                .sendMessage(message);

    }

    private static boolean isNumeric(String str) {
        return str != null && str.matches("\\d+");
    }

    private String parseBuildData(CommandArgs args) {
        String message = "Don't have any builds. Please define one.";
        int total = 0;
        String input = args.get(0).toLowerCase();
        String buildName = args.get(1).equals("") ? "normal" : args.get(1).toLowerCase();
        Boolean build = false;

        try (FileReader fileReader = new FileReader(LunarCore.getConfig().getDataDir() + "/BuildDetails.json")) {
            List<BuilderData> buildInformation = JsonUtils.loadToList(fileReader, BuilderData.class);
            switch (input) {
                case "all", "a" -> {
                    for (BuilderData buildInfo : buildInformation) {
                        build = generateBuild(args, buildInfo, args.getTarget(), buildName);
                        if (build) {
                            total++;
                        }
                    }
                }
                default -> {
                    if (isNumeric(input)) {
                        int id = Utils.parseSafeInt(input);
                        for (BuilderData buildInfo : buildInformation) {
                            if (buildInfo.getAvatarId() != id) {
                                continue;
                            }
                            generateBuild(args, buildInfo, args.getTarget(), buildName);
                            message = "Give " + buildInfo.getFullName() + " relics for " + buildName.toUpperCase() + " build.";
                            break;
                        }
                    } else {
                        for (BuilderData buildInfo : buildInformation) {
                            if (!buildInfo.getAvatarName().equals(input)) {
                                continue;
                            }
                            generateBuild(args, buildInfo, args.getTarget(), buildName);
                            message = "Give " + buildInfo.getFullName() + " relics for " + buildName.toUpperCase() + " build.";
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // TODO: handle exception
            args.sendMessage("Something wrong!!");
        }

        if (total > 0) {
            message = "Give " + total + " characters relics for " + buildName.toUpperCase() + " build.";
        }

        return message;
    }

    public Boolean generateBuild(CommandArgs args, BuilderData buildInfo, Player player, String buildName) {
        int id = buildInfo.getAvatarId();
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
            // Validate avatar excel (In case we are using an older version of the server
            var excel = GameData.getAvatarExcelMap().get(id);

            // Create avatar
            avatar = new GameAvatar(excel);

            // Gives the avatar to player
            player.addAvatar(avatar);
        }

        // Set avatar basic data
        avatar.setLevel(80);
        avatar.setExp(0);
        avatar.setPromotion(6);
        avatar.setRewards(0b00101010);

        // Set avatar skills
        for (int pointId : avatar.getExcel().getSkillTreeIds()) {
            var skillTree = GameData.getAvatarSkillTreeExcel(pointId, 1);
            if (skillTree == null)
                continue;

            int minLevel = skillTree.isDefaultUnlock() ? 1 : 0;
            int pointLevel = Math.max(Math.min(buildInfo.getSkill(), skillTree.getMaxLevel()), minLevel);

            avatar.getSkills().put(pointId, pointLevel);
        }

        // Get build information
        var buildList = buildInfo.getBuildList();

        // Return immediately if build list is empty
        if (buildList.isEmpty()) {
            return false;
        }

        // Check build list
        BuildDetail buildDetail = new BuildDetail();
        BuildDetail[] result = buildList.stream()
                .filter(detail -> detail.getBuildName().equals(buildName))
                .toArray(BuildDetail[]::new);

        if (result.length == 0 && buildList.size() > 1) {
            args.getSender()
                    .sendMessage(buildInfo.getFullName() + " don't have " + buildName.toUpperCase() + " build.");
            buildDetail = buildList.get(0);
            args.getSender()
                    .sendMessage("Revert " + buildInfo.getFullName() + " to " + buildDetail.getBuildName().toUpperCase() + " build.");
        }

        buildDetail = (result.length > 0) ? result[0] : buildList.get(0);

        // Set avatar eidolons
        avatar.setRank(buildDetail.getEidolon());

        // Set equipment
        var equipmentData = buildDetail.getEquipment();
        if (equipmentData != null) {
            // Validate equipment excel (In case we are using an older version of the server
            var excel = GameData.getItemExcelMap().get(equipmentData.getTid());
            if (excel != null) {
                // Create equipment
                var equipment = new GameItem(excel);

                // Set equipment props
                equipment.setLevel(80);
                equipment.setExp(0);
                equipment.setPromotion(6);
                equipment.setRank(equipmentData.getImposition());

                // Add to player
                player.getInventory().addItem(equipment);
                avatar.equipItem(equipment);
            }
        }

        // Set relics
        for (var relicData : buildDetail.getRelicList()) {
            // Validate relic excel (In case we are using an older version of the server)
            var excel = GameData.getItemExcelMap().get(relicData.getTid());
            if (excel == null)
                continue;

            // Create relic
            var relic = new GameItem(excel);
            int mainAffixId = relicData.getMainAffixId() != null ? relicData.getMainAffixId() : 1;

            // Set relic props
            relic.setLevel(15);
            relic.setExp(0);
            relic.setMainAffix(mainAffixId);

            // Set sub-stats
            relic.resetSubAffixes();

            String[] subAffixList = relicData.getSubAffix().split(" ");
            Int2IntMap subAffixMap = new Int2IntOpenHashMap();

            for (String subAffix : subAffixList) {
                String[] split = subAffix.split("[:,]");
                if (split.length >= 2) {
                    int key = Integer.parseInt(split[0]);
                    int value = Integer.parseInt(split[1]);

                    subAffixMap.put(key, value);
                }
            }

            if (subAffixMap != null) {
                // Reset sub-stats first

                for (var entry : subAffixMap.int2IntEntrySet()) {
                    if (entry.getIntValue() <= 0)
                        continue;

                    var subAffix = GameData.getRelicSubAffixExcel(
                            relic.getExcel().getRelicExcel().getSubAffixGroup(),
                            entry.getIntKey());
                    if (subAffix == null)
                        continue;

                    // Set count
                    int count = Math.min(entry.getIntValue(), 6);
                    relic.getSubAffixes().add(new GameItemSubAffix(subAffix, count));
                }
            }

            // Apply sub stat upgrades to the relic
            int upgrades = relic.getMaxNormalSubAffixCount() - relic.getCurrentSubAffixCount();
            if (upgrades > 0) {
                relic.addSubAffixes(upgrades);
            }

            if (args.hasFlag("-max")) {
                if (relic.getSubAffixes() == null) {
                    relic.resetSubAffixes();
                }

                relic.getSubAffixes().forEach(subAffix -> subAffix.setStep(subAffix.getCount() * 2));
            }

            // Add to player
            player.getInventory().addItem(relic);
            avatar.equipItem(relic);
        }

        // Save
        avatar.save();

        return true;
    }
}
