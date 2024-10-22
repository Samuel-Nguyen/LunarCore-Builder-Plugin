package dev.draven.builder;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import dev.draven.builder.data.CharacterBuildData;
import dev.draven.builder.data.CharacterBuildData.BuildDetail;
import dev.draven.builder.data.CharacterBuildData.Equipment;
import dev.draven.builder.data.CharacterBuildData.Relic;
import emu.lunarcore.LunarCore;
import emu.lunarcore.command.Command;
import emu.lunarcore.command.CommandArgs;
import emu.lunarcore.command.CommandHandler;
import emu.lunarcore.data.GameData;
import emu.lunarcore.data.excel.ItemExcel;
import emu.lunarcore.game.avatar.GameAvatar;
import emu.lunarcore.game.inventory.GameItem;
import emu.lunarcore.game.inventory.GameItemSubAffix;
import emu.lunarcore.game.inventory.Inventory;
import emu.lunarcore.game.inventory.tabs.InventoryTabType;
import emu.lunarcore.game.player.Player;
import emu.lunarcore.util.JsonUtils;
import emu.lunarcore.util.Utils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

@Command(
    label = "build",
    aliases = { "b" },
    permission = "player.give",
    requireTarget = true,
    desc = "/build [all/nickname/id] (build name) (-max)"
)
public class BuilderCommand implements CommandHandler {

    private static final int MAX_LEVEL = 80;
    private static final int MAX_PROMOTION = 6;
    private static final int MAX_RELIC_LEVEL = 15;
    private static final int EMPTY_EXP = 0;
    private static final int NO_REWARDS = 0b00101010;
    private CommandArgs args;

    @Override
    public void execute(CommandArgs args) {
        this.args = args;
        if (!hasInventorySpace()) {
            args.sendMessage("Error: The targeted player does not have enough space in their inventory.");
            return;
        }

        String message = parseBuildData();
        args.getSender().sendMessage(message);
    }

    private boolean hasInventorySpace() {
        Inventory inventory = args.getTarget().getInventory();
        return hasAvailableCapacity(inventory, InventoryTabType.RELIC) &&
                hasAvailableCapacity(inventory, InventoryTabType.EQUIPMENT);
    }

    private boolean hasAvailableCapacity(Inventory inventory, InventoryTabType type) {
        return inventory.getTab(type).getAvailableCapacity() > 0;
    }

    private String parseBuildData() {
        String input = args.get(0).toLowerCase();
        String buildName = args.get(1).isEmpty() ? "normal" : args.get(1).toLowerCase();
        List<CharacterBuildData> buildInformation = loadBuildInformation();

        if (buildInformation.isEmpty()) {
            return "No builds found. Please define one.";
        }

        return switch (input) {
            case "all", "a" -> processAllBuilds(buildInformation, buildName);
            default -> processSpecificBuild(buildInformation, input, buildName);
        };
    }

    private List<CharacterBuildData> loadBuildInformation() {
        try (FileReader fileReader = new FileReader(LunarCore.getConfig().getDataDir() + "/BuildDetails.json")) {
            return JsonUtils.loadToList(fileReader, CharacterBuildData.class);
        } catch (Exception e) {
            LunarCore.getLogger().error("Error loading BuildDetails.json", e);
            return Collections.emptyList();
        }
    }

    private String processAllBuilds(List<CharacterBuildData> buildInformation, String buildName) {
        long total = buildInformation.stream()
                .filter(buildInfo -> generateBuild(buildInfo, buildName))
                .count();

        return "Gave " + total + " characters relics for " + buildName.toUpperCase() + " build.";
    }

    private String processSpecificBuild(List<CharacterBuildData> buildInformation, String input, String buildName) {
        Optional<CharacterBuildData> buildInfoOpt = findBuild(buildInformation, input);

        if (buildInfoOpt == null) {
            return "Character not found.";
        }

        return buildInfoOpt.map(buildInfo -> {
            generateBuild(buildInfo, buildName);
            return "";
        }).orElse("Build not found for input: " + input);
    }

