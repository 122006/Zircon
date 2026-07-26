import * as vscode from 'vscode';

export interface ZirconWorkspaceInfo {
    hasWorkspace: boolean;
    hasJavaFiles: boolean;
    hasZirconMarkers: boolean;
    markers: string[];
    javaFileCount: number;
    buildFiles: string[];
}

const SEARCH_EXCLUDE = '**/{node_modules,build,out,.git,.gradle}/**';
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
            buildFiles: []
        };
    }

    const [javaFiles, gradleFiles, pomFiles] = await Promise.all([
        vscode.workspace.findFiles('**/*.java', SEARCH_EXCLUDE),
        vscode.workspace.findFiles('**/*.{gradle,gradle.kts}', SEARCH_EXCLUDE),
        vscode.workspace.findFiles('**/pom.xml', SEARCH_EXCLUDE)
    ]);

    const buildFiles = [...gradleFiles, ...pomFiles];
    const markers = new Set<string>();
    const filesToInspect = [...buildFiles, ...javaFiles];

    for (const uri of filesToInspect) {
        const content = await readText(uri);
        if (!content) {
            continue;
        }
        for (const marker of ZIRCON_MARKERS) {
            if (content.includes(marker.text)) {
                markers.add(marker.label);
            }
        }
        // One reliable marker is enough to enable Zircon. Stop early without
        // making project detection depend on filesystem enumeration order.
        if (markers.size > 0) {
            break;
        }
    }

    output.appendLine(`[Zircon] Java files: ${javaFiles.length}, build files: ${buildFiles.length}, markers: ${[...markers].join(', ') || 'none'}`);

    return {
        hasWorkspace: true,
        hasJavaFiles: javaFiles.length > 0,
        hasZirconMarkers: markers.size > 0,
        markers: [...markers].sort(),
        javaFileCount: javaFiles.length,
        buildFiles: buildFiles.map((uri) => vscode.workspace.asRelativePath(uri))
    };
}

async function readText(uri: vscode.Uri): Promise<string | undefined> {
    try {
        const bytes = await vscode.workspace.fs.readFile(uri);
        return Buffer.from(bytes).toString('utf8');
    } catch {
        return undefined;
    }
}
