import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import liquibase.Liquibase
import momosetkn.liquibase.client.LiquibaseClient
import kotlin.reflect.KVisibility
import kotlin.reflect.full.functions

class LiquibaseClientSpec : FunSpec({
    context("LiquibaseClient") {
        test("public methods are identical to liquibase.Liquibase") {
            val liquibaseClientPublicMethods =
                LiquibaseClient::class.members.filter { it.visibility == KVisibility.PUBLIC }
            val liquibasePublicMethods =
                Liquibase::class.functions.filter { it.visibility == KVisibility.PUBLIC }

            val sortedLiquibaseClientPublicMethods = liquibaseClientPublicMethods
                .map { it.name }
                .distinct()
                .sorted()
                .filter { it !in sortedLiquibaseClientPublicMethodsExcludes }
            val sortedLiquibasePublicMethods = liquibasePublicMethods
                .map { it.name }
                .distinct()
                .sorted()
                .filter { it !in sortedLiquibasePublicMethodsExcludes }

            sortedLiquibaseClientPublicMethods.zip(sortedLiquibasePublicMethods).forEach { (m1, m2) ->
                m1 shouldBe m2
            }

            sortedLiquibaseClientPublicMethods.size shouldBe sortedLiquibasePublicMethods.size
        }
    }
})

private val sortedLiquibaseClientPublicMethodsExcludes = listOf(
    "showSummaryOutput",
    "showSummary",
    "changeLogFile",
    "database",
    "resourceAccessor",
    "changeLogParameters",
    "changeExecListener",
    "defaultChangeExecListener",
    "liquibase",
    "diffTypes",
    "diffChangeLog",
)

private val sortedLiquibasePublicMethodsExcludes = listOf(
    "getLog",
    "outputHeader",
    "getDatabase",
    "getChangeLogFile",
    "setShowSummaryOutput",
    "getShowSummary",
    "setShowSummary",
    "getResourceAccessor",
    "getChangeLogParameters",
    "setChangeExecListener",
    "getDefaultChangeExecListener",
)
