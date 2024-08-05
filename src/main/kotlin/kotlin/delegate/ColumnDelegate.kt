package org.liquibase.kotlin.delegate

import liquibase.change.ColumnConfig
import liquibase.changelog.DatabaseChangeLog
import liquibase.exception.ChangeLogParseException
import liquibase.util.PatchedObjectUtil

/**
 * This class is a delegate for nested columns found frequently in the DSL, such as inside the
 * {@code createTable} change.  It can handle both normal columns, as found in the
 * {@code createTable} change, and the {@code LoadDataColumnConfig} columns that can be found in the
 * {@code loadData} change.  When the {@link ChangeSetDelegate} creates a ColumnDelegate for a,
 * given change, it will need to set the correct columnConfigClass.
 * <p>
 * This class also handles the nested where and whereParams elements that appear in the
 * {@code update} and {@code delete} changes.  This probably does not cohere with the overall
 * purpose of the class, but it is much better than having to duplicate the column processing logic
 * since the {@code update} change uses columns and a where clause.
 * <p>
 * This delegate will expand expressions to make databaseChangeLog property substitutions.  It is
 * important that the caller does not do it again.
 *
 * @author Steven C. Saliman
 */
class ColumnDelegate(
    private val columnConfigClass: Class<out ColumnConfig> = ColumnConfig::class.java,
    private val databaseChangeLog: DatabaseChangeLog,
    private val changeSetId: String = "<unknown>",
    private val changeName: String = "<unknown>",
    private val change: Any,
) {
    /**
     * Parse a single column entry in a closure.
     * @param params the attributes to set.
     * @param closure a child closure to call, such as a constraint clause
     */
    fun column(
        params: Map<String, Any>,
        closure: (ConstraintDelegate.() -> Unit)? = null,
    ) {
        val column = columnConfigClass.getDeclaredConstructor().newInstance()

        // Process the column params
        params.forEach { (key, value) ->
            try {
                PatchedObjectUtil.setProperty(
                    column,
                    key,
                    DelegateUtil.expandExpressions(value, databaseChangeLog),
                )
            } catch (e: RuntimeException) {
                // Rethrow as an ChangeLogParseException with a more helpful message than you'll get
                // from the Liquibase helper.
                throw ChangeLogParseException(
                    "ChangeSet '$changeSetId': '$key' is not a valid column attribute for '$changeName' changes.",
                    e,
                )
            }
        }

        // Process nested closure (constraints)
        closure?.let {
            val constraintDelegate = ConstraintDelegate(databaseChangeLog, changeSetId, changeName)
            it(constraintDelegate)
            column.constraints = constraintDelegate.constraint
        }

        // Try to add the column to the change.  If we're dealing with something like a "delete"
        // change, we'll get an exception, which we'll rethrow as a parse exception to tell the user
        // that columns are not allowed in that change.
        try {
            change::class.java
                .getMethod(
                    "addColumn",
                    ColumnConfig::class.java,
                ).invoke(change, column)
        } catch (e: NoSuchMethodException) {
            throw ChangeLogParseException(
                "ChangeSet '$changeSetId': columns are not allowed in '$changeName' changes.",
                e,
            )
        }
    }

    /**
     * Process the where clause for the closure and add it to the change.  If the change doesn't
     * support where clauses, we'll get a ChangeLogParseException.
     * @param whereClause the where clause to use.
     */
    fun where(whereClause: String) {
        val expandedWhereClause = DelegateUtil.expandExpressions(whereClause, databaseChangeLog)
        // If we have a where clause, try to set it in the change.
        try {
            PatchedObjectUtil.setProperty(change, "where", expandedWhereClause)
        } catch (e: RuntimeException) {
            throw ChangeLogParseException(
                "ChangeSet '$changeSetId': a where clause is invalid for '$changeName' changes.",
                e,
            )
        }
    }

    /**
     * Process the whereParams clause for the closure and add the parameters to the change.  If the
     * change doesn't support whereParams, we'll get a ChangeLogParseException.
     * @param closure the nested closure with the parameters themselves.
     */
    fun whereParams(closure: WhereParamsDelegate.() -> Unit) {
        val whereParamsDelegate =
            WhereParamsDelegate(databaseChangeLog, changeSetId, changeName, change)
        whereParamsDelegate.closure()
    }

    operator fun Any?.invoke() =
        throw ChangeLogParseException(
            "ChangeSet '$changeSetId': '$changeName' is not a valid child element of $changeName changes",
        )
}
