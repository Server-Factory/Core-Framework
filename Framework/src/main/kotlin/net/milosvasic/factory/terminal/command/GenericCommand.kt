package net.milosvasic.factory.terminal.command

import net.milosvasic.factory.terminal.TerminalCommand

/**
 * Generic terminal command for executing arbitrary shell commands.
 *
 * @param command The command string to execute
 * @author Mail Server Factory Team
 * @since 3.1.0
 */
class GenericCommand(command: String) : TerminalCommand(command)
