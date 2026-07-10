import * as vscode from 'vscode';

const TEMPLATE_PREFIXES = ['STR.', 'f', 'j', '$'] as const;
const SCAN_CACHE = new Map<string, { version: number; result: ZirconScanResult }>();

export interface ZirconScanResult {
    diagnostics: vscode.Diagnostic[];
    prefixRanges: vscode.Range[];
    operatorRanges: vscode.Range[];
    templateStringCount: number;
}

interface TemplateParseResult {
    endIndex: number;
    isClosed: boolean;
    diagnostics: vscode.Diagnostic[];
    operatorRanges: vscode.Range[];
    prefixRange: vscode.Range;
}

interface NestedScanResult {
    endIndex: number;
    isClosed: boolean;
}

export function scanDocument(document: vscode.TextDocument): ZirconScanResult {
    const cacheKey = document.uri.toString();
    const cached = SCAN_CACHE.get(cacheKey);
    if (cached && cached.version === document.version) {
        return cached.result;
    }

    const text = document.getText();
    const diagnostics: vscode.Diagnostic[] = [];
    const prefixRanges: vscode.Range[] = [];
    const operatorRanges: vscode.Range[] = [];
    let templateStringCount = 0;

    for (let index = 0; index < text.length; index++) {
        if (text[index] === '/' && text[index + 1] === '/') {
            index = skipLineComment(text, index);
            continue;
        }
        if (text[index] === '/' && text[index + 1] === '*') {
            index = skipBlockComment(text, index);
            continue;
        }
        if (text[index] === '"' || text[index] === '\'') {
            index = skipQuotedSegment(text, index, text.length);
            continue;
        }
        const prefix = matchTemplatePrefix(text, index);
        if (!prefix) {
            continue;
        }

        const parseResult = parseTemplateString(document, text, index, prefix);
        templateStringCount += 1;
        diagnostics.push(...parseResult.diagnostics);
        prefixRanges.push(parseResult.prefixRange);
        operatorRanges.push(...parseResult.operatorRanges);
        index = Math.max(index, parseResult.endIndex);
    }

    operatorRanges.push(...scanOperators(document, text));

    const result = {
        diagnostics,
        prefixRanges,
        operatorRanges,
        templateStringCount
    };
    SCAN_CACHE.set(cacheKey, {
        version: document.version,
        result
    });
    return result;
}

export function clearScanCache(uri?: vscode.Uri): void {
    if (!uri) {
        SCAN_CACHE.clear();
        return;
    }
    SCAN_CACHE.delete(uri.toString());
}

function matchTemplatePrefix(text: string, index: number): string | undefined {
    for (const prefix of TEMPLATE_PREFIXES) {
        if (!text.startsWith(prefix, index)) {
            continue;
        }
        if (!isValidTemplatePrefixBoundary(text, index, prefix)) {
            continue;
        }
        const quoteIndex = index + prefix.length;
        if (quoteIndex >= text.length || text[quoteIndex] !== '"') {
            continue;
        }
        return prefix;
    }
    return undefined;
}

