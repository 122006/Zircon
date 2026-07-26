import * as vscode from 'vscode';
import { ExMethodDescriptor } from './exMethodModel';
import { ExMethodIndex } from './exMethodIndex';
import { resolveDefinitionTargets, resolveMethodTargets } from './exMethodUsage';

const SEARCH_EXCLUDE = '**/{node_modules,build,out,.git,.gradle}/**';
let suppressCustomReferenceProvider = false;
let suppressZirconRenameProvider = false;

export function registerExMethodNavigation(
    context: vscode.ExtensionContext,
    index: ExMethodIndex,
    output: vscode.OutputChannel,
    nativeAgentAvailable: () => boolean = () => false,
    isDocumentEnabled: (document: vscode.TextDocument) => boolean = () => true
): void {
    const selector: vscode.DocumentSelector = [
        { language: 'java', scheme: 'file' },
        { language: 'java', scheme: 'untitled' }
    ];

    context.subscriptions.push(
        vscode.languages.registerHoverProvider(selector, {
            async provideHover(document, position) {
                if (!isDocumentEnabled(document)) {
                    return undefined;
                }
                if (nativeAgentAvailable()) {
                    return undefined;
                }
                await index.ensureImportedDependencies(document);
                const targets = resolveMethodTargets(index, document, position);
                if (targets.length === 0) {
                    return undefined;
                }
                const markdown = new vscode.MarkdownString(undefined, true);
                for (const descriptor of targets) {
                    markdown.appendMarkdown(renderDescriptor(descriptor));
                    markdown.appendMarkdown('\n\n---\n\n');
                }
                return new vscode.Hover(markdown);
            }
        }),
        vscode.languages.registerDefinitionProvider(selector, {
            async provideDefinition(document, position) {
                if (!isDocumentEnabled(document)) {
                    return [];
                }
                if (nativeAgentAvailable()) {
                    return [];
                }
                await index.ensureImportedDependencies(document);
                const targets = resolveDefinitionTargets(index, document, position);
                return targets.map((descriptor) => toDefinitionLink(descriptor));
            }
        }),
        vscode.languages.registerReferenceProvider(selector, {
            async provideReferences(document, position, options) {
                if (!isDocumentEnabled(document)) {
                    return [];
                }
                if (suppressCustomReferenceProvider) {
                    return [];
                }
                // The JDT agent now makes extension invocations accurate native
                // SearchEngine matches. Keep the source scan only as a
                // fallback for users who explicitly disable the agent.
                if (nativeAgentAvailable()) {
                    return [];
                }
                await index.ensureImportedDependencies(document);
                const targets = resolveMethodTargets(index, document, position);
                if (targets.length === 0) {
                    return [];
                }
                const locations = await findReferences(targets, index, output, options.includeDeclaration);
                return dedupeLocations(locations);
            }
        }),
        vscode.languages.registerRenameProvider(selector, {
            async provideRenameEdits(document, position, newName) {
                if (suppressZirconRenameProvider || !isDocumentEnabled(document)) {
                    return undefined;
                }
                await index.ensureImportedDependencies(document);
                const targets = resolveMethodTargets(index, document, position);
                if (targets.length === 0) {
                    return undefined;
                }
                if (!nativeAgentAvailable()) {
                    return buildFallbackRenameEdit(document, targets, newName, index, output);
                }

                // Let JDT build the authoritative refactoring first. Its
                // RenameMethodProcessor currently drops explicit static calls
                // after an extension facade participates in the search, even
                // though SearchEngine returns those exact references. Merge
                // only those native SearchEngine locations back into the edit.
                suppressZirconRenameProvider = true;
                let nativeEdit: vscode.WorkspaceEdit | undefined;
                try {
                    nativeEdit = await vscode.commands.executeCommand<vscode.WorkspaceEdit>(
                        'vscode.executeDocumentRenameProvider',
                        document.uri,
                        position,
                        newName
                    );
                } finally {
                    suppressZirconRenameProvider = false;
                }
                const references = await probeNativeJavaReferences(document, position);
                return await mergeNativeRenameReferences(
                    nativeEdit ?? new vscode.WorkspaceEdit(),
                    references,
                    targets[0].methodName,
                    newName
                );
            }
        })
    );
}

async function buildFallbackRenameEdit(
    document: vscode.TextDocument,
    targets: readonly ExMethodDescriptor[],
    newName: string,
    index: ExMethodIndex,
    output: vscode.OutputChannel
): Promise<vscode.WorkspaceEdit | undefined> {
    const locations = await findReferences([...targets], index, output, true);
    if (locations.length === 0) {
        return undefined;
    }
    const edit = new vscode.WorkspaceEdit();
    const unique = dedupeLocations(locations);
    const edited = new Set<string>();
    for (const location of unique) {
        edit.replace(location.uri, location.range, newName);
        edited.add(locationKey(location.uri, location.range));
    }
    // The declaration is always part of includeDeclaration, but retain an exact
    // declaration edit if a dependency/source scan was temporarily incomplete.
    for (const target of targets) {
        const declaration = new vscode.Location(target.uri, target.nameRange);
        if (target.uri.scheme === 'file' && !edited.has(locationKey(declaration.uri, declaration.range))) {
            edit.replace(target.uri, target.nameRange, newName);
        }
    }
    return edit;
}

