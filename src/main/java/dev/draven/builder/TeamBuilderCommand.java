package dev.draven.builder;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.draven.builder.data.CharacterBuildData;
import dev.draven.builder.data.TeamBuildData;
import dev.draven.builder.data.CharacterBuildData.Equipment;
import dev.draven.builder.data.CharacterBuildData.Relic;
import emu.lunarcore.GameConstants;
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
import emu.lunarcore.game.player.lineup.PlayerLineup;
import emu.lunarcore.util.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

@Command(label = "teambuild", aliases = {
        "tb" }, permission = "player.lineup", requireTarget = true, desc = "/teambuild [name]")
public class TeamBuilderCommand implements CommandHandler {

    private static final int MAX_LEVEL = 80;
    private static final int MAX_PROMOTION = 6;
    private static final int DEFAULT_SKILL_LEVEL = 8;
    private static final int MAX_RELIC_LEVEL = 15;
    private static final int EMPTY_EXP = 0;
    private static final int NO_REWARDS = 0b00101010;
    private CommandArgs args;
    private Player player;

    @Override
    public void execute(CommandArgs args) {
        this.args = args;
        this.player = args.getTarget();
        try {
            if (!hasInventorySpace()) {
                args.sendMessage("Error: The targeted player does not have enough space in their inventory.");
                return;
            }

            String name = args.get(0).toLowerCase();
            List<TeamBuildData> teamInformation = loadTeamData();

            String message = setupTeamLineup(teamInformation, name);
            args.getSender().sendMessage(message);
        } catch (Exception e) {
            args.sendMessage("Error processing build command: " + e.getMessage());
            LunarCore.getLogger().error("Error in builder command", e);
        }
    }

    private boolean hasInventorySpace() {
        Inventory inventory = player.getInventory();
        return inventory.getTab(InventoryTabType.RELIC).getAvailableCapacity() > 0 &&
                inventory.getTab(InventoryTabType.EQUIPMENT).getAvailableCapacity() > 0;
    }

    private List<TeamBuildData> loadTeamData() {
        try (FileReader reader = new FileReader(LunarCore.getConfig().getDataDir() + "/TeamDetails.json")) {
            return JsonUtils.loadToList(reader, TeamBuildData.class);
        } catch (Exception e) {
            LunarCore.getLogger().error("Error loading TeamDetails.json", e);
            return Collections.emptyList();
        }
    }

    private Optional<TeamBuildData> findTeamByName(List<TeamBuildData> teamInformation, String teamName) {
        return teamInformation.stream()
                .filter(team -> team.getName().equalsIgnoreCase(teamName))
                .findFirst();
    }

    public String setupTeamLineup(List<TeamBuildData> teamInformation, String teamName) {
        Optional<TeamBuildData> teamOpt = findTeamByName(teamInformation, teamName);

        if (teamOpt.isEmpty()) {
            return "Team '" + teamName + "' not found.";
        }

        TeamBuildData team = teamOpt.get();
        List<Integer> avatarIds = new ArrayList<>();

        for (CharacterBuildData character : team.getBuildData()) {
            GameAvatar avatar = getOrCreateAvatar(character.getAvatarId());
            if (avatar != null) {
                setupAvatar(avatar, character);
                applyNormalBuild(avatar, character);
                avatarIds.add(avatar.getAvatarId());
            }

            if (avatarIds.size() >= GameConstants.MAX_AVATARS_IN_TEAM) {
                return "Team lineup must only have 4 characters.";
            }
        }

        // Set up the lineup
        if (!avatarIds.isEmpty()) {
            setPlayerLineup(avatarIds);
            return "Team lineup for '" + teamName + "' has been set successfully.";
        } else {
            return "No avatars could be added to the lineup.";
        }
    }

    private void applyNormalBuild(GameAvatar avatar, CharacterBuildData character) {
        character.getBuilds().stream()
                .filter(build -> build.getBuildName().equalsIgnoreCase("normal"))
                .findFirst()
                .ifPresent(buildDetail -> {
                    avatar.setRank(buildDetail.getEidolonLevel());
                    unequipAvatarItems(avatar);
                    equipItem(avatar, buildDetail.getEquipment());
                    equipRelics(avatar, buildDetail.getRelics(), character.getDefaultRelics());
                });
    }

    private void setPlayerLineup(List<Integer> avatarIds) {
        PlayerLineup lineup = player.getCurrentLineup();
        lineup.getAvatars().clear();
        lineup.getAvatars().addAll(avatarIds);
        lineup.save();

        lineup.refreshLineup();
        player.getScene().syncLineup();
    }

    private GameAvatar getOrCreateAvatar(int id) {
        GameAvatar avatar = player.getAvatarById(id);

        if (avatar != null) {
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
    private void unequipAvatarItems(GameAvatar avatar) {
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

        Map<String, Integer> skillLevels = buildInfo.getSkillLevel();

        for (int pointId : avatar.getExcel().getSkillTreeIds()) {
            Integer exactLevel = skillLevels.get(String.valueOf(pointId));

            int pointLevel;
            if (exactLevel != null) {
                pointLevel = exactLevel;
            } else {
                var skillTree = GameData.getAvatarSkillTreeExcel(pointId, 1);
                if (skillTree != null) {
                    pointLevel = Math.min(DEFAULT_SKILL_LEVEL, skillTree.getMaxLevel());
                    pointLevel = Math.max(pointLevel, skillTree.isDefaultUnlock() ? 1 : 0);
                } else {
                    continue;
                }
            }
            avatar.getSkills().put(pointId, pointLevel);
        }
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
                    int step = (split.length == 3) ? Integer.parseInt(split[2]) : -1;
                    subAffixMap.put(affixId, new int[] { count, step });
                });

        return subAffixMap;
    }

    private void applySubAffixes(GameItem relic, Int2ObjectMap<int[]> subAffixMap) {
        subAffixMap.forEach((affixId, values) -> {
            int count = values[0];
            int step = values[1];

            if (count > 0) {
                var subAffix = GameData.getRelicSubAffixExcel(relic.getExcel().getRelicExcel().getSubAffixGroup(),
                        affixId);
                if (subAffix != null) {
                    GameItemSubAffix newSubAffix = new GameItemSubAffix(subAffix, Math.min(count, 6));

                    if (step != -1) {
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

    private int getRelicType(int itemId) {
        return itemId % 10;
    }
}
