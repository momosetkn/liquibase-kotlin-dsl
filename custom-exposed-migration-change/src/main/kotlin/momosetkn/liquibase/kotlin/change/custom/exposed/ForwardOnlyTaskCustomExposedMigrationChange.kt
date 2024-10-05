package momosetkn.liquibase.kotlin.change.custom.exposed

import liquibase.change.custom.CustomChange
import liquibase.change.custom.CustomTaskChange
import liquibase.database.Database
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor

class ForwardOnlyTaskCustomExposedMigrationChange(
    private val define: CustomTaskChangeDefineImpl,
) : CustomChange, CustomTaskChange {
    private var resourceAccessor: ResourceAccessor? = null
    override fun getConfirmationMessage(): String {
        return define.confirmationMessage
    }

    override fun setUp() = Unit

    override fun setFileOpener(resourceAccessor: ResourceAccessor) {
        this.resourceAccessor = resourceAccessor
    }

    override fun validate(database: Database): ValidationErrors {
        return define.validateBlock(database.toExposedMigrationDatabase())
    }

    override fun execute(database: Database) {
        define.executeBlock(database.toExposedMigrationDatabase())
    }
}
