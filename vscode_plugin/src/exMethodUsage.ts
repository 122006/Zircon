import * as vscode from 'vscode';
import {
    buildClassType,
    buildDocumentContext,
    eraseType,
    ExMethodIndex,
    findDeclaredVariableType,
    findReceiverType,
    InternalJavaContext,
    normalizeType,
    readWordAt,
    resolveTypeName,
    simpleNameOf
} from './exMethodIndex';
import { ExMethodDescriptor } from './exMethodModel';
import { countStructuralBraceDelta } from './javaLexing';

interface MemberInvocationContext {
    methodName: string;
    receiverTypes: string[];
    receiverType: string | undefined;
    allowDirectOnly: boolean;
    methodNameRange: vscode.Range;
}

export interface ExMethodCallContext extends MemberInvocationContext {
    activeParameter: number;
    targets: ExMethodDescriptor[];
}

export interface ExMethodCompletionContext {
    methodNamePrefix: string;
    methodNameRange: vscode.Range;
    receiverType: string | undefined;
    receiverTypes: string[];
    targets: ExMethodDescriptor[];
}

export interface ExMethodInvocationSource {
    receiverExpression: string;
    receiverStart: number;
    methodName: string;
    methodStart: number;
    methodEnd: number;
    argumentsText: string;
    callStart: number;
    callEnd: number;
}

export function findExMethodInvocationSource(
    document: vscode.TextDocument,
    position: vscode.Position
): ExMethodInvocationSource | undefined {
    const wordRange = document.getWordRangeAtPosition(position);
    if (!wordRange) {
        return undefined;
    }
    const text = document.getText();
    const methodStart = document.offsetAt(wordRange.start);
    const methodEnd = document.offsetAt(wordRange.end);
    const operator = findReceiverOperator(text, methodStart);
    if (!operator || operator.kind !== 'member') {
        return undefined;
    }
    const receiverExpression = readExpressionBackward(text, operator.operatorStart);
    if (!receiverExpression) {
        return undefined;
    }
    let receiverEnd = operator.operatorStart;
    while (receiverEnd > 0 && /\s/.test(text[receiverEnd - 1])) {
        receiverEnd -= 1;
    }
    const receiverStart = Math.max(0, receiverEnd - receiverExpression.length);
    const openParen = skipWhitespaceForward(text, methodEnd);
    if (text[openParen] !== '(') {
        return undefined;
    }
    const closeParen = findMatchingClose(text, openParen, '(', ')');
    if (closeParen < 0) {
        return undefined;
    }
    return {
        receiverExpression,
        receiverStart,
        methodName: text.slice(methodStart, methodEnd),
        methodStart,
        methodEnd,
        argumentsText: text.slice(openParen + 1, closeParen),
        callStart: receiverStart,
        callEnd: closeParen + 1
    };
}

interface IdentifierMatch {
    value: string;
    startOffset: number;
    endOffset: number;
}

interface ReceiverOperator {
    kind: 'member' | 'reference';
    operatorStart: number;
}

interface RawInvocationContext {
    methodName: string;
    methodNameRange: vscode.Range;
    receiverExpression?: string;
    allowDirectOnly: boolean;
    receiverTypes: string[];
    argumentCount?: number;
    activeParameter?: number;
}

export function resolveMethodTargets(
    index: ExMethodIndex,
    document: vscode.TextDocument,
    position: vscode.Position
): ExMethodDescriptor[] {
    const declarations = index.findDeclarations(document, position);
    if (declarations.length > 0) {
        return declarations;
    }

    const invocation = findUsageContextAtPosition(index, document, position);
    if (!invocation) {
        return [];
    }
    const context = buildDocumentContext(document, position);
    return findMatchingDescriptors(index, invocation.methodName, invocation.receiverTypes, invocation.allowDirectOnly)
        .filter((descriptor) => isDescriptorVisibleToContext(descriptor, context));
}

export function resolveDefinitionTargets(
    index: ExMethodIndex,
    document: vscode.TextDocument,
    position: vscode.Position
): ExMethodDescriptor[] {
    const exactTargets = resolveMethodTargets(index, document, position);
    if (exactTargets.length > 0) {
        return exactTargets;
    }

    const invocation = findUsageContextAtPosition(index, document, position);
    if (!invocation) {
        return [];
    }

    const context = buildDocumentContext(document, position);
    const candidates = index.findByName(invocation.methodName)
        .filter((descriptor) => !descriptor.shouldInvokeDirectly || invocation.allowDirectOnly)
        .filter((descriptor) => isDescriptorVisibleToContext(descriptor, context));

    const argumentCount = invocation.argumentCount;
    const filtered = argumentCount === undefined
        ? candidates
        : candidates.filter((descriptor) => matchesArgumentCount(descriptor, argumentCount));

    return selectPreferredDefinitionTargets(index, filtered, invocation.receiverTypes, context.packageName, document.uri);
}

export function resolveCallContext(
    index: ExMethodIndex,
    document: vscode.TextDocument,
    position: vscode.Position
): ExMethodCallContext | undefined {
    const text = document.getText();
    const cursorOffset = document.offsetAt(position);
    const raw = findCallInvocation(index, document, text, cursorOffset);
    if (!raw) {
        return undefined;
    }

    const context = buildDocumentContext(document, position);
    const targets = findMatchingDescriptors(
        index,
        raw.methodName,
        raw.receiverTypes,
        raw.allowDirectOnly,
        raw.activeParameter !== undefined ? raw.activeParameter + 1 : undefined
    ).filter((descriptor) => isDescriptorVisibleToContext(descriptor, context));
    if (targets.length === 0) {
        return undefined;
    }
    return {
        methodName: raw.methodName,
        receiverTypes: raw.receiverTypes,
        receiverType: raw.receiverTypes[0],
        allowDirectOnly: raw.allowDirectOnly,
        methodNameRange: raw.methodNameRange,
        activeParameter: raw.activeParameter ?? 0,
        targets
    };
}

