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
package liquibase.util

import liquibase.exception.UnexpectedLiquibaseException
import liquibase.statement.DatabaseFunction
import liquibase.statement.SequenceCurrentValueFunction
import liquibase.statement.SequenceNextValueFunction
import liquibase.structure.core.ForeignKeyConstraintType
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.math.BigInteger
import java.util.Locale

/**
 * This class is a copy of the ObjectUtil class in Liquibase itself, but patched to work with the
 * Kotlin DSL.  This is a short term hack until Liquibase ParsedNode parsing properly rejects
 * invalid nodes with an error instead of silently ignoring them.  See
 * https://liquibase.jira.com/browse/CORE-1968?focusedCommentId=24201#comment-24201
 * for mor information.
 *
 * @author Nathan Voxland
 * @author Steven C. Saliman
 */
object PatchedObjectUtil {
    private val methodCache = HashMap<Class<*>, Array<Method>>()

    @Throws(IllegalAccessException::class, InvocationTargetException::class)
    fun getProperty(
        obj: Any,
        propertyName: String,
    ): Any {
        val readMethod = getReadMethod(obj, propertyName)
        readMethod?.let {
            return it.invoke(obj)
        }
        throw UnexpectedLiquibaseException(
            "Property '$propertyName' not found on object type " + obj.javaClass.name,
        )
    }

    fun hasProperty(
        obj: Any,
        propertyName: String,
    ): Boolean = hasReadProperty(obj, propertyName) && hasWriteProperty(obj, propertyName)

    fun hasReadProperty(
        obj: Any,
        propertyName: String,
    ): Boolean = getReadMethod(obj, propertyName) != null

    fun hasWriteProperty(
        obj: Any,
        propertyName: String,
    ): Boolean = getWriteMethod(obj, propertyName) != null

    fun setProperty(
        obj: Any?, // todo; confirm this can be null
        propertyName: String,
        propertyValue: String?, // todo; confirm this can be null
    ) {
        val method =
            getWriteMethod(obj, propertyName)
                ?: throw UnexpectedLiquibaseException(
                    "Property '$propertyName' not found on object type " + obj?.javaClass?.name,
                )
        val parameterType = method.parameterTypes[0]
        var finalValue: Any? = propertyValue
        when (parameterType) {
            Boolean::class.java, Boolean::class.javaPrimitiveType ->
                finalValue =
                    java.lang.Boolean.valueOf(propertyValue)

            Integer::class.java -> finalValue = Integer.valueOf(propertyValue)
            Long::class.java -> finalValue = java.lang.Long.valueOf(propertyValue)
            BigInteger::class.java -> finalValue = BigInteger(propertyValue)
            DatabaseFunction::class.java -> finalValue = DatabaseFunction(propertyValue)
            SequenceNextValueFunction::class.java ->
                finalValue =
                    SequenceNextValueFunction(propertyValue)

            SequenceCurrentValueFunction::class.java ->
                finalValue =
                    SequenceCurrentValueFunction(propertyValue)

            else ->
                if (Enum::class.java.isAssignableFrom(parameterType)) {
                    error("not implemented")
//                    @Suppress("UNCHECKED_CAST")
//                    finalValue =
//                        Enum.valueOf(parameterType as Class<out Enum<*>>, propertyValue)
                }
        }
        try {
            method.invoke(obj, finalValue)
        } catch (e: IllegalAccessException) {
            throw UnexpectedLiquibaseException(e)
        } catch (e: IllegalArgumentException) {
            throw UnexpectedLiquibaseException(
                "Cannot call " + method.toString() + " with value of type " +
                    finalValue?.javaClass?.name,
            )
        } catch (e: InvocationTargetException) {
            throw UnexpectedLiquibaseException(e)
        }
    }

    private fun getReadMethod(
        obj: Any,
        propertyName: String,
    ): Method? {
        val getMethodName =
            "get" + propertyName.substring(0, 1).toUpperCase(Locale.ENGLISH) +
                propertyName.substring(1)
        val isMethodName =
            "is" + propertyName.substring(0, 1).toUpperCase(Locale.ENGLISH) +
                propertyName.substring(1)
        val methods = getMethods(obj)
        for (method in methods) {
            if ((method.name == getMethodName || method.name == isMethodName) &&
                method.parameterTypes.isEmpty()
            ) {
                return method
            }
        }
        return null
    }

    private fun getWriteMethod(
        obj: Any?, // todo; confirm this can be null
        propertyName: String,
    ): Method? {
        val methodName =
            "set" + propertyName.substring(0, 1).toUpperCase(Locale.ENGLISH) +
                propertyName.substring(1)
        val alternateName =
            "should" + propertyName.substring(0, 1).toUpperCase(Locale.ENGLISH) +
                propertyName.substring(1)
        checkNotNull(obj)
        val methods = getMethods(obj)
        for (method in methods) {
            if ((method.name == methodName || method.name == alternateName) &&
                method.parameterTypes.size == 1
            ) {
                val c = method.parameterTypes[0]
                if (c == Boolean::class.java ||
                    c == Boolean::class.javaPrimitiveType ||
                    c == Integer::class.java ||
                    c == Long::class.java ||
                    c == BigInteger::class.java ||
                    c == DatabaseFunction::class.java ||
                    c == SequenceNextValueFunction::class.java ||
                    c == SequenceCurrentValueFunction::class.java ||
                    c == String::class.java ||
                    Enum::class.java.isAssignableFrom(c) &&
                    c != ForeignKeyConstraintType::class.java
                ) {
                    return method
                }
            }
        }
        return null
    }

    private fun getMethods(obj: Any): Array<Method> {
        var methods = methodCache[obj.javaClass]
        if (methods == null) {
            methods = obj.javaClass.methods
            methodCache[obj.javaClass] = methods
        }
        return methods ?: arrayOf()
    }
}
