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

data class Settings(val consented: Boolean = false, val voice: Boolean = true)

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
        settings = Settings(prefs.getBoolean(CONSENTED, false), prefs.getBoolean(VOICE, true))
        if (file.baseFile.exists()) {
            try {
                val root = JSONObject(String(file.readFully()))
                trips += root.getJSONArray(TRIPS).objects().map(::trip)
                stamps += root.getJSONArray(STAMPS).objects().map { Stamp(it.getString("code"), LocalDateTime.parse(it.getString("at"))) }
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
        prefs.edit().putBoolean(CONSENTED, next.consented).putBoolean(VOICE, next.voice).apply()
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
                .put(STAMPS, JSONArray(stamps.map { JSONObject().put("code", it.code).put("at", it.at.toString()) }))
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

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    private fun json(trip: Trip) = JSONObject()
        .put("id", trip.id)
        .put("startedAt", trip.startedAt.toString())
        .put("endedAt", trip.endedAt.toString())
        .put("distanceM", trip.distanceM)
        .put("regionM", JSONObject(trip.regionM))
        .put("wiperM", trip.wiperM)
        .put("lowBeamM", trip.lowBeamM)
        .put("coastWindowM", trip.coastWindowM)
        .put("spots", JSONArray(trip.spots))
        .put("litPasses", JSONArray(trip.litPasses))
        .put("sunrise", trip.sunrise)
        .put("sunset", trip.sunset)

    private fun trip(o: JSONObject): Trip {
        val regions = o.getJSONObject("regionM")
        return Trip(
            o.getString("id"),
            LocalDateTime.parse(o.getString("startedAt")),
            LocalDateTime.parse(o.getString("endedAt")),
            o.getDouble("distanceM"),
            regions.keys().asSequence().associateWith { regions.getDouble(it) },
            o.getDouble("wiperM"),
            o.getDouble("lowBeamM"),
            o.getDouble("coastWindowM"),
            o.getJSONArray("spots").strings(),
            o.getJSONArray("litPasses").strings(),
            o.getBoolean("sunrise"),
            o.getBoolean("sunset"),
        )
    }

    private const val NAME = "chagok"
    private const val OPEN = "chagok-open"
    private const val TRIPS = "trips"
    private const val STAMPS = "stamps"
    private const val EARNED = "earned"
    private const val CONSENTED = "consented"
    private const val VOICE = "voice"
}
