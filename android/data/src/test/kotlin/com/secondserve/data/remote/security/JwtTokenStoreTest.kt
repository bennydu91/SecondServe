package com.secondserve.data.remote.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class MockTokenStore : TokenStore {
    private val storage = mutableMapOf<String, String>()

    override fun saveToken(token: String) {
        storage["jwt_token"] = token
    }

    override fun getToken(): String? = storage["jwt_token"]

    override fun hasToken(): Boolean = storage.containsKey("jwt_token")

    override fun clearToken() {
        storage.remove("jwt_token")
    }
}

class JwtTokenStoreTest {

    private lateinit var tokenStore: TokenStore

    @BeforeEach
    fun setup() {
        tokenStore = MockTokenStore()
    }

    @Test
    fun testSaveAndGetToken() {
        val token = "test.jwt.token"
        tokenStore.saveToken(token)
        assertEquals(token, tokenStore.getToken())
    }

    @Test
    fun testHasTokenWhenTokenExists() {
        tokenStore.saveToken("test.jwt.token")
        assertTrue(tokenStore.hasToken())
    }

    @Test
    fun testHasTokenWhenTokenDoesNotExist() {
        assertFalse(tokenStore.hasToken())
    }

    @Test
    fun testClearToken() {
        tokenStore.saveToken("test.jwt.token")
        assertTrue(tokenStore.hasToken())
        tokenStore.clearToken()
        assertFalse(tokenStore.hasToken())
        assertNull(tokenStore.getToken())
    }

    @Test
    fun testOverwriteToken() {
        val token1 = "test.jwt.token1"
        val token2 = "test.jwt.token2"
        tokenStore.saveToken(token1)
        assertEquals(token1, tokenStore.getToken())
        tokenStore.saveToken(token2)
        assertEquals(token2, tokenStore.getToken())
    }

    @Test
    fun testGetTokenWhenEmpty() {
        assertNull(tokenStore.getToken())
    }
}
