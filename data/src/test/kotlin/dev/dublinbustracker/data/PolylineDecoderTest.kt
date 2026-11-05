package dev.dublinbustracker.data

import kotlin.test.Test
import kotlin.test.assertEquals

class PolylineDecoderTest {
    @Test
    fun `decodes the reference polyline from the encoding spec`() {
        val points = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")

        assertEquals(listOf(38.5, 40.7, 43.252), points.map { it.lat })
        assertEquals(listOf(-120.2, -120.95, -126.453), points.map { it.lon })
    }

    @Test
    fun `an empty string is an empty path`() {
        assertEquals(emptyList(), decodePolyline(""))
    }
}
