package org.liquibase.kotlin.change

import liquibase.change.AbstractChange
import liquibase.database.Database
import liquibase.statement.SqlStatement

class KotlinChange : AbstractChange() {
    override fun getConfirmationMessage(): String = "Custom Kotlin change executed"

    override fun generateStatements(database: Database?): Array<SqlStatement> = emptyArray()
}
