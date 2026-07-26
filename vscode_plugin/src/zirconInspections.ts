import * as vscode from 'vscode';
import { findTemplateLiterals } from './stringConversions';
import { TEMPLATE_CODE, TEMPLATE_FORMAT } from './templateStringSplitter';

export interface ZirconDiagnosticFix {
    title: string;
    range: vscode.Range;
    newText: string;
}

export interface FixableZirconDiagnostic extends vscode.Diagnostic {
    zirconFix?: ZirconDiagnosticFix;
}

interface ExMethodHeader {
    annotationRanges: Array<{ start: number; end: number }>;
    annotationText: string;
    headerStart: number;
    methodNameStart: number;
    methodNameEnd: number;
    parameters: string[];
    isStatic: boolean;
    hasExplicitTarget: boolean;
}

const FORMAT_TOKEN = /^%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d*)?[tT]?[a-zA-Z%]$/;

export function inspectZirconDocument(document: vscode.TextDocument): vscode.Diagnostic[] {
    const text = document.getText();
    return [
        ...inspectExMethodDeclarations(document, text),
        ...inspectFormatStrings(document, text),
        ...inspectPrimitiveOptionalChains(document, text)
    ];
}

function inspectExMethodDeclarations(document: vscode.TextDocument, text: string): vscode.Diagnostic[] {
    const diagnostics: vscode.Diagnostic[] = [];
    for (const header of findExMethodHeaders(text)) {
        for (const duplicate of header.annotationRanges.slice(1)) {
            diagnostics.push(diagnostic(
                document,
                duplicate.start,
                duplicate.end,
                '重复的 @ExMethod 注解。',
                vscode.DiagnosticSeverity.Warning,
                'zircon.exMethod.duplicate'
            ));
        }
        if (!header.isStatic) {
            const insertionOffset = findStaticInsertionOffset(text, header.headerStart, header.methodNameStart);
            const item = diagnostic(
                document,
                header.methodNameStart,
                header.methodNameEnd,
                '@ExMethod 只能标注 static 方法。',
                vscode.DiagnosticSeverity.Error,
                'zircon.exMethod.mustBeStatic'
            );
            item.zirconFix = {
                title: 'Zircon: 为扩展方法添加 static',
                range: offsetRange(document, insertionOffset, insertionOffset),
                newText: 'static '
            };
            diagnostics.push(item);
        }
        if (!header.hasExplicitTarget && header.parameters.length === 0) {
            diagnostics.push(diagnostic(
                document,
                header.methodNameStart,
                header.methodNameEnd,
                '非静态目标扩展方法必须用第一个参数声明代理类型。',
                vscode.DiagnosticSeverity.Warning,
                'zircon.exMethod.missingReceiver'
            ));
        } else if (!header.hasExplicitTarget && /\.\.\./.test(header.parameters[0] ?? '')) {
            diagnostics.push(diagnostic(
                document,
                header.methodNameStart,
                header.methodNameEnd,
                '扩展方法的第一个代理参数不能是可变参数。',
                vscode.DiagnosticSeverity.Error,
                'zircon.exMethod.varargReceiver'
            ));
        }
    }
    return diagnostics;
}

function inspectFormatStrings(document: vscode.TextDocument, text: string): vscode.Diagnostic[] {
    const diagnostics: vscode.Diagnostic[] = [];
    for (const literal of findTemplateLiterals(text)) {
        if (!literal.closed || literal.prefix !== 'f') {
            continue;
        }
        for (let index = 0; index < literal.ranges.length; index += 1) {
            const formatRange = literal.ranges[index];
            if (formatRange.style !== TEMPLATE_FORMAT) {
                continue;
            }
            const token = text.slice(formatRange.startIndex, formatRange.endIndex);
            if (!FORMAT_TOKEN.test(token)) {
                diagnostics.push(diagnostic(
                    document,
                    formatRange.startIndex,
                    formatRange.endIndex,
                    `非法的 String.format 格式符：${token}`,
                    vscode.DiagnosticSeverity.Error,
                    'zircon.string.invalidFormat'
                ));
                continue;
            }
            const codeRange = literal.ranges[index + 1];
            if (!codeRange || codeRange.style !== TEMPLATE_CODE) {
                continue;
            }
            const expression = text.slice(codeRange.startIndex, codeRange.endIndex).trim();
            const actualType = inferExpressionType(text, codeRange.startIndex, expression);
            if (actualType && !isFormatCompatible(token, actualType)) {
                diagnostics.push(diagnostic(
                    document,
                    codeRange.startIndex,
                    codeRange.endIndex,
                    `格式符 ${token} 与表达式类型 ${actualType} 不匹配。`,
                    vscode.DiagnosticSeverity.Error,
                    'zircon.string.formatTypeMismatch'
                ));
            }
        }
    }
    return diagnostics;
}

