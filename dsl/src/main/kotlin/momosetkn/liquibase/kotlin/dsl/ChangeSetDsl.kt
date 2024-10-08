package momosetkn.liquibase.kotlin.dsl

import liquibase.Scope
import liquibase.change.core.AddAutoIncrementChange
import liquibase.change.core.AddColumnChange
import liquibase.change.core.AddDefaultValueChange
import liquibase.change.core.AddForeignKeyConstraintChange
import liquibase.change.core.AddLookupTableChange
import liquibase.change.core.AddNotNullConstraintChange
import liquibase.change.core.AddPrimaryKeyChange
import liquibase.change.core.AddUniqueConstraintChange
import liquibase.change.core.AlterSequenceChange
import liquibase.change.core.CreateIndexChange
import liquibase.change.core.CreateProcedureChange
import liquibase.change.core.CreateSequenceChange
import liquibase.change.core.CreateTableChange
import liquibase.change.core.CreateViewChange
import liquibase.change.core.DeleteDataChange
import liquibase.change.core.DropAllForeignKeyConstraintsChange
import liquibase.change.core.DropColumnChange
import liquibase.change.core.DropDefaultValueChange
import liquibase.change.core.DropForeignKeyConstraintChange
import liquibase.change.core.DropIndexChange
import liquibase.change.core.DropNotNullConstraintChange
import liquibase.change.core.DropPrimaryKeyChange
import liquibase.change.core.DropProcedureChange
import liquibase.change.core.DropSequenceChange
import liquibase.change.core.DropTableChange
import liquibase.change.core.DropUniqueConstraintChange
import liquibase.change.core.DropViewChange
import liquibase.change.core.ExecuteShellCommandChange
import liquibase.change.core.InsertDataChange
import liquibase.change.core.LoadDataChange
import liquibase.change.core.LoadUpdateDataChange
import liquibase.change.core.MergeColumnChange
import liquibase.change.core.ModifyDataTypeChange
import liquibase.change.core.OutputChange
import liquibase.change.core.RawSQLChange
import liquibase.change.core.RenameColumnChange
import liquibase.change.core.RenameSequenceChange
import liquibase.change.core.RenameTableChange
import liquibase.change.core.RenameViewChange
import liquibase.change.core.SQLFileChange
import liquibase.change.core.SetColumnRemarksChange
import liquibase.change.core.SetTableRemarksChange
import liquibase.change.core.StopChange
import liquibase.change.core.TagDatabaseChange
import liquibase.change.core.UpdateDataChange
import liquibase.change.custom.CustomChangeWrapper
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.exception.ChangeLogParseException
import liquibase.exception.RollbackImpossibleException
import liquibase.statement.DatabaseFunction
import liquibase.statement.SequenceNextValueFunction
import momosetkn.liquibase.kotlin.dsl.Expressions.evalClassNameExpressions
import momosetkn.liquibase.kotlin.dsl.Expressions.evalExpressions
import momosetkn.liquibase.kotlin.dsl.Expressions.tryEvalExpressions
import org.intellij.lang.annotations.Language

/**
 * The ChangeSetDsl class provides a domain-specific language (DSL) for constructing and managing database change sets.
 * This class includes methods for defining changes to database structures such as tables, columns, indexes, views,
 * procedures, sequences, and constraints.
 *
 * @property changeLog The change log associated with the change set.
 * @property context The context in which the change set executes.
 * @property changeSetSupport Support utilities for change set operations. It is made public for custom DSL.
 */
