import * as vscode from 'vscode';

const REDHAT_JAVA_EXTENSION_ID = 'redhat.java';
const JAVA_EXECUTE_WORKSPACE_COMMAND = 'java.execute.workspaceCommand';
const JAVA_GET_ALL_PROJECTS_COMMAND = 'java.project.getAll';

export interface JavaProjectClasspaths {
    classpaths: string[];
    modulepaths: string[];
}

export interface RedHatJavaExtensionApi {
    getClasspaths(uri: vscode.Uri | string, options?: { scope?: 'runtime' | 'test' }): Promise<JavaProjectClasspaths>;
    serverReady?(): Promise<boolean>;
    onDidClasspathUpdate?: vscode.Event<vscode.Uri>;
    onDidProjectsImport?: vscode.Event<readonly vscode.Uri[]>;
    onDidProjectsDelete?: vscode.Event<readonly vscode.Uri[]>;
}

export type JavaProjectChangeReason =
    | 'server-ready'
    | 'classpath-updated'
    | 'projects-imported'
    | 'projects-deleted';

export function registerJavaProjectChangeListeners(
    context: vscode.ExtensionContext,
    output: vscode.OutputChannel,
    onChange: (reason: JavaProjectChangeReason, uris: readonly vscode.Uri[]) => void
): void {
    let registered = false;
    const tryRegister = async (): Promise<void> => {
        if (registered) {
            return;
        }
        const api = await getRedHatJavaApi(output, { activateIfNeeded: false });
        if (!api) {
            return;
        }
        registered = true;

        const disposables: vscode.Disposable[] = [];
        if (api.onDidClasspathUpdate) {
            disposables.push(api.onDidClasspathUpdate((uri) => onChange('classpath-updated', [uri])));
        }
        if (api.onDidProjectsImport) {
            disposables.push(api.onDidProjectsImport((uris) => onChange('projects-imported', uris)));
        }
        if (api.onDidProjectsDelete) {
            disposables.push(api.onDidProjectsDelete((uris) => onChange('projects-deleted', uris)));
        }
        context.subscriptions.push(...disposables);

        if (typeof api.serverReady === 'function') {
            void api.serverReady()
                .then(() => onChange('server-ready', []))
                .catch((error) => {
                    output.appendLine(`[Zircon] Failed while waiting for redhat.java serverReady: ${String(error)}`);
                });
        }
    };

    void tryRegister();
    context.subscriptions.push(vscode.extensions.onDidChange(() => {
        void tryRegister();
    }));
}

export async function collectJavaProjectDependencyJars(output: vscode.OutputChannel): Promise<string[]> {
    const api = await getRedHatJavaApi(output, { activateIfNeeded: false });
    if (!api) {
        return [];
    }

    await waitForServerReady(api, output);
    const projectUris = await getAllJavaProjectUris(output);
    if (projectUris.length === 0) {
        return [];
    }

    const jarPaths = new Set<string>();
    for (const projectUri of projectUris) {
        for (const scope of ['runtime', 'test'] as const) {
            try {
                const classpaths = await api.getClasspaths(projectUri.toString(), { scope });
                collectJarPaths(classpaths, jarPaths);
            } catch (error) {
                output.appendLine(`[Zircon] Failed to query ${scope} classpaths for ${projectUri.toString()}: ${String(error)}`);
            }
        }
    }

    if (jarPaths.size > 0) {
        output.appendLine(`[Zircon] Resolved ${jarPaths.size} dependency jars from ${projectUris.length} Java projects via redhat.java.`);
    }
    return [...jarPaths].sort((left, right) => left.localeCompare(right));
}

export async function getAllJavaProjectUris(output: vscode.OutputChannel): Promise<vscode.Uri[]> {
    try {
        const raw = await vscode.commands.executeCommand<unknown>(
            JAVA_EXECUTE_WORKSPACE_COMMAND,
            JAVA_GET_ALL_PROJECTS_COMMAND
        );
        return normalizeCommandUris(raw);
    } catch (error) {
        output.appendLine(`[Zircon] Failed to enumerate Java projects via redhat.java: ${String(error)}`);
        return [];
    }
}

