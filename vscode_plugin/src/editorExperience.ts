import * as vscode from 'vscode';
import { findTemplateAtOffset, findTemplateLiterals, TemplateLiteral } from './stringConversions';
import { TEMPLATE_CODE, TEMPLATE_STRING } from './templateStringSplitter';

interface CursorAction {
    offset: number;
    text: string;
    cursorAdvance: number;
}

export function registerZirconEditorExperience(
    context: vscode.ExtensionContext,
    isEnabled: () => boolean
): void {
    context.subscriptions.push(
        vscode.commands.registerTextEditorCommand('zircon.templateEnter', async (editor) => {
            if (!isEnabled() || editor.document.languageId !== 'java') {
                await typeDefault('\n');
                return;
            }
            const source = editor.document.getText();
            const eol = editor.document.eol === vscode.EndOfLine.CRLF ? '\r\n' : '\n';
            const actions = editor.selections.map((selection): CursorAction | undefined => {
                if (!selection.isEmpty) {
                    return undefined;
                }
                const offset = editor.document.offsetAt(selection.active);
                const literal = findTemplateAtOffset(source, offset);
                if (!literal || !literal.closed || offset >= literal.endQuote || !isInRange(literal, offset, TEMPLATE_STRING)) {
                    return undefined;
                }
                const line = editor.document.lineAt(selection.active.line).text;
                const baseIndent = /^\s*/.exec(line)?.[0] ?? '';
                const indentUnit = editor.options.insertSpaces
                    ? ' '.repeat(typeof editor.options.tabSize === 'number' ? editor.options.tabSize : 4)
                    : '\t';
                const insertion = `" +${eol}${baseIndent}${indentUnit}${literal.prefix}"`;
                return { offset, text: insertion, cursorAdvance: insertion.length };
            });
            if (actions.some((action) => !action)) {
                await typeDefault('\n');
                return;
            }
            await applyCursorActions(editor, actions as CursorAction[]);
        }),
        vscode.commands.registerTextEditorCommand('zircon.typeQuote', async (editor) => {
            if (!isEnabled() || editor.document.languageId !== 'java') {
                await typeDefault('"');
                return;
            }
            const source = editor.document.getText();
            const actions = editor.selections.map((selection): CursorAction | undefined => {
                if (!selection.isEmpty) {
                    return undefined;
                }
                const offset = editor.document.offsetAt(selection.active);
                const literal = findTemplateAtOffset(source, offset);
                if (!literal || !literal.closed || !isInRange(literal, offset, TEMPLATE_CODE)) {
                    return undefined;
                }
                if (source[offset] === '"') {
                    return { offset, text: '', cursorAdvance: 1 };
                }
                return { offset, text: '""', cursorAdvance: 1 };
            });
            if (actions.some((action) => !action)) {
                await typeDefault('"');
                return;
            }
            await applyCursorActions(editor, actions as CursorAction[]);
        }),
        vscode.commands.registerTextEditorCommand('zircon.typeLeftBrace', async (editor) => {
            if (!isEnabled() || editor.document.languageId !== 'java') {
                await typeDefault('{');
                return;
            }
            const source = editor.document.getText();
            const templates = findTemplateLiterals(source);
            const actions = editor.selections.map((selection): CursorAction | undefined => {
                if (!selection.isEmpty) {
                    return undefined;
                }
                const offset = editor.document.offsetAt(selection.active);
                const literal = templates.find((item) => offset > item.openingQuote && offset <= item.endQuote);
                if (!literal || literal.prefix === 'STR.' || source[offset - 1] !== '$' || isEscaped(source, offset - 1)) {
                    return undefined;
                }
                return { offset, text: '{}', cursorAdvance: 1 };
            });
            if (actions.some((action) => !action)) {
                await typeDefault('{');
                return;
            }
            await applyCursorActions(editor, actions as CursorAction[]);
        }),
        vscode.workspace.onDidChangeTextDocument((event) => {
            if (!isEnabled() || event.document.languageId !== 'java') {
                return;
            }
            const editor = vscode.window.activeTextEditor;
            if (!editor || editor.document.uri.toString() !== event.document.uri.toString()) {
                return;
            }
            const shouldSuggest = event.contentChanges.some((change) => {
                if (change.text !== '.') {
                    return false;
                }
                const insertedEnd = change.rangeOffset + change.text.length;
                return event.document.getText(new vscode.Range(
                    event.document.positionAt(Math.max(0, insertedEnd - 2)),
                    event.document.positionAt(insertedEnd)
                )) === '?.';
            });
            if (shouldSuggest) {
                setTimeout(() => {
                    const active = vscode.window.activeTextEditor;
                    if (active && active.document.uri.toString() === event.document.uri.toString()) {
                        void vscode.commands.executeCommand('editor.action.triggerSuggest');
                    }
                }, 0);
            }
        })
    );
}

function isInRange(literal: TemplateLiteral, offset: number, style: number): boolean {
    return literal.ranges.some((range) => range.style === style
        && offset >= range.startIndex
        && offset <= range.endIndex);
}

async function applyCursorActions(editor: vscode.TextEditor, actions: CursorAction[]): Promise<void> {
    const sorted = [...actions].sort((left, right) => left.offset - right.offset);
    const applied = await editor.edit((builder) => {
        for (const action of sorted) {
            if (action.text.length > 0) {
                builder.insert(editor.document.positionAt(action.offset), action.text);
            }
        }
    }, { undoStopBefore: true, undoStopAfter: true });
    if (!applied) {
        return;
    }
    editor.selections = sorted.map((action, index) => {
        const precedingLength = sorted.slice(0, index).reduce((sum, item) => sum + item.text.length, 0);
        const position = editor.document.positionAt(action.offset + precedingLength + action.cursorAdvance);
        return new vscode.Selection(position, position);
    });
}

async function typeDefault(text: string): Promise<void> {
    await vscode.commands.executeCommand('default:type', { text });
}

function isEscaped(source: string, index: number): boolean {
    let backslashes = 0;
    for (let cursor = index - 1; cursor >= 0 && source[cursor] === '\\'; cursor -= 1) {
        backslashes += 1;
    }
    return backslashes % 2 === 1;
}
