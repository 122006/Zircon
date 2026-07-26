export const TEMPLATE_STRING = 0;
export const TEMPLATE_CODE = 1;
export const TEMPLATE_FORMAT = 2;

export type TemplateSyntax = 'dollar' | 'format' | 'str';

export interface TemplateRange {
    style: number;
    startIndex: number;
    endIndex: number;
}

export interface TemplateSplitResult {
    ranges: TemplateRange[];
    originalString: string;
    endQuoteIndex: number;
}

/**
 * TypeScript port of base/.../TemplateStringSplitter.java.
 * Keep the state machine in sync: javac, IDEA, JDT and VS Code must agree on
 * interpolation and closing-quote boundaries.
 */
export function splitTemplateString(text: string, prefix: string, syntax: TemplateSyntax): TemplateSplitResult {
    return syntax === 'str'
        ? splitStr(text, prefix)
        : splitDollar(text, prefix, syntax === 'format');
}

function splitDollar(text: string, prefix: string, formatted: boolean): TemplateSplitResult {
    const ranges: TemplateRange[] = [];
    let start = prefix.length + 1;
    let mode = -1;
    let parenthesisCount = 0;
    for (let cursor = start; cursor < text.length - 1; cursor += 1) {
        const current = text[cursor];
        if (isSinglyEscaped(text, cursor)) {
            continue;
        }
        if (mode === 2) {
            if (current === '{') {
                parenthesisCount += 1;
            }
            if (current === '}' && --parenthesisCount === 0) {
                addCodeRange(ranges, text, start, cursor, formatted);
                start = cursor + 1;
                mode = -1;
            }
            continue;
        }
        if (mode === 1) {
            if (isShorthandCodeCharacter(current, parenthesisCount > 0)) {
                continue;
            }
            if (current === '(' && /^\([^)]*\).*$/s.test(text.slice(cursor))) {
                parenthesisCount += 1;
                continue;
            }
            if (current === ')' && parenthesisCount > 0) {
                if (/^\)\.[A-Za-z_\u4e00-\u9fa5$]+/s.test(text.slice(cursor))) {
                    parenthesisCount -= 1;
                    continue;
                }
                ranges.push({ style: TEMPLATE_CODE, startIndex: start, endIndex: cursor + 1 });
                start = cursor + 1;
                mode = -1;
                continue;
            }
            ranges.push({ style: TEMPLATE_CODE, startIndex: start, endIndex: cursor });
            mode = -1;
            start = cursor;
            cursor -= 1;
            continue;
        }
        if (current === '$'
            && !isDoublyEscapedDollar(text, cursor)
            && cursor + 1 < text.length
            && isInterpolationStart(text[cursor + 1])) {
            if (cursor !== start) {
                ranges.push({ style: TEMPLATE_STRING, startIndex: start, endIndex: cursor });
            }
            if (text[cursor + 1] === '{') {
                const codeStart = cursor + 2;
                const codeEnd = findEmbeddedExpressionEnd(text, codeStart);
                if (codeEnd < 0) {
                    start = codeStart;
                    mode = 2;
                    parenthesisCount = 0;
                } else {
                    addCodeRange(ranges, text, codeStart, codeEnd, formatted);
                    start = codeEnd + 1;
                    cursor = codeEnd;
                    mode = -1;
                }
            } else {
                start = cursor + 1;
                mode = 1;
                parenthesisCount = 0;
            }
            continue;
        }
        if (mode === -1 && current === '"') {
            if (cursor > start) {
                ranges.push({ style: TEMPLATE_STRING, startIndex: start, endIndex: cursor });
            }
            return { ranges, originalString: text.slice(0, cursor + 1), endQuoteIndex: cursor };
        }
    }
    const fallbackEnd = Math.max(0, text.length - 1);
    if (fallbackEnd > start) {
        if (mode > 0) {
            addCodeRange(ranges, text, start, fallbackEnd, formatted);
        } else {
            ranges.push({ style: TEMPLATE_STRING, startIndex: start, endIndex: fallbackEnd });
        }
    }
    return { ranges, originalString: text, endQuoteIndex: fallbackEnd };
}