function inspectPrimitiveOptionalChains(document: vscode.TextDocument, text: string): vscode.Diagnostic[] {
    const primitiveReturns = collectPrimitiveReturnTypes(text);
    if (primitiveReturns.size === 0) {
        return [];
    }
    const diagnostics: vscode.Diagnostic[] = [];
    const chainPattern = /[A-Za-z_$][\w$]*(?:\([^;\r\n]*?\))?(?:(?:\?\.|\.)[A-Za-z_$][\w$]*(?:\([^;\r\n]*?\))?)+/g;
    for (let match = chainPattern.exec(text); match; match = chainPattern.exec(text)) {
        const chain = match[0];
        if (!chain.includes('?.')) {
            continue;
        }
        const finalCall = /\.([A-Za-z_$][\w$]*)\s*\([^()]*\)\s*$/.exec(chain);
        if (!finalCall) {
            continue;
        }
        const primitive = primitiveReturns.get(finalCall[1]);
        if (!primitive) {
            continue;
        }
        const trailing = text.slice(match.index + chain.length, match.index + chain.length + 8);
        if (/^\s*\?:/.test(trailing)) {
            continue;
        }
        const defaultValue = primitiveDefaultValue(primitive);
        const next = text.slice(match.index + chain.length).trimStart()[0];
        const needsParentheses = next !== undefined && ![';', ',', ')'].includes(next);
        const replacement = needsParentheses
            ? `(${chain} ?: ${defaultValue})`
            : `${chain} ?: ${defaultValue}`;
        const item = diagnostic(
            document,
            match.index,
            match.index + chain.length,
            `可选链最终返回基本类型 ${primitive}，空值拆箱可能导致异常；请提供 Elvis 默认值。`,
            vscode.DiagnosticSeverity.Warning,
            'zircon.optional.primitiveResult'
        );
        item.zirconFix = {
            title: `Zircon: 追加 Elvis 默认值 ${defaultValue}`,
            range: offsetRange(document, match.index, match.index + chain.length),
            newText: replacement
        };
        diagnostics.push(item);
    }
    return diagnostics;
}

function findExMethodHeaders(text: string): ExMethodHeader[] {
    const annotationPattern = /@(?:zircon\.)?ExMethod\b(?:\s*\((?:[^()]|\([^()]*\))*\))?/g;
    const annotations: Array<{ start: number; end: number; text: string }> = [];
    for (let match = annotationPattern.exec(text); match; match = annotationPattern.exec(text)) {
        annotations.push({ start: match.index, end: annotationPattern.lastIndex, text: match[0] });
    }
    const grouped = new Map<number, ExMethodHeader>();
    for (const annotation of annotations) {
        const header = parseFollowingMethodHeader(text, annotation.end);
        if (!header) {
            continue;
        }
        const existing = grouped.get(header.methodNameStart);
        if (existing) {
            existing.annotationRanges.push({ start: annotation.start, end: annotation.end });
            existing.annotationText += ` ${annotation.text}`;
            existing.hasExplicitTarget ||= /\bex\s*=/.test(annotation.text);
        } else {
            grouped.set(header.methodNameStart, {
                ...header,
                annotationRanges: [{ start: annotation.start, end: annotation.end }],
                annotationText: annotation.text,
                hasExplicitTarget: /\bex\s*=/.test(annotation.text)
            });
        }
    }
    return [...grouped.values()];
}

function parseFollowingMethodHeader(
    text: string,
    from: number
): Omit<ExMethodHeader, 'annotationRanges' | 'annotationText' | 'hasExplicitTarget'> | undefined {
    let headerStart = from;
    while (headerStart < text.length && /\s/.test(text[headerStart])) {
        headerStart += 1;
    }
    while (text[headerStart] === '@') {
        const lineEnd = text.indexOf('\n', headerStart);
        if (lineEnd < 0) {
            return undefined;
        }
        headerStart = lineEnd + 1;
        while (headerStart < text.length && /\s/.test(text[headerStart])) {
            headerStart += 1;
        }
    }
    const openParen = text.indexOf('(', headerStart);
    if (openParen < 0 || /[;{}]/.test(text.slice(headerStart, openParen))) {
        return undefined;
    }
    const methodNameMatch = /([A-Za-z_$][\w$]*)\s*$/.exec(text.slice(headerStart, openParen));
    if (!methodNameMatch) {
        return undefined;
    }
    const methodNameStart = headerStart + (methodNameMatch.index ?? 0);
    const closeParen = findBalancedClose(text, openParen, '(', ')');
    if (closeParen < 0) {
        return undefined;
    }
    const headerPrefix = text.slice(headerStart, methodNameStart);
    return {
        headerStart,
        methodNameStart,
        methodNameEnd: methodNameStart + methodNameMatch[1].length,
        parameters: splitTopLevel(text.slice(openParen + 1, closeParen), ','),
        isStatic: /\bstatic\b/.test(headerPrefix)
    };
}

