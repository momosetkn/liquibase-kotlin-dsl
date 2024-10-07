# liquibase-kotlin

[Liquibase-kotlin](https://momosetkn.github.io/liquibase-kotlin) was created with the aim of integrating [liquibase](https://github.com/liquibase/liquibase) with kotlin.
This module provides Kotlin-DSL, Wrapper-client, ORM-integration.

Liquibase-kotlin documentation page 
- [liquibase-kotlin document](https://momosetkn.github.io/liquibase-kotlin-docs) 

## How to install

```kotlin
repositories {
    // Add below to repositories. because this product is publish to jitpack.
    maven { url = URI("https://jitpack.io") }
}

dependencies {
    // liquibase
    implementation("org.liquibase:liquibase-core:4.29.2")
    val liquibaseKotlinVersion = "0.7.1"
    // You can choose to install either kotlin-script or kotlin-compiled.
    // for kotlin-script
    implementation("com.github.momosetkn.liquibase-kotlin:liquibase-kotlin-dsl:$liquibaseKotlinVersion")
    implementation("com.github.momosetkn.liquibase-kotlin:liquibase-kotlin-script-parser:$liquibaseKotlinVersion")
    implementation("com.github.momosetkn.liquibase-kotlin:liquibase-kotlin-script-serializer:$liquibaseKotlinVersion")
    // for kotlin-compiled
    implementation("com.github.momosetkn.liquibase-kotlin:liquibase-kotlin-dsl:$liquibaseKotlinVersion")
    implementation("com.github.momosetkn.liquibase-kotlin:liquibase-kotlin-compiled-parser:$liquibaseKotlinVersion")
    implementation("com.github.momosetkn.liquibase-kotlin:liquibase-kotlin-compiled-serializer:$liquibaseKotlinVersion")
    // If you want to use call liquibase-command by kotlin, add the following code.
    implementation("com.github.momosetkn.liquibase-kotlin:liquibase-kotlin-client:$liquibaseKotlinVersion")
    // If you want to use komapper on customChange, add the following code.
    implementation("com.github.momosetkn.liquibase-kotlin:liquibase-kotlin-custom-komapper-jdbc-change:$liquibaseKotlinVersion")
    // If you want to use exposed on customChange, add the following code.
    implementation("com.github.momosetkn.liquibase-kotlin:liquibase-kotlin-custom-exposed-migration-change:$liquibaseKotlinVersion")
    // If you want to use ktorm on customChange, add the following code.
    implementation("com.github.momosetkn.liquibase-kotlin:liquibase-kotlin-custom-ktorm-change:$liquibaseKotlinVersion")
    // If you want to use jOOQ on customChange, add the following code.
    implementation("com.github.momosetkn.liquibase-kotlin:liquibase-kotlin-custom-jooq-change:$liquibaseKotlinVersion")
}
```

## Features

### Kotlin-DSL

You can choose between KotlinScript and Kotlin (not script).
Both have the same syntax for changeSet.

> [!NOTE]
> kotlin-script module is It is can integration with existing migration files.
> But when integrating the kotlin-compiled module, you need to load kotlin-compiled migration using include or includeAll.

#### kotlin-script

Please place the kts files under `src/main/resources`.

kotlin-script example
https://github.com/momosetkn/liquibase-kotlin-example/blob/main/liquibase-kotlin-script-example/src/main/kotlin/Main.kt

#### kotlin-compiled

kotlin-compiled is read KotlinCompiledDatabaseChangeLog class in classpath with using `Class.forName`.
the changelog file is specifying the class name.

example
```kotlin
class DatabaseChangelogAll : KotlinCompiledDatabaseChangeLog({
    includeAll("changelogs.main") // specify package
})
```

```kotlin
class DatabaseChangelog0 : KotlinCompiledDatabaseChangeLog({
    changeSet(author = "momose", id = "100-0") {
        tagDatabase("started")
    }

    changeSet(author = "momose", id = "100-10") {
        createTable(tableName = "company") {
            column(name = "id", type = "UUID") {
                constraints(nullable = false, primaryKey = true)
            }
            column(name = "name", type = "VARCHAR(256)")
        }
    }
})
```

kotlin-compiled example
https://github.com/momosetkn/liquibase-kotlin-example/blob/main/liquibase-kotlin-compiled-example/src/main/kotlin/Main.kt

### Kotlin client

Client module can execute Liquibase commands programmatically.

example
```kotlin
configureLiquibase {
    global {
        general {
            showBanner = false
        }
    }
}
val database = LiquibaseDatabaseFactory.create(
    driver = "org.postgresql.Driver",
    url = "jdbc:postgresql://127.0.0.1:5432/test",
    username = "root",
    password = "test",
)
val liquibaseClient = LiquibaseClient(
    changelogFile = "db.changelog.all.kts",
    database = database,
)
liquibaseClient.update()
```

### Use Komapper on customChange

`liquibase-kotlin-custom-komapper-jdbc-change` module can use [Komapper](https://www.komapper.org/) on customChange

> [!NOTE]
> can use both the `kotlin-script` and `kotlin-compiled`.

add bellow dependencies.

```kotlin
implementation("com.github.momosetkn.liquibase-kotlin:liquibase-kotlin-custom-komapper-jdbc-change:$liquibaseKotlinVersion")
```

changeSet example
```kotlin
changeSet(author = "momose", id = "100-40") {
    customKomapperJdbcChange(
        execute = { db ->
            val query = QueryDsl.executeScript(
                """
                CREATE TABLE created_by_komapper (
                    id uuid NOT NULL,
                    name character varying(256)
                );
                """.trimIndent()
            )
            db.runQuery(query)
        },
        rollback = { db ->
            val query = QueryDsl.executeScript("DROP TABLE created_by_komapper")
            db.runQuery(query)
        },
    )
}
```

### Use jOOQ on customChange

`liquibase-kotlin-custom-jooq-change` module can use [jOOQ](https://www.jooq.org/) on customChange

> [!NOTE]
> can use both the `kotlin-script` and `kotlin-compiled`.

add bellow dependencies.

```kotlin
implementation("com.github.momosetkn.liquibase-kotlin:liquibase-kotlin-custom-jooq-change:$liquibaseKotlinVersion")
```

changeSet example
```kotlin
changeSet(author = "momose", id = "100-40") {
    customJooqChange(
        execute = { db ->
            val query = 
                """
                CREATE TABLE created_by_komapper (
                    id uuid NOT NULL,
                    name character varying(256)
                );
                """.trimIndent()
            db.execute(query)
        },
        rollback = { db ->
            val query = "DROP TABLE created_by_komapper"
            db.execute(query)
        },
    )
}
```

### Use Exposed-migration on customChange

`liquibase-kotlin-custom-exposed-migration-change` module can use [Exposed](https://jetbrains.github.io/Exposed/home.html) on customChange

> [!NOTE]
> can use both the `kotlin-script` and `kotlin-compiled`.

add bellow dependencies.

```kotlin
implementation("com.github.momosetkn.liquibase-kotlin:liquibase-kotlin-custom-exposed-migration-change:$liquibaseKotlinVersion")
```

changeSet example
```kotlin
changeSet(author = "momose", id = "100-60") {
    // https://jetbrains.github.io/Exposed/table-definition.html#dsl-create-table
    val createdByExposed = object : Table("created_by_exposed") {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 256)
        override val primaryKey = PrimaryKey(id)
    }
    customExposedMigrationChange(
        execute = { db ->
            transaction(db) {
                SchemaUtils.create(createdByExposed)
            }
        },
        rollback = { db ->
            transaction(db) {
                SchemaUtils.drop(createdByExposed)
            }
        },
    )
}
```


### Use Ktorm on customChange

`liquibase-kotlin-custom-Ktorm-change` module can use [Ktorm](https://www.ktorm.org/en/quick-start.html) on customChange

> [!NOTE]
> can use both the `kotlin-script` and `kotlin-compiled`.

add bellow dependencies.

```kotlin
implementation("com.github.momosetkn.liquibase-kotlin:liquibase-kotlin-custom-ktorm-change:$liquibaseKotlinVersion")
```

changeSet example
```kotlin
changeSet(author = "momose", id = "100-70") {
    customKtormChange(
        execute = { db ->
            val query = """
                 CREATE TABLE created_by_ktorm (
                    id uuid NOT NULL,
                    name character varying(256)
                );
            """.trimIndent()
            db.useConnection { conn ->
                conn.createStatement().execute(query)
            }
        },
        rollback = { db ->
            val query = "DROP TABLE created_by_ktorm"
            db.useConnection { conn ->
                conn.createStatement().execute(query)
            }
        },
    )
}
```

## Prerequisite

- JDK 17 or later

### Test passed liquibase version
- 4.26.0
- 4.27.0
- 4.28.0
- 4.29.2

# example project
https://github.com/momosetkn/liquibase-kotlin-example
