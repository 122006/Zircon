import * as path from 'path';
import * as vscode from 'vscode';
import { getJavaConfigurationTarget, getZirconConfig } from './config';
import { ZirconWorkspaceInfo } from './projectDetector';

export class ZirconJavaAgentManager {
    constructor(
        private readonly context: vscode.ExtensionContext,
        private readonly output: vscode.OutputChannel
    ) {}

    public getAgentJarPath(): string {
        return this.context.asAbsolutePath(path.join('server', 'zircon-agent.jar'));
    }

    public async ensureInjected(info: ZirconWorkspaceInfo, force: boolean): Promise<boolean> {
        const config = getZirconConfig();
        if (!config.enable) {
            await this.removeInjectedVmArgsAndScheduleRestart();
            return false;
        }
        if (!config.enableExperimentalJavaAgent) {
            await this.removeInjectedVmArgsAndScheduleRestart();
            if (force) {
                void vscode.window.showWarningMessage('当前 Zircon JDT Agent 仍为实验能力，默认已关闭。请先启用 `zircon.enableExperimentalJavaAgent` 后再尝试注入。');
            }
            return false;
        }
        if (!force && !config.autoInjectJavaAgent) {
            return this.hasInjectedVmArg();
        }
        if (!force && config.onlyInjectWhenProjectUsesZircon && !info.hasZirconMarkers) {
            this.output.appendLine('[Zircon] Skip agent injection because current workspace does not look like a Zircon project.');
            return this.hasInjectedVmArg();
        }

        const agentUri = vscode.Uri.file(this.getAgentJarPath());
        try {
            await vscode.workspace.fs.stat(agentUri);
        } catch {
            this.output.appendLine(`[Zircon] Agent jar not found: ${agentUri.fsPath}`);
            void vscode.window.showWarningMessage('未找到 `server/zircon-agent.jar`，请先构建 VSCode Agent。');
            return false;
        }

        const javaConfig = vscode.workspace.getConfiguration('java');
        const currentVmArgs = javaConfig.get<string>('jdt.ls.vmargs', '') ?? '';
        const nextVmArgs = this.buildNextVmArgs(currentVmArgs, config.debug);

        if (nextVmArgs === currentVmArgs) {
            this.output.appendLine('[Zircon] Java agent vmargs already up to date.');
            return true;
        }

        await javaConfig.update('jdt.ls.vmargs', nextVmArgs, getJavaConfigurationTarget(config));
        this.output.appendLine(`[Zircon] Updated java.jdt.ls.vmargs -> ${nextVmArgs}`);
        return true;
    }

    public async restartJavaLanguageServer(): Promise<void> {
        const commands = await vscode.commands.getCommands(true);
        if (!commands.includes('java.server.restart')) {
            void vscode.window.showWarningMessage('未检测到 `java.server.restart` 命令，请确认已安装 VS Code Java 扩展。');
            return;
        }
        try {
            await vscode.commands.executeCommand('java.server.restart');
        } catch (error) {
            this.output.appendLine(`[Zircon] Failed to restart Java Language Server: ${String(error)}`);
            throw error;
        }
    }

    public hasInjectedVmArg(): boolean {
        const javaConfig = vscode.workspace.getConfiguration('java');
        const vmArgs = javaConfig.get<string>('jdt.ls.vmargs', '') ?? '';
        return hasZirconAgentVmArg(vmArgs);
    }

    public async removeInjectedVmArgsAndScheduleRestart(): Promise<boolean> {
        const removed = await this.removeInjectedVmArgs();
        if (removed) {
            this.scheduleJavaLanguageServerRestart();
        }
        return removed;
    }

    private async removeInjectedVmArgs(): Promise<boolean> {
        const javaConfig = vscode.workspace.getConfiguration('java');
        const inspected = javaConfig.inspect<string>('jdt.ls.vmargs');
        const targets: ReadonlyArray<{
            value: string | undefined;
            target: vscode.ConfigurationTarget.Global | vscode.ConfigurationTarget.Workspace;
        }> = [
            { value: inspected?.globalValue, target: vscode.ConfigurationTarget.Global },
            { value: inspected?.workspaceValue, target: vscode.ConfigurationTarget.Workspace }
        ];
        let removed = false;
        for (const entry of targets) {
            if (entry.value === undefined) {
                continue;
            }
            const sanitizedVmArgs = sanitizeZirconVmArgs(entry.value, getConfiguredAdditionalVmArgs());
            if (sanitizedVmArgs === entry.value) {
                continue;
            }
            await javaConfig.update(
                'jdt.ls.vmargs',
                sanitizedVmArgs.length > 0 ? sanitizedVmArgs : undefined,
                entry.target
            );
            removed = true;
        }
        if (removed) {
            this.output.appendLine('[Zircon] Removed Zircon javaagent vmargs from every configured scope.');
        }
        return removed;
    }

