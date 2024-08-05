package liquibase.serializer.ext

import liquibase.change.Change
import liquibase.change.ColumnConfig
import liquibase.change.ConstraintsConfig
import liquibase.changelog.ChangeLogChild
import liquibase.changelog.ChangeSet
import liquibase.serializer.ChangeLogSerializer
import liquibase.serializer.LiquibaseSerializable
import liquibase.sql.visitor.SqlVisitor
import liquibase.util.ISODateFormat
import java.io.File
import java.io.OutputStream
import java.util.*

class KotlinChangeLogSerializer : ChangeLogSerializer {
    private val isoFormat = ISODateFormat()

    override fun getValidFileExtensions(): Array<String> = arrayOf("groovy")

    override fun getPriority(): Int = ChangeLogSerializer.PRIORITY_DEFAULT

    override fun serialize(
        change: LiquibaseSerializable,
        pretty: Boolean,
    ): String = serializeObject(change)

    override fun <T : ChangeLogChild> write(
        changeSets: List<T>,
        out: OutputStream,
    ) {
        out.write("databaseChangeLog {\n".toByteArray())
        out.write(changeSets.joinToString("\n\n") { indent(serialize(it, true)) }.toByteArray())
        out.write("\n\n}\n".toByteArray())
    }

    override fun append(
        changeSet: ChangeSet,
        changeLogFile: File,
    ): Unit =
        throw UnsupportedOperationException(
            """KotlinChangeLogSerializer does not append changelog content.
            |To append a newly generated changelog to an existing changelog, specify a new filename
            |for the new changelog, then copy and paste that content into the existing file.
            """.trimMargin(),
        )

    private fun serializeObject(changeSet: ChangeSet): String {
        val attrNames =
            listOf("id", "author", "runAlways", "runOnChange", "failOnError", "context", "dbms")
        val attributes =
            mutableMapOf(
                "id" to changeSet.id,
                "author" to changeSet.author,
            )
        val children = mutableListOf<String>()

        if (changeSet.isAlwaysRun) {
            attributes["runAlways"] = true.toString()
        }

        if (changeSet.isRunOnChange) {
            attributes["runOnChange"] = true.toString()
        }

        if (changeSet.failOnError != null) {
            attributes["failOnError"] = changeSet.failOnError.toString()
        }

        if (changeSet.contexts != null && changeSet.contexts.contexts.isNotEmpty()) {
            attributes["context"] = changeSet.contexts.contexts.joinToString(",")
        }

        if (changeSet.dbmsSet.isNotEmpty()) {
            attributes["dbms"] = changeSet.dbmsSet.joinToString(",")
        }

        if (!changeSet.comments.isNullOrBlank()) {
            children.add("""comment "${changeSet.comments.replace("\"", "\\\"")}"""")
        }

        changeSet.changes.forEach { change ->
            children.add(serialize(change, true))
        }

        val renderedChildren = children.joinToString("\n") { indent(it) }
        return """
            |changeSet(${buildPropertyListFrom(attrNames, attributes).joinToString(", ")}) {
            |$renderedChildren
            |}
            """.trimMargin()
    }

    private fun serializeObject(change: Change): String {
        val fields = change.serializableFields
        val children = mutableListOf<String>()
        val attributes = mutableListOf<String>()
        var textBody: String? = null

        fields.forEach { field ->
            val fieldValue = change.getSerializableFieldValue(field)
            when {
                fieldValue == null -> Unit
                fieldValue is Collection<*> -> {
                    fieldValue.filterIsInstance<ColumnConfig>().forEach {
                        children.add(serialize(it, true))
                    }
                }

                fieldValue is ColumnConfig -> {
                    children.add(serialize(fieldValue, true))
                }

                field in listOf("procedureBody", "sql", "selectQuery") -> {
                    textBody = fieldValue.toString()
                }

                else -> {
                    attributes.add(field)
                }
            }
        }

        val serializedChange =
            if (attributes.isNotEmpty()) {
                "${change.serializedObjectName}(${
                    buildPropertyListFrom(
                        attributes,
                        change,
                    ).joinToString(", ")
                })"
            } else {
                change.serializedObjectName
            }