export async function mergeNativeRenameReferences(
    edit: vscode.WorkspaceEdit,
    references: readonly vscode.Location[],
    oldName: string,
    newName: string
): Promise<vscode.WorkspaceEdit> {
    const merged = new vscode.WorkspaceEdit();
    const existing = new Set<string>();
    const documents = new Map<string, vscode.TextDocument>();
    const openDocument = async (uri: vscode.Uri): Promise<vscode.TextDocument> => {
        const key = uri.toString();
        const cached = documents.get(key);
        if (cached) {
            return cached;
        }
        const document = await vscode.workspace.openTextDocument(uri);
        documents.set(key, document);
        return document;
    };
    for (const [uri, textEdits] of edit.entries()) {
        const editedDocument = await openDocument(uri);
        for (const textEdit of textEdits) {
            // A facade MethodReferenceMatch can currently carry a range from
            // the extension selector through the following direct static
            // selector. Never forward such a destructive multi-token edit.
            const normalizedRange = normalizeRenameRange(editedDocument, textEdit.range, oldName);
            if (!normalizedRange) {
                continue;
            }
            merged.replace(uri, normalizedRange, newName);
            existing.add(locationKey(uri, normalizedRange));
        }
    }
    for (const location of dedupeLocations([...references])) {
        const referencedDocument = await openDocument(location.uri);
        const normalizedRange = normalizeRenameRange(referencedDocument, location.range, oldName);
        if (!normalizedRange) {
            continue;
        }
        const key = locationKey(location.uri, normalizedRange);
        if (existing.has(key)) {
            continue;
        }
        merged.replace(location.uri, normalizedRange, newName);
        existing.add(key);
    }
    return merged;
}

function normalizeRenameRange(
    document: vscode.TextDocument,
    range: vscode.Range,
    methodName: string
): vscode.Range | undefined {
    if (document.getText(range) === methodName) {
        return range;
    }
    const selectorRange = new vscode.Range(
        range.start,
        range.start.translate(0, methodName.length)
    );
    return document.getText(selectorRange) === methodName ? selectorRange : undefined;
}

function locationKey(uri: vscode.Uri, range: vscode.Range): string {
    return `${uri.toString()}:${range.start.line}:${range.start.character}:${range.end.line}:${range.end.character}`;
}

export async function probeNativeJavaReferences(
    document: vscode.TextDocument,
    position: vscode.Position
): Promise<vscode.Location[]> {
    suppressCustomReferenceProvider = true;
    try {
        return await vscode.commands.executeCommand<vscode.Location[]>(
            'vscode.executeReferenceProvider',
            document.uri,
            position
        ) ?? [];
    } finally {
        suppressCustomReferenceProvider = false;
    }
}

function toDefinitionLink(descriptor: ExMethodDescriptor): vscode.DefinitionLink {
    return {
        targetUri: descriptor.uri,
        targetRange: descriptor.nameRange,
        targetSelectionRange: descriptor.nameRange
    };
}

async function findReferences(
    targets: ExMethodDescriptor[],
    index: ExMethodIndex,
    output: vscode.OutputChannel,
    includeDeclaration: boolean
): Promise<vscode.Location[]> {
    const javaFiles = await vscode.workspace.findFiles('**/*.java', SEARCH_EXCLUDE);
    const targetNames = new Set(targets.map((target) => target.methodName));
    const locations: vscode.Location[] = [];

    if (includeDeclaration) {
        for (const target of targets) {
            locations.push(new vscode.Location(target.uri, target.nameRange));
        }
    }

    for (const uri of javaFiles) {
        const document = await vscode.workspace.openTextDocument(uri);
        const text = document.getText();
        for (const methodName of targetNames) {
            const regex = new RegExp(`\\b${escapeRegExp(methodName)}\\b`, 'g');
            for (let match = regex.exec(text); match !== null; match = regex.exec(text)) {
                const methodIndex = match.index;
                const methodPosition = document.positionAt(methodIndex);
                const resolved = resolveMethodTargets(index, document, methodPosition);
                if (resolved.some((candidate) => targets.some((target) => isSameDescriptor(candidate, target)))) {
                    const range = new vscode.Range(
                        document.positionAt(methodIndex),
                        document.positionAt(methodIndex + methodName.length)
                    );
                    locations.push(new vscode.Location(uri, range));
                }
            }
        }
    }

    output.appendLine(`[Zircon] references resolved: targets=${targets.length}, locations=${locations.length}`);
    return locations;
}

function isSameDescriptor(left: ExMethodDescriptor, right: ExMethodDescriptor): boolean {
    return left.uri.toString() === right.uri.toString()
        && left.methodName === right.methodName
        && left.line === right.line;
}

function dedupeLocations(locations: vscode.Location[]): vscode.Location[] {
    const seen = new Set<string>();
    return locations.filter((location) => {
        const key = `${location.uri.toString()}:${location.range.start.line}:${location.range.start.character}`;
        if (seen.has(key)) {
            return false;
        }
        seen.add(key);
        return true;
    });
}

function renderDescriptor(descriptor: ExMethodDescriptor): string {
    const params = descriptor.parameters.map((parameter) => `\`${parameter.type} ${parameter.name}\``).join(', ');
    const tags = [
        descriptor.isStaticExtension ? '静态扩展' : '实例扩展',
        descriptor.cover ? 'cover' : 'non-cover',
        descriptor.shouldInvokeDirectly ? 'direct-only' : ''
    ].filter((item) => item.length > 0).join(' / ');

    return [
        `**Zircon 扩展方法：\`${descriptor.methodName}\`**`,
        '',
        `- 声明类：\`${descriptor.qualifiedDeclaringClass}\``,
        `- 目标类型：\`${descriptor.targetTypes.join('`, `')}\``,
        `- 返回类型：\`${descriptor.returnType}\``,
        `- 参数：${params.length > 0 ? params : '无'}`,
        `- 标签：${tags}`,
        `- 定义位置：第 ${descriptor.line + 1} 行`
    ].join('\n');
}

function escapeRegExp(value: string): string {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
