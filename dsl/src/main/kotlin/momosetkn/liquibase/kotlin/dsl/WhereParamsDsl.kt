package momosetkn.liquibase.kotlin.dsl

import liquibase.change.ColumnConfig
import liquibase.changelog.DatabaseChangeLog
import momosetkn.liquibase.kotlin.dsl.Expressions.evalExpressions

@ChangeLogDslMarker
class WhereParamsDsl(
    private val changeLog: DatabaseChangeLog,
) {
    private val params = mutableListOf<ColumnConfig>()

    internal operator fun invoke(
        block: WhereParamsDsl.() -> Unit,
    ): List<ColumnConfig> {
        block(this)
        return params
    }

    fun param(
        name: String,
        value: String?,
    ) {
        val columnConfig = ColumnConfig()
        columnConfig.name = name.evalExpressions(changeLog)
        columnConfig.value = value?.evalExpressions(changeLog)

        params.add(columnConfig)
    }
}
