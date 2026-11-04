package com.example.chagok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class RegionsTest {

    private val regions = Regions.parse(File("src/main/assets/${Regions.ASSET}").readText())

    @Test
    fun loadsEverySigungu() {
        assertEquals(256, regions.all.size)
    }

    @Test
    fun findsSigunguByPoint() {
        assertEquals("32030", regions.find(37.7949, 128.9196)?.code)
        assertEquals("32540", regions.find(37.6770, 128.7090)?.code)
        assertEquals("서울특별시 중구", regions.find(37.5665, 126.9780)?.name)
        assertEquals("제주특별자치도", regions.find(33.4634, 126.3100)?.province)
    }

    @Test
    fun separatesNeighborsAcrossBorder() {
        assertEquals("32540", regions.find(37.6896, 128.7575)?.code)
        assertEquals("32030", regions.find(37.6907, 128.7600)?.code)
    }

    @Test
    fun seaIsNoSigungu() {
        assertNull(regions.find(37.80, 129.10))
    }

    @Test
    fun demoRoutesStayOnLand() {
        Rules.routes.flatMap { Rules.path(it) }.forEach { assertNotNull(it.toString(), regions.find(it.lat, it.lon)) }
    }
}
