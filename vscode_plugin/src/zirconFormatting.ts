import * as vscode from 'vscode';
import { findTemplateLiterals } from './stringConversions';
import { TEMPLATE_CODE } from './templateStringSplitter';

const FORMAT_PROXY_MARKER = '__ZIRCON_FORMAT_PROXY_';
let suppressZirconFormatter = false;

interface FormattingProxy {
    text: string;
    restore(formattedText: string): string | undefined;
}

interface ImportBlockEdit {
    start: number;
    end: number;
    newText: string;
}

export function registerZirconFormatting(
    context: vscode.ExtensionContext,
    output: vscode.OutputChannel,
    isEnabled: () => boolean
): void {
    const selector: vscode.DocumentSelector = [
        { language: 'java', scheme: 'file' },
        { language: 'java', scheme: 'untitled' }
    ];
    const provider: vscode.DocumentFormattingEditProvider = {
        async provideDocumentFormattingEdits(document, options) {
            if (suppressZirconFormatter || !isEnabled()) {
                return undefined;
            }
            return formatZirconDocument(document, options, output);
        }
    };
    context.subscriptions.push(
        vscode.languages.registerDocumentFormattingEditProvider(selector, provider),
        vscode.commands.registerCommand('zircon.formatDocument', async () => {
            const editor = vscode.window.activeTextEditor;
            if (!editor || editor.document.languageId !== 'java') {
                void vscode.window.showWarningMessage('请先打开 Java 文件。');
                return;
            }
            const edits = await formatZirconDocument(editor.document, {
                insertSpaces: editor.options.insertSpaces !== false,
                tabSize: typeof editor.options.tabSize === 'number' ? editor.options.tabSize : 4
            }, output);
            if (!edits || edits.length === 0) {
                return;
            }
            const workspaceEdit = new vscode.WorkspaceEdit();
            for (const edit of edits) {
                workspaceEdit.replace(editor.document.uri, edit.range, edit.newText);
            }
            await vscode.workspace.applyEdit(workspaceEdit);
        }),
        vscode.commands.registerCommand('zircon.optimizeImports', async () => {
            const editor = vscode.window.activeTextEditor;
            if (!editor || editor.document.languageId !== 'java') {
                void vscode.window.showWarningMessage('请先打开 Java 文件。');
                return;
            }
            await optimizeZirconImports(editor.document, output);
        })
    );
}

export function isZirconFormattingProxy(document: vscode.TextDocument): boolean {
    return document.uri.scheme === 'untitled' && document.getText().includes(FORMAT_PROXY_MARKER);
}

export function createZirconFormattingProxy(source: string): FormattingProxy {
    let markerPrefix = FORMAT_PROXY_MARKER;
    while (source.includes(markerPrefix)) {
        markerPrefix += '_';
    }
    const templates = findTemplateLiterals(source);
    const replacements: Array<{ start: number; end: number; text: string }> = [];
    const templateRestorations: Array<{ marker: string; original: string }> = [];
    templates.forEach((template, index) => {
        const marker = `${markerPrefix}T${index}__`;
        replacements.push({ start: template.start, end: template.end, text: `"${marker}"` });
        templateRestorations.push({ marker, original: source.slice(template.start, template.end) });
    });

    const operatorRestorations: Array<{ marker: string; operator: '?.' | '?:' }> = [];
    for (const operator of findLanguageOperators(source, templates.map((item) => ({ start: item.start, end: item.end })))) {
        const marker = `${markerPrefix}O${operatorRestorations.length}__`;
        const replacement = operator.operator === '?.'
            ? `/*${marker}*/.`
            : `/*${marker}*/==`;
        replacements.push({ start: operator.start, end: operator.start + 2, text: replacement });
        operatorRestorations.push({ marker, operator: operator.operator });
    }

    let proxyText = source;
    for (const replacement of replacements.sort((left, right) => right.start - left.start)) {
        proxyText = proxyText.slice(0, replacement.start) + replacement.text + proxyText.slice(replacement.end);
    }
    return {
        text: proxyText,
        restore(formattedText: string): string | undefined {
            let restored = formattedText;
            for (const item of templateRestorations) {
                const pattern = new RegExp(`"${escapeRegExp(item.marker)}"`, 'g');
                restored = restored.replace(pattern, () => item.original);
            }
            for (const item of operatorRestorations) {
                const suffix = item.operator === '?.' ? '\\s*\\.' : '\\s*==';
                const pattern = new RegExp(`/\\*${escapeRegExp(item.marker)}\\*/${suffix}`, 'g');
                restored = restored.replace(pattern, () => item.operator);
            }
            return restored.includes(markerPrefix) ? undefined : restored;
        }
    };
}

