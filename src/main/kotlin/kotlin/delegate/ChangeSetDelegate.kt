package org.liquibase.kotlin.delegate

import KeyValueDelegate
import liquibase.Scope
import liquibase.change.AddColumnConfig
import liquibase.change.Change
import liquibase.change.ChangeFactory
import liquibase.change.ColumnConfig
import liquibase.change.core.LoadDataColumnConfig
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.exception.ChangeLogParseException
import liquibase.exception.RollbackImpossibleException
import liquibase.util.PatchedObjectUtil

/**
 * This class is the closure delegate for a ChangeSet.  It processes all the refactoring changes for
 * the ChangeSet.  it basically creates all the changes that need to belong to the ChangeSet, but it
 * doesn't worry too much about validity of the change because Liquibase itself will deal with that.
 *
 * To keep the code simple, we don't worry too much about supporting things that we know to be
 * invalid.  For example, if you try to use a change like
 * addColumn { column(columnName: 'newcolumn') }, you'll get a wonderfully helpful
 * MissingMethodException because of the missing map in the change.  We aren't going to muddy up the
 * code trying to support addColumn changes with no attributes because we know that at least a
 * table name is required.  Similarly, it doesn't make sense to have an addColumn change without at
 * least one column, so we don't deal well with the addColumn change without a closure.
 *
 * @author Steven C. Saliman
 */
