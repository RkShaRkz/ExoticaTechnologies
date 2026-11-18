package exoticatechnologies.modifications.tests

import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import exoticatechnologies.modifications.ShipModLoader
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.VariantTagProvider
import exoticatechnologies.util.tests.utils.FakeFleetMember
import exoticatechnologies.util.tests.utils.FakeGame
import exoticatechnologies.util.tests.utils.FakeVariantTree
import org.junit.After
import org.junit.Assert
import org.junit.Test

/**
 * Unit tests for [ShipModLoader.getWholeShipMods] (issue #76, F1): the whole-ship read must
 * return the modifications of EVERY module slot, also when several slots mount the same module
 * variant, and must never list the same modifications twice.
 *
 * They go through the public entry point with all three providers in place, on fake variant
 * trees. Modules are told apart by the bandwidth stored in their tag.
 */
class ShipModLoaderWholeShipTests {

    private val segmentSlots = listOf("SEGMENT1", "SEGMENT2", "SEGMENT3", "SEGMENT4", "SEGMENT5")
    private val segmentId = "train_segment_standard"

    /**
     * @param isInRefitScreen false: campaign, the variant-tag cache is filled by reads; true:
     * refit or fleet screen, every read decodes the tag
     */
    private fun startGame(isInRefitScreen: Boolean) {
        FakeGame.install(isInRefitScreen)
        // The loader reads through the shared provider instance; start every test without
        // anything a previous test may have cached in it.
        VariantTagProvider.getInstance().clearCache()
    }

    @After
    fun tearDown() {
        VariantTagProvider.getInstance().clearCache()
        FakeGame.uninstall()
    }

    /** A ship that mounts the same module variant in five slots, like the Helltrain. */
    private fun train(): ShipVariantAPI {
        return FakeVariantTree.shipWithIdenticalModules("train_warfreighter", segmentId, segmentSlots)
    }

    private fun shipMember(id: String, variant: ShipVariantAPI): FleetMemberAPI {
        return FakeFleetMember.member(id, "ISS Unit Test", variant)
    }

    /** Tags [variant] the way the provider does, with modifications of the given bandwidth. */
    private fun tagWithBandwidth(variant: ShipVariantAPI, bandwidth: Float) {
        variant.addTag(EXOTICA_INDICATOR + "{\"baseBandwidth\":" + bandwidth + "}")
    }

    /** The bandwidths of the listed modifications, sorted, so a test can name them in any order. */
    private fun bandwidthsOf(wholeShipMods: List<ShipModifications>): List<Float> {
        return wholeShipMods.map { it.bandwidth }.sorted()
    }

    /** Tags the root with 99 and SEGMENT1..5 with 11..15. */
    private fun tagEveryNode(root: ShipVariantAPI) {
        tagWithBandwidth(root, 99f)
        var bandwidth = 11f
        for (slotId in segmentSlots) {
            tagWithBandwidth(root.getModuleVariant(slotId), bandwidth)
            bandwidth += 1f
        }
    }

    // ---- identical modules ----

    @Test
    fun testEveryIdenticalModuleIsReadInTheCampaign() {
        startGame(false)
        val campaign = train()
        tagEveryNode(campaign)

        val wholeShipMods = ShipModLoader.getWholeShipMods(shipMember("fake-member-1", campaign), campaign)

        // Before the fix this was [11.0, 99.0]: SEGMENT2..5 were skipped as "seen".
        Assert.assertEquals(listOf(11f, 12f, 13f, 14f, 15f, 99f), bandwidthsOf(wholeShipMods))
    }

    @Test
    fun testEveryIdenticalModuleIsReadOnTheRefitScreen() {
        startGame(true)
        val campaign = train()
        tagEveryNode(campaign)

        val wholeShipMods = ShipModLoader.getWholeShipMods(shipMember("fake-member-1", campaign), campaign)

        Assert.assertEquals(listOf(11f, 12f, 13f, 14f, 15f, 99f), bandwidthsOf(wholeShipMods))
    }

    @Test
    fun testModificationsThatOnlyALaterIdenticalModuleCarriesAreNotMissed() {
        // The situation the hullmod keep/strip decision depends on: the only installation of the
        // ship sits on the fourth of five identical modules.
        startGame(false)
        val campaign = train()
        tagWithBandwidth(campaign.getModuleVariant("SEGMENT4"), 44f)

        val wholeShipMods = ShipModLoader.getWholeShipMods(shipMember("fake-member-1", campaign), campaign)

        // Before the fix the list was empty.
        Assert.assertEquals(listOf(44f), bandwidthsOf(wholeShipMods))
    }

    @Test
    fun testAModuleVariantPassedInStillReadsTheWholeShip() {
        startGame(false)
        val campaign = train()
        tagEveryNode(campaign)

        val wholeShipMods = ShipModLoader.getWholeShipMods(shipMember("fake-member-1", campaign), campaign.getModuleVariant("SEGMENT2"))

        Assert.assertEquals(listOf(11f, 12f, 13f, 14f, 15f, 99f), bandwidthsOf(wholeShipMods))
    }

