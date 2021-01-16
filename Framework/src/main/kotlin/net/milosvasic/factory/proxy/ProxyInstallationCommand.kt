package net.milosvasic.factory.proxy

import net.milosvasic.factory.terminal.TerminalCommand
import net.milosvasic.factory.terminal.command.Commands

class ProxyInstallationCommand(proxy: Proxy) : TerminalCommand(Commands.installProxy(proxy))