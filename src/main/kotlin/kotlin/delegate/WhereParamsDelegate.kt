package org.liquibase.kotlin.delegate

import PatchedObjectUtil
import liquibase.change.Change
import liquibase.change.ColumnConfig
import liquibase.exception.ChangeLogParseException
import liquibase.util.PatchedObjectUtil

/**
 * This class is a delegate for "whereParams" that can be nested in columnar changes like the
 * `createTable` change.  It is given a change to which it adds the params it processes.
 *
 * @author Steven C. Saliman
 */
class WhereParamsDelegate {
    var databaseChangeLog: Any? = null
    var changeSetId = "<unknown>" // used for error messages
    var changeName = "<unknown>" // used for error messages
    var change: Any? = null

    /**
     * Process one "param" from a "whereParams" closure, and add it to the change.  We'll get an
     * exception if whereParams are not supported by the change. which we'll rethrow as a parse
     * exception to tell the user that columns are not allowed in that change.
     * @param params the parameters for the the "param"
     */
    fun param(params: Map<String, Any>) {
        val columnConfig = ColumnConfig()
        for ((key, value) in params) {
            try {
                PatchedObjectUtil.setProperty(
                    columnConfig,
                    key,
                    DelegateUtil.expandExpressions(value, databaseChangeLog),
                )
            } catch (e: RuntimeException) {
                // Rethrow as an ChangeLogParseException with a more helpful message than you'll get
                // from the Liquibase helper.
                throw ChangeLogParseException(
                    "ChangeSet '$changeSetId': '$key' is not a valid whereParams attribute for '$changeName' changes.",
                    e,
                )
            }
        }

        // try to add the columnConfig to the whereParams of the change
        try {
            (change as? Change)?.addWhereParam(columnConfig)
        } catch (e: NoSuchMethodException) {
            throw ChangeLogParseException(
                "ChangeSet '$changeSetId': whereParams are not allowed in '$changeName' changes.",
                e,
            )
        }
    }
}
