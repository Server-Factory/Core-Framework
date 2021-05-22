package net.milosvasic.factory.common.validation

interface Validation<T> {

    fun validate(vararg what: T): Boolean
}