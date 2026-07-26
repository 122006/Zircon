import * as vscode from 'vscode';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { registerZirconCodeActions } from './codeActions';
import { getZirconConfig } from './config';
import { ZirconDiagnostics } from './diagnostics';
import { registerExMethodCompletion } from './exMethodCompletion';
import { DEPENDENCY_DOCUMENT_SCHEME, ExMethodIndex } from './exMethodIndex';
import { probeNativeJavaReferences, registerExMethodNavigation } from './exMethodNavigation';
import { registerExMethodSignatureHelp } from './exMethodSignatureHelp';
import { registerZirconEditorExperience } from './editorExperience';
import { ZirconJavaAgentManager } from './javaAgent';
import { registerJavaProjectChangeListeners } from './javaProjectClasspath';
import {
    detectWorkspaceInfo,
    isDocumentInZirconProject,
    ZirconWorkspaceInfo
} from './projectDetector';
import { registerZirconSemanticTokens } from './semanticTokens';
import { ZirconStatusBar } from './statusBar';
import { clearScanCache, scanDocument } from './syntax';
import { isZirconFormattingProxy, registerZirconFormatting } from './zirconFormatting';

export async function activate(context: vscode.ExtensionContext): Promise<void> {
    const output = vscode.window.createOutputChannel('Zircon');
    const diagnosticsLogPath = path.join(os.tmpdir(), 'zircon_vscode_diagnostics.log');
    fs.writeFileSync(diagnosticsLogPath, '');
    const statusBar = new ZirconStatusBar();
    const agentManager = new ZirconJavaAgentManager(context, output);
    const exMethodIndex = new ExMethodIndex(output);
    let workspaceInfo: ZirconWorkspaceInfo = {
        hasWorkspace: false,
        hasJavaFiles: false,
        hasZirconMarkers: false,
        markers: [],
        javaFileCount: 0,
        buildFiles: [],
        zirconProjectRoots: [],
        classpathProjectCount: 0,
        classpathDetectionReady: false
    };
    const documentEnabled = (document: vscode.TextDocument): boolean => {
        const config = getZirconConfig();
        return config.enable && isDocumentInZirconProject(document, workspaceInfo);
    };
    const diagnostics = new ZirconDiagnostics(documentEnabled);
    const codeActionsEnabled = (document?: vscode.TextDocument): boolean => {
        const config = getZirconConfig();
        return config.enable
            && config.enableCodeActions
            && (document ? documentEnabled(document) : workspaceInfo.hasZirconMarkers);
    };
    const editorExperienceEnabled = (document: vscode.TextDocument): boolean => {
        const config = getZirconConfig();
        return config.enable && config.enableEditorExperience && documentEnabled(document);
    };
    const pendingRefreshTimers = new Map<string, NodeJS.Timeout>();
    const diagnosticSnapshots = new Map<string, string>();
    let pendingIndexRebuildTimer: NodeJS.Timeout | undefined;

    const scheduleIndexRebuild = (reason: string): void => {
        if (pendingIndexRebuildTimer) {
            clearTimeout(pendingIndexRebuildTimer);
        }
        pendingIndexRebuildTimer = setTimeout(() => {
            pendingIndexRebuildTimer = undefined;
            void exMethodIndex.rebuild().catch((error) => {
                output.appendLine(`[Zircon] ExMethod index rebuild failed (${reason}): ${String(error)}`);
            });
        }, 1200);
    };

    context.subscriptions.push(
        output,
        diagnostics,
        statusBar,
        exMethodIndex,
        vscode.workspace.registerTextDocumentContentProvider(DEPENDENCY_DOCUMENT_SCHEME, {
            provideTextDocumentContent(uri) {
                return exMethodIndex.getVirtualDocumentContent(uri) ?? '// dependency source unavailable';
            }
        })
    );
    registerZirconSemanticTokens(context, documentEnabled);
    registerExMethodCompletion(
        context,
        exMethodIndex,
        output,
        () => getZirconConfig().enableExperimentalJavaAgent && agentManager.hasActiveAgent(),
        documentEnabled
    );
    registerExMethodNavigation(
        context,
        exMethodIndex,
        output,
        () => getZirconConfig().enableExperimentalJavaAgent && agentManager.hasActiveAgent(),
        documentEnabled
    );
    registerExMethodSignatureHelp(
        context,
        exMethodIndex,
        output,
        documentEnabled
    );
    registerZirconCodeActions(context, exMethodIndex, codeActionsEnabled);
    registerZirconEditorExperience(context, editorExperienceEnabled);
    registerZirconFormatting(context, output, (document) => {
        const config = getZirconConfig();
        return config.enable && documentEnabled(document);
    }, exMethodIndex);
    const javaSourceWatcher = vscode.workspace.createFileSystemWatcher('**/*.java');
    context.subscriptions.push(
        javaSourceWatcher,
        javaSourceWatcher.onDidCreate((uri) => scheduleIndexRebuild(`javaSource:create:${uri.toString()}`)),
        javaSourceWatcher.onDidChange((uri) => scheduleIndexRebuild(`javaSource:change:${uri.toString()}`)),
        javaSourceWatcher.onDidDelete((uri) => scheduleIndexRebuild(`javaSource:delete:${uri.toString()}`))
    );

    const refreshWorkspace = async (forceInject: boolean): Promise<void> => {
        const config = getZirconConfig();
        if (!config.enable) {
            await vscode.commands.executeCommand('setContext', 'zircon.projectActive', false);
            const removed = await agentManager.removeInjectedVmArgsAndScheduleRestart();
            if (removed) {
                output.appendLine('[Zircon] Removed Java agent configuration because the extension is disabled.');
            }
            output.appendLine('[Zircon] Extension disabled by configuration.');
            statusBar.update(workspaceInfo, false);
            diagnostics.refreshOpenEditors();
            return;
        }

        workspaceInfo = await detectWorkspaceInfo(output);
        const activeDocument = vscode.window.activeTextEditor?.document;
        await vscode.commands.executeCommand(
            'setContext',
            'zircon.projectActive',
            Boolean(activeDocument
                && config.enableEditorExperience
                && isDocumentInZirconProject(activeDocument, workspaceInfo))
        );
        scheduleIndexRebuild(`refreshWorkspace:${forceInject ? 'force' : 'normal'}`);
        const injected = await agentManager.ensureInjected(workspaceInfo, forceInject);
        statusBar.update(workspaceInfo, injected);
        diagnostics.refreshOpenEditors();
    };

    let workspaceRefreshQueue: Promise<void> = Promise.resolve();
    const scheduleWorkspaceRefresh = (forceInject: boolean, reason: string): Promise<void> => {
        const pending = workspaceRefreshQueue.then(async (): Promise<void> => {
            try {
                await refreshWorkspace(forceInject);
            } catch (error) {
                output.appendLine(`[Zircon] Workspace refresh failed (${reason}): ${String(error)}`);
            }
        });
        workspaceRefreshQueue = pending.then(() => undefined, () => undefined);
        return pending;
    };
    registerJavaProjectChangeListeners(context, output, (reason, uris) => {
        const suffix = uris.length > 0
            ? ` (${uris.map((uri) => uri.fsPath || uri.toString()).join(', ')})`
            : '';
        output.appendLine(`[Zircon] Java project event: ${reason}${suffix}`);
        scheduleIndexRebuild(`javaProject:${reason}`);
        void scheduleWorkspaceRefresh(false, `javaProject:${reason}`);
    });

    const refreshDocument = (document: vscode.TextDocument): void => {
        if (isZirconFormattingProxy(document)) {
            return;
        }
        cancelScheduledRefresh(document.uri);
        void exMethodIndex.updateDocument(document);
        diagnostics.refresh(document);
        setTimeout(() => {
            logJavaDiagnostics(document.uri, 'refreshDocument');
        }, 1500);
    };

    const cancelScheduledRefresh = (uri: vscode.Uri): void => {
        const key = uri.toString();
        const timer = pendingRefreshTimers.get(key);
        if (!timer) {
            return;
        }
        clearTimeout(timer);
        pendingRefreshTimers.delete(key);
    };

    const scheduleDocumentRefresh = (document: vscode.TextDocument): void => {
        if (document.languageId !== 'java' || isZirconFormattingProxy(document)) {
            return;
        }
        const key = document.uri.toString();
        cancelScheduledRefresh(document.uri);
        pendingRefreshTimers.set(key, setTimeout(() => {
            pendingRefreshTimers.delete(key);
            refreshDocument(document);
        }, 120));
    };

    const logJavaDiagnostics = (uri: vscode.Uri, reason: string): void => {
        const config = getZirconConfig();
        if (!config.debug || uri.scheme !== 'file' || !uri.fsPath.toLowerCase().endsWith('.java')) {
            return;
        }
        const items = vscode.languages.getDiagnostics(uri);
        const errors = items.filter((item) => item.severity === vscode.DiagnosticSeverity.Error);
        const key = uri.toString();
        const snapshot = JSON.stringify(errors.map((item) => ({
            message: item.message,
            source: item.source ?? '',
            code: typeof item.code === 'object' && item.code !== null
                ? String(item.code.value)
                : String(item.code ?? ''),
            start: [item.range.start.line, item.range.start.character],
            end: [item.range.end.line, item.range.end.character]
        })));
        if (diagnosticSnapshots.get(key) === snapshot) {
            return;
        }
        diagnosticSnapshots.set(key, snapshot);
        if (errors.length === 0) {
            fs.appendFileSync(
                diagnosticsLogPath,
                `[Zircon] VSCode diagnostics (${reason}): file=${uri.fsPath}, total=${items.length}, errors=0\n`
            );
            return;
        }
        const header = `[Zircon] VSCode diagnostics (${reason}): file=${uri.fsPath}, total=${items.length}, errors=${errors.length}`;
        output.appendLine(header);
        fs.appendFileSync(diagnosticsLogPath, `${header}\n`);
        for (const item of errors.slice(0, 20)) {
            const code = typeof item.code === 'object' && item.code !== null
                ? String(item.code.value)
                : String(item.code ?? '');
            const codeSuffix = code.length > 0 ? `#${code}` : '';
            const line =
                `[Zircon]   error L${item.range.start.line + 1}:${item.range.start.character + 1}`
                + `-${item.range.end.line + 1}:${item.range.end.character + 1}`
                + ` ${item.source ?? 'unknown'}${codeSuffix}: ${item.message}`;
            output.appendLine(line);
            fs.appendFileSync(diagnosticsLogPath, `${line}\n`);
        }
    };

    context.subscriptions.push(
        vscode.commands.registerCommand('zircon.forceInject', async () => {
            await scheduleWorkspaceRefresh(true, 'forceInject');
            const selection = await vscode.window.showInformationMessage(
                'Zircon 已更新 Java Agent 配置，是否立即重启 Java Language Server？',
                '立即重启'
            );
            if (selection === '立即重启') {
                await agentManager.restartJavaLanguageServer();
            }
        }),
        vscode.commands.registerCommand('zircon.restartJavaServer', async () => {
            await agentManager.restartJavaLanguageServer();
        }),
        vscode.commands.registerCommand('zircon.showProjectStatus', async () => {
            await scheduleWorkspaceRefresh(false, 'showProjectStatus');
            const summary = [
                `hasWorkspace=${workspaceInfo.hasWorkspace}`,
                `hasJavaFiles=${workspaceInfo.hasJavaFiles}`,
                `hasZirconMarkers=${workspaceInfo.hasZirconMarkers}`,
                `javaFileCount=${workspaceInfo.javaFileCount}`,
                `classpathProjectCount=${workspaceInfo.classpathProjectCount}`,
                `classpathDetectionReady=${workspaceInfo.classpathDetectionReady}`,
                `zirconProjectRoots=${workspaceInfo.zirconProjectRoots.join(', ') || 'none'}`,
                `markers=${workspaceInfo.markers.join(', ') || 'none'}`,
                `agentInjected=${agentManager.hasInjectedVmArg()}`,
                `agentActive=${agentManager.hasActiveAgent()}`,
                `exMethodCount=${exMethodIndex.size()}`
            ].join('\n');
            output.appendLine(`[Zircon] Project status\n${summary}`);
            output.show(true);
            void vscode.window.showInformationMessage(`Zircon 状态：${workspaceInfo.hasZirconMarkers ? '已检测到项目标记' : '未检测到项目标记'}。详细信息已输出到 Zircon 面板。`);
        }),
        vscode.commands.registerCommand('zircon.refreshDiagnostics', () => {
            diagnostics.refreshOpenEditors();
        }),
        vscode.commands.registerCommand('zircon.probeNativeJavaSearch', async () => {
            const editor = vscode.window.activeTextEditor;
            if (!editor || editor.document.languageId !== 'java') {
                void vscode.window.showWarningMessage('请先把光标放在 Java 扩展方法声明或调用上。');
                return;
            }
            const position = editor.selection.active;
            const locations = await probeNativeJavaReferences(editor.document, position);
            output.appendLine(
                `[ZirconSearch] native references at ${editor.document.uri.toString()}`
                + `:${position.line + 1}:${position.character + 1} -> ${locations.length}`
            );
            for (const location of locations.slice(0, 50)) {
                output.appendLine(
                    `[ZirconSearch]   ${location.uri.toString()}`
                    + `:${location.range.start.line + 1}:${location.range.start.character + 1}`
                );
            }
            void vscode.window.showInformationMessage(
                `JDT 原生引用查询返回 ${locations.length} 个位置，详情见 Zircon 输出。`
            );
        }),
        {
            dispose: () => {
                for (const timer of pendingRefreshTimers.values()) {
                    clearTimeout(timer);
                }
                pendingRefreshTimers.clear();
                if (pendingIndexRebuildTimer) {
                    clearTimeout(pendingIndexRebuildTimer);
                    pendingIndexRebuildTimer = undefined;
                }
            }
        },
        vscode.workspace.onDidOpenTextDocument(refreshDocument),
        vscode.workspace.onDidSaveTextDocument(refreshDocument),
        vscode.workspace.onDidCloseTextDocument((document) => {
            cancelScheduledRefresh(document.uri);
            clearScanCache(document.uri);
            diagnostics.clear(document);
            diagnosticSnapshots.delete(document.uri.toString());
            if (document.uri.scheme !== 'file') {
                exMethodIndex.remove(document.uri);
            }
        }),
        vscode.workspace.onDidChangeTextDocument((event) => scheduleDocumentRefresh(event.document)),
        vscode.languages.onDidChangeDiagnostics((event) => {
            for (const uri of event.uris) {
                logJavaDiagnostics(uri, 'onDidChangeDiagnostics');
            }
        }),
        vscode.workspace.onDidChangeWorkspaceFolders(async () => {
            await scheduleWorkspaceRefresh(false, 'workspaceFolders');
        }),
        vscode.workspace.onDidChangeConfiguration(async (event) => {
            // Do not react to java.jdt.ls.vmargs here: ensureInjected writes this setting itself.
            // Listening to that write creates feedback loops across multiple VS Code windows.
            if (event.affectsConfiguration('zircon')) {
                await scheduleWorkspaceRefresh(false, 'configuration');
            }
        }),
        vscode.window.onDidChangeActiveTextEditor((editor) => {
            const config = getZirconConfig();
            void vscode.commands.executeCommand(
                'setContext',
                'zircon.projectActive',
                Boolean(editor
                    && config.enableEditorExperience
                    && isDocumentInZirconProject(editor.document, workspaceInfo))
            );
            if (editor && editor.document.languageId === 'java' && getZirconConfig().debug) {
                const result = scanDocument(editor.document);
                output.appendLine(`[Zircon] Active document: ${editor.document.fileName}, templateStrings=${result.templateStringCount}, diagnostics=${result.diagnostics.length}`);
                logJavaDiagnostics(editor.document.uri, 'onDidChangeActiveTextEditor');
            }
        })
    );

    setTimeout(() => {
        void scheduleWorkspaceRefresh(false, 'activate').then(() => {
            const activeEditor = vscode.window.activeTextEditor;
            if (activeEditor && activeEditor.document.languageId === 'java') {
                logJavaDiagnostics(activeEditor.document.uri, 'activate');
            }
        });
    }, 300);
}

export function deactivate(): void {
    // no-op
}
