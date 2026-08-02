package org.khorum.oss.kontinuance.server.suite

import org.khorum.oss.kontinuance.persistence.InMemoryRunStore
import org.khorum.oss.kontinuance.persistence.RunStore
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

abstract class IntegrationTest(private val port: Int) {
    @TestConfiguration
    class EmptyStore {
        @Bean
        @Primary
        fun store(): RunStore = InMemoryRunStore()
    }

    protected val client: WebTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    fun post(uri: String, scope: RestPostBuilder.() -> Unit) {
        val builder = RestPostBuilder(uri)


    }
}

class RestPostBuilder(
    var uri: String? = null,
    var bodyValue: String? = null,
    var mediaType: MediaType = MediaType.APPLICATION_JSON
)

class TestBuilder(
    private val responseSpec: WebTestClient.ResponseSpec
) {
    fun isUnauthorized() {
        responseSpec.expectStatus().isUnauthorized
    }
}