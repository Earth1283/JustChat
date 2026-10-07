package io.github.Earth1283.justChat.core.persistence

import io.github.Earth1283.justChat.core.model.ChatMetadata
import io.github.Earth1283.justChat.core.model.MetadataSource
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.util.Properties
import java.util.UUID

class SqliteContinuityStore private constructor(private val conn: Connection) : ContinuityStore {
    private val lock = Any()

    override fun loadPlayer(uuid: UUID): StoredPlayer = synchronized(lock) {
        val id = uuid.toString()
        var meta: MetadataRow? = null
        conn.prepareStatement(
            "SELECT last_known_username, prefix, suffix, primary_group, source, updated_at FROM player_metadata WHERE uuid = ?",
        ).use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    meta = MetadataRow(
                        uuid = uuid,
                        username = rs.getString(1),
                        metadata = ChatMetadata(rs.getString(2), rs.getString(3), rs.getString(4)),
                        sourceName = rs.getString(5) ?: "",
                        updatedAt = rs.getLong(6),
                    )
                }
            }
        }
        var color: String? = null
        conn.prepareStatement("SELECT chat_color FROM player_prefs WHERE uuid = ?").use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) color = rs.getString(1) }
        }
        val ignores = LinkedHashMap<UUID, String>()
        conn.prepareStatement("SELECT target, target_name FROM ignores WHERE owner = ?").use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val target = runCatching { UUID.fromString(rs.getString(1)) }.getOrNull() ?: continue
                    ignores[target] = rs.getString(2) ?: ""
                }
            }
        }
        StoredPlayer(meta, color, ignores)
    }

    override fun write(batch: WriteBatch) {
        if (batch.isEmpty) return
        synchronized(lock) {
            val auto = conn.autoCommit
            conn.autoCommit = false
            try {
                if (batch.metadata.isNotEmpty()) {
                    conn.prepareStatement(
                        """INSERT INTO player_metadata (uuid, last_known_username, prefix, suffix, primary_group, source, updated_at)
                           VALUES (?, ?, ?, ?, ?, ?, ?)
                           ON CONFLICT(uuid) DO UPDATE SET last_known_username = excluded.last_known_username,
                             prefix = excluded.prefix, suffix = excluded.suffix, primary_group = excluded.primary_group,
                             source = excluded.source, updated_at = excluded.updated_at""",
                    ).use { ps ->
                        for (r in batch.metadata) {
                            ps.setString(1, r.uuid.toString())
                            ps.setString(2, r.username)
                            ps.setString(3, r.metadata.prefix)
                            ps.setString(4, r.metadata.suffix)
                            ps.setString(5, r.metadata.primaryGroup)
                            ps.setString(6, r.sourceName)
                            ps.setLong(7, r.updatedAt)
                            ps.addBatch()
                        }
                        ps.executeBatch()
                    }
                }
                for (c in batch.colors) {
                    if (c.isReset) {
                        conn.prepareStatement("DELETE FROM player_prefs WHERE uuid = ?").use { ps ->
                            ps.setString(1, c.uuid.toString()); ps.executeUpdate()
                        }
                    } else {
                        conn.prepareStatement(
                            "INSERT INTO player_prefs (uuid, chat_color) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET chat_color = excluded.chat_color",
                        ).use { ps ->
                            ps.setString(1, c.uuid.toString()); ps.setString(2, c.color); ps.executeUpdate()
                        }
                    }
                }
                for (op in batch.ignoreOpsInOrder) {
                    when (op) {
                        is IgnoreOp.Add -> conn.prepareStatement(
                            """INSERT INTO ignores (owner, target, target_name, created_at) VALUES (?, ?, ?, ?)
                               ON CONFLICT(owner, target) DO UPDATE SET target_name = excluded.target_name""",
                        ).use { ps ->
                            ps.setString(1, op.owner.toString()); ps.setString(2, op.target.toString())
                            ps.setString(3, op.targetName); ps.setLong(4, System.currentTimeMillis()); ps.executeUpdate()
                        }
                        is IgnoreOp.Remove -> conn.prepareStatement("DELETE FROM ignores WHERE owner = ? AND target = ?").use { ps ->
                            ps.setString(1, op.owner.toString()); ps.setString(2, op.target.toString()); ps.executeUpdate()
                        }
                    }
                }
                conn.commit()
            } catch (t: Throwable) {
                runCatching { conn.rollback() }
                throw t
            } finally {
                runCatching { conn.autoCommit = auto }
            }
        }
    }

    override fun hasLiveHistory(): Boolean = synchronized(lock) {
        conn.prepareStatement("SELECT 1 FROM player_metadata WHERE source IN (?, ?) LIMIT 1").use { ps ->
            ps.setString(1, MetadataSource.LUCKPERMS_API.name)
            ps.setString(2, MetadataSource.VAULT_API.name)
            ps.executeQuery().use { it.next() }
        }
    }

    override fun close() {
        synchronized(lock) { runCatching { conn.close() } }
    }

    companion object {
        fun open(file: Path): SqliteContinuityStore {
            file.parent?.let { Files.createDirectories(it) }
            val conn = connectWithoutDriverManager(file)
            try {
                conn.createStatement().use { st ->
                    st.execute("PRAGMA journal_mode=WAL")
                    st.execute("PRAGMA synchronous=NORMAL")
                    st.execute("PRAGMA foreign_keys=ON")
                    st.execute("PRAGMA busy_timeout=5000")
                    st.execute(
                        """CREATE TABLE IF NOT EXISTS player_metadata (
                             uuid TEXT PRIMARY KEY NOT NULL,
                             last_known_username TEXT,
                             prefix TEXT,
                             suffix TEXT,
                             primary_group TEXT,
                             source TEXT,
                             updated_at INTEGER NOT NULL)""",
                    )
                    st.execute("CREATE TABLE IF NOT EXISTS player_prefs (uuid TEXT PRIMARY KEY NOT NULL, chat_color TEXT)")
                    st.execute(
                        """CREATE TABLE IF NOT EXISTS ignores (
                             owner TEXT NOT NULL,
                             target TEXT NOT NULL,
                             target_name TEXT,
                             created_at INTEGER NOT NULL,
                             PRIMARY KEY (owner, target))""",
                    )
                }
            } catch (t: Throwable) {
                runCatching { conn.close() }
                throw t
            }
            return SqliteContinuityStore(conn)
        }

        private fun connectWithoutDriverManager(file: Path): Connection =
            org.sqlite.JDBC().connect("jdbc:sqlite:${file.toAbsolutePath()}", Properties())
                ?: error("SQLite driver rejected the connection URL")
    }
}
