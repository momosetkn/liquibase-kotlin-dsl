package momosetkn

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import komapper.databasechangelog
import momosetkn.liquibase.client.LiquibaseClient
import momosetkn.utils.DDLUtils.shouldBeEqualDdl
import momosetkn.utils.Database
import momosetkn.utils.DatabaseKomapperExtensions.komapperDb
import momosetkn.utils.InterchangeableChangeLog
import momosetkn.utils.set
import org.komapper.core.dsl.Meta
import org.komapper.core.dsl.QueryDsl

class ChangeSetSpec : FunSpec({
    beforeEach {
        Database.start()
    }
    afterEach {
        Database.stop()
    }
    val client = LiquibaseClient {
        globalArgs {
            general {
                showBanner = false
            }
        }
    }
    fun subject() {
        val container = Database.startedContainer
        client.update(
            driver = "org.postgresql.Driver",
            url = container.jdbcUrl,
            username = container.username,
            password = container.password,
            changelogFile = InterchangeableChangeLog::class.qualifiedName!!,
        )
    }

    context("executeCommand") {
        InterchangeableChangeLog.set {
            changeSet(author = "user", id = "100") {
                executeCommand(
                    executable = "docker",
                    timeout = "10s"
                ) {
                    arg("ps")
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
            Database.shouldBeEqualDdl(
                """
                    CREATE TABLE public.table_a (
                        id integer NOT NULL,
                        name character varying(255)
                    );
                    ALTER TABLE public.table_a OWNER TO test;
                    ALTER TABLE ONLY public.table_a
                        ADD CONSTRAINT table_a_pkey PRIMARY KEY (id);
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
            Database.shouldBeEqualDdl(
                """
                    CREATE TABLE public.table_a (
                        id integer NOT NULL,
                        name character varying(255)
                    );
                    ALTER TABLE public.table_a OWNER TO test;
                    ALTER TABLE ONLY public.table_a
                        ADD CONSTRAINT table_a_pkey PRIMARY KEY (id);
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
            Database.shouldBeEqualDdl(
                """
                   CREATE TABLE public."寿司" (
                        "ｉｄ" integer NOT NULL,
                        "寿司ネタの名前" character varying(255)
                    );
                    ALTER TABLE public."寿司" OWNER TO test;
                    ALTER TABLE ONLY public."寿司"
                        ADD CONSTRAINT "寿司_pkey" PRIMARY KEY ("ｉｄ");
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
        test("can migrate") {
            shouldThrow<IllegalStateException> {
                subject()
            }
            Database.shouldBeEqualDdl(
                """
                    CREATE TABLE public.company (
                        id uuid NOT NULL,
                        name character varying(256)
                    );
                    ALTER TABLE public.company OWNER TO test;
                    ALTER TABLE ONLY public.company
                        ADD CONSTRAINT company_pkey PRIMARY KEY (id);
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
            val db = Database.komapperDb()
            val d = Meta.databasechangelog
            val results = db.runQuery {
                QueryDsl.from(d)
            }
            results.size shouldBe 1
            results[0].tag shouldBe "example_tag1"
        }
    }
})
