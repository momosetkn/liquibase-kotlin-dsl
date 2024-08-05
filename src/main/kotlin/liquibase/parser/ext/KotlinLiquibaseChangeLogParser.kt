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

package liquibase.parser.ext

import DatabaseChangeLogDelegate
import liquibase.changelog.ChangeLogParameters
import liquibase.changelog.DatabaseChangeLog
import liquibase.exception.ChangeLogParseException
import liquibase.parser.ChangeLogParser
import liquibase.resource.ResourceAccessor

/**
 * This is the main parser class for the Liquibase Kotlin DSL.  It is the integration point to
 * Liquibase itself.  It must be in the liquibase.parser.ext package to be found by Liquibase at
 * runtime.
 *
 * @author Tim Berglund
 * @author Steven C. Saliman
 */
class KotlinLiquibaseChangeLogParser : ChangeLogParser {
    override fun parse(
        physicalChangeLogLocation: String,
        changeLogParameters: ChangeLogParameters,
        resourceAccessor: ResourceAccessor,
    ): DatabaseChangeLog {
        var physicalChangeLogLocation = physicalChangeLogLocation.replace("\\\\", "/")
        val inputStream =
            resourceAccessor.openStream(null, physicalChangeLogLocation)
                ?: throw ChangeLogParseException(
                    "$physicalChangeLogLocation does not exist",
                )

        return try {
            val changeLog =
                DatabaseChangeLog(physicalChangeLogLocation).apply {
                    this.changeLogParameters = changeLogParameters
                }

            val binding = Binding()
            val shell = KotlinShell(binding)

            val script = shell.parse(InputStreamReader(inputStream, "UTF8"))
            script.getDatabaseChangeLog = { changeLog }
            script.getResourceAccessor = { resourceAccessor }
            script.methodMissing = getChangeLogMethodMissing(changeLog)
            script.run()

            changeLog
        } finally {
            try {
                inputStream.close()
            } catch (ignored: Exception) {
                // Can't do much more than hope for the best here
            }
        }
    }

    fun supports(
        changeLogFile: String,
        resourceAccessor: ResourceAccessor,
    ) = changeLogFile.endsWith(".groovy")

    fun getPriority() = PRIORITY_DEFAULT

    private fun getChangeLogMethodMissing(changeLog: DatabaseChangeLog): (String, Any) -> Unit =
        { name: String, args: Any ->
            if (name == "databaseChangeLog") {
                processDatabaseChangeLogRootElement(changeLog, resourceAccessor, args)
            } else {
                throw ChangeLogParseException("Unrecognized root element $name")
            }
        }

    private fun processDatabaseChangeLogRootElement(
        databaseChangeLog: DatabaseChangeLog,
        resourceAccessor: ResourceAccessor,
        args: Any,
    ) {
        when (args) {
            is Closure<*> -> {
                throw ChangeLogParseException("databaseChangeLog element cannot be empty")
            }

            is List<*> -> {
                if (args.size > 2 || args.size < 1) {
                    throw ChangeLogParseException(
                        "databaseChangeLog element has too many parameters: $args",
                    )
                }

                val closure = args[1]
                if (closure !is Closure<*>) {
                    throw ChangeLogParseException(
                        "databaseChangeLog element must be followed by a closure (databaseChangeLog { ... })",
                    )
                }
                val delegate = DatabaseChangeLogDelegate(databaseChangeLog, resourceAccessor)
                closure.delegate = delegate
                closure.resolveStrategy = Closure.DELEGATE_FIRST
                closure.call()
            }

            else -> {
                throw ChangeLogParseException(
                    "databaseChangeLog element has invalid parameters: $args",
                )
            }
        }
    }
}