        return when {
            children.isNotEmpty() -> {
                val renderedChildren = children.joinToString("\n") { indent(it) }
                "$serializedChange {\n$renderedChildren\n}"
            }

            textBody != null -> {
                if (textBody!!.contains("\n")) {
                    "$serializedChange {\n  \"\"\"\n$textBody\n\"\"\""
                } else {
                    "$serializedChange {\n  \"$textBody\"\n}"
                }
            }

            else -> serializedChange
        }
    }

    private fun serializeObject(columnConfig: ColumnConfig): String {
        val propertyNames =
            listOf(
                "name",
                "type",
                "value",
                "valueNumeric",
                "valueDate",
                "valueBoolean",
                "valueComputed",
                "defaultValue",
                "defaultValueNumeric",
                "defaultValueDate",
                "defaultValueBoolean",
                "defaultValueComputed",
                "autoIncrement",
                "remarks",
            )
        val properties = buildPropertyListFrom(propertyNames, columnConfig)
        val column = "column(${properties.joinToString(", ")})"
        return if (columnConfig.constraints != null) {
            "$column {\n  ${serialize(columnConfig.constraints, true)}\n}"
        } else {
            column
        }
    }

    private fun serializeObject(constraintsConfig: ConstraintsConfig): String {
        val propertyNames =
            listOf(
                "nullable",
                "primaryKey",
                "primaryKeyName",
                "primaryKeyTablespace",
                "references",
                "referencedTableName",
                "referencedColumnNames",
                "unique",
                "uniqueConstraintName",
                "checkConstraint",
                "deleteCascade",
                "foreignKeyName",
                "initiallyDeferred",
                "deferrable",
            )
        return "constraints(${
            buildPropertyListFrom(
                propertyNames,
                constraintsConfig,
            ).joinToString(", ")
        })"
    }

    private fun serializeObject(visitor: SqlVisitor): String {
        val buildPropertyList =
            buildPropertyListFrom(
                visitor.serializableFields.toList(),
                visitor,
            )
        return "${visitor.name}(${buildPropertyList.joinToString(", ")})"
    }

    private fun serializeObject(change: LiquibaseSerializable): String {
        val fields = change.serializableFields
        val children = mutableListOf<String>()
        val attributes = mutableListOf<String>()
        var textBody: String? = null

        fields.forEach { field ->
            val fieldValue = change.getSerializableFieldValue(field)
            if (fieldValue != null) {
                val serializationType = change.getSerializableFieldType(field)
                when {
                    fieldValue is Collection<*> -> {
                        fieldValue.filterIsInstance<LiquibaseSerializable>().forEach {
                            children.add(serialize(it, true))
                        }
                    }

                    field in listOf("procedureBody", "sql", "selectQuery") -> {
                        textBody = fieldValue.toString()
                    }

                    fieldValue is ChangeSet -> {
                        children.add(serialize(fieldValue, true))
                    }

                    fieldValue is LiquibaseSerializable -> {
                        children.add(serialize(fieldValue, true))
                    }

                    serializationType == LiquibaseSerializable.SerializationType.NESTED_OBJECT -> {
                        // TODO: Cast will always fail
                        children.add(serialize(fieldValue as LiquibaseSerializable, true))
                    }

                    serializationType == LiquibaseSerializable.SerializationType.DIRECT_VALUE -> {
                        textBody = fieldValue.toString()
                    }

                    else -> {
                        attributes.add(field)
                    }
                }
            }
        }

        val serializedChange =
            if (attributes.isNotEmpty()) {
                "${change.serializedObjectName}(${
                    buildPropertyListFrom(
                        attributes,
                        change,
                    ).joinToString(", ")
                })"
            } else {
                change.serializedObjectName
            }

        return when {
            children.isNotEmpty() -> {
                val renderedChildren = children.joinToString("\n") { indent(it) }
                "$serializedChange {\n$renderedChildren\n}"
            }

            textBody != null -> {
                "$serializedChange {\n  ''' $textBody '''\n}"
            }

            else -> serializedChange
        }
    }

    private fun indent(text: String?): String =
        text?.lineSequence()?.joinToString("\n") { "  $it" } ?: ""

    private fun buildPropertyListFrom(
        propertyNames: List<String>,
        obj: Any,
    ): List<String> {
        val properties = mutableListOf<String>()

        propertyNames.forEach { propertyName ->
            val propertyValue = obj::class.members.find { it.name == propertyName }?.call(obj)
            if (propertyValue != null) {
                val propertyString =
                    when (propertyValue) {
                        is Boolean -> propertyValue.toString()
                        is Number -> propertyValue.toString()
                        is java.sql.Timestamp -> "'''${isoFormat.format(propertyValue)}'''"
                        else -> "'''$propertyValue'''"
                    }
                properties.add("$propertyName: $propertyString")
            }
        }

        return properties
    }
}
