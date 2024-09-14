package momosetkn

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import momosetkn.ResourceUtils.getResourceAsString
import momosetkn.liquibase.changelogs.TypesafeDatabaseChangelogAll
import momosetkn.liquibase.client.LiquibaseClient
import momosetkn.liquibase.kotlin.serializer.KotlinTypesafeChangeLogSerializer
import java.nio.file.Paths

class KotlinTypesafeMigrateAndSerializeSpec : FunSpec({
    beforeSpec {
        Database.start()
        KotlinTypesafeChangeLogSerializer.sourceRootDirectory = Paths.get(RESOURCE_DIR)
    }
    afterSpec {
        Database.stop()
    }

    context("Migrate and serialize") {
        test("can migrate") {
            val container = Database.startedContainer
            LiquibaseClient.update(
                driver = "org.postgresql.Driver",
                url = container.jdbcUrl,
                username = container.username,
                password = container.password,
                changelogFile = PARSER_INPUT_CHANGELOG,
            )
            LiquibaseClient.rollback(
                driver = "org.postgresql.Driver",
                url = container.jdbcUrl,
                username = container.username,
                password = container.password,
                changelogFile = PARSER_INPUT_CHANGELOG,
                tag = "started",
            )
            LiquibaseClient.update(
                driver = "org.postgresql.Driver",
                url = container.jdbcUrl,
                username = container.username,
                password = container.password,
                changelogFile = PARSER_INPUT_CHANGELOG,
            )
            val actualSerializedChangeLogFile =
                Paths.get(RESOURCE_DIR, SERIALIZER_ACTUAL_CHANGELOG)
            val f = actualSerializedChangeLogFile.toFile()
            if (f.exists()) f.delete()
            LiquibaseClient.generateChangelog(
                driver = "org.postgresql.Driver",
                url = container.jdbcUrl,
                username = container.username,
                password = container.password,
                changelogFile = actualSerializedChangeLogFile.toString(),
            )
            val ddl = Database.generateDdl()

            // check database
            val expectedDdl = getResourceAsString(PARSER_EXPECT_DDL)
            ddl shouldBe expectedDdl

            // check serializer
            val actual = getFileAsString(SERIALIZER_ACTUAL_CHANGELOG)
                .maskingChangeSet()
            val expect = getResourceAsString(SERIALIZER_EXPECT_CHANGELOG)
                .maskingChangeSet()
            actual shouldBe expect
        }
    }
})

private fun getFileAsString(path: String) =
    Paths.get(RESOURCE_DIR, path).toFile().readText()

private val changeSetRegex = Regex("""changeSet\(author = "(.+)", id = "(\d+)-(\d)"\) \{""")

private fun String.maskingChangeSet() =
    this.replace(changeSetRegex, "changeSet(author = \"**********\", id = \"*************-\$3\") {")

private val PARSER_INPUT_CHANGELOG = TypesafeDatabaseChangelogAll::class.qualifiedName!!
private const val SERIALIZER_ACTUAL_CHANGELOG = "KotlinTypesafeMigrateAndSerializeSpec/serializer_actual/ChangeLog0.kt"

// To avoid auto-formatting, do not add a file extension.
private const val SERIALIZER_EXPECT_CHANGELOG = "KotlinTypesafeMigrateAndSerializeSpec/serializer_expect/ChangeLog0_kt"
private const val PARSER_EXPECT_DDL = "KotlinTypesafeMigrateAndSerializeSpec/parser_expect/db.ddl-0.sql"
private const val RESOURCE_DIR = "src/main/resources"
