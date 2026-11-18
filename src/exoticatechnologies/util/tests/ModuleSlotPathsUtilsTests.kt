package exoticatechnologies.util.tests

import com.fs.starfarer.api.combat.ShipVariantAPI
import exoticatechnologies.util.ModuleSlotPathsUtils
import exoticatechnologies.util.tests.utils.FakeVariantTree
import org.junit.Assert
import org.junit.Test

/**
 * Unit tests for [ModuleSlotPathsUtils], run on fake variant trees ([FakeVariantTree]).
 *
 * The recurring fixture is a "train": a root with the SAME module variant id in five slots, the
 * shape of ED Shipyards' Helltrain / Newfoundland war freighters that issue #76 is about.
 */
class ModuleSlotPathsUtilsTests {

    private val segmentSlots = listOf("SEGMENT1", "SEGMENT2", "SEGMENT3", "SEGMENT4", "SEGMENT5")

    private fun train(): ShipVariantAPI {
        return FakeVariantTree.shipWithIdenticalModules("train_warfreighter", "train_segment_standard", segmentSlots)
    }

    /** A ship with two DIFFERENT modules, like tbj_overslaught's left / right. */
    private fun twoDistinctModules(): ShipVariantAPI {
        val modules = LinkedHashMap<String, ShipVariantAPI>()
        modules["LEFT"] = FakeVariantTree.variant("ship_left_Start")
        modules["RIGHT"] = FakeVariantTree.variant("ship_right_Start")
        return FakeVariantTree.variant("ship_Start", modules)
    }

    /** A root whose module in slot OUTER owns a module of its own in slot INNER. */
    private fun nested(): ShipVariantAPI {
        val innerModules = LinkedHashMap<String, ShipVariantAPI>()
        innerModules["INNER"] = FakeVariantTree.variant("nested_inner")
        val outerModules = LinkedHashMap<String, ShipVariantAPI>()
        outerModules["OUTER"] = FakeVariantTree.variant("nested_outer", innerModules)
        return FakeVariantTree.variant("nested_root", outerModules)
    }

    // ---- childSlotPath ----

    @Test
    fun testChildSlotPathOfRootIsTheSlotIdItself() {
        val slotId = "SEGMENT3"
        // Same instance, not just an equal String: the common case must not allocate.
        Assert.assertSame(slotId, ModuleSlotPathsUtils.childSlotPath(ModuleSlotPathsUtils.ROOT_SLOT_PATH, slotId))
    }

    @Test
    fun testChildSlotPathBelowAModuleJoinsTheSlotIds() {
        Assert.assertEquals("OUTER/INNER", ModuleSlotPathsUtils.childSlotPath("OUTER", "INNER"))
    }

    // ---- joinSlotPaths ----

    @Test
    fun testJoinSlotPathsHandlesEveryCombinationOfRootAndModule() {
        Assert.assertEquals("", ModuleSlotPathsUtils.joinSlotPaths(ModuleSlotPathsUtils.ROOT_SLOT_PATH, ModuleSlotPathsUtils.ROOT_SLOT_PATH))
        Assert.assertEquals("SEGMENT3", ModuleSlotPathsUtils.joinSlotPaths(ModuleSlotPathsUtils.ROOT_SLOT_PATH, "SEGMENT3"))
        Assert.assertEquals("SEGMENT3", ModuleSlotPathsUtils.joinSlotPaths("SEGMENT3", ModuleSlotPathsUtils.ROOT_SLOT_PATH))
        Assert.assertEquals("OUTER/INNER", ModuleSlotPathsUtils.joinSlotPaths("OUTER", "INNER"))
        Assert.assertEquals("A/B/C", ModuleSlotPathsUtils.joinSlotPaths("A", "B/C"))
    }

    // ---- forEachModuleVariantWithSlotPath ----

    @Test
    fun testWalkVisitsEveryModuleWithItsSlotPathButNotTheRoot() {
        val visited = mutableListOf<String>()
        ModuleSlotPathsUtils.forEachModuleVariantWithSlotPath(train(), ModuleSlotPathsUtils.ROOT_SLOT_PATH) { variant, slotPath ->
            visited.add(variant.hullVariantId + "@" + slotPath)
        }
        Assert.assertEquals(
            listOf(
                "train_segment_standard@SEGMENT1",
                "train_segment_standard@SEGMENT2",
                "train_segment_standard@SEGMENT3",
                "train_segment_standard@SEGMENT4",
                "train_segment_standard@SEGMENT5"
            ),
            visited
        )
    }