function splitStr(text: string, prefix: string): TemplateSplitResult {
    const ranges: TemplateRange[] = [];
    let start = prefix.length + 1;
    let mode = -1;
    let braceCount = 0;
    for (let cursor = start; cursor < text.length - 1; cursor += 1) {
        const current = text[cursor];
        if (current !== '{' && isSinglyEscaped(text, cursor)) {
            continue;
        }
        if (mode === 2) {
            if (current === '{') {
                braceCount += 1;
            }
            if (current === '}' && --braceCount === 0) {
                if (cursor > start) {
                    ranges.push({ style: TEMPLATE_CODE, startIndex: start, endIndex: cursor });
                }
                start = cursor + 1;
                mode = -1;
            }
            continue;
        }
        if (current === '\\'
            && !isDoublyEscapedDollar(text, cursor)
            && cursor + 1 < text.length
            && text[cursor + 1] === '{') {
            if (cursor !== start) {
                ranges.push({ style: TEMPLATE_STRING, startIndex: start, endIndex: cursor });
            }
            const codeStart = cursor + 2;
            const codeEnd = findEmbeddedExpressionEnd(text, codeStart);
            if (codeEnd < 0) {
                start = codeStart;
                mode = 2;
                braceCount = 0;
            } else {
                if (codeEnd > codeStart) {
                    ranges.push({ style: TEMPLATE_CODE, startIndex: codeStart, endIndex: codeEnd });
                }
                start = codeEnd + 1;
                cursor = codeEnd;
                mode = -1;
            }
            continue;
        }
        if (mode === -1 && current === '"') {
            if (cursor > start) {
                ranges.push({ style: TEMPLATE_STRING, startIndex: start, endIndex: cursor });
            }
            return { ranges, originalString: text.slice(0, cursor + 1), endQuoteIndex: cursor };
        }
    }
    const fallbackEnd = Math.max(0, text.length - 1);
    if (fallbackEnd > start) {
        ranges.push({ style: mode > 0 ? TEMPLATE_CODE : TEMPLATE_STRING, startIndex: start, endIndex: fallbackEnd });
    }
    return { ranges, originalString: text, endQuoteIndex: fallbackEnd };
}

function addCodeRange(ranges: TemplateRange[], text: string, start: number, end: number, formatted: boolean): void {
    if (end <= start) {
        return;
    }
    const code = text.slice(start, end);
    if (formatted && code.startsWith('%')) {
        const separator = code.indexOf(':');
        if (separator >= 0) {
            ranges.push({ style: TEMPLATE_FORMAT, startIndex: start, endIndex: start + separator });
            ranges.push({ style: TEMPLATE_CODE, startIndex: start + separator + 1, endIndex: end });
            return;
        }
    }
    ranges.push({ style: TEMPLATE_CODE, startIndex: start, endIndex: end });
}

function isInterpolationStart(value: string): boolean {
    return value === '{' || value === '$' || value === '_'
        || /[A-Za-z\u4e00-\u9fa5]/u.test(value);
}

function isShorthandCodeCharacter(value: string, insideParentheses: boolean): boolean {
    if (insideParentheses) {
        return value !== ')';
    }
    return value === '.' || value === '$' || value === '_'
        || /[0-9A-Za-z\u4e00-\u9fa5]/u.test(value);
}

function isSinglyEscaped(text: string, index: number): boolean {
    return index >= 2 && text[index - 1] === '\\' && text[index - 2] !== '\\';
}

function isDoublyEscapedDollar(text: string, index: number): boolean {
    return index >= 2 && text[index - 1] === '\\' && text[index - 2] === '\\';
}

function findEmbeddedExpressionEnd(text: string, codeStart: number): number {
    const normal = 0;
    const character = 1;
    const string = 2;
    const lineComment = 3;
    const blockComment = 4;
    let state = normal;
    let braceDepth = 1;
    for (let index = codeStart; index < text.length; index += 1) {
        const current = text[index];
        const next = text[index + 1] ?? '\0';
        if (state === lineComment) {
            if (current === '\r' || current === '\n') {
                state = normal;
            }
            continue;
        }
        if (state === blockComment) {
            if (current === '*' && next === '/') {
                state = normal;
                index += 1;
            }
            continue;
        }
        if (state === character) {
            if (current === '\'' && isTransferredQuoteDelimiter(text, index)) {
                state = normal;
            }
            continue;
        }
        if (state === string) {
            if (current === '"' && isTransferredQuoteDelimiter(text, index)) {
                state = normal;
            }
            continue;
        }
        if (current === '/' && next === '/') {
            state = lineComment;
            index += 1;
        } else if (current === '/' && next === '*') {
            state = blockComment;
            index += 1;
        } else if (current === '\'' && isTransferredQuoteDelimiter(text, index)) {
            state = character;
        } else if (current === '"' && isTransferredQuoteDelimiter(text, index)) {
            state = string;
        } else if (current === '{') {
            braceDepth += 1;
        } else if (current === '}' && --braceDepth === 0) {
            return index;
        }
    }
    return -1;
}

function isTransferredQuoteDelimiter(text: string, quoteIndex: number): boolean {
    let backslashes = 0;
    for (let index = quoteIndex - 1; index >= 0 && text[index] === '\\'; index -= 1) {
        backslashes += 1;
    }
    const afterOptionalEscapeRemoval = Math.max(0, backslashes - 1);
    const afterBackslashPairReduction = Math.floor((afterOptionalEscapeRemoval + 1) / 2);
    return afterBackslashPairReduction % 2 === 0;
}