    private Optional<CharacterBuildData> findBuild(List<CharacterBuildData> buildInformation, String input) {
        return isNumeric(input)
                ? buildInformation.stream().filter(b -> b.getAvatarId() == Utils.parseSafeInt(input)).findFirst()
                : buildInformation.stream().filter(b -> b.getAvatarName().equalsIgnoreCase(input)).findFirst();
    }

    public Boolean generateBuild(CharacterBuildData buildInfo, String buildName) {
        Player player = args.getTarget();
        GameAvatar avatar = getOrCreateAvatar(buildInfo.getAvatarId(), player);
        if (avatar == null)
            return false;

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
        if (excel == null) {
            LunarCore.getLogger().error("Avatar Excel data not found for ID: " + id);
            return null;
        }

        avatar = new GameAvatar(excel);
        player.addAvatar(avatar);
        return avatar;
    }

    @Deprecated
    private void unequipAvatarItems(GameAvatar avatar, Player player) {
        List<GameItem> unequipList = avatar.getEquips().keySet().stream()
                .map(avatar::unequipItem)
                .filter(item -> item != null)
                .toList();

        player.getInventory().removeItems(unequipList);
    }

    private void setupAvatar(GameAvatar avatar, CharacterBuildData buildInfo) {
        avatar.setLevel(MAX_LEVEL);
        avatar.setExp(EMPTY_EXP);
        avatar.setPromotion(MAX_PROMOTION);
        avatar.setRewards(NO_REWARDS);

        for (int pointId : avatar.getExcel().getSkillTreeIds()) {
            var skillTree = GameData.getAvatarSkillTreeExcel(pointId, 1);
            if (skillTree != null) {
                int pointLevel = Math.min(buildInfo.getSkillLevel(), skillTree.getMaxLevel());
                pointLevel = Math.max(pointLevel, skillTree.isDefaultUnlock() ? 1 : 0);
                avatar.getSkills().put(pointId, pointLevel);
            }
        }
    }

    private void applyBuild(GameAvatar avatar, CharacterBuildData buildInfo, String buildName) {
        BuildDetail buildDetail = getBuildDetail(buildInfo, buildName);
        avatar.setRank(buildDetail.getEidolonLevel());
        equipItem(avatar, buildDetail.getEquipment());
        equipRelics(avatar, buildDetail.getRelics(), buildInfo.getDefaultRelics());
    }

    private BuildDetail getBuildDetail(CharacterBuildData buildInfo, String buildName) {
        Optional<BuildDetail> buildDetailOpt = buildInfo.getBuilds().stream()
                .filter(detail -> detail.getBuildName().equalsIgnoreCase(buildName))
                .findFirst();

        if (buildDetailOpt.isEmpty()) {
            String fallbackBuildName = buildInfo.getBuilds().get(0).getBuildName();
            args.sendMessage("Warning: Build '" + buildName.toUpperCase() + "' not found for character " + buildInfo.getFullName() + "."
                + " Applying the '" + fallbackBuildName.toUpperCase() + "' build instead.");
            args.sendMessage("Gave " + buildInfo.getFullName() + " relics for " + fallbackBuildName.toUpperCase() + " build.");
        }

        return buildDetailOpt.orElse(buildInfo.getBuilds().get(0));
    }

    @Deprecated
    private void equipItem(GameAvatar avatar, Equipment equipmentData) {
        if (equipmentData != null) {
            var excel = GameData.getItemExcelMap().get(equipmentData.getItemId());
            if (excel != null) {
                GameItem equipment = createGameItem(excel, equipmentData.getEnhancementLevel());
                avatar.getOwner().getInventory().addItem(equipment);
                avatar.equipItem(equipment);
            }
        }
    }

    private GameItem createGameItem(ItemExcel excel, int enhancementLevel) {
        GameItem equipment = new GameItem(excel);
        equipment.setLevel(MAX_LEVEL);
        equipment.setExp(EMPTY_EXP);
        equipment.setPromotion(MAX_PROMOTION);
        equipment.setRank(enhancementLevel);
        return equipment;
    }