export function resolveCompletionContext(
    index: ExMethodIndex,
    document: vscode.TextDocument,
    position: vscode.Position
): ExMethodCompletionContext | undefined {
    const text = document.getText();
    const cursorOffset = document.offsetAt(position);
    const identifier = readIdentifierBackward(text, cursorOffset);
    const prefixStart = identifier ? identifier.startOffset : cursorOffset;
    const prefix = identifier ? text.slice(identifier.startOffset, cursorOffset) : '';
    const operator = findReceiverOperator(text, prefixStart);
    let receiverTypes: string[] = [];
    let allowDirectOnly = false;

    if (operator) {
        const receiverExpression = readExpressionBackward(text, operator.operatorStart);
        if (!receiverExpression) {
            return undefined;
        }
        receiverTypes = inferExpressionTypes(index, document, prefixStart, receiverExpression);
        allowDirectOnly = isThisLikeExpression(receiverExpression);
    } else if (prefix.length > 0) {
        receiverTypes = inferImplicitReceiverTypes(document, cursorOffset);
        allowDirectOnly = true;
    } else {
        return undefined;
    }

    if (receiverTypes.length === 0 && !allowDirectOnly) {
        return undefined;
    }

    const methodNameRange = new vscode.Range(
        document.positionAt(prefixStart),
        position
    );
    const targets = index.findMatches(receiverTypes, allowDirectOnly)
        .filter((descriptor) => prefix.length === 0 || descriptor.methodName.startsWith(prefix));
    if (targets.length === 0) {
        return undefined;
    }
    return {
        methodNamePrefix: prefix,
        methodNameRange,
        receiverType: receiverTypes[0],
        receiverTypes,
        targets
    };
}

function findUsageContextAtPosition(
    index: ExMethodIndex,
    document: vscode.TextDocument,
    position: vscode.Position
): RawInvocationContext | undefined {
    const wordRange = document.getWordRangeAtPosition(position);
    if (!wordRange) {
        return undefined;
    }

    const methodName = readWordAt(document, position);
    if (!methodName) {
        return undefined;
    }

    const text = document.getText();
    const methodStart = document.offsetAt(wordRange.start);
    const methodEnd = document.offsetAt(wordRange.end);
    const operator = findReceiverOperator(text, methodStart);
    const argumentCount = readInvocationArgumentCount(text, methodEnd);

    if (operator) {
        const receiverExpression = readExpressionBackward(text, operator.operatorStart);
        if (!receiverExpression) {
            return undefined;
        }
        const receiverTypes = inferExpressionTypes(index, document, methodStart, receiverExpression);
        return {
            methodName,
            methodNameRange: wordRange,
            receiverExpression,
            allowDirectOnly: isThisLikeExpression(receiverExpression),
            receiverTypes,
            argumentCount
        };
    }

    const nextOffset = skipWhitespaceForward(text, methodEnd);
    if (nextOffset < text.length && text[nextOffset] === '(') {
        return {
            methodName,
            methodNameRange: wordRange,
            allowDirectOnly: true,
            receiverTypes: inferImplicitReceiverTypes(document, methodStart),
            argumentCount
        };
    }

    return undefined;
}

function findMatchingDescriptors(
    index: ExMethodIndex,
    methodName: string,
    receiverTypes: readonly string[],
    allowDirectOnly: boolean,
    argumentCount?: number
): ExMethodDescriptor[] {
    const matches = index.findMatches(receiverTypes, allowDirectOnly)
        .filter((descriptor) => descriptor.methodName === methodName);
    if (argumentCount === undefined) {
        return matches;
    }

    const filtered = matches.filter((descriptor) => matchesArgumentCount(descriptor, argumentCount));
    return filtered.length > 0 ? filtered : matches;
}

function matchesArgumentCount(descriptor: ExMethodDescriptor, argumentCount: number): boolean {
    const visibleParameters = getVisibleParameters(descriptor);
    if (visibleParameters.length === argumentCount) {
        return true;
    }
    const lastParameter = visibleParameters[visibleParameters.length - 1];
    return Boolean(lastParameter && /\.\.\.$/.test(lastParameter.type) && argumentCount >= visibleParameters.length - 1);
}

function getVisibleParameters(descriptor: ExMethodDescriptor) {
    return descriptor.isStaticExtension ? descriptor.parameters : descriptor.parameters.slice(1);
}

