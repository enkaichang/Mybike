package com.example.mybike

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import com.example.mybike.fetchYouBikeData
import com.example.mybike.convertToGeoJson

class ApiTest {
    @Test
    fun testApi() = runBlocking {
        val data = fetchYouBikeData()
        assertNotNull("Data should not be null", data)
        assertTrue("Data should not be empty", data!!.isNotEmpty())
        
        val geoJson = convertToGeoJson(data)
        println(com.google.gson.Gson().toJson(geoJson))
    }
}
