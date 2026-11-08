package finance.db

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

fun Connection.execute(sql: String, vararg params: Any?): Int =
    prepareStatement(sql).use { statement ->
        statement.bind(params)
        statement.executeUpdate()
    }

fun Connection.insert(sql: String, vararg params: Any?): Long {
    execute(sql, *params)
    return createStatement().use { statement ->
        statement.executeQuery("SELECT last_insert_rowid()").use { rs ->
            rs.next()
            rs.getLong(1)
        }
    }
}

fun <T> Connection.query(sql: String, vararg params: Any?, mapper: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { statement ->
        statement.bind(params)
        statement.executeQuery().use { rs ->
            buildList { while (rs.next()) add(mapper(rs)) }
        }
    }

private fun PreparedStatement.bind(params: Array<out Any?>) {
    params.forEachIndexed { index, param ->
        if (param == null) setNull(index + 1, Types.NULL) else setObject(index + 1, param)
    }
}
