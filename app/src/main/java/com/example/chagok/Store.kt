package com.example.chagok

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.util.AtomicFile
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime

data class Settings(val consented: Boolean = false)

object Store {

    private lateinit var prefs: SharedPreferences
    private lateinit var file: AtomicFile
    private lateinit var openFile: AtomicFile

    val trips = mutableStateListOf<Trip>()
    val stamps = mutableStateListOf<Stamp>()
    val earned = mutableStateListOf<Earned>()
    var settings by mutableStateOf(Settings())
        private set
    var regions by mutableStateOf<Regions?>(null)
        private set

    fun load(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        file = AtomicFile(File(context.filesDir, "$NAME.json"))
        openFile = AtomicFile(File(context.filesDir, "$OPEN.json"))
        settings = Settings(prefs.getBoolean(CONSENTED, false))
        if (file.baseFile.exists()) {
            try {
                val root = JSONObject(String(file.readFully()))
                trips += root.getJSONArray(TRIPS).objects().map(::trip)
                stamps += root.getJSONArray(STAMPS).objects().map { Stamp(it.getString("code"), Moment.valueOf(it.getString("moment")), Season.valueOf(it.getString("season")), LocalDateTime.parse(it.getString("at"))) }
                earned += root.getJSONArray(EARNED).objects().map { Earned(Badge.valueOf(it.getString("badge")), LocalDateTime.parse(it.getString("at"))) }
            } catch (e: Exception) {
                Log.e(NAME, "records unreadable, starting empty", e)
                file.baseFile.renameTo(File(context.filesDir, "$NAME.broken.json"))
                trips.clear()
                stamps.clear()
                earned.clear()
            }
        }
        Thread {
            val parsed = Regions.parse(context.assets.open(Regions.ASSET).bufferedReader().readText())
            Handler(context.mainLooper).post { regions = parsed }
        }.start()
    }

    fun update(next: Settings) {
        settings = next
        prefs.edit().putBoolean(CONSENTED, next.consented).apply()
    }

    fun openTrip(): Trip? = if (openFile.baseFile.exists()) runCatching { trip(JSONObject(String(openFile.readFully()))) }.getOrNull() else null

    fun saveOpen(trip: Trip?) {
        if (trip == null) {
            openFile.delete()
            return
        }
        write(openFile, json(trip))
    }

    fun add(trip: Trip, reveal: Reveal) {
        trips += trip
        stamps += reveal.stamps
        earned += reveal.badges
        save()
        saveOpen(null)
    }

    /** 시연용 지난 기록을 한 주행씩 판정해서 쌓습니다. */
    fun addSamples(trips: List<Trip>) {
        trips.forEach { add(it, Rules.judge(it, this.trips, stamps, earned)) }
    }

    fun clear() {
        trips.clear()
        stamps.clear()
        earned.clear()
        save()
    }

    private fun save() {
        write(
            file,
            JSONObject()
                .put(TRIPS, JSONArray(trips.map(::json)))
                .put(STAMPS, JSONArray(stamps.map { JSONObject().put("code", it.code).put("moment", it.moment.name).put("season", it.season.name).put("at", it.at.toString()) }))
                .put(EARNED, JSONArray(earned.map { JSONObject().put("badge", it.badge.name).put("at", it.at.toString()) })),
        )
    }

    private fun write(target: AtomicFile, root: JSONObject) {
        val out = target.startWrite()
        try {
            out.write(root.toString().toByteArray())
            target.finishWrite(out)
        } catch (e: Exception) {
            target.failWrite(out)
            Log.e(NAME, "records write failed", e)
        }
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun json(trip: Trip) = JSONObject()
        .put("id", trip.id)
        .put("startedAt", trip.startedAt.toString())
        .put("endedAt", trip.endedAt.toString())
        .put("distanceM", trip.distanceM)
        .put("places", JSONObject(trip.places.mapValues { (_, moments) -> JSONObject(moments.mapKeys { it.key.name }) }))
        .put("wiperM", trip.wiperM)
        .put("spots", JSONObject(trip.spots.mapValues { it.value.name }))

    private fun trip(o: JSONObject): Trip {
        val places = o.getJSONObject("places")
        val spots = o.getJSONObject("spots")
        return Trip(
            o.getString("id"),
            LocalDateTime.parse(o.getString("startedAt")),
            LocalDateTime.parse(o.getString("endedAt")),
            o.getDouble("distanceM"),
            places.keys().asSequence().associateWith { code ->
                val moments = places.getJSONObject(code)
                moments.keys().asSequence().associate { Moment.valueOf(it) to moments.getDouble(it) }
            },
            o.getDouble("wiperM"),
            spots.keys().asSequence().associateWith { Moment.valueOf(spots.getString(it)) },
        )
    }

    private const val NAME = "chagok"
    private const val OPEN = "chagok-open"
    private const val TRIPS = "trips"
    private const val STAMPS = "stamps"
    private const val EARNED = "earned"
    private const val CONSENTED = "consented"
}