export function buildFallbackImportOptimization(source: string): ImportBlockEdit | undefined {
    const lineBreak = source.includes('\r\n') ? '\r\n' : '\n';
    const pattern = /^[ \t]*import\s+(static\s+)?([\w.*]+)\s*;[ \t]*(?:\r?\n|$)/gm;
    const matches = [...source.matchAll(pattern)];
    if (matches.length < 2) {
        return undefined;
    }
    const first = matches[0];
    const last = matches[matches.length - 1];
    const start = first.index ?? 0;
    const end = (last.index ?? 0) + last[0].length;
    const block = source.slice(start, end);
    const importsOnly = matches.map((match) => match[0]).join('');
    if (block.replace(/\s/g, '') !== importsOnly.replace(/\s/g, '')) {
        // Preserve comments and import grouping that require JDT semantics.
        return undefined;
    }
    const normal = new Set<string>();
    const statics = new Set<string>();
    for (const match of matches) {
        (match[1] ? statics : normal).add(match[2]);
    }
    const groups = [
        [...normal].sort().map((name) => `import ${name};`).join(lineBreak),
        [...statics].sort().map((name) => `import static ${name};`).join(lineBreak)
    ].filter((group) => group.length > 0);
    const newText = `${groups.join(lineBreak + lineBreak)}${lineBreak}`;
    return source.slice(start, end) === newText ? undefined : { start, end, newText };
}

async function formatZirconDocument(
    document: vscode.TextDocument,
    options: vscode.FormattingOptions,
    output: vscode.OutputChannel
): Promise<vscode.TextEdit[] | undefined> {
    const source = document.getText();
    const nativeEdits = await requestNativeFormatting(document.uri, options);
    if (nativeEdits) {
        const nativeText = applyTextEdits(source, nativeEdits, document);
        if (preservesZirconSyntax(source, nativeText)) {
            return nativeEdits;
        }
        output.appendLine('[Zircon] Native formatter changed Zircon syntax; retrying through a formatting proxy.');
    }

    const proxy = createZirconFormattingProxy(source);
    const proxyDocument = await vscode.workspace.openTextDocument({ language: 'java', content: proxy.text });
    const proxyEdits = await requestNativeFormatting(proxyDocument.uri, options);
    if (!proxyEdits) {
        output.appendLine('[Zircon] Java formatter did not return edits for the Zircon formatting proxy.');
        return undefined;
    }
    const formattedProxy = applyTextEdits(proxy.text, proxyEdits, proxyDocument);
    const restored = proxy.restore(formattedProxy);
    if (restored === undefined || !preservesZirconSyntax(source, restored)) {
        output.appendLine('[Zircon] Formatting proxy restoration failed; no edits were applied.');
        return undefined;
    }
    if (restored === source) {
        return [];
    }
    return [vscode.TextEdit.replace(fullDocumentRange(document), restored)];
}

async function requestNativeFormatting(
    uri: vscode.Uri,
    options: vscode.FormattingOptions
): Promise<vscode.TextEdit[] | undefined> {
    suppressZirconFormatter = true;
    try {
        return await vscode.commands.executeCommand<vscode.TextEdit[]>(
            'vscode.executeFormatDocumentProvider',
            uri,
            options
        );
    } catch {
        return undefined;
    } finally {
        suppressZirconFormatter = false;
    }
}

