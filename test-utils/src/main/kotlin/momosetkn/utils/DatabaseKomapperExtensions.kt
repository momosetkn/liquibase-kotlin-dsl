package momosetkn.utils

import org.komapper.jdbc.JdbcDatabase

object DatabaseKomapperExtensions {
    fun DatabaseServer.komapperDb() = JdbcDatabase(
        url = this.startedServer.jdbcUrl,
        user = this.startedServer.username,
        password = this.startedServer.password,
    )
}
