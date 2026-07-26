import * as vscode from 'vscode';
import { ExMethodIndex } from './exMethodIndex';
import { ExMethodDescriptor } from './exMethodModel';
import { formatTypeForDisplay, formatVisibleParameterList } from './exMethodPresentation';
import { specializeExMethodDescriptor } from './exMethodSpecialization';
import { resolveCompletionContext } from './exMethodUsage';
import { buildExMethodImportTextEdits } from './javaImports';

export function registerExMethodCompletion(
    context: vscode.ExtensionContext,
    index: ExMethodIndex,
    output: vscode.OutputChannel,
    nativeAgentAvailable: () => boolean = () => false
): void {
    const provider: vscode.CompletionItemProvider = {
        async provideCompletionItems(
            document: vscode.TextDocument,
            position: vscode.Position
        ): Promise<vscode.CompletionItem[]> {
            if (document.languageId !== 'java') {
                return [];
            }
            if (isMemberCompletion(document, position) && !nativeAgentAvailable()) {
                await index.ensureAllDependenciesIndexed();
            } else {
                // The Agent obtains global candidates from JDT's persistent
                // annotation index. TypeScript stays import-scoped and acts as
                // the full-scan fallback only when the Agent is unavailable.
                await index.ensureImportedDependencies(document);
            }
            const completion = resolveCompletionContext(index, document, position);
            if (!completion) {
                return [];
            }

            output.appendLine(`[Zircon] completion prefix=${completion.methodNamePrefix || '<all>'}, receiverType=${completion.receiverType ?? 'unknown'}, matches=${completion.targets.length}`);
            return completion.targets.map((descriptor) => {
                return toCompletionItem(index, document, descriptor, completion.receiverTypes, completion.methodNameRange);
            });
        }
    };

    context.subscriptions.push(vscode.languages.registerCompletionItemProvider(
        [{ language: 'java', scheme: 'file' }, { language: 'java', scheme: 'untitled' }],
        provider,
        '.',
        ':'
    ));
}

function isMemberCompletion(document: vscode.TextDocument, position: vscode.Position): boolean {
    const text = document.getText();
    let offset = document.offsetAt(position);
    while (offset > 0 && /[\w$]/.test(text[offset - 1])) {
        offset--;
    }
    while (offset > 0 && /\s/.test(text[offset - 1])) {
        offset--;
    }
    return offset > 0 && text[offset - 1] === '.'
        || offset > 1 && text.slice(offset - 2, offset) === '::';
}

function toCompletionItem(
    index: ExMethodIndex,
    document: vscode.TextDocument,
    descriptor: ExMethodDescriptor,
    receiverTypes: readonly string[],
    replaceRange: vscode.Range
): vscode.CompletionItem {
    const specialized = specializeExMethodDescriptor(index, descriptor, receiverTypes);
    const additionalTextEdits = buildExMethodImportTextEdits(document, descriptor);
    const item = new vscode.CompletionItem(
        `${descriptor.methodName}${formatVisibleParameterList(specialized.visibleParameters)}`,
        vscode.CompletionItemKind.Method
    );
    item.detail = `${descriptor.qualifiedDeclaringClass} -> ${formatTypeForDisplay(specialized.returnType)}`
        + (additionalTextEdits.length > 0 ? ' · auto import' : '');
    item.documentation = new vscode.MarkdownString([
        `**Zircon 扩展方法** \`${descriptor.methodName}\``,
        '',
        `- 声明类：\`${descriptor.qualifiedDeclaringClass}\``,
        `- 目标类型：\`${descriptor.targetTypes.join('`, `')}\``,
        `- 签名：\`${descriptor.methodName}${formatVisibleParameterList(specialized.visibleParameters)}: ${formatTypeForDisplay(specialized.returnType)}\``,
        descriptor.cover ? '- 行为：cover 原有方法' : '- 行为：普通扩展方法',
        descriptor.shouldInvokeDirectly ? '- IDE：仅建议直接调用' : ''
    ].filter((line) => line.length > 0).join('\n'));
    item.insertText = buildSnippet(specialized.visibleParameters, descriptor.methodName);
    item.filterText = descriptor.methodName;
    item.range = replaceRange;
    item.additionalTextEdits = additionalTextEdits;
    item.sortText = `${additionalTextEdits.length > 0 ? '1' : '0'}-${descriptor.methodName}-${descriptor.qualifiedDeclaringClass}`;
    return item;
}

function buildSnippet(parameters: ReadonlyArray<ExMethodDescriptor['parameters'][number]>, methodName: string): vscode.SnippetString {
    const snippet = new vscode.SnippetString();
    snippet.appendText(methodName);
    snippet.appendText('(');
    parameters.forEach((parameter, index) => {
        if (index > 0) {
            snippet.appendText(', ');
        }
        snippet.appendPlaceholder(parameter.name || `arg${index + 1}`);
    });
    snippet.appendText(')');
    return snippet;
}
