package com.example.chagok

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

object Drive {

    var driving by mutableStateOf(false)
        private set
    var reveal by mutableStateOf<Reveal?>(null)
    var replaying by mutableStateOf<Route?>(null)
        private set

    /** 정차 중 근처 명소를 찾을 때 쓰는 위치. 화면이 너무 자주 바뀌지 않게 조금 움직이면 그대로 둡니다. */
    var here by mutableStateOf<Point?>(null)
        private set

    private lateinit var context: Context
    private lateinit var vehicle: Vehicle
    private lateinit var fused: FusedLocation
    private lateinit var navi: NaviHelper
    private lateinit var voice: Voice
    private val main = Handler(Looper.getMainLooper())
    private val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private val zone get() = ZoneId.systemDefault()

    private var started = false
    private var locating = false
    private var wiper = false
    private var lowBeam = false
    private var position: Point? = null
    private var recorder: Recorder? = null
    private var pending: Reveal? = null
    private var replayStep: Runnable? = null
    private val alertIds = mutableListOf<String>()
    private val nearEnds = mutableSetOf<Pair<String, Boolean>>()

    fun start(appContext: Context) {
        if (started) return
        started = true
        context = appContext
        voice = Voice(context)
        Store.openTrip()?.let { Store.add(it, Rules.judge(it, Store.trips, Store.stamps, Store.earned)) }

        vehicle = Vehicle(context)
        vehicle.initialize()
        vehicle.getGear().registerGearSelection(gearListener)
        vehicle.getWiper().registerWiperState(windshield, wiperListener)
        vehicle.getLight().registerLowBeamLightState(lowBeamListener)

        navi = NaviHelper(context)
        navi.initialize()

        fused = FusedLocation(context)
        fused.initialize()
        watchLocation()
    }

    fun watchLocation() {
        if (!started || locating || !fused.isSupported() || context.checkSelfPermission(LOCATION_PERMISSION) != PackageManager.PERMISSION_GRANTED) return
        locating = true
        fused.registerFusedLocationCallback(locationListener)
        Rules.spots.forEach { spot ->
            addAlert(spot, spot.start, atEnd = false)
            addAlert(spot, spot.end, atEnd = true)
        }
    }

    fun stop() {
        if (!started) return
        started = false
        stopReplay()
        main.removeCallbacks(showReveal)
        vehicle.getGear().unregisterGearSelection(gearListener)
        vehicle.getWiper().unregisterWiperState(windshield, wiperListener)
        vehicle.getLight().unregisterLowBeamLightState(lowBeamListener)
        vehicle.release()
        navi.release()
        if (locating) {
            fused.unregisterFusedLocationCallback(locationListener)
            alertIds.forEach(fused::removeProximityAlert)
            alertIds.clear()
            locating = false
        }
        fused.release()
        voice.release()
    }

    fun replay(route: Route) {
        stopReplay()
        replaying = route
        val path = Rules.path(route)
        var index = 0
        val step = object : Runnable {
            override fun run() {
                val point = path.getOrNull(index++) ?: return stopReplay()
                onPosition(point)
                Rules.spots.forEach { spot ->
                    replayAlert(spot, spot.start, point, atEnd = false)
                    replayAlert(spot, spot.end, point, atEnd = true)
                }
                main.postDelayed(this, REPLAY_TICK_MS)
            }
        }
        replayStep = step
        main.post(step)
    }

    /** 명소 시작점을 내비 목적지로 보냅니다. */
    fun guide(spot: Spot) {
        navi.requestRoute(RouteInfo(spot.start.lat, spot.start.lon, poiName = context.getString(spot.name), poiId = spot.code, address = "", poiSubId = ""))
    }

    fun stopReplay() {
        replayStep?.let(main::removeCallbacks)
        replayStep = null
        replaying = null
        nearEnds.clear()
    }

    private fun addAlert(spot: Spot, point: Point, atEnd: Boolean) {
        val listener = object : ProximityAlertListener {
            override fun onRegistered(alertId: String) {
                main.post { alertIds += alertId }
            }

            override fun onTransition(event: ProximityAlertEvent) {
                if (event.transition == ProximityTransition.ENTER) main.post { if (replaying == null) onSpot(spot, atEnd) }
            }

            override fun onError(e: Exception) = fail("alert ${spot.code}", e)
        }
        fused.addProximityAlert(ProximityAlertRequest(point.lat, point.lon, Rules.SPOT_RADIUS_M, setOf(ProximityTransition.ENTER)), listener)
    }

    private fun replayAlert(spot: Spot, end: Point, at: Point, atEnd: Boolean) {
        val key = spot.code to atEnd
        if (Rules.distanceM(end, at) >= Rules.SPOT_RADIUS_M) nearEnds -= key
        else if (nearEnds.add(key)) onSpot(spot, atEnd)
    }

    private fun onGear(gear: VehicleGear?) {
        if (gear == null || gear == VehicleGear.GEAR_UNKNOWN) return
        val moving = gear != VehicleGear.GEAR_PARK
        if (moving == driving) return
        driving = moving
        if (moving) open() else close()
    }

    private fun open() {
        main.removeCallbacks(showReveal)
        reveal = null
        val now = LocalDateTime.now()
        recorder = Recorder("trip-${System.currentTimeMillis()}", now)
    }

    private fun close() {
        val trip = recorder?.snapshot(LocalDateTime.now()) ?: return
        recorder = null
        val result = Rules.judge(trip, Store.trips, Store.stamps, Store.earned)
        Store.add(trip, result)
        if (result.isEmpty) return
        pending = result
        main.postDelayed(showReveal, REVEAL_DELAY_MS)
    }

    private val showReveal = Runnable {
        val result = pending ?: return@Runnable
        pending = null
        reveal = result
        voice.speak(context.getString(R.string.say_reveal, result.stamps.size))
    }

    private fun onPosition(point: Point) {
        val previous = position
        position = point
        if (here.let { it == null || Rules.distanceM(it, point) > HERE_STEP_M }) here = point
        val trip = recorder ?: return
        val meters = previous?.let { Rules.distanceM(it, point) } ?: return
        if (meters > MAX_STEP_M) return
        val region = Store.regions?.find(point.lat, point.lon)?.code
        trip.move(meters, region, momentAt(point), wiper)
        Store.saveOpen(trip.snapshot(LocalDateTime.now()))
    }

    private fun onSpot(spot: Spot, atEnd: Boolean) {
        recorder?.enter(spot, atEnd, momentAt(position ?: spot.end))
    }

    private fun momentAt(point: Point) = Rules.moment(ZonedDateTime.now(zone), point, wiper, lowBeam)

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location != null) main.post { if (replaying == null) onPosition(Point(location.latitude, location.longitude)) }
        }

        override fun onError(e: Exception) = fail("location", e)
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            main.post { onGear(gear) }
        }

        override fun onFailed(e: Exception) = fail("gear", e)
    }

    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) {
            main.post { wiper = state == WiperState.ON }
        }

        override fun onFailed(e: Exception) = fail("wiper", e)
    }

    private val lowBeamListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) {
            main.post { lowBeam = state == LightState.ON }
        }

        override fun onFailed(e: Exception) = fail("lowBeam", e)
    }

    private fun fail(what: String, e: Exception) {
        Log.e(TAG, what, e)
    }

    const val LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    private const val TAG = "Chagok"
    private const val REVEAL_DELAY_MS = 3_000L
    private const val REPLAY_TICK_MS = 500L
    private const val MAX_STEP_M = 1_000.0
    private const val HERE_STEP_M = 500.0
}
