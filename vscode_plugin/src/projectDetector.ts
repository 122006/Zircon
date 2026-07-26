import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import * as yauzl from 'yauzl';
import { getAllJavaProjectUris, getRedHatJavaApi } from './javaProjectClasspath';

export interface ZirconWorkspaceInfo {
    hasWorkspace: boolean;
    hasJavaFiles: boolean;
    hasZirconMarkers: boolean;
    markers: string[];
    javaFileCount: number;
    buildFiles: string[];
    /**
     * JDT project roots whose resolved classpath contains zircon.ExMethod.
     * Text-marker roots are retained only while the Java project model is not ready.
     */
    zirconProjectRoots: string[];
    classpathProjectCount: number;
    classpathDetectionReady: boolean;
}

const SEARCH_EXCLUDE = '**/{node_modules,build,out,.git,.gradle}/**';
const ZIRCON_CLASS_ENTRY = 'zircon/ExMethod.class';
const ZIRCON_MARKERS: ReadonlyArray<{ text: string; label: string }> = [
    { text: 'com.github.122006.Zircon', label: 'Maven/Gradle 依赖' },
    { text: 'artifactId>javac<', label: 'Maven javac 依赖' },
    { text: 'artifactId>zircon<', label: 'Maven zircon 依赖' },
    { text: '-Xplugin:ZrString', label: '编译参数 ZrString' },
    { text: '-Xplugin:ZrExMethod', label: '编译参数 ZrExMethod' },
    { text: '-Xplugin:ZrOptionalChain', label: '编译参数 ZrOptionalChain' },
    { text: 'apply plugin: \'zircon\'', label: 'Gradle zircon 插件' },
    { text: 'id("zircon")', label: 'Gradle zircon 插件 ID' },
    { text: 'import zircon.', label: 'Java import zircon.*' },
    { text: '@ExMethod', label: '拓展方法注解' }
];

