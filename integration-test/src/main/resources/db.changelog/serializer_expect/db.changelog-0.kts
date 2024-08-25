databaseChangeLog {
    changeSet(author = "**********", id = "*************-1") {
        createTable(tableName = "company") {
            column(name = "id", type = "UUID") {
                constraints(nullable = false, primaryKey = true, primaryKeyName = "company_pkey")
            }
            column(name = "name", type = "VARCHAR(256)")
        }
    }

    changeSet(author = "**********", id = "*************-2") {
        createTable(tableName = "employee") {
            column(name = "id", type = "UUID") {
                constraints(nullable = false, primaryKey = true, primaryKeyName = "employee_pkey")
            }
            column(name = "company_id", type = "UUID") {
                constraints(nullable = false)
            }
            column(name = "new_name", type = "VARCHAR(256)")
            column(name = "not_null_name", type = "VARCHAR(256)") {
                constraints(nullable = false)
            }
            column(name = "not_null_name2", type = "VARCHAR(256)") {
                constraints(nullable = false)
            }
        }
    }

    changeSet(author = "**********", id = "*************-3") {
        addForeignKeyConstraint(baseColumnNames = "company_id", baseTableName = "employee", constraintName = "employee_company_id_fkey", deferrable = false, initiallyDeferred = false, onDelete = "CASCADE", onUpdate = "RESTRICT", referencedColumnNames = "id", referencedTableName = "company", validate = true)
    }

}
