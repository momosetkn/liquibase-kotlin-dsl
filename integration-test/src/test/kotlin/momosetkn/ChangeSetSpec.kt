package momosetkn

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import komapper.databasechangelog
import liquibase.exception.CommandExecutionException
import momosetkn.liquibase.command.client.LiquibaseCommandClient
import momosetkn.liquibase.kotlin.dsl.ChangeSetDsl
import momosetkn.utils.DDLUtils.sql
import momosetkn.utils.DDLUtils.toMainDdl
import momosetkn.utils.DatabaseKomapperExtensions.komapperDb
import momosetkn.utils.InterchangeableChangeLog
import momosetkn.utils.set
import momosetkn.utils.shouldMatchWithoutLineBreaks
import org.komapper.core.dsl.Meta
import org.komapper.core.dsl.QueryDsl
import org.komapper.core.dsl.query.single

class ChangeSetSpec : FunSpec({
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

    context("comment") {
        InterchangeableChangeLog.set {
            changeSet(author = "user", id = "100") {
                comment("comment_123") // precedence this
                comment("comment_456")
            }
        }
        test("can migrate") {
            subject()
            val db = SharedResources.targetDatabaseServer.komapperDb()
            val d = Meta.databasechangelog
            val result = db.runQuery {
                QueryDsl.from(d).single()
            }
            result.comments shouldBe "comment_123"
        }
    }
    context("preConditions") {
        fun databaseUsername() = SharedResources.targetDatabaseServer.startedServer.username
        context("postgresql and <currentUser>") {
            InterchangeableChangeLog.set {
                changeSet(author = "user", id = "100") {
                    preConditions(
                        onFail = "MARK_RAN",
                    ) {
                        dbms(type = "h2")
                        runningAs(username = databaseUsername())
                    }
                    createCompanyTable()
                }
            }
            test("can migrate") {
                subject()
                SharedResources.targetDatabaseServer.generateDdl().toMainDdl() shouldMatchWithoutLineBreaks sql(
                    """
                    CREATE CACHED TABLE "PUBLIC"."COMPANY"(
                        "ID" UUID NOT NULL,
                        "NAME" CHARACTER VARYING(256)
                    );
                    ALTER TABLE "PUBLIC"."COMPANY" ADD CONSTRAINT "PUBLIC"."PK_COMPANY" PRIMARY KEY("ID");
                    """.trimIndent()
                )
                val db = SharedResources.targetDatabaseServer.komapperDb()
                val d = Meta.databasechangelog
                val result = db.runQuery {
                    QueryDsl.from(d).single()
                }
                result.id shouldBe "100"
            }
        }
        context("mysql and root") {
            InterchangeableChangeLog.set {
                changeSet(author = "user", id = "100") {
                    preConditions(
                        onFail = "MARK_RAN",
                    ) {
                        dbms(type = "mysql")
                        runningAs(username = "root")
                    }
                    createCompanyTable()
                }
            }
            test("can migrate") {
                subject()
                SharedResources.targetDatabaseServer.generateDdl().toMainDdl() shouldMatchWithoutLineBreaks sql("")
                val db = SharedResources.targetDatabaseServer.komapperDb()
                val d = Meta.databasechangelog
                val result = db.runQuery {
                    QueryDsl.from(d).single()
                }
                result.exectype shouldBe "MARK_RAN"
            }
        }
        context("(mysql and root) or (postgresql and currentUser)") {
            InterchangeableChangeLog.set {
                changeSet(author = "user", id = "100") {
                    preConditions(
                        onFail = "MARK_RAN",
                    ) {
                        or {
                            and {
                                dbms(type = "mysql")
                                runningAs(username = "root")
                            }
                            and {
                                dbms(type = "h2")
                                runningAs(username = databaseUsername())
                            }
                        }
                    }
                    createCompanyTable()
                }
            }
            test("can migrate") {
                subject()
                SharedResources.targetDatabaseServer.generateDdl().toMainDdl() shouldMatchWithoutLineBreaks sql(
                    """
                    CREATE CACHED TABLE "PUBLIC"."COMPANY"(
                        "ID" UUID NOT NULL,
                        "NAME" CHARACTER VARYING(256)
                    );
                    ALTER TABLE "PUBLIC"."COMPANY" ADD CONSTRAINT "PUBLIC"."PK_COMPANY" PRIMARY KEY("ID");
                    """.trimIndent()
                )
                val db = SharedResources.targetDatabaseServer.komapperDb()
                val d = Meta.databasechangelog
                val result = db.runQuery {
                    QueryDsl.from(d).single()
                }
                result.id shouldBe "100"
            }
        }
    }

    context("executeCommand") {
        InterchangeableChangeLog.set {
            changeSet(author = "user", id = "100") {
                executeCommand(
                    executable = "java",
                    timeout = "10s"
                ) {
                    arg("--version")
                }
            }
        }
        test("can migrate") {
            subject()
            // confirm output docker help
        }
    }
    context("output") {
        InterchangeableChangeLog.set {
            changeSet(author = "user", id = "100") {
                output(
                    message = "output_message",
                )
            }
        }
        test("can migrate") {
            subject()
            // confirm output "output_message"
        }
    }

    context("sql") {
        InterchangeableChangeLog.set {
            changeSet(author = "user", id = "100") {
                sql(
                    """
                    create table table_a (
                        id int primary key,
                        name varchar(255)
                    )
                    """.trimIndent()
                )
            }
        }
        test("can migrate") {
            subject()
            SharedResources.targetDatabaseServer.generateDdl().toMainDdl() shouldMatchWithoutLineBreaks sql(
                """
                    CREATE CACHED TABLE "PUBLIC"."TABLE_A"(
                        "ID" INTEGER NOT NULL,
                        "NAME" CHARACTER VARYING(255)
                    );
                    ALTER TABLE "PUBLIC"."TABLE_A" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_C" PRIMARY KEY("ID");
                """.trimIndent()
            )
        }
    }
    context("sql(original)") {
        InterchangeableChangeLog.set {
            changeSet(author = "user", id = "100") {
                sql {
                    """
                    create table table_a (
                        id int primary key,
                        name varchar(255)
                    )
                    """.trimIndent()
                }
            }
        }
        test("can migrate") {
            subject()
            SharedResources.targetDatabaseServer.generateDdl().toMainDdl() shouldMatchWithoutLineBreaks sql(
                """
                    CREATE CACHED TABLE "PUBLIC"."TABLE_A"(
                        "ID" INTEGER NOT NULL,
                        "NAME" CHARACTER VARYING(255)
                    );
                    ALTER TABLE "PUBLIC"."TABLE_A" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_C" PRIMARY KEY("ID");
                """.trimIndent()
            )
        }
    }
    context("sqlFile") {
        InterchangeableChangeLog.set {
            changeSet(author = "user", id = "100") {
                sqlFile(
                    path = "ChangeSetSpec/sqlFile.sql",
                    encoding = "Shift_JIS"
                )
            }
        }
        test("can migrate") {
            subject()
            SharedResources.targetDatabaseServer.generateDdl().toMainDdl() shouldMatchWithoutLineBreaks sql(
                """
                   CREATE CACHED TABLE "PUBLIC".U&"\5bff\53f8"(
                       U&"\ff49\ff44" INTEGER NOT NULL,
                       U&"\5bff\53f8\30cd\30bf\306e\540d\524d" CHARACTER VARYING(255)
                   );
                   ALTER TABLE "PUBLIC".U&"\5bff\53f8" ADD CONSTRAINT "PUBLIC"."CONSTRAINT_B" PRIMARY KEY(U&"\ff49\ff44");
                """.trimIndent()
            )
        }
    }
    context("stop") {
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
                stop("stop")
            }
            changeSet(author = "user", id = "300") {
                // not executed
                createTable(tableName = "company") {
                    column(name = "id", type = "UUID") {
                        constraints(nullable = false, primaryKey = true)
                    }
                    column(name = "name", type = "VARCHAR(256)")
                }
            }
        }
        test("throw error") {
            shouldThrow<CommandExecutionException> {
                subject()
            }
            SharedResources.targetDatabaseServer.generateDdl().toMainDdl() shouldMatchWithoutLineBreaks sql(
                """
                    CREATE CACHED TABLE "PUBLIC"."COMPANY"(
                        "ID" UUID NOT NULL,
                        "NAME" CHARACTER VARYING(256)
                    );
                    ALTER TABLE "PUBLIC"."COMPANY" ADD CONSTRAINT "PUBLIC"."PK_COMPANY" PRIMARY KEY("ID");
                """.trimIndent()
            )
        }
    }
    context("tagDatabase") {
        InterchangeableChangeLog.set {
            changeSet(author = "user", id = "100") {
                tagDatabase("example_tag1")
            }
        }
        test("can migrate") {
            subject()
            val db = SharedResources.targetDatabaseServer.komapperDb()
            val d = Meta.databasechangelog
            val result = db.runQuery {
                QueryDsl.from(d).single()
            }
            result.tag shouldBe "example_tag1"
        }
    }
})

private fun ChangeSetDsl.createCompanyTable() {
    createTable(tableName = "company") {
        column(name = "id", type = "UUID") {
            constraints(nullable = false, primaryKey = true)
        }
        column(name = "name", type = "VARCHAR(256)")
    }
}
