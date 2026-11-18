package exoticatechnologies.modifications.tests

import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.VariantTagProvider
import exoticatechnologies.util.tests.utils.FakeFleetMember
import exoticatechnologies.util.tests.utils.FakeGame
import exoticatechnologies.util.tests.utils.FakeVariantTree
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the read cache of [VariantTagProvider] (issue #76, F4): a module must be served
 * its OWN data, also when other modules of the ship carry the same hullVariantId or one that
 * differs only in its last `_suffix`.
 *
 * Every test uses a provider of its own, so no cached state is shared between tests. Modules are
 * told apart by the bandwidth stored in their tag.
 */
class VariantTagProviderCacheTests {

    private val segmentSlots = listOf("SEGMENT1", "SEGMENT2", "SEGMENT3", "SEGMENT4", "SEGMENT5")
    private val segmentId = "train_segment_standard"

    private lateinit var provider: VariantTagProvider

    @Before
    fun setup() {
        // "Not on the refit screen": only then is the cache filled from tag reads.
        FakeGame.install(false)
        provider = VariantTagProvider()
    }

    @After
    fun tearDown() {
        FakeGame.uninstall()
    }

    /** A ship that mounts the same module variant in five slots, like the Helltrain. */
    private fun train(): ShipVariantAPI {
        return FakeVariantTree.shipWithIdenticalModules("train_warfreighter", segmentId, segmentSlots)
    }

    private fun shipMember(variant: ShipVariantAPI): FleetMemberAPI {
        return FakeFleetMember.member("fake-member-1", "ISS Unit Test", variant)
    }

    /** Tags [variant] the way the provider does, with modifications of the given bandwidth. */
    private fun tagWithBandwidth(variant: ShipVariantAPI, bandwidth: Float) {
        variant.addTag(EXOTICA_INDICATOR + "{\"baseBandwidth\":" + bandwidth + "}")
    }

    private fun modsWithBandwidth(bandwidth: Float): ShipModifications {
        val mods = ShipModifications()
        mods.bandwidth = bandwidth
        return mods
    }

    private fun bandwidthOf(mods: ShipModifications?): Float? {
        return if (mods != null) {
            mods.bandwidth
        } else {
            null
        }
    }

    // ---- identical modules ----

    @Test
    fun testIdenticalModulesAreEachServedTheirOwnData() {
        val campaign = train()
        val member = shipMember(campaign)
        tagWithBandwidth(campaign.getModuleVariant("SEGMENT1"), 11f)
        tagWithBandwidth(campaign.getModuleVariant("SEGMENT3"), 33f)

        // SEGMENT1 is read first, which caches its data under the hullVariantId all five share.
        Assert.assertEquals(11f, bandwidthOf(provider.get(member, campaign.getModuleVariant("SEGMENT1"))))
        // Before the fix both of these returned SEGMENT1's data.
        Assert.assertEquals(33f, bandwidthOf(provider.get(member, campaign.getModuleVariant("SEGMENT3"))))
        Assert.assertNull(provider.get(member, campaign.getModuleVariant("SEGMENT2")))
    }

    @Test
    fun testRepeatedReadsOfOneModuleAreServedFromTheCache() {
        val campaign = train()
        val member = shipMember(campaign)
        tagWithBandwidth(campaign.getModuleVariant("SEGMENT3"), 33f)

        val first = provider.get(member, campaign.getModuleVariant("SEGMENT3"))
        val second = provider.get(member, campaign.getModuleVariant("SEGMENT3"))

        Assert.assertNotNull(first)
        // The same object, not a second decode of the tag: identical modules are still cached.
        Assert.assertSame(first, second)
    }

    @Test
    fun testSetOnOneIdenticalModuleDoesNotChangeWhatAnotherOneReturns() {
        val campaign = train()
        val member = shipMember(campaign)
        val second = campaign.getModuleVariant("SEGMENT2")
        val fourth = campaign.getModuleVariant("SEGMENT4")
        val modsOfFourth = modsWithBandwidth(44f)

        provider.set(member, second, modsWithBandwidth(22f))
        provider.set(member, fourth, modsOfFourth)

        // Before the fix SEGMENT2 was served SEGMENT4's object: the later write replaced the one
        // cache entry the two shared.
        Assert.assertEquals(22f, bandwidthOf(provider.get(member, second)))
        Assert.assertSame(modsOfFourth, provider.get(member, fourth))
    }

    @Test
    fun testDetachedCloneOfAnIdenticalModuleIsReadFromItsOwnTag() {
        val campaign = train()
        val member = shipMember(campaign)
        tagWithBandwidth(campaign.getModuleVariant("SEGMENT1"), 11f)
        provider.get(member, campaign.getModuleVariant("SEGMENT1"))

        // A clone of "one of the five segments" that is not an instance of the member's tree.
        // Nothing says which segment it is, so the cache must not answer for it.
        val taggedClone = FakeVariantTree.variant(segmentId)
        tagWithBandwidth(taggedClone, 77f)
        val untaggedClone = FakeVariantTree.variant(segmentId)

        Assert.assertEquals(77f, bandwidthOf(provider.get(member, taggedClone)))
        Assert.assertNull(provider.get(member, untaggedClone))
    }

    // ---- siblings that differ only in the last '_suffix' ----

    @Test
    fun testSuffixSiblingIsNotServedItsSiblingsData() {
        val modules = LinkedHashMap<String, ShipVariantAPI>()
        modules["BAY1"] = FakeVariantTree.variant("remnant_hangar1_Standard")
        modules["BAY2"] = FakeVariantTree.variant("remnant_hangar1_Strike")
        val campaign = FakeVariantTree.variant("remnant_station", modules)
        val member = shipMember(campaign)
        tagWithBandwidth(campaign.getModuleVariant("BAY1"), 11f)

        Assert.assertEquals(11f, bandwidthOf(provider.get(member, campaign.getModuleVariant("BAY1"))))
        // Before the fix the fuzzy key ('remnant_hangar1') served BAY1's data to BAY2.
        Assert.assertNull(provider.get(member, campaign.getModuleVariant("BAY2")))
    }

    // ---- what the fuzzy match exists for keeps working ----

    @Test
    fun testRefitCloneOfASingleModuleShipIsServedFromTheCache() {
        val memberVariant = FakeVariantTree.variant("ship_Start")
        val member = shipMember(memberVariant)
        val mods = modsWithBandwidth(50f)
        provider.set(member, memberVariant, mods)

        // 'ship_0' is the refit clone of 'ship_Start'; a clone can also keep the id.
        Assert.assertSame(mods, provider.get(member, FakeVariantTree.variant("ship_0")))
        Assert.assertSame(mods, provider.get(member, FakeVariantTree.variant("ship_Start")))
    }

    @Test
    fun testRenamedCloneOfAUniqueModuleIsServedThatModulesEntry() {
        val modules = LinkedHashMap<String, ShipVariantAPI>()
        modules["LEFT"] = FakeVariantTree.variant("ship_left_Start")
        modules["RIGHT"] = FakeVariantTree.variant("ship_right_Start")
        val campaign = FakeVariantTree.variant("ship_Start", modules)
        val member = shipMember(campaign)
        val modsOfLeft = modsWithBandwidth(50f)
        provider.set(member, campaign.getModuleVariant("LEFT"), modsOfLeft)

        // The id is unique on the ship, so the clone is placed in LEFT and shares its entry.
        Assert.assertSame(modsOfLeft, provider.get(member, FakeVariantTree.variant("ship_left_0")))
        Assert.assertNull(provider.get(member, FakeVariantTree.variant("ship_right_0")))
    }

    @Test
    fun testModuleLevelMemberAndTheWorkingCopyOfItsVariantShareOneEntry() {
        // The transient member the refit screen creates for a selected module: unnamed, and its
        // variant is the module alone.
        val memberVariant = FakeVariantTree.variant(segmentId)
        val moduleMember = FakeFleetMember.member("fake-module-member", null, memberVariant)
        val mods = modsWithBandwidth(50f)
        provider.set(moduleMember, memberVariant, mods)

        Assert.assertSame(mods, provider.get(moduleMember, memberVariant))
        Assert.assertSame(mods, provider.get(moduleMember, FakeVariantTree.variant(segmentId)))
    }

    // ---- reads that come with a slot path ----

    @Test
    fun testSlotPathReadsThroughAModuleLevelMemberTellIdenticalModulesApart() {
        // A module-level member has no tree of its own in which the five segments could be
        // told apart. A caller that walks the ship passes each node's slot path instead.
        val campaign = train()
        val moduleMember = FakeFleetMember.member("fake-module-member", null, campaign.getModuleVariant("SEGMENT2"))
        tagWithBandwidth(campaign.getModuleVariant("SEGMENT1"), 11f)
        tagWithBandwidth(campaign.getModuleVariant("SEGMENT3"), 33f)

        Assert.assertEquals(11f, bandwidthOf(provider.get(moduleMember, campaign.getModuleVariant("SEGMENT1"), "SEGMENT1")))
        Assert.assertEquals(33f, bandwidthOf(provider.get(moduleMember, campaign.getModuleVariant("SEGMENT3"), "SEGMENT3")))
        Assert.assertNull(provider.get(moduleMember, campaign.getModuleVariant("SEGMENT4"), "SEGMENT4"))
    }

    @Test
    fun testSlotPathReadAndPlainReadOfARootMemberShareOneEntry() {
        val campaign = train()
        val member = shipMember(campaign)
        val third = campaign.getModuleVariant("SEGMENT3")
        tagWithBandwidth(third, 33f)

        val throughTheWalk = provider.get(member, third, "SEGMENT3")
        val plain = provider.get(member, third)

        Assert.assertNotNull(throughTheWalk)
        // The slot path a walk passes and the one worked out from the instance are the same
        // String, so both reads find the same entry.
        Assert.assertSame(throughTheWalk, plain)
    }

    @Test
    fun testNothingIsCachedForAReadOnTheRefitScreen() {
        FakeGame.uninstall()
        FakeGame.install(true)
        val campaign = train()
        val member = shipMember(campaign)
        tagWithBandwidth(campaign.getModuleVariant("SEGMENT3"), 33f)

        val first = provider.get(member, campaign.getModuleVariant("SEGMENT3"))
        val second = provider.get(member, campaign.getModuleVariant("SEGMENT3"))

        // Existing behaviour, unchanged: on the refit and fleet screens every read decodes the
        // tag. Each module still gets its own data.
        Assert.assertNotSame(first, second)
        Assert.assertEquals(33f, bandwidthOf(first))
        Assert.assertEquals(33f, bandwidthOf(second))
    }

    // ---- writes and cache lifetime ----

    @Test
    fun testClearCacheMakesTheNextReadDecodeTheTagAgain() {
        val campaign = train()
        val member = shipMember(campaign)
        tagWithBandwidth(campaign.getModuleVariant("SEGMENT3"), 33f)
        val beforeClear = provider.get(member, campaign.getModuleVariant("SEGMENT3"))

        provider.clearCache()
        val afterClear = provider.get(member, campaign.getModuleVariant("SEGMENT3"))

        Assert.assertNotSame(beforeClear, afterClear)
        Assert.assertEquals(33f, bandwidthOf(afterClear))
    }

    @Test
    fun testRemoveEmptiesThatModuleAndLeavesTheDataOfAnotherIdenticalModule() {
        val campaign = train()
        val member = shipMember(campaign)
        val second = campaign.getModuleVariant("SEGMENT2")
        val fourth = campaign.getModuleVariant("SEGMENT4")
        provider.set(member, second, modsWithBandwidth(22f))
        provider.set(member, fourth, modsWithBandwidth(44f))

        provider.remove(member, second)

        Assert.assertNull(provider.get(member, second))
        Assert.assertEquals(44f, bandwidthOf(provider.get(member, fourth)))
    }

    companion object {
        /** The prefix of the variant tag Exotica stores its data in (VariantTagProvider). */
        private const val EXOTICA_INDICATOR = "\$\$EXOTICA\$\$"
    }
}