    // ---- nothing is listed twice ----

    @Test
    fun testASharedStockModuleObjectIsListedOnce() {
        // Until a slot gets its own per-ship copy, the game mounts ONE shared stock variant
        // object in every slot that uses it. Three slots, one object, one set of installations.
        startGame(false)
        val shared = FakeVariantTree.variant(segmentId)
        tagWithBandwidth(shared, 77f)
        val modules = LinkedHashMap<String, ShipVariantAPI>()
        modules["SEGMENT1"] = shared
        modules["SEGMENT2"] = shared
        modules["SEGMENT3"] = shared
        val campaign = FakeVariantTree.variant("train_warfreighter", modules)

        val wholeShipMods = ShipModLoader.getWholeShipMods(shipMember("fake-member-1", campaign), campaign)

        Assert.assertEquals(listOf(77f), bandwidthsOf(wholeShipMods))
    }

    @Test
    fun testRepeatedReadsReturnTheSameModificationsEachTime() {
        startGame(false)
        val campaign = train()
        tagEveryNode(campaign)
        val member = shipMember("fake-member-1", campaign)

        val first = ShipModLoader.getWholeShipMods(member, campaign)
        val second = ShipModLoader.getWholeShipMods(member, campaign)

        Assert.assertEquals(6, first.size)
        Assert.assertEquals(6, second.size)
        for (index in first.indices) {
            // Served from the cache the second time: the same objects, in the same order.
            Assert.assertSame(first[index], second[index])
        }
    }

    @Test
    fun testASharedStockModuleObjectIsListedOnceOnTheRefitScreen() {
        // The same on the refit screen, where nothing is cached and every read decodes the tag:
        // the object is still read once, not once per slot.
        startGame(true)
        val shared = FakeVariantTree.variant(segmentId)
        tagWithBandwidth(shared, 77f)
        val modules = LinkedHashMap<String, ShipVariantAPI>()
        modules["SEGMENT1"] = shared
        modules["SEGMENT2"] = shared
        modules["SEGMENT3"] = shared
        val campaign = FakeVariantTree.variant("train_warfreighter", modules)

        val wholeShipMods = ShipModLoader.getWholeShipMods(shipMember("fake-member-1", campaign), campaign)

        Assert.assertEquals(listOf(77f), bandwidthsOf(wholeShipMods))
    }

    // ---- read through a module-level member ----

    @Test
    fun testEveryIdenticalModuleIsReadThroughAModuleLevelMember() {
        // The read is made for the transient member of one module (unnamed, its variant is the
        // module alone). The walk still covers the whole ship and tells the five segments apart.
        startGame(false)
        val campaign = train()
        tagEveryNode(campaign)
        val moduleVariant = campaign.getModuleVariant("SEGMENT2")
        val moduleMember = FakeFleetMember.member("fake-module-member", null, moduleVariant)

        val wholeShipMods = ShipModLoader.testsOnly_collectWholeShip(moduleMember, campaign, moduleVariant)

        Assert.assertEquals(listOf(11f, 12f, 13f, 14f, 15f, 99f), bandwidthsOf(wholeShipMods))
    }

    @Test
    fun testModificationsOnALaterIdenticalModuleAreSeenThroughAModuleLevelMember() {
        // The input of the keep/strip decision in ExoticaTechHM.advanceInCampaign's branch for a
        // child-module member: an empty record on the first segment, the only installation on
        // the fourth.
        startGame(false)
        val campaign = train()
        tagWithBandwidth(campaign.getModuleVariant("SEGMENT1"), 0f)
        tagWithBandwidth(campaign.getModuleVariant("SEGMENT4"), 44f)
        val moduleVariant = campaign.getModuleVariant("SEGMENT1")
        val moduleMember = FakeFleetMember.member("fake-module-member", null, moduleVariant)

        val wholeShipMods = ShipModLoader.testsOnly_collectWholeShip(moduleMember, campaign, moduleVariant)

        Assert.assertEquals(listOf(0f, 44f), bandwidthsOf(wholeShipMods))
    }

    // ---- the member's refit graph ----

    @Test
    fun testRefitCopyOfTheWholeShipAddsNothingTheRootTreeAlreadyHas() {
        // Ship tab: the refit variant is a copy of the whole ship with the same tags. Every
        // module has the same key in both graphs and is listed once.
        startGame(false)
        val campaign = train()
        tagEveryNode(campaign)
        val refitCopy = FakeVariantTree.copyOf(campaign)

        val wholeShipMods = ShipModLoader.testsOnly_collectWholeShip(shipMember("fake-member-1", campaign), campaign, refitCopy)

        Assert.assertEquals(listOf(11f, 12f, 13f, 14f, 15f, 99f), bandwidthsOf(wholeShipMods))
    }

