@file:Suppress("ktlint:standard:no-consecutive-comments", "CyclomaticComplexMethod")

package momosetkn.liquibase.client

import liquibase.CatalogAndSchema
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Scope
import liquibase.UpdateSummaryEnum
import liquibase.UpdateSummaryOutputEnum
import liquibase.change.CheckSum
import liquibase.changelog.ChangeLogParameters
import liquibase.changelog.ChangeSet
import liquibase.changelog.ChangeSetStatus
import liquibase.changelog.DatabaseChangeLog
import liquibase.changelog.RanChangeSet
import liquibase.changelog.visitor.ChangeExecListener
import liquibase.changelog.visitor.DefaultChangeExecListener
import liquibase.command.CommandScope
import liquibase.command.core.GenerateChangelogCommandStep
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep
import liquibase.command.core.helpers.PreCompareCommandStep
import liquibase.command.core.helpers.ReferenceDbUrlConnectionCommandStep
import liquibase.database.Database
import liquibase.diff.DiffResult
import liquibase.diff.compare.CompareControl
import liquibase.diff.compare.CompareControl.SchemaComparison
import liquibase.diff.output.changelog.DiffToChangeLog
import liquibase.exception.CommandExecutionException
import liquibase.exception.DatabaseException
import liquibase.exception.LiquibaseException
import liquibase.lockservice.DatabaseChangeLogLock
import liquibase.resource.ResourceAccessor
import liquibase.snapshot.SnapshotControl
import liquibase.snapshot.SnapshotGeneratorFactory
import liquibase.structure.DatabaseObject
import momosetkn.liquibase.client.DateUtils.toJavaUtilDate
import java.io.PrintStream
import java.io.Writer
import java.time.LocalDateTime
import java.util.Arrays
import kotlin.reflect.KClass

