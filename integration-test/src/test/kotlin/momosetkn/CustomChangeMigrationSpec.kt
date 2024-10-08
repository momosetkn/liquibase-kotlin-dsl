package momosetkn

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import momosetkn.liquibase.changelogs.customechannge.ExecuteCountTaskCustomChange
import momosetkn.liquibase.command.client.LiquibaseCommandClient
import momosetkn.liquibase.kotlin.parser.KotlinCompiledDatabaseChangeLog
import momosetkn.liquibase.kotlin.serializer.KotlinCompiledChangeLogSerializer
import momosetkn.utils.Constants
import momosetkn.utils.DatabaseServer
import java.nio.file.Paths

class CustomChangeMigrationSpec : FunSpec({
    beforeSpec {
        KotlinCompiledChangeLogSerializer.sourceRootPath = Paths.get(Constants.TEST_RESOURCE_DIR)
    }
    beforeEach {
        DatabaseServer.startAndClear()
    }

    context("Migrate and serialize") {
        test("can migrate") {
            val client = LiquibaseCommandClient {
                global {
                    general {
                        showBanner = false
                    }
                }
            }
            val container = DatabaseServer.startedContainer
            client.update(
                driver = container.driver,
                url = container.jdbcUrl,
                username = container.username,
                password = container.password,
                changelogFile = CustomChangeMigrationSpecChangelog1::class.qualifiedName!!,
            )

            ExecuteCountTaskCustomChange.executeCount shouldBe 1
        }
    }
})

class CustomChangeMigrationSpecChangelog1 : KotlinCompiledDatabaseChangeLog({
    changeSet(author = "momose (generated)", id = "100-10") {
        customChange(`class` = ExecuteCountTaskCustomChange::class)
    }
})
