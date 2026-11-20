package exoticatechnologies.modifications.tests

import com.fs.starfarer.api.combat.ShipVariantAPI
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.VariantTagProvider
import exoticatechnologies.util.tests.utils.FakeFleetMember
import exoticatechnologies.util.tests.utils.FakeGame
import exoticatechnologies.util.tests.utils.FakeVariantTree
import org.junit.Assert
import org.junit.Test

/**
 * Unit tests for the write-through of [VariantTagProvider] (issue #76, F2): a tag written on one
 * module must be mirrored onto THAT module in every other copy of the ship, and onto no other.
 *
 * Most of them drive the engine-free half of the write-through
 * ([VariantTagProvider.testsOnly_mirrorWrite]) with a set of object graphs given as arguments.
 * That seam is new, so these tests cannot be run against the code before the fix; the one test
 * that goes through [VariantTagProvider.set] can, and fails there.
 *
 * Where the graph sets come from:
 * - logged in the diagnostic run of issue #76 (root member acting, a module selected in the
 *   refit screen, so the display working tree is ONE module variant that is not an instance of
 *   the ship's own tree): the three "whole ship write" tests and the two "logged" tests;
 * - constructed, not seen in any log: everything else (a transient module member acting, the
 *   ship tab, suffix siblings, renamed clones, single-module ships). What the transient
 *   member's own variant is while a module tab is selected was not logged, so both
 *   possibilities are covered: it is the instance mounted in the ship's tree, or it is not.
 *
 * Naming used throughout: `campaign` is the fleet member's own variant tree, `displayShip` the
 * refit screen's working copy of the whole ship (ship tab), `displayModule` its working copy of
 * one module (module tab).
 */
class VariantTagProviderWriteThroughTests {

    private val provider = VariantTagProvider.getInstance()

    private val segmentSlots = listOf("SEGMENT1", "SEGMENT2", "SEGMENT3", "SEGMENT4", "SEGMENT5")
    private val segmentId = "train_segment_standard"

    /** A ship that mounts the same module variant in five slots, like the Helltrain. */
    private fun train(): ShipVariantAPI {
        return FakeVariantTree.shipWithIdenticalModules("train_warfreighter", segmentId, segmentSlots)
    }

    /** A ship with two different modules, like tbj_overslaught. */
    private fun twoDistinctModules(): ShipVariantAPI {
        val modules = LinkedHashMap<String, ShipVariantAPI>()
        modules["LEFT"] = FakeVariantTree.variant("ship_left_Start")
        modules["RIGHT"] = FakeVariantTree.variant("ship_right_Start")
        return FakeVariantTree.variant("ship_Start", modules)
    }

    private fun exoticaTag(payload: String): String {
        return EXOTICA_INDICATOR + payload
    }

    private fun exoticaTagOf(variant: ShipVariantAPI): String? {
        return variant.tags.firstOrNull { it.startsWith(EXOTICA_INDICATOR) }
    }

    /** Slot ids of the modules of [root] that carry an Exotica tag, in slot order. */
    private fun taggedSlots(root: ShipVariantAPI): List<String> {
        return root.stationModules.keys.filter { exoticaTagOf(root.getModuleVariant(it)) != null }
    }

    // ---- through the provider's public entry point ----

    @Test
    fun testSetOnOneIdenticalModuleTagsThatModuleOnly() {
        // The whole path a real install takes below the UI: VariantTagProvider.set() for a module
        // of the member's own tree, outside the refit screen. On the code before the fix this
        // test fails: SEGMENT1, the first module carrying the id, is tagged as well.
        FakeGame.install(false)
        try {
            val campaign = train()
            val member = FakeFleetMember.member("fake-member-1", "ISS Unit Test", campaign)
            val mods = ShipModifications()
            mods.bandwidth = 42f

            provider.set(member, campaign.getModuleVariant("SEGMENT3"), mods)

            Assert.assertEquals(listOf("SEGMENT3"), taggedSlots(campaign))
            Assert.assertNull(exoticaTagOf(campaign))
        } finally {
            FakeGame.uninstall()
        }
    }

