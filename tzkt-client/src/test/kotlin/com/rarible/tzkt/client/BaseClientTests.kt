package com.rarible.tzkt.client

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.web.reactive.function.client.WebClient

abstract class BaseClientTests {

    val mockServer = MockWebServer()
    val client = WebClient.create(mockServer.url("").toString())
//    val client = WebClient.create("https://api.tzkt.io")

    fun mock(body: String) {
        mockServer.enqueue(
            MockResponse()
                .setBody(body)
                .setResponseCode(200)
                .addHeader("Content-Type", APPLICATION_JSON.toString())
        )
    }

    fun mock404() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(404)
        )
    }

    fun request() = mockServer.takeRequest()
}
