/*
 * Copyright 2011-2024 Tim Berglund and Steven C. Saliman
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.liquibase.kotlin.delegate

import liquibase.ContextExpression
import liquibase.Labels
import liquibase.changelog.ChangeSet
import liquibase.exception.ChangeLogParseException
import liquibase.sql.visitor.SqlVisitor
import liquibase.sql.visitor.SqlVisitorFactory
import liquibase.util.PatchedObjectUtil

/**
 * This delegate handles the Liquibase ModifySql element, which can be used to tweak the SQL that
 * Liquibase generates.
 *
 * @author Steven C. Saliman
 */
class ModifySqlDelegate(
    private val params: Map<String, Any?> = emptyMap(),
    private val changeSet: ChangeSet,
) {
    var modifySqlDbmsList: Set<String>? = null
    var modifySqlAppliedOnRollback: Boolean? = null
    var modifySqlContexts: ContextExpression? = null
    var modifySqlLabels: Labels? = null
    val sqlVisitors = mutableListOf<SqlVisitor>()

    init {
        params?.let {
            val unsupportedKeys =
                it.keys - listOf("dbms", "context", "contextFilter", "labels", "applyToRollback")
            when {
                unsupportedKeys.isNotEmpty() -> {
                    throw ChangeLogParseException(
                        "ChangeSet '${changeSet.id}':  '${unsupportedKeys.first()}' is not a supported attribute of the 'modifySql' element.",
                    )
                }

                it["dbms"] != null -> {
                    val value =
                        DelegateUtil.expandExpressions(
                            it["dbms"].toString(),
                            changeSet.changeLog,
                        )
                    modifySqlDbmsList = value?.replace(" ", "").split(",").toSet()
                }

                it["contextFilter"] != null || it["context"] != null -> {
                    val context = it["contextFilter"] ?: it["context"]
                    val value =
                        DelegateUtil.expandExpressions(
                            context.toString(),
                            changeSet.changeLog,
                        )
                    modifySqlContexts = ContextExpression(value)
                }

                it["labels"] != null -> {
                    val value =
                        DelegateUtil.expandExpressions(
                            it["labels"].toString(),
                            changeSet.changeLog,
                        )
                    modifySqlLabels = Labels(value)
                }
            }

            modifySqlAppliedOnRollback = DelegateUtil.parseTruth(it["applyToRollback"], false)
        }
    }

    fun prepend(params: Map<String, Any?> = emptyMap()) {
        createSqlVisitor("prepend", params)
    }

    fun append(params: Map<String, Any?> = emptyMap()) {
        createSqlVisitor("append", params)
    }

    fun replace(params: Map<String, Any?> = emptyMap()) {
        createSqlVisitor("replace", params)
    }

    fun regExpReplace(params: Map<String, Any?> = emptyMap()) {
        createSqlVisitor("regExpReplace", params)
    }

    private fun createSqlVisitor(
        type: String,
        params: Map<String, Any?>,
    ) {
        val sqlVisitor = SqlVisitorFactory.getInstance().create(type)

        params.forEach { (key, value) ->
            try {
                PatchedObjectUtil.setProperty(
                    sqlVisitor,
                    key,
                    DelegateUtil.expandExpressions(value.toString(), changeSet.changeLog),
                )
            } catch (e: RuntimeException) {
                throw ChangeLogParseException(
                    "ChangeSet '${changeSet.id}': '$key' is not a valid attribute for '$type' modifySql elements.",
                    e,
                )
            }
        }

        modifySqlDbmsList?.let { sqlVisitor.applicableDbms = it }
        // todo: contexts or contextFilter?
//        modifySqlContexts?.let { sqlVisitor.contexts = it }
        modifySqlContexts?.let { sqlVisitor.contextFilter = it }
        modifySqlLabels?.let { sqlVisitor.labels = it }
        sqlVisitor.setApplyToRollback(modifySqlAppliedOnRollback ?: false)

        sqlVisitors.add(sqlVisitor)
    }
}
