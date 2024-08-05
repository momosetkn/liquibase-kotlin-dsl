package org.liquibase.kotlin.delegate

import liquibase.exception.ChangeLogParseException

class ArgumentDelegate {
    private var args = mutableListOf<Any>()
    private val changeSetId = "<unknown>" // used for error messages
    private val changeName = "<unknown>" // used for error messages

    /**
     * Process an argument where the argument is simply a string.  This is not how the Liquibase XML
     * works, but it is really nice shorthand.
     * @param value the argument to add
     */
    fun arg(value: String) {
        args.add(value)
    }

    /**
     * Process an argument where the argument is in the {@code value} entry of the given map.  This
     * is consistent with how Liquibase XML works.
     * @param valueMap the map containing the argument.
     */
    fun arg(valueMap: Map<String, Any>) {
        // we want a helpful message if the value map has anything other than a "value" key.
        valueMap.forEach { key, value ->
            if (key == "value") {
                args.add(value)
            } else {
                throw ChangeLogParseException(
                    "ChangeSet '$changeSetId': '$key' is not a valid argument attribute of $changeName changes",
                )
            }
        }
    }

    /**
     * Kotlin handles non-existent method calls through the invocation API. We override this to tell
     * the user which changeSet had the invalid element.
     * @param name the name of the method Kotlin attempted to call.
     * @param args the original arguments to that method.
     */
    @Throws(NoSuchMethodException::class)
    fun noSuchMethod(name: String): Unit =
        throw ChangeLogParseException(
            "ChangeSet '$changeSetId': '$name' is not a valid child element of $changeName changes",
        )
}