    @Test
    fun testWalkBuildsNestedSlotPathsAndHonoursTheStartingPath() {
        val root = nested()
        val fromRoot = mutableListOf<String>()
        ModuleSlotPathsUtils.forEachModuleVariantWithSlotPath(root, ModuleSlotPathsUtils.ROOT_SLOT_PATH) { _, slotPath ->
            fromRoot.add(slotPath)
        }
        Assert.assertEquals(listOf("OUTER", "OUTER/INNER"), fromRoot)

        // Starting at the OUTER module and telling the walk where that module sits yields the
        // very same path for the node below it.
        val outer = root.getModuleVariant("OUTER")
        val fromOuter = mutableListOf<String>()
        ModuleSlotPathsUtils.forEachModuleVariantWithSlotPath(outer, "OUTER") { _, slotPath -> fromOuter.add(slotPath) }
        Assert.assertEquals(listOf("OUTER/INNER"), fromOuter)
    }

    @Test
    fun testWalkOfAShipWithoutModulesVisitsNothing() {
        var visits = 0
        ModuleSlotPathsUtils.forEachModuleVariantWithSlotPath(FakeVariantTree.variant("single_ship"), ModuleSlotPathsUtils.ROOT_SLOT_PATH) { _, _ -> visits++ }
        Assert.assertEquals(0, visits)
    }

    // ---- slotPathOf ----

    @Test
    fun testSlotPathOfRootIsTheRootSlotPath() {
        val root = train()
        val path = ModuleSlotPathsUtils.slotPathOf(root, root)
        Assert.assertTrue(path.isPresent())
        Assert.assertEquals(ModuleSlotPathsUtils.ROOT_SLOT_PATH, path.get())
    }

    @Test
    fun testSlotPathOfTellsIdenticalModulesApart() {
        val root = train()
        for (slotId in segmentSlots) {
            val path = ModuleSlotPathsUtils.slotPathOf(root, root.getModuleVariant(slotId))
            Assert.assertTrue("module in $slotId must be found", path.isPresent())
            Assert.assertEquals(slotId, path.get())
        }
    }

    @Test
    fun testSlotPathOfIsEmptyForAnInstanceOutsideTheTree() {
        val root = train()
        // Same id as the five segments, but not one of them.
        val lookalike = FakeVariantTree.variant("train_segment_standard")
        Assert.assertFalse(ModuleSlotPathsUtils.slotPathOf(root, lookalike).isPresent())
    }

    @Test
    fun testSlotPathOfAnInstanceMountedInSeveralSlotsIsTheFirstOfThem() {
        // The game hands out ONE shared stock variant object for every slot that has no per-ship
        // override yet. Such slots are physically one object with one set of tags, so resolving
        // it to the first slot is as good as any - what matters is that it resolves the same way
        // every time.
        val shared = FakeVariantTree.variant("train_segment_standard")
        val modules = LinkedHashMap<String, ShipVariantAPI>()
        modules["SEGMENT1"] = shared
        modules["SEGMENT2"] = shared
        modules["SEGMENT3"] = shared
        val root = FakeVariantTree.variant("train_warfreighter", modules)

        Assert.assertEquals("SEGMENT1", ModuleSlotPathsUtils.slotPathOf(root, shared).get())
        Assert.assertEquals("SEGMENT1", ModuleSlotPathsUtils.slotPathOf(root, root.getModuleVariant("SEGMENT3")).get())
    }

    @Test
    fun testSlotPathOfFindsNestedModules() {
        val root = nested()
        val inner = root.getModuleVariant("OUTER").getModuleVariant("INNER")
        Assert.assertEquals("OUTER/INNER", ModuleSlotPathsUtils.slotPathOf(root, inner).get())
    }

    // ---- variantAtSlotPath ----

    @Test
    fun testVariantAtSlotPathReturnsTheModuleMountedThere() {
        val root = train()
        Assert.assertSame(root, ModuleSlotPathsUtils.variantAtSlotPath(root, ModuleSlotPathsUtils.ROOT_SLOT_PATH).get())
        for (slotId in segmentSlots) {
            Assert.assertSame(root.getModuleVariant(slotId), ModuleSlotPathsUtils.variantAtSlotPath(root, slotId).get())
        }
    }

    @Test
    fun testVariantAtSlotPathFollowsTheSameSlotInACopyOfTheShip() {
        val campaign = train()
        val refitCopy = FakeVariantTree.copyOf(campaign)
        val pathOfThird = ModuleSlotPathsUtils.slotPathOf(campaign, campaign.getModuleVariant("SEGMENT3")).get()

        val sameModuleInCopy = ModuleSlotPathsUtils.variantAtSlotPath(refitCopy, pathOfThird).get()

        Assert.assertSame(refitCopy.getModuleVariant("SEGMENT3"), sameModuleInCopy)
        Assert.assertNotSame(campaign.getModuleVariant("SEGMENT3"), sameModuleInCopy)
    }

