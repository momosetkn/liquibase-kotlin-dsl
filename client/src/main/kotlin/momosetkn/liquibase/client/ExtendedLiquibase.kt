package momosetkn.liquibase.client

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.Scope
import liquibase.database.Database
import liquibase.exception.LiquibaseException
import liquibase.resource.ResourceAccessor
import java.io.Writer

class ExtendedLiquibase(
    changeLogFile: String,
    database: Database,
    resourceAccessor: ResourceAccessor = Scope.getCurrentScope().resourceAccessor,
) : Liquibase(changeLogFile, resourceAccessor, database,) {
    // to public futureRollbackSQL method, because the original futureRollbackSQL method is protected.
    @Throws(LiquibaseException::class)
    fun extendedFutureRollbackSQL(
        count: Int? = null,
        tag: String? = null,
        contexts: Contexts? = null,
        labelExpression: LabelExpression?,
        output: Writer? = null,
        checkLiquibaseTables: Boolean
    ) {
        return futureRollbackSQL(count, tag, contexts, labelExpression, output, checkLiquibaseTables)
    }
}
