`/*
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

import liquibase.exception.ChangeLogParseException

/**
 * A general-purpose delegate class to provide key/value support in a builder.  This delegate
 * supports 2 ways of creating the key/value pairs.  We can pass them in a manner consistent with
 * the XML, namely a series of {@code param ( name : ' someName ' , value : ' someValue ' ) )
 * elements.  The Kotlin DSL parser also supports a simpler mechanism whereby any method in the
 * closure is assumed to be the key and the method's arguments are assumed to be the value.  So the
 * code snippet above becomes {@code someName ( ' someValue ' )}*
 * <p>
 * The map created by this delegate will not do database changeLog property  substitution, that will
 * be up to the caller.
 *
 * @author Steven C. Saliman
 */
class KeyValueDelegate(
    private val changeSetId: String = "<unknown>" // used for error messages
) {
     var map = mutableMapOf<String, Any?>()

    /**
     * This method supports the standard XML like method of passing a name/value pair inside a
     * {@code param} method
     * @param params
     */
    fun param(params: Map<*, *>) {
        var mapKey: Any? = null
        var mapValue: Any? = null
        for ((key, value) in params) {
            when (key) {
                "name" -> mapKey = value
                "value" -> mapValue = value
                else -> throw ChangeLogParseException(
                    "ChangeSet '$changeSetId': '$key' is an invalid property for 'customPrecondition' parameters.",
                )
            }
        }

        // we don't need a value, but we do need a key
        if (mapKey == null) {
            throw ChangeLogParseException(
                "ChangeSet '$changeSetId': 'customPrecondition' parameters need at least a name.",
            )
        }
        map[mapKey] = mapValue
    }

    /**
     * This method supports the Kotlin DSL mechanism of passing a name/value pair by using the
     * method name as the key and the method arguments as the value.
     * @param name
     * @param args
     */
    fun methodMissing(
        name: String,
        args: List<Any?>,
    ) {
        map[name] = if (args.isNotEmpty()) args[0] else args
    }
}
