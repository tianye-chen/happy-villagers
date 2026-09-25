package dev.tianye.happyvillagers.gametest;

import dev.tianye.happyvillagers.HappyConfig;
import dev.tianye.happyvillagers.HappyVillagers;
import dev.tianye.happyvillagers.happiness.HappinessCalculator;
import dev.tianye.happyvillagers.happiness.HappinessData;
import dev.tianye.happyvillagers.happiness.HappinessLevel;
import dev.tianye.happyvillagers.happiness.HappinessManager;
import dev.tianye.happyvillagers.happiness.HomeScanner;
import dev.tianye.happyvillagers.trade.BonusTrade;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Run with {@code ./gradlew runGameTestServer}. Only registered when game tests are enabled (dev runs). */
@GameTestHolder(HappyVillagers.MOD_ID)
@PrefixGameTestTemplate(false)
public class HappinessGameTests {
    private static final String EMPTY = "empty";

    /** 7x7 stone box: floor y0, walls y1-3, roof y4 -> 5x5x3 = 75 blocks of air inside. */
    private static void buildRoom(GameTestHelper helper) {
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                for (int y = 0; y <= 4; y++) {
                    boolean shell = y == 0 || y == 4 || x == 0 || x == 6 || z == 0 || z == 6;
                    helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    @GameTest(template = EMPTY, skyAccess = true)
    public static void furnishedRoomIsAHome(GameTestHelper helper) {
        buildRoom(helper);
        helper.setBlock(new BlockPos(3, 1, 6), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(new BlockPos(3, 2, 6), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(new BlockPos(0, 2, 3), Blocks.GLASS);
        helper.setBlock(new BlockPos(6, 2, 3), Blocks.GLASS_PANE);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.POTTED_POPPY);
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(new BlockPos(5, 1, 5), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));

        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 3, 1, 3);
        villager.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(5, 1, 5))));

        HomeScanner.Result home = HomeScanner.scan(helper.getLevel(), villager);
        check(helper, home.enclosed(), "room should be enclosed");
        check(helper, home.volume() == 73, "expected 75 - 2 bed blocks = 73 volume, got " + home.volume());
        check(helper, home.roof(), "room should have a roof");
        check(helper, home.bed(), "claimed bed should be inside");
        check(helper, home.doors() == 1, "expected 1 door, got " + home.doors());
        check(helper, home.windows() == 2, "expected 2 windows, got " + home.windows());
        check(helper, home.greenery() == 1, "expected 1 greenery, got " + home.greenery());
        check(helper, home.skyAccess(), "door should lead to the sky");
        helper.succeed();
    }

    @GameTest(template = EMPTY, skyAccess = true)
    public static void openFieldIsHomeless(GameTestHelper helper) {
        buildFloor(helper);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 8, 1, 8);
        HomeScanner.Result home = HomeScanner.scan(helper.getLevel(), villager);
        check(helper, !home.enclosed(), "open field must not count as a home");
        check(helper, home.skyAccess(), "open field sees the sky");
        helper.succeed();
    }

    @GameTest(template = EMPTY, skyAccess = true)
    public static void onlyTwoTallGapsLeak(GameTestHelper helper) {
        buildRoom(helper);
        helper.setBlock(new BlockPos(6, 2, 3), Blocks.AIR);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 3, 1, 3);
        HomeScanner.Result oneTall = HomeScanner.scan(helper.getLevel(), villager);
        check(helper, oneTall.enclosed(), "a 1-block tall hole must not break the home");
        check(helper, !oneTall.skyAccess(), "no door and no 2-tall opening means no sky access");

        helper.setBlock(new BlockPos(6, 1, 3), Blocks.AIR);
        HomeScanner.Result twoTall = HomeScanner.scan(helper.getLevel(), villager);
        check(helper, !twoTall.enclosed(), "a 2-block tall opening leaks the fill");
        check(helper, twoTall.skyAccess(), "the 2-tall opening leads outside");
        helper.succeed();
    }

    private static void moveTo(GameTestHelper helper, Villager villager, int x, int y, int z) {
        BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        villager.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    @GameTest(template = EMPTY, skyAccess = true)
    public static void wanderingOutsideKeepsHome(GameTestHelper helper) {
        buildFloor(helper);
        buildRoom(helper);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 3, 1, 3);
        HappinessData data = HappinessManager.get(villager);

        HomeScanner.Result inside = HomeScanner.findHome(helper.getLevel(), villager, data);
        check(helper, inside.enclosed() && data.homeAnchor().isPresent(), "home should be found and remembered while inside");

        moveTo(helper, villager, 12, 1, 12);
        HomeScanner.Result outside = HomeScanner.findHome(helper.getLevel(), villager, data);
        check(helper, outside.enclosed() && outside.volume() == 75,
                "a villager out in the field still has its home, got enclosed=" + outside.enclosed() + " volume=" + outside.volume());

        helper.setBlock(new BlockPos(6, 1, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(6, 2, 3), Blocks.AIR);
        HomeScanner.Result broken = HomeScanner.findHome(helper.getLevel(), villager, data);
        check(helper, !broken.enclosed(), "an opened-up home no longer counts");
        check(helper, data.homeAnchor().isEmpty(), "the broken home should be forgotten");
        helper.succeed();
    }

    @GameTest(template = EMPTY, skyAccess = true)
    public static void claimedBedAnchorsHome(GameTestHelper helper) {
        buildFloor(helper);
        buildRoom(helper);
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(new BlockPos(5, 1, 5), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 12, 1, 12);
        villager.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(5, 1, 5))));

        HomeScanner.Result home = HomeScanner.findHome(helper.getLevel(), villager, HappinessManager.get(villager));
        check(helper, home.enclosed() && home.bed() && home.volume() == 73,
                "the claimed bed's room is home even though the villager never stood in it, got enclosed="
                        + home.enclosed() + " bed=" + home.bed() + " volume=" + home.volume());
        helper.succeed();
    }

    @GameTest(template = EMPTY, skyAccess = true)
    public static void tinyCellIsPenalised(GameTestHelper helper) {
        // 1x1x2 cell, typical of a trading hall
        for (int y = 0; y <= 3; y++) {
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    boolean air = x == 1 && z == 1 && (y == 1 || y == 2);
                    helper.setBlock(new BlockPos(x, y, z), air ? Blocks.AIR : Blocks.STONE);
                }
            }
        }
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 1, 1, 1);
        HomeScanner.Result home = HomeScanner.scan(helper.getLevel(), villager);
        check(helper, home.enclosed() && home.volume() == 2, "expected a closed 2 block cell, got " + home.volume());
        HappinessData data = HappinessManager.get(villager);
        HappinessCalculator.evaluate(helper.getLevel(), villager, data);
        check(helper, data.target() == 0.0, "a 2 block cell should crush happiness to 0, got " + data.target());
        helper.succeed();
    }

    @GameTest(template = EMPTY, skyAccess = true)
    public static void formulasMatchSpec(GameTestHelper helper) {
        check(helper, HappinessCalculator.areaScore(27) == 0.0, "27 is neutral");
        check(helper, Math.abs(HappinessCalculator.areaScore(26) + 0.3) < 1e-9, "-0.3 per block under 27");
        check(helper, Math.abs(HappinessCalculator.areaScore(245) - 2.18) < 1e-9, "+0.01 per block up to 245");
        check(helper, Math.abs(HappinessCalculator.areaScore(500) - 3.455) < 1e-9, "+0.005 per block after 245");

        check(helper, HappinessLevel.of(0.0) == HappinessLevel.SEVERELY_DEPRESSED, "0 = Severely Depressed");
        check(helper, HappinessLevel.of(0.1) == HappinessLevel.DEPRESSED, "0.1 = Depressed");
        check(helper, HappinessLevel.of(1.0) == HappinessLevel.MISERABLE, "1.0 = Miserable");
        check(helper, HappinessLevel.of(4.9) == HappinessLevel.GRUMPY, "4.9 = Grumpy");
        check(helper, HappinessLevel.of(5.0) == HappinessLevel.NEUTRAL, "5.0 = Neutral");
        check(helper, HappinessLevel.of(9.9) == HappinessLevel.POTENTIAL_MAN, "9.9 = Potential Man");
        check(helper, HappinessLevel.of(10.0) == HappinessLevel.ECSTATIC, "10 = Ecstatic");
        helper.succeed();
    }

    @GameTest(template = EMPTY, skyAccess = true)
    public static void defaultBonusTradesParse(GameTestHelper helper) {
        int configured = HappyConfig.BONUS_TRADES.get().size();
        int parsed = BonusTrade.fromConfig(helper.getLevel().registryAccess()).size();
        check(helper, parsed == configured, "only " + parsed + " of " + configured + " bonus trades parsed, see log");
        helper.succeed();
    }

    private static Villager trader(GameTestHelper helper, VillagerProfession profession, int level, double happiness) {
        buildFloor(helper);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 8, 1, 8);
        villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(level));
        HappinessData data = HappinessManager.get(villager);
        data.markInitialized();
        data.setHappiness(happiness);
        return villager;
    }

    @GameTest(template = EMPTY, skyAccess = true)
    public static void ecstaticLibrarianOffersMendingCheaper(GameTestHelper helper) {
        Villager villager = trader(helper, VillagerProfession.LIBRARIAN, 1, 10.0);
        List<MerchantOffer> base = new ArrayList<>(villager.getOffers());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        villager.mobInteract(player, InteractionHand.MAIN_HAND); // vanilla path -> mixin starts the session
        MerchantOffers offers = villager.getOffers();
        check(helper, offers.size() == base.size() + 1, "expected exactly one bonus trade, got " + (offers.size() - base.size()));
        ItemStack bonus = offers.get(offers.size() - 1).getResult();
        check(helper, bonus.is(Items.ENCHANTED_BOOK), "bonus trade should be an enchanted book");
        var stored = bonus.get(DataComponents.STORED_ENCHANTMENTS);
        check(helper, stored != null && stored.getLevel(helper.getLevel().registryAccess().holderOrThrow(Enchantments.MENDING)) == 1,
                "bonus book should be Mending I");
        for (MerchantOffer offer : base) {
            int expected = (int) Math.round(offer.getBaseCostA().getCount() * -0.3);
            check(helper, offer.getSpecialPriceDiff() == expected,
                    "expected price diff " + expected + " but got " + offer.getSpecialPriceDiff());
        }

        CompoundTag saved = villager.saveWithoutId(new CompoundTag());
        int savedCount = saved.getCompound("Offers").getList("Recipes", Tag.TAG_COMPOUND).size();
        check(helper, savedCount == base.size(), "mid-trade save must write the real offers, wrote " + savedCount);

        villager.setTradingPlayer(null); // closing the screen -> mixin restores the offers
        check(helper, villager.getOffers().size() == base.size(), "offers should be restored after trading");
        for (MerchantOffer offer : villager.getOffers()) {
            check(helper, offer.getSpecialPriceDiff() == 0, "price diffs should be reset after trading");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, skyAccess = true)
    public static void unhappyVillagerWithholdsAndOvercharges(GameTestHelper helper) {
        Villager villager = trader(helper, VillagerProfession.FARMER, 1, 2.0);
        MerchantOffers offers = villager.getOffers();
        while (offers.size() < 8) {
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 5), new ItemStack(Items.STICK), 12, 1, 0.05F));
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        villager.mobInteract(player, InteractionHand.MAIN_HAND);

        // lockTradesBelow 4.0: at 2.0 half of the 8 trades remain; markup = 1.0 * (5 - 2) / 5 = +60%
        check(helper, villager.getOffers().size() == 4, "expected 4 visible trades, got " + villager.getOffers().size());
        for (MerchantOffer offer : villager.getOffers()) {
            int expected = (int) Math.round(offer.getBaseCostA().getCount() * 0.6);
            check(helper, offer.getSpecialPriceDiff() == expected, "expected markup " + expected + ", got " + offer.getSpecialPriceDiff());
        }
        villager.setTradingPlayer(null);
        check(helper, villager.getOffers().size() == 8, "withheld trades must come back after trading");
        helper.succeed();
    }

    @GameTest(template = EMPTY, skyAccess = true)
    public static void neutralVillagerTradesLikeVanilla(GameTestHelper helper) {
        Villager villager = trader(helper, VillagerProfession.LIBRARIAN, 1, 5.5);
        int count = villager.getOffers().size();
        villager.mobInteract(helper.makeMockPlayer(GameType.SURVIVAL), InteractionHand.MAIN_HAND);
        check(helper, villager.getOffers().size() == count, "neutral villager must not add or remove trades");
        for (MerchantOffer offer : villager.getOffers()) {
            check(helper, offer.getSpecialPriceDiff() == 0, "neutral villager must not change prices");
        }
        villager.setTradingPlayer(null);
        helper.succeed();
    }

    @GameTest(template = EMPTY, skyAccess = true)
    public static void villagerQuitsAtZero(GameTestHelper helper) {
        Villager villager = trader(helper, VillagerProfession.FARMER, 3, 0.0);
        helper.succeedWhen(() -> {
            check(helper, villager.getVillagerData().getProfession() == VillagerProfession.NONE, "villager should have quit");
            check(helper, HappinessManager.get(villager).hasQuit(), "quit flag should be set");
            check(helper, villager.getOffers().isEmpty(), "a villager who quit has no trades");
        });
    }
}
