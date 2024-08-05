package org.liquibase.kotlin.delegate

import liquibase.ContextExpression
import liquibase.Labels
import liquibase.change.visitor.ChangeVisitorFactory
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.changelog.IncludeAllFilter
import liquibase.database.DatabaseList
import liquibase.database.ObjectQuotingStrategy
import liquibase.exception.ChangeLogParseException
import liquibase.resource.ResourceAccessor
import java.util.Comparator
import java.util.Properties

/**
 * This class is the delegate for the {@code databaseChangeLog} element.  It is the starting point
 * for parsing the Kotlin DSL.
 *
 * @author Steven C. Saliman
 */
class DatabaseChangeLogDelegate(
    private val params: Map<String, Any> = emptyMap(),
    private val databaseChangeLog: DatabaseChangeLog,
    private val resourceAccessor: ResourceAccessor,
) {
    init {
        params.forEach { (key, value) ->
            val resolvedValue =
                when (key) {
                    "context", "contextFilter" -> ContextExpression(value.toString())
                    else -> value
                }
            databaseChangeLog[key] = resolvedValue
        }
    }

    /**
     * Parse a changeSet and add it to the change log.
     * @param params the attributes of the change set.
     * @param closure the closure containing, among other things, all the refactoring changes the
     * change set should make.
     */
    fun changeSet(
        params: Map<String, Any>,
        closure: ChangeSetDelegate.() -> Unit,
    ) {
        if (params.containsKey("alwaysRun")) {
            throw ChangeLogParseException(
                "Error: ChangeSet '${params["id"]}': the alwaysRun attribute of a changeSet has been removed. Please use 'runAlways' instead.",
            )
        }

        val unsupportedKeys =
            params.keys -
                listOf(
                    "id",
                    "author",
                    "dbms",
                    "runAlways",
                    "runOnChange",
                    "context",
                    "contextFilter",
                    "labels",
                    "runInTransaction",
                    "failOnError",
                    "onValidationFail",
                    "objectQuotingStrategy",
                    "logicalFilePath",
                    "filePath",
                    "created",
                    "runOrder",
                    "ignore",
                    "runWith",
                    "runWithSpoolFile",
                )
        if (unsupportedKeys.isNotEmpty()) {
            throw ChangeLogParseException(
                "ChangeSet '${params["id"]}': ${unsupportedKeys.first()} is not a supported ChangeSet attribute",
            )
        }

        val objectQuotingStrategy =
            params["objectQuotingStrategy"]?.let {
                try {
                    ObjectQuotingStrategy.valueOf(it.toString())
                } catch (e: IllegalArgumentException) {
                    throw ChangeLogParseException(
                        "ChangeSet '${params["id"]}': $it is not a supported ChangeSet ObjectQuotingStrategy",
                    )
                }
            }

        val filePath = params["logicalFilePath"] ?: params["filePath"] ?: databaseChangeLog.filePath
        val contextFilter = params["contextFilter"] ?: params["context"]

        val changeSet =
            ChangeSet(
                DelegateUtil.expandExpressions(params["id"], databaseChangeLog),
                DelegateUtil.expandExpressions(params["author"], databaseChangeLog),
                DelegateUtil.parseTruth(params["runAlways"], false),
                DelegateUtil.parseTruth(params["runOnChange"], false),
                filePath.toString(),
                DelegateUtil.expandExpressions(contextFilter, databaseChangeLog),
                DelegateUtil.expandExpressions(params["dbms"], databaseChangeLog),
                DelegateUtil.expandExpressions(params["runWith"], databaseChangeLog),
                DelegateUtil.expandExpressions(params["runWithSpoolFile"], databaseChangeLog),
                DelegateUtil.parseTruth(params["runInTransaction"], true),
                objectQuotingStrategy,
                databaseChangeLog,
            )

        changeSet.changeLogParameters = databaseChangeLog.changeLogParameters

        if (params.containsKey("failOnError")) {
            changeSet.failOnError = DelegateUtil.parseTruth(params["failOnError"], false)
        }

        params["onValidationFail"]?.let {
            changeSet.onValidationFail = ChangeSet.ValidationFailOption.valueOf(it.toString())
        }

        params["labels"]?.let {
            changeSet.labels = Labels(it.toString())
        }

        params["created"]?.let {
            changeSet.created = it.toString()
        }

        params["runOrder"]?.let {
            changeSet.runOrder = it.toString()
        }

        if (params.containsKey("ignore")) {
            changeSet.ignore = DelegateUtil.parseTruth(params["ignore"], false)
        }

        val delegate = ChangeSetDelegate(changeSet, databaseChangeLog)
        closure(delegate)

        databaseChangeLog.addChangeSet(changeSet)
    }

    fun include(params: Map<String, Any> = emptyMap()) {
        val unsupportedKeys =
            params.keys -
                listOf(
                    "file",
                    "relativeToChangelogFile",
                    "errorIfMissing",
                    "context",
                    "contextFilter",
                    "labels",
                    "ignore",
                )
        if (unsupportedKeys.isNotEmpty()) {
            throw ChangeLogParseException(
                "DatabaseChangeLog: '${unsupportedKeys.first()}' is not a supported attribute of the 'include' element.",
            )
        }

        val relativeToChangelogFile =
            DelegateUtil.parseTruth(
                params["relativeToChangelogFile"],
                false,
            )
        val errorIfMissing = DelegateUtil.parseTruth(params["errorIfMissing"], false)

        val fileName =
            databaseChangeLog
                .changeLogParameters
                .expandExpressions(params["file"], databaseChangeLog)
        val context = params["contextFilter"] ?: params["context"]
        val includeContexts = ContextExpression(context.toString())
        val labels = Labels(params["labels"].toString())
        val ignore = DelegateUtil.parseTruth(params["ignore"], false)

        databaseChangeLog.include(
            fileName.toString(),
            relativeToChangelogFile,
            errorIfMissing,
            resourceAccessor,
            includeContexts,
            labels,
            ignore,
            DatabaseChangeLog.OnUnknownFileFormat.FAIL,
        )
    }

    fun includeAll(params: Map<String, Any> = emptyMap()) {
        val unsupportedKeys =
            params.keys -
                listOf(
                    "path",
                    "relativeToChangelogFile",
                    "errorIfMissingOrEmpty",
                    "resourceComparator",
                    "filter",
                    "context",
                    "contextFilter",
                    "labels",
                    "ignore",
                    "minDepth",
                    "maxDepth",
                    "endsWithFilter",
                ).toSet()
        if (unsupportedKeys.isNotEmpty()) {
            throw ChangeLogParseException(
                "DatabaseChangeLog: '${unsupportedKeys.first()}' is not a supported attribute of the 'includeAll' element.",
            )
        }

        val includeAllParams = createIncludeAllParams(params)
        databaseChangeLog.includeAll(
            includeAllParams["path"].toString(),
            includeAllParams["relativeToChangelogFile"] as Boolean,
            includeAllParams["filter"] as IncludeAllFilter?,
            includeAllParams["errorIfMissingOrEmpty"] as Boolean,
            includeAllParams["resourceComparator"] as Comparator<String>,
            resourceAccessor,
            includeAllParams["includeContexts"] as ContextExpression,
            includeAllParams["labels"] as Labels,
            includeAllParams["ignore"] as Boolean,
            includeAllParams["minDepth"] as Int,
            includeAllParams["maxDepth"] as Int,
            includeAllParams["endsWithFilter"].toString(),
            null,
        )
    }

    fun includeAllSql(params: Map<String, Any> = emptyMap()) {
        val includeAllKeys =
            listOf(
                "path",
                "relativeToChangelogFile",
                "errorIfMissingOrEmpty",
                "resourceComparator",
                "filter",
                "context",
                "contextFilter",
                "labels",
                "ignore",
                "minDepth",
                "maxDepth",
                "endsWithFilter",
            )
        val changeSetKeys =
            listOf(
                "author",
                "dbms",
                "runAlways",
                "runOnChange",
                "context",
                "contextFilter",
                "labels",
                "failOnError",
                "onValidationFail",
                "objectQuotingStrategy",
                "created",
                "ignore",
                "runWith",
                "runWithSpoolFile",
            )
        val sqlFileKeys =
            listOf(
                "dbms",
                "encoding",
                "endDelimiter",
                "relativeToChangeLogFile",
                "splitStatements",
                "stripComments",
            )

        val unsupportedKeys =
            params.keys - includeAllKeys - changeSetKeys - sqlFileKeys -
                listOf(
                    "idPrefix",
                    "idSuffix",
                    "idKeepsExtension",
                )
        if (unsupportedKeys.isNotEmpty()) {
            throw ChangeLogParseException(
                "DatabaseChangeLog: '${unsupportedKeys.first()}' is not a supported attribute of the 'includeAll' element.",
            )
        }

        val includeAllParams = createIncludeAllParams(params.filterKeys(includeAllKeys))

        val changeSetParams =
            params.filterKeys(changeSetKeys).toMutableMap().apply {
                putIfAbsent("author", "various (generated by includeAllSql)")
                putIfAbsent("runAlways", DelegateUtil.parseTruth(params["runAlways"], false))
                putIfAbsent("runOnChange", DelegateUtil.parseTruth(params["runOnChange"], true))
                putIfAbsent("failOnError", DelegateUtil.parseTruth(params["failOnError"], false))
            }

        val sqlFileParams =
            params.filterKeys(sqlFileKeys).toMutableMap().apply {
                putIfAbsent("relativeToChangelogFile", false)
            }

        val sqlFiles =
            databaseChangeLog.findResources(
                includeAllParams["path"].toString(),
                includeAllParams["relativeToChangelogFile"] as Boolean,
                includeAllParams["filter"] as IncludeAllFilter?,
                includeAllParams["errorIfMissingOrEmpty"] as Boolean,
                includeAllParams["resourceComparator"] as Comparator<String>,
                resourceAccessor,
                includeAllParams["minDepth"] as Int,
                includeAllParams["maxDepth"] as Int,
                includeAllParams["endsWithFilter"].toString(),
            )

        if (sqlFiles.isNullOrEmpty()) return

        val idKeepsExtension = DelegateUtil.parseTruth(params["idKeepsExtension"], false)

        sqlFiles.forEach { fileName ->
            val baseName =
                fileName.path.split('/', '\\').last().run {
                    if (!idKeepsExtension && contains('.')) substringBeforeLast('.') else this
                }
            changeSetParams["id"] =
                "${params["idPrefix"] ?: ""}$baseName${params["idSuffix"] ?: ""}"
            sqlFileParams["path"] = fileName
            changeSet(changeSetParams) {
                sqlFile(sqlFileParams)
            }
        }
    }

    fun preConditions(
        params: Map<String, Any> = emptyMap(),
        closure: PreconditionDelegate.() -> Unit,
    ) {
        databaseChangeLog.preconditions =
            PreconditionDelegate.buildPreconditionContainer(
                databaseChangeLog,
                "<none>",
                params,
                closure,
            )
    }

    fun property(params: Map<String, Any> = emptyMap()) {
        val unsupportedKeys =
            params.keys -
                listOf(
                    "name",
                    "value",
                    "context",
                    "contextFilter",
                    "labels",
                    "dbms",
                    "global",
                    "file",
                    "relativeToChangelogFile",
                    "errorIfMissing",
                )
        if (unsupportedKeys.isNotEmpty()) {
            throw ChangeLogParseException(
                "DatabaseChangeLog: ${unsupportedKeys.first()} is not a supported property attribute",
            )
        }

        val context =
            params["contextFilter"]?.let { ContextExpression(it.toString()) }
                ?: params["context"]?.let { ContextExpression(it.toString()) }
        val labels = params["labels"]?.let { Labels(it.toString()) }
        val dbms = params["dbms"]
        val global = DelegateUtil.parseTruth(params["global"], true)

        val changeLogParameters = databaseChangeLog.changeLogParameters

        if (params["file"] == null) {
            changeLogParameters.set(
                params["name"].toString(),
                params["value"].toString(),
                context,
                labels,
                dbms,
                global,
                databaseChangeLog,
            )
        } else {
            val propFile = params["file"].toString()
            val relativeTo =
                if (DelegateUtil.parseTruth(params["relativeToChangelogFile"], false)) {
                    databaseChangeLog.physicalFilePath
                } else {
                    null
                }
            val errorIfMissing = DelegateUtil.parseTruth(params["errorIfMissing"], true)
            val props = Properties()

            resourceAccessor.openStream(relativeTo, propFile)?.use { stream ->
                props.load(stream)
                props.forEach { (k, v) ->
                    changeLogParameters.set(
                        k.toString(),
                        v.toString(),
                        context,
                        labels,
                        dbms,
                        global,
                        databaseChangeLog,
                    )
                }
            } ?: if (errorIfMissing) {
                throw ChangeLogParseException("Unable to load file with properties: $propFile")
            }
        }
    }

    fun removeChangeSetProperty(params: Map<String, Any> = emptyMap()) {
        val unsupportedKeys = params.keys - listOf("change", "dbms", "remove")
        if (unsupportedKeys.isNotEmpty()) {
            throw ChangeLogParseException(
                "DatabaseChangeLog: ${unsupportedKeys.first()} is not a supported property attribute",
            )
        }

        if (params["dbms"] == null || params["remove"] == null) {
            throw ChangeLogParseException(
                "DatabaseChangeLog: missing value for the 'dbms' or 'remove' parameter",
            )
        }

        val currentDb = databaseChangeLog.changeLogParameters.database
        if (!DatabaseList.definitionMatches(params["dbms"].toString(), currentDb, false)) {
            return
        }

        val changeVisitor = ChangeVisitorFactory.getInstance().create(params["change"].toString())
        if (changeVisitor == null) {
            throw ChangeLogParseException(
                "DatabaseChangeLog: ${params["change"]} is not a valid change type",
            )
        }

        changeVisitor.dbms = params["dbms"].toString().split(",")
        changeVisitor.remove = params["remove"].toString()
        databaseChangeLog.changeVisitors.add(changeVisitor)
    }

    operator fun get(name: String): Any? {
        val changeLogParameters = databaseChangeLog.changeLogParameters
        return if (changeLogParameters.hasValue(name, databaseChangeLog)) {
            changeLogParameters.getValue(name, databaseChangeLog)
        } else {
            throw NoSuchFieldException("Missing property: $name")
        }
    }

    operator fun invoke(
        name: String,
        args: Array<Any?>,
    ): Any? =
        throw ChangeLogParseException(
            "DatabaseChangeLog: '$name' is not a valid element of a DatabaseChangeLog",
        )

    private fun createIncludeAllParams(params: Map<String, Any>): Map<String, Any> {
        val includeAllParams = params.toMutableMap()

        includeAllParams["relativeToChangelogFile"] =
            DelegateUtil.parseTruth(params["relativeToChangelogFile"], false)
        includeAllParams["errorIfMissingOrEmpty"] =
            DelegateUtil.parseTruth(params["errorIfMissingOrEmpty"], true)
        val context = params["contextFilter"] ?: params["context"]
        includeAllParams["includeContexts"] = ContextExpression(context.toString())
        includeAllParams["ignore"] = DelegateUtil.parseTruth(params["ignore"], false)
        includeAllParams["labels"] = Labels(params["labels"].toString())
        includeAllParams["minDepth"] = params["minDepth"]?.toString()?.toInt() ?: 0
        includeAllParams["maxDepth"] = params["maxDepth"]?.toString()?.toInt() ?: Int.MAX_VALUE
        includeAllParams["endsWithFilter"] = params["endsWithFilter"]?.toString() ?: ""

        val resourceComparator =
            params["resourceComparator"]?.let {
                val comparatorName =
                    databaseChangeLog
                        .changeLogParameters
                        .expandExpressions(it, databaseChangeLog)
                try {
                    Class.forName(comparatorName).newInstance() as Comparator<String>
                } catch (e: InstantiationException) {
                    throw ChangeLogParseException(
                        "DatabaseChangeLog: '$comparatorName' is not a valid resource comparator. Does the class exist, and does it implement Comparator?",
                    )
                } catch (e: IllegalAccessException) {
                    throw ChangeLogParseException(
                        "DatabaseChangeLog: '$comparatorName' is not a valid resource comparator. Does the class exist, and does it implement Comparator?",
                    )
                } catch (e: ClassNotFoundException) {
                    throw ChangeLogParseException(
                        "DatabaseChangeLog: '$comparatorName' is not a valid resource comparator. Does the class exist, and does it implement Comparator?",
                    )
                } catch (e: ClassCastException) {
                    throw ChangeLogParseException(
                        "DatabaseChangeLog: '$comparatorName' is not a valid resource comparator. Does the class exist, and does it implement Comparator?",
                    )
                }
            } ?: getStandardChangeLogComparator()
        includeAllParams["resourceComparator"] = resourceComparator

        val filter =
            params["filter"]?.let {
                val filterName =
                    databaseChangeLog
                        .changeLogParameters
                        .expandExpressions(it, databaseChangeLog)
                try {
                    Class.forName(filterName).newInstance() as IncludeAllFilter
                } catch (e: InstantiationException) {
                    throw ChangeLogParseException(
                        "DatabaseChangeLog: '$filterName' is not a valid resource filter. Does the class exist, and does it implement IncludeAllFilter?",
                    )
                } catch (e: IllegalAccessException) {
                    throw ChangeLogParseException(
                        "DatabaseChangeLog: '$filterName' is not a valid resource filter. Does the class exist, and does it implement IncludeAllFilter?",
                    )
                } catch (e: ClassNotFoundException) {
                    throw ChangeLogParseException(
                        "DatabaseChangeLog: '$filterName' is not a valid resource filter. Does the class exist, and does it implement IncludeAllFilter?",
                    )
                } catch (e: ClassCastException) {
                    throw ChangeLogParseException(
                        "DatabaseChangeLog: '$filterName' is not a valid resource filter. Does the class exist, and does it implement IncludeAllFilter?",
                    )
                }
            }
        includeAllParams["filter"] = filter

        val pathName =
            params["path"]?.let {
                databaseChangeLog
                    .changeLogParameters
                    .expandExpressions(it, databaseChangeLog)
            }
                ?: throw ChangeLogParseException(
                    "DatabaseChangeLog: No path attribute for includeAll",
                )

        if (pathName.contains('$')) {
            throw ChangeLogParseException(
                "DatabaseChangeLog: '$pathName' contains an invalid property in an 'includeAll' element.",
            )
        }
        includeAllParams["path"] = pathName

        return includeAllParams
    }

    private fun getStandardChangeLogComparator(): Comparator<String> =
        Comparator.comparing {
            it.replace("WEB-INF/classes/", "")
        }
}

fun Map<String, Any>.filterKeys(keys: List<String>): Map<String, Any> = filter { it.key in keys }

fun ChangeSetDelegate(
    changeSet: ChangeSet,
    databaseChangeLog: DatabaseChangeLog,
): ChangeSetDelegate = ChangeSetDelegate(changeSet, databaseChangeLog)