@Suppress("TooManyFunctions", "LargeClass")
class LiquibaseClient(
    val changeLogFile: String,
    val database: Database,
    val resourceAccessor: ResourceAccessor = Scope.getCurrentScope().resourceAccessor,
    private val defaultOutputWriter: Writer = LiquibaseMultilineLogWriter(),
) : AutoCloseable {
    var diffTypes = listOf(
        "columns",
        "foreignkeys",
        "indexes",
        "primarykeys",
        "tables",
        "uniqueconstraints",
        "views"
    )

    val liquibase: ExtendedLiquibase = ExtendedLiquibase(
        changeLogFile = changeLogFile,
        database = database,
        resourceAccessor = resourceAccessor,
    )

    var showSummaryOutput: UpdateSummaryOutputEnum? = null
        set(value) {
            liquibase.setShowSummaryOutput(value)
            field = value
        }

    var showSummary: UpdateSummaryEnum? = null
        set(value) {
            liquibase.setShowSummary(value)
            field = value
        }

    val changeLogParameters: ChangeLogParameters?
        get() = liquibase.changeLogParameters

    var changeExecListener: ChangeExecListener? = null
        set(value) {
            liquibase.setChangeExecListener(value)
            field = value
        }

    val defaultChangeExecListener: DefaultChangeExecListener
        get() = liquibase.defaultChangeExecListener

    // comment-out. The following method is for the sole purpose of testing Liquibase.
//    fun getLog() {
//        this.liquibase.log
//    }

    @Throws(LiquibaseException::class)
    fun getDatabaseChangeLog(): DatabaseChangeLog {
        return this.liquibase.databaseChangeLog
    }

    @Throws(LiquibaseException::class)
    fun update() {
        @Suppress("ForbiddenComment")
        this.liquibase.update(
            Contexts(),
            LabelExpression(),
        )
    }

    @Throws(LiquibaseException::class)
    fun update(
        tag: String? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase.update(tag, Contexts(), LabelExpression())
        } else {
            this.liquibase.update(tag, Contexts(), LabelExpression(), output)
        }
    }

    @Throws(LiquibaseException::class)
    fun update(
        tag: String? = null,
        contexts: Contexts? = null,
        labelExpression: LabelExpression? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase.update(tag, contexts, labelExpression)
        } else {
            this.liquibase.update(tag, contexts, labelExpression, output)
        }
    }

    @Throws(LiquibaseException::class)
    fun update(
        tag: String? = null,
        contexts: String? = null,
        labelExpression: String? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase.update(tag, Contexts(contexts), LabelExpression(labelExpression))
        } else {
            this.liquibase.update(tag, Contexts(contexts), LabelExpression(labelExpression), output)
        }
    }

    @Throws(LiquibaseException::class)
    fun update(
        changesToApply: Int,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase.update(changesToApply, Contexts(), LabelExpression())
        } else {
            this.liquibase.update(changesToApply, Contexts(), LabelExpression(), output)
        }
    }

    @Throws(LiquibaseException::class)
    fun update(
        changesToApply: Int,
        contexts: Contexts? = null,
        labelExpression: LabelExpression? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase.update(changesToApply, contexts, labelExpression)
        } else {
            this.liquibase.update(changesToApply, contexts, labelExpression, output)
        }
    }

    @Throws(LiquibaseException::class)
    fun update(
        changesToApply: Int,
        contexts: String? = null,
        labelExpression: String? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase.update(changesToApply, Contexts(contexts), LabelExpression(labelExpression))
        } else {
            this.liquibase.update(changesToApply, Contexts(contexts), LabelExpression(labelExpression), output)
        }
    }

    @Throws(LiquibaseException::class)
    fun updateCountSql(
        count: Int,
        contexts: Contexts? = null,
        labelExpression: LabelExpression? = null,
        output: Writer = defaultOutputWriter,
    ) {
        return this.liquibase.updateCountSql(count, contexts, labelExpression, output)
    }

    @Throws(LiquibaseException::class)
    fun updateToTagSql(
        tag: String,
        contexts: Contexts? = null,
        labelExpression: LabelExpression? = null,
        output: Writer = defaultOutputWriter,
    ) {
        return this.liquibase.updateToTagSql(tag, contexts, labelExpression, output)
    }

    @Throws(LiquibaseException::class)
    fun rollback(
        changesToRollback: Int,
        rollbackScript: String? = null,
        contexts: Contexts? = null,
        labelExpression: LabelExpression? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase
                .rollback(changesToRollback, rollbackScript, contexts, labelExpression)
        } else {
            this.liquibase
                .rollback(changesToRollback, rollbackScript, contexts, labelExpression, output)
        }
    }

    @Throws(LiquibaseException::class)
    fun rollback(
        changesToRollback: Int,
        rollbackScript: String? = null,
        contexts: String? = null,
        labelExpression: String? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase
                .rollback(changesToRollback, rollbackScript, Contexts(contexts), LabelExpression(labelExpression))
        } else {
            this.liquibase
                .rollback(
                    changesToRollback,
                    rollbackScript,
                    Contexts(contexts),
                    LabelExpression(labelExpression),
                    output
                )
        }
    }

    @Throws(LiquibaseException::class)
    fun rollback(
        tagToRollBackTo: String,
        rollbackScript: String? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase
                .rollback(tagToRollBackTo, rollbackScript, Contexts(), LabelExpression())
        } else {
            this.liquibase
                .rollback(tagToRollBackTo, rollbackScript, Contexts(), LabelExpression(), output)
        }
    }

    @Throws(LiquibaseException::class)
    fun rollback(
        tagToRollBackTo: String,
        rollbackScript: String? = null,
        contexts: Contexts? = null,
        labelExpression: LabelExpression? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase
                .rollback(tagToRollBackTo, rollbackScript, contexts, labelExpression)
        } else {
            this.liquibase
                .rollback(tagToRollBackTo, rollbackScript, contexts, labelExpression, output)
        }
    }

    @Throws(LiquibaseException::class)
    fun rollback(
        tagToRollBackTo: String,
        rollbackScript: String? = null,
        contexts: String? = null,
        labelExpression: String? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase
                .rollback(tagToRollBackTo, rollbackScript, Contexts(contexts), LabelExpression(labelExpression))
        } else {
            this.liquibase
                .rollback(tagToRollBackTo, rollbackScript, Contexts(contexts), LabelExpression(labelExpression), output)
        }
    }

    @Throws(LiquibaseException::class)
    fun rollback(
        dateToRollBackTo: LocalDateTime,
        rollbackScript: String? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase
                .rollback(dateToRollBackTo.toJavaUtilDate(), rollbackScript, Contexts(), LabelExpression())
        } else {
            this.liquibase
                .rollback(dateToRollBackTo.toJavaUtilDate(), rollbackScript, Contexts(), LabelExpression(), output)
        }
    }

    @Throws(LiquibaseException::class)
    fun rollback(
        dateToRollBackTo: LocalDateTime,
        rollbackScript: String? = null,
        contexts: Contexts? = null,
        labelExpression: LabelExpression? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase
                .rollback(dateToRollBackTo.toJavaUtilDate(), rollbackScript, contexts, labelExpression)
        } else {
            this.liquibase
                .rollback(dateToRollBackTo.toJavaUtilDate(), rollbackScript, contexts, labelExpression, output)
        }
    }

    @Throws(LiquibaseException::class)
    fun rollback(
        dateToRollBackTo: LocalDateTime,
        rollbackScript: String? = null,
        contexts: String? = null,
        labelExpression: String? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase
                .rollback(
                    dateToRollBackTo.toJavaUtilDate(),
                    rollbackScript,
                    Contexts(contexts),
                    LabelExpression(labelExpression),
                )
        } else {
            this.liquibase
                .rollback(
                    dateToRollBackTo.toJavaUtilDate(),
                    rollbackScript,
                    Contexts(contexts),
                    LabelExpression(labelExpression),
                    output
                )
        }
    }

    @Throws(LiquibaseException::class)
    fun changeLogSync(
        tag: String? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase.changeLogSync(tag, Contexts(), LabelExpression())
        } else {
            this.liquibase.changeLogSync(tag, Contexts(), LabelExpression(), output)
        }
    }

    @Throws(LiquibaseException::class)
    fun changeLogSync(
        tag: String? = null,
        contexts: Contexts? = null,
        labelExpression: LabelExpression? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase.changeLogSync(tag, contexts, labelExpression)
        } else {
            this.liquibase.changeLogSync(tag, contexts, labelExpression, output)
        }
    }

    @Throws(LiquibaseException::class)
    fun changeLogSync(
        tag: String? = null,
        contexts: String? = null,
        labelExpression: String? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase.changeLogSync(tag, Contexts(contexts), LabelExpression(labelExpression))
        } else {
            this.liquibase.changeLogSync(tag, Contexts(contexts), LabelExpression(labelExpression), output)
        }
    }

    @Throws(LiquibaseException::class)
    fun markNextChangeSetRan(
        contexts: Contexts? = null,
        labelExpression: LabelExpression? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase.markNextChangeSetRan(contexts, labelExpression)
        } else {
            this.liquibase.markNextChangeSetRan(contexts, labelExpression, output)
        }
    }

    @Throws(LiquibaseException::class)
    fun markNextChangeSetRan(
        contexts: String? = null,
        labelExpression: String? = null,
        output: Writer? = null,
    ) {
        return if (output == null) {
            this.liquibase.markNextChangeSetRan(Contexts(contexts), LabelExpression(labelExpression))
        } else {
            this.liquibase.markNextChangeSetRan(Contexts(contexts), LabelExpression(labelExpression), output)
        }
    }

    @Throws(LiquibaseException::class)
    fun futureRollbackSQL(
        count: Int? = null,
        tag: String? = null,
        contexts: Contexts? = null,
        labelExpression: LabelExpression?,
        output: Writer,
        checkLiquibaseTables: Boolean
    ) {
        return this.liquibase
            .extendedFutureRollbackSQL(count, tag, contexts, labelExpression, output, checkLiquibaseTables)
    }

    @Throws(DatabaseException::class)
    fun dropAll(vararg schemas: CatalogAndSchema) {
        return this.liquibase.dropAll(*schemas)
    }

    @Throws(DatabaseException::class)
    fun dropAll(dropDbclhistory: Boolean? = null, vararg schemas: CatalogAndSchema) {
        return this.liquibase.dropAll(dropDbclhistory, *schemas)
    }

    @Throws(LiquibaseException::class)
    fun tag(tagString: String) {
        return this.liquibase.tag(tagString)
    }

    @Throws(LiquibaseException::class)
    fun tagExists(tagString: String): Boolean {
        return this.liquibase.tagExists(tagString)
    }

    @Throws(LiquibaseException::class)
    fun updateTestingRollback(contexts: String? = null) {
        return this.liquibase.updateTestingRollback(contexts)
    }

    @Throws(LiquibaseException::class)
    fun updateTestingRollback(contexts: Contexts? = null, labelExpression: LabelExpression? = null) {
        return this.liquibase.updateTestingRollback(contexts, labelExpression)
    }

    @Throws(LiquibaseException::class)
    fun updateTestingRollback(
        tag: String? = null,
        contexts: Contexts? = null,
        labelExpression: LabelExpression? = null
    ) {
        return this.liquibase.updateTestingRollback(tag, contexts, labelExpression)
    }

    @Throws(LiquibaseException::class)
    fun checkLiquibaseTables(
        updateExistingNullChecksums: Boolean,
        databaseChangeLog: DatabaseChangeLog,
        contexts: Contexts? = null,
        labelExpression: LabelExpression?
    ) {
        return this.liquibase.checkLiquibaseTables(
            updateExistingNullChecksums,
            databaseChangeLog,
            contexts,
            labelExpression
        )
    }

    @get:Throws(DatabaseException::class)
    val isSafeToRunUpdate: Boolean
        get() = this.liquibase.isSafeToRunUpdate

    @Throws(LiquibaseException::class)
    fun listLocks(): Array<DatabaseChangeLogLock> {
        return this.liquibase.listLocks()
    }

    @Throws(LiquibaseException::class)
    fun reportLocks(out: PrintStream? = null) {
        return this.liquibase.reportLocks(out)
    }

    @Throws(LiquibaseException::class)
    fun forceReleaseLocks() {
        return this.liquibase.forceReleaseLocks()
    }

    @Throws(LiquibaseException::class)
    fun listUnrunChangeSets(
        contexts: Contexts? = null,
        labels: LabelExpression,
        checkLiquibaseTables: Boolean = true
    ): List<ChangeSet> {
        return this.liquibase.listUnrunChangeSets(contexts, labels, checkLiquibaseTables)
    }

    @Throws(LiquibaseException::class)
    fun getChangeSetStatuses(
        contexts: Contexts? = null,
        labelExpression: LabelExpression,
        checkLiquibaseTables: Boolean = true
    ): List<ChangeSetStatus> {
        return this.liquibase.getChangeSetStatuses(contexts, labelExpression, checkLiquibaseTables)
    }

    @Throws(LiquibaseException::class)
    fun getChangeSetStatuses(
        contexts: String? = null,
        labelExpression: String,
        checkLiquibaseTables: Boolean = true
    ): List<ChangeSetStatus> {
        return this.liquibase
            .getChangeSetStatuses(Contexts(contexts), LabelExpression(labelExpression), checkLiquibaseTables)
    }

    @Throws(LiquibaseException::class)
    fun reportStatus(
        verbose: Boolean,
        contexts: Contexts? = null,
        labels: LabelExpression? = null,
        out: Writer = defaultOutputWriter,
    ) {
        return this.liquibase.reportStatus(verbose, contexts, labels, out)
    }

    @Throws(LiquibaseException::class)
    fun reportStatus(
        verbose: Boolean,
        contexts: String? = null,
        labels: String? = null,
        out: Writer = defaultOutputWriter,
    ) {
        return this.liquibase.reportStatus(verbose, Contexts(contexts), LabelExpression(labels), out)
    }

    @Throws(LiquibaseException::class)
    fun listUnexpectedChangeSets(
        contexts: Contexts? = null,
        labelExpression: LabelExpression? = null
    ): Collection<RanChangeSet> {
        return this.liquibase.listUnexpectedChangeSets(contexts, labelExpression)
    }

    @Throws(LiquibaseException::class)
    fun listUnexpectedChangeSets(contexts: String? = null, labelExpression: String? = null): Collection<RanChangeSet> {
        return this.liquibase.listUnexpectedChangeSets(Contexts(contexts), LabelExpression(labelExpression))
    }

    @Throws(LiquibaseException::class)
    fun reportUnexpectedChangeSets(
        verbose: Boolean,
        contexts: String? = null,
        out: Writer = defaultOutputWriter,
    ) {
        return this.liquibase.reportUnexpectedChangeSets(verbose, contexts, out)
    }

    @Throws(LiquibaseException::class)
    fun reportUnexpectedChangeSets(
        verbose: Boolean,
        contexts: Contexts? = null,
        labelExpression: LabelExpression? = null,
        out: Writer = defaultOutputWriter,
    ) {
        return this.liquibase.reportUnexpectedChangeSets(verbose, contexts, labelExpression, out)
    }

    @Throws(LiquibaseException::class)
    fun clearCheckSums() {
        return this.liquibase.clearCheckSums()
    }

    @Throws(LiquibaseException::class)
    fun calculateCheckSum(changeSetIdentifier: String): CheckSum {
        return this.liquibase.calculateCheckSum(changeSetIdentifier)
    }

    @Throws(LiquibaseException::class)
    fun calculateCheckSum(changeSetPath: String, changeSetId: String, changeSetAuthor: String): CheckSum {
        return this.liquibase.calculateCheckSum(changeSetPath, changeSetId, changeSetAuthor)
    }

    @Throws(LiquibaseException::class)
    fun generateDocumentation(
        outputDirectory: String,
        contexts: Contexts? = null,
        labelExpression: LabelExpression? = null,
        vararg schemaList: CatalogAndSchema?
    ) {
        return this.liquibase
            .generateDocumentation(outputDirectory, contexts, labelExpression, *schemaList)
    }

    @Throws(LiquibaseException::class)
    fun diff(
        referenceDatabase: Database,
        targetDatabase: Database? = null,
        compareControl: CompareControl? = null
    ): DiffResult {
        val targetDatabase = targetDatabase ?: this.liquibase.database
        return this.liquibase.diff(
            referenceDatabase,
            targetDatabase,
            compareControl ?: compareControl(targetDatabase = targetDatabase, referenceDatabase = referenceDatabase)
        )
    }

    @Throws(LiquibaseException::class)
    fun validate() {
        return this.liquibase.validate()
    }

    fun setChangeLogParameter(key: String, value: Any? = null) {
        return this.liquibase.setChangeLogParameter(key, value)
    }

    @Throws(DatabaseException::class, CommandExecutionException::class)
    fun generateChangeLog(
        catalogAndSchema: CatalogAndSchema = CatalogAndSchema.DEFAULT,
        changeLogWriter: DiffToChangeLog? = null,
        outputStream: PrintStream? = null,
        vararg snapshotTypes: KClass<out DatabaseObject>
    ) {
        @Suppress("SpreadOperator")
        return this.liquibase.generateChangeLog(
            catalogAndSchema,
            changeLogWriter,
            outputStream,
            *snapshotTypes.map { it.java }.toTypedArray()
        )
    }

    @SafeVarargs
    @Throws(DatabaseException::class, CommandExecutionException::class)
    fun diffChangeLog(
        referenceDatabase: Database,
        outputStream: PrintStream? = null,
        vararg snapshotTypes: Class<out DatabaseObject?>?
    ) {
        var finalCompareTypes: Set<Class<out DatabaseObject?>>? = null
        if ((snapshotTypes != null) && (snapshotTypes.size > 0)) {
            finalCompareTypes = HashSet(Arrays.asList(*snapshotTypes))
        }
        val compareControl = CompareControl(
            arrayOf(
                SchemaComparison(
                    buildCatalogAndSchema(this.liquibase.database),
                    buildCatalogAndSchema(referenceDatabase)
                )
            ),
            finalCompareTypes
        )

        // Reference liquibase.command.core.DiffToChangeLogCommand.run
        CommandScope("diffChangelog")
            .addArgumentValue(GenerateChangelogCommandStep.CHANGELOG_FILE_ARG, changeLogFile)
            .addArgumentValue(PreCompareCommandStep.COMPARE_CONTROL_ARG, compareControl)
            .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, this.liquibase.database)
            .addArgumentValue(ReferenceDbUrlConnectionCommandStep.REFERENCE_DATABASE_ARG, referenceDatabase)
            .addArgumentValue(PreCompareCommandStep.SNAPSHOT_TYPES_ARG, snapshotTypes)
            .setOutput(outputStream)
            .execute()
    }

    @Throws(LiquibaseException::class)
    override fun close() {
        return this.liquibase.close()
    }

    private fun compareControl(
        targetDatabase: Database,
        referenceDatabase: Database,
    ): CompareControl {
        val targetCatalogAndSchema: CatalogAndSchema = buildCatalogAndSchema(targetDatabase)
        val referenceCatalogAndSchema: CatalogAndSchema = buildCatalogAndSchema(referenceDatabase)
        val schemaComparisons = arrayOf(
            CompareControl.SchemaComparison(referenceCatalogAndSchema, targetCatalogAndSchema)
        )
        val snapshotGeneratorFactory = SnapshotGeneratorFactory.getInstance()
        val referenceSnapshot = snapshotGeneratorFactory.createSnapshot(
            referenceDatabase.defaultSchema,
            referenceDatabase,
            SnapshotControl(
                referenceDatabase,
                diffTypes.joinToString(",")
            )
        )
        return CompareControl(schemaComparisons, referenceSnapshot.snapshotControl.typesToInclude)
    }

    private fun buildCatalogAndSchema(database: Database): CatalogAndSchema {
        return CatalogAndSchema(database.defaultCatalogName, database.defaultSchemaName)
    }
}