function parseTemplateString(
    document: vscode.TextDocument,
    text: string,
    prefixStart: number,
    prefix: string
): TemplateParseResult {
    const openingQuoteIndex = prefixStart + prefix.length;
    let cursor = openingQuoteIndex + 1;
    let closed = false;
    const diagnostics: vscode.Diagnostic[] = [];
    const operatorRanges: vscode.Range[] = [];

    while (cursor < text.length) {
        const current = text[cursor];
        if (current === '\r' || current === '\n') {
            break;
        }
        if (current === '"') {
            closed = true;
            break;
        }
        if (prefix === 'STR.') {
            if (current === '\\') {
                if (text[cursor + 1] === '{' && !isEscaped(text, cursor)) {
                    const nested = scanBracedInterpolation(text, cursor + 2);
                    cursor = nested.isClosed ? nested.endIndex + 1 : nested.endIndex;
                    continue;
                }
                cursor += 2;
                continue;
            }
            cursor += 1;
            continue;
        }
        if (current === '\\') {
            cursor += 2;
            continue;
        }
        if (current === '$' && !isDollarEscaped(text, cursor)) {
            const next = text[cursor + 1];
            if (next === '{') {
                const nested = scanBracedInterpolation(text, cursor + 2);
                cursor = nested.isClosed ? nested.endIndex + 1 : nested.endIndex;
                continue;
            }
            if (isIdentifierStart(next)) {
                cursor = scanSimpleInterpolation(text, cursor + 1);
                continue;
            }
        }
        cursor += 1;
    }

    if (!closed) {
        diagnostics.push(new vscode.Diagnostic(
            new vscode.Range(document.positionAt(prefixStart), document.positionAt(Math.min(cursor, text.length))),
            `未闭合的 Zircon 模板字符串：${prefix}"..."`,
            vscode.DiagnosticSeverity.Error
        ));
    } else {
        if (prefix === 'STR.') {
            scanStrTemplateOperators(document, text, openingQuoteIndex + 1, cursor, operatorRanges, diagnostics);
        } else {
            scanDollarTemplateOperators(document, text, openingQuoteIndex + 1, cursor, operatorRanges, diagnostics);
        }
    }

    return {
        endIndex: cursor,
        isClosed: closed,
        diagnostics,
        operatorRanges,
        prefixRange: new vscode.Range(document.positionAt(prefixStart), document.positionAt(openingQuoteIndex))
    };
}

function scanDollarTemplateOperators(
    document: vscode.TextDocument,
    text: string,
    contentStart: number,
    contentEnd: number,
    operatorRanges: vscode.Range[],
    diagnostics: vscode.Diagnostic[]
): void {
    for (let index = contentStart; index < contentEnd; index++) {
        if (text[index] !== '$' || isDollarEscaped(text, index)) {
            continue;
        }
        const next = text[index + 1];
        if (next === '{') {
            operatorRanges.push(new vscode.Range(document.positionAt(index), document.positionAt(index + 2)));
            const end = findBalancedClosing(text, index + 2, contentEnd, '{', '}');
            if (end === -1) {
                diagnostics.push(new vscode.Diagnostic(
                    new vscode.Range(document.positionAt(index), document.positionAt(Math.min(index + 2, contentEnd))),
                    '`${...}` 插值表达式未闭合。',
                    vscode.DiagnosticSeverity.Error
                ));
                continue;
            }
            operatorRanges.push(new vscode.Range(document.positionAt(end), document.positionAt(end + 1)));
            index = end;
            continue;
        }
        if (isIdentifierStart(next)) {
            operatorRanges.push(new vscode.Range(document.positionAt(index), document.positionAt(index + 1)));
        }
    }
}

function scanStrTemplateOperators(
    document: vscode.TextDocument,
    text: string,
    contentStart: number,
    contentEnd: number,
    operatorRanges: vscode.Range[],
    diagnostics: vscode.Diagnostic[]
): void {
    for (let index = contentStart; index < contentEnd - 1; index++) {
        if (text[index] !== '\\' || text[index + 1] !== '{' || isEscaped(text, index)) {
            continue;
        }
        operatorRanges.push(new vscode.Range(document.positionAt(index), document.positionAt(index + 2)));
        const end = findBalancedClosing(text, index + 2, contentEnd, '{', '}');
        if (end === -1) {
            diagnostics.push(new vscode.Diagnostic(
                new vscode.Range(document.positionAt(index), document.positionAt(index + 2)),
                '`\\{...}` 插值表达式未闭合。',
                vscode.DiagnosticSeverity.Error
            ));
            continue;
        }
        operatorRanges.push(new vscode.Range(document.positionAt(end), document.positionAt(end + 1)));
        index = end;
    }
}

function scanOperators(document: vscode.TextDocument, text: string): vscode.Range[] {
    const ranges: vscode.Range[] = [];
    const operatorPattern = /\?\.|\?:/g;
    for (let match = operatorPattern.exec(text); match !== null; match = operatorPattern.exec(text)) {
        const start = match.index;
        ranges.push(new vscode.Range(document.positionAt(start), document.positionAt(start + match[0].length)));
    }
    return ranges;
}

