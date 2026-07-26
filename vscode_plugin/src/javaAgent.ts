import * as crypto from 'crypto';
import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import { getJavaConfigurationTarget, getZirconConfig } from './config';
import { ZirconWorkspaceInfo } from './projectDetector';

export class ZirconJavaAgentManager {
    private injectionQueue: Promise<void> = Promise.resolve();
    private restartTimer: NodeJS.Timeout | undefined;

    constructor(
        private readonly context: vscode.ExtensionContext,
        private readonly output: vscode.OutputChannel
    ) {}

    public getAgentJarPath(): string {
        return this.context.asAbsolutePath(path.join('server', 'zircon-agent.jar'));
    }

    public getHeartbeatPath(): string {
        const workspaceKey = crypto.createHash('sha256')
            .update((vscode.workspace.workspaceFolders ?? [])
                .map((folder) => canonicalizePath(folder.uri.fsPath))
                .sort()
                .join('\0'))
            .digest('hex')
            .slice(0, 16);
        return path.join(
            this.context.globalStorageUri.fsPath,
            `zircon-jdt-agent-${workspaceKey || 'empty'}.properties`
        );
    }

    public async ensureInjected(info: ZirconWorkspaceInfo, force: boolean): Promise<boolean> {
        const pending = this.injectionQueue.then(() => this.ensureInjectedNow(info, force));
        this.injectionQueue = pending.then(() => undefined, () => undefined);
        return pending;
    }

