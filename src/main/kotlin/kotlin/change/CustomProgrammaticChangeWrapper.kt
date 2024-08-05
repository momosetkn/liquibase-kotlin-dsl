package org.liquibase.kotlin.change

import liquibase.change.AbstractChange
import liquibase.change.custom.CustomChange
import liquibase.change.custom.CustomSqlChange
import liquibase.change.custom.CustomSqlRollback
import liquibase.change.custom.CustomTaskChange
import liquibase.change.custom.CustomTaskRollback
import liquibase.database.Database
import liquibase.exception.CustomChangeException
import liquibase.exception.RollbackImpossibleException
import liquibase.exception.ValidationErrors
import liquibase.exception.Warnings
import liquibase.statement.SqlStatement

class CustomProgrammaticChangeWrapper(
    private val customChange: CustomChange,
) : AbstractChange() {
    override fun getConfirmationMessage(): String = customChange.confirmationMessage

    override fun generateStatements(database: Database): Array<SqlStatement> {
        configureCustomChange()
        if (customChange is CustomSqlChange) {
            return customChange.generateStatements(database)
        } else if (customChange is CustomTaskChange) {
            customChange.execute(database)
        }

        // doesn't provide any sql statements to execute
        return emptyArray()
    }

    override fun validate(database: Database): ValidationErrors =
        try {
            customChange.validate(database)
        } catch (e: AbstractMethodError) {
            ValidationErrors()
        }

    override fun generateRollbackStatements(database: Database): Array<SqlStatement> {
        if (supportsRollback(database)) {
            try {
                configureCustomChange()
                if (customChange is CustomSqlChange) {
                    return customChange.generateStatements(database)
                } else if (customChange is CustomTaskRollback) {
                    customChange.rollback(database)
                }
            } catch (e: CustomChangeException) {
                throw RollbackImpossibleException(e)
            }
        }

        // doesn't provide any sql statements to execute
        return emptyArray()
    }

    override fun supportsRollback(database: Database): Boolean =
        customChange is CustomSqlRollback || customChange is CustomTaskRollback

    override fun warn(database: Database): Warnings {
        // does not support warns
        return Warnings()
    }

    private fun configureCustomChange() {
        try {
            customChange.setFileOpener(resourceAccessor)
            customChange.setUp()
        } catch (e: Exception) {
            throw CustomChangeException(e)
        }
    }
}
