import * as vscode from 'vscode';
import { findTemplateLiterals, TemplateLiteral } from './stringConversions';
import { TEMPLATE_CODE, TEMPLATE_FORMAT } from './templateStringSplitter';

const SCAN_CACHE = new Map<string, { version: number; result: ZirconScanResult }>();

export interface ZirconScanResult {
    diagnostics: vscode.Diagnostic[];
    prefixRanges: vscode.Range[];
    operatorRanges: vscode.Range[];
    templateStringCount: number;
}

/**
 * Diagnostics, semantic highlighting, conversions and editor input now all
 * consume the TypeScript port of base TemplateStringSplitter. Do not add a
 * second template-string state machine here.
 */
export function scanDocument(document: vscode.TextDocument): ZirconScanResult {
    const cacheKey = document.uri.toString();
    const cached = SCAN_CACHE.get(cacheKey);
    if (cached && cached.version === document.version) {
        return cached.result;
    }

    const text = document.getText();
    const literals = findTemplateLiterals(text);
    const diagnostics: vscode.Diagnostic[] = [];
    const prefixRanges: vscode.Range[] = [];
    const operatorRanges: vscode.Range[] = [];

    for (const literal of literals) {
        prefixRanges.push(toRange(document, literal.start, literal.openingQuote));
        if (!literal.closed) {
            diagnostics.push(new vscode.Diagnostic(
                toRange(document, literal.start, Math.max(literal.start + literal.prefix.length + 1, literal.end)),
                `未闭合的 Zircon 模板字符串：${literal.prefix}"..."`,
                vscode.DiagnosticSeverity.Error
            ));
        }
        addTemplateOperatorRanges(document, text, literal, operatorRanges, diagnostics);
    }
    addLanguageOperatorRanges(document, text, literals, operatorRanges);

    const result = {
        diagnostics,
        prefixRanges,
        operatorRanges,
        templateStringCount: literals.length
    };
    SCAN_CACHE.set(cacheKey, { version: document.version, result });
    return result;
}

export function clearScanCache(uri?: vscode.Uri): void {
    if (!uri) {
        SCAN_CACHE.clear();
        return;
    }
    SCAN_CACHE.delete(uri.toString());
}

function addTemplateOperatorRanges(
    document: vscode.TextDocument,
    text: string,
    literal: TemplateLiteral,
    ranges: vscode.Range[],
    diagnostics: vscode.Diagnostic[]
): void {
    for (const range of literal.ranges) {
        if (range.style === TEMPLATE_FORMAT) {
            ranges.push(toRange(document, range.startIndex, range.endIndex));
            continue;
        }
        if (range.style !== TEMPLATE_CODE) {
            continue;
        }
        if (literal.prefix === 'STR.' && text.slice(range.startIndex - 2, range.startIndex) === '\\{') {
            ranges.push(toRange(document, range.startIndex - 2, range.startIndex));
        } else if (text.slice(range.startIndex - 2, range.startIndex) === '${') {
            ranges.push(toRange(document, range.startIndex - 2, range.startIndex));
        } else if (text[range.startIndex - 1] === '$') {
            ranges.push(toRange(document, range.startIndex - 1, range.startIndex));
        }
        if (text[range.endIndex] === '}') {
            ranges.push(toRange(document, range.endIndex, range.endIndex + 1));
        } else if (literal.closed && range.endIndex >= literal.endQuote) {
            diagnostics.push(new vscode.Diagnostic(
                toRange(document, Math.max(literal.openingQuote + 1, range.startIndex - 2), literal.endQuote),
                literal.prefix === 'STR.' ? '`\\{...}` 插值表达式未闭合。' : '`${...}` 插值表达式未闭合。',
                vscode.DiagnosticSeverity.Error
            ));
        }
    }
}

function addLanguageOperatorRanges(
    document: vscode.TextDocument,
    text: string,
    templates: TemplateLiteral[],
    ranges: vscode.Range[]
): void {
    let templateIndex = 0;
    for (let index = 0; index < text.length; index += 1) {
        while (templateIndex < templates.length && index >= templates[templateIndex].end) {
            templateIndex += 1;
        }
        const template = templates[templateIndex];
        if (template && index >= template.start && index < template.end) {
            const codeRange = template.ranges.find((range) => range.style === TEMPLATE_CODE
                && index >= range.startIndex && index < range.endIndex);
            if (!codeRange) {
                index = Math.max(index, template.end - 1);
                continue;
            }
        } else if (text[index] === '/' && text[index + 1] === '/') {
            const newline = text.indexOf('\n', index + 2);
            index = newline < 0 ? text.length : newline;
            continue;
        } else if (text[index] === '/' && text[index + 1] === '*') {
            const closing = text.indexOf('*/', index + 2);
            index = closing < 0 ? text.length : closing + 1;
            continue;
        } else if (text[index] === '"' || text[index] === '\'') {
            index = skipQuoted(text, index) - 1;
            continue;
        }

        const operator = text.slice(index, index + 2);
        if (operator === '?.' || operator === '?:') {
            ranges.push(toRange(document, index, index + 2));
            index += 1;
        }
    }
}

function skipQuoted(text: string, start: number): number {
    const quote = text[start];
    for (let index = start + 1; index < text.length; index += 1) {
        if (text[index] === '\\') {
            index += 1;
        } else if (text[index] === quote) {
            return index + 1;
        }
    }
    return text.length;
}

function toRange(document: vscode.TextDocument, start: number, end: number): vscode.Range {
    return new vscode.Range(document.positionAt(start), document.positionAt(Math.max(start, end)));
}