    private async ensureInjectedNow(info: ZirconWorkspaceInfo, force: boolean): Promise<boolean> {
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

        if (areVmArgsEquivalent(nextVmArgs, currentVmArgs)) {
            this.output.appendLine('[Zircon] Java agent vmargs already up to date.');
            if (!this.hasActiveAgent()) {
                // A saved VM argument is configuration, not proof that the
                // already-running JDT process loaded it. Verify again after
                // startup and restart only if the full server never writes its
                // heartbeat.
                this.scheduleInactiveAgentVerification(4000);
            }
            return true;
        }

        await javaConfig.update('jdt.ls.vmargs', nextVmArgs, getJavaConfigurationTarget(config));
        this.output.appendLine(`[Zircon] Updated java.jdt.ls.vmargs -> ${nextVmArgs}`);
        if (!force) {
            // The Java extension may already have started JDT LS before Zircon's
            // workspace scan finishes. Restart once after the first injection so the
            // source of truth is the running VM, not merely the saved setting.
            this.scheduleJavaLanguageServerRestart(1200);
        }
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

    public hasActiveAgent(): boolean {
        if (!this.hasInjectedVmArg()) {
            return false;
        }
        return readActiveAgentHeartbeat(this.getHeartbeatPath(), this.getAgentJarPath());
    }

    public async removeInjectedVmArgsAndScheduleRestart(): Promise<boolean> {
        const removed = await this.removeInjectedVmArgs();
        if (removed) {
            this.scheduleJavaLanguageServerRestart(1200);
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
        parts.push(`-Dzircon.debug=${debug}`.replace('Z', 'z'));
        parts.push(`-Dzircon.agent.jar="${this.getAgentJarPath()}"`.replace('Z', 'z'));
        parts.push(`-Dzircon.agent.heartbeat="${this.getHeartbeatPath()}"`.replace('Z', 'z'));
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

    private scheduleJavaLanguageServerRestart(delayMs: number): void {
        if (this.restartTimer) {
            clearTimeout(this.restartTimer);
        }
        this.restartTimer = setTimeout(() => {
            this.restartTimer = undefined;
            void this.restartJavaLanguageServer().catch((error) => {
                this.output.appendLine(`[Zircon] Deferred Java Language Server restart failed: ${String(error)}`);
            });
        }, delayMs);
    }

    private scheduleInactiveAgentVerification(delayMs: number): void {
        if (this.restartTimer) {
            return;
        }
        this.restartTimer = setTimeout(() => {
            this.restartTimer = undefined;
            if (this.hasActiveAgent()) {
                this.output.appendLine('[Zircon] JDT Agent runtime heartbeat detected.');
                return;
            }
            this.output.appendLine('[Zircon] JDT Agent VM argument exists but no runtime heartbeat was detected; restarting Java Language Server.');
            void this.restartJavaLanguageServer().catch((error) => {
                this.output.appendLine(`[Zircon] Inactive-agent restart failed: ${String(error)}`);
            });
        }, delayMs);
    }
}

const ZIRCON_AGENT_OPTION_NAMES = new Set([
    'zircon.vscode',
    'zircon.forcelocalsuppress',
    'zircon.debug',
    'zircon.debug.problemfiles',
    'zircon.debug.selectors',
    'zircon.trace',
    'zircon.trace.selectors',
    'zircon.agent.jar',
    'zircon.agent.heartbeat',
    'zircon.workspace.roots'
]);

export function readActiveAgentHeartbeat(
    heartbeatPath: string,
    expectedAgentJar: string,
    isProcessAlive: (pid: number) => boolean = defaultProcessAlive
): boolean {
    try {
        const values = new Map<string, string>();
        for (const line of fs.readFileSync(heartbeatPath, 'utf8').split(/\r?\n/)) {
            const separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            values.set(line.slice(0, separator), line.slice(separator + 1));
        }
        const pid = Number(values.get('pid'));
        const startedAt = Number(values.get('startedAt'));
        const agentJar = values.get('agentJar') ?? '';
        if (!Number.isSafeInteger(pid)
                || pid <= 0
                || !Number.isFinite(startedAt)
                || values.get('mode') !== 'full'
                || canonicalizePath(agentJar) !== canonicalizePath(expectedAgentJar)
                || !isProcessAlive(pid)) {
            return false;
        }
        const agentModifiedAt = fs.statSync(expectedAgentJar).mtimeMs;
        return startedAt + 1000 >= agentModifiedAt;
    } catch {
        return false;
    }
}

function defaultProcessAlive(pid: number): boolean {
    try {
        process.kill(pid, 0);
        return true;
    } catch (error) {
        return (error as NodeJS.ErrnoException).code === 'EPERM';
    }
}

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

export function areVmArgsEquivalent(left: string, right: string): boolean {
    const leftArgs = splitVmArgs(left).map(canonicalizeVmArg);
    const rightArgs = splitVmArgs(right).map(canonicalizeVmArg);
    return leftArgs.length === rightArgs.length
        && leftArgs.every((argument, index) => argument === rightArgs[index]);
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

function canonicalizeVmArg(argument: string): string {
    const javaAgentMatch = argument.match(/^-javaagent:(.+)$/i);
    if (javaAgentMatch) {
        const separator = javaAgentMatch[1].indexOf('=');
        const rawPath = separator >= 0 ? javaAgentMatch[1].slice(0, separator) : javaAgentMatch[1];
        const options = separator >= 0 ? javaAgentMatch[1].slice(separator) : '';
        return `-javaagent:${canonicalizePath(rawPath)}${options}`;
    }

    const propertyMatch = argument.match(/^-D([^=]+)=(.*)$/i);
    if (!propertyMatch) {
        return argument;
    }
    const name = propertyMatch[1].toLowerCase();
    if (!ZIRCON_AGENT_OPTION_NAMES.has(name)) {
        return argument;
    }
    let value = propertyMatch[2];
    if (name === 'zircon.agent.jar' || name === 'zircon.agent.heartbeat') {
        value = canonicalizePath(value);
    } else if (name === 'zircon.workspace.roots') {
        value = stripWrappingQuotes(value)
            .split(path.delimiter)
            .map(canonicalizePath)
            .join(path.delimiter);
    } else if (name === 'zircon.vscode'
            || name === 'zircon.forcelocalsuppress'
            || name === 'zircon.debug') {
        value = stripWrappingQuotes(value).toLowerCase();
    }
    return `-D${name}=${value}`;
}

function canonicalizePath(value: string): string {
    const normalized = stripWrappingQuotes(value).replace(/\\/g, '/');
    return /^[a-z]:\//i.test(normalized) || normalized.startsWith('//')
        ? normalized.toLowerCase()
        : normalized;
}

function stripWrappingQuotes(value: string): string {
    return value.length >= 2 && value.startsWith('"') && value.endsWith('"')
        ? value.slice(1, -1)
        : value;
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
