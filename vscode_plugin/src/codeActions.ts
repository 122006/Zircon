import * as vscode from 'vscode';
import { ExMethodIndex } from './exMethodIndex';
import { ExMethodDescriptor } from './exMethodModel';
import {
    findExMethodInvocationSource,
    isExMethodDescriptorVisible,
    resolveMethodTargets
} from './exMethodUsage';
import { extensionToNormalInvocation, normalToExtensionInvocation } from './exMethodConversions';
import { buildExMethodImportTextEdits } from './javaImports';
import {
    collectConversions,
    collectToJavaChanges,
    collectToTemplateChanges,
    TextConversion
} from './stringConversions';
import { FixableZirconDiagnostic } from './zirconInspections';

type BatchDirection = 'toTemplate' | 'toJava';

export function registerZirconCodeActions(
    context: vscode.ExtensionContext,
    exMethodIndex: ExMethodIndex,
    isEnabled: (document?: vscode.TextDocument) => boolean
): void {
    const selector: vscode.DocumentSelector = [{ language: 'java', scheme: 'file' }, { language: 'java', scheme: 'untitled' }];
    context.subscriptions.push(
        vscode.languages.registerCodeActionsProvider(selector, new ZirconCodeActionProvider(exMethodIndex, isEnabled), {
            providedCodeActionKinds: [
                vscode.CodeActionKind.QuickFix,
                vscode.CodeActionKind.RefactorRewrite,
                vscode.CodeActionKind.SourceFixAll,
                vscode.CodeActionKind.SourceOrganizeImports
            ]
        }),
        vscode.commands.registerCommand('zircon.convertFileToTemplates', async () => {
            await convertActiveFile('toTemplate', isEnabled);
        }),
        vscode.commands.registerCommand('zircon.convertFileToJava', async () => {
            await convertActiveFile('toJava', isEnabled);
        }),
        vscode.commands.registerCommand('zircon.convertWorkspaceToTemplates', async () => {
            await convertWorkspace('toTemplate', isEnabled);
        }),
        vscode.commands.registerCommand('zircon.convertWorkspaceToJava', async () => {
            await convertWorkspace('toJava', isEnabled);
        }),
        vscode.commands.registerCommand('zircon.convertWorkspaceToExtensionMethods', async () => {
            await convertExMethodWorkspace('toExtension', exMethodIndex, isEnabled);
        }),
        vscode.commands.registerCommand('zircon.convertWorkspaceToNormalMethods', async () => {
            await convertExMethodWorkspace('toNormal', exMethodIndex, isEnabled);
        }),
        vscode.commands.registerCommand('zircon.refreshExMethodIndex', async () => {
            await exMethodIndex.rebuild();
            void vscode.window.showInformationMessage(`Zircon 扩展方法索引已刷新，共 ${exMethodIndex.size()} 个方法。`);
        })
    );
}

class ZirconCodeActionProvider implements vscode.CodeActionProvider {
    constructor(
        private readonly exMethodIndex: ExMethodIndex,
        private readonly isEnabled: (document?: vscode.TextDocument) => boolean
    ) {
    }

    async provideCodeActions(
        document: vscode.TextDocument,
        range: vscode.Range,
        context: vscode.CodeActionContext
    ): Promise<(vscode.CodeAction | vscode.Command)[]> {
        if (!this.isEnabled(document)) {
            return [];
        }
        if (context.only?.value.startsWith(vscode.CodeActionKind.SourceOrganizeImports.value)) {
            const organizeImports = new vscode.CodeAction(
                'Zircon: 使用 JDT 优化导入',
                vscode.CodeActionKind.SourceOrganizeImports
            );
            organizeImports.command = {
                command: 'zircon.optimizeImports',
                title: 'Zircon: 使用 JDT 优化导入'
            };
            return [organizeImports];
        }
        await this.exMethodIndex.ensureImportedDependencies(document);
        const source = document.getText();
        const start = document.offsetAt(range.start);
        const end = document.offsetAt(range.end);
        const actions = collectConversions(source)
            .filter((conversion) => intersects(conversion, start, end))
            .map((conversion) => conversionAction(document, conversion));
        actions.push(...diagnosticFixActions(document, context.diagnostics));
        actions.push(...exMethodIntentions(document, range.start, this.exMethodIndex));

        const toTemplate = collectToTemplateChanges(source);
        if (toTemplate.length > 0) {
            actions.push(batchAction(
                document,
                toTemplate,
                'Zircon: 转换当前文件中的 Java 字符串语法',
                'zircon.fixAll.toTemplates'
            ));
        }
        const toJava = collectToJavaChanges(source);
        if (toJava.length > 0) {
            actions.push(batchAction(
                document,
                toJava,
                'Zircon: 将当前文件模板字符串转换为普通 Java',
                'zircon.fixAll.toJava'
            ));
        }
        return actions;
    }
}

