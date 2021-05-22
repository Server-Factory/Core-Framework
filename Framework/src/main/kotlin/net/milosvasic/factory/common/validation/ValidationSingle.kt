package net.milosvasic.factory.common.validation

interface ValidationSingle<T> {

    fun validate(what: T): Boolean
}