package momosetkn.utils

import org.slf4j.LoggerFactory
import java.net.ServerSocket
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlin.system.measureTimeMillis

class DatabaseServer {
    private val log = LoggerFactory.getLogger(this.javaClass.name)

    private var config: DatabaseConfig? = null
    private val gradlewUtils = GradlewUtils()

    // Automatically find a free port
    private val port = ServerSocket(0).use { socket ->
        socket.localPort
    }

    // For a clean run every time,
    private val databaseName = "liquibase_kotlin_test_$port"

    val startedServer
        get() =
            requireNotNull(config) {
                "${DatabaseServer::class.qualifiedName} is not started"
            }

    @Synchronized
    fun startAndClear() {
        if (this.config == null) {
            this.config = createServer()
        }
        getConnection(startedServer).use {
            checkDb(it)
            dropDb(it)
        }
    }

    private fun createServer(): DatabaseConfig {
        val dir = databaseDirectory()
        val databaseConfig = DatabaseConfig(
            driver = "org.h2.Driver",
            jdbcUrl = "jdbc:h2:tcp://127.0.0.1:$port/$dir/$databaseName",
            username = "sa",
            password = "",
        )
        Class.forName(databaseConfig.driver)
        val launchTime =
            measureTimeMillis {
                lunchH2Server(port)
                recursiveCheckDbLoop(databaseConfig)
            }
        log.info("database started in $launchTime ms")
        return databaseConfig
    }

    @Synchronized
    fun clear() {
        this.config?.also { server ->
            getConnection(server).use {
                dropDb(it)
            }
        }
    }

    private fun lunchH2Server(port: Int): () -> Unit {
        val command = listOfNotNull(
            gradlewUtils.getDefaultShell(),
            "./gradlew",
            "test-utils:startH2Server",
            "-Pport=$port"
        )
        val destroy = gradlewUtils.executeCommand(command)
        Thread.sleep(LAUNCH_INITIAL_WAIT)

        return destroy
    }

    fun generateDdl(): String {
        return getConnection(startedServer).use {
            val stmt = it.createStatement()
            val rs = stmt.executeQuery("SCRIPT NODATA")

            val schema = StringBuilder()

            while (rs.next()) {
                schema.append(rs.getString(1)).append("\n")
            }

            schema.toString()
        }
    }

    fun getConnection(config: DatabaseConfig) =
        DriverManager.getConnection(config.jdbcUrl, config.username, config.password)

    private fun databaseDirectory() =
        Path.of(System.getProperty("user.dir")).resolveSibling("build/tmp/test")

    private tailrec fun recursiveCheckDbLoop(container: DatabaseConfig) {
        val isSuccess = runCatching {
            getConnection(container).use {
                checkDb(it)
            }
        }.isSuccess
        if (isSuccess) {
            return
        } else {
            Thread.sleep(CONFIRM_ESTABLISHED_INTERVAL)
            recursiveCheckDbLoop(container)
        }
    }

    private fun checkDb(conn: Connection) {
        val stmt = conn.createStatement()
        val resultSet = stmt.executeQuery("select 1")
        resultSet.next()
        check(resultSet.getInt(1) == 1) {
            "database connection failed"
        }
    }

    private fun dropDb(conn: Connection) {
        val stmt = conn.createStatement()
        stmt.execute("DROP ALL OBJECTS")
    }

    companion object {
        private const val LAUNCH_INITIAL_WAIT = 200L
        private const val CONFIRM_ESTABLISHED_INTERVAL = 50L
    }
}

data class DatabaseConfig(
    val driver: String,
    val jdbcUrl: String,
    val username: String,
    val password: String,
)