function findStaticInsertionOffset(text: string, headerStart: number, methodNameStart: number): number {
    const prefix = text.slice(headerStart, methodNameStart);
    const visibility = /^\s*(public|protected|private)\s+/.exec(prefix);
    return visibility ? headerStart + visibility[0].length : headerStart;
}

function collectPrimitiveReturnTypes(text: string): Map<string, string> {
    const result = new Map<string, string>();
    const pattern = /\b(boolean|byte|short|int|long|float|double|char)\s+([A-Za-z_$][\w$]*)\s*\(/g;
    for (let match = pattern.exec(text); match; match = pattern.exec(text)) {
        result.set(match[2], match[1]);
    }
    return result;
}

function inferExpressionType(text: string, offset: number, expression: string): string | undefined {
    if (/^"(?:\\.|[^"\\])*"$/.test(expression)) return 'String';
    if (/^'(?:\\.|[^'\\])'$/.test(expression)) return 'char';
    if (/^(true|false)$/.test(expression)) return 'boolean';
    if (/^[+-]?\d+[lL]$/.test(expression)) return 'long';
    if (/^[+-]?\d+[fF]$/.test(expression)) return 'float';
    if (/^[+-]?\d+\.\d+(?:[dD])?$/.test(expression)) return 'double';
    if (/^[+-]?\d+$/.test(expression)) return 'int';
    if (!/^[A-Za-z_$][\w$]*$/.test(expression)) return undefined;
    const declarationPattern = new RegExp(`\\b([A-Za-z_$][\\w$<>,.?\\[\\]]*)\\s+${escapeRegExp(expression)}\\b`, 'g');
    let type: string | undefined;
    for (let match = declarationPattern.exec(text.slice(0, offset)); match; match = declarationPattern.exec(text.slice(0, offset))) {
        type = match[1];
    }
    return type;
}

function isFormatCompatible(format: string, type: string): boolean {
    const conversion = format[format.length - 1].toLowerCase();
    const simpleType = type.replace(/<.*>/, '').replace(/^java\.lang\./, '');
    const integral = /^(byte|short|int|long|integer|bigInteger)$/i.test(simpleType);
    const floating = /^(float|double|bigDecimal)$/i.test(simpleType);
    if (conversion === 's' || conversion === 'b' || conversion === 'h') return true;
    if ('dox'.includes(conversion)) return integral;
    if ('efga'.includes(conversion)) return floating;
    if (conversion === 'c') return integral || /^(char|character)$/i.test(simpleType);
    if (format.toLowerCase().includes('t')) return /^(date|calendar|long|temporalAccessor)$/i.test(simpleType);
    return true;
}

function primitiveDefaultValue(type: string): string {
    if (type === 'boolean') return 'false';
    if (type === 'char') return `'\\0'`;
    if (type === 'long') return '0L';
    if (type === 'float') return '0F';
    if (type === 'double') return '0D';
    return '0';
}

function splitTopLevel(text: string, separator: string): string[] {
    if (text.trim().length === 0) return [];
    const result: string[] = [];
    let start = 0;
    let depth = 0;
    for (let index = 0; index < text.length; index += 1) {
        const char = text[index];
        if (char === '"' || char === '\'') {
            index = skipQuoted(text, index) - 1;
        } else if ('(<[{'.includes(char)) {
            depth += 1;
        } else if (')>]}'.includes(char)) {
            depth = Math.max(0, depth - 1);
        } else if (char === separator && depth === 0) {
            result.push(text.slice(start, index).trim());
            start = index + 1;
        }
    }
    result.push(text.slice(start).trim());
    return result;
}

function findBalancedClose(text: string, opening: number, open: string, close: string): number {
    let depth = 0;
    for (let index = opening; index < text.length; index += 1) {
        if (text[index] === '"' || text[index] === '\'') {
            index = skipQuoted(text, index) - 1;
        } else if (text[index] === open) {
            depth += 1;
        } else if (text[index] === close && --depth === 0) {
            return index;
        }
    }
    return -1;
}

function skipQuoted(text: string, start: number): number {
    const quote = text[start];
    for (let index = start + 1; index < text.length; index += 1) {
        if (text[index] === '\\') index += 1;
        else if (text[index] === quote) return index + 1;
    }
    return text.length;
}

function diagnostic(
    document: vscode.TextDocument,
    start: number,
    end: number,
    message: string,
    severity: vscode.DiagnosticSeverity,
    code: string
): FixableZirconDiagnostic {
    const item = new vscode.Diagnostic(offsetRange(document, start, end), message, severity) as FixableZirconDiagnostic;
    item.source = 'Zircon';
    item.code = code;
    return item;
}

function offsetRange(document: vscode.TextDocument, start: number, end: number): vscode.Range {
    return new vscode.Range(document.positionAt(start), document.positionAt(end));
}

function escapeRegExp(value: string): string {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