class ChangeSetDelegate(
    val changeSet: ChangeSet,
    val databaseChangeLog: DatabaseChangeLog,
    val inRollback: Boolean = false,
) {
    val changeFactory = Scope.getCurrentScope().getSingleton(ChangeFactory::class.java)

    // -------------------------------------------------------------------------------------------
    // Non refactoring elements.

    fun comment(text: String) {
        changeSet.comments = DelegateUtil.expandExpressions(text, databaseChangeLog)
    }

    fun preConditions(
        params: Map<String, Any> = emptyMap(),
        closure: PreconditionDelegate.() -> Unit,
    ) {
        changeSet.preconditions =
            PreconditionDelegate.buildPreconditionContainer(
                databaseChangeLog,
                changeSet.id,
                params,
                closure,
            )
    }

    fun validCheckSum(checksum: String) {
        changeSet.addValidCheckSum(checksum)
    }

    /**
     * Process an empty rollback.  This doesn't actually do anything, but empty rollbacks are
     * allowed by the spec.
     */
    fun rollback() {
        // To support empty rollbacks (allowed by the spec)
    }

    fun rollback(sql: String) {
        changeSet.addRollBackSQL(DelegateUtil.expandExpressions(sql, databaseChangeLog))
    }

    fun rollback(closure: ChangeSetDelegate.() -> String?) {
        val delegate = ChangeSetDelegate(changeSet, databaseChangeLog, true)
        val sql = delegate.closure()
        if (sql != null) {
            changeSet.addRollBackSQL(DelegateUtil.expandExpressions(sql, databaseChangeLog))
        }
    }

    fun rollback(params: Map<String, Any>) {
        var id: String? = null
        var author: String? = null
        var filePath: String? = null

        params.forEach { (key, value) ->
            when (key) {
                "changeSetId" -> id = DelegateUtil.expandExpressions(value, databaseChangeLog)
                "changeSetAuthor" ->
                    author =
                        DelegateUtil.expandExpressions(value, databaseChangeLog)
                "changeSetPath" ->
                    filePath =
                        DelegateUtil.expandExpressions(value, databaseChangeLog)
                else -> throw ChangeLogParseException(
                    "ChangeSet '${changeSet.id}': '$key' is not a valid rollback attribute.",
                )
            }
        }

        if (id == null) {
            throw RollbackImpossibleException(
                "no changeSetId given for rollback in '${changeSet.id}'",
            )
        }

        if (filePath == null) {
            filePath = databaseChangeLog.filePath
        }

        val referencedChangeSet = databaseChangeLog.getChangeSet(filePath, author, id)
        referencedChangeSet?.changes?.forEach { change ->
            changeSet.addRollbackChange(change)
        }
            ?: throw RollbackImpossibleException(
                "Could not find changeSet to use for rollback: $filePath:$author:$id",
            )
    }

    fun modifySql(
        params: Map<String, Any> = emptyMap(),
        closure: ModifySqlDelegate.() -> Unit,
    ) {
        val delegate = ModifySqlDelegate(params, changeSet)
        delegate.closure()
        delegate.sqlVisitors.forEach {
            changeSet.addSqlVisitor(it)
        }
    }

    // TODO: replace to kotlin?
//    fun groovyChange(closure: GroovyChangeDelegate.() -> Unit) {
//        val delegate = GroovyChangeDelegate(closure, changeSet)
//        delegate.changeSet = changeSet
//        delegate.closure()
//    }

    // ------------------------------------------------------------------------------------------
    // Refactoring changes.  Most changes will be handled by method missing.  We only need to define
    // methods that take closures or strings.

    /**
     * Groovy calls methodMissing when it can't find a matching method to call.  We use it to create
     * a Liquibase change with the same name as the element.  The methodMissing method can only
     * create changes that are present in the Liquibase Registry, have no nested elements, and take
     * maps as attributes.
     *
     * Changes that allow nested elements need special handling in their own methods to handle the
     * closure delegate needed to process the nested elements.  Non-map arguments (like strings)
     * also need special handling.
     * @param name the name of the method Groovy wanted to call.  We'll assume it is a valid
     * Liquibase change name.
     * @param args the original arguments to that method.  We can only handle a single map here.
     * @throws ChangeLogParseException if there is no change with the given name in the registry.
     */
    fun methodMissing(
        name: String,
        args: Array<Any?>,
    ): Any? {
        val change = lookupChange(name)

        when {
            args.isEmpty() -> addChange(change)
            args.size > 1 || args[0] !is Map<*, *> -> throw ChangeLogParseException(
                "ChangeSet '${changeSet.id}': '$name' changes are only valid with a single map argument",
            )
            else -> addMapBasedChange(name, args[0] as Map<String, Any>)
        }
        return null
    }

    fun addColumn(
        params: Map<String, Any>,
        closure: ColumnDelegate.() -> Unit,
    ) {
        addChange(
            makeColumnarChangeFromMap("addColumn", AddColumnConfig::class.java, params, closure),
        )
    }

    fun addForeignKeyConstraint(params: Map<String, Any>) {
        addMapBasedChange("addForeignKeyConstraint", params)
        if (params["referencesUniqueColumn"] != null) {
            println(
                "Warning: ChangeSet '${changeSet.id}': addForeignKeyConstraint's referencesUniqueColumn parameter has been deprecated, and may be removed in a future release.",
            )
            println("Consider removing it, as Liquibase ignores it anyway.")
        }
    }

    fun createIndex(
        params: Map<String, Any>,
        closure: ColumnDelegate.() -> Unit,
    ) {
        addChange(
            makeColumnarChangeFromMap("createIndex", AddColumnConfig::class.java, params, closure),
        )
    }

    fun createProcedure(
        params: Map<String, Any> = emptyMap(),
        closure: () -> String,
    ) {
        val change = makeChangeFromMap("createProcedure", params)
        change["procedureText"] = DelegateUtil.expandExpressions(closure(), databaseChangeLog)
        addChange(change)
    }

    fun createProcedure(storedProc: String) {
        val change = lookupChange("createProcedure")
        change["procedureText"] = DelegateUtil.expandExpressions(storedProc, databaseChangeLog)
        addChange(change)
    }

    fun createTable(
        params: Map<String, Any>,
        closure: ColumnDelegate.() -> Unit,
    ) {
        addChange(
            makeColumnarChangeFromMap("createTable", ColumnConfig::class.java, params, closure),
        )
    }

    fun createView(
        params: Map<String, Any>,
        closure: () -> String,
    ) {
        val change = makeChangeFromMap("createView", params)
        change["selectQuery"] = DelegateUtil.expandExpressions(closure(), databaseChangeLog)
        addChange(change)
    }

    fun customChange(
        params: Map<String, Any>,
        closure: (KeyValueDelegate.() -> Unit)? = null,
    ) {
        val change = lookupChange("customChange")
        change["classLoader"] = closure?.javaClass?.classLoader ?: this::class.java.classLoader
        val className = DelegateUtil.expandExpressions(params["class"] as String, databaseChangeLog)
        change["class"] = className
//        change.setClass(className)

        closure?.let {
            val delegate = KeyValueDelegate()
            it(delegate)
            delegate.map.forEach { (key, value) ->
                change["param"] = DelegateUtil.expandExpressions(value, databaseChangeLog)
//                change.setParam(key, DelegateUtil.expandExpressions(value, databaseChangeLog))
            }
        }

        addChange(change)
    }

    fun customChange(closure: () -> Unit) {
        // TODO Figure out how to implement closure-based custom changes.
    }

    fun delete(
        params: Map<String, Any>,
        closure: ColumnDelegate.() -> Unit,
    ) {
        addChange(makeColumnarChangeFromMap("delete", ColumnConfig::class.java, params, closure))
    }

    fun dropColumn(
        params: Map<String, Any>,
        closure: ColumnDelegate.() -> Unit,
    ) {
        addChange(
            makeColumnarChangeFromMap("dropColumn", ColumnConfig::class.java, params, closure),
        )
    }

    fun empty() {
        // To support empty changes (allowed by the spec)
    }

    fun executeCommand(
        params: Map<String, Any>,
        closure: ArgumentDelegate.() -> Unit,
    ) {
        val change = makeChangeFromMap("executeCommand", params)
        val delegate = ArgumentDelegate(changeSet.id, "executeCommand")
        closure(delegate)
        delegate.args.forEach { arg ->
            change.addArg(DelegateUtil.expandExpressions(arg, databaseChangeLog))
        }

        addChange(change)
    }

    fun insert(
        params: Map<String, Any>,
        closure: ColumnDelegate.() -> Unit,
    ) {
        addChange(makeColumnarChangeFromMap("insert", ColumnConfig::class.java, params, closure))
    }

    fun loadData(
        params: Map<String, Any>,
        closure: ColumnDelegate.() -> Unit,
    ) {
        addChange(
            makeColumnarChangeFromMap(
                "loadData",
                LoadDataColumnConfig::class.java,
                params,
                closure,
            ),
        )
    }

    fun loadUpdateData(
        params: Map<String, Any>,
        closure: ColumnDelegate.() -> Unit,
    ) {
        addChange(
            makeColumnarChangeFromMap(
                "loadUpdateData",
                LoadDataColumnConfig::class.java,
                params,
                closure,
            ),
        )
    }

    fun output(params: Map<String, Any>) {
        if (!params.containsKey("target")) {
            params["target"] = "STDERR"
        }
        addMapBasedChange("output", params)
    }

    fun sql(
        params: Map<String, Any> = emptyMap(),
        closure: CommentDelegate.() -> String,
    ) {
        val change = makeChangeFromMap("sql", params)
        val delegate = CommentDelegate(changeSet.id, "sql")
        change.sql = DelegateUtil.expandExpressions(delegate.closure(), databaseChangeLog)
        change.comment = DelegateUtil.expandExpressions(delegate.comment, databaseChangeLog)
        addChange(change)
    }

    fun sql(sql: String) {
        val change = lookupChange("sql")
        change.sql = DelegateUtil.expandExpressions(sql, databaseChangeLog)
        addChange(change)
    }

    fun sqlFile(params: Map<String, Any>) {
        if (params.containsKey("sql")) {
            throw ChangeLogParseException(
                "ChangeSet '${changeSet.id}': 'sql' is an invalid property for 'sqlFile' changes.",
            )
        }
        val change = makeChangeFromMap("sqlFile", params)
        change.finishInitialization()
        addChange(change)
    }

    fun stop(message: String) {
        val change = lookupChange("stop")
        change.message = DelegateUtil.expandExpressions(message, databaseChangeLog)
        addChange(change)
    }

    fun tagDatabase(tagName: String) {
        val change = lookupChange("tagDatabase")
        change.tag = DelegateUtil.expandExpressions(tagName, databaseChangeLog)
        addChange(change)
    }

    fun update(
        params: Map<String, Any>,
        closure: ColumnDelegate.() -> Unit,
    ) {
        addChange(makeColumnarChangeFromMap("update", ColumnConfig::class.java, params, closure))
    }

    private fun lookupChange(name: String): Change {
        val change = changeFactory.create(name)
        return change
            ?: throw ChangeLogParseException(
                "ChangeSet '${changeSet.id}': '$name' is not a valid element of a ChangeSet",
            )
    }

    private fun makeColumnarChangeFromMap(
        name: String,
        columnConfigClass: Class<out ColumnConfig>,
        params: Map<String, Any>,
        closure: ColumnDelegate.() -> Unit,
    ): Change {
        val change = makeChangeFromMap(name, params)
        val columnDelegate =
            ColumnDelegate(columnConfigClass, databaseChangeLog, changeSet.id, name, change)
        columnDelegate.closure()
        return change
    }

    private fun makeChangeFromMap(
        name: String,
        sourceMap: Map<String, Any>,
    ): Change {
        val change = lookupChange(name)
        sourceMap.forEach { (key, value) ->
            try {
                PatchedObjectUtil.setProperty(
                    change,
                    key,
                    DelegateUtil.expandExpressions(value, databaseChangeLog),
                )
            } catch (ex: NumberFormatException) {
                change[key] = value.toString().toBigInteger()
            } catch (re: RuntimeException) {
                throw ChangeLogParseException(
                    "ChangeSet '${changeSet.id}': '$key' is an invalid property for '$name' changes.",
                    re,
                )
            }
        }
        return change
    }

    private fun addMapBasedChange(
        name: String,
        sourceMap: Map<String, Any>,
    ) {
        addChange(makeChangeFromMap(name, sourceMap))
    }

    private fun addChange(change: Change) {
        if (inRollback) {
            changeSet.addRollbackChange(change)
        } else {
            changeSet.addChange(change)
        }
    }
}