function findCallInvocation(
    index: ExMethodIndex,
    document: vscode.TextDocument,
    text: string,
    cursorOffset: number
): RawInvocationContext | undefined {
    let nestedParens = 0;
    let nestedAngles = 0;
    let nestedBrackets = 0;
    let nestedBraces = 0;

    for (let indexOffset = cursorOffset - 1; indexOffset >= 0; indexOffset--) {
        const char = text[indexOffset];
        if (char === ')') {
            nestedParens++;
            continue;
        }
        if (char === '(') {
            if (nestedParens === 0 && nestedAngles === 0 && nestedBrackets === 0 && nestedBraces === 0) {
                const invocation = buildCallInvocation(index, document, text, indexOffset, cursorOffset);
                if (invocation) {
                    return invocation;
                }
            } else {
                nestedParens = Math.max(nestedParens - 1, 0);
            }
            continue;
        }
        if (char === '>') {
            nestedAngles++;
            continue;
        }
        if (char === '<') {
            nestedAngles = Math.max(nestedAngles - 1, 0);
            continue;
        }
        if (char === ']') {
            nestedBrackets++;
            continue;
        }
        if (char === '[') {
            nestedBrackets = Math.max(nestedBrackets - 1, 0);
            continue;
        }
        if (char === '}') {
            nestedBraces++;
            continue;
        }
        if (char === '{') {
            nestedBraces = Math.max(nestedBraces - 1, 0);
        }
    }
    return undefined;
}

function buildCallInvocation(
    index: ExMethodIndex,
    document: vscode.TextDocument,
    text: string,
    openParenOffset: number,
    cursorOffset: number
): RawInvocationContext | undefined {
    const methodIdentifier = readIdentifierBackward(text, openParenOffset);
    if (!methodIdentifier) {
        return undefined;
    }

    const operator = findReceiverOperator(text, methodIdentifier.startOffset);
    const methodNameRange = new vscode.Range(
        document.positionAt(methodIdentifier.startOffset),
        document.positionAt(methodIdentifier.endOffset)
    );
    const activeParameter = countActiveParameters(text, openParenOffset + 1, cursorOffset);

    if (!operator) {
        return {
            methodName: methodIdentifier.value,
            methodNameRange,
            allowDirectOnly: true,
            receiverTypes: inferImplicitReceiverTypes(document, methodIdentifier.startOffset),
            argumentCount: countTotalArguments(text, openParenOffset),
            activeParameter
        };
    }

    if (operator.kind === 'reference') {
        return undefined;
    }

    const receiverExpression = readExpressionBackward(text, operator.operatorStart);
    if (!receiverExpression) {
        return undefined;
    }

    return {
        methodName: methodIdentifier.value,
        methodNameRange,
        receiverExpression,
        allowDirectOnly: isThisLikeExpression(receiverExpression),
        receiverTypes: inferExpressionTypes(index, document, methodIdentifier.startOffset, receiverExpression),
        argumentCount: countTotalArguments(text, openParenOffset),
        activeParameter
    };
}

function isDescriptorVisibleToContext(descriptor: ExMethodDescriptor, context: InternalJavaContext): boolean {
    if (descriptor.packageName === context.packageName) {
        return true;
    }

    if (context.visibleTypes.get(descriptor.declaringClass) === descriptor.qualifiedDeclaringClass) {
        return true;
    }

    for (const importedType of context.imports.values()) {
        if (importedType === descriptor.qualifiedDeclaringClass) {
            return true;
        }
        if (importedType.endsWith('.*')) {
            const importedPackage = importedType.slice(0, -2);
            if (descriptor.packageName === importedPackage) {
                return true;
            }
        }
    }

    return false;
}

export function isExMethodDescriptorVisible(
    document: vscode.TextDocument,
    position: vscode.Position,
    descriptor: ExMethodDescriptor
): boolean {
    return isDescriptorVisibleToContext(descriptor, buildDocumentContext(document, position));
}

function selectPreferredDefinitionTargets(
    index: ExMethodIndex,
    targets: ExMethodDescriptor[],
    receiverTypes: readonly string[],
    currentPackageName: string,
    currentDocumentUri: vscode.Uri
): ExMethodDescriptor[] {
    if (targets.length <= 1) {
        return targets;
    }

    const rankedTargets = targets.map((descriptor) => ({
        descriptor,
        matchedTargetType: pickMatchedTargetType(index, descriptor, receiverTypes)
    }));

    let bestTargetType: string | undefined;
    let narrowedTargets = rankedTargets;
    for (const candidate of rankedTargets) {
        if (!candidate.matchedTargetType) {
            continue;
        }
        if (!bestTargetType) {
            bestTargetType = candidate.matchedTargetType;
            narrowedTargets = rankedTargets.filter((entry) => isSameDescriptorTarget(entry.matchedTargetType, bestTargetType));
            continue;
        }
        if (isSameDescriptorTarget(candidate.matchedTargetType, bestTargetType)) {
            continue;
        }
        if (isMoreSpecificDescriptorTarget(index, candidate.matchedTargetType, bestTargetType)) {
            bestTargetType = candidate.matchedTargetType;
            narrowedTargets = rankedTargets.filter((entry) => isSameDescriptorTarget(entry.matchedTargetType, bestTargetType));
        }
    }

    const sorted = [...narrowedTargets.map((entry) => entry.descriptor)].sort((left, right) => {
        const packageCompare = comparePackageCloseness(currentPackageName, left.qualifiedDeclaringClass, right.qualifiedDeclaringClass);
        if (packageCompare !== 0) {
            return -packageCompare;
        }
        const leftScore = definitionTargetScore(left, currentDocumentUri);
        const rightScore = definitionTargetScore(right, currentDocumentUri);
        if (leftScore !== rightScore) {
            return leftScore - rightScore;
        }
        if (left.qualifiedDeclaringClass !== right.qualifiedDeclaringClass) {
            return left.qualifiedDeclaringClass.localeCompare(right.qualifiedDeclaringClass);
        }
        return left.line - right.line;
    });

    return sorted.length > 0 ? [sorted[0]] : [];
}