    // ---- no refit screen ----

    @Test
    fun testWriteToOneIdenticalModuleDoesNotReachTheFirstOne() {
        val campaign = train()
        val third = campaign.getModuleVariant("SEGMENT3")
        third.addTag(exoticaTag("third"))

        provider.testsOnly_mirrorWrite(campaign, campaign, campaign, null, null, listOf(campaign), third, exoticaTag("third"))

        // Before the fix the first module carrying the id (SEGMENT1) was written as well.
        Assert.assertEquals(listOf("SEGMENT3"), taggedSlots(campaign))
        Assert.assertNull(exoticaTagOf(campaign))
    }

    // ---- refit screen, ship tab: the display tree is a copy of the whole ship ----

    @Test
    fun testWriteToACampaignModuleReachesTheSameSlotOfTheDisplayShip() {
        val campaign = train()
        val displayShip = FakeVariantTree.copyOf(campaign)
        val fourth = campaign.getModuleVariant("SEGMENT4")

        provider.testsOnly_mirrorWrite(campaign, campaign, displayShip, displayShip, campaign, listOf(campaign, displayShip), fourth, exoticaTag("fourth"))

        Assert.assertEquals(listOf("SEGMENT4"), taggedSlots(campaign))
        Assert.assertEquals(listOf("SEGMENT4"), taggedSlots(displayShip))
        Assert.assertEquals(exoticaTag("fourth"), exoticaTagOf(displayShip.getModuleVariant("SEGMENT4")))
    }

    @Test
    fun testWriteToADisplayModuleReachesTheSameSlotOfTheCampaignTree() {
        val campaign = train()
        val displayShip = FakeVariantTree.copyOf(campaign)
        val displayFifth = displayShip.getModuleVariant("SEGMENT5")

        provider.testsOnly_mirrorWrite(campaign, campaign, displayShip, displayShip, campaign, listOf(campaign, displayShip), displayFifth, exoticaTag("fifth"))

        Assert.assertEquals(listOf("SEGMENT5"), taggedSlots(campaign))
        Assert.assertEquals(listOf("SEGMENT5"), taggedSlots(displayShip))
    }

    @Test
    fun testWriteToTheShipRootReachesTheDisplayRootAndNoModule() {
        val campaign = train()
        // Refit clones are renamed in their last '_suffix'; the root is still the same ship.
        val displayShip = FakeVariantTree.copyOf(campaign) { if (it == "train_warfreighter") "train_0" else it }

        provider.testsOnly_mirrorWrite(campaign, campaign, displayShip, displayShip, campaign, listOf(campaign, displayShip), campaign, exoticaTag("root"))

        Assert.assertEquals(exoticaTag("root"), exoticaTagOf(campaign))
        Assert.assertEquals(exoticaTag("root"), exoticaTagOf(displayShip))
        Assert.assertEquals(emptyList<String>(), taggedSlots(campaign))
        Assert.assertEquals(emptyList<String>(), taggedSlots(displayShip))
    }

    // ---- refit screen, module tab: the display tree is ONE detached module variant ----

    @Test
    fun testWholeShipWriteToAnotherIdenticalModuleLeavesTheSelectedModuleAlone() {
        // SEGMENT2 is selected. A whole-ship exotic writes every campaign module in turn through
        // the ROOT member; this is the write for SEGMENT5.
        val campaign = train()
        val selected = campaign.getModuleVariant("SEGMENT2")
        val displayModule = FakeVariantTree.variant(segmentId)
        displayModule.addTag(exoticaTag("selected module's own data"))
        val fifth = campaign.getModuleVariant("SEGMENT5")

        provider.testsOnly_mirrorWrite(campaign, campaign, campaign, displayModule, selected, listOf(campaign, displayModule), fifth, exoticaTag("fifth"))

        // Before the fix the display module was overwritten by EVERY identical sibling's write,
        // because the lookup returned a graph's root whenever its id matched.
        Assert.assertEquals(exoticaTag("selected module's own data"), exoticaTagOf(displayModule))
        Assert.assertEquals(listOf("SEGMENT5"), taggedSlots(campaign))
    }