export async function detectWorkspaceInfo(output: vscode.OutputChannel): Promise<ZirconWorkspaceInfo> {
    const folders = vscode.workspace.workspaceFolders ?? [];
    if (folders.length === 0) {
        return {
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
    }

    const [javaFiles, gradleFiles, pomFiles, javaProjects] = await Promise.all([
        vscode.workspace.findFiles('**/*.java', SEARCH_EXCLUDE),
        vscode.workspace.findFiles('**/*.{gradle,gradle.kts}', SEARCH_EXCLUDE),
        vscode.workspace.findFiles('**/pom.xml', SEARCH_EXCLUDE),
        getAllJavaProjectUris(output)
    ]);

    const buildFiles = [...gradleFiles, ...pomFiles];
    const classpathRoots = await detectClasspathProjectRoots(javaProjects, output);
    const classpathDetectionReady = classpathRoots !== undefined;
    const resolvedClasspathRoots = classpathRoots ?? [];
    const markers = new Set<string>();
    const markerRoots = new Set<string>();
    if (classpathDetectionReady) {
        if (resolvedClasspathRoots.length > 0) {
            markers.add('JDT classpath: zircon.ExMethod');
        }
    } else {
        // Content markers are a startup fallback only. Once JDT has resolved
        // project classpaths, no Java source files are opened for detection.
        for (const uri of [...buildFiles, ...javaFiles]) {
            const root = resolveOwningProjectRoot(uri, javaProjects, folders);
            if (markerRoots.has(root)) {
                continue;
            }
            const content = await readText(uri);
            if (!content) {
                continue;
            }
            let matched = false;
            for (const marker of ZIRCON_MARKERS) {
                if (content.includes(marker.text)) {
                    markers.add(marker.label);
                    matched = true;
                }
            }
            if (matched) {
                markerRoots.add(root);
            }
        }
    }

    // Once JDT exposes projects, its classpath is authoritative. Before serverReady
    // we retain marker roots so startup features do not briefly disappear.
    const zirconProjectRoots = classpathDetectionReady
        ? resolvedClasspathRoots
        : javaProjects.length > 0
            ? [...markerRoots].filter((root) => javaProjects.some((project) => samePath(project.fsPath, root)))
            : [...markerRoots];

    output.appendLine(
        `[Zircon] Java files: ${javaFiles.length}, build files: ${buildFiles.length}, `
        + `JDT projects: ${javaProjects.length}, Zircon project roots: ${zirconProjectRoots.length}, `
        + `markers: ${[...markers].join(', ') || 'none'}`
    );

    return {
        hasWorkspace: true,
        hasJavaFiles: javaFiles.length > 0,
        hasZirconMarkers: markers.size > 0,
        markers: [...markers].sort(),
        javaFileCount: javaFiles.length,
        buildFiles: buildFiles.map((uri) => vscode.workspace.asRelativePath(uri)),
        zirconProjectRoots: zirconProjectRoots.sort((left, right) => left.localeCompare(right)),
        classpathProjectCount: resolvedClasspathRoots.length,
        classpathDetectionReady
    };
}

export function isDocumentInZirconProject(
    document: vscode.TextDocument,
    info: ZirconWorkspaceInfo
): boolean {
    if (!info.hasZirconMarkers || document.languageId !== 'java') {
        return false;
    }
    if (document.uri.scheme !== 'file') {
        return info.zirconProjectRoots.length > 0 || !info.classpathDetectionReady;
    }
    if (info.zirconProjectRoots.length === 0) {
        return !info.classpathDetectionReady;
    }
    return info.zirconProjectRoots.some((root) => isPathWithin(document.uri.fsPath, root));
}

async function detectClasspathProjectRoots(
    projectUris: readonly vscode.Uri[],
    output: vscode.OutputChannel
): Promise<string[] | undefined> {
    const api = await getRedHatJavaApi(output, { activateIfNeeded: false });
    if (!api || projectUris.length === 0) {
        return undefined;
    }
    if (typeof api.serverReady === 'function') {
        try {
            const ready = await Promise.race([
                api.serverReady(),
                new Promise<boolean>((resolve) => setTimeout(() => resolve(false), 8000))
            ]);
            if (!ready) {
                return undefined;
            }
        } catch {
            return undefined;
        }
    }

    const roots: string[] = [];
    const markerCache = new Map<string, Promise<boolean>>();
    for (const projectUri of projectUris) {
        let matched = false;
        for (const scope of ['runtime', 'test'] as const) {
            try {
                const resolved = await api.getClasspaths(projectUri.toString(), { scope });
                const entries = [...(resolved.classpaths ?? []), ...(resolved.modulepaths ?? [])];
                for (const entry of entries) {
                    let pending = markerCache.get(entry);
                    if (!pending) {
                        pending = classpathEntryContainsZircon(entry);
                        markerCache.set(entry, pending);
                    }
                    if (await pending) {
                        matched = true;
                        break;
                    }
                }
            } catch (error) {
                output.appendLine(
                    `[Zircon] Failed to inspect ${scope} classpath for ${projectUri.toString()}: ${String(error)}`
                );
            }
            if (matched) {
                break;
            }
        }
        if (matched) {
            roots.push(path.resolve(projectUri.fsPath));
        }
    }
    return dedupePaths(roots);
}

async function classpathEntryContainsZircon(rawEntry: string): Promise<boolean> {
    const entry = toFsPath(rawEntry);
    if (!entry) {
        return false;
    }
    try {
        const stat = await fs.promises.stat(entry);
        if (stat.isDirectory()) {
            await fs.promises.access(path.join(entry, ...ZIRCON_CLASS_ENTRY.split('/')));
            return true;
        }
        if (!stat.isFile() || !entry.toLowerCase().endsWith('.jar')) {
            return false;
        }
        return await zipContainsEntry(entry, ZIRCON_CLASS_ENTRY);
    } catch {
        return false;
    }
}

function zipContainsEntry(zipPath: string, expectedEntry: string): Promise<boolean> {
    return new Promise((resolve) => {
        yauzl.open(zipPath, { lazyEntries: true, autoClose: false }, (openError, zip) => {
            if (openError || !zip) {
                resolve(false);
                return;
            }
            let settled = false;
            const finish = (value: boolean): void => {
                if (settled) {
                    return;
                }
                settled = true;
                zip.close();
                resolve(value);
            };
            zip.on('entry', (entry) => {
                if (entry.fileName === expectedEntry) {
                    finish(true);
                } else {
                    zip.readEntry();
                }
            });
            zip.once('end', () => finish(false));
            zip.once('error', () => finish(false));
            zip.readEntry();
        });
    });
}

function resolveOwningProjectRoot(
    uri: vscode.Uri,
    projects: readonly vscode.Uri[],
    folders: readonly vscode.WorkspaceFolder[]
): string {
    const matchingProject = projects
        .map((project) => path.resolve(project.fsPath))
        .filter((root) => isPathWithin(uri.fsPath, root))
        .sort((left, right) => right.length - left.length)[0];
    if (matchingProject) {
        return matchingProject;
    }
    return path.resolve(vscode.workspace.getWorkspaceFolder(uri)?.uri.fsPath
        ?? folders[0]?.uri.fsPath
        ?? path.dirname(uri.fsPath));
}

function isPathWithin(candidate: string, root: string): boolean {
    const relative = path.relative(path.resolve(root), path.resolve(candidate));
    return relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative));
}

function samePath(left: string, right: string): boolean {
    return path.resolve(left).toLowerCase() === path.resolve(right).toLowerCase();
}

function dedupePaths(values: readonly string[]): string[] {
    const result = new Map<string, string>();
    for (const value of values) {
        result.set(path.resolve(value).toLowerCase(), path.resolve(value));
    }
    return [...result.values()];
}

function toFsPath(value: string): string | undefined {
    if (/^[A-Za-z]:[\\/]/.test(value)) {
        return value;
    }
    if (/^[a-zA-Z][\w+.-]*:/.test(value)) {
        try {
            return vscode.Uri.parse(value).fsPath;
        } catch {
            return undefined;
        }
    }
    return value.length > 0 ? value : undefined;
}

async function readText(uri: vscode.Uri): Promise<string | undefined> {
    try {
        const bytes = await vscode.workspace.fs.readFile(uri);
        return Buffer.from(bytes).toString('utf8');
    } catch {
        return undefined;
    }
}