function diagnosticFixActions(
    document: vscode.TextDocument,
    diagnostics: readonly vscode.Diagnostic[]
): vscode.CodeAction[] {
    const actions: vscode.CodeAction[] = [];
    for (const diagnosticItem of diagnostics) {
        const zirconDiagnostic = diagnosticItem as FixableZirconDiagnostic;
        if (!zirconDiagnostic.zirconFix) {
            continue;
        }
        const action = new vscode.CodeAction(zirconDiagnostic.zirconFix.title, vscode.CodeActionKind.QuickFix);
        const edit = new vscode.WorkspaceEdit();
        edit.replace(document.uri, zirconDiagnostic.zirconFix.range, zirconDiagnostic.zirconFix.newText);
        action.edit = edit;
        action.diagnostics = [diagnosticItem];
        action.isPreferred = true;
        actions.push(action);
    }
    return actions;
}

function exMethodIntentions(
    document: vscode.TextDocument,
    position: vscode.Position,
    index: ExMethodIndex
): vscode.CodeAction[] {
    const invocation = findExMethodInvocationSource(document, position);
    if (!invocation) {
        return [];
    }
    const actions: vscode.CodeAction[] = [];
    const targets = resolveMethodTargets(index, document, position);
    const extensionTarget = targets.find((descriptor) => !isOwnerReceiver(invocation.receiverExpression, descriptor));
    if (extensionTarget) {
        const replacement = extensionToNormalInvocation(invocation, extensionTarget);
        actions.push(replacementAction(
            document,
            invocation.callStart,
            invocation.callEnd,
            replacement,
            'Zircon: 扩展方法调用转换为普通静态调用'
        ));
        const importEdits = buildExMethodImportTextEdits(document, extensionTarget);
        if (importEdits.length > 0) {
            const importAction = new vscode.CodeAction(
                `Zircon: 导入 ${extensionTarget.qualifiedDeclaringClass}`,
                vscode.CodeActionKind.QuickFix
            );
            const edit = new vscode.WorkspaceEdit();
            for (const textEdit of importEdits) {
                edit.replace(document.uri, textEdit.range, textEdit.newText);
            }
            importAction.edit = edit;
            actions.push(importAction);
        }
        const navigation = new vscode.CodeAction('Zircon: 跳转到扩展方法声明', vscode.CodeActionKind.Empty);
        navigation.command = {
            command: 'vscode.open',
            title: '跳转到扩展方法声明',
            arguments: [extensionTarget.uri, { selection: extensionTarget.nameRange }]
        };
        actions.push(navigation);
    }

    const directTarget = index.findByName(invocation.methodName)
        .find((descriptor) => isOwnerReceiver(invocation.receiverExpression, descriptor)
            && isExMethodDescriptorVisible(document, position, descriptor));
    if (directTarget) {
        const replacement = normalToExtensionInvocation(invocation.argumentsText, directTarget);
        if (replacement) {
            actions.push(replacementAction(
                document,
                invocation.callStart,
                invocation.callEnd,
                replacement,
                'Zircon: 普通静态调用转换为扩展方法调用'
            ));
        }
    }
    return actions;
}

function replacementAction(
    document: vscode.TextDocument,
    start: number,
    end: number,
    replacement: string,
    title: string
): vscode.CodeAction {
    const action = new vscode.CodeAction(title, vscode.CodeActionKind.RefactorRewrite);
    const edit = new vscode.WorkspaceEdit();
    edit.replace(document.uri, new vscode.Range(document.positionAt(start), document.positionAt(end)), replacement);
    action.edit = edit;
    return action;
}