function definitionTargetScore(descriptor: ExMethodDescriptor, currentDocumentUri: vscode.Uri): number {
    if (descriptor.uri.toString() === currentDocumentUri.toString()) {
        return 0;
    }
    if (descriptor.uri.scheme === 'file') {
        return 1;
    }
    return 2;
}

function readInvocationArgumentCount(text: string, methodEndOffset: number): number | undefined {
    const openParenOffset = skipWhitespaceForward(text, methodEndOffset);
    if (openParenOffset >= text.length || text[openParenOffset] !== '(') {
        return undefined;
    }
    return countTotalArguments(text, openParenOffset);
}

function countTotalArguments(text: string, openParenOffset: number): number {
    let depth = 0;
    let angleDepth = 0;
    let bracketDepth = 0;
    let braceDepth = 0;
    let hasContent = false;
    let argumentCount = 1;

    for (let index = openParenOffset + 1; index < text.length; index++) {
        const char = text[index];
        if (char === '(') {
            depth++;
            hasContent = true;
            continue;
        }
        if (char === ')') {
            if (depth === 0) {
                return hasContent ? argumentCount : 0;
            }
            depth = Math.max(depth - 1, 0);
            continue;
        }
        if (char === '<') {
            angleDepth++;
            hasContent = true;
            continue;
        }
        if (char === '>') {
            angleDepth = Math.max(angleDepth - 1, 0);
            continue;
        }
        if (char === '[') {
            bracketDepth++;
            hasContent = true;
            continue;
        }
        if (char === ']') {
            bracketDepth = Math.max(bracketDepth - 1, 0);
            continue;
        }
        if (char === '{') {
            braceDepth++;
            hasContent = true;
            continue;
        }
        if (char === '}') {
            braceDepth = Math.max(braceDepth - 1, 0);
            continue;
        }
        if (char === ',' && depth === 0 && angleDepth === 0 && bracketDepth === 0 && braceDepth === 0) {
            argumentCount++;
            continue;
        }
        if (!/\s/.test(char)) {
            hasContent = true;
        }
    }

    return hasContent ? argumentCount : 0;
}

function pickMatchedTargetType(
    index: ExMethodIndex,
    descriptor: ExMethodDescriptor,
    receiverTypes: readonly string[]
): string | undefined {
    let bestTargetType: string | undefined;
    for (const targetType of descriptor.targetTypes) {
        if (receiverTypes.length > 0 && !receiverTypes.some((receiverType) => index.isTypeAssignable(receiverType, targetType))) {
            continue;
        }
        if (!bestTargetType || isMoreSpecificDescriptorTarget(index, targetType, bestTargetType)) {
            bestTargetType = targetType;
        }
    }
    return bestTargetType;
}

function isMoreSpecificDescriptorTarget(index: ExMethodIndex, candidateType: string, currentType: string): boolean {
    return index.isTypeAssignable(candidateType, currentType) && !index.isTypeAssignable(currentType, candidateType);
}

function isSameDescriptorTarget(leftType: string | undefined, rightType: string | undefined): boolean {
    return normalizeType(leftType ?? '') === normalizeType(rightType ?? '');
}

function comparePackageCloseness(currentPackageName: string, leftOwnerClassName: string, rightOwnerClassName: string): number {
    const leftParts = leftOwnerClassName.split('.');
    const rightParts = rightOwnerClassName.split('.');
    const currentParts = currentPackageName.split('.');
    for (let index = 0; index < currentParts.length; index++) {
        const currentPart = currentParts[index];
        const leftMatches = leftParts.length > index && leftParts[index] === currentPart;
        const rightMatches = rightParts.length > index && rightParts[index] === currentPart;
        if (leftMatches && rightMatches) {
            continue;
        }
        if (leftMatches) {
            return 1;
        }
        if (rightMatches) {
            return -1;
        }
    }
    if (leftParts.length < rightParts.length) {
        return 1;
    }
    if (leftParts.length > rightParts.length) {
        return -1;
    }
    return 0;
}

function inferImplicitReceiverTypes(document: vscode.TextDocument, anchorOffset: number): string[] {
    const position = document.positionAt(anchorOffset);
    const context = buildDocumentContext(document, position);
    const receiverTypes: string[] = [
        ...inferAnonymousReceiverTypes(document.getText(), anchorOffset, context),
        ...inferEnclosingNamedReceiverTypes(context, anchorOffset)
    ];

    if (receiverTypes.length === 0) {
        const currentClass = context.currentClassQualifiedName
            ?? resolveTypeName(context.currentClassName ?? '', context);
        if (currentClass) {
            receiverTypes.push(currentClass);
        }
    }

    return dedupeTypes(receiverTypes.flatMap((receiverType) => [receiverType, buildClassType(receiverType)]));
}

function inferEnclosingNamedReceiverTypes(context: InternalJavaContext, anchorOffset: number): string[] {
    return context.typeDeclarations
        .filter((candidate) => anchorOffset >= candidate.startOffset && anchorOffset <= candidate.bodyEndOffset)
        .sort((left, right) => {
            const leftSpan = left.bodyEndOffset - left.startOffset;
            const rightSpan = right.bodyEndOffset - right.startOffset;
            return leftSpan - rightSpan;
        })
        .map((candidate) => candidate.qualifiedName);
}

