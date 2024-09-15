package momosetkn.liquibase.kotlin.dsl

import liquibase.changelog.DatabaseChangeLog

@ChangeLogDslMarker
class KeyValueDsl(
    private val changeLog: DatabaseChangeLog,
) {
    private var map = mutableMapOf<String, Any?>()

    internal operator fun invoke(
        block: KeyValueDsl.() -> Unit,
    ): Map<String, Any?> {
        block(this)
        return map
    }

    fun param(
        name: String,
        value: Any?,
    ) {
        map[name] = value.tryEvalExpressionsOrNull(changeLog)
    }
}
