package momosetkn.sql

import java.util.Properties
import java.util.concurrent.Executor

/**
 * Not close connection.
 * Closed by liquibase.
 * therefore, not close in custom-change.
 */
@Suppress("TooManyFunctions")
class NotCloseConnectionProxy(
    private val connection: java.sql.Connection,
) : java.sql.Connection {
    override fun createStatement(): java.sql.Statement {
        return connection.createStatement()
    }

    override fun prepareStatement(sql: String?): java.sql.PreparedStatement {
        return connection.prepareStatement(sql)
    }

    override fun prepareStatement(sql: String?, autoGeneratedKeys: Int): java.sql.PreparedStatement {
        return connection.prepareStatement(sql, autoGeneratedKeys)
    }

    override fun prepareStatement(sql: String?, columnIndexes: IntArray?): java.sql.PreparedStatement {
        return connection.prepareStatement(sql, columnIndexes)
    }

    override fun prepareStatement(
        sql: String?,
        resultSetType: Int,
        resultSetConcurrency: Int
    ): java.sql.PreparedStatement {
        return connection.prepareStatement(sql, resultSetType, resultSetConcurrency)
    }

    override fun prepareStatement(
        sql: String?,
        resultSetType: Int,
        resultSetConcurrency: Int,
        resultSetHoldability: Int
    ): java.sql.PreparedStatement {
        return connection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability)
    }

    override fun prepareStatement(sql: String?, columnNames: Array<out String>?): java.sql.PreparedStatement {
        return connection.prepareStatement(sql, columnNames)
    }

    override fun prepareCall(sql: String?): java.sql.CallableStatement {
        return connection.prepareCall(sql)
    }

    override fun prepareCall(sql: String?, resultSetType: Int, resultSetConcurrency: Int): java.sql.CallableStatement {
        return connection.prepareCall(sql, resultSetType, resultSetConcurrency)
    }

    override fun prepareCall(
        sql: String?,
        resultSetType: Int,
        resultSetConcurrency: Int,
        resultSetHoldability: Int
    ): java.sql.CallableStatement {
        return connection.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability)
    }

    override fun nativeSQL(sql: String?): String {
        return connection.nativeSQL(sql)
    }

    override fun setAutoCommit(autoCommit: Boolean) {
        connection.setAutoCommit(autoCommit)
    }

    override fun getAutoCommit(): Boolean {
        return connection.getAutoCommit()
    }

    override fun commit() {
        connection.commit()
    }

    override fun rollback() {
        connection.rollback()
    }

    override fun <T : Any?> unwrap(iface: Class<T>?): T {
        return connection.unwrap(iface)
    }

    override fun isWrapperFor(iface: Class<*>?): Boolean {
        return connection.isWrapperFor(iface)
    }

    override fun close() {
        // Do nothing
    }

    override fun isClosed(): Boolean {
        return connection.isClosed()
    }

    override fun getMetaData(): java.sql.DatabaseMetaData {
        return connection.getMetaData()
    }

    override fun setReadOnly(readOnly: Boolean) {
        connection.setReadOnly(readOnly)
    }

    override fun isReadOnly(): Boolean {
        return connection.isReadOnly()
    }

    override fun setCatalog(catalog: String?) {
        connection.setCatalog(catalog)
    }

    override fun getCatalog(): String {
        return connection.getCatalog()
    }

    override fun setTransactionIsolation(level: Int) {
        connection.setTransactionIsolation(level)
    }

    override fun getTransactionIsolation(): Int {
        return connection.getTransactionIsolation()
    }

    override fun getWarnings(): java.sql.SQLWarning {
        return connection.getWarnings()
    }

    override fun clearWarnings() {
        connection.clearWarnings()
    }

    override fun createStatement(resultSetType: Int, resultSetConcurrency: Int): java.sql.Statement {
        return connection.createStatement(resultSetType, resultSetConcurrency)
    }

    override fun createStatement(
        resultSetType: Int,
        resultSetConcurrency: Int,
        resultSetHoldability: Int
    ): java.sql.Statement {
        return connection.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability)
    }

    override fun getTypeMap(): MutableMap<String, Class<*>> {
        return connection.getTypeMap()
    }

    override fun setTypeMap(map: MutableMap<String, Class<*>>?) {
        connection.setTypeMap(map)
    }

    override fun setHoldability(holdability: Int) {
        connection.setHoldability(holdability)
    }

    override fun getHoldability(): Int {
        return connection.getHoldability()
    }

    override fun setSavepoint(): java.sql.Savepoint {
        return connection.setSavepoint()
    }

    override fun setSavepoint(name: String?): java.sql.Savepoint {
        return connection.setSavepoint(name)
    }

    override fun rollback(savepoint: java.sql.Savepoint?) {
        connection.rollback(savepoint)
    }

    override fun releaseSavepoint(savepoint: java.sql.Savepoint?) {
        connection.releaseSavepoint(savepoint)
    }

    override fun createClob(): java.sql.Clob {
        return connection.createClob()
    }

    override fun createBlob(): java.sql.Blob {
        return connection.createBlob()
    }

    override fun createNClob(): java.sql.NClob {
        return connection.createNClob()
    }

    override fun createSQLXML(): java.sql.SQLXML {
        return connection.createSQLXML()
    }

    override fun isValid(timeout: Int): Boolean {
        return connection.isValid(timeout)
    }

    override fun setClientInfo(name: String?, value: String?) {
        connection.setClientInfo(name, value)
    }

    override fun setClientInfo(properties: Properties?) {
        connection.setClientInfo(properties)
    }

    override fun getClientInfo(name: String?): String {
        return connection.getClientInfo(name)
    }

    override fun getClientInfo(): Properties {
        return connection.getClientInfo()
    }

    override fun createArrayOf(typeName: String?, elements: Array<out Any>?): java.sql.Array {
        return connection.createArrayOf(typeName, elements)
    }

    override fun createStruct(typeName: String?, attributes: Array<out Any>?): java.sql.Struct {
        return connection.createStruct(typeName, attributes)
    }

    override fun setSchema(schema: String?) {
        connection.setSchema(schema)
    }

    override fun getSchema(): String {
        return connection.getSchema()
    }

    override fun abort(executor: Executor?) {
        connection.abort(executor)
    }

    override fun setNetworkTimeout(executor: Executor?, milliseconds: Int) {
        connection.setNetworkTimeout(executor, milliseconds)
    }

    override fun getNetworkTimeout(): Int {
        return connection.getNetworkTimeout()
    }
}
