package momosetkn

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import komapper.databasechangelog
import liquibase.Scope
import liquibase.changelog.ChangeLogParameters
import momosetkn.liquibase.client.LiquibaseClient
import momosetkn.liquibase.client.LiquibaseDatabaseFactory
import momosetkn.liquibase.kotlin.parser.KotlinCompiledLiquibaseChangeLogParser
import momosetkn.liquibase.kotlin.serializer.KotlinCompiledChangeLogSerializer
import momosetkn.utils.Constants
import momosetkn.utils.DatabaseConfig
import momosetkn.utils.DatabaseKomapperExtensions.komapperDb
import momosetkn.utils.InterchangeableChangeLog
import momosetkn.utils.maskChangeSetParams
import momosetkn.utils.set
import momosetkn.utils.toVersion
import org.komapper.core.dsl.Meta
import org.komapper.core.dsl.QueryDsl
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.io.StringWriter
import java.nio.file.Paths

@OptIn(ExperimentalKotest::class)
class LiquibaseClientSpec : FunSpec({
    beforeSpec {
        KotlinCompiledChangeLogSerializer.sourceRootPath = Paths.get(Constants.TEST_RESOURCE_DIR)
    }
    beforeEach {
        SharedResources.targetDatabaseServer.startAndClear()
    }

    fun liquibaseClient(
        databaseConfig: DatabaseConfig = SharedResources.targetDatabaseServer.startedServer,
        changeLogFile: String? = null
    ): LiquibaseClient {
        val database = LiquibaseDatabaseFactory.create(
            driver = databaseConfig.driver,
            url = databaseConfig.jdbcUrl,
            username = databaseConfig.username,
            password = databaseConfig.password,
        )
        return LiquibaseClient(
            changeLogFile = changeLogFile ?: InterchangeableChangeLog::class.qualifiedName!!,
            database = database,
        )
    }

    context("update(tag)") {
        InterchangeableChangeLog.set {
            changeSet(author = "user", id = "100") {
                createTable(tableName = "company") {
                    column(name = "id", type = "UUID") {
                        constraints(nullable = false, primaryKey = true)
                    }
                    column(name = "name", type = "VARCHAR(256)")
                }
            }
            changeSet(author = "user", id = "200") {
                tagDatabase(tag = "finish")
            }
        }
        test("can migrate") {
            liquibaseClient().update(tag = "finish")

            val db = SharedResources.targetDatabaseServer.komapperDb()
            val d = Meta.databasechangelog
            val result = db.runQuery {
                QueryDsl.from(d)
            }
            result.size shouldBe 2
        }
    }

    context("generateChangeLog") {
        val outputFile = File("build/tmp/test/${LiquibaseClientSpec::class.simpleName}_generateChangeLog.kt")
        InterchangeableChangeLog.set {
            changeSet(author = "user", id = "100") {
                createTable(tableName = "company") {
                    column(name = "id", type = "UUID") {
                        constraints(nullable = false, primaryKey = true)
                    }
                    column(name = "name", type = "VARCHAR(256)")
                }
            }
            changeSet(author = "user", id = "200") {
                tagDatabase(tag = "finish")
            }
        }
        beforeEach {
            if (outputFile.exists()) {
                outputFile.delete()
            }
        }
        test("can generated") {
            val databaseChangeLog = KotlinCompiledLiquibaseChangeLogParser().parse(
                physicalChangeLogLocation = InterchangeableChangeLog::class.qualifiedName!!,
                changeLogParameters = ChangeLogParameters(),
                resourceAccessor = Scope.getCurrentScope().resourceAccessor,
            )
            databaseChangeLog.physicalFilePath = outputFile.toString()

            val baos = ByteArrayOutputStream()
            liquibaseClient().update()
            liquibaseClient(
                changeLogFile = outputFile.toString(),
            ).generateChangeLog(
                outputStream = PrintStream(baos),
            )
            val generateResult = baos.toString()
            println(generateResult) // empty

            val actual = outputFile.readText()

            actual.maskChangeSetParams() shouldBe """
                package build.tmp.test

                import momosetkn.liquibase.kotlin.parser.KotlinCompiledDatabaseChangeLog

                class LiquibaseClientSpec_generateChangeLog : KotlinCompiledDatabaseChangeLog({
                    changeSet(author = "**********", id = "*************-1") {
                        createTable(tableName = "COMPANY") {
                            column(name = "ID", type = "UUID") {
                                constraints(nullable = false, primaryKey = true, primaryKeyName = "PK_COMPANY")
                            }
                            column(name = "NAME", type = "VARCHAR(256)")
                        }
                    }

                })

            """.trimIndent().maskChangeSetParams()
        }
    }
    context("diffChangeLog") {
        val outputFile = File("build/tmp/test/${LiquibaseClientSpec::class.simpleName}_diffChangeLog.kt")
        beforeEach {
            SharedResources.referenceDatabaseServer.startAndClear()
            InterchangeableChangeLog.set {
                changeSet(author = "user", id = "100") {
                    createTable(tableName = "company") {
                        column(name = "id", type = "UUID") {
                            constraints(nullable = false, primaryKey = true)
                        }
                        column(name = "name", type = "VARCHAR(256)")
                    }
                }
            }
            liquibaseClient(
                databaseConfig = SharedResources.targetDatabaseServer.startedServer,
            ).update()
            InterchangeableChangeLog.set {
                changeSet(author = "user", id = "100") {
                    createTable(tableName = "company") {
                        column(name = "id", type = "UUID") {
                            constraints(nullable = false, primaryKey = true)
                        }
                        column(name = "name", type = "VARCHAR(256)")
                    }
                }
                changeSet(author = "user", id = "200") {
                    createTable(tableName = "company2") {
                        column(name = "id", type = "UUID") {
                            constraints(nullable = false, primaryKey = true)
                        }
                        column(name = "name", type = "VARCHAR(256)")
                    }
                }
                changeSet(author = "user", id = "300") {
                    createIndex(associatedWith = "", indexName = "name_idx", tableName = "company2") {
                        column(name = "name")
                    }
                }
            }
            liquibaseClient(
                databaseConfig = SharedResources.referenceDatabaseServer.startedServer,
            ).update()
            if (outputFile.exists()) {
                outputFile.delete()
            }
        }
        test("can generated diff changeLog") {
            val referenceDatabaseConfig = SharedResources.referenceDatabaseServer.startedServer
            val referenceDatabase = LiquibaseDatabaseFactory.create(
                driver = referenceDatabaseConfig.driver,
                url = referenceDatabaseConfig.jdbcUrl,
                username = referenceDatabaseConfig.username,
                password = referenceDatabaseConfig.password,
            )
            val baos = ByteArrayOutputStream()
            liquibaseClient(
                changeLogFile = outputFile.toString(),
            ).diffChangeLog(
                referenceDatabase = referenceDatabase,
                outputStream = PrintStream(baos),
            )
            val generateResult = baos.toString()
            println(generateResult) // empty

            val actual = outputFile.readText()

            actual.maskChangeSetParams() shouldBe """
                package build.tmp.test

                import momosetkn.liquibase.kotlin.parser.KotlinCompiledDatabaseChangeLog

                class LiquibaseClientSpec_diffChangeLog : KotlinCompiledDatabaseChangeLog({
                    changeSet(author = "**********", id = "*************-1") {
                        createTable(tableName = "COMPANY2") {
                            column(name = "ID", type = "UUID") {
                                constraints(nullable = false, primaryKey = true, primaryKeyName = "PK_COMPANY2")
                            }
                            column(name = "NAME", type = "VARCHAR(256)")
                        }
                    }

                    changeSet(author = "**********", id = "*************-2") {
                        createIndex(associatedWith = "", indexName = "NAME_IDX", tableName = "COMPANY2") {
                            column(name = "NAME")
                        }
                    }

                })

            """.trimIndent().maskChangeSetParams()
        }
    }

    context("updateCountSql").config(
        enabled = System.getProperty("liquibaseVersion", "999.9.9").toVersion() >= "4.29.2".toVersion()
    ) {
        InterchangeableChangeLog.set {
            changeSet(author = "user", id = "100") {
                createTable(tableName = "company") {
                    column(name = "id", type = "UUID") {
                        constraints(nullable = false, primaryKey = true)
                    }
                    column(name = "name", type = "VARCHAR(256)")
                }
            }
        }
        test("can output sql") {
            val sw = StringWriter()
            liquibaseClient().updateCountSql(1, output = sw)
            println(sw.toString())
            sw.toString() shouldStartWith "-- Create Database Lock Table"
        }
    }

    context("updateToTagSql").config(
        enabled = System.getProperty("liquibaseVersion", "999.9.9").toVersion() >= "4.29.2".toVersion()
    ) {
        InterchangeableChangeLog.set {
            changeSet(author = "user", id = "100") {
                createTable(tableName = "company") {
                    column(name = "id", type = "UUID") {
                        constraints(nullable = false, primaryKey = true)
                    }
                    column(name = "name", type = "VARCHAR(256)")
                }
            }
            changeSet(author = "user", id = "200") {
                tagDatabase(tag = "finish")
            }
        }
        test("can output sql") {
            val sw = StringWriter()
            liquibaseClient().use {
                it.updateToTagSql("finish", output = sw)
            }
            SharedResources.targetDatabaseServer.generateDdl()
            println(sw.toString())
            sw.toString() shouldStartWith "-- Create Database Lock Table"
        }
    }

    context("diff") {
        context("not specify targetDatabase") {
            beforeEach {
                SharedResources.referenceDatabaseServer.startAndClear()
                InterchangeableChangeLog.set {
                    changeSet(author = "user", id = "100") {
                        createTable(tableName = "company") {
                            column(name = "id", type = "UUID") {
                                constraints(nullable = false, primaryKey = true)
                            }
                            column(name = "name", type = "VARCHAR(256)")
                        }
                    }
                }
                liquibaseClient(
                    databaseConfig = SharedResources.targetDatabaseServer.startedServer,
                ).update()
                InterchangeableChangeLog.set {
                    changeSet(author = "user", id = "100") {
                        createTable(tableName = "company") {
                            column(name = "id", type = "UUID") {
                                constraints(nullable = false, primaryKey = true)
                            }
                            column(name = "name", type = "VARCHAR(256)")
                        }
                    }
                    changeSet(author = "user", id = "200") {
                        createTable(tableName = "company2") {
                            column(name = "id", type = "UUID") {
                                constraints(nullable = false, primaryKey = true)
                            }
                            column(name = "name", type = "VARCHAR(256)")
                        }
                    }
                }
                liquibaseClient(
                    databaseConfig = SharedResources.referenceDatabaseServer.startedServer,
                ).update()
            }
            test("can output diffResult") {
                val server = SharedResources.referenceDatabaseServer.startedServer
                val referenceDatabase = LiquibaseDatabaseFactory.create(
                    driver = server.driver,
                    url = server.jdbcUrl,
                    username = server.username,
                    password = server.password,
                )
                val diffResult = liquibaseClient().use {
                    it.diff(
                        referenceDatabase = referenceDatabase
                    )
                }
                diffResult.missingObjects.size shouldBe 5
                diffResult.missingObjects.map { it.name }.sorted() shouldBe listOf(
                    "COMPANY2",
                    "ID",
                    "NAME",
                    "PK_COMPANY2",
                    "PRIMARY_KEY_A",
                )
            }
        }
        context("specify targetDatabase") {
            beforeEach {
                SharedResources.referenceDatabaseServer.startAndClear()
                InterchangeableChangeLog.set {
                    changeSet(author = "user", id = "100") {
                        createTable(tableName = "company") {
                            column(name = "id", type = "UUID") {
                                constraints(nullable = false, primaryKey = true)
                            }
                            column(name = "name", type = "VARCHAR(256)")
                        }
                    }
                }
                liquibaseClient(
                    databaseConfig = SharedResources.targetDatabaseServer.startedServer,
                ).update()
                InterchangeableChangeLog.set {
                    changeSet(author = "user", id = "100") {
                        createTable(tableName = "company") {
                            column(name = "id", type = "UUID") {
                                constraints(nullable = false, primaryKey = true)
                            }
                            column(name = "name", type = "VARCHAR(256)")
                        }
                    }
                    changeSet(author = "user", id = "200") {
                        createTable(tableName = "company2") {
                            column(name = "id", type = "UUID") {
                                constraints(nullable = false, primaryKey = true)
                            }
                            column(name = "name", type = "VARCHAR(256)")
                        }
                    }
                }
                liquibaseClient(
                    databaseConfig = SharedResources.referenceDatabaseServer.startedServer,
                ).update()
            }
            test("can output diffResult") {
                val targetDatabaseConfig = SharedResources.targetDatabaseServer.startedServer
                val targetDatabase = LiquibaseDatabaseFactory.create(
                    driver = targetDatabaseConfig.driver,
                    url = targetDatabaseConfig.jdbcUrl,
                    username = targetDatabaseConfig.username,
                    password = targetDatabaseConfig.password,
                )
                val referenceDatabaseConfig = SharedResources.referenceDatabaseServer.startedServer
                val referenceDatabase = LiquibaseDatabaseFactory.create(
                    driver = referenceDatabaseConfig.driver,
                    url = referenceDatabaseConfig.jdbcUrl,
                    username = referenceDatabaseConfig.username,
                    password = referenceDatabaseConfig.password,
                )
                val diffResult = liquibaseClient().use {
                    it.diff(
                        targetDatabase = targetDatabase,
                        referenceDatabase = referenceDatabase,
                    )
                }
                diffResult.missingObjects.size shouldBe 5
                diffResult.missingObjects.map { it.name }.sorted() shouldBe listOf(
                    "COMPANY2",
                    "ID",
                    "NAME",
                    "PK_COMPANY2",
                    "PRIMARY_KEY_A",
                )
            }
        }
    }
})
