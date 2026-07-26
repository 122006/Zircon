import {
    splitTemplateString,
    TEMPLATE_CODE,
    TEMPLATE_FORMAT,
    TEMPLATE_STRING,
    TemplateRange,
    TemplateSyntax
} from './templateStringSplitter';

export type ConversionKind =
    | 'java-literal-to-template'
    | 'concat-to-template'
    | 'format-to-template'
    | 'template-to-java'
    | 'template-to-format'
    | 'remove-quote-escapes';

export interface TextConversion {
    start: number;
    end: number;
    newText: string;
    title: string;
    kind: ConversionKind;
}

export interface TemplateLiteral {
    start: number;
    end: number;
    prefix: string;
    openingQuote: number;
    endQuote: number;
    closed: boolean;
    ranges: TemplateRange[];
}

const TEMPLATE_PREFIXES = ['STR.', 'f', 'j', '$'] as const;
const JAVA_FORMAT = /%(\d+\$)?([-#+ 0,(<]*)?(\d+)?(\.\d*)?([tT])?([a-zA-Z%])/g;

export function findTemplateLiterals(source: string): TemplateLiteral[] {
    const result: TemplateLiteral[] = [];
    for (let index = 0; index < source.length; index += 1) {
        if (source[index] === '/' && source[index + 1] === '/') {
            index = skipLineComment(source, index) - 1;
            continue;
        }
        if (source[index] === '/' && source[index + 1] === '*') {
            index = skipBlockComment(source, index) - 1;
            continue;
        }
        if (source[index] === '"' || source[index] === '\'') {
            index = skipQuoted(source, index) - 1;
            continue;
        }
        const prefix = matchTemplatePrefix(source, index);
        if (!prefix) {
            continue;
        }
        const syntax: TemplateSyntax = prefix === 'STR.' ? 'str' : prefix === 'f' ? 'format' : 'dollar';
        const newline = source.indexOf('\n', index);
        const scanEnd = newline < 0 ? source.length : newline + 1;
        // The shared splitter normally receives one javac string token. Limit
        // the editor-side input to the physical line and append a sentinel so
        // a closing quote at EOF is still visited by the identical state
        // machine. An invalid string can no longer consume later templates.
        const split = splitTemplateString(`${source.slice(index, scanEnd)}\0`, prefix, syntax);
        const endQuote = index + split.endQuoteIndex;
        const closed = split.endQuoteIndex >= prefix.length + 1 && endQuote < scanEnd && source[endQuote] === '"';
        const end = closed ? endQuote + 1 : scanEnd;
        result.push({
            start: index,
            end,
            prefix,
            openingQuote: index + prefix.length,
            endQuote,
            closed,
            ranges: split.ranges.map((range) => ({
                style: range.style,
                startIndex: index + range.startIndex,
                endIndex: index + range.endIndex
            }))
        });
        index = Math.max(index, end - 1);
    }
    return result;
}

export function findTemplateAtOffset(source: string, offset: number): TemplateLiteral | undefined {
    return findTemplateLiterals(source).find((literal) => offset > literal.openingQuote && offset <= literal.endQuote);
}

export function collectConversions(source: string): TextConversion[] {
    return deduplicateConversions([
        ...collectTemplateConversions(source),
        ...collectStringFormatConversions(source),
        ...collectConcatenationConversions(source),
        ...collectJavaLiteralConversions(source)
    ]);
}

export function collectToTemplateChanges(source: string): TextConversion[] {
    return selectNonOverlapping([
        ...collectStringFormatConversions(source),
        ...collectConcatenationConversions(source),
        ...collectJavaLiteralConversions(source)
    ]);
}

export function collectToJavaChanges(source: string): TextConversion[] {
    return selectNonOverlapping(
        collectTemplateConversions(source).filter((conversion) => conversion.kind === 'template-to-java')
    );
}

export function applyConversions(source: string, conversions: readonly TextConversion[]): string {
    let result = source;
    for (const conversion of [...conversions].sort((left, right) => right.start - left.start)) {
        result = result.slice(0, conversion.start) + conversion.newText + result.slice(conversion.end);
    }
    return result;
}

function collectTemplateConversions(source: string): TextConversion[] {
    const conversions: TextConversion[] = [];
    for (const literal of findTemplateLiterals(source)) {
        if (!literal.closed) {
            continue;
        }
        const normal = templateToJava(source, literal);
        if (normal !== undefined) {
            conversions.push({
                start: literal.start,
                end: literal.end,
                newText: normal,
                title: 'Zircon: 转换为普通 Java 字符串',
                kind: 'template-to-java'
            });
        }
        const literalText = source.slice(literal.start, literal.end);
        if (literalText.includes('\\"')) {
            conversions.push({
                start: literal.start,
                end: literal.end,
                newText: literalText.replace(/\\"/g, '"'),
                title: 'Zircon: 移除模板字符串中不必要的引号转义',
                kind: 'remove-quote-escapes'
            });
        }
        if (literal.prefix === '$' && literal.ranges.some((range) => {
            return range.style === TEMPLATE_CODE && /^%[^:]+:/.test(source.slice(range.startIndex, range.endIndex));
        })) {
            conversions.push({
                start: literal.start,
                end: literal.start + literal.prefix.length,
                newText: 'f',
                title: 'Zircon: 转换为 f 格式化模板字符串',
                kind: 'template-to-format'
            });
        }
    }
    return conversions;
}

function templateToJava(source: string, literal: TemplateLiteral): string | undefined {
    if (literal.prefix === 'j') {
        return undefined;
    }
    if (literal.prefix === 'f') {
        let format = '';
        let pendingFormat: string | undefined;
        const args: string[] = [];
        for (const range of literal.ranges) {
            const value = source.slice(range.startIndex, range.endIndex);
            if (range.style === TEMPLATE_FORMAT) {
                pendingFormat = value;
            } else if (range.style === TEMPLATE_STRING) {
                format += value;
            } else if (range.style === TEMPLATE_CODE) {
                format += pendingFormat ?? '%s';
                pendingFormat = undefined;
                args.push(transferTemplateCode(value));
            }
        }
        return `String.format("${format}"${args.map((argument) => `, ${argument}`).join('')})`;
    }

    const parts: string[] = [];
    for (const range of literal.ranges) {
        const value = source.slice(range.startIndex, range.endIndex);
        if (range.style === TEMPLATE_STRING) {
            parts.push(`"${literal.prefix === 'STR.' ? value : value.replace(/\\\$/g, '$')}"`);
        } else if (range.style === TEMPLATE_CODE) {
            const code = transferTemplateCode(value);
            parts.push(parts.length === 0 ? `String.valueOf(${code})` : `(${code})`);
        }
    }
    if (parts.length === 0) {
        return '""';
    }
    return `(${parts.join(' + ')})`;
}

function collectStringFormatConversions(source: string): TextConversion[] {
    const conversions: TextConversion[] = [];
    const pattern = /\bString\s*\.\s*format\s*\(/g;
    for (let match = pattern.exec(source); match; match = pattern.exec(source)) {
        if (isInsideCommentOrLiteral(source, match.index)) {
            continue;
        }
        const opening = source.indexOf('(', match.index);
        const closing = findBalanced(source, opening, '(', ')');
        if (closing < 0) {
            continue;
        }
        const args = splitTopLevel(source, opening + 1, closing, ',');
        if (args.length === 0) {
            continue;
        }
        const formatLiteral = args[0].text.trim();
        if (!isJavaStringLiteral(formatLiteral)) {
            continue;
        }
        const replacement = stringFormatToTemplate(formatLiteral, args.slice(1).map((argument) => argument.text.trim()));
        conversions.push({
            start: match.index,
            end: closing + 1,
            newText: replacement,
            title: 'Zircon: String.format 转换为 f 模板字符串',
            kind: 'format-to-template'
        });
        pattern.lastIndex = closing + 1;
    }
    return conversions;
}

function stringFormatToTemplate(formatLiteral: string, args: string[]): string {
    const content = formatLiteral.slice(1, -1).replace(/\$ /g, '$ ');
    let argumentIndex = 0;
    let lastIndex = 0;
    let output = 'f"';
    JAVA_FORMAT.lastIndex = 0;
    for (let match = JAVA_FORMAT.exec(content); match; match = JAVA_FORMAT.exec(content)) {
        output += escapeTemplateDollar(content.slice(lastIndex, match.index));
        const token = match[0];
        if (token === '%%') {
            output += '%';
        } else if (token === '%n') {
            output += '\\n';
        } else {
            const argument = compactExpression(args[argumentIndex] ?? '');
            argumentIndex += 1;
            if (token === '%s' || token === '%d') {
                output += `\${${argument}}`;
            } else {
                output += `\${${token}:${argument}}`;
            }
        }
        lastIndex = match.index + token.length;
    }
    output += escapeTemplateDollar(content.slice(lastIndex));
    return `${output}"`;
}

function collectJavaLiteralConversions(source: string): TextConversion[] {
    const conversions: TextConversion[] = [];
    for (const literal of findJavaStringLiterals(source)) {
        const text = source.slice(literal.start, literal.end);
        if (!text.includes('${')) {
            continue;
        }
        conversions.push({
            start: literal.start,
            end: literal.end,
            newText: `$${text}`,
            title: 'Zircon: 转换为 $ 模板字符串',
            kind: 'java-literal-to-template'
        });
    }
    return conversions;
}

function collectConcatenationConversions(source: string): TextConversion[] {
    const conversions: TextConversion[] = [];
    for (const statement of findStatementExpressions(source)) {
        const pieces = splitTopLevel(source, statement.start, statement.end, '+');
        if (pieces.length < 2 || !pieces.some((piece) => isJavaStringLiteral(piece.text.trim()))) {
            continue;
        }
        const replacement = concatenationToTemplate(pieces.map((piece) => piece.text.trim()));
        conversions.push({
            start: statement.start,
            end: statement.end,
            newText: replacement,
            title: 'Zircon: 字符串拼接转换为 $ 模板字符串',
            kind: 'concat-to-template'
        });
    }
    return conversions;
}

function concatenationToTemplate(pieces: string[]): string {
    let output = '$"';
    for (const piece of pieces) {
        if (isJavaStringLiteral(piece)) {
            output += escapeTemplateDollar(piece.slice(1, -1));
        } else {
            output += `\${${compactExpression(stripOuterParentheses(piece))}}`;
        }
    }
    return `${output}"`;
}

function findStatementExpressions(source: string): Array<{ start: number; end: number }> {
    const expressions: Array<{ start: number; end: number }> = [];
    let segmentStart = 0;
    let parentheses = 0;
    let brackets = 0;
    for (let index = 0; index < source.length; index += 1) {
        if (source[index] === '/' && source[index + 1] === '/') {
            index = skipLineComment(source, index) - 1;
            continue;
        }
        if (source[index] === '/' && source[index + 1] === '*') {
            index = skipBlockComment(source, index) - 1;
            continue;
        }
        const template = matchTemplatePrefix(source, index);
        if (template) {
            const split = splitTemplateString(source.slice(index), template, template === 'STR.' ? 'str' : template === 'f' ? 'format' : 'dollar');
            index += Math.max(template.length, split.endQuoteIndex);
            continue;
        }
        if (source[index] === '"' || source[index] === '\'') {
            index = skipQuoted(source, index) - 1;
            continue;
        }
        if (source[index] === '(') {
            parentheses += 1;
        } else if (source[index] === ')') {
            parentheses = Math.max(0, parentheses - 1);
        } else if (source[index] === '[') {
            brackets += 1;
        } else if (source[index] === ']') {
            brackets = Math.max(0, brackets - 1);
        } else if ((source[index] === '{' || source[index] === '}') && parentheses === 0 && brackets === 0) {
            segmentStart = index + 1;
        } else if (source[index] === ';' && parentheses === 0 && brackets === 0) {
            const expression = extractStatementExpression(source, segmentStart, index);
            if (expression) {
                expressions.push(expression);
            }
            segmentStart = index + 1;
        }
    }
    return expressions;
}

function extractStatementExpression(source: string, start: number, end: number): { start: number; end: number } | undefined {
    let expressionStart = start;
    const statement = source.slice(start, end);
    const returnMatch = /\breturn\s+/.exec(statement);
    if (returnMatch) {
        expressionStart = start + returnMatch.index + returnMatch[0].length;
    } else {
        const assignment = findTopLevelAssignment(source, start, end);
        if (assignment < 0) {
            return undefined;
        }
        expressionStart = assignment + 1;
    }
    while (expressionStart < end && /\s/.test(source[expressionStart])) {
        expressionStart += 1;
    }
    while (end > expressionStart && /\s/.test(source[end - 1])) {
        end -= 1;
    }
    return end > expressionStart ? { start: expressionStart, end } : undefined;
}

function findTopLevelAssignment(source: string, start: number, end: number): number {
    let depth = 0;
    for (let index = start; index < end; index += 1) {
        if (source[index] === '"' || source[index] === '\'') {
            index = skipQuoted(source, index) - 1;
            continue;
        }
        if ('([{'.includes(source[index])) {
            depth += 1;
        } else if (')]}'.includes(source[index])) {
            depth = Math.max(0, depth - 1);
        } else if (source[index] === '=' && depth === 0) {
            const previous = source[index - 1] ?? '';
            const next = source[index + 1] ?? '';
            if (previous !== '=' && previous !== '!' && previous !== '<' && previous !== '>' && next !== '=' && next !== '>') {
                return index;
            }
        }
    }
    return -1;
}

function findJavaStringLiterals(source: string): Array<{ start: number; end: number }> {
    const literals: Array<{ start: number; end: number }> = [];
    for (let index = 0; index < source.length; index += 1) {
        if (source[index] === '/' && source[index + 1] === '/') {
            index = skipLineComment(source, index) - 1;
            continue;
        }
        if (source[index] === '/' && source[index + 1] === '*') {
            index = skipBlockComment(source, index) - 1;
            continue;
        }
        const template = matchTemplatePrefix(source, index);
        if (template) {
            const split = splitTemplateString(source.slice(index), template, template === 'STR.' ? 'str' : template === 'f' ? 'format' : 'dollar');
            index += Math.max(template.length, split.endQuoteIndex);
            continue;
        }
        if (source[index] === '\'') {
            index = skipQuoted(source, index) - 1;
            continue;
        }
        if (source[index] !== '"') {
            continue;
        }
        const end = skipQuoted(source, index);
        if (end > index + 1 && source[end - 1] === '"') {
            literals.push({ start: index, end });
        }
        index = end - 1;
    }
    return literals;
}

function matchTemplatePrefix(source: string, index: number): string | undefined {
    for (const prefix of TEMPLATE_PREFIXES) {
        if (!source.startsWith(`${prefix}"`, index)) {
            continue;
        }
        const previous = source[index - 1];
        if (previous && /[\w$\u4e00-\u9fa5]/u.test(previous)) {
            continue;
        }
        return prefix;
    }
    return undefined;
}

function splitTopLevel(source: string, start: number, end: number, delimiter: string): Array<{ text: string; start: number; end: number }> {
    const parts: Array<{ text: string; start: number; end: number }> = [];
    let partStart = start;
    let depth = 0;
    for (let index = start; index < end; index += 1) {
        if (source[index] === '/' && source[index + 1] === '/') {
            index = Math.min(end, skipLineComment(source, index)) - 1;
            continue;
        }
        if (source[index] === '/' && source[index + 1] === '*') {
            index = Math.min(end, skipBlockComment(source, index)) - 1;
            continue;
        }
        const template = matchTemplatePrefix(source, index);
        if (template) {
            const split = splitTemplateString(source.slice(index), template, template === 'STR.' ? 'str' : template === 'f' ? 'format' : 'dollar');
            index += Math.max(template.length, split.endQuoteIndex);
            continue;
        }
        if (source[index] === '"' || source[index] === '\'') {
            index = Math.min(end, skipQuoted(source, index)) - 1;
            continue;
        }
        if ('([{'.includes(source[index])) {
            depth += 1;
        } else if (')]}'.includes(source[index])) {
            depth = Math.max(0, depth - 1);
        } else if (source[index] === delimiter && depth === 0) {
            parts.push({ text: source.slice(partStart, index), start: partStart, end: index });
            partStart = index + 1;
        }
    }
    parts.push({ text: source.slice(partStart, end), start: partStart, end });
    return parts;
}

function findBalanced(source: string, opening: number, open: string, close: string): number {
    let depth = 0;
    for (let index = opening; index < source.length; index += 1) {
        if (source[index] === '"' || source[index] === '\'') {
            index = skipQuoted(source, index) - 1;
            continue;
        }
        if (source[index] === '/' && source[index + 1] === '/') {
            index = skipLineComment(source, index) - 1;
            continue;
        }
        if (source[index] === '/' && source[index + 1] === '*') {
            index = skipBlockComment(source, index) - 1;
            continue;
        }
        if (source[index] === open) {
            depth += 1;
        } else if (source[index] === close && --depth === 0) {
            return index;
        }
    }
    return -1;
}

function isInsideCommentOrLiteral(source: string, offset: number): boolean {
    let index = 0;
    while (index < offset) {
        if (source[index] === '/' && source[index + 1] === '/') {
            const end = skipLineComment(source, index);
            if (end > offset) {
                return true;
            }
            index = end;
        } else if (source[index] === '/' && source[index + 1] === '*') {
            const end = skipBlockComment(source, index);
            if (end > offset) {
                return true;
            }
            index = end;
        } else if (source[index] === '"' || source[index] === '\'') {
            const end = skipQuoted(source, index);
            if (end > offset) {
                return true;
            }
            index = end;
        } else {
            index += 1;
        }
    }
    return false;
}

function skipQuoted(source: string, start: number): number {
    const quote = source[start];
    let index = start + 1;
    while (index < source.length) {
        if (source[index] === quote && !isEscaped(source, index)) {
            return index + 1;
        }
        index += 1;
    }
    return source.length;
}

function skipLineComment(source: string, start: number): number {
    const newline = source.indexOf('\n', start + 2);
    return newline < 0 ? source.length : newline + 1;
}

function skipBlockComment(source: string, start: number): number {
    const closing = source.indexOf('*/', start + 2);
    return closing < 0 ? source.length : closing + 2;
}

function isEscaped(source: string, index: number): boolean {
    let backslashes = 0;
    for (let cursor = index - 1; cursor >= 0 && source[cursor] === '\\'; cursor -= 1) {
        backslashes += 1;
    }
    return backslashes % 2 === 1;
}

function isJavaStringLiteral(value: string): boolean {
    return /^"(?:\\.|[^"\\])*"$/s.test(value);
}

function compactExpression(value: string): string {
    return value.trim().replace(/\s*\n\s*/g, ' ');
}

function stripOuterParentheses(value: string): string {
    const trimmed = value.trim();
    if (trimmed.startsWith('(') && findBalanced(trimmed, 0, '(', ')') === trimmed.length - 1) {
        return trimmed.slice(1, -1).trim();
    }
    return trimmed;
}

function transferTemplateCode(value: string): string {
    return value.replace(/\\?([a-z0-9"'])/g, '$1').replace(/\\\\/g, '\\');
}

function escapeTemplateDollar(value: string): string {
    return value.replace(/(^|[^\\])\$/g, '$1\\$');
}

function deduplicateConversions(conversions: TextConversion[]): TextConversion[] {
    const seen = new Set<string>();
    return conversions.filter((conversion) => {
        const key = `${conversion.start}:${conversion.end}:${conversion.kind}:${conversion.newText}`;
        if (seen.has(key)) {
            return false;
        }
        seen.add(key);
        return true;
    });
}

function selectNonOverlapping(conversions: TextConversion[]): TextConversion[] {
    const selected: TextConversion[] = [];
    for (const conversion of conversions.sort((left, right) => left.start - right.start || right.end - left.end)) {
        if (selected.some((item) => conversion.start < item.end && conversion.end > item.start)) {
            continue;
        }
        selected.push(conversion);
    }
    return selected;
}
