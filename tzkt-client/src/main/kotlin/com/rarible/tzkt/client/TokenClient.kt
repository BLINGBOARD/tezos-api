package com.rarible.tzkt.client

import com.rarible.tzkt.meta.MetaService
import com.rarible.tzkt.model.ItemId
import com.rarible.tzkt.model.Page
import com.rarible.tzkt.model.Part
import com.rarible.tzkt.model.Token
import com.rarible.tzkt.model.TokenMeta
import com.rarible.tzkt.royalties.RoyaltiesHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.web.reactive.function.client.WebClient

class TokenClient(
    webClient: WebClient,
    val metaService: MetaService,
    val royaltyHander: RoyaltiesHandler
) : BaseClient(webClient) {

    suspend fun token(itemId: String): Token {
        val parsed = ItemId.parse(itemId)
        val tokens = invoke<List<Token>> {
            it.path(BASE_PATH)
                .queryParam("contract", parsed.contract)
                .queryParam("tokenId", parsed.tokenId)
                .queryParam("token.standard", "fa2")
        }
        val result = tokens.first()

        // enrich with parsed meta
        return result.copy(meta = metaService.meta(result))
    }

    suspend fun tokenMeta(itemId: String): TokenMeta {
        val parsed = ItemId.parse(itemId)
        val tokens = invoke<List<Token>> {
            it.path(BASE_PATH)
                .queryParam("contract", parsed.contract)
                .queryParam("tokenId", parsed.tokenId)
        }
        val token = tokens.first()
        return metaService.meta(token)
    }

    suspend fun isNft(itemId: String): Boolean? {
        val parsed = ItemId.parse(itemId)
        val token = invoke<List<Token>> {
            it.path(BASE_PATH)
                .queryParam("contract", parsed.contract)
                .queryParam("tokenId", parsed.tokenId)
        }.first()
        return token.metadata?.let { it["artifactUri"] != null }
    }

    suspend fun tokens(size: Int = DEFAULT_SIZE, continuation: String?, sortAsc: Boolean = true): Page<Token> {
        val tokens = invoke<List<Token>> { builder ->
            builder.path(BASE_PATH)
                .queryParam("token.standard", "fa2")
                .apply {
                    queryParam("limit", size)
                    continuation?.let { queryParam("offset.cr", it) }
                    val sorting = if (sortAsc) "sort.asc" else "sort.desc"
                    queryParam(sorting, "id")
                    queryParam("metadata.artifactUri.null", "false")
                }

            // enrich with parsed meta
        }.map { token -> token.copy(meta = metaService.meta(token)) }
        return Page.Get(
            items = tokens,
            size = size,
            last = { it.id.toString() }
        )
    }

    suspend fun allTokensByLastUpdate(
        size: Int = DEFAULT_SIZE,
        continuation: String?,
        sortAsc: Boolean = true
    ): Page<Token> {
        var prefix = ""
        val tokens = invoke<List<Token>> { builder ->
            builder.path(BASE_PATH)
                .queryParam("token.standard", "fa2")
                .apply {
                    queryParam("limit", size)
                    continuation?.let {
                        val parsedContinuation = continuation.split("_")
                        if (parsedContinuation.size != 3) {
                            throw Exception("Could not parse continuation $continuation")
                        }
                        val comparison = parsedContinuation[0]
                        queryParam("lastLevel.$comparison", parsedContinuation[1])
                        queryParam("id.ni", parsedContinuation[2])
                    }
                    val sorting = if (sortAsc) "sort.asc" else "sort.desc"
                    queryParam(sorting, "lastLevel")
                    queryParam("metadata.artifactUri.null", "false")
                }

            // enrich with parsed meta
        }.map { token -> token.copy(meta = metaService.meta(token)) }

        val lastToken = tokens.last()
        val lastLevel = lastToken.lastLevel
        val continuationId = tokens.filter {
            it.lastLevel == lastLevel
        }.map {
            it.id
        }

        val sameLastLevelTokens = invoke<List<Token>> { builder ->
            builder.path(BASE_PATH)
                .queryParam("token.standard", "fa2")
                .apply {
                    queryParam("limit", size)
                    val sorting = if (sortAsc) "sort.asc" else "sort.desc"
                    queryParam(sorting, "lastLevel")
                    queryParam("metadata.artifactUri.null", "false")
                    queryParam("lastLevel", lastLevel)
                    queryParam("id.ni", continuationId)
                }

            // enrich with parsed meta
        }.map { token -> token.copy(meta = metaService.meta(token)) }

        prefix = if (sameLastLevelTokens.isNotEmpty()) {
            if (sortAsc)
                "ge"
            else
                "le"
        } else {
            if (sortAsc)
                "gt"
            else
                "lt"
        }

        return Page.Get(
            items = tokens,
            size = size,
            last = { "${prefix}_${lastLevel}_${continuationId.joinToString(",")}" }
        )
    }

    suspend fun tokens(ids: List<String>): List<Token> {
        val tokens = coroutineScope {
            ids
                .map {
                    async { token(it) }
                }
                .awaitAll()
        }
        return tokens
    }

    suspend fun royalty(itemId: String): List<Part> {
        val parsed = ItemId.parse(itemId)
        return royaltyHander.processRoyalties(parsed.contract, parsed.tokenId)
    }

    companion object {
        const val BASE_PATH = "v1/tokens"
        const val DEFAULT_SIZE = 1000
    }
}