function isOwnerReceiver(receiverExpression: string, descriptor: ExMethodDescriptor): boolean {
    const normalized = receiverExpression.replace(/\s/g, '');
    return normalized === descriptor.qualifiedDeclaringClass
        || normalized === descriptor.declaringClass
        || normalized.endsWith(`.${descriptor.declaringClass}`);
}

function conversionAction(document: vscode.TextDocument, conversion: TextConversion): vscode.CodeAction {
    const action = new vscode.CodeAction(conversion.title, vscode.CodeActionKind.RefactorRewrite);
    const edit = new vscode.WorkspaceEdit();
    edit.replace(document.uri, toRange(document, conversion), conversion.newText);
    action.edit = edit;
    action.isPreferred = conversion.kind === 'format-to-template' || conversion.kind === 'concat-to-template';
    return action;
}

function batchAction(
    document: vscode.TextDocument,
    conversions: TextConversion[],
    title: string,
    kind: string
): vscode.CodeAction {
    const action = new vscode.CodeAction(title, vscode.CodeActionKind.SourceFixAll.append(kind));
    const edit = new vscode.WorkspaceEdit();
    for (const conversion of conversions) {
        edit.replace(document.uri, toRange(document, conversion), conversion.newText);
    }
    action.edit = edit;
    return action;
}

async function convertActiveFile(
    direction: BatchDirection,
    isEnabled: (document?: vscode.TextDocument) => boolean
): Promise<void> {
    const editor = vscode.window.activeTextEditor;
    if (!editor || editor.document.languageId !== 'java' || !isEnabled(editor.document)) {
        void vscode.window.showWarningMessage('请先在 Zircon Java 项目中打开一个 Java 文件。');
        return;
    }
    const count = await applyDocumentConversions(editor.document, direction);
    void vscode.window.showInformationMessage(count > 0
        ? `Zircon 已完成 ${count} 处语法转换。`
        : '当前文件没有可转换的语法。');
}

async function convertWorkspace(
    direction: BatchDirection,
    isEnabled: (document?: vscode.TextDocument) => boolean
): Promise<void> {
    if (!isEnabled()) {
        void vscode.window.showWarningMessage('当前工作区未启用 Zircon。');
        return;
    }
    const uris = await vscode.workspace.findFiles(
        '**/*.java',
        '**/{.git,.gradle,.idea,node_modules,build,out,target,bin}/**'
    );
    let changedFiles = 0;
    let changedRanges = 0;
    await vscode.window.withProgress({
        location: vscode.ProgressLocation.Notification,
        title: direction === 'toTemplate' ? 'Zircon：批量转换为模板字符串' : 'Zircon：批量转换为普通 Java',
        cancellable: true
    }, async (progress, token) => {
        for (let index = 0; index < uris.length && !token.isCancellationRequested; index += 1) {
            const uri = uris[index];
            progress.report({
                increment: uris.length > 0 ? 100 / uris.length : 100,
                message: vscode.workspace.asRelativePath(uri)
            });
            const document = await vscode.workspace.openTextDocument(uri);
            if (!isEnabled(document)) {
                continue;
            }
            const count = await applyDocumentConversions(document, direction);
            if (count > 0) {
                changedFiles += 1;
                changedRanges += count;
            }
        }
    });
    void vscode.window.showInformationMessage(
        `Zircon 批量转换完成：${changedFiles} 个文件，${changedRanges} 处修改。修改保留在编辑器中，可统一检查后保存。`
    );
}

async function applyDocumentConversions(document: vscode.TextDocument, direction: BatchDirection): Promise<number> {
    const source = document.getText();
    const conversions = direction === 'toTemplate'
        ? collectToTemplateChanges(source)
        : collectToJavaChanges(source);
    if (conversions.length === 0) {
        return 0;
    }
    const edit = new vscode.WorkspaceEdit();
    for (const conversion of conversions) {
        edit.replace(document.uri, toRange(document, conversion), conversion.newText);
    }
    return await vscode.workspace.applyEdit(edit) ? conversions.length : 0;
}