    @Test
    fun testVariantAtSlotPathIsEmptyWhenTheTreeHasNoSuchSlot() {
        Assert.assertFalse(ModuleSlotPathsUtils.variantAtSlotPath(train(), "SEGMENT9").isPresent())
        Assert.assertFalse(ModuleSlotPathsUtils.variantAtSlotPath(train(), "SEGMENT1/INNER").isPresent())
        Assert.assertFalse(ModuleSlotPathsUtils.variantAtSlotPath(FakeVariantTree.variant("single_ship"), "SEGMENT1").isPresent())
    }

    @Test
    fun testVariantAtSlotPathFindsNestedModules() {
        val root = nested()
        val inner = root.getModuleVariant("OUTER").getModuleVariant("INNER")
        Assert.assertSame(inner, ModuleSlotPathsUtils.variantAtSlotPath(root, "OUTER/INNER").get())
    }

    @Test
    fun testVariantAtSlotPathResolvesASlotIdContainingTheSeparator() {
        // Holds as long as no module mounted on a module spells the same path; see the KDoc of
        // variantAtSlotPath for that assumption.
        val modules = LinkedHashMap<String, ShipVariantAPI>()
        modules["WS/001"] = FakeVariantTree.variant("odd_module")
        val root = FakeVariantTree.variant("odd_root", modules)

        val path = ModuleSlotPathsUtils.slotPathOf(root, root.getModuleVariant("WS/001")).get()

        Assert.assertSame(root.getModuleVariant("WS/001"), ModuleSlotPathsUtils.variantAtSlotPath(root, path).get())
    }

    // ---- relativeSlotPath ----

    @Test
    fun testRelativeSlotPathToTheShipRootIsThePathItself() {
        Assert.assertEquals("SEGMENT3", ModuleSlotPathsUtils.relativeSlotPath("SEGMENT3", ModuleSlotPathsUtils.ROOT_SLOT_PATH).get())
        Assert.assertEquals("", ModuleSlotPathsUtils.relativeSlotPath("", ModuleSlotPathsUtils.ROOT_SLOT_PATH).get())
    }

    @Test
    fun testRelativeSlotPathToItselfIsTheRootSlotPath() {
        Assert.assertEquals(ModuleSlotPathsUtils.ROOT_SLOT_PATH, ModuleSlotPathsUtils.relativeSlotPath("SEGMENT3", "SEGMENT3").get())
    }

    @Test
    fun testRelativeSlotPathBelowAnAncestorDropsTheAncestor() {
        Assert.assertEquals("INNER", ModuleSlotPathsUtils.relativeSlotPath("OUTER/INNER", "OUTER").get())
    }

    @Test
    fun testRelativeSlotPathIsEmptyOutsideTheAncestor() {
        // a sibling
        Assert.assertFalse(ModuleSlotPathsUtils.relativeSlotPath("SEGMENT2", "SEGMENT3").isPresent())
        // the ship root is not below a module
        Assert.assertFalse(ModuleSlotPathsUtils.relativeSlotPath("", "SEGMENT3").isPresent())
        // SEGMENT10 merely starts with the characters of SEGMENT1; it is not mounted below it
        Assert.assertFalse(ModuleSlotPathsUtils.relativeSlotPath("SEGMENT10", "SEGMENT1").isPresent())
    }

    // ---- slotPathOfOnlyVariantWithId ----

    @Test
    fun testOnlyVariantWithIdFindsAUniqueModuleAndTheRoot() {
        val root = twoDistinctModules()
        Assert.assertEquals("RIGHT", ModuleSlotPathsUtils.slotPathOfOnlyVariantWithId(root, "ship_right_Start").get())
        Assert.assertEquals(ModuleSlotPathsUtils.ROOT_SLOT_PATH, ModuleSlotPathsUtils.slotPathOfOnlyVariantWithId(root, "ship_Start").get())
    }

    @Test
    fun testOnlyVariantWithIdRefusesToPickAmongIdenticalModules() {
        // Five modules carry this id. Answering "SEGMENT1" here is the defect of issue #76.
        Assert.assertFalse(ModuleSlotPathsUtils.slotPathOfOnlyVariantWithId(train(), "train_segment_standard").isPresent())
    }

