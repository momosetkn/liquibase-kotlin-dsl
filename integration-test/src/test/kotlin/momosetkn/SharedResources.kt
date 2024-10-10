package momosetkn

import momosetkn.utils.DatabaseServer

object SharedResources {
    val targetDatabaseServer = DatabaseServer()
    val referenceDatabaseServer = DatabaseServer()
}