async function convertExMethodWorkspace(
    direction: 'toExtension' | 'toNormal',
    index: ExMethodIndex,
    isEnabled: (document?: vscode.TextDocument) => boolean
): Promise<void> {
    if (!isEnabled()) {
        void vscode.window.showWarningMessage('当前工作区未启用 Zircon。');
        return;
    }
    const uris = await vscode.workspace.findFiles(
        '**/*.java',
        '**/{.git,.gradle,.idea,node_modules,build,out,target,bin}/**'
    );
    let changedFiles = 0;
    let changedCalls = 0;
    await vscode.window.withProgress({
        location: vscode.ProgressLocation.Notification,
        title: direction === 'toExtension'
            ? 'Zircon：批量转换为扩展方法调用'
            : 'Zircon：批量转换为普通静态调用',
        cancellable: true
    }, async (progress, token) => {
        for (let fileIndex = 0; fileIndex < uris.length && !token.isCancellationRequested; fileIndex += 1) {
            const document = await vscode.workspace.openTextDocument(uris[fileIndex]);
            if (!isEnabled(document)) {
                continue;
            }
            progress.report({
                increment: uris.length > 0 ? 100 / uris.length : 100,
                message: vscode.workspace.asRelativePath(document.uri)
            });
            await index.ensureImportedDependencies(document);
            const edits = collectExMethodInvocationEdits(document, index, direction);
            if (edits.length === 0) {
                continue;
            }
            const workspaceEdit = new vscode.WorkspaceEdit();
            for (const edit of edits) {
                workspaceEdit.replace(document.uri, edit.range, edit.newText);
            }
            if (await vscode.workspace.applyEdit(workspaceEdit)) {
                changedFiles += 1;
                changedCalls += edits.length;
            }
        }
    });
    void vscode.window.showInformationMessage(
        `Zircon 扩展方法批量转换完成：${changedFiles} 个文件，${changedCalls} 处调用。修改未自动保存。`
    );
}

function collectExMethodInvocationEdits(
    document: vscode.TextDocument,
    index: ExMethodIndex,
    direction: 'toExtension' | 'toNormal'
): vscode.TextEdit[] {
    const text = document.getText();
    const methodNames = new Set(index.getAll().map((descriptor) => descriptor.methodName));
    const edits: vscode.TextEdit[] = [];
    const seen = new Set<string>();
    const pattern = /\b([A-Za-z_$][\w$]*)\s*\(/g;
    for (let match = pattern.exec(text); match; match = pattern.exec(text)) {
        const methodName = match[1];
        if (!methodNames.has(methodName)) {
            continue;
        }
        const methodOffset = match.index;
        const position = document.positionAt(methodOffset + Math.floor(methodName.length / 2));
        const invocation = findExMethodInvocationSource(document, position);
        if (!invocation) {
            continue;
        }
        const key = `${invocation.callStart}:${invocation.callEnd}`;
        if (seen.has(key)) {
            continue;
        }
        let replacement: string | undefined;
        if (direction === 'toNormal') {
            const descriptor = resolveMethodTargets(index, document, position)
                .find((candidate) => !isOwnerReceiver(invocation.receiverExpression, candidate));
            if (descriptor) {
                replacement = extensionToNormalInvocation(invocation, descriptor);
            }
        } else {
            const descriptor = index.findByName(methodName)
                .find((candidate) => isOwnerReceiver(invocation.receiverExpression, candidate)
                    && isExMethodDescriptorVisible(document, position, candidate));
            if (descriptor) {
                replacement = normalToExtensionInvocation(invocation.argumentsText, descriptor);
            }
        }
        if (!replacement) {
            continue;
        }
        seen.add(key);
        edits.push(new vscode.TextEdit(
            new vscode.Range(document.positionAt(invocation.callStart), document.positionAt(invocation.callEnd)),
            replacement
        ));
        pattern.lastIndex = invocation.callEnd;
    }
    return edits;
}


function intersects(conversion: TextConversion, start: number, end: number): boolean {
    if (start === end) {
        return start >= conversion.start && start <= conversion.end;
    }
    return start < conversion.end && end > conversion.start;
}

function toRange(document: vscode.TextDocument, conversion: TextConversion): vscode.Range {
    return new vscode.Range(document.positionAt(conversion.start), document.positionAt(conversion.end));
}
