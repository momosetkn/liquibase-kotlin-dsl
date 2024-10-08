package momosetkn.liquibase.kotlin.change.custom.komapper

import org.komapper.jdbc.JdbcDatabase
import org.komapper.jdbc.JdbcDialect
import org.komapper.jdbc.JdbcDialects
import org.komapper.jdbc.spi.JdbcDialectFactory
import java.util.ServiceLoader

object LiquibaseKomapperJdbcConfig {
    var provideJdbcDatabase: (
        javaxSqlDataSource: javax.sql.DataSource,
        liquibaseDatabaseShortName: String
    ) -> JdbcDatabase = ::defaultProvideJdbcDatabase

    fun defaultProvideJdbcDatabase(
        javaxSqlDataSource: javax.sql.DataSource,
        liquibaseDatabaseShortName: String
    ): JdbcDatabase {
        return JdbcDatabase(javaxSqlDataSource, getJdbcDialect(liquibaseDatabaseShortName))
    }

    private fun getJdbcDialect(liquibaseDatabaseShortName: String): JdbcDialect {
        return runCatching {
            JdbcDialects.get(liquibaseDatabaseShortName)
        }.fold(
            onSuccess = { it },
            onFailure = {
                val loader = ServiceLoader.load(JdbcDialectFactory::class.java)
                val factory = loader.singleOrNull()
                checkNotNull(factory) {
                    @Suppress("MaxLineLength")
                    "I could not find the `org.komapper.jdbc.JdbcDialects` that should be used. Please set the `${this::class.qualifiedName}.provideJdbcDatabase`."
                }
                factory.create()
            }
        )
    }
}