    @Test
    fun testWorkingCopyOfAMountedModuleIsNotListedASecondTime() {
        // Module tab, read for the module-level member. Its variant is the node mounted in
        // SEGMENT2 and its refit variant a working copy of it: the copy is placed in that slot,
        // has the key the root tree already used, and is skipped.
        startGame(false)
        val campaign = train()
        tagEveryNode(campaign)
        val workingCopy = FakeVariantTree.variant(segmentId)
        tagWithBandwidth(workingCopy, 12f)
        val moduleMember = FakeFleetMember.member("fake-module-member", null, campaign.getModuleVariant("SEGMENT2"))

        val wholeShipMods = ShipModLoader.testsOnly_collectWholeShip(moduleMember, campaign, workingCopy)

        Assert.assertEquals(listOf(11f, 12f, 13f, 14f, 15f, 99f), bandwidthsOf(wholeShipMods))
    }

    @Test
    fun testInstallationOnAWorkingCopyThatCannotBePlacedIsStillSeen() {
        // Module tab on one of five identical segments, read for the module-level member, and
        // nothing says which segment it is: neither its variant nor the working copy is a node
        // of the ship's tree. The copy holds the ship's only installation, and the keep/strip
        // decision must see it.
        startGame(false)
        val campaign = train()
        val workingCopy = FakeVariantTree.variant(segmentId)
        tagWithBandwidth(workingCopy, 77f)
        val moduleMember = FakeFleetMember.member("fake-module-member", null, FakeVariantTree.variant(segmentId))

        val wholeShipMods = ShipModLoader.testsOnly_collectWholeShip(moduleMember, campaign, workingCopy)

        Assert.assertEquals(listOf(77f), bandwidthsOf(wholeShipMods))
    }

    @Test
    fun testWorkingCopyThatCannotBePlacedIsListedNextToItsOlderRecordInTheRootTree() {
        // The known price of reading a graph that cannot be placed: the module it stands for is
        // also in the root tree, so that one module is listed twice (13 from the root tree and
        // the working copy's 30) until the refit screen saves the variant.
        startGame(false)
        val campaign = train()
        tagEveryNode(campaign)
        val workingCopy = FakeVariantTree.variant(segmentId)
        tagWithBandwidth(workingCopy, 30f)
        val moduleMember = FakeFleetMember.member("fake-module-member", null, FakeVariantTree.variant(segmentId))

        val wholeShipMods = ShipModLoader.testsOnly_collectWholeShip(moduleMember, campaign, workingCopy)

        Assert.assertEquals(listOf(11f, 12f, 13f, 14f, 15f, 30f, 99f), bandwidthsOf(wholeShipMods))
    }

    // ---- ships without identical modules, and twins, keep working as before ----

    @Test
    fun testEveryDistinctModuleIsRead() {
        startGame(false)
        val modules = LinkedHashMap<String, ShipVariantAPI>()
        modules["LEFT"] = FakeVariantTree.variant("ship_left_Start")
        modules["RIGHT"] = FakeVariantTree.variant("ship_right_Start")
        val campaign = FakeVariantTree.variant("ship_Start", modules)
        tagWithBandwidth(campaign, 99f)
        tagWithBandwidth(campaign.getModuleVariant("LEFT"), 11f)
        tagWithBandwidth(campaign.getModuleVariant("RIGHT"), 12f)

        val wholeShipMods = ShipModLoader.getWholeShipMods(shipMember("fake-member-1", campaign), campaign)

        Assert.assertEquals(listOf(11f, 12f, 99f), bandwidthsOf(wholeShipMods))
    }

    @Test
    fun testSingleModuleShipReturnsItsOwnModifications() {
        startGame(false)
        val campaign = FakeVariantTree.variant("ship_Start")
        tagWithBandwidth(campaign, 50f)

        val wholeShipMods = ShipModLoader.getWholeShipMods(shipMember("fake-member-1", campaign), campaign)

        Assert.assertEquals(listOf(50f), bandwidthsOf(wholeShipMods))
    }

    @Test
    fun testTwoShipsOfTheSameHullEachReturnTheirOwnModules() {
        // Two ships of the same hull: every node of one has a twin with the same hullVariantId
        // in the same slot of the other. Each read walks its own ship's tree only.
        startGame(false)
        val firstShip = train()
        val secondShip = train()
        tagWithBandwidth(firstShip.getModuleVariant("SEGMENT2"), 22f)
        tagWithBandwidth(secondShip.getModuleVariant("SEGMENT5"), 55f)

        val modsOfFirst = ShipModLoader.getWholeShipMods(shipMember("fake-member-1", firstShip), firstShip)
        val modsOfSecond = ShipModLoader.getWholeShipMods(shipMember("fake-member-2", secondShip), secondShip)

        Assert.assertEquals(listOf(22f), bandwidthsOf(modsOfFirst))
        Assert.assertEquals(listOf(55f), bandwidthsOf(modsOfSecond))
    }

    companion object {
        /** The prefix of the variant tag Exotica stores its data in (VariantTagProvider). */
        private const val EXOTICA_INDICATOR = "\$\$EXOTICA\$\$"
    }
}
