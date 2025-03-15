package exoticatechnologies.util.tests

import com.fs.starfarer.api.campaign.BuffManagerAPI
import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.fleet.*
import com.fs.starfarer.api.loading.*
import exoticatechnologies.hullmods.exotics.ExoticHullmod
import exoticatechnologies.hullmods.exotics.ExoticHullmodLookup
import exoticatechnologies.hullmods.exotics.HullmodExoticHandler
import exoticatechnologies.hullmods.exotics.HullmodExoticKey
import exoticatechnologies.modifications.exotics.impl.HullmodExotic
import exoticatechnologies.util.datastructures.Optional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.*

class HullmodExoticHandlerTests {

    @Before
    fun setup() {
        // Just clear the lookup map for each test
        HullmodExoticHandler.testsOnly_clearLookupMap()
    }

    @Test
    fun testConcurrentKeyInsertions() = runBlocking {
        val handler = HullmodExoticHandler
        val exotic = createAnnonymousHullmodExotic("test", EXOTIC_JSON, TEST_HULLMOD_ID)
        val fleetMember = createAnnonymousFleetMemberAPI(TEST_FLEETMEMBER_ID, TEST_FLEETMEMBER_SHIPNAME)
//        val variant = mockk<ShipVariantAPI>(relaxed = true)
        val variant = createAnnonymousShipVariantAPI(TEST_HULL_SPEC_HULL_ID)

        // Simulate 100 concurrent installations
        val jobs = (1..100).map {
            async(Dispatchers.Default) {
                handler.shouldInstallHullmodExoticToVariant(
                        exotic,
                        fleetMember,
                        variant,
                        Optional.of(listOf(variant))
                )
            }
        }
        jobs.awaitAll()

        // Verify only 1 entry exists
        Assert.assertEquals(1, handler.testsOnly_grabAllKeysForParticularFleetMember(fleetMember).size)
        Assert.assertTrue(handler.doesEntryExist(exotic, fleetMember))
    }

    @Test
    fun testKeyRemoval() {
        val handler = HullmodExoticHandler
        val exotic = createAnnonymousHullmodExotic("test", EXOTIC_JSON, TEST_HULLMOD_ID)
        val fleetMember = createAnnonymousFleetMemberAPI(TEST_FLEETMEMBER_ID, TEST_FLEETMEMBER_SHIPNAME)
//        val variant = mockk<ShipVariantAPI>(relaxed = true)
        val variant = createAnnonymousShipVariantAPI(TEST_HULL_SPEC_HULL_ID)
        // Prepare and put the hullmod in the map
        val exoticHullmod = object : ExoticHullmod() {
            override val hullModId: String
                get() = TEST_HULLMOD_ID

            override fun removeEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize, stats: MutableShipStatsAPI, id: String) {
                TODO("Not yet implemented")
            }
        }
        ExoticHullmodLookup.addToLookupMap(exoticHullmod)


        // Install and verify
        handler.shouldInstallHullmodExoticToVariant(
                exotic,
                fleetMember,
                variant,
                Optional.of(listOf(variant))
        )
        Assert.assertEquals(1, handler.testsOnly_grabAllKeysForParticularFleetMember(fleetMember).size)

