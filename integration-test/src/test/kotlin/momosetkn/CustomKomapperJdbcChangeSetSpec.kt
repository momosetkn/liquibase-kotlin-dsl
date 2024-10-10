package momosetkn

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import komapper.Company2
import komapper.company2
import liquibase.exception.CommandExecutionException
import momosetkn.liquibase.command.client.LiquibaseCommandClient
import momosetkn.liquibase.kotlin.change.custom.komapper.customKomapperJdbcChange
import momosetkn.utils.DDLUtils.sql
import momosetkn.utils.DDLUtils.toMainDdl
import momosetkn.utils.DatabaseKomapperExtensions.komapperDb
import momosetkn.utils.InterchangeableChangeLog
import momosetkn.utils.set
import momosetkn.utils.shouldMatchWithoutLineBreaks
import org.komapper.core.dsl.Meta
import org.komapper.core.dsl.QueryDsl
import org.komapper.core.dsl.query.single
import java.util.UUID

class CustomKomapperJdbcChangeSetSpec : FunSpec({
    beforeEach {
        SharedResources.targetDatabaseServer.startAndClear()
    }
    val client = LiquibaseCommandClient {
        global {
            general {
                showBanner = false
            }
        }
    }

    context("forwardOnly") {
        fun subject() {
            val server = SharedResources.targetDatabaseServer.startedServer
            client.update(
                driver = server.driver,
                url = server.jdbcUrl,
                username = server.username,
                password = server.password,
                changelogFile = InterchangeableChangeLog::class.qualifiedName!!,
            )
        }
        val c = Meta.company2
        InterchangeableChangeLog.set {
            changeSet(author = "momose", id = "100-10") {
                customKomapperJdbcChange(
                    execute = { db ->
                        val query = QueryDsl.executeScript(
                            """
                                CREATE TABLE company2 (
                                    id uuid primary key,
                                    name character varying(255)
                                );
                            """.trimIndent()
                        )
                        db.runQuery(query)
                    },
                    rollback = { db ->
                        val query = QueryDsl.executeScript("DROP TABLE company2")
                        db.runQuery(query)
                    },
                )
            }
            changeSet(author = "momose", id = "100-20") {
                customKomapperJdbcChange(
                    execute = { db ->
                        val query = QueryDsl.insert(c).single(
                            Company2(
                                id = UUID.randomUUID(),
                                name = "CreatedByKomapper_name",
                            )
                        )
                        db.runQuery(query)
                    },
                )
            }
        }
        test("can migrate") {
            subject()
            SharedResources.targetDatabaseServer.generateDdl().toMainDdl() shouldMatchWithoutLineBreaks sql(
                """
                    CREATE CACHED TABLE "PUBLIC"."COMPANY2"(
                        "ID" UUID NOT NULL,
                        "NAME" CHARACTER VARYING(255)
                    );
                    ALTER TABLE "PUBLIC"."COMPANY2" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_A" PRIMARY KEY("ID");
                """.trimIndent()
            )
            val db = SharedResources.targetDatabaseServer.komapperDb()
            val query = QueryDsl.from(c).single()
            val item = db.runQuery(query)
            item.name shouldBe "CreatedByKomapper_name"
        }
    }

    context("forward and rollback") {
        val startedTag = "started"
        fun subject() {
            val server = SharedResources.targetDatabaseServer.startedServer
            client.update(
                driver = server.driver,
                url = server.jdbcUrl,
                username = server.username,
                password = server.password,
                changelogFile = InterchangeableChangeLog::class.qualifiedName!!,
            )
            client.rollback(
                driver = server.driver,
                url = server.jdbcUrl,
                username = server.username,
                password = server.password,
                changelogFile = InterchangeableChangeLog::class.qualifiedName!!,
                tag = startedTag,
            )
        }
        val c = Meta.company2
        context("rollback arg is given") {
            InterchangeableChangeLog.set {
                changeSet(author = "momose", id = "0-10") {
                    tagDatabase(startedTag)
                }
                changeSet(author = "momose", id = "100-10") {
                    customKomapperJdbcChange(
                        execute = { db ->
                            val query = QueryDsl.executeScript(
                                """
                                    CREATE TABLE company2 (
                                        id uuid primary key,
                                        name character varying(255)
                                    );
                                """.trimIndent()
                            )
                            db.runQuery(query)
                        },
                        rollback = { db ->
                            val query = QueryDsl.executeScript("DROP TABLE company2")
                            db.runQuery(query)
                        },
                    )
                }
                changeSet(author = "momose", id = "100-20") {
                    customKomapperJdbcChange(
                        execute = { db ->
                            val query = QueryDsl.insert(c).single(
                                Company2(
                                    id = UUID.randomUUID(),
                                    name = "CreatedByKomapper_name",
                                )
                            )
                            db.runQuery(query)
                        },
                        rollback = { db ->
                            val query = QueryDsl.delete(c).all()
                            db.runQuery(query)
                        }
                    )
                }
            }
            test("can rollback") {
                subject()
                SharedResources.targetDatabaseServer.generateDdl().toMainDdl() shouldMatchWithoutLineBreaks sql("")
            }
        }
        context("rollback arg is none") {
            InterchangeableChangeLog.set {
                changeSet(author = "momose", id = "0-10") {
                    tagDatabase(startedTag)
                }
                changeSet(author = "momose", id = "100-10") {
                    customKomapperJdbcChange(
                        execute = { db ->
                            val query = QueryDsl.executeScript(
                                """
                                    CREATE TABLE company2 (
                                        id uuid primary key,
                                        name character varying(255)
                                    );
                                """.trimIndent()
                            )
                            db.runQuery(query)
                        },
                        rollback = { db ->
                            val query = QueryDsl.executeScript("DROP TABLE company2")
                            db.runQuery(query)
                        },
                    )
                }
                changeSet(author = "momose", id = "100-20") {
                    customKomapperJdbcChange(
                        execute = { db ->
                            val query = QueryDsl.insert(c).single(
                                Company2(
                                    id = UUID.randomUUID(),
                                    name = "CreatedByKomapper_name",
                                )
                            )
                            db.runQuery(query)
                        }
                    )
                }
            }
            test("throw error") {
                shouldThrow<CommandExecutionException> {
                    subject()
                }
            }
        }
    }
})