function inferAnonymousReceiverTypes(text: string, anchorOffset: number, context: InternalJavaContext): string[] {
    interface AnonymousReceiverDeclaration {
        qualifiedName: string;
        startOffset: number;
        bodyEndOffset: number;
        bodyDepth: number;
    }

    const declarations: AnonymousReceiverDeclaration[] = [];
    const headerPattern = /\bnew\s+([A-Za-z_$][\w$.]*(?:\s*<[^{}()]+>)?)\s*\([^{};]*\)\s*\{/g;
    const stack: AnonymousReceiverDeclaration[] = [];
    let depth = 0;
    let cursor = 0;

    const closeCompletedDeclarations = (nextOffset: number): void => {
        while (stack.length > 0 && stack[stack.length - 1].bodyDepth > depth) {
            const completed = stack.pop();
            if (completed) {
                completed.bodyEndOffset = nextOffset;
            }
        }
    };

    for (let match = headerPattern.exec(text); match !== null && match.index < anchorOffset; match = headerPattern.exec(text)) {
        depth += countBraceDelta(text.slice(cursor, match.index));
        closeCompletedDeclarations(match.index);

        const resolvedType = resolveTypeName(normalizeType(match[1]), context) ?? normalizeType(match[1]);
        const declaration: AnonymousReceiverDeclaration = {
            qualifiedName: resolvedType,
            startOffset: match.index,
            bodyEndOffset: text.length,
            bodyDepth: depth + 1
        };
        declarations.push(declaration);
        stack.push(declaration);
        depth++;
        cursor = headerPattern.lastIndex;
    }

    depth += countBraceDelta(text.slice(cursor, anchorOffset));
    closeCompletedDeclarations(anchorOffset);

    return declarations
        .filter((candidate) => anchorOffset >= candidate.startOffset && anchorOffset <= candidate.bodyEndOffset)
        .sort((left, right) => {
            const leftSpan = left.bodyEndOffset - left.startOffset;
            const rightSpan = right.bodyEndOffset - right.startOffset;
            return leftSpan - rightSpan;
        })
        .map((candidate) => candidate.qualifiedName);
}

function countBraceDelta(fragment: string): number {
    return countStructuralBraceDelta(fragment);
}

function inferExpressionTypes(
    index: ExMethodIndex,
    document: vscode.TextDocument,
    anchorOffset: number,
    expression: string
): string[] {
    const normalizedExpression = unwrapExpression(expression);
    if (normalizedExpression.length === 0) {
        return [];
    }

    const literalTypes = inferLiteralTypes(normalizedExpression);
    if (literalTypes.length > 0) {
        return literalTypes;
    }

    if (normalizedExpression.endsWith('.class')) {
        const classBase = normalizedExpression.slice(0, -'.class'.length).trim();
        const classTypes = inferTypeReceiverTypes(document, anchorOffset, classBase);
        const underlyingType = classTypes.find((candidate) => simpleNameOf(candidate) !== 'Class');
        return underlyingType ? [buildClassType(underlyingType)] : [];
    }

    const newExpressionType = inferNewExpressionType(document, anchorOffset, normalizedExpression);
    if (newExpressionType) {
        return [newExpressionType];
    }

    const arrayAccessType = inferArrayAccessType(index, document, anchorOffset, normalizedExpression);
    if (arrayAccessType.length > 0) {
        return arrayAccessType;
    }

    const callExpressionTypes = inferCallExpressionTypes(index, document, anchorOffset, normalizedExpression);
    if (callExpressionTypes.length > 0) {
        return callExpressionTypes;
    }

    const typeReceiverTypes = inferTypeReceiverTypes(document, anchorOffset, normalizedExpression);
    if (typeReceiverTypes.length > 0) {
        return typeReceiverTypes;
    }

    const memberAccessTypes = inferMemberAccessTypes(index, document, anchorOffset, normalizedExpression);
    if (memberAccessTypes.length > 0) {
        return memberAccessTypes;
    }

    return inferSimpleExpressionTypes(document, anchorOffset, normalizedExpression);
}

function inferLiteralTypes(expression: string): string[] {
    if (/^"(?:\\.|[^"\\])*"$/.test(expression)) {
        return ['java.lang.String'];
    }
    if (/^'(?:\\.|[^'\\])'$/.test(expression)) {
        return ['java.lang.Character'];
    }
    if (/^(true|false)$/.test(expression)) {
        return ['java.lang.Boolean'];
    }
    if (/^\d+[lL]$/.test(expression)) {
        return ['java.lang.Long'];
    }
    if (/^\d+[fF]$/.test(expression)) {
        return ['java.lang.Float'];
    }
    if (/^\d+\.\d+[dD]?$/.test(expression)) {
        return ['java.lang.Double'];
    }
    if (/^\d+$/.test(expression)) {
        return ['java.lang.Integer'];
    }
    return [];
}

function inferNewExpressionType(
    document: vscode.TextDocument,
    anchorOffset: number,
    expression: string
): string | undefined {
    const position = document.positionAt(anchorOffset);
    const context = buildDocumentContext(document, position);
    const newArrayMatch = expression.match(/^new\s+(.+?)\s*\[[^\]]*]\s*(?:\[[^\]]*])*/);
    if (newArrayMatch) {
        const elementType = resolveTypeName(newArrayMatch[1], context) ?? normalizeType(newArrayMatch[1]);
        const dimensions = (expression.match(/\[[^\]]*]/g) ?? []).length;
        return `${elementType}${'[]'.repeat(dimensions)}`;
    }
    const newExpressionMatch = expression.match(/^new\s+([A-Za-z_$][\w$.]*(?:<.*>)?)\s*\(/);
    if (!newExpressionMatch) {
        return undefined;
    }
    return resolveTypeName(newExpressionMatch[1], context) ?? normalizeType(newExpressionMatch[1]);
}