    @Test
    fun testWholeShipWriteToTheSelectedModuleReachesItsDisplayCopy() {
        val campaign = train()
        val selected = campaign.getModuleVariant("SEGMENT2")
        val displayModule = FakeVariantTree.variant(segmentId)

        provider.testsOnly_mirrorWrite(campaign, campaign, campaign, displayModule, selected, listOf(campaign, displayModule), selected, exoticaTag("second"))

        Assert.assertEquals(exoticaTag("second"), exoticaTagOf(displayModule))
        Assert.assertEquals(listOf("SEGMENT2"), taggedSlots(campaign))
    }

    @Test
    fun testWriteToTheDisplayModuleReachesItsOwnSlotAndNotTheFirstModule() {
        // The player installs on the selected module (SEGMENT2): the write targets the display
        // copy, made for the transient module member whose own variant is the campaign node.
        val campaign = train()
        val selected = campaign.getModuleVariant("SEGMENT2")
        val displayModule = FakeVariantTree.variant(segmentId)

        provider.testsOnly_mirrorWrite(campaign, selected, displayModule, displayModule, selected, listOf(selected, campaign, displayModule), displayModule, exoticaTag("second"))

        // Before the fix this write landed on SEGMENT1, the first module carrying the id.
        Assert.assertEquals(listOf("SEGMENT2"), taggedSlots(campaign))
        Assert.assertEquals(exoticaTag("second"), exoticaTagOf(displayModule))
    }

    @Test
    fun testWriteToADisplayModuleThatCannotBePlacedTouchesNoCampaignModule() {
        // Same as above, but the transient member's variant is not an instance of the campaign
        // tree either. Nothing says which of the five segments is being edited, so none of them
        // may be written; the working copy and its original still agree with each other.
        val campaign = train()
        val detachedMemberVariant = FakeVariantTree.variant(segmentId)
        val displayModule = FakeVariantTree.variant(segmentId)

        provider.testsOnly_mirrorWrite(
            campaign, detachedMemberVariant, displayModule, displayModule, detachedMemberVariant,
            listOf(detachedMemberVariant, campaign, displayModule), displayModule, exoticaTag("somewhere")
        )

        Assert.assertEquals(emptyList<String>(), taggedSlots(campaign))
        Assert.assertEquals(exoticaTag("somewhere"), exoticaTagOf(displayModule))
        Assert.assertEquals(exoticaTag("somewhere"), exoticaTagOf(detachedMemberVariant))
    }

    @Test
    fun testWholeShipWriteLeavesADisplayModuleThatCannotBePlacedAlone() {
        val campaign = train()
        val detachedMemberVariant = FakeVariantTree.variant(segmentId)
        val displayModule = FakeVariantTree.variant(segmentId)
        displayModule.addTag(exoticaTag("selected module's own data"))

        for (slotId in segmentSlots) {
            provider.testsOnly_mirrorWrite(
                campaign, campaign, campaign, displayModule, detachedMemberVariant,
                listOf(campaign, displayModule), campaign.getModuleVariant(slotId), exoticaTag(slotId)
            )
        }

        Assert.assertEquals(exoticaTag("selected module's own data"), exoticaTagOf(displayModule))
        for (slotId in segmentSlots) {
            Assert.assertEquals(exoticaTag(slotId), exoticaTagOf(campaign.getModuleVariant(slotId)))
        }
    }

