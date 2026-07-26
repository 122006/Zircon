import * as vscode from 'vscode';
import { ExMethodDescriptor } from './exMethodModel';
import { ExMethodIndex } from './exMethodIndex';
import { formatParameter, formatTypeForDisplay, getVisibleParameters } from './exMethodPresentation';
import { specializeExMethodDescriptor } from './exMethodSpecialization';
import { resolveCallContext } from './exMethodUsage';

export function registerExMethodSignatureHelp(
    context: vscode.ExtensionContext,
    index: ExMethodIndex,
    output: vscode.OutputChannel,
    isDocumentEnabled: (document: vscode.TextDocument) => boolean = () => true
): void {
    const selector: vscode.DocumentSelector = [
        { language: 'java', scheme: 'file' },
        { language: 'java', scheme: 'untitled' }
    ];

    context.subscriptions.push(
        vscode.languages.registerSignatureHelpProvider(selector, {
            async provideSignatureHelp(document, position) {
                if (!isDocumentEnabled(document)) {
                    return undefined;
                }
                await index.ensureImportedDependencies(document);
                const call = resolveCallContext(index, document, position);
                if (!call) {
                    return undefined;
                }

                const signatures = orderByParameterFit(call.targets, call.activeParameter)
                    .map((descriptor) => toSignatureInformation(index, descriptor, call.receiverTypes));
                if (signatures.length === 0) {
                    return undefined;
                }
                const help = new vscode.SignatureHelp();
                help.signatures = signatures;
                help.activeSignature = 0;
                help.activeParameter = Math.max(call.activeParameter, 0);
                output.appendLine(`[Zircon] signature help method=${call.methodName}, receiver=${call.receiverType ?? 'unknown'}, matches=${call.targets.length}, activeParameter=${call.activeParameter}`);
                return help;
            }
        }, '(', ','),
        vscode.languages.registerWorkspaceSymbolProvider({
            provideWorkspaceSymbols(query) {
                const normalized = query.trim().toLowerCase();
                return index.getAll()
                    .filter((descriptor) => matchesWorkspaceQuery(descriptor, normalized))
                    .map((descriptor) => new vscode.SymbolInformation(
                        descriptor.methodName,
                        vscode.SymbolKind.Method,
                        descriptor.qualifiedDeclaringClass,
                        new vscode.Location(descriptor.uri, descriptor.nameRange)
                    ));
            }
        })
    );
}

function toSignatureInformation(
    index: ExMethodIndex,
    descriptor: ExMethodDescriptor,
    receiverTypes: readonly string[]
): vscode.SignatureInformation {
    const specialized = specializeExMethodDescriptor(index, descriptor, receiverTypes);
    const label = `${descriptor.methodName}(${specialized.visibleParameters.map((parameter, index) => formatParameter(parameter, index)).join(', ')}): ${formatTypeForDisplay(specialized.returnType)}`;
    const documentation = new vscode.MarkdownString([
        `**Zircon 扩展方法** \`${descriptor.methodName}\``,
        '',
        `- 声明类：\`${descriptor.qualifiedDeclaringClass}\``,
        `- 目标类型：\`${descriptor.targetTypes.join('`, `')}\``,
        descriptor.cover ? '- 行为：cover 原有方法' : '- 行为：普通扩展方法',
        descriptor.shouldInvokeDirectly ? '- IDE：仅建议直接调用' : ''
    ].filter((line) => line.length > 0).join('\n'));
    const information = new vscode.SignatureInformation(label, documentation);
    information.parameters = specialized.visibleParameters.map((parameter, index) => new vscode.ParameterInformation(formatParameter(parameter, index)));
    return information;
}

function orderByParameterFit(descriptors: ExMethodDescriptor[], activeParameter: number): ExMethodDescriptor[] {
    return [...descriptors].sort((left, right) => {
        const leftDistance = Math.abs(getVisibleParameters(left).length - (activeParameter + 1));
        const rightDistance = Math.abs(getVisibleParameters(right).length - (activeParameter + 1));
        if (leftDistance !== rightDistance) {
            return leftDistance - rightDistance;
        }
        return left.signature.localeCompare(right.signature);
    });
}

function matchesWorkspaceQuery(descriptor: ExMethodDescriptor, normalizedQuery: string): boolean {
    if (normalizedQuery.length === 0) {
        return true;
    }
    const haystack = [
        descriptor.methodName,
        descriptor.qualifiedDeclaringClass,
        descriptor.targetTypes.join(' '),
        descriptor.signature
    ].join(' ').toLowerCase();
    return haystack.includes(normalizedQuery);
}