        // Remove and verify
        handler.removeHullmodExoticFromFleetMember(
                TEST_HULLMOD_ID,
                fleetMember
        )
        Assert.assertEquals(0, handler.testsOnly_grabAllKeysForParticularFleetMember(fleetMember).size)
    }

    @Test
    fun testDifferentFleetMemberInstances_SameID() {
        val TEST_HULLMOD_ID = TEST_HULLMOD_ID

        val handler = HullmodExoticHandler
        val exotic = createAnnonymousHullmodExotic("test", EXOTIC_JSON, TEST_HULLMOD_ID)

        // Two different instances with same ID
        val member1 = createAnnonymousFleetMemberAPI(TEST_FLEETMEMBER_ID, "shipname1")
        val member2 = createAnnonymousFleetMemberAPI(TEST_FLEETMEMBER_ID, "shipname2")

        handler.shouldInstallHullmodExoticToVariant(
                exotic,
                member1,
                createAnnonymousShipVariantAPI(TEST_HULL_SPEC_HULL_ID),
                Optional.of(emptyList())
        )
        handler.shouldInstallHullmodExoticToVariant(
                exotic,
                member2,
                createAnnonymousShipVariantAPI(TEST_HULL_SPEC_HULL_ID),
                Optional.of(emptyList())
        )

        // Should treat as same key
        Assert.assertEquals(1, handler.testsOnly_grabAllKeysForParticularFleetMember(member1).size)
    }

    @Test
    fun testMultipleVariantInstallations() {
        val handler = HullmodExoticHandler
        val exotic = createAnnonymousHullmodExotic("test", EXOTIC_JSON, TEST_HULLMOD_ID)
        val fleetMember = createAnnonymousFleetMemberAPI(TEST_FLEETMEMBER_ID, TEST_FLEETMEMBER_SHIPNAME)
//        val variant1 = mockk<ShipVariantAPI>(relaxed = true)
//        val variant2 = mockk<ShipVariantAPI>(relaxed = true)
        val variant1 = createAnnonymousShipVariantAPI("hull1")
        val variant2 = createAnnonymousShipVariantAPI("hull2")

        // First installation
        val shouldInstall1 = handler.shouldInstallHullmodExoticToVariant(
                exotic,
                fleetMember,
                variant1,
                Optional.of(listOf(variant1, variant2))
        )
        val install1success = handler.installHullmodExoticToVariant(
                exotic,
                fleetMember,
                variant1
        )

        // Second installation on different variant
        val shouldInstall2 = handler.shouldInstallHullmodExoticToVariant(
                exotic,
                fleetMember,
                variant2,
                Optional.of(listOf(variant1, variant2))
        )
        val install2success = handler.installHullmodExoticToVariant(
                exotic,
                fleetMember,
                variant2
        )

        // Verify installed variants
        val data = handler.testsOnly_getDataForKey(
                HullmodExoticKey(exotic, fleetMember.id)
        ).get()



        Assert.assertTrue(shouldInstall1)
        Assert.assertTrue(install1success)
        Assert.assertTrue(shouldInstall2)
        Assert.assertTrue(install2success)
        Assert.assertEquals(2, data.listOfVariantsWeInstalledOn.size)
    }

    @Test
    fun testConcurrentRemovals() = runBlocking {
        val TEST_HULLMOD_ID = TEST_HULLMOD_ID

        val handler = HullmodExoticHandler
        val exotic = createAnnonymousHullmodExotic("test", EXOTIC_JSON, TEST_HULLMOD_ID)
        val fleetMember = createAnnonymousFleetMemberAPI(TEST_FLEETMEMBER_ID, TEST_FLEETMEMBER_SHIPNAME)
//        val variant = mockk<ShipVariantAPI>(relaxed = true)
        val variant = createAnnonymousShipVariantAPI(TEST_HULL_SPEC_HULL_ID)
        // Prepare and put the hullmod in the map
        val exoticHullmod = object : ExoticHullmod() {
            override val hullModId: String
                get() = Companion.TEST_HULLMOD_ID

            override fun removeEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize, stats: MutableShipStatsAPI, id: String) {
                TODO("Not yet implemented")
            }
        }
        ExoticHullmodLookup.addToLookupMap(exoticHullmod)

        // Initial setup
        handler.shouldInstallHullmodExoticToVariant(
                exotic,
                fleetMember,
                variant,
                Optional.of(listOf(variant))
        )

        // Simulate 100 concurrent removals
        val jobs = (1..100).map {
            async(Dispatchers.Default) {
                handler.removeHullmodExoticFromFleetMember(
                        TEST_HULLMOD_ID,
                        fleetMember
                )
            }
        }
        jobs.awaitAll()

        Assert.assertEquals(0, handler.testsOnly_grabAllKeysForParticularFleetMember(fleetMember).size)
    }

    @Test
    fun testInvalidShipNameHandling() {
        val TEST_HULLMOD_ID = TEST_HULLMOD_ID

        val handler = HullmodExoticHandler
        val exotic = createAnnonymousHullmodExotic("test", EXOTIC_JSON, TEST_HULLMOD_ID)
        val fleetMember = object : FleetMemberAPI by createAnnonymousFleetMemberAPI(TEST_FLEETMEMBER_ID, TEST_FLEETMEMBER_SHIPNAME) {
            override fun getShipName(): String? = null
        }

        val result = handler.shouldInstallHullmodExoticToVariant(
                exotic,
                fleetMember,
                createAnnonymousShipVariantAPI(TEST_HULL_SPEC_HULL_ID),
                Optional.of(emptyList())
        )

        Assert.assertFalse(result)
        Assert.assertEquals(0, handler.testsOnly_grabAllKeysForParticularFleetMember(fleetMember).size)
    }

    @Test
    fun testCrossFleetMemberIsolation() {
        val TEST_HULLMOD_ID = TEST_HULLMOD_ID

        val handler = HullmodExoticHandler
        val exotic = createAnnonymousHullmodExotic("test", EXOTIC_JSON, TEST_HULLMOD_ID)

        val member1 = createAnnonymousFleetMemberAPI("fleet123", "name123")
        val member2 = createAnnonymousFleetMemberAPI("fleet456", "name456")

        handler.shouldInstallHullmodExoticToVariant(
                exotic,
                member1,
                createAnnonymousShipVariantAPI("hull1"),
                Optional.of(emptyList())
        )
        handler.shouldInstallHullmodExoticToVariant(
                exotic,
                member2,
                createAnnonymousShipVariantAPI("hull2"),
                Optional.of(emptyList())
        )

        Assert.assertEquals(1, handler.testsOnly_grabAllKeysForParticularFleetMember(member1).size)
        Assert.assertEquals(1, handler.testsOnly_grabAllKeysForParticularFleetMember(member2).size)
    }



    private fun createAnnonymousHullmodExotic(key: String, settingsJson: JSONObject = EXOTIC_JSON, hullmodId: String): HullmodExotic {
        return HullmodExotic(
                key = key,
                settingsObj = settingsJson,
                hullmodId = hullmodId,
                statDescriptionKey = "statDescriptionKey",
                color = Color.BLACK
        )
    }

    private fun createAnnonymousFleetMemberAPI(fleetMemberId: String, shipName: String): FleetMemberAPI {
        return object : FleetMemberAPI {
            override fun getCaptain(): PersonAPI {
                TODO("Not yet implemented")
            }

            override fun getStats(): MutableShipStatsAPI {
                TODO("Not yet implemented")
            }

            override fun getShipName(): String {
                return shipName
            }

            override fun setShipName(name: String?) {
                TODO("Not yet implemented")
            }

            override fun getId(): String {
                return fleetMemberId
            }

            override fun getSpecId(): String {
                TODO("Not yet implemented")
            }

            override fun getHullId(): String {
                TODO("Not yet implemented")
            }

            override fun getType(): FleetMemberType {
                TODO("Not yet implemented")
            }

            override fun isFlagship(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getNumFlightDecks(): Int {
                TODO("Not yet implemented")
            }

            override fun isCarrier(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isCivilian(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setFlagship(isFlagship: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getFleetPointCost(): Int {
                TODO("Not yet implemented")
            }

            override fun isFighterWing(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isFrigate(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isDestroyer(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isCruiser(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isCapital(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getNumFightersInWing(): Int {
                TODO("Not yet implemented")
            }

            override fun getFuelCapacity(): Float {
                TODO("Not yet implemented")
            }

            override fun getCargoCapacity(): Float {
                TODO("Not yet implemented")
            }

            override fun getMinCrew(): Float {
                TODO("Not yet implemented")
            }

            override fun getNeededCrew(): Float {
                TODO("Not yet implemented")
            }

            override fun getMaxCrew(): Float {
                TODO("Not yet implemented")
            }

            override fun getFuelUse(): Float {
                TODO("Not yet implemented")
            }

            override fun getRepairTracker(): RepairTrackerAPI {
                TODO("Not yet implemented")
            }

            override fun getHullSpec(): ShipHullSpecAPI {
                TODO("Not yet implemented")
            }

            override fun getFleetCommander(): PersonAPI {
                TODO("Not yet implemented")
            }

            override fun canBeDeployedForCombat(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getVariant(): ShipVariantAPI {
                TODO("Not yet implemented")
            }

            override fun getFleetData(): FleetDataAPI {
                TODO("Not yet implemented")
            }

            override fun setVariant(variant: ShipVariantAPI?, withRefit: Boolean, withStatsUpdate: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getCrewComposition(): CrewCompositionAPI {
                TODO("Not yet implemented")
            }

            override fun getStatus(): FleetMemberStatusAPI {
                TODO("Not yet implemented")
            }

            override fun getCrewFraction(): Float {
                TODO("Not yet implemented")
            }

            override fun getReplacementChassisCount(): Int {
                TODO("Not yet implemented")
            }

            override fun setStatUpdateNeeded(statUpdateNeeded: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getBuffManager(): BuffManagerAPI {
                TODO("Not yet implemented")
            }

            override fun isMothballed(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getDeployCost(): Float {
                TODO("Not yet implemented")
            }

            override fun setCaptain(commander: PersonAPI?) {
                TODO("Not yet implemented")
            }

            override fun getMemberStrength(): Float {
                TODO("Not yet implemented")
            }

            override fun getOwner(): Int {
                TODO("Not yet implemented")
            }

            override fun setOwner(owner: Int) {
                TODO("Not yet implemented")
            }

            override fun getBaseSellValue(): Float {
                TODO("Not yet implemented")
            }

            override fun getBaseBuyValue(): Float {
                TODO("Not yet implemented")
            }

            override fun needsRepairs(): Boolean {
                TODO("Not yet implemented")
            }

            override fun canBeRepaired(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getDeploymentPointsCost(): Float {
                TODO("Not yet implemented")
            }

            override fun getDeploymentCostSupplies(): Float {
                TODO("Not yet implemented")
            }

            override fun getBaseDeployCost(): Float {
                TODO("Not yet implemented")
            }

            override fun isAlly(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setAlly(isAlly: Boolean) {
                TODO("Not yet implemented")
            }

            override fun setFleetCommanderForStats(alternateFleetCommander: PersonAPI?, fleetForStats: FleetDataAPI?) {
                TODO("Not yet implemented")
            }

            override fun getFleetDataForStats(): FleetDataAPI {
                TODO("Not yet implemented")
            }

            override fun getFleetCommanderForStats(): PersonAPI {
                TODO("Not yet implemented")
            }

            override fun updateStats() {
                TODO("Not yet implemented")
            }

            override fun isStation(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getBaseDeploymentCostSupplies(): Float {
                TODO("Not yet implemented")
            }

            override fun getBaseValue(): Float {
                TODO("Not yet implemented")
            }

            override fun setSpriteOverride(spriteOverride: String?) {
                TODO("Not yet implemented")
            }

            override fun getSpriteOverride(): String {
                TODO("Not yet implemented")
            }

            override fun getOverrideSpriteSize(): Vector2f {
                TODO("Not yet implemented")
            }

            override fun setOverrideSpriteSize(overrideSpriteSize: Vector2f?) {
                TODO("Not yet implemented")
            }

            override fun isPhaseShip(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setId(id: String?) {
                TODO("Not yet implemented")
            }

            override fun getUnmodifiedDeploymentPointsCost(): Float {
                TODO("Not yet implemented")
            }

        }
    }

    private fun createAnnonymousShipVariantAPI(hullSpecHullId: String): ShipVariantAPI {
        return object: ShipVariantAPI {
            override fun clone(): ShipVariantAPI {
                TODO("Not yet implemented")
            }

            override fun getHullSpec(): ShipHullSpecAPI {
                return object: ShipHullSpecAPI {
                    override fun getShieldSpec(): ShipHullSpecAPI.ShieldSpecAPI {
                        TODO("Not yet implemented")
                    }

                    override fun getDefenseType(): ShieldAPI.ShieldType {
                        TODO("Not yet implemented")
                    }

                    override fun getHullId(): String {
                        return hullSpecHullId
                    }

                    override fun getHullName(): String {
                        TODO("Not yet implemented")
                    }

                    override fun getHints(): EnumSet<ShipHullSpecAPI.ShipTypeHints> {
                        TODO("Not yet implemented")
                    }

                    override fun getNoCRLossTime(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getCRToDeploy(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getCRLossPerSecond(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getCRLossPerSecond(stats: MutableShipStatsAPI?): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getBaseValue(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getOrdnancePoints(stats: MutableCharacterStatsAPI?): Int {
                        TODO("Not yet implemented")
                    }

                    override fun getHullSize(): ShipAPI.HullSize {
                        TODO("Not yet implemented")
                    }

                    override fun getHitpoints(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getArmorRating(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getFluxCapacity(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getFluxDissipation(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getShieldType(): ShieldAPI.ShieldType {
                        TODO("Not yet implemented")
                    }

                    override fun getAllWeaponSlotsCopy(): MutableList<WeaponSlotAPI> {
                        TODO("Not yet implemented")
                    }

                    override fun getSpriteName(): String {
                        TODO("Not yet implemented")
                    }

                    override fun isCompatibleWithBase(): Boolean {
                        TODO("Not yet implemented")
                    }

                    override fun getBaseHullId(): String {
                        TODO("Not yet implemented")
                    }

                    override fun getBaseShieldFluxPerDamageAbsorbed(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getHullNameWithDashClass(): String {
                        TODO("Not yet implemented")
                    }

                    override fun hasHullName(): Boolean {
                        TODO("Not yet implemented")
                    }

                    override fun getBreakProb(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getMinPieces(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getMaxPieces(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getFighterBays(): Int {
                        TODO("Not yet implemented")
                    }

                    override fun getMinCrew(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getMaxCrew(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getCargo(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getFuel(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getFuelPerLY(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun isDHull(): Boolean {
                        TODO("Not yet implemented")
                    }

                    override fun isDefaultDHull(): Boolean {
                        TODO("Not yet implemented")
                    }

                    override fun setDParentHullId(dParentHullId: String?) {
                        TODO("Not yet implemented")
                    }

                    override fun getDParentHullId(): String {
                        TODO("Not yet implemented")
                    }

                    override fun getDParentHull(): ShipHullSpecAPI {
                        TODO("Not yet implemented")
                    }

                    override fun getBaseHull(): ShipHullSpecAPI {
                        TODO("Not yet implemented")
                    }

                    override fun getBuiltInWings(): MutableList<String> {
                        TODO("Not yet implemented")
                    }

                    override fun isBuiltInWing(index: Int): Boolean {
                        TODO("Not yet implemented")
                    }

                    override fun getDesignation(): String {
                        TODO("Not yet implemented")
                    }

                    override fun hasDesignation(): Boolean {
                        TODO("Not yet implemented")
                    }

                    override fun isRestoreToBase(): Boolean {
                        TODO("Not yet implemented")
                    }

                    override fun setRestoreToBase(restoreToBase: Boolean) {
                        TODO("Not yet implemented")
                    }

                    override fun getModuleAnchor(): Vector2f {
                        TODO("Not yet implemented")
                    }

                    override fun setModuleAnchor(moduleAnchor: Vector2f?) {
                        TODO("Not yet implemented")
                    }

                    override fun setCompatibleWithBase(compatibleWithBase: Boolean) {
                        TODO("Not yet implemented")
                    }

                    override fun getTags(): MutableSet<String> {
                        TODO("Not yet implemented")
                    }

                    override fun addTag(tag: String?) {
                        TODO("Not yet implemented")
                    }

                    override fun hasTag(tag: String?): Boolean {
                        TODO("Not yet implemented")
                    }

                    override fun getRarity(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun getNameWithDesignationWithDashClass(): String {
                        TODO("Not yet implemented")
                    }

                    override fun getDescriptionId(): String {
                        TODO("Not yet implemented")
                    }

                    override fun isBaseHull(): Boolean {
                        TODO("Not yet implemented")
                    }

                    override fun setManufacturer(manufacturer: String?) {
                        TODO("Not yet implemented")
                    }

                    override fun getManufacturer(): String {
                        TODO("Not yet implemented")
                    }

                    override fun getFleetPoints(): Int {
                        TODO("Not yet implemented")
                    }

                    override fun getBuiltInMods(): MutableList<String> {
                        TODO("Not yet implemented")
                    }

                    override fun getWeaponSlotAPI(slotId: String?): WeaponSlotAPI {
                        TODO("Not yet implemented")
                    }

                    override fun getDescriptionPrefix(): String {
                        TODO("Not yet implemented")
                    }

                    override fun isBuiltInMod(modId: String?): Boolean {
                        TODO("Not yet implemented")
                    }

                    override fun addBuiltInMod(modId: String?) {
                        TODO("Not yet implemented")
                    }

                    override fun isCivilianNonCarrier(): Boolean {
                        TODO("Not yet implemented")
                    }

                    override fun setHullName(hullName: String?) {
                        TODO("Not yet implemented")
                    }

                    override fun setDesignation(designation: String?) {
                        TODO("Not yet implemented")
                    }

                    override fun isPhase(): Boolean {
                        TODO("Not yet implemented")
                    }

                    override fun getShipFilePath(): String {
                        TODO("Not yet implemented")
                    }

                    override fun getTravelDriveId(): String {
                        TODO("Not yet implemented")
                    }

                    override fun setTravelDriveId(travelDriveId: String?) {
                        TODO("Not yet implemented")
                    }

                    override fun getEngineSpec(): ShipHullSpecAPI.EngineSpecAPI {
                        TODO("Not yet implemented")
                    }

                    override fun getSuppliesToRecover(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun setSuppliesToRecover(suppliesToRecover: Float) {
                        TODO("Not yet implemented")
                    }

                    override fun getSuppliesPerMonth(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun setSuppliesPerMonth(suppliesPerMonth: Float) {
                        TODO("Not yet implemented")
                    }

                    override fun setRepairPercentPerDay(repairPercentPerDay: Float) {
                        TODO("Not yet implemented")
                    }

                    override fun setCRToDeploy(crToDeploy: Float) {
                        TODO("Not yet implemented")
                    }

                    override fun getNoCRLossSeconds(): Float {
                        TODO("Not yet implemented")
                    }

                    override fun setNoCRLossSeconds(noCRLossSeconds: Float) {
                        TODO("Not yet implemented")
                    }

                    override fun setCRLossPerSecond(crLossPerSecond: Float) {
                        TODO("Not yet implemented")
                    }

                    override fun getBuiltInWeapons(): HashMap<String, String> {
                        TODO("Not yet implemented")
                    }

                    override fun isBuiltIn(slotId: String?): Boolean {
                        TODO("Not yet implemented")
                    }

                    override fun addBuiltInWeapon(slotId: String?, weaponId: String?) {
                        TODO("Not yet implemented")
                    }

                    override fun getShipDefenseId(): String {
                        TODO("Not yet implemented")
                    }

                    override fun setShipDefenseId(shipDefenseId: String?) {
                        TODO("Not yet implemented")
                    }

                    override fun getShipSystemId(): String {
                        TODO("Not yet implemented")
                    }

                    override fun setShipSystemId(shipSystemId: String?) {
                        TODO("Not yet implemented")
                    }

                    override fun setDescriptionPrefix(descriptionPrefix: String?) {
                        TODO("Not yet implemented")
                    }

                    override fun getWeaponSlot(slotId: String?): WeaponSlotAPI {
                        TODO("Not yet implemented")
                    }

                    override fun setFleetPoints(fleetPoints: Int) {
                        TODO("Not yet implemented")
                    }

                    override fun setDescriptionId(descriptionId: String?) {
                        TODO("Not yet implemented")
                    }

                    override fun getHyperspaceJitterColor(): Color {
                        TODO("Not yet implemented")
                    }

                    override fun isDHullOldMethod(): Boolean {
                        TODO("Not yet implemented")
                    }

                }
            }

            override fun getDisplayName(): String {
                TODO("Not yet implemented")
            }

            override fun getDesignation(): String {
                TODO("Not yet implemented")
            }

            override fun getHullMods(): MutableCollection<String> {
                TODO("Not yet implemented")
            }

            override fun clearHullMods() {
                TODO("Not yet implemented")
            }

            override fun getHints(): EnumSet<ShipHullSpecAPI.ShipTypeHints> {
                TODO("Not yet implemented")
            }

            override fun addMod(modId: String?) {
                TODO("Not yet implemented")
            }

            override fun removeMod(modId: String?) {
                TODO("Not yet implemented")
            }

            override fun addWeapon(slotId: String?, weaponId: String?) {
                TODO("Not yet implemented")
            }

            override fun getNumFluxVents(): Int {
                TODO("Not yet implemented")
            }

            override fun getNumFluxCapacitors(): Int {
                TODO("Not yet implemented")
            }

            override fun getNonBuiltInWeaponSlots(): MutableList<String> {
                TODO("Not yet implemented")
            }

            override fun getWeaponId(slotId: String?): String {
                TODO("Not yet implemented")
            }

            override fun setNumFluxCapacitors(capacitors: Int) {
                TODO("Not yet implemented")
            }

            override fun setNumFluxVents(vents: Int) {
                TODO("Not yet implemented")
            }

            override fun setSource(source: VariantSource?) {
                TODO("Not yet implemented")
            }

            override fun clearSlot(slotId: String?) {
                TODO("Not yet implemented")
            }

            override fun getWeaponSpec(slotId: String?): WeaponSpecAPI {
                TODO("Not yet implemented")
            }

            override fun getFittedWeaponSlots(): MutableCollection<String> {
                TODO("Not yet implemented")
            }

            override fun autoGenerateWeaponGroups() {
                TODO("Not yet implemented")
            }

            override fun hasUnassignedWeapons(): Boolean {
                TODO("Not yet implemented")
            }

            override fun assignUnassignedWeapons() {
                TODO("Not yet implemented")
            }

            override fun getGroup(index: Int): WeaponGroupSpec {
                TODO("Not yet implemented")
            }

            override fun computeOPCost(stats: MutableCharacterStatsAPI?): Int {
                TODO("Not yet implemented")
            }

            override fun computeWeaponOPCost(stats: MutableCharacterStatsAPI?): Int {
                TODO("Not yet implemented")
            }

            override fun computeHullModOPCost(): Int {
                TODO("Not yet implemented")
            }

            override fun computeHullModOPCost(stats: MutableCharacterStatsAPI?): Int {
                TODO("Not yet implemented")
            }

            override fun getSource(): VariantSource {
                TODO("Not yet implemented")
            }

            override fun isStockVariant(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isEmptyHullVariant(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setHullVariantId(hullVariantId: String?) {
                TODO("Not yet implemented")
            }

            override fun getHullVariantId(): String {
                TODO("Not yet implemented")
            }

            override fun getWeaponGroups(): MutableList<WeaponGroupSpec> {
                TODO("Not yet implemented")
            }

            override fun addWeaponGroup(group: WeaponGroupSpec?) {
                TODO("Not yet implemented")
            }

            override fun setVariantDisplayName(variantName: String?) {
                TODO("Not yet implemented")
            }

            override fun getHullSize(): ShipAPI.HullSize {
                TODO("Not yet implemented")
            }

            override fun isFighter(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getFullDesignationWithHullName(): String {
                TODO("Not yet implemented")
            }

            override fun hasHullMod(id: String?): Boolean {
                TODO("Not yet implemented")
            }

            override fun getSlot(slotId: String?): WeaponSlotAPI {
                TODO("Not yet implemented")
            }

            override fun isCombat(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isStation(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getWingId(index: Int): String {
                TODO("Not yet implemented")
            }

            override fun setWingId(index: Int, wingId: String?) {
                TODO("Not yet implemented")
            }

            override fun getWings(): MutableList<String> {
                TODO("Not yet implemented")
            }

            override fun getLaunchBaysSlotIds(): MutableList<String> {
                TODO("Not yet implemented")
            }

            override fun getFittedWings(): MutableList<String> {
                TODO("Not yet implemented")
            }

            override fun setHullSpecAPI(hullSpec: ShipHullSpecAPI?) {
                TODO("Not yet implemented")
            }

            override fun getPermaMods(): MutableSet<String> {
                TODO("Not yet implemented")
            }

            override fun clearPermaMods() {
                TODO("Not yet implemented")
            }

            override fun removePermaMod(modId: String?) {
                TODO("Not yet implemented")
            }

            override fun addPermaMod(modId: String?) {
                TODO("Not yet implemented")
            }

            override fun addPermaMod(modId: String?, isSMod: Boolean) {
                TODO("Not yet implemented")
            }

            override fun isCarrier(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getSortedMods(): MutableList<String> {
                TODO("Not yet implemented")
            }

            override fun getSuppressedMods(): MutableSet<String> {
                TODO("Not yet implemented")
            }

            override fun addSuppressedMod(modId: String?) {
                TODO("Not yet implemented")
            }

            override fun removeSuppressedMod(modId: String?) {
                TODO("Not yet implemented")
            }

            override fun clearSuppressedMods() {
                TODO("Not yet implemented")
            }

            override fun isGoalVariant(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setGoalVariant(goalVariant: Boolean) {
                TODO("Not yet implemented")
            }

            override fun getNonBuiltInHullmods(): MutableCollection<String> {
                TODO("Not yet implemented")
            }

            override fun getWing(index: Int): FighterWingSpecAPI {
                TODO("Not yet implemented")
            }

            override fun getUnusedOP(stats: MutableCharacterStatsAPI?): Int {
                TODO("Not yet implemented")
            }

            override fun isCivilian(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getModuleSlots(): MutableList<String> {
                TODO("Not yet implemented")
            }

            override fun getStatsForOpCosts(): MutableShipStatsAPI {
                TODO("Not yet implemented")
            }

            override fun isLiner(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isFreighter(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isTanker(): Boolean {
                TODO("Not yet implemented")
            }

            override fun isDHull(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getStationModules(): MutableMap<String, String> {
                TODO("Not yet implemented")
            }

            override fun getNonBuiltInWings(): MutableList<String> {
                TODO("Not yet implemented")
            }

            override fun hasTag(tag: String?): Boolean {
                TODO("Not yet implemented")
            }

            override fun addTag(tag: String?) {
                TODO("Not yet implemented")
            }

            override fun removeTag(tag: String?) {
                TODO("Not yet implemented")
            }

            override fun getTags(): MutableCollection<String> {
                TODO("Not yet implemented")
            }

            override fun clearTags() {
                TODO("Not yet implemented")
            }

            override fun clear() {
                TODO("Not yet implemented")
            }

            override fun getOriginalVariant(): String {
                TODO("Not yet implemented")
            }

            override fun setOriginalVariant(targetVariant: String?) {
                TODO("Not yet implemented")
            }

            override fun getModuleVariant(slotId: String?): ShipVariantAPI {
                TODO("Not yet implemented")
            }

            override fun setModuleVariant(slotId: String?, variant: ShipVariantAPI?) {
                TODO("Not yet implemented")
            }

            override fun isTransport(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getVariantFilePath(): String {
                TODO("Not yet implemented")
            }

            override fun getSMods(): LinkedHashSet<String> {
                TODO("Not yet implemented")
            }

            override fun getFullDesignationWithHullNameForShip(): String {
                TODO("Not yet implemented")
            }

            override fun refreshBuiltInWings() {
                TODO("Not yet implemented")
            }

            override fun hasDMods(): Boolean {
                TODO("Not yet implemented")
            }

            override fun getSModdedBuiltIns(): LinkedHashSet<String> {
                TODO("Not yet implemented")
            }

            override fun isMayAutoAssignWeapons(): Boolean {
                TODO("Not yet implemented")
            }

            override fun setMayAutoAssignWeapons(mayAutoAssign: Boolean) {
                TODO("Not yet implemented")
            }

        }
    }

    companion object {
        val EXOTIC_JSON = JSONObject("{ \"name\": \"exotic\" }")
        const val TEST_FLEETMEMBER_ID = "fleetMemberId"
        const val TEST_FLEETMEMBER_SHIPNAME = "fleetMemberShipName"
        const val TEST_HULLMOD_ID = "hullmod123"

        const val TEST_HULL_SPEC_HULL_ID = "hull123"
    }
}
