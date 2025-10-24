package net.milosvasic.logger

/**
 * Simple logging facade for Core/Framework module.
 *
 * This is a basic implementation that delegates to java.util.logging.
 * Applications using Core/Framework can replace this with their own logging implementation.
 *
 * @since 3.1.0
 */
object Log {

    private val logger = java.util.logging.Logger.getLogger("MailServerFactory")
    private const val TAG = "MailServerFactory"

    init {
        // Basic configuration
        logger.level = java.util.logging.Level.ALL
    }

    // Single-parameter methods (message only)
    fun v(message: String) {
        logger.finest(message)
    }

    fun d(message: String) {
        logger.fine(message)
    }

    fun i(message: String) {
        logger.info(message)
    }

    fun w(message: String) {
        logger.warning(message)
    }

    fun e(message: String) {
        logger.severe(message)
    }

    // Two-parameter methods (tag + message)
    fun v(tag: String, message: String) {
        logger.finest("[$tag] $message")
    }

    fun d(tag: String, message: String) {
        logger.fine("[$tag] $message")
    }

    fun i(tag: String, message: String) {
        logger.info("[$tag] $message")
    }

    fun w(tag: String, message: String) {
        logger.warning("[$tag] $message")
    }

    fun w(tag: String, exception: Exception) {
        logger.warning("[$tag] ${exception.message}")
        logger.log(java.util.logging.Level.WARNING, "Exception: ", exception)
    }

    fun e(tag: String, message: String) {
        logger.severe("[$tag] $message")
    }

    fun e(tag: String, exception: Exception) {
        logger.severe("[$tag] ${exception.message}")
        logger.log(java.util.logging.Level.SEVERE, "Exception: ", exception)
    }

    // Exception-only method
    fun e(exception: Exception) {
        logger.severe(exception.message ?: "Exception occurred")
        logger.log(java.util.logging.Level.SEVERE, "Exception: ", exception)
    }
}