    private buildNextVmArgs(currentVmArgs: string, debug: boolean): string {
        const config = getZirconConfig();
        const sanitized = sanitizeZirconVmArgs(currentVmArgs, getConfiguredAdditionalVmArgs(config));
        const parts: string[] = [];

        if (sanitized.length > 0) {
            parts.push(sanitized);
        }

        parts.push(`-javaagent:"${this.getAgentJarPath()}"`);
        parts.push('-Dzircon.vscode=true'.replace('Z', 'z'));
        parts.push('-Dzircon.forceLocalSuppress=true'.replace('Z', 'z'));
        parts.push(`-Dzircon.debug=${debug}`.replace('Z', 'z'));
        parts.push(`-Dzircon.agent.jar="${this.getAgentJarPath()}"`.replace('Z', 'z'));
        const workspaceRoots = (vscode.workspace.workspaceFolders ?? [])
            .map((folder) => folder.uri.fsPath)
            .filter((folder) => folder.length > 0);
        if (workspaceRoots.length > 0) {
            parts.push(`-Dzircon.workspace.roots="${workspaceRoots.join(path.delimiter)}"`.replace('Z', 'z'));
        }

        const additionalVmArgs = getConfiguredAdditionalVmArgs(config);
        for (const vmArg of additionalVmArgs) {
            if (!parts.includes(vmArg)) {
                parts.push(vmArg);
            }
        }

        return parts.join(' ').trim();
    }

    private scheduleJavaLanguageServerRestart(): void {
        setTimeout(() => {
            void this.restartJavaLanguageServer().catch((error) => {
                this.output.appendLine(`[Zircon] Deferred Java Language Server restart failed: ${String(error)}`);
            });
        }, 5000);
    }
}

const ZIRCON_AGENT_OPTION_NAMES = new Set([
    'zircon.vscode',
    'zircon.forcelocalsuppress',
    'zircon.debug',
    'zircon.agent.jar',
    'zircon.workspace.roots'
]);

export function hasZirconAgentVmArg(vmArgs: string): boolean {
    return splitVmArgs(vmArgs).some(isZirconAgentArgument);
}

export function sanitizeZirconVmArgs(vmArgs: string, additionalVmArgs: readonly string[] = []): string {
    const configuredAdditionalVmArgs = new Set(
        additionalVmArgs.flatMap((argument) => splitVmArgs(argument.trim()))
    );
    return splitVmArgs(vmArgs)
        .filter((argument) => !isZirconAgentArgument(argument))
        .filter((argument) => !isManagedZirconSystemProperty(argument))
        .filter((argument) => !configuredAdditionalVmArgs.has(argument))
        .join(' ');
}

function getConfiguredAdditionalVmArgs(config = getZirconConfig()): string[] {
    const uniqueArgs = new Set<string>();
    for (const configuredValue of [...config.additionalAgentVmArgs, ...config.additionalJdtVmArgs]) {
        for (const vmArg of splitVmArgs(configuredValue.trim())) {
            if (vmArg.length > 0) {
                uniqueArgs.add(vmArg);
            }
        }
    }
    return [...uniqueArgs];
}

function splitVmArgs(vmArgs: string): string[] {
    return vmArgs.match(/(?:[^\s"]+|"[^"]*")+/g) ?? [];
}

function isZirconAgentArgument(argument: string): boolean {
    const match = argument.match(/^-javaagent:(.+)$/i);
    if (!match) {
        return false;
    }
    const jarPath = match[1]
        .split('=', 1)[0]
        .replace(/"/g, '')
        .replace(/\\/g, '/')
        .toLowerCase();
    return jarPath.endsWith('/zircon-agent.jar') || jarPath === 'zircon-agent.jar';
}

function isManagedZirconSystemProperty(argument: string): boolean {
    const match = argument.match(/^-D([^=]+)=/i);
    return match !== null && ZIRCON_AGENT_OPTION_NAMES.has(match[1].toLowerCase());
}