export async function getRedHatJavaApi(
    output: vscode.OutputChannel,
    options?: { activateIfNeeded?: boolean }
): Promise<RedHatJavaExtensionApi | undefined> {
    const extension = vscode.extensions.getExtension<unknown>(REDHAT_JAVA_EXTENSION_ID);
    if (!extension) {
        return undefined;
    }

    if (extension.isActive) {
        const direct = asRedHatJavaApi(extension.exports);
        if (direct) {
            return direct;
        }
    }

    if (options?.activateIfNeeded === false) {
        return undefined;
    }

    try {
        const activated = await extension.activate();
        return asRedHatJavaApi(activated)
            ?? (extension.isActive ? asRedHatJavaApi(extension.exports) : undefined);
    } catch (error) {
        output.appendLine(`[Zircon] Failed to activate redhat.java API: ${String(error)}`);
        return undefined;
    }
}

async function waitForServerReady(api: RedHatJavaExtensionApi, output: vscode.OutputChannel): Promise<void> {
    if (typeof api.serverReady !== 'function') {
        return;
    }
    try {
        await Promise.race([
            api.serverReady().then(() => undefined),
            delay(8000)
        ]);
    } catch (error) {
        output.appendLine(`[Zircon] Waiting for redhat.java serverReady failed: ${String(error)}`);
    }
}

function asRedHatJavaApi(candidate: unknown): RedHatJavaExtensionApi | undefined {
    if (!candidate || typeof candidate !== 'object') {
        return undefined;
    }
    const maybeApi = candidate as Partial<RedHatJavaExtensionApi>;
    return typeof maybeApi.getClasspaths === 'function'
        ? maybeApi as RedHatJavaExtensionApi
        : undefined;
}

function collectJarPaths(classpaths: JavaProjectClasspaths | undefined, jarPaths: Set<string>): void {
    if (!classpaths) {
        return;
    }
    const candidates = [
        ...(classpaths.classpaths ?? []),
        ...(classpaths.modulepaths ?? [])
    ];
    for (const candidate of candidates) {
        const jarPath = normalizePathCandidate(candidate);
        if (!jarPath || !jarPath.toLowerCase().endsWith('.jar')) {
            continue;
        }
        jarPaths.add(jarPath);
    }
}

function normalizeCommandUris(raw: unknown): vscode.Uri[] {
    const uris = new Map<string, vscode.Uri>();
    collectUris(raw, uris);
    return [...uris.values()];
}

function collectUris(raw: unknown, uris: Map<string, vscode.Uri>): void {
    if (!raw) {
        return;
    }
    if (raw instanceof vscode.Uri) {
        uris.set(raw.toString(), raw);
        return;
    }
    if (typeof raw === 'string') {
        const uri = toUri(raw);
        if (uri) {
            uris.set(uri.toString(), uri);
        }
        return;
    }
    if (Array.isArray(raw)) {
        for (const item of raw) {
            collectUris(item, uris);
        }
        return;
    }
    if (typeof raw === 'object') {
        const value = raw as Record<string, unknown>;
        for (const key of ['uri', 'projectUri', 'path']) {
            const maybeUri = value[key];
            if (typeof maybeUri === 'string') {
                const uri = toUri(maybeUri);
                if (uri) {
                    uris.set(uri.toString(), uri);
                }
            }
        }
    }
}

function toUri(value: string): vscode.Uri | undefined {
    if (/^[A-Za-z]:[\\/]/.test(value)) {
        return vscode.Uri.file(value);
    }
    if (/^[a-zA-Z][\w+.-]*:/.test(value)) {
        return vscode.Uri.parse(value);
    }
    if (value.length > 0) {
        return vscode.Uri.file(value);
    }
    return undefined;
}

function normalizePathCandidate(value: string): string | undefined {
    if (value.length === 0) {
        return undefined;
    }
    if (/^[A-Za-z]:[\\/]/.test(value)) {
        return value;
    }
    return /^[a-zA-Z][\w+.-]*:/.test(value)
        ? vscode.Uri.parse(value).fsPath
        : value;
}

function delay(milliseconds: number): Promise<void> {
    return new Promise((resolve) => {
        setTimeout(resolve, milliseconds);
    });
}
