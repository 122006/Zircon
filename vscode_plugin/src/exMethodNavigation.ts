import * as vscode from 'vscode';
import { ExMethodDescriptor } from './exMethodModel';
import { ExMethodIndex } from './exMethodIndex';
import { resolveDefinitionTargets, resolveMethodTargets } from './exMethodUsage';

const SEARCH_EXCLUDE = '**/{node_modules,build,out,.git,.gradle}/**';

export function registerExMethodNavigation(
    context: vscode.ExtensionContext,
    index: ExMethodIndex,
    output: vscode.OutputChannel
): void {
    const selector: vscode.DocumentSelector = [
        { language: 'java', scheme: 'file' },
        { language: 'java', scheme: 'untitled' }
    ];

    context.subscriptions.push(
        vscode.languages.registerHoverProvider(selector, {
            provideHover(document, position) {
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
            provideDefinition(document, position) {
                const targets = resolveDefinitionTargets(index, document, position);
                return targets.map((descriptor) => toDefinitionLink(descriptor));
            }
        }),
        vscode.languages.registerReferenceProvider(selector, {
            async provideReferences(document, position, options) {
                const targets = resolveMethodTargets(index, document, position);
                if (targets.length === 0) {
                    return [];
                }
                const locations = await findReferences(targets, index, output, options.includeDeclaration);
                return dedupeLocations(locations);
            }
        })
    );
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
    const javaFiles = await vscode.workspace.findFiles('**/*.java', SEARCH_EXCLUDE, 1000);
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