function inferArrayAccessType(
    index: ExMethodIndex,
    document: vscode.TextDocument,
    anchorOffset: number,
    expression: string
): string[] {
    if (!expression.endsWith(']')) {
        return [];
    }
    const openBracket = findMatchingOpen(expression, expression.length - 1, '[', ']');
    if (openBracket < 0) {
        return [];
    }
    const baseExpression = expression.slice(0, openBracket).trim();
    if (baseExpression.length === 0) {
        return [];
    }
    const baseTypes = inferExpressionTypes(index, document, anchorOffset, baseExpression);
    return dedupeTypes(baseTypes
        .filter((candidate) => candidate.endsWith('[]'))
        .map((candidate) => candidate.slice(0, -2)));
}

function inferCallExpressionTypes(
    index: ExMethodIndex,
    document: vscode.TextDocument,
    anchorOffset: number,
    expression: string
): string[] {
    if (!expression.endsWith(')')) {
        return [];
    }
    const openParenOffset = findMatchingOpen(expression, expression.length - 1, '(', ')');
    if (openParenOffset < 0) {
        return [];
    }
    const methodIdentifier = readIdentifierBackward(expression, openParenOffset);
    if (!methodIdentifier) {
        return [];
    }

    const argumentText = expression.slice(openParenOffset + 1, -1);
    const argumentCount = countArgumentSlots(argumentText);
    const operator = findReceiverOperator(expression, methodIdentifier.startOffset);
    let receiverTypes: string[];
    let allowDirectOnly = false;

    if (operator) {
        if (operator.kind === 'reference') {
            return [];
        }
        const receiverExpression = readExpressionBackward(expression, operator.operatorStart);
        if (!receiverExpression) {
            return [];
        }
        receiverTypes = inferExpressionTypes(index, document, anchorOffset, receiverExpression);
        allowDirectOnly = isThisLikeExpression(receiverExpression);
    } else {
        receiverTypes = inferImplicitReceiverTypes(document, anchorOffset);
        allowDirectOnly = true;
    }

    const descriptors = findMatchingDescriptors(index, methodIdentifier.value, receiverTypes, allowDirectOnly, argumentCount);
    if (descriptors.length > 0) {
        return dedupeTypes(descriptors.map((descriptor) => resolveInvocationReturnType(descriptor, receiverTypes)));
    }

    return inferCommonMemberReturnTypes(index, receiverTypes, methodIdentifier.value, argumentCount);
}

function inferCommonMemberReturnTypes(
    index: ExMethodIndex,
    receiverTypes: readonly string[],
    methodName: string,
    argumentCount: number
): string[] {
    if (methodName === 'getClass' && argumentCount === 0) {
        return dedupeTypes(receiverTypes
            .filter((receiverType) => simpleNameOf(receiverType) !== 'Class')
            .map((receiverType) => buildClassType(eraseType(receiverType))));
    }
    if ((methodName === 'stream' || methodName === 'parallelStream')
        && argumentCount === 0
        && receiverTypes.some((receiverType) => index.isTypeAssignable(receiverType, 'java.util.Collection'))) {
        return ['java.util.stream.Stream'];
    }
    if (methodName === 'entrySet'
        && argumentCount === 0
        && receiverTypes.some((receiverType) => index.isTypeAssignable(receiverType, 'java.util.Map'))) {
        return ['java.util.Set'];
    }
    if ((methodName === 'keySet')
        && argumentCount === 0
        && receiverTypes.some((receiverType) => index.isTypeAssignable(receiverType, 'java.util.Map'))) {
        return ['java.util.Set'];
    }
    if ((methodName === 'values')
        && argumentCount === 0
        && receiverTypes.some((receiverType) => index.isTypeAssignable(receiverType, 'java.util.Map'))) {
        return ['java.util.Collection'];
    }
    if ((methodName === 'iterator')
        && argumentCount === 0
        && receiverTypes.some((receiverType) => index.isTypeAssignable(receiverType, 'java.lang.Iterable'))) {
        return ['java.util.Iterator'];
    }
    return [];
}

function inferTypeReceiverTypes(
    document: vscode.TextDocument,
    anchorOffset: number,
    expression: string
): string[] {
    if (!looksLikeTypeExpression(expression)) {
        return [];
    }
    const position = document.positionAt(anchorOffset);
    const context = buildDocumentContext(document, position);
    const resolvedType = resolveTypeName(expression, context);
    if (!resolvedType) {
        return [];
    }
    return dedupeTypes([resolvedType, buildClassType(resolvedType)]);
}

function inferMemberAccessTypes(
    index: ExMethodIndex,
    document: vscode.TextDocument,
    anchorOffset: number,
    expression: string
): string[] {
    const operator = findLastTopLevelMemberOperator(expression);
    if (!operator) {
        return [];
    }
    const receiverExpression = expression.slice(0, operator.operatorStart).trim();
    const memberName = expression.slice(operator.operatorStart + operator.operatorLength).trim();
    if (receiverExpression.length === 0 || memberName.length === 0) {
        return [];
    }

    if (memberName === 'length') {
        const receiverTypes = inferExpressionTypes(index, document, anchorOffset, receiverExpression);
        if (receiverTypes.some((receiverType) => receiverType.endsWith('[]'))) {
            return ['int'];
        }
    }

    return [];
}

