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

import KeyValueDelegate
import liquibase.changelog.DatabaseChangeLog
import liquibase.exception.ChangeLogParseException
import liquibase.precondition.CustomPreconditionWrapper
import liquibase.precondition.Precondition
import liquibase.precondition.PreconditionFactory
import liquibase.precondition.core.AndPrecondition
import liquibase.precondition.core.NotPrecondition
import liquibase.precondition.core.OrPrecondition
import liquibase.precondition.core.PreconditionContainer
import liquibase.precondition.core.PreconditionContainer.ErrorOption
import liquibase.precondition.core.PreconditionContainer.FailOption
import liquibase.precondition.core.PreconditionContainer.OnSqlOutputOption
import liquibase.precondition.core.SqlPrecondition
import liquibase.util.PatchedObjectUtil

class PreconditionDelegate(
    private val databaseChangeLog: DatabaseChangeLog,
    private val changeSetId: String = "<unknown>", // used for error messages
) {
    private val preconditions: MutableList<Precondition> = mutableListOf()

    operator fun invoke(
        name: String,
        params: Map<String, Any>,
    ) {
        val preconditionFactory = PreconditionFactory.getInstance()
        var precondition: Any? = null

        try {
            precondition = preconditionFactory.create(name)
        } catch (e: RuntimeException) {
            throw ChangeLogParseException(
                "ChangeSet '$changeSetId': '$name' is an invalid precondition.",
                e,
            )
        }

        // We don't always get an exception for an invalid precondition...
        if (precondition == null) {
            throw ChangeLogParseException(
                "ChangeSet '$changeSetId': '$name' is an invalid precondition.",
            )
        }

        params.forEach { (key, value) ->
            try {
                PatchedObjectUtil.setProperty(
                    precondition,
                    key,
                    DelegateUtil.expandExpressions(value, databaseChangeLog),
                )
            } catch (e: RuntimeException) {
                throw ChangeLogParseException(
                    "ChangeSet '$changeSetId': '$key' is an invalid property for '$name' preconditions.",
                    e,
                )
            }
        }

        preconditions.add(precondition)
    }

    fun sqlCheck(
        params: Map<String, Any> = emptyMap(),
        closure: () -> Any,
    ) {
        val precondition = SqlPrecondition()

        params.forEach { (key, value) ->
            try {
                PatchedObjectUtil.setProperty(
                    precondition,
                    key,
                    DelegateUtil.expandExpressions(value, databaseChangeLog),
                )
            } catch (e: RuntimeException) {
                throw ChangeLogParseException(
                    "ChangeSet '$changeSetId': '$key' is an invalid property for 'sqlCheck' preconditions.",
                    e,
                )
            }
        }

        val sql = DelegateUtil.expandExpressions(closure.invoke(), databaseChangeLog)
        if (sql != null && sql != "null") {
            precondition.sql = sql.toString()
        }

        preconditions.add(precondition)
    }

    fun customPrecondition(
        params: Map<String, Any> = emptyMap(),
        closure: (() -> Map<String, Any>),
    ) {
        val delegate = KeyValueDelegate(changeSetId = changeSetId)

        val precondition = CustomPreconditionWrapper()

        params.forEach { (key, value) ->
            try {
                val expandedValue = DelegateUtil.expandExpressions(value, databaseChangeLog)
                PatchedObjectUtil.setProperty(precondition, key, expandedValue)
            } catch (e: RuntimeException) {
                throw ChangeLogParseException(
                    "ChangeSet '$changeSetId': '$key' is an invalid property for 'customPrecondition' preconditions.",
                    e,
                )
            }
        }

        delegate.map.forEach { (key, value) ->
            val expandedValue = DelegateUtil.expandExpressions(value, databaseChangeLog).toString()
            precondition.setParam(key, expandedValue)
        }

        preconditions.add(precondition)
    }

    fun and(closure: () -> Any) {
        val precondition = nestedPrecondition(AndPrecondition::class.java, closure)
        preconditions.add(precondition)
    }

    fun or(closure: () -> Any) {
        val precondition = nestedPrecondition(OrPrecondition::class.java, closure)
        preconditions.add(precondition)
    }

    fun not(closure: () -> Any) {
        val precondition = nestedPrecondition(NotPrecondition::class.java, closure)
        preconditions.add(precondition)
    }

    companion object {
        fun buildPreconditionContainer(
            databaseChangeLog: DatabaseChangeLog,
            changeSetId: String,
            params: Map<String, Any>,
            closure: PreconditionDelegate.() -> Unit,
        ): PreconditionContainer {
            val preconditions = PreconditionContainer()

            // Process parameters.  3 of them need a special case.
            params.forEach { (key, value) ->
                val paramValue = DelegateUtil.expandExpressions(value, databaseChangeLog)
                when (key) {
                    "onFail" -> preconditions.onFail = FailOption.valueOf(paramValue.toString())
                    "onError" -> preconditions.onError = ErrorOption.valueOf(paramValue.toString())
                    "onUpdateSQL" ->
                        preconditions.onSqlOutput =
                            OnSqlOutputOption.valueOf(paramValue.toString())

                    else -> {
                        try {
                            PatchedObjectUtil.setProperty(preconditions, key, paramValue)
                        } catch (e: RuntimeException) {
                            throw ChangeLogParseException(
                                "ChangeSet '$changeSetId': '$key' is an invalid property for preconditions.",
                                e,
                            )
                        }
                    }
                }
            }

            val delegate =
                PreconditionDelegate(
                    databaseChangeLog = databaseChangeLog,
                    changeSetId = changeSetId,
                )

            delegate.preconditions.forEach { precondition ->
                preconditions.addNestedPrecondition(precondition)
            }

            return preconditions
        }
    }

    private fun nestedPrecondition(
        preconditionClass: Class<*>,
        closure: () -> Any,
    ): Precondition {
        val nestedPrecondition = preconditionClass.newInstance()

        val delegate =
            PreconditionDelegate(
                databaseChangeLog = this.databaseChangeLog,
                changeSetId = this.changeSetId,
            )

        delegate.preconditions.forEach { precondition ->
            nestedPrecondition::class.java
                .getMethod("addNestedPrecondition", Any::class.java)
                .invoke(nestedPrecondition, precondition)
        }

        return nestedPrecondition as Precondition
    }
}
