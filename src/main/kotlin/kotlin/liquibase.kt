package org.liquibase.kotlin

import liquibase.parser.ChangeLogParserFactory
import liquibase.parser.ext.KotlinLiquibaseChangeLogParser

// todo: confirm can registered
object LiquibaseKotlin {
    init {
        ChangeLogParserFactory.getInstance().register(KotlinLiquibaseChangeLogParser())
    }
}