async function optimizeZirconImports(document: vscode.TextDocument, output: vscode.OutputChannel): Promise<void> {
    const actions = await vscode.commands.executeCommand<Array<vscode.CodeAction | vscode.Command>>(
        'vscode.executeCodeActionProvider',
        document.uri,
        fullDocumentRange(document),
        vscode.CodeActionKind.SourceOrganizeImports.value,
        20
    ) ?? [];
    const nativeAction = actions.find((item) => {
        if (item instanceof vscode.CodeAction) {
            return item.command?.command !== 'zircon.optimizeImports';
        }
        return item.command !== 'zircon.optimizeImports';
    });
    if (nativeAction) {
        if (nativeAction instanceof vscode.CodeAction) {
            if (nativeAction.edit) {
                await vscode.workspace.applyEdit(nativeAction.edit);
            }
            if (nativeAction.command) {
                await vscode.commands.executeCommand(
                    nativeAction.command.command,
                    ...(nativeAction.command.arguments ?? [])
                );
            }
        } else {
            await vscode.commands.executeCommand(nativeAction.command, ...(nativeAction.arguments ?? []));
        }
        return;
    }

    const fallback = buildFallbackImportOptimization(document.getText());
    if (!fallback) {
        output.appendLine('[Zircon] JDT did not provide organize-imports edits and no safe local import rewrite was available.');
        return;
    }
    const edit = new vscode.WorkspaceEdit();
    edit.replace(
        document.uri,
        new vscode.Range(document.positionAt(fallback.start), document.positionAt(fallback.end)),
        fallback.newText
    );
    await vscode.workspace.applyEdit(edit);
}

function applyTextEdits(source: string, edits: readonly vscode.TextEdit[], document: vscode.TextDocument): string {
    const normalized = edits.map((edit) => ({
        start: document.offsetAt(edit.range.start),
        end: document.offsetAt(edit.range.end),
        newText: edit.newText
    })).sort((left, right) => right.start - left.start);
    let result = source;
    for (const edit of normalized) {
        result = result.slice(0, edit.start) + edit.newText + result.slice(edit.end);
    }
    return result;
}

function fullDocumentRange(document: vscode.TextDocument): vscode.Range {
    return new vscode.Range(new vscode.Position(0, 0), document.positionAt(document.getText().length));
}

function preservesZirconSyntax(before: string, after: string): boolean {
    const beforeTemplates = templateStructure(before);
    const afterTemplates = templateStructure(after);
    if (JSON.stringify(beforeTemplates) !== JSON.stringify(afterTemplates)) {
        return false;
    }
    const beforeOperators = findLanguageOperators(before, findTemplateLiterals(before)).map((item) => item.operator);
    const afterOperators = findLanguageOperators(after, findTemplateLiterals(after)).map((item) => item.operator);
    return beforeOperators.join(',') === afterOperators.join(',');
}

function templateStructure(source: string): unknown[] {
    return findTemplateLiterals(source).map((template) => ({
        prefix: template.prefix,
        closed: template.closed,
        codeRanges: template.ranges.filter((range) => range.style === TEMPLATE_CODE).length,
        fixedRanges: template.ranges
            .filter((range) => range.style !== TEMPLATE_CODE)
            .map((range) => ({
                style: range.style,
                text: source.slice(range.startIndex, range.endIndex)
            }))
    }));
}

function findLanguageOperators(
    source: string,
    excludedRanges: readonly { start: number; end: number }[]
): Array<{ start: number; operator: '?.' | '?:' }> {
    const operators: Array<{ start: number; operator: '?.' | '?:' }> = [];
    let excludedIndex = 0;
    for (let index = 0; index < source.length; index += 1) {
        while (excludedIndex < excludedRanges.length && index >= excludedRanges[excludedIndex].end) {
            excludedIndex++;
        }
        const excluded = excludedRanges[excludedIndex];
        if (excluded && index >= excluded.start && index < excluded.end) {
            index = excluded.end - 1;
            continue;
        }
        if (source.startsWith('//', index)) {
            const newline = source.indexOf('\n', index + 2);
            index = newline < 0 ? source.length : newline;
            continue;
        }
        if (source.startsWith('/*', index)) {
            const end = source.indexOf('*/', index + 2);
            index = end < 0 ? source.length : end + 1;
            continue;
        }
        if (source[index] === '"' || source[index] === '\'') {
            index = skipQuoted(source, index) - 1;
            continue;
        }
        const operator = source.slice(index, index + 2);
        if (operator === '?.' || operator === '?:') {
            operators.push({ start: index, operator });
            index++;
        }
    }
    return operators;
}

function skipQuoted(source: string, start: number): number {
    const quote = source[start];
    for (let index = start + 1; index < source.length; index++) {
        if (source[index] === '\\') {
            index++;
        } else if (source[index] === quote) {
            return index + 1;
        }
    }
    return source.length;
}

function escapeRegExp(value: string): string {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
