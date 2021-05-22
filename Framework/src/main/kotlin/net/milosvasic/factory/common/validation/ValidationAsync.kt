package net.milosvasic.factory.common.validation

interface ValidationAsync <T> {

    fun validate(what: T, callback: ValidationCallback)
}