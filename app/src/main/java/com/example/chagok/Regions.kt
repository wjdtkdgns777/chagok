package com.example.chagok

import org.json.JSONArray
import org.json.JSONObject

class Region(val code: String, val name: String, val rings: List<DoubleArray>) {

    val province: String get() = name.substringBefore(' ')

    private val minLon = rings.minOf { ring -> (ring.indices step 2).minOf { ring[it] } }
    private val maxLon = rings.maxOf { ring -> (ring.indices step 2).maxOf { ring[it] } }
    private val minLat = rings.minOf { ring -> (1 until ring.size step 2).minOf { ring[it] } }
    private val maxLat = rings.maxOf { ring -> (1 until ring.size step 2).maxOf { ring[it] } }

    fun contains(lat: Double, lon: Double): Boolean {
        if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) return false
        return rings.count { crosses(it, lat, lon) } % 2 == 1
    }

    private fun crosses(ring: DoubleArray, lat: Double, lon: Double): Boolean {
        var inside = false
        var j = ring.size - 2
        for (i in ring.indices step 2) {
            val x1 = ring[i]
            val y1 = ring[i + 1]
            val x2 = ring[j]
            val y2 = ring[j + 1]
            if ((y1 > lat) != (y2 > lat) && lon < (x2 - x1) * (lat - y1) / (y2 - y1) + x1) inside = !inside
            j = i
        }
        return inside
    }
}

class Regions(val all: List<Region>) {

    private var last: Region? = null

    fun find(lat: Double, lon: Double): Region? {
        last?.takeIf { it.contains(lat, lon) }?.let { return it }
        return all.firstOrNull { it.contains(lat, lon) }?.also { last = it }
    }

    fun byCode(code: String) = all.firstOrNull { it.code == code }

    companion object {
        const val ASSET = "sigungu.geojson"

        fun parse(text: String) = Regions(
            JSONObject(text).getJSONArray("features").objects().map { feature ->
                val properties = feature.getJSONObject("properties")
                val geometry = feature.getJSONObject("geometry")
                val coordinates = geometry.getJSONArray("coordinates")
                val polygons = if (geometry.getString("type") == "MultiPolygon") coordinates.arrays() else listOf(coordinates)
                Region(properties.getString("code"), properties.getString("name"), polygons.flatMap { it.arrays() }.map(::ring))
            },
        )

        private fun ring(points: JSONArray) = DoubleArray(points.length() * 2) { points.getJSONArray(it / 2).getDouble(it % 2) }

        private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

        private fun JSONArray.arrays() = (0 until length()).map { getJSONArray(it) }
    }
}