function inferSimpleExpressionTypes(
    document: vscode.TextDocument,
    anchorOffset: number,
    expression: string
): string[] {
    const position = document.positionAt(anchorOffset);
    const context = buildDocumentContext(document, position);
    if (expression === 'this') {
        return context.currentClassQualifiedName ? [context.currentClassQualifiedName] : [];
    }
    if (expression === 'super') {
        return dedupeTypes([
            context.currentSuperClassName ?? '',
            context.currentClassQualifiedName ?? ''
        ]);
    }
    if (expression === 'null') {
        return [];
    }

    const textBefore = document.getText(new vscode.Range(new vscode.Position(0, 0), position));
    const declaredType = findDeclaredVariableType(textBefore, expression, context);
    if (declaredType) {
        return [declaredType];
    }

    const directType = findReceiverType(document, position, expression);
    if (directType) {
        return [directType];
    }
    return [];
}

function resolveDescriptorReturnType(descriptor: ExMethodDescriptor): string {
    const minimalContext = {
        packageName: descriptor.packageName,
        imports: new Map<string, string>(),
        primaryClassName: descriptor.declaringClass,
        currentClassName: descriptor.declaringClass,
        currentClassQualifiedName: descriptor.qualifiedDeclaringClass,
        visibleTypes: new Map<string, string>(),
        typeDeclarations: []
    };
    minimalContext.visibleTypes.set(descriptor.declaringClass, descriptor.qualifiedDeclaringClass);
    minimalContext.visibleTypes.set(simpleNameOf(descriptor.qualifiedDeclaringClass), descriptor.qualifiedDeclaringClass);
    for (const typeName of descriptor.targetTypes) {
        minimalContext.visibleTypes.set(simpleNameOf(typeName), typeName);
    }

    const directResolution = resolveTypeName(descriptor.returnType, minimalContext);
    if (directResolution) {
        return directResolution;
    }
    if (/^[A-Z][\w$]*$/.test(descriptor.returnType)) {
        return `${descriptor.qualifiedDeclaringClass}.${descriptor.returnType}`;
    }
    return descriptor.returnType;
}

function resolveInvocationReturnType(descriptor: ExMethodDescriptor, receiverTypes: readonly string[]): string {
    const receiverParameterType = descriptor.parameters[0]?.type;
    if (receiverTypes.length > 0
        && receiverParameterType
        && normalizeType(receiverParameterType) === normalizeType(descriptor.returnType)) {
        return receiverTypes[0];
    }
    return resolveDescriptorReturnType(descriptor);
}

function isThisLikeExpression(expression: string): boolean {
    const normalized = unwrapExpression(expression);
    return normalized === 'this' || normalized === 'super';
}

function unwrapExpression(expression: string): string {
    let normalized = expression.trim().replace(/^(return|yield|throw)\s+/, '').trim();
    let changed = true;
    while (changed) {
        changed = false;
        if (normalized.startsWith('(')) {
            const matchingClose = findMatchingClose(normalized, 0, '(', ')');
            if (matchingClose === normalized.length - 1) {
                normalized = normalized.slice(1, -1).trim();
                changed = true;
                continue;
            }
            const castClose = findMatchingClose(normalized, 0, '(', ')');
            if (castClose > 0 && castClose < normalized.length - 1) {
                const possibleType = normalized.slice(1, castClose);
                if (looksLikeTypeExpression(possibleType)) {
                    normalized = normalized.slice(castClose + 1).trim();
                    changed = true;
                }
            }
        }
    }
    return normalized;
}

function findReceiverOperator(text: string, endExclusive: number): ReceiverOperator | undefined {
    const cursor = skipWhitespaceBackward(text, endExclusive);
    if (cursor < 0) {
        return undefined;
    }
    if (text[cursor] === '.') {
        return {
            kind: 'member',
            operatorStart: cursor > 0 && text[cursor - 1] === '?' ? cursor - 1 : cursor
        };
    }
    if (text[cursor] === ':' && cursor > 0 && text[cursor - 1] === ':') {
        return {
            kind: 'reference',
            operatorStart: cursor - 1
        };
    }
    return undefined;
}

function readExpressionBackward(text: string, endExclusive: number): string | undefined {
    const end = skipWhitespaceBackward(text, endExclusive);
    if (end < 0) {
        return undefined;
    }

    let depthParen = 0;
    let depthBracket = 0;
    let depthBrace = 0;
    let depthAngle = 0;
    let index = end;

    for (; index >= 0; index--) {
        const char = text[index];
        if (char === ')') {
            depthParen++;
            continue;
        }
        if (char === '(') {
            if (depthParen === 0) {
                break;
            }
            depthParen--;
            continue;
        }
        if (char === ']') {
            depthBracket++;
            continue;
        }
        if (char === '[') {
            if (depthBracket === 0) {
                break;
            }
            depthBracket--;
            continue;
        }
        if (char === '}') {
            depthBrace++;
            continue;
        }
        if (char === '{') {
            if (depthBrace === 0) {
                break;
            }
            depthBrace--;
            continue;
        }
        if (char === '>') {
            if (!(index > 0 && text[index - 1] === '-')) {
                depthAngle++;
                continue;
            }
        }
        if (char === '<') {
            if (depthAngle > 0) {
                depthAngle--;
                continue;
            }
        }
        if (depthParen === 0
            && depthBracket === 0
            && depthBrace === 0
            && depthAngle === 0
            && isExpressionBoundary(text, index)) {
            break;
        }
    }

    const expression = text.slice(index + 1, end + 1).trim();
    return expression.length > 0 ? expression : undefined;
}

