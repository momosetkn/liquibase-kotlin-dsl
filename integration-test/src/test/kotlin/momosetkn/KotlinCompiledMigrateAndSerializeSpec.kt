package momosetkn

import io.kotest.core.spec.style.FunSpec
import momosetkn.liquibase.changelogs.CompiledDatabaseChangelogAll
import momosetkn.liquibase.client.LiquibaseClient
import momosetkn.liquibase.client.LiquibaseDatabaseFactory
import momosetkn.liquibase.kotlin.serializer.KotlinCompiledChangeLogSerializer
import momosetkn.utils.Constants
import momosetkn.utils.DDLUtils.sql
import momosetkn.utils.DDLUtils.toMainDdl
import momosetkn.utils.ResourceUtils.getResourceAsString
import momosetkn.utils.maskChangeSetParams
import momosetkn.utils.shouldMatchWithoutLineBreaks
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Paths

class KotlinCompiledMigrateAndSerializeSpec : FunSpec({
    beforeSpec {
        KotlinCompiledChangeLogSerializer.sourceRootPath = Paths.get(Constants.TEST_RESOURCE_DIR)
    }
    beforeEach {
        SharedResources.targetDatabaseServer.startAndClear()
    }

    context("Serialize output file is relative path") {
        test("can migrate and serialize") {
            val server = SharedResources.targetDatabaseServer.startedServer
            val database = LiquibaseDatabaseFactory.create(
                driver = server.driver,
                url = server.jdbcUrl,
                username = server.username,
                password = server.password,
            )
            val liquibaseClient = LiquibaseClient(
                changeLogFile = PARSER_INPUT_CHANGELOG,
                database = database,
            )
            println("${this::class.simpleName} -- before update")
            liquibaseClient.update()
            println("${this::class.simpleName} -- before rollback")
            liquibaseClient.rollback(tagToRollBackTo = "started")
            println("${this::class.simpleName} -- before update(2)")
            liquibaseClient.update()
            val actualSerializedChangeLogFile =
                Paths.get(Constants.TEST_RESOURCE_DIR, SERIALIZER_ACTUAL_CHANGELOG)
            val f = actualSerializedChangeLogFile.toFile()
            if (f.exists()) f.delete()
            val generateLiquibaseClient = LiquibaseClient(
                changeLogFile = f.toString(),
                database = database,
            )
            println("${this::class.simpleName} -- before generateChangeLog")
            val baos = ByteArrayOutputStream()
            generateLiquibaseClient.generateChangeLog(
                outputStream = PrintStream(baos),
            )
            val generateResult = baos.toString()
            println(generateResult) // empty

            // check database
            val expectedDdl = getResourceAsString(PARSER_EXPECT_DDL)
            SharedResources.targetDatabaseServer.generateDdl().toMainDdl() shouldMatchWithoutLineBreaks sql(expectedDdl)

            // check serializer
            val actual = f.readText().maskChangeSetParams()
            val expect = getResourceAsString(SERIALIZER_EXPECT_CHANGELOG)
                .maskChangeSetParams()
            actual shouldMatchWithoutLineBreaks expect
        }
    }

    context("Serialize output file is absolute path") {
        test("can migrate and serialize") {
            val server = SharedResources.targetDatabaseServer.startedServer
            val database = LiquibaseDatabaseFactory.create(
                driver = server.driver,
                url = server.jdbcUrl,
                username = server.username,
                password = server.password,
            )
            val liquibaseClient = LiquibaseClient(
                changeLogFile = PARSER_INPUT_CHANGELOG,
                database = database,
            )
            println("${this::class.simpleName} -- before update")
            liquibaseClient.update()
            println("${this::class.simpleName} -- before rollback")
            liquibaseClient.rollback(tagToRollBackTo = "started")
            println("${this::class.simpleName} -- before update(2)")
            liquibaseClient.update()
            val actualSerializedChangeLogFile =
                Paths.get(Constants.TEST_RESOURCE_DIR, SERIALIZER_ACTUAL_CHANGELOG)
            val f = actualSerializedChangeLogFile.toFile()
            if (f.exists()) f.delete()
            val generateLiquibaseClient = LiquibaseClient(
                changeLogFile = f.absolutePath.toString(), // absolute
                database = database,
            )
            println("${this::class.simpleName} -- before generateChangeLog")
            val baos = ByteArrayOutputStream()
            generateLiquibaseClient.generateChangeLog(
                outputStream = PrintStream(baos),
            )
            val generateResult = baos.toString()
            println(generateResult) // empty

            // check database
            val expectedDdl = getResourceAsString(PARSER_EXPECT_DDL)
            SharedResources.targetDatabaseServer.generateDdl().toMainDdl() shouldMatchWithoutLineBreaks sql(expectedDdl)

            // check serializer
            val actual = f.readText().maskChangeSetParams()
            val expect = getResourceAsString(SERIALIZER_EXPECT_CHANGELOG)
                .maskChangeSetParams()
            actual shouldMatchWithoutLineBreaks expect
        }
    }
}) {
    companion object {
        private val PARSER_INPUT_CHANGELOG = CompiledDatabaseChangelogAll::class.qualifiedName!!
        private const val SERIALIZER_ACTUAL_CHANGELOG =
            "KotlinCompiledMigrateAndSerializeSpec/serializer_actual/ChangeLog0.kt"

        // To avoid auto-formatting, do not add a file extension.
        private const val SERIALIZER_EXPECT_CHANGELOG =
            "KotlinCompiledMigrateAndSerializeSpec/serializer_expect/ChangeLog0_kt"
        private const val PARSER_EXPECT_DDL = "KotlinCompiledMigrateAndSerializeSpec/parser_expect/db.ddl-0.sql"
    }
}
