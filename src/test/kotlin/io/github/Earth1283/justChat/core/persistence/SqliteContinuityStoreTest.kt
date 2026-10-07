package io.github.Earth1283.justChat.core.persistence

import io.github.Earth1283.justChat.core.model.ChatMetadata
import java.nio.file.Files
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqliteContinuityStoreTest {
    private val directory = Files.createTempDirectory("justchat-sqlite")
    private val file = directory.resolve("data/justchat.db")

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `round trips metadata colors and ignores across reopen`() {
        val owner = UUID.randomUUID()
        val target = UUID.randomUUID()
        SqliteContinuityStore.open(file).use { store ->
            store.write(
                WriteBatch(
                    metadata = listOf(MetadataRow(owner, "Steve", ChatMetadata("[A] ", null, "admin"), "LUCKPERMS_API", 123)),
                    colors = listOf(ColorRow(owner, "#ff8800")),
                    ignoreOpsInOrder = listOf(IgnoreOp.Add(owner, target, "Alex")),
                ),
            )
        }
        SqliteContinuityStore.open(file).use { store ->
            val loaded = store.loadPlayer(owner)
            assertEquals("[A] ", loaded.metadata?.metadata?.prefix)
            assertNull(loaded.metadata?.metadata?.suffix)
            assertEquals("admin", loaded.metadata?.metadata?.primaryGroup)
            assertEquals("Steve", loaded.metadata?.username)
            assertEquals("#ff8800", loaded.chatColor)
            assertEquals(mapOf(target to "Alex"), loaded.ignoredNamesByUuid)
            assertTrue(store.hasLiveHistory())
        }
    }

    @Test
    fun `an unknown player loads as empty`() {
        SqliteContinuityStore.open(file).use { store ->
            val loaded = store.loadPlayer(UUID.randomUUID())
            assertNull(loaded.metadata)
            assertNull(loaded.chatColor)
            assertTrue(loaded.ignoredNamesByUuid.isEmpty())
            assertFalse(store.hasLiveHistory())
        }
    }

    @Test
    fun `ignore operations apply in order and color reset deletes the preference`() {
        val owner = UUID.randomUUID()
        val target = UUID.randomUUID()
        SqliteContinuityStore.open(file).use { store ->
            store.write(WriteBatch(emptyList(), listOf(ColorRow(owner, "red")), listOf(IgnoreOp.Add(owner, target, "Alex"))))
            store.write(WriteBatch(emptyList(), listOf(ColorRow(owner, null)), listOf(IgnoreOp.Add(owner, target, "Alex"), IgnoreOp.Remove(owner, target))))
            val loaded = store.loadPlayer(owner)
            assertNull(loaded.chatColor)
            assertTrue(loaded.ignoredNamesByUuid.isEmpty())
        }
    }

    @Test
    fun `upserting metadata replaces the previous row`() {
        val id = UUID.randomUUID()
        SqliteContinuityStore.open(file).use { store ->
            store.write(WriteBatch(listOf(MetadataRow(id, "A", ChatMetadata("one", null, null), "VAULT_API", 1)), emptyList(), emptyList()))
            store.write(WriteBatch(listOf(MetadataRow(id, "B", ChatMetadata("two", null, null), "VAULT_API", 2)), emptyList(), emptyList()))
            assertEquals("two", store.loadPlayer(id).metadata?.metadata?.prefix)
            assertEquals("B", store.loadPlayer(id).metadata?.username)
        }
    }
}
