package exoticatechnologies.util.tests

import com.fs.starfarer.api.campaign.BuffManagerAPI
import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.*
import exoticatechnologies.hullmods.exotics.HullmodExoticInstallData
import exoticatechnologies.hullmods.exotics.HullmodExoticKey
import exoticatechnologies.modifications.exotics.impl.HullmodExotic
import org.json.JSONObject
import org.junit.Test
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

class MapKeyTests {

    @Test
    fun test_that_HullmodExoticKey_equals_works() {

        val key1 = HullmodExoticKey(
                HullmodExotic(
                        key = "bla",
                        settingsObj = EXOTIC_JSON,
                        hullmodId = "hullmodId",
                        statDescriptionKey = "statDescriptionKey",
                        color = Color.BLACK
                ),
//                createAnnonymousFleetMemberAPI(TEST_FLEETMEMBER_ID)
                TEST_FLEETMEMBER_ID
        )

        val key2 = HullmodExoticKey(
                HullmodExotic(
                        key = "bla",
                        settingsObj = EXOTIC_JSON,
                        hullmodId = "hullmodId",
                        statDescriptionKey = "statDescriptionKey",
                        color = Color.BLACK
                ),
//                createAnnonymousFleetMemberAPI(TEST_FLEETMEMBER_ID)
                TEST_FLEETMEMBER_ID
        )

        println("Key1: "+key1)
        println("Key2: "+key2)

        assert(key1 == key2)
    }

    @Test
    fun test_how_many_keys_can_go_into_map() {
        val testMap = mutableMapOf<HullmodExoticKey, HullmodExoticInstallData>()
        val testMap2 = ConcurrentHashMap<HullmodExoticKey, HullmodExoticInstallData>()

        val key1 = HullmodExoticKey(
                hullmodExotic = createAnnonymousHullmodExotic(
                        key = "key",
                        hullmodId = "hullmodId"
                ),
//                parentFleetMember = createAnnonymousFleetMemberAPI(TEST_FLEETMEMBER_ID)
                parentFleetMemberId = TEST_FLEETMEMBER_ID
        )

        val key2 = HullmodExoticKey(
                hullmodExotic = createAnnonymousHullmodExotic(
                        key = "key2",
                        hullmodId = "hullmodId"
                ),
//                parentFleetMember = createAnnonymousFleetMemberAPI(TEST_FLEETMEMBER_ID)
                parentFleetMemberId = TEST_FLEETMEMBER_ID
        )

        val key3 = HullmodExoticKey(
                hullmodExotic = createAnnonymousHullmodExotic(
                        key = "key3",
                        hullmodId = "hullmodId"
                ),
//                parentFleetMember = createAnnonymousFleetMemberAPI(TEST_FLEETMEMBER_ID)
                parentFleetMemberId = TEST_FLEETMEMBER_ID
        )


        val testHullmodExoticData = HullmodExoticInstallData(
                createAnnonymousFleetMemberAPI(TEST_FLEETMEMBER_ID),
                emptyList(),
                emptyList(),
                emptyList()
        )


        // Try putting in more keys
        testMap.put(key1, testHullmodExoticData)
        testMap.put(key2, testHullmodExoticData)
        testMap.put(key3, testHullmodExoticData)

        testMap2.put(key1, testHullmodExoticData)
        testMap2.put(key2, testHullmodExoticData)
        testMap2.put(key3, testHullmodExoticData)


        // assert key equality
        assert(key1 == key2)
        assert(key2 == key3)
        assert(key1 == key3)
        // assert size
        assert(testMap.size == 1)
        assert(testMap.keys.size == 1)
        assert(testMap2.size == 1)
        assert(testMap2.keys.size == 1)
    }

    //TODO write some HullmodExotic tests



    private fun createAnnonymousHullmodExotic(key: String, settingsJson: JSONObject = EXOTIC_JSON, hullmodId: String): HullmodExotic {
        return HullmodExotic(
                key = key,
                settingsObj = settingsJson,
                hullmodId = hullmodId,
                statDescriptionKey = "statDescriptionKey",
                color = Color.BLACK
        )
    }

    private fun createAnnonymousFleetMemberAPI(fleetMemberId: String): FleetMemberAPI {
        return object : FleetMemberAPI {
            override fun getCaptain(): PersonAPI {
                TODO("Not yet implemented")
            }

            override fun getStats(): MutableShipStatsAPI {
                TODO("Not yet implemented")
            }

            override fun getShipName(): String {
                TODO("Not yet implemented")
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

    companion object {
        val EXOTIC_JSON = JSONObject("{ \"name\": \"exotic\" }")
        const val TEST_FLEETMEMBER_ID = "fleetMemberId"
    }
}