    @Test
    fun testOnlyVariantWithIdMatchesARenamedRefitCloneFuzzily() {
        // 'ship_left_0' is how a refit clone of 'ship_left_Start' is named.
        Assert.assertEquals("LEFT", ModuleSlotPathsUtils.slotPathOfOnlyVariantWithId(twoDistinctModules(), "ship_left_0").get())
    }

    @Test
    fun testOnlyVariantWithIdPrefersTheExactMatchOverFuzzySiblings() {
        val modules = LinkedHashMap<String, ShipVariantAPI>()
        modules["BAY1"] = FakeVariantTree.variant("remnant_hangar1_Standard")
        modules["BAY2"] = FakeVariantTree.variant("remnant_hangar1_Strike")
        val root = FakeVariantTree.variant("remnant_station", modules)

        // Both siblings share the prefix 'remnant_hangar1', but each id exists exactly once.
        Assert.assertEquals("BAY2", ModuleSlotPathsUtils.slotPathOfOnlyVariantWithId(root, "remnant_hangar1_Strike").get())
        Assert.assertEquals("BAY1", ModuleSlotPathsUtils.slotPathOfOnlyVariantWithId(root, "remnant_hangar1_Standard").get())
        // A clone id that fuzzy-matches both of them cannot be placed.
        Assert.assertFalse(ModuleSlotPathsUtils.slotPathOfOnlyVariantWithId(root, "remnant_hangar1_0").isPresent())
    }

    @Test
    fun testOnlyVariantWithIdIsEmptyWhenNothingMatches() {
        Assert.assertFalse(ModuleSlotPathsUtils.slotPathOfOnlyVariantWithId(train(), "some_other_ship").isPresent())
    }

    // ---- slotPathOfGraphRoot ----

    @Test
    fun testGraphRootInsideTheShipTreeIsPlacedByInstance() {
        val root = train()
        // Identical modules, yet each one is placed exactly, because it IS the node in the tree.
        Assert.assertEquals("SEGMENT4", ModuleSlotPathsUtils.slotPathOfGraphRoot(root, root.getModuleVariant("SEGMENT4")).get())
        Assert.assertEquals(ModuleSlotPathsUtils.ROOT_SLOT_PATH, ModuleSlotPathsUtils.slotPathOfGraphRoot(root, root).get())
    }

    @Test
    fun testGraphRootThatIsACopyOfTheWholeShipIsPlacedAtTheRoot() {
        val root = train()
        Assert.assertEquals(ModuleSlotPathsUtils.ROOT_SLOT_PATH, ModuleSlotPathsUtils.slotPathOfGraphRoot(root, FakeVariantTree.copyOf(root)).get())

        // Refit clones are renamed; the copy still owns the same slots.
        val renamedCopy = FakeVariantTree.copyOf(twoDistinctModules()) { it.substringBeforeLast("_") + "_0" }
        Assert.assertEquals(ModuleSlotPathsUtils.ROOT_SLOT_PATH, ModuleSlotPathsUtils.slotPathOfGraphRoot(twoDistinctModules(), renamedCopy).get())
    }

    @Test
    fun testGraphRootThatIsACloneOfAUniqueModuleIsPlacedInThatModulesSlot() {
        val root = twoDistinctModules()
        val cloneOfRight = FakeVariantTree.variant("ship_right_0")
        Assert.assertEquals("RIGHT", ModuleSlotPathsUtils.slotPathOfGraphRoot(root, cloneOfRight).get())
    }

    @Test
    fun testGraphRootThatIsACloneOfAnIdenticalModuleCannotBePlaced() {
        // A detached clone of "one of the five segments": nothing says which one. It must not be
        // treated as the same module as any of them, least of all the first.
        val cloneOfSomeSegment = FakeVariantTree.variant("train_segment_standard")
        Assert.assertFalse(ModuleSlotPathsUtils.slotPathOfGraphRoot(train(), cloneOfSomeSegment).isPresent())
    }

    @Test
    fun testGraphRootOfASingleModuleShipCloneIsPlacedAtTheRoot() {
        // No station modules at all: 'ship_0' is the refit clone of 'ship_Start'.
        val ship = FakeVariantTree.variant("ship_Start")
        Assert.assertEquals(ModuleSlotPathsUtils.ROOT_SLOT_PATH, ModuleSlotPathsUtils.slotPathOfGraphRoot(ship, FakeVariantTree.variant("ship_0")).get())
    }

    @Test
    fun testGraphRootOfAnUnrelatedShipCannotBePlaced() {
        Assert.assertFalse(ModuleSlotPathsUtils.slotPathOfGraphRoot(train(), FakeVariantTree.variant("some_other_ship")).isPresent())
    }
}
