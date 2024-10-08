package momosetkn.liquibase.kotlin.dsl

import liquibase.ContextExpression
import liquibase.Labels
import liquibase.changelog.DatabaseChangeLog
import liquibase.sql.visitor.AppendSqlVisitor
import liquibase.sql.visitor.PrependSqlVisitor
import liquibase.sql.visitor.RegExpReplaceSqlVisitor
import liquibase.sql.visitor.ReplaceSqlVisitor
import liquibase.sql.visitor.SqlVisitor
import momosetkn.liquibase.kotlin.dsl.Expressions.evalExpressions
import momosetkn.liquibase.kotlin.dsl.util.ReflectionUtils.new
import momosetkn.liquibase.kotlin.dsl.util.StringsUtil.splitAndTrim

@ChangeLogDslMarker
class ModifySqlDsl(
    private val changeLog: DatabaseChangeLog,
    private val context: ModifySqlContext,
) {
    private val sqlVisitors =
        mutableListOf<SqlVisitor>()

    internal operator fun invoke(
        block: ModifySqlDsl.() -> Unit,
    ): List<SqlVisitor> {
        block(this)
        return sqlVisitors
    }

    fun prepend(value: String) {
        createSqlVisitor<PrependSqlVisitor>().apply {
            this.value = value.evalExpressions(changeLog)
        }
    }

    fun append(value: String) {
        createSqlVisitor<AppendSqlVisitor>().apply {
            this.value = value.evalExpressions(changeLog)
        }
    }

    fun replace(
        replace: String,
        with: String,
    ) {
        createSqlVisitor<ReplaceSqlVisitor>().apply {
            this.replace = replace.evalExpressions(changeLog)
            this.with = with.evalExpressions(changeLog)
        }
    }

    fun regExpReplace(
        replace: String,
        with: String,
    ) {
        createSqlVisitor<RegExpReplaceSqlVisitor>().apply {
            this.replace = replace.evalExpressions(changeLog)
            this.with = with.evalExpressions(changeLog)
        }
    }

    private inline fun <reified E : SqlVisitor> createSqlVisitor(): E {
        // not use liquibase.sql.visitor.SqlVisitorFactory.
        // because, for typesafe.
        val sqlVisitor = E::class.new()

        context.dbms?.let { sqlVisitor.applicableDbms = it }
        context.contextFilter?.let { sqlVisitor.contextFilter = it }
        context.labels?.let { sqlVisitor.labels = it }
        sqlVisitor.isApplyToRollback = context.applyToRollback

        sqlVisitors.add(sqlVisitor)
        return sqlVisitor
    }

    companion object {
        fun build(
            changeLog: DatabaseChangeLog,
            dbms: String? = null,
            contextFilter: String? = null,
            labels: String? = null,
            applyToRollback: Boolean? = null,
        ): ModifySqlDsl {
            val typedDbms: Set<String>? =
                dbms?.evalExpressions(changeLog)?.splitAndTrim()?.toSet()
            val typedContextFilter: ContextExpression? =
                contextFilter?.evalExpressions(changeLog)?.let {
                    ContextExpression(it)
                }
            val typedLabels: Labels? =
                labels?.evalExpressions(changeLog)?.let { Labels(it) }

            val context =
                ModifySqlContext(
                    dbms = typedDbms,
                    contextFilter = typedContextFilter,
                    labels = typedLabels,
                    applyToRollback = applyToRollback ?: false,
                )
            return ModifySqlDsl(
                changeLog = changeLog,
                context = context,
            )
        }
    }
}

data class ModifySqlContext(
    val dbms: Set<String>?,
    val contextFilter: ContextExpression?,
    val labels: Labels?,
    val applyToRollback: Boolean,
)