function isExpressionBoundary(text: string, index: number): boolean {
    const char = text[index];
    if (char === ';' || char === '=' || char === ',' || char === '\n' || char === '\r') {
        return true;
    }
    if (char === '?') {
        return !(index + 1 < text.length && (text[index + 1] === '.' || text[index + 1] === ':'));
    }
    if (char === '-' && index + 1 < text.length && text[index + 1] === '>') {
        return true;
    }
    if (char === ':') {
        return !(index > 0 && (text[index - 1] === ':' || text[index - 1] === '?'))
            && !(index + 1 < text.length && text[index + 1] === ':');
    }
    return false;
}

function readIdentifierBackward(text: string, endExclusive: number): IdentifierMatch | undefined {
    const end = skipWhitespaceBackward(text, endExclusive);
    if (end < 0) {
        return undefined;
    }

    let start = end;
    while (start >= 0 && /[A-Za-z0-9_$]/.test(text[start])) {
        start--;
    }
    start += 1;
    if (start > end) {
        return undefined;
    }

    return {
        value: text.slice(start, end + 1),
        startOffset: start,
        endOffset: end + 1
    };
}

function skipWhitespaceBackward(text: string, endExclusive: number): number {
    let index = endExclusive - 1;
    while (index >= 0 && /\s/.test(text[index])) {
        index--;
    }
    return index;
}

function skipWhitespaceForward(text: string, startOffset: number): number {
    let index = startOffset;
    while (index < text.length && /\s/.test(text[index])) {
        index++;
    }
    return index;
}

function countActiveParameters(text: string, startOffset: number, cursorOffset: number): number {
    let activeParameter = 0;
    let nestedParens = 0;
    let nestedAngles = 0;
    let nestedBrackets = 0;
    let nestedBraces = 0;

    for (let index = startOffset; index < cursorOffset; index++) {
        const char = text[index];
        if (char === '(') {
            nestedParens++;
            continue;
        }
        if (char === ')') {
            nestedParens = Math.max(nestedParens - 1, 0);
            continue;
        }
        if (char === '<') {
            nestedAngles++;
            continue;
        }
        if (char === '>') {
            nestedAngles = Math.max(nestedAngles - 1, 0);
            continue;
        }
        if (char === '[') {
            nestedBrackets++;
            continue;
        }
        if (char === ']') {
            nestedBrackets = Math.max(nestedBrackets - 1, 0);
            continue;
        }
        if (char === '{') {
            nestedBraces++;
            continue;
        }
        if (char === '}') {
            nestedBraces = Math.max(nestedBraces - 1, 0);
            continue;
        }
        if (char === ','
            && nestedParens === 0
            && nestedAngles === 0
            && nestedBrackets === 0
            && nestedBraces === 0) {
            activeParameter++;
        }
    }

    return activeParameter;
}

function countArgumentSlots(argumentText: string): number {
    if (argumentText.trim().length === 0) {
        return 0;
    }
    return countActiveParameters(argumentText, 0, argumentText.length) + 1;
}

function findMatchingOpen(text: string, closeOffset: number, openChar: string, closeChar: string): number {
    let depth = 0;
    for (let index = closeOffset; index >= 0; index--) {
        const char = text[index];
        if (char === closeChar) {
            depth++;
            continue;
        }
        if (char === openChar) {
            depth--;
            if (depth === 0) {
                return index;
            }
        }
    }
    return -1;
}

function findMatchingClose(text: string, openOffset: number, openChar: string, closeChar: string): number {
    let depth = 0;
    for (let index = openOffset; index < text.length; index++) {
        const char = text[index];
        if (char === openChar) {
            depth++;
            continue;
        }
        if (char === closeChar) {
            depth--;
            if (depth === 0) {
                return index;
            }
        }
    }
    return -1;
}

function findLastTopLevelMemberOperator(text: string): { operatorStart: number; operatorLength: number } | undefined {
    let depthParen = 0;
    let depthBracket = 0;
    let depthBrace = 0;
    let depthAngle = 0;
    for (let index = text.length - 1; index >= 0; index--) {
        const char = text[index];
        if (char === ')') {
            depthParen++;
            continue;
        }
        if (char === '(') {
            depthParen = Math.max(depthParen - 1, 0);
            continue;
        }
        if (char === ']') {
            depthBracket++;
            continue;
        }
        if (char === '[') {
            depthBracket = Math.max(depthBracket - 1, 0);
            continue;
        }
        if (char === '}') {
            depthBrace++;
            continue;
        }
        if (char === '{') {
            depthBrace = Math.max(depthBrace - 1, 0);
            continue;
        }
        if (char === '>') {
            if (!(index > 0 && text[index - 1] === '-')) {
                depthAngle++;
                continue;
            }
        }
        if (char === '<') {
            depthAngle = Math.max(depthAngle - 1, 0);
            continue;
        }
        if (char === '.' && depthParen === 0 && depthBracket === 0 && depthBrace === 0 && depthAngle === 0) {
            const optional = index > 0 && text[index - 1] === '?';
            return {
                operatorStart: optional ? index - 1 : index,
                operatorLength: optional ? 2 : 1
            };
        }
    }
    return undefined;
}

function looksLikeTypeExpression(expression: string): boolean {
    return /^[A-Z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*$/.test(expression);
}

function dedupeTypes(types: readonly string[]): string[] {
    const unique = new Set<string>();
    for (const typeName of types) {
        const normalized = normalizeType(typeName);
        if (normalized.length > 0) {
            unique.add(normalized);
        }
    }
    return [...unique];
}