    @Test
    fun testLoggedWholeShipWriteToTheDisplayModuleReachesTheSelectedSlotWhenItsOriginalIsMounted() {
        // The sixth module write of the logged AlphaSubcore install: the whole-ship flow, acting
        // through the ROOT member, writes the display module itself. Its original is the module
        // mounted in SEGMENT2, so that is where the write is mirrored.
        val campaign = train()
        val selected = campaign.getModuleVariant("SEGMENT2")
        val displayModule = FakeVariantTree.variant(segmentId)

        provider.testsOnly_mirrorWrite(campaign, campaign, campaign, displayModule, selected, listOf(campaign, displayModule), displayModule, exoticaTag("second"))

        // On the code before the fix this write landed on the first module carrying the id.
        Assert.assertEquals(listOf("SEGMENT2"), taggedSlots(campaign))
        Assert.assertEquals(exoticaTag("second"), exoticaTagOf(displayModule))
    }

    @Test
    fun testLoggedWholeShipWriteToTheDisplayModuleTouchesNoCampaignModuleWhenItCannotBePlaced() {
        // The same write when the display module's original is not an instance of the ship's
        // tree either. Nothing says which of the five segments is selected, so none is written.
        // The data is not lost: it stays on the display module, which the refit screen saves
        // into its slot.
        val campaign = train()
        val detachedOriginal = FakeVariantTree.variant(segmentId)
        val displayModule = FakeVariantTree.variant(segmentId)

        provider.testsOnly_mirrorWrite(campaign, campaign, campaign, displayModule, detachedOriginal, listOf(campaign, displayModule), displayModule, exoticaTag("somewhere"))

        Assert.assertEquals(emptyList<String>(), taggedSlots(campaign))
        Assert.assertEquals(exoticaTag("somewhere"), exoticaTagOf(displayModule))
        Assert.assertNull(exoticaTagOf(detachedOriginal))
    }

    // ---- ships without identical modules keep working as before ----

    @Test
    fun testRenamedDisplayModuleOfAUniqueModuleStillReachesItsCampaignNode() {
        // tbj_overslaught-like: LEFT is selected and its refit clone is renamed 'ship_left_0'.
        // The id is unique on the ship, so it is placed by its (fuzzy) id as it always was.
        val campaign = twoDistinctModules()
        val detachedMemberVariant = FakeVariantTree.variant("ship_left_Start")
        val displayModule = FakeVariantTree.variant("ship_left_0")

        provider.testsOnly_mirrorWrite(
            campaign, detachedMemberVariant, displayModule, displayModule, detachedMemberVariant,
            listOf(detachedMemberVariant, campaign, displayModule), displayModule, exoticaTag("left")
        )

        Assert.assertEquals(listOf("LEFT"), taggedSlots(campaign))
        Assert.assertEquals(exoticaTag("left"), exoticaTagOf(detachedMemberVariant))
    }

    @Test
    fun testWriteToOneSuffixSiblingLeavesTheSelectedSiblingAlone() {
        // Two different modules whose ids differ only in the last '_suffix'. BAY2 is selected;
        // the write is for BAY1. A fuzzy id match must not make them one module.
        val modules = LinkedHashMap<String, ShipVariantAPI>()
        modules["BAY1"] = FakeVariantTree.variant("remnant_hangar1_Standard")
        modules["BAY2"] = FakeVariantTree.variant("remnant_hangar1_Strike")
        val campaign = FakeVariantTree.variant("remnant_station", modules)
        val displayModule = FakeVariantTree.variant("remnant_hangar1_Strike")
        val bay1 = campaign.getModuleVariant("BAY1")

        provider.testsOnly_mirrorWrite(
            campaign, campaign, campaign, displayModule, campaign.getModuleVariant("BAY2"),
            listOf(campaign, displayModule), bay1, exoticaTag("bay1")
        )

        Assert.assertNull(exoticaTagOf(displayModule))
        Assert.assertEquals(listOf("BAY1"), taggedSlots(campaign))
    }