function findBalancedClosing(
    text: string,
    from: number,
    limit: number,
    open: string,
    close: string
): number {
    let depth = 0;
    for (let index = from; index < limit; index++) {
        const current = text[index];
        if (current === '\'' || current === '"') {
            index = skipQuotedSegment(text, index, limit);
            continue;
        }
        if (current === '\\') {
            index += 1;
            continue;
        }
        if (current === open) {
            depth += 1;
            continue;
        }
        if (current === close) {
            if (depth === 0) {
                return index;
            }
            depth -= 1;
        }
    }
    return -1;
}

function skipQuotedSegment(text: string, start: number, limit: number): number {
    const quote = text[start];
    for (let index = start + 1; index < limit; index++) {
        if (text[index] === '\\') {
            index += 1;
            continue;
        }
        if (text[index] === quote) {
            return index;
        }
    }
    return limit - 1;
}

function skipLineComment(text: string, start: number): number {
    for (let index = start + 2; index < text.length; index++) {
        if (text[index] === '\r' || text[index] === '\n') {
            return index - 1;
        }
    }
    return text.length - 1;
}

function skipBlockComment(text: string, start: number): number {
    for (let index = start + 2; index < text.length - 1; index++) {
        if (text[index] === '*' && text[index + 1] === '/') {
            return index + 1;
        }
    }
    return text.length - 1;
}

function scanBracedInterpolation(text: string, from: number): NestedScanResult {
    let depth = 1;
    for (let index = from; index < text.length; index++) {
        const current = text[index];
        if (current === '\r' || current === '\n') {
            return {
                endIndex: index,
                isClosed: false
            };
        }
        if (current === '\'' || current === '"') {
            index = skipQuotedSegment(text, index, text.length);
            continue;
        }
        if (current === '\\') {
            index += 1;
            continue;
        }
        if (current === '{') {
            depth += 1;
            continue;
        }
        if (current !== '}') {
            continue;
        }
        depth -= 1;
        if (depth === 0) {
            return {
                endIndex: index,
                isClosed: true
            };
        }
    }
    return {
        endIndex: text.length,
        isClosed: false
    };
}

function scanSimpleInterpolation(text: string, from: number): number {
    let parenDepth = 0;
    for (let index = from; index < text.length; index++) {
        const current = text[index];
        if (current === '\r' || current === '\n') {
            return index;
        }
        if (current === '\\') {
            index += 1;
            continue;
        }
        if (parenDepth > 0) {
            if (current === '\'' || current === '"') {
                index = skipQuotedSegment(text, index, text.length);
                continue;
            }
            if (current === '(') {
                parenDepth += 1;
                continue;
            }
            if (current === ')') {
                parenDepth -= 1;
            }
            continue;
        }
        if (isSimpleInterpolationChar(current)) {
            continue;
        }
        if (current === '(') {
            parenDepth = 1;
            continue;
        }
        return index - 1;
    }
    return text.length - 1;
}

function isSimpleInterpolationChar(value: string): boolean {
    return /[A-Za-z0-9_\u4e00-\u9fa5.$]/.test(value);
}

function isEscaped(text: string, index: number): boolean {
    let slashCount = 0;
    for (let cursor = index - 1; cursor >= 0 && text[cursor] === '\\'; cursor--) {
        slashCount += 1;
    }
    return slashCount % 2 === 1;
}

function isDollarEscaped(text: string, index: number): boolean {
    return index > 0 && text[index - 1] === '\\';
}

function isIdentifierStart(value: string | undefined): boolean {
    return value !== undefined && /[A-Za-z_\u4e00-\u9fa5$]/.test(value);
}

function isValidTemplatePrefixBoundary(text: string, index: number, prefix: string): boolean {
    const previous = index > 0 ? text[index - 1] : undefined;
    if (prefix === '$') {
        return previous === undefined || !/[A-Za-z0-9_\u4e00-\u9fa5$."')\]]/.test(previous);
    }
    return previous === undefined || !/[A-Za-z0-9_\u4e00-\u9fa5$]/.test(previous);
}