    @Deprecated
    private void equipRelics(GameAvatar avatar, List<Relic> buildRelics, List<Relic> defaultRelics) {
        List<Relic> appliedRelics = new ArrayList<>();

        for (Relic defaultRelic : defaultRelics) {
            int defaultType = getRelicType(defaultRelic.getItemId());

            Relic buildRelic = buildRelics.stream()
                    .filter(r -> getRelicType(r.getItemId()) == defaultType)
                    .findFirst()
                    .orElse(null);

            if (buildRelic != null) {
                Relic appliedRelic = new Relic();
                appliedRelic.setItemId(buildRelic.getItemId());

                appliedRelic.setPrimaryAffixId(
                        Optional.ofNullable(buildRelic.getPrimaryAffixId()).orElse(defaultRelic.getPrimaryAffixId()));
                appliedRelic.setSubAffixes(
                        Optional.ofNullable(buildRelic.getSubAffixes()).orElse(defaultRelic.getSubAffixes()));

                appliedRelics.add(appliedRelic);
            } else {
                appliedRelics.add(defaultRelic);
            }
        }

        for (Relic relicData : appliedRelics) {
            var excel = GameData.getItemExcelMap().get(relicData.getItemId());
            if (excel != null) {
                GameItem relic = new GameItem(excel);
                setupRelic(relic, relicData);
                avatar.getOwner().getInventory().addItem(relic);
                avatar.equipItem(relic);
            }
        }
    }

    private void setupRelic(GameItem relic, Relic relicData) {
        relic.setLevel(MAX_RELIC_LEVEL);
        relic.setExp(EMPTY_EXP);
        relic.setMainAffix(Optional.ofNullable(relicData.getPrimaryAffixId()).orElse(1));
        relic.resetSubAffixes();

        Int2ObjectMap<int[]> subAffixMap = parseSubAffixes(relicData.getSubAffixes());
        applySubAffixes(relic, subAffixMap);

        if (args.hasFlag("-max")) {
            applyPerfectSubAffixes(relic);
        }
    }

    private Int2ObjectMap<int[]> parseSubAffixes(String subAffixData) {
        Int2ObjectMap<int[]> subAffixMap = new Int2ObjectOpenHashMap<>();

        Arrays.stream(subAffixData.split(" "))
                .map(s -> s.split(":"))
                .filter(split -> split.length >= 2)
                .forEach(split -> {
                    int affixId = Integer.parseInt(split[0]);
                    int count = Integer.parseInt(split[1]);
                    int step = (split.length == 3) ? Integer.parseInt(split[2]) : 0;
                    subAffixMap.put(affixId, new int[]{count, step});
                });

        return subAffixMap;
    }

    private void applySubAffixes(GameItem relic, Int2ObjectMap<int[]> subAffixMap) {
        subAffixMap.forEach((affixId, values) -> {
            int count = values[0];
            int step = values[1];

            if (count > 0) {
                var subAffix = GameData.getRelicSubAffixExcel(relic.getExcel().getRelicExcel().getSubAffixGroup(), affixId);
                if (subAffix != null) {
                    GameItemSubAffix newSubAffix = new GameItemSubAffix(subAffix, Math.min(count, 6));

                    if (step != 0) {
                        newSubAffix.setStep(Math.min(step, count * 2));
                    }

                    relic.getSubAffixes().add(newSubAffix);
                }
            }
        });

        int upgrades = relic.getMaxNormalSubAffixCount() - relic.getCurrentSubAffixCount();
        if (upgrades > 0) {
            relic.addSubAffixes(upgrades);
        }
    }

    private void applyPerfectSubAffixes(GameItem relic) {
        if (relic.getSubAffixes() == null) {
            relic.resetSubAffixes();
        }

        relic.getSubAffixes().forEach(subAffix -> subAffix.setStep(subAffix.getCount() * 2));
    }

    private static boolean isNumeric(String str) {
        return str != null && str.matches("\\d+");
    }

    private int getRelicType(int itemId) {
        return itemId % 10;
    }
}