@Suppress("LargeClass", "TooManyFunctions")
@ChangeLogDslMarker
class ChangeSetDsl(
    private val changeLog: DatabaseChangeLog,
    private val context: ChangeSetContext,
) {
    private val log = Scope.getCurrentScope().getLog(javaClass)

    val changeSetSupport =
        ChangeSetSupport(
            changeSet = context.changeSet,
            inRollback = context.inRollback,
        )

    /**
     * Adds a comment to the current context's change set.
     * Can set comment in [ChangeLogDsl.changeSet]
     *
     * @param text The comment text. If set, that value will take precedence.
     */
    fun comment(text: String) {
        // NOTE: First to win in XML, last to win in groovy DSL
        context.changeSet.comments = context.changeSet.comments ?: text.evalExpressions(changeLog)
    }

    /**
     * Precondition the control execute changeSet for the database state.
     * [official-document](https://docs.liquibase.com/concepts/changelogs/preconditions.html)
     *
     * @param onError either of CONTINUE / HALT / MARK_RAN / WARN
     * @param onErrorMessage provides a specific error message when an error occurs.
     * @param onFail either of CONTINUE / HALT / MARK_RAN / WARN
     * @param onFailMessage provides a specific failure message when a failure occurs.
     * @param onSqlOutput FAIL / IGNORE / TEST. used in update-sql command.
     * @param block Specify the condition of precondition
     */
    fun preConditions(
        onError: String? = null,
        onErrorMessage: String? = null,
        onFail: String? = null,
        onFailMessage: String? = null,
        onSqlOutput: String? = null,
        block: RootPreConditionDsl.() -> Unit,
    ) {
        val preconditionContainerContext =
            PreconditionContainerContext(
                onError = onError,
                onFail = onFail,
                onFailMessage = onFailMessage?.evalExpressions(changeLog),
                onErrorMessage = onErrorMessage?.evalExpressions(changeLog),
                onSqlOutput = onSqlOutput,
            )
        val dsl = PreConditionDsl.build(
            changeLog = changeLog,
            preconditionContainerContext = preconditionContainerContext,
        )
        context.changeSet.preconditions = wrapChangeLogParseException { dsl(block) }
    }

    fun validCheckSum(checksum: String) {
        context.changeSet.addValidCheckSum(checksum)
    }

    fun rollback(block: ChangeSetDsl.() -> Unit) {
        val rollbackContext = context.copy(
            inRollback = true,
        )
        val dsl =
            ChangeSetDsl(
                changeLog = changeLog,
                context = rollbackContext,
            )
        block(dsl)
    }

    fun rollback(
        changeSetId: String,
        changeSetAuthor: String,
        changeSetPath: String? = null,
    ) {
        val overrideId = changeSetId.evalExpressions(changeLog)
        val overrideAuthor = changeSetAuthor.evalExpressions(changeLog)

        val overrideFilePath =
            changeSetPath?.let {
                changeSetPath.evalExpressions(changeLog)
            } ?: changeLog.filePath

        val referencedChangeSet =
            changeLog.getChangeSet(
                overrideFilePath,
                overrideAuthor,
                overrideId,
            ) ?: throw RollbackImpossibleException(
                "Could not find changeSet to use for rollback: $changeSetPath:$changeSetAuthor:$changeSetId",
            )
        referencedChangeSet.changes.forEach { change ->
            context.changeSet.addRollbackChange(change)
        }
    }

    // Entities
    fun createTable(
        catalogName: String? = null,
        ifNotExists: Boolean? = null,
        remarks: String? = null,
        schemaName: String? = null,
        tableName: String,
        tablespace: String? = null,
        block: ColumnDsl.() -> Unit,
    ) {
        val change = changeSetSupport.createChange("createTable") as CreateTableChange
        change.catalogName = catalogName
        change.ifNotExists = ifNotExists
        change.remarks = remarks
        change.schemaName = schemaName
        change.tableName = tableName
        change.tablespace = tablespace
        val dsl = ColumnDsl(changeLog)
        change.columns = wrapChangeLogParseException { dsl(block) }
        changeSetSupport.addChange(change)
    }

    fun dropTable(
        cascadeConstraints: Boolean? = null,
        catalogName: String? = null,
        schemaName: String? = null,
        tableName: String,
    ) {
        val change = changeSetSupport.createChange("dropTable") as DropTableChange
        change.isCascadeConstraints = cascadeConstraints
        change.catalogName = catalogName
        change.schemaName = schemaName
        change.tableName = tableName
        changeSetSupport.addChange(change)
    }

    fun setTableRemarks(
        catalogName: String? = null,
        remarks: String,
        schemaName: String? = null,
        tableName: String,
    ) {
        val change = changeSetSupport.createChange("setTableRemarks") as SetTableRemarksChange
        change.catalogName = catalogName
        change.remarks = remarks
        change.schemaName = schemaName
        change.tableName = tableName
        changeSetSupport.addChange(change)
    }

    fun renameTable(
        catalogName: String? = null,
        newTableName: String,
        oldTableName: String,
        schemaName: String? = null,
    ) {
        val change = changeSetSupport.createChange("renameTable") as RenameTableChange
        change.catalogName = catalogName
        change.newTableName = newTableName
        change.oldTableName = oldTableName
        change.schemaName = schemaName
        changeSetSupport.addChange(change)
    }

    fun addColumn(
        catalogName: String? = null,
        schemaName: String? = null,
        tableName: String,
        block: AddColumnDsl.() -> Unit,
    ) {
        val change = changeSetSupport.createChange("addColumn") as AddColumnChange
        change.catalogName = catalogName
        change.schemaName = schemaName
        change.tableName = tableName
        val dsl = AddColumnDsl(changeLog)
        change.columns = wrapChangeLogParseException { dsl(block) }
        changeSetSupport.addChange(change)
    }

    fun dropColumn(
        catalogName: String? = null,
        columnName: String? = null,
        schemaName: String? = null,
        tableName: String,
        // Used when deleting multiple columns.
        block: (ColumnDsl.() -> Unit)? = null,
    ) {
        val change = changeSetSupport.createChange("dropColumn") as DropColumnChange
        change.catalogName = catalogName
        change.columnName = columnName
        change.schemaName = schemaName
        change.tableName = tableName
        block?.also {
            val dsl = ColumnDsl(changeLog)
            change.columns = wrapChangeLogParseException { dsl(block) }
        }
        changeSetSupport.addChange(change)
    }

    fun renameColumn(
        catalogName: String? = null,
        columnDataType: String? = null,
        newColumnName: String,
        oldColumnName: String,
        remarks: String? = null,
        schemaName: String? = null,
        tableName: String,
    ) {
        val change = changeSetSupport.createChange("renameColumn") as RenameColumnChange
        change.catalogName = catalogName
        change.columnDataType = columnDataType
        change.newColumnName = newColumnName
        change.oldColumnName = oldColumnName
        change.remarks = remarks
        change.schemaName = schemaName
        change.tableName = tableName
        changeSetSupport.addChange(change)
    }

    fun modifyDataType(
        catalogName: String? = null,
        columnName: String,
        newDataType: String,
        schemaName: String? = null,
        tableName: String,
    ) {
        val change = changeSetSupport.createChange("modifyDataType") as ModifyDataTypeChange
        change.catalogName = catalogName
        change.columnName = columnName
        change.newDataType = newDataType
        change.schemaName = schemaName
        change.tableName = tableName
        changeSetSupport.addChange(change)
    }

    fun setColumnRemarks(
        catalogName: String? = null,
        columnName: String,
        remarks: String,
        schemaName: String? = null,
        tableName: String,
        columnDataType: String? = null,
        columnParentType: String? = null,
    ) {
        val change = changeSetSupport.createChange("setColumnRemarks") as SetColumnRemarksChange
        change.catalogName = catalogName
        change.columnName = columnName
        change.remarks = remarks
        change.schemaName = schemaName
        change.tableName = tableName
        change.columnDataType = columnDataType
        change.columnParentType = columnParentType
        changeSetSupport.addChange(change)
    }

    fun addAutoIncrement(
        catalogName: String? = null,
        columnDataType: String? = null,
        columnName: String,
        defaultOnNull: Boolean? = null,
        generationType: String? = null,
        incrementBy: Long? = null,
        schemaName: String? = null,
        startWith: Long? = null,
        tableName: String,
    ) {
        val change = changeSetSupport.createChange("addAutoIncrement") as AddAutoIncrementChange
        change.catalogName = catalogName
        change.columnDataType = columnDataType
        change.columnName = columnName
        change.defaultOnNull = defaultOnNull
        change.generationType = generationType
        change.incrementBy = incrementBy?.toBigInteger()
        change.schemaName = schemaName
        change.startWith = startWith?.toBigInteger()
        change.tableName = tableName
        changeSetSupport.addChange(change)
    }

    fun createIndex(
        associatedWith: String? = null,
        catalogName: String? = null,
        clustered: Boolean? = null,
        indexName: String? = null,
        schemaName: String? = null,
        tableName: String,
        tablespace: String? = null,
        unique: Boolean? = null,
        block: AddColumnDsl.() -> Unit,
    ) {
        val change = changeSetSupport.createChange("createIndex") as CreateIndexChange
        change.associatedWith = associatedWith
        change.catalogName = catalogName
        change.clustered = clustered
        change.indexName = indexName
        change.schemaName = schemaName
        change.tableName = tableName
        change.tablespace = tablespace
        change.isUnique = unique
        val dsl = AddColumnDsl(changeLog)
        change.columns = wrapChangeLogParseException { dsl(block) }
        changeSetSupport.addChange(change)
    }

    fun dropIndex(
        catalogName: String? = null,
        indexName: String,
        schemaName: String? = null,
        tableName: String? = null,
    ) {
        val change = changeSetSupport.createChange("dropIndex") as DropIndexChange
        change.catalogName = catalogName
        change.indexName = indexName
        change.schemaName = schemaName
        change.tableName = tableName
        changeSetSupport.addChange(change)
    }

    fun createView(
        catalogName: String? = null,
        encoding: String? = null,
        fullDefinition: Boolean? = null,
        path: String? = null,
        relativeToChangelogFile: Boolean? = null,
        remarks: String? = null,
        replaceIfExists: Boolean? = null,
        schemaName: String? = null,
        viewName: String,
        selectQuery: () -> String,
    ) {
        val change = changeSetSupport.createChange("createView") as CreateViewChange
        change.catalogName = catalogName
        change.encoding = encoding
        change.fullDefinition = fullDefinition
        change.path = path
        change.relativeToChangelogFile = relativeToChangelogFile
        change.remarks = remarks
        change.replaceIfExists = replaceIfExists
        change.schemaName = schemaName
        change.selectQuery = selectQuery().evalExpressions(changeLog)
        change.viewName = viewName
        changeSetSupport.addChange(change)
    }

    fun dropView(
        catalogName: String? = null,
        ifExists: Boolean? = null,
        schemaName: String? = null,
        viewName: String,
    ) {
        val change = changeSetSupport.createChange("dropView") as DropViewChange
        change.catalogName = catalogName
        change.isIfExists = ifExists
        change.schemaName = schemaName
        change.viewName = viewName
        changeSetSupport.addChange(change)
    }

    fun renameView(
        catalogName: String? = null,
        newViewName: String,
        oldViewName: String,
        schemaName: String? = null,
    ) {
        val change = changeSetSupport.createChange("renameView") as RenameViewChange
        change.catalogName = catalogName
        change.newViewName = newViewName
        change.oldViewName = oldViewName
        change.schemaName = schemaName
        changeSetSupport.addChange(change)
    }

    fun createProcedure(
        catalogName: String? = null,
        dbms: String? = null,
        encoding: String? = null,
        path: String,
        procedureName: String? = null,
        relativeToChangelogFile: Boolean? = null,
        replaceIfExists: Boolean? = null,
        schemaName: String? = null,
        procedureText: () -> String,
    ) {
        val change = changeSetSupport.createChange("createProcedure") as CreateProcedureChange
        change.catalogName = catalogName
        change.dbms = dbms
        change.encoding = encoding
        change.path = path
        change.procedureText = procedureText().evalExpressions(changeLog)
        change.procedureName = procedureName
        change.isRelativeToChangelogFile = relativeToChangelogFile
        change.replaceIfExists = replaceIfExists
        change.schemaName = schemaName
        changeSetSupport.addChange(change)
    }

    fun dropProcedure(
        catalogName: String? = null,
        procedureName: String,
        schemaName: String? = null,
    ) {
        val change = changeSetSupport.createChange("dropProcedure") as DropProcedureChange
        change.catalogName = catalogName
        change.procedureName = procedureName
        change.schemaName = schemaName
        changeSetSupport.addChange(change)
    }

    fun createSequence(
        cacheSize: Long? = null,
        catalogName: String? = null,
        cycle: Boolean? = null,
        dataType: String? = null,
        incrementBy: Long? = null,
        maxValue: Long? = null,
        minValue: Long? = null,
        ordered: Boolean? = null,
        schemaName: String? = null,
        sequenceName: String,
        startValue: Long? = null,
    ) {
        val change = changeSetSupport.createChange("createSequence") as CreateSequenceChange
        change.cacheSize = cacheSize?.toBigInteger()
        change.catalogName = catalogName
        change.cycle = cycle
        change.dataType = dataType
        change.incrementBy = incrementBy?.toBigInteger()
        change.maxValue = maxValue?.toBigInteger()
        change.minValue = minValue?.toBigInteger()
        change.isOrdered = ordered
        change.schemaName = schemaName
        change.sequenceName = sequenceName
        change.startValue = startValue?.toBigInteger()
        changeSetSupport.addChange(change)
    }

    fun dropSequence(
        catalogName: String? = null,
        schemaName: String? = null,
        sequenceName: String,
    ) {
        val change = changeSetSupport.createChange("dropSequence") as DropSequenceChange
        change.catalogName = catalogName
        change.schemaName = schemaName
        change.sequenceName = sequenceName
        changeSetSupport.addChange(change)
    }

    fun renameSequence(
        catalogName: String? = null,
        newSequenceName: String,
        oldSequenceName: String,
        schemaName: String? = null,
    ) {
        val change = changeSetSupport.createChange("renameSequence") as RenameSequenceChange
        change.catalogName = catalogName
        change.newSequenceName = newSequenceName
        change.oldSequenceName = oldSequenceName
        change.schemaName = schemaName
        changeSetSupport.addChange(change)
    }

    fun alterSequence(
        cacheSize: String? = null,
        catalogName: String? = null,
        cycle: Boolean? = null,
        dataType: String? = null,
        incrementBy: Long? = null,
        maxValue: String? = null,
        minValue: String? = null,
        ordered: Boolean? = null,
        schemaName: String? = null,
        sequenceName: String,
    ) {
        val change = changeSetSupport.createChange("alterSequence") as AlterSequenceChange
        change.cacheSize = cacheSize?.toBigInteger()
        change.catalogName = catalogName
        change.cycle = cycle
        change.dataType = dataType
        change.incrementBy = incrementBy?.toBigInteger()
        change.maxValue = maxValue?.toBigInteger()
        change.minValue = minValue?.toBigInteger()
        change.isOrdered = ordered
        change.schemaName = schemaName
        change.sequenceName = sequenceName
        changeSetSupport.addChange(change)
    }

    // for pro
//    fun createFunction(
//        catalogName: String? = null,
//        dbms: String? = null,
//        encoding: String? = null,
//        functionBody: String,
//        functionName: String,
//        path: String,
//        procedureText: String,
//        relativeToChangelogFile: Boolean? = null,
//        replaceIfExists: Boolean? = null,
//        schemaName: String? = null,
//    ) {
//        val change = changeSetSupport.createChange("createFunction") as CreateFunctionChange
//        change.catalogName = catalogName
//        change.dbms = dbms
//        change.encoding = encoding
//        change.functionBody = functionBody
//        change.functionName = functionName
//        change.path = path
//        change.procedureText = procedureText
//        change.isRelativeToChangelogFile = relativeToChangelogFile
//        change.replaceIfExists = replaceIfExists
//        change.schemaName = schemaName
//        changeSetSupport.addChange(change)
//    }
//
//    fun dropFunction(
//        catalogName: String? = null,
//        functionName: String,
//        schemaName: String? = null,
//    ) {
//        val change = changeSetSupport.createChange("dropFunction") as DropFunctionChange
//        change.catalogName = catalogName
//        change.functionName = functionName
//        change.schemaName = schemaName
//        changeSetSupport.addChange(change)
//    }
//
//    fun createPackage(
//        catalogName: String? = null,
//        dbms: String? = null,
//        encoding: String? = null,
//        packageName: String,
//        packageText: String,
//        path: String,
//        procedureText: String,
//        relativeToChangelogFile: Boolean? = null,
//        replaceIfExists: Boolean? = null,
//        schemaName: String? = null,
//    ) {
//        val change = changeSetSupport.createChange("createPackage") as CreatePackageChange
//        change.catalogName = catalogName
//        change.dbms = dbms
//        change.encoding = encoding
//        change.packageName = packageName
//        change.packageText = packageText
//        change.path = path
//        change.procedureText = procedureText
//        change.isRelativeToChangelogFile = relativeToChangelogFile
//        change.replaceIfExists = replaceIfExists
//        change.schemaName = schemaName
//        changeSetSupport.addChange(change)
//    }
//
//    fun createPackageBody(
//        catalogName: String? = null,
//        dbms: String? = null,
//        encoding: String? = null,
//        packageBodyName: String,
//        packageBodyText: String,
//        path: String,
//        procedureText: String,
//        relativeToChangelogFile: Boolean? = null,
//        replaceIfExists: Boolean? = null,
//        schemaName: String? = null,
//    ) {
//        val change = changeSetSupport.createChange("createPackageBody") as CreatePackageBodyChange
//        change.catalogName = catalogName
//        change.dbms = dbms
//        change.encoding = encoding
//        change.packageBodyName = packageBodyName
//        change.packageBodyText = packageBodyText
//        change.path = path
//        change.procedureText = procedureText
//        change.isRelativeToChangelogFile = relativeToChangelogFile
//        change.replaceIfExists = replaceIfExists
//        change.schemaName = schemaName
//        changeSetSupport.addChange(change)
//    }
//
//    fun dropPackage(
//        catalogName: String? = null,
//        packageName: String,
//        schemaName: String? = null,
//    ) {
//        val change = changeSetSupport.createChange("dropPackage") as DropPackageChange
//        change.catalogName = catalogName
//        change.packageName = packageName
//        change.schemaName = schemaName
//        changeSetSupport.addChange(change)
//    }
//
//    fun dropPackageBody(
//        catalogName: String? = null,
//        packageBodyName: String,
//        schemaName: String? = null,
//    ) {
//        val change = changeSetSupport.createChange("dropPackageBody") as DropPackageBodyChange
//        change.catalogName = catalogName
//        change.packageBodyName = packageBodyName
//        change.schemaName = schemaName
//        changeSetSupport.addChange(change)
//    }
//
//    fun createSynonym(
//        objectCatalogName: String? = null,
//        objectName: String,
//        objectSchemaName: String? = null,
//        objectType: String? = null,
//        private: String? = null,
//        replaceIfExists: Boolean? = null,
//        synonymCatalogName: String? = null,
//        synonymName: String,
//        synonymSchemaName: String? = null,
//    ) {
//        val change = changeSetSupport.createChange("createSynonym") as CreateSynonymChange
//        change.objectCatalogName = objectCatalogName
//        change.objectName = objectName
//        change.objectSchemaName = objectSchemaName
//        change.objectType = objectType
//        change.private = private
//        change.replaceIfExists = replaceIfExists
//        change.synonymCatalogName = synonymCatalogName
//        change.synonymName = synonymName
//        change.synonymSchemaName = synonymSchemaName
//        changeSetSupport.addChange(change)
//    }
//
//    fun dropSynonym(
//        objectType: String? = null,
//        private: String? = null,
//        synonymCatalogName: String? = null,
//        synonymName: String,
//        synonymSchemaName: String? = null,
//    ) {
//        val change = changeSetSupport.createChange("dropSynonym") as DropSynonymChange
//        change.objectType = objectType
//        change.private = private
//        change.synonymCatalogName = synonymCatalogName
//        change.synonymName = synonymName
//        change.synonymSchemaName = synonymSchemaName
//        changeSetSupport.addChange(change)
//    }
//
//    fun createTrigger(
//        catalogName: String? = null,
//        dbms: String? = null,
//        disabled: Boolean? = null,
//        encoding: String? = null,
//        path: String,
//        procedureText: String,
//        relativeToChangelogFile: Boolean? = null,
//        replaceIfExists: Boolean? = null,
//        schemaName: String? = null,
//        scope: String? = null,
//        tableName: String? = null,
//        triggerBody: String,
//        triggerName: String,
//    ) {
//        val change = changeSetSupport.createChange("createTrigger") as CreateTriggerChange
//        change.catalogName = catalogName
//        change.dbms = dbms
//        change.disabled = disabled
//        change.encoding = encoding
//        change.path = path
//        change.procedureText = procedureText
//        change.isRelativeToChangelogFile = relativeToChangelogFile
//        change.replaceIfExists = replaceIfExists
//        change.schemaName = schemaName
//        change.scope = scope
//        change.tableName = tableName
//        change.triggerBody = triggerBody
//        change.triggerName = triggerName
//        changeSetSupport.addChange(change)
//    }
//
//    fun enableTrigger(
//        catalogName: String? = null,
//        schemaName: String? = null,
//        scope: String? = null,
//        tableName: String? = null,
//        triggerName: String,
//    ) {
//        val change = changeSetSupport.createChange("enableTrigger") as EnableTriggerChange
//        change.catalogName = catalogName
//        change.schemaName = schemaName
//        change.scope = scope
//        change.tableName = tableName
//        change.triggerName = triggerName
//        changeSetSupport.addChange(change)
//    }
//
//    fun dropTrigger(
//        catalogName: String? = null,
//        schemaName: String? = null,
//        scope: String? = null,
//        tableName: String? = null,
//        triggerName: String,
//    ) {
//        val change = changeSetSupport.createChange("dropTrigger") as DropTriggerChange
//        change.catalogName = catalogName
//        change.schemaName = schemaName
//        change.scope = scope
//        change.tableName = tableName
//        change.triggerName = triggerName
//        changeSetSupport.addChange(change)
//    }
//
//    fun disableTrigger(
//        catalogName: String? = null,
//        schemaName: String? = null,
//        scope: String? = null,
//        tableName: String? = null,
//        triggerName: String,
//    ) {
//        val change = changeSetSupport.createChange("disableTrigger") as DisableTriggerChange
//        change.catalogName = catalogName
//        change.schemaName = schemaName
//        change.scope = scope
//        change.tableName = tableName
//        change.triggerName = triggerName
//        changeSetSupport.addChange(change)
//    }
//
//    fun renameTrigger(
//        catalogName: String? = null,
//        newTriggerName: String,
//        oldTriggerName: String,
//        schemaName: String? = null,
//        tableName: String? = null,
//    ) {
//        val change = changeSetSupport.createChange("renameTrigger") as RenameTriggerChange
//        change.catalogName = catalogName
//        change.newTriggerName = newTriggerName
//        change.oldTriggerName = oldTriggerName
//        change.schemaName = schemaName
//        change.tableName = tableName
//        changeSetSupport.addChange(change)
//    }
//
//    // Constraints
//    fun addCheckConstraint(
//        catalogName: String? = null,
//        constraintBody: String,
//        constraintName: String,
//        disabled: Boolean? = null,
//        schemaName: String? = null,
//        tableName: String,
//        validate: Boolean? = null,
//    ) {
//        val change = changeSetSupport.createChange("addCheckConstraint") as AddCheckConstraintChange
//        change.catalogName = catalogName
//        change.constraintBody = constraintBody
//        change.constraintName = constraintName
//        change.disabled = disabled
//        change.schemaName = schemaName
//        change.tableName = tableName
//        change.validate = validate
//        changeSetSupport.addChange(change)
//    }
//
//    fun enableCheckConstraint(
//        catalogName: String? = null,
//        constraintName: String,
//        schemaName: String? = null,
//        tableName: String,
//    ) {
//        val change = changeSetSupport.createChange("enableCheckConstraint") as EnableCheckConstraintChange
//        change.catalogName = catalogName
//        change.constraintName = constraintName
//        change.schemaName = schemaName
//        change.tableName = tableName
//        changeSetSupport.addChange(change)
//    }
//
//    fun dropCheckConstraint(
//        catalogName: String? = null,
//        constraintName: String,
//        schemaName: String? = null,
//        tableName: String,
//    ) {
//        val change = changeSetSupport.createChange("dropCheckConstraint") as DropCheckConstraintChange
//        change.catalogName = catalogName
//        change.constraintName = constraintName
//        change.schemaName = schemaName
//        change.tableName = tableName
//        changeSetSupport.addChange(change)
//    }
//
//    fun disableCheckConstraint(
//        catalogName: String? = null,
//        constraintName: String,
//        schemaName: String? = null,
//        tableName: String,
//    ) {
//        val change = changeSetSupport.createChange("disableCheckConstraint") as DisableCheckConstraintChange
//        change.catalogName = catalogName
//        change.constraintName = constraintName
//        change.schemaName = schemaName
//        change.tableName = tableName
//        changeSetSupport.addChange(change)
//    }

    fun addDefaultValue(
        catalogName: String? = null,
        columnDataType: String? = null,
        columnName: String,
        defaultValue: String? = null,
        defaultValueBoolean: Boolean? = null,
        defaultValueComputed: String? = null,
        defaultValueConstraintName: String? = null,
        defaultValueDate: String? = null,
        defaultValueNumeric: String? = null,
        defaultValueSequenceNext: String? = null,
        schemaName: String? = null,
        tableName: String,
    ) {
        val change = changeSetSupport.createChange("addDefaultValue") as AddDefaultValueChange
        change.catalogName = catalogName
        change.columnDataType = columnDataType
        change.columnName = columnName
        change.defaultValue = defaultValue
        change.defaultValueBoolean = defaultValueBoolean
        defaultValueComputed?.also { change.defaultValueComputed = DatabaseFunction(it) }
        change.defaultValueConstraintName = defaultValueConstraintName
        change.defaultValueDate = defaultValueDate
        change.defaultValueNumeric = defaultValueNumeric
        defaultValueSequenceNext?.also { change.defaultValueSequenceNext = SequenceNextValueFunction(it) }
        change.schemaName = schemaName
        change.tableName = tableName
        changeSetSupport.addChange(change)
    }

    fun dropDefaultValue(
        catalogName: String? = null,
        columnDataType: String? = null,
        columnName: String,
        schemaName: String? = null,
        tableName: String,
    ) {
        val change = changeSetSupport.createChange("dropDefaultValue") as DropDefaultValueChange
        change.catalogName = catalogName
        change.columnDataType = columnDataType
        change.columnName = columnName
        change.schemaName = schemaName
        change.tableName = tableName
        changeSetSupport.addChange(change)
    }

    fun addForeignKeyConstraint(
        baseColumnNames: String,
        baseTableCatalogName: String? = null,
        baseTableName: String,
        baseTableSchemaName: String? = null,
        constraintName: String,
        deferrable: Boolean? = null,
        initiallyDeferred: Boolean? = null,
        onDelete: String? = null,
        onUpdate: String? = null,
        referencedColumnNames: String,
        referencedTableCatalogName: String? = null,
        referencedTableName: String,
        referencedTableSchemaName: String? = null,
        validate: Boolean? = null,
        referencesUniqueColumn: Boolean? = null,
    ) {
        val change = changeSetSupport.createChange("addForeignKeyConstraint") as AddForeignKeyConstraintChange
        change.baseColumnNames = baseColumnNames
        change.baseTableCatalogName = baseTableCatalogName
        change.baseTableName = baseTableName
        change.baseTableSchemaName = baseTableSchemaName
        change.constraintName = constraintName
        change.deferrable = deferrable
        change.initiallyDeferred = initiallyDeferred
        change.onDelete = onDelete
        change.onUpdate = onUpdate
        change.referencedColumnNames = referencedColumnNames
        change.referencedTableCatalogName = referencedTableCatalogName
        change.referencedTableName = referencedTableName
        change.referencedTableSchemaName = referencedTableSchemaName
        change.validate = validate
        referencesUniqueColumn?.also {
            log.warning("referencesUniqueColumn is deprecated")
        }
        change.referencesUniqueColumn = referencesUniqueColumn
        changeSetSupport.addChange(change)
    }

    fun dropForeignKeyConstraint(
        baseTableCatalogName: String? = null,
        baseTableName: String,
        baseTableSchemaName: String? = null,
        constraintName: String,
    ) {
        val change = changeSetSupport.createChange("dropForeignKeyConstraint") as DropForeignKeyConstraintChange
        change.baseTableCatalogName = baseTableCatalogName
        change.baseTableName = baseTableName
        change.baseTableSchemaName = baseTableSchemaName
        change.constraintName = constraintName
        changeSetSupport.addChange(change)
    }

    fun dropAllForeignKeyConstraints(
        baseTableCatalogName: String? = null,
        baseTableName: String,
        baseTableSchemaName: String? = null,
    ) {
        val change = changeSetSupport.createChange("dropAllForeignKeyConstraints") as DropAllForeignKeyConstraintsChange
        change.baseTableCatalogName = baseTableCatalogName
        change.baseTableName = baseTableName
        change.baseTableSchemaName = baseTableSchemaName
        changeSetSupport.addChange(change)
    }

    fun addNotNullConstraint(
        catalogName: String? = null,
        columnDataType: String? = null,
        columnName: String,
        constraintName: String? = null,
        defaultNullValue: String? = null,
        schemaName: String? = null,
        tableName: String,
        validate: Boolean? = null,
    ) {
        val change = changeSetSupport.createChange("addNotNullConstraint") as AddNotNullConstraintChange
        change.catalogName = catalogName
        change.columnDataType = columnDataType
        change.columnName = columnName
        change.constraintName = constraintName
        change.defaultNullValue = defaultNullValue
        change.schemaName = schemaName
        change.tableName = tableName
        change.validate = validate
        changeSetSupport.addChange(change)
    }

    fun dropNotNullConstraint(
        catalogName: String? = null,
        columnDataType: String? = null,
        columnName: String,
        constraintName: String? = null,
        schemaName: String? = null,
        tableName: String,
    ) {
        val change = changeSetSupport.createChange("dropNotNullConstraint") as DropNotNullConstraintChange
        change.catalogName = catalogName
        change.columnDataType = columnDataType
        change.columnName = columnName
        change.constraintName = constraintName
        change.schemaName = schemaName
        change.tableName = tableName
        changeSetSupport.addChange(change)
    }

    fun addPrimaryKey(
        catalogName: String? = null,
        clustered: Boolean? = null,
        columnNames: String,
        constraintName: String? = null,
        forIndexCatalogName: String? = null,
        forIndexName: String? = null,
        forIndexSchemaName: String? = null,
        schemaName: String? = null,
        tableName: String,
        tablespace: String? = null,
        validate: Boolean? = null,
    ) {
        val change = changeSetSupport.createChange("addPrimaryKey") as AddPrimaryKeyChange
        change.catalogName = catalogName
        change.clustered = clustered
        change.columnNames = columnNames
        change.constraintName = constraintName
        change.forIndexCatalogName = forIndexCatalogName
        change.forIndexName = forIndexName
        change.forIndexSchemaName = forIndexSchemaName
        change.schemaName = schemaName
        change.tableName = tableName
        change.tablespace = tablespace
        change.validate = validate
        changeSetSupport.addChange(change)
    }

    fun dropPrimaryKey(
        catalogName: String? = null,
        constraintName: String? = null,
        dropIndex: Boolean? = null,
        schemaName: String? = null,
        tableName: String,
    ) {
        val change = changeSetSupport.createChange("dropPrimaryKey") as DropPrimaryKeyChange
        change.catalogName = catalogName
        change.constraintName = constraintName
        change.dropIndex = dropIndex
        change.schemaName = schemaName
        change.tableName = tableName
        changeSetSupport.addChange(change)
    }

    fun addUniqueConstraint(
        catalogName: String? = null,
        clustered: Boolean? = null,
        columnNames: String,
        constraintName: String? = null,
        deferrable: Boolean? = null,
        disabled: Boolean? = null,
        forIndexCatalogName: String? = null,
        forIndexName: String? = null,
        forIndexSchemaName: String? = null,
        initiallyDeferred: Boolean? = null,
        schemaName: String? = null,
        tableName: String,
        tablespace: String? = null,
        validate: Boolean? = null,
    ) {
        val change = changeSetSupport.createChange("addUniqueConstraint") as AddUniqueConstraintChange
        change.catalogName = catalogName
        change.clustered = clustered
        change.columnNames = columnNames
        change.constraintName = constraintName
        change.deferrable = deferrable
        change.disabled = disabled
        change.forIndexCatalogName = forIndexCatalogName
        change.forIndexName = forIndexName
        change.forIndexSchemaName = forIndexSchemaName
        change.initiallyDeferred = initiallyDeferred
        change.schemaName = schemaName
        change.tableName = tableName
        change.tablespace = tablespace
        change.validate = validate
        changeSetSupport.addChange(change)
    }

    fun dropUniqueConstraint(
        catalogName: String? = null,
        constraintName: String,
        schemaName: String? = null,
        tableName: String,
        uniqueColumns: String? = null,
    ) {
        val change = changeSetSupport.createChange("dropUniqueConstraint") as DropUniqueConstraintChange
        change.catalogName = catalogName
        change.constraintName = constraintName
        change.schemaName = schemaName
        change.tableName = tableName
        change.uniqueColumns = uniqueColumns
        changeSetSupport.addChange(change)
    }

    // Data
    fun addLookupTable(
        constraintName: String? = null,
        existingColumnName: String,
        existingTableCatalogName: String? = null,
        existingTableName: String,
        existingTableSchemaName: String? = null,
        newColumnDataType: String? = null,
        newColumnName: String,
        newTableCatalogName: String? = null,
        newTableName: String,
        newTableSchemaName: String? = null,
    ) {
        val change = changeSetSupport.createChange("addLookupTable") as AddLookupTableChange
        change.constraintName = constraintName
        change.existingColumnName = existingColumnName
        change.existingTableCatalogName = existingTableCatalogName
        change.existingTableName = existingTableName
        change.existingTableSchemaName = existingTableSchemaName
        change.newColumnDataType = newColumnDataType
        change.newColumnName = newColumnName
        change.newTableCatalogName = newTableCatalogName
        change.newTableName = newTableName
        change.newTableSchemaName = newTableSchemaName
        changeSetSupport.addChange(change)
    }

    fun delete(
        catalogName: String? = null,
        schemaName: String? = null,
        tableName: String,
        block: ModifyDataColumnDsl.() -> Unit,
    ) {
        val change = changeSetSupport.createChange("delete") as DeleteDataChange
        change.catalogName = catalogName
        change.schemaName = schemaName
        change.tableName = tableName
        val dsl = ModifyDataColumnDsl(changeLog)
        val dslResult = wrapChangeLogParseException { dsl(block) }
        if (dslResult.columns.isNotEmpty()) {
            throw ChangeLogParseException(
                "delete type is not allow columns"
            )
        }
        change.where = dslResult.where
        dslResult.params.forEach {
            change.addWhereParam(it)
        }
        changeSetSupport.addChange(change)
    }

    fun insert(
        catalogName: String? = null,
        dbms: String? = null,
        schemaName: String? = null,
        tableName: String,
        block: ColumnDsl.() -> Unit,
    ) {
        val change = changeSetSupport.createChange("insert") as InsertDataChange
        change.catalogName = catalogName
        change.dbms = dbms
        change.schemaName = schemaName
        change.tableName = tableName
        val dsl = ColumnDsl(changeLog)
        change.columns = wrapChangeLogParseException { dsl(block) }
        changeSetSupport.addChange(change)
    }

    fun loadData(
        catalogName: String? = null,
        commentLineStartsWith: String? = null,
        encoding: String? = null,
        file: String,
        quotchar: String? = null,
        relativeToChangelogFile: Boolean? = null,
        schemaName: String? = null,
        separator: String? = null,
        tableName: String,
        usePreparedStatements: Boolean? = null,
        block: LoadDataColumnDsl.() -> Unit,
    ) {
        val change = changeSetSupport.createChange("loadData") as LoadDataChange
        change.catalogName = catalogName
        change.commentLineStartsWith = commentLineStartsWith
        change.encoding = encoding
        change.file = file
        change.quotchar = quotchar
        change.isRelativeToChangelogFile = relativeToChangelogFile
        change.schemaName = schemaName
        change.separator = separator
        change.tableName = tableName
        change.usePreparedStatements = usePreparedStatements

        val dsl = LoadDataColumnDsl(changeLog)
        val columns = wrapChangeLogParseException { dsl(block) }
        change.columns = columns

        changeSetSupport.addChange(change)
    }

    fun loadUpdateData(
        catalogName: String? = null,
        commentLineStartsWith: String? = null,
        encoding: String? = null,
        file: String,
        onlyUpdate: Boolean? = null,
        primaryKey: String,
        quotchar: String? = null,
        relativeToChangelogFile: Boolean? = null,
        schemaName: String? = null,
        separator: String? = null,
        tableName: String,
        usePreparedStatements: Boolean? = null,
        block: LoadDataColumnDsl.() -> Unit,
    ) {
        val change = changeSetSupport.createChange("loadUpdateData") as LoadUpdateDataChange
        change.catalogName = catalogName
        change.commentLineStartsWith = commentLineStartsWith
        change.encoding = encoding
        change.file = file
        change.onlyUpdate = onlyUpdate
        change.primaryKey = primaryKey
        change.quotchar = quotchar
        change.isRelativeToChangelogFile = relativeToChangelogFile
        change.schemaName = schemaName
        change.separator = separator
        change.tableName = tableName
        change.usePreparedStatements = usePreparedStatements

        val dsl = LoadDataColumnDsl(changeLog)
        val columns = wrapChangeLogParseException { dsl(block) }
        change.columns = columns

        changeSetSupport.addChange(change)
    }

    fun mergeColumns(
        catalogName: String? = null,
        column1Name: String,
        column2Name: String,
        finalColumnName: String,
        finalColumnType: String,
        joinString: String? = null,
        schemaName: String? = null,
        tableName: String,
    ) {
        val change = changeSetSupport.createChange("mergeColumns") as MergeColumnChange
        change.catalogName = catalogName
        change.column1Name = column1Name
        change.column2Name = column2Name
        change.finalColumnName = finalColumnName
        change.finalColumnType = finalColumnType
        change.joinString = joinString
        change.schemaName = schemaName
        change.tableName = tableName
        changeSetSupport.addChange(change)
    }

    /**
     * https://docs.liquibase.com/change-types/modify-sql.html
     */
    fun modifySql(
        dbms: String? = null,
        contextFilter: String? = null,
        context: String? = null,
        applyToRollback: Boolean? = false,
        block: ModifySqlDsl.() -> Unit,
    ) {
        val dsl =
            ModifySqlDsl.build(
                changeLog = changeLog,
                dbms = dbms,
                contextFilter = contextFilter ?: context,
                applyToRollback = applyToRollback,
            )
        val sqlVisitors = wrapChangeLogParseException { dsl(block) }
        sqlVisitors.forEach {
            this.context.changeSet.addSqlVisitor(it)
        }
    }

    fun update(
        catalogName: String? = null,
        schemaName: String? = null,
        tableName: String,
        block: ModifyDataColumnDsl.() -> Unit,
    ) {
        val change = changeSetSupport.createChange("update") as UpdateDataChange
        change.catalogName = catalogName
        change.schemaName = schemaName
        change.tableName = tableName
        val dsl = ModifyDataColumnDsl(changeLog)
        val dslResult = wrapChangeLogParseException { dsl(block) }
        change.columns = dslResult.columns
        change.where = dslResult.where
        dslResult.params.forEach {
            change.addWhereParam(it)
        }
        changeSetSupport.addChange(change)
    }

    // Miscellaneous

    /**
     * Applies a custom-change to the change set.
     * Class is requiring implements the [liquibase.change.custom.CustomChange]
     * [official-document](https://docs.liquibase.com/change-types/custom-change.html)
     *
     * @param `class` specify KClass or Class<*> of CustomChange or className of CustomChange.
     * @param block Key-value to be given to CustomChange.
     */
    fun customChange(
        @Suppress("FunctionParameterNaming")
        `class`: Any,
        block: (KeyValueDsl.() -> Unit)? = null,
    ) {
        val change = changeSetSupport.createChange("customChange") as CustomChangeWrapper
        change.setClass(`class`.evalClassNameExpressions(changeLog))
        block?.let {
            val dsl = KeyValueDsl(changeLog)
            val map = wrapChangeLogParseException { dsl(block) }
            map.forEach { (key, value) ->
                change.setParam(key, value?.tryEvalExpressions(changeLog)?.toString())
            }
        }
        changeSetSupport.addChange(change)
    }

    fun empty() = Unit

    /**
     * Executes a shell command with the provided executable and optional parameters.
     * [official-document](https://docs.liquibase.com/change-types/execute-command.html)
     *
     * @param executable The command or executable to run. Required.
     * @param os execute codntion by os. get os by Java system property the "os.name".
     * @param timeout The maximum amount of time the command is allowed to run.
     * @param block arguments for executable.
     */
    fun executeCommand(
        executable: String,
        os: String? = null,
        timeout: String? = null,
        block: (ArgumentDsl.() -> Unit)? = null,
    ) {
        val change = changeSetSupport.createChange("executeCommand") as ExecuteShellCommandChange
        change.executable = executable
        change.setOs(os)
        change.timeout = timeout
        block?.also {
            val dsl = ArgumentDsl(changeLog)
            val args = wrapChangeLogParseException { dsl(block) }
            args.forEach {
                change.addArg(it.tryEvalExpressions(changeLog).toString())
            }
        }
        changeSetSupport.addChange(change)
    }

    // for pro
//    fun markUnused(
//        catalogName: String? = null,
//        columnName: String,
//        schemaName: String? = null,
//        tableName: String,
//    ) {
//        val change = changeSetSupport.createChange("markUnused") as MarkUnusedChange
//        change.catalogName = catalogName
//        change.columnName = columnName
//        change.schemaName = schemaName
//        change.tableName = tableName
//        changeSetSupport.addChange(change)
//    }

    /**
     * Outputs a message to the specified target.
     * [official-document](https://docs.liquibase.com/change-types/output.html)
     *
     * @param message message to be outputted.
     * @param target output target. STDOUT, STDERR, FATAL, WARN, INFO, DEBUG. default target is "STDERR".
     */
    fun output(
        message: String? = null,
        target: String? = null,
    ) {
        val change = changeSetSupport.createChange("output") as OutputChange
        change.message = message
        change.target = target ?: "STDERR"
        changeSetSupport.addChange(change)
    }

    /**
     * Executes a raw SQL change.
     * [official-document](https://docs.liquibase.com/change-types/sql.html)
     *
     * @param dbms The type of database. [database-type](https://docs.liquibase.com/start/tutorials/home.html)
     * @param endDelimiter The delimiter for the end of the SQL statement. Default is ";".
     * @param splitStatements Whether to split the SQL statements. Default is true.
     * @param stripComments Whether to strip comments from the SQL. Default is true.
     * @param block The DSL-block that returns SQL strings
     */
    fun sql(
        dbms: String? = null,
        endDelimiter: String? = null,
        splitStatements: Boolean? = null,
        stripComments: Boolean? = null,
        block: SqlBlockDsl.() -> String,
    ) {
        val change = changeSetSupport.createChange("sql") as RawSQLChange
        change.dbms = dbms
        change.endDelimiter = endDelimiter
        change.isSplitStatements = splitStatements
        change.isStripComments = stripComments
        val dsl = SqlBlockDsl(changeLog)
        val commentDslResult = wrapChangeLogParseException { dsl(block) }
        change.sql = commentDslResult.sql.evalExpressions(changeLog)
        change.comment = commentDslResult.comment?.evalExpressions(changeLog)
        changeSetSupport.addChange(change)
    }

    /**
     * Executes a raw SQL change.
     * [official-document](https://docs.liquibase.com/change-types/sql.html)
     *
     * @param sql The SQL.required.
     * @param dbms The type of database. [database-type](https://docs.liquibase.com/start/tutorials/home.html)
     * @param endDelimiter The delimiter for the end of the SQL statement. Default is ";".
     * @param splitStatements Whether to split the SQL statements. Default is true.
     * @param stripComments Whether to strip comments from the SQL. Default is true.
     * @param comment An optional comment for the SQL change. Default is null.
     */
    fun sql(
        @Language("sql") sql: String,
        dbms: String? = null,
        endDelimiter: String? = null,
        splitStatements: Boolean? = null,
        stripComments: Boolean? = null,
        comment: String? = null,
    ) {
        sql(
            dbms = dbms,
            endDelimiter = endDelimiter,
            splitStatements = splitStatements,
            stripComments = stripComments,
        ) {
            if (comment != null) {
                comment(comment)
            }
            sql
        }
    }

    /**
     * Executes an SQL file as part of a change set.
     * [official-document](https://docs.liquibase.com/change-types/sql-file.html)
     *
     * @param dbms The type of database. [database-type](https://docs.liquibase.com/start/tutorials/home.html)
     * @param encoding The encoding of the SQL file. If null, the default system encoding will be used.
     * @param endDelimiter The delimiter for the end of the SQL statement. Default is ";".
     * @param path The path to the SQL file.
     * @param relativeToChangelogFile Specifies whether the path is relative to the changelog file. Defaults is false.
     * @param splitStatements Whether to split the SQL statements. Default is true.
     * @param stripComments Whether to strip comments from the SQL. Default is true.
     */
    fun sqlFile(
        dbms: String? = null,
        encoding: String? = null,
        endDelimiter: String? = null,
        path: String,
        relativeToChangelogFile: Boolean? = null,
        splitStatements: Boolean? = null,
        stripComments: Boolean? = null,
    ) {
        val change = changeSetSupport.createChange("sqlFile") as SQLFileChange
        change.dbms = dbms
        change.encoding = encoding
        change.endDelimiter = endDelimiter
        change.path = path
        change.isRelativeToChangelogFile = relativeToChangelogFile
        change.isSplitStatements = splitStatements
        change.isStripComments = stripComments
        changeSetSupport.addChange(change)
    }

    /**
     * Stops the current change process and logs an optional message.
     * This change is useful for debug or step update.
     * [official-document](https://docs.liquibase.com/change-types/stop.html)
     *
     * @param message output message when stop.
     */
    fun stop(message: String? = null) {
        val change = changeSetSupport.createChange("stop") as StopChange
        change.message = message?.evalExpressions(changeLog)
        changeSetSupport.addChange(change)
    }

    /**
     * We will tag the current state.
     * It will be used for rollback.
     * [official-document](https://docs.liquibase.com/commands/utility/tag.html)
     *
     * @param tag name of tag
     */
    fun tagDatabase(tag: String) {
        val change = changeSetSupport.createChange("tagDatabase") as TagDatabaseChange
        change.tag = tag.evalExpressions(changeLog)
        changeSetSupport.addChange(change)
    }

    private fun <E> wrapChangeLogParseException(
        block: () -> E
    ) = runCatching {
        block()
    }.fold(
        onSuccess = { it },
        onFailure = {
            throw ChangeLogParseException(
                "changeSetId: ${context.changeSet.id}. ${it.message}",
                it,
            )
        }
    )
}

data class ChangeSetContext(
    val changeSet: ChangeSet,
    val inRollback: Boolean = false,
) {
    fun <E> withChangeSetContext(block: ChangeSetContext.() -> E): E = this.block()
}