    @Test
    fun testSingleModuleShipWriteToTheRefitCloneReachesTheMemberVariant() {
        // The case the old root-first rule existed for: 'ship_0' is the refit clone of the
        // member's 'ship_Start'. Skipping member.variant would hide the write from later reads.
        val memberVariant = FakeVariantTree.variant("ship_Start")
        val refitClone = FakeVariantTree.variant("ship_0")

        provider.testsOnly_mirrorWrite(memberVariant, memberVariant, refitClone, refitClone, memberVariant, listOf(memberVariant, refitClone), refitClone, exoticaTag("single"))

        Assert.assertEquals(exoticaTag("single"), exoticaTagOf(memberVariant))
        Assert.assertEquals(exoticaTag("single"), exoticaTagOf(refitClone))
    }

    @Test
    fun testSingleModuleShipWriteToACloneOutsideEveryGraphReachesTheMemberVariant() {
        val memberVariant = FakeVariantTree.variant("ship_Start")
        val someClone = FakeVariantTree.variant("ship_Hull")

        provider.testsOnly_mirrorWrite(memberVariant, memberVariant, memberVariant, null, null, listOf(memberVariant), someClone, exoticaTag("single"))

        Assert.assertEquals(exoticaTag("single"), exoticaTagOf(memberVariant))
    }

    // ---- strip, replace, and what must not be written ----

    @Test
    fun testStripRemovesTheTagFromTheSameSlotOnly() {
        val campaign = train()
        val displayShip = FakeVariantTree.copyOf(campaign)
        for (slotId in segmentSlots) {
            campaign.getModuleVariant(slotId).addTag(exoticaTag(slotId))
            displayShip.getModuleVariant(slotId).addTag(exoticaTag(slotId))
        }
        val third = campaign.getModuleVariant("SEGMENT3")

        provider.testsOnly_mirrorWrite(campaign, campaign, displayShip, displayShip, campaign, listOf(campaign, displayShip), third, null)

        val others = listOf("SEGMENT1", "SEGMENT2", "SEGMENT4", "SEGMENT5")
        Assert.assertEquals(others, taggedSlots(campaign))
        Assert.assertEquals(others, taggedSlots(displayShip))
    }

    @Test
    fun testMirroredWriteReplacesTheOldTagInsteadOfAddingASecondOne() {
        val campaign = train()
        val displayShip = FakeVariantTree.copyOf(campaign)
        val displayThird = displayShip.getModuleVariant("SEGMENT3")
        displayThird.addTag(exoticaTag("old"))
        displayThird.addTag("some_unrelated_tag")

        provider.testsOnly_mirrorWrite(campaign, campaign, displayShip, displayShip, campaign, listOf(campaign, displayShip), campaign.getModuleVariant("SEGMENT3"), exoticaTag("new"))

        Assert.assertEquals(listOf(exoticaTag("new")), displayThird.tags.filter { it.startsWith(EXOTICA_INDICATOR) })
        Assert.assertTrue(displayThird.tags.contains("some_unrelated_tag"))
    }

    @Test
    fun testNodeInTheRightSlotButOfAnotherVariantIsNotWritten() {
        // Same slots, but SEGMENT3 of the other graph holds a different variant altogether: the
        // two graphs are not copies of each other, so slot and id together do not match.
        val campaign = train()
        val otherModules = LinkedHashMap<String, ShipVariantAPI>()
        for (slotId in segmentSlots) {
            otherModules[slotId] = FakeVariantTree.variant(if (slotId == "SEGMENT3") "some_other_module" else segmentId)
        }
        val otherGraph = FakeVariantTree.variant("train_warfreighter", otherModules)

        provider.testsOnly_mirrorWrite(campaign, campaign, otherGraph, otherGraph, campaign, listOf(campaign, otherGraph), campaign.getModuleVariant("SEGMENT3"), exoticaTag("third"))

        Assert.assertEquals(listOf("SEGMENT3"), taggedSlots(campaign))
        Assert.assertEquals(emptyList<String>(), taggedSlots(otherGraph))
    }

    companion object {
        /** The prefix of the variant tag Exotica stores its data in (VariantTagProvider). */
        private const val EXOTICA_INDICATOR = "\$\$EXOTICA\$\$"
    }
}
