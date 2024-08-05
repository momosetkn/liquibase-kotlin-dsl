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

import KotlinChange
import groovy.sql.Sql
import liquibase.changelog.ChangeSet
import liquibase.database.Database
import liquibase.database.DatabaseConnection
import java.sql.Connection

/**
 * <p></p>
 *
 * @author Tim Berglund
 */
class KotlinChangeDelegate(
    val groovyChangeClosure: Closure<*>,
    val changeSet: ChangeSet,
) {
    lateinit var change: KotlinChange
    lateinit var initClosure: Closure<*>
    lateinit var validateClosure: Closure<*>
    lateinit var changeClosure: Closure<*>
    lateinit var rollbackClosure: Closure<*>
    var confirmationMessage: String = ""
    var checksum: String = ""
    lateinit var database: Database
    lateinit var databaseConnection: DatabaseConnection
    lateinit var connection: Connection
    lateinit var sql: Sql

    fun init(c: Closure<*>) {
        c.delegate = this
        initClosure = c
    }

    fun validate(c: Closure<*>) {
        c.delegate = this
        validateClosure = c
    }

    fun change(c: Closure<*>) {
        c.delegate = this
        changeClosure = c
    }

    fun rollback(c: Closure<*>) {
        c.delegate = this
        rollbackClosure = c
    }

    fun confirm(message: String) {
        confirmationMessage = message
    }

    fun checkSum(checkSum: String) {
        this.checksum = checkSum
    }
}
