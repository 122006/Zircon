import AdmZip = require('adm-zip');
import { execFile as execFileCallback } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import { promisify } from 'util';
import * as vscode from 'vscode';
import { ExMethodDescriptor, JavaDocumentContext, JavaParameterInfo } from './exMethodModel';
import { countStructuralBraceDelta } from './javaLexing';
import { collectJavaProjectDependencyJars } from './javaProjectClasspath';

const SEARCH_EXCLUDE = '**/{node_modules,build,out,.git,.gradle}/**';
const DEPENDENCY_SEARCH_EXCLUDE = '**/{node_modules,.git,.gradle,out}/**';
const execFile = promisify(execFileCallback);
const stat = promisify(fs.stat);
const readFile = promisify(fs.readFile);
const SKIPPED_SCAN_PREFIXES = [
    'com.sun.',
    'sun.',
    'jdk.',
    'java.',
    'javax.'
] as const;
const MAX_DEPENDENCY_JARS = 80;
const MAX_WORKSPACE_JAVA_FILES = 5_000;
const MAX_SOURCE_JAR_ENTRIES = 6_000;
const MAX_BINARY_JAR_ENTRIES = 10_000;
const MAX_BINARY_EXTENSION_CLASSES_PER_REBUILD = 40;
const JAVAP_TIMEOUT_MS = 3_000;

export const DEPENDENCY_DOCUMENT_SCHEME = 'zircon-dependency';

interface JavaTypeDeclaration {
    simpleName: string;
    qualifiedName: string;
    parentTypes: string[];
    startOffset: number;
    bodyStartOffset: number;
    bodyEndOffset: number;
}

interface ScannedJavaTypeDeclaration extends JavaTypeDeclaration {
    bodyDepth: number;
}

interface JavaTextSource {
    uri: vscode.Uri;
    fileName: string;
    getText(): string;
    lineAt(line: number): { text: string };
}

interface DependencyIndexStats {
    documentCount: number;
    jarCount: number;
}

interface IndexedDependencyDocument {
    uri: vscode.Uri;
    descriptors: ExMethodDescriptor[];
    typeDeclarations: JavaTypeDeclaration[];
    content?: string;
}

interface DependencyJarCacheEntry {
    stamp: string;
    documents: IndexedDependencyDocument[];
}

interface BinaryIndexBudget {
    remainingClasses: number;
    didLogLimit: boolean;
}

export interface InternalJavaContext {
    packageName: string;
    imports: Map<string, string>;
    primaryClassName?: string;
    currentClassName?: string;
    currentClassQualifiedName?: string;
    currentSuperClassName?: string;
    visibleTypes: Map<string, string>;
    typeDeclarations: JavaTypeDeclaration[];
}

export class ExMethodIndex implements vscode.Disposable {
    private readonly descriptors = new Map<string, ExMethodDescriptor[]>();
    private readonly typeDeclarations = new Map<string, JavaTypeDeclaration[]>();
    private readonly virtualDocumentContents = new Map<string, string>();
    private readonly parentTypes = new Map<string, Set<string>>();
    private readonly simpleToQualifiedTypes = new Map<string, Set<string>>();
    private readonly dependencyJarCache = new Map<string, DependencyJarCacheEntry>();
    private rebuildPromise: Promise<void> | undefined;
    private rebuildRequestedWhileRunning = false;

    constructor(private readonly output: vscode.OutputChannel) {}

    public async initialize(): Promise<void> {
        await this.rebuild();
    }

    public async rebuild(): Promise<void> {
        if (this.rebuildPromise) {
            this.rebuildRequestedWhileRunning = true;
            return this.rebuildPromise;
        }
        this.rebuildPromise = this.runRebuildLoop().finally(() => {
            this.rebuildPromise = undefined;
        });
        return this.rebuildPromise;
    }

    public async updateDocument(document: vscode.TextDocument): Promise<void> {
        if (document.languageId !== 'java') {
            return;
        }
        const text = document.getText();
        this.descriptors.set(document.uri.toString(), parseExMethods(document));
        this.typeDeclarations.set(document.uri.toString(), scanJavaSource(text).typeDeclarations);
        this.rebuildHierarchyCache();
    }

    public remove(uri: vscode.Uri): void {
        this.descriptors.delete(uri.toString());
        this.typeDeclarations.delete(uri.toString());
        this.rebuildHierarchyCache();
    }

    public getAll(): ExMethodDescriptor[] {
        return [...this.descriptors.values()].flat();
    }

    public size(): number {
        return this.getAll().length;
    }

    public getVirtualDocumentContent(uri: vscode.Uri): string | undefined {
        return this.virtualDocumentContents.get(uri.toString());
    }

    public findMatches(
        receiverTypesInput: readonly string[] | string | undefined,
        allowDirectOnly: boolean
    ): ExMethodDescriptor[] {
        const receiverTypes = normalizeReceiverTypeList(receiverTypesInput);
        const descriptors = this.getAll();
        if (receiverTypes.length === 0) {
            return allowDirectOnly
                ? descriptors.filter((descriptor) => descriptor.shouldInvokeDirectly)
                : [];
        }

        return descriptors.filter((descriptor) => {
            if (descriptor.shouldInvokeDirectly && !allowDirectOnly) {
                return false;
            }
            return descriptor.targetTypes.some((targetType) => {
                return receiverTypes.some((receiverType) => this.isTypeAssignable(receiverType, targetType));
            });
        });
    }

    public isTypeAssignable(receiverType: string, targetType: string): boolean {
        const receiverBase = eraseType(receiverType);
        const targetBase = eraseType(targetType);
        if (targetBase === 'java.lang.Object' || targetBase === 'Object') {
            return true;
        }
        if (this.matchesClassLikeType(receiverType, targetType)) {
            return true;
        }

        const targetCandidates = this.getCanonicalTypeCandidates(targetBase);
        for (const receiverCandidate of this.getCanonicalTypeCandidates(receiverBase)) {
            if (targetCandidates.has(receiverCandidate) || targetCandidates.has(simpleNameOf(receiverCandidate))) {
                return true;
            }
            if (this.hasParentPath(receiverCandidate, targetCandidates)) {
                return true;
            }
        }

        return simpleNameOf(receiverBase) === simpleNameOf(targetBase);
    }

    public findByName(methodName: string): ExMethodDescriptor[] {
        return this.getAll().filter((descriptor) => descriptor.methodName === methodName);
    }

    public findDeclarations(document: vscode.TextDocument, position: vscode.Position): ExMethodDescriptor[] {
        const methodName = readWordAt(document, position);
        if (!methodName) {
            return [];
        }
        return (this.descriptors.get(document.uri.toString()) ?? []).filter((descriptor) => {
            return descriptor.methodName === methodName
                && descriptor.nameRange.start.line <= position.line
                && descriptor.nameRange.end.line >= position.line;
        });
    }

    public dispose(): void {
        this.descriptors.clear();
        this.typeDeclarations.clear();
        this.virtualDocumentContents.clear();
        this.parentTypes.clear();
        this.simpleToQualifiedTypes.clear();
        this.dependencyJarCache.clear();
    }

    private async runRebuildLoop(): Promise<void> {
        do {
            this.rebuildRequestedWhileRunning = false;
            await this.doRebuild();
        } while (this.rebuildRequestedWhileRunning);
    }

    private async doRebuild(): Promise<void> {
        const javaFiles = await vscode.workspace.findFiles('**/*.java', SEARCH_EXCLUDE, MAX_WORKSPACE_JAVA_FILES);
        if (javaFiles.length === MAX_WORKSPACE_JAVA_FILES) {
            this.output.appendLine(`[Zircon] Workspace Java source indexing reached the ${MAX_WORKSPACE_JAVA_FILES}-file safety limit.`);
        }
        const nextDescriptors = new Map<string, ExMethodDescriptor[]>();
        const nextTypes = new Map<string, JavaTypeDeclaration[]>();
        const nextVirtualDocumentContents = new Map<string, string>();
        let processedJavaFiles = 0;
        for (const uri of javaFiles) {
            try {
                const text = Buffer.from(await vscode.workspace.fs.readFile(uri)).toString('utf8');
                const source = scanJavaSource(text);
                const javaSource = createVirtualJavaSource(uri, uri.fsPath, text);
                nextDescriptors.set(uri.toString(), parseExMethods(javaSource));
                nextTypes.set(uri.toString(), source.typeDeclarations);
            } catch (error) {
                this.output.appendLine(`[Zircon] Failed to index ${uri.fsPath}: ${String(error)}`);
            }
            processedJavaFiles++;
            if (processedJavaFiles % 8 === 0) {
                await yieldToEventLoop();
            }
        }
        const dependencyStats = await collectDependencyIndexEntries(
            nextDescriptors,
            nextTypes,
            nextVirtualDocumentContents,
            this.output,
            this.dependencyJarCache
        );

        // Disk reads above can race with an unsaved editor buffer. Apply the current
        // in-memory version last so completion/navigation never regress to disk text.
        for (const document of vscode.workspace.textDocuments) {
            if (document.languageId !== 'java' || document.uri.scheme !== 'file') {
                continue;
            }
            const text = document.getText();
            nextDescriptors.set(document.uri.toString(), parseExMethods(document));
            nextTypes.set(document.uri.toString(), scanJavaSource(text).typeDeclarations);
        }

        this.descriptors.clear();
        this.typeDeclarations.clear();
        this.virtualDocumentContents.clear();
        for (const [key, value] of nextDescriptors) {
            this.descriptors.set(key, value);
        }
        for (const [key, value] of nextTypes) {
            this.typeDeclarations.set(key, value);
        }
        for (const [key, value] of nextVirtualDocumentContents) {
            this.virtualDocumentContents.set(key, value);
        }
        this.rebuildHierarchyCache();
        this.output.appendLine(
            `[Zircon] Indexed ${this.size()} extension methods from ${javaFiles.length} Java files and `
            + `${dependencyStats.documentCount} dependency documents across ${dependencyStats.jarCount} jars.`
        );
    }

    private rebuildHierarchyCache(): void {
        this.parentTypes.clear();
        this.simpleToQualifiedTypes.clear();

        for (const [child, parents] of Object.entries(BUILTIN_PARENT_RELATIONS)) {
            this.registerTypeName(child);
            for (const parent of parents) {
                this.registerParentRelation(child, parent);
            }
        }

        for (const declarations of this.typeDeclarations.values()) {
            for (const declaration of declarations) {
                this.registerTypeName(declaration.qualifiedName);
                for (const parentType of declaration.parentTypes) {
                    this.registerParentRelation(declaration.qualifiedName, parentType);
                }
            }
        }
    }

    private registerTypeName(typeName: string): void {
        const normalized = eraseType(typeName);
        if (normalized.length === 0) {
            return;
        }
        this.ensureParentEntry(normalized);
        const simpleName = simpleNameOf(normalized);
        this.ensureParentEntry(simpleName);
        let candidates = this.simpleToQualifiedTypes.get(simpleName);
        if (!candidates) {
            candidates = new Set<string>();
            this.simpleToQualifiedTypes.set(simpleName, candidates);
        }
        candidates.add(normalized);
    }

    private registerParentRelation(childType: string, parentType: string): void {
        const child = eraseType(childType);
        const parent = eraseType(parentType);
        if (child.length === 0 || parent.length === 0 || child === parent) {
            return;
        }

        this.registerTypeName(child);
        this.registerTypeName(parent);
        this.addParent(child, parent);
        this.addParent(simpleNameOf(child), parent);
        this.addParent(child, simpleNameOf(parent));
        this.addParent(simpleNameOf(child), simpleNameOf(parent));
    }

    private addParent(child: string, parent: string): void {
        const normalizedChild = eraseType(child);
        const normalizedParent = eraseType(parent);
        if (normalizedChild.length === 0 || normalizedParent.length === 0) {
            return;
        }
        const parents = this.ensureParentEntry(normalizedChild);
        parents.add(normalizedParent);
    }

    private ensureParentEntry(typeName: string): Set<string> {
        const normalized = eraseType(typeName);
        let parents = this.parentTypes.get(normalized);
        if (!parents) {
            parents = new Set<string>();
            this.parentTypes.set(normalized, parents);
        }
        return parents;
    }

    private getCanonicalTypeCandidates(typeName: string): Set<string> {
        const normalized = eraseType(typeName);
        const candidates = new Set<string>();
        if (normalized.length === 0) {
            return candidates;
        }

        candidates.add(normalized);
        const simpleName = simpleNameOf(normalized);
        candidates.add(simpleName);
        if (JAVA_LANG_TYPES.has(simpleName)) {
            candidates.add(`java.lang.${simpleName}`);
        }
        for (const qualifiedName of this.simpleToQualifiedTypes.get(simpleName) ?? []) {
            candidates.add(qualifiedName);
        }
        return candidates;
    }

    private hasParentPath(typeName: string, targetCandidates: Set<string>): boolean {
        const visited = new Set<string>();
        const queue = [eraseType(typeName)];

        while (queue.length > 0) {
            const current = queue.shift();
            if (!current || visited.has(current)) {
                continue;
            }
            visited.add(current);
            if (targetCandidates.has(current) || targetCandidates.has(simpleNameOf(current))) {
                return true;
            }
            const parents = this.parentTypes.get(current);
            if (!parents) {
                continue;
            }
            for (const parent of parents) {
                if (!visited.has(parent)) {
                    queue.push(parent);
                }
            }
        }
        return false;
    }

    private matchesClassLikeType(receiverType: string, targetType: string): boolean {
        const receiverClass = parseClassLikeType(receiverType);
        const targetClass = parseClassLikeType(targetType);
        if (!receiverClass || !targetClass) {
            return false;
        }
        const receiverRaw = eraseType(receiverClass.rawType);
        const targetRaw = eraseType(targetClass.rawType);
        if (receiverRaw !== targetRaw && simpleNameOf(receiverRaw) !== simpleNameOf(targetRaw)) {
            return false;
        }
        if (!targetClass.argument) {
            return true;
        }
        if (!receiverClass.argument) {
            return false;
        }
        return this.matchesTypeArgument(receiverClass.argument, targetClass.argument);
    }

    private matchesTypeArgument(receiverArgument: string, targetArgument: string): boolean {
        const receiver = normalizeType(receiverArgument);
        const target = normalizeType(targetArgument);
        if (target === '?' || receiver === target) {
            return true;
        }
        if (target.startsWith('? extends ')) {
            return this.isTypeAssignable(receiver, target.slice('? extends '.length));
        }
        if (target.startsWith('? super ')) {
            return this.isTypeAssignable(target.slice('? super '.length), receiver);
        }
        if (receiver.startsWith('? extends ')) {
            return this.isTypeAssignable(receiver.slice('? extends '.length), target);
        }
        return this.isTypeAssignable(receiver, target);
    }
}

export function parseJavaContext(document: vscode.TextDocument): JavaDocumentContext {
    const context = buildDocumentContext(document, new vscode.Position(0, 0));
    return {
        packageName: context.packageName,
        imports: context.imports,
        currentClassName: context.currentClassName
    };
}

export function buildDocumentContext(document: vscode.TextDocument, position: vscode.Position): InternalJavaContext {
    return buildContextFromText(document.getText(), document.offsetAt(position));
}

export function buildContextFromText(text: string, offset = 0): InternalJavaContext {
    const source = scanJavaSource(text);
    const currentType = source.typeDeclarations
        .filter((candidate) => offset >= candidate.startOffset && offset <= candidate.bodyEndOffset)
        .sort((left, right) => {
            const leftSpan = left.bodyEndOffset - left.startOffset;
            const rightSpan = right.bodyEndOffset - right.startOffset;
            return leftSpan - rightSpan;
        })[0];

    return {
        packageName: source.packageName,
        imports: source.imports,
        primaryClassName: source.primaryClassName,
        currentClassName: currentType?.simpleName ?? source.primaryClassName,
        currentClassQualifiedName: currentType?.qualifiedName,
        currentSuperClassName: currentType?.parentTypes[0],
        visibleTypes: source.visibleTypes,
        typeDeclarations: source.typeDeclarations
    };
}

export function findReceiverType(document: vscode.TextDocument, position: vscode.Position, token: string): string | undefined {
    const context = buildDocumentContext(document, position);
    if (token === 'this') {
        return context.currentClassQualifiedName ?? resolveTypeName(context.currentClassName ?? '', context);
    }
    if (token === 'super') {
        return context.currentSuperClassName ?? context.currentClassQualifiedName;
    }

    const textBefore = document.getText(new vscode.Range(new vscode.Position(0, 0), position));
    const localType = findDeclaredVariableType(textBefore, token, context);
    if (localType) {
        return localType;
    }

    if (looksLikeQualifiedTypeExpression(token)) {
        return resolveTypeName(token, context);
    }

    return resolveTypeName(token, context);
}

function parseExMethods(document: JavaTextSource): ExMethodDescriptor[] {
    const text = document.getText();
    const lines = text.split(/\r?\n/);
    const lineOffsets = collectLineOffsets(text, lines);
    const context = buildContextFromText(text);
    const descriptors: ExMethodDescriptor[] = [];
    let annotationBuffer: string[] = [];
    let activeAnnotation: string | undefined;
    let activeAnnotationParenDepth = 0;
    let collectingSignature = false;
    let signatureBuffer = '';
    let methodStartLine = -1;
    let methodStartOffset = -1;

    for (let index = 0; index < lines.length; index++) {
        const line = lines[index];
        const trimmed = line.trim();

        if (!collectingSignature && activeAnnotation !== undefined) {
            if (trimmed.length > 0) {
                activeAnnotation += ` ${trimmed}`;
                activeAnnotationParenDepth += countAnnotationParenthesisDelta(trimmed);
            }
            if (activeAnnotationParenDepth <= 0) {
                annotationBuffer.push(activeAnnotation);
                activeAnnotation = undefined;
                activeAnnotationParenDepth = 0;
            }
            continue;
        }

        if (!collectingSignature && (isZirconAnnotation(trimmed) || (annotationBuffer.length > 0 && trimmed.startsWith('@')))) {
            activeAnnotation = trimmed;
            activeAnnotationParenDepth = countAnnotationParenthesisDelta(trimmed);
            if (activeAnnotationParenDepth <= 0) {
                annotationBuffer.push(activeAnnotation);
                activeAnnotation = undefined;
                activeAnnotationParenDepth = 0;
            }
            continue;
        }

        if (!collectingSignature && annotationBuffer.length > 0) {
            if (trimmed.length === 0) {
                continue;
            }
            collectingSignature = true;
            methodStartLine = index;
            methodStartOffset = lineOffsets[index] + Math.max(line.indexOf(trimmed), 0);
        }

        if (collectingSignature) {
            signatureBuffer += ` ${trimmed}`;
            if (trimmed.includes('{') || trimmed.endsWith(';')) {
                const descriptor = buildDescriptorFromSignature(
                    document,
                    context,
                    annotationBuffer,
                    signatureBuffer,
                    methodStartLine,
                    methodStartOffset
                );
                if (descriptor) {
                    descriptors.push(descriptor);
                }
                annotationBuffer = [];
                signatureBuffer = '';
                collectingSignature = false;
                methodStartLine = -1;
                methodStartOffset = -1;
            }
        }
    }

    return descriptors;
}

function collectLineOffsets(text: string, lines: readonly string[]): number[] {
    const offsets: number[] = [];
    let offset = 0;
    for (const line of lines) {
        offsets.push(offset);
        offset += line.length;
        if (offset < text.length && text[offset] === '\r') {
            offset++;
        }
        if (offset < text.length && text[offset] === '\n') {
            offset++;
        }
    }
    return offsets;
}

function isZirconAnnotation(text: string): boolean {
    return /^@ExMethod(?:IDE)?(?:\s|\(|$)/.test(text);
}

function countAnnotationParenthesisDelta(text: string): number {
    let depth = 0;
    let quote: string | undefined;
    let escaping = false;
    for (const char of text) {
        if (quote) {
            if (escaping) {
                escaping = false;
            } else if (char === '\\') {
                escaping = true;
            } else if (char === quote) {
                quote = undefined;
            }
            continue;
        }
        if (char === '"' || char === '\'') {
            quote = char;
        } else if (char === '(') {
            depth++;
        } else if (char === ')') {
            depth--;
        }
    }
    return depth;
}

function buildDescriptorFromSignature(
    document: JavaTextSource,
    context: InternalJavaContext,
    annotations: string[],
    rawSignature: string,
    line: number,
    signatureStartOffset: number
): ExMethodDescriptor | undefined {
    const normalized = rawSignature.replace(/\s+/g, ' ').replace(/\s*\{.*$/, '').trim();
    const signatureMatch = normalized.match(/^(?:public|protected|private|static|final|synchronized|abstract|native|default|strictfp|\s)+(?:(<.*?>)\s+)?(.+?)\s+([A-Za-z_$][\w$]*)\s*\((.*)\)$/);
    if (!signatureMatch) {
        return undefined;
    }

    const returnType = normalizeType(signatureMatch[2]);
    const methodName = signatureMatch[3];
    const methodTypeBounds = parseMethodTypeParameterBounds(signatureMatch[1], context);
    const parameterBlock = signatureMatch[4];
    const parameters = splitTopLevel(parameterBlock, ',')
        .map((part) => parseParameter(part))
        .filter((value): value is JavaParameterInfo => value !== undefined);

    const exAnnotation = annotations.find((item) => /^@ExMethod(?:\s|\(|$)/.test(item));
    const ideAnnotation = annotations.find((item) => /^@ExMethodIDE(?:\s|\(|$)/.test(item));
    const targetTypes = parseTargetTypes(exAnnotation, parameters, context, methodTypeBounds);
    if (targetTypes.length === 0) {
        return undefined;
    }

    const declaringType = context.typeDeclarations
        .filter((candidate) => signatureStartOffset >= candidate.startOffset && signatureStartOffset <= candidate.bodyEndOffset)
        .sort((left, right) => (left.bodyEndOffset - left.startOffset) - (right.bodyEndOffset - right.startOffset))[0];
    const declaringClass = declaringType?.simpleName ?? context.primaryClassName ?? path.basename(document.fileName, '.java');
    const qualifiedDeclaringClass = declaringType?.qualifiedName
        ?? (context.packageName.length > 0 ? `${context.packageName}.${declaringClass}` : declaringClass);
    const methodLineText = document.lineAt(Math.max(line, 0)).text;
    const methodColumn = Math.max(methodLineText.indexOf(`${methodName}(`), 0);
    const nameRange = new vscode.Range(
        new vscode.Position(Math.max(line, 0), methodColumn),
        new vscode.Position(Math.max(line, 0), methodColumn + methodName.length)
    );

    return {
        uri: document.uri,
        packageName: context.packageName,
        declaringClass,
        qualifiedDeclaringClass,
        methodName,
        returnType,
        parameters,
        targetTypes,
        filterAnnotations: parseClassArray(exAnnotation, 'filterAnnotation', context),
        isStaticExtension: hasExplicitEx(exAnnotation),
        cover: /\bcover\s*=\s*true\b/.test(exAnnotation ?? ''),
        shouldInvokeDirectly: /\bshouldInvokeDirectly\s*=\s*true\b/.test(ideAnnotation ?? ''),
        line,
        nameRange,
        signature: normalized
    };
}

function scanJavaSource(text: string): {
    packageName: string;
    imports: Map<string, string>;
    primaryClassName?: string;
    visibleTypes: Map<string, string>;
    typeDeclarations: JavaTypeDeclaration[];
} {
    const imports = new Map<string, string>();
    const packageMatch = text.match(/^\s*package\s+([\w.]+)\s*;/m);
    const packageName = packageMatch ? packageMatch[1] : '';

    const importPattern = /^\s*import\s+([\w.*]+)\s*;/gm;
    let wildcardImportIndex = 0;
    for (let match = importPattern.exec(text); match !== null; match = importPattern.exec(text)) {
        const qualifiedName = match[1];
        if (qualifiedName.endsWith('.*')) {
            imports.set(`*${wildcardImportIndex++}`, qualifiedName);
        } else {
            imports.set(simpleNameOf(qualifiedName), qualifiedName);
        }
    }

    const rawDeclarations = scanTypeDeclarations(text, packageName);
    const visibleTypes = new Map<string, string>();
    for (const declaration of rawDeclarations) {
        visibleTypes.set(declaration.simpleName, declaration.qualifiedName);
        const nestedName = declaration.qualifiedName.slice(packageName.length > 0 ? packageName.length + 1 : 0);
        visibleTypes.set(nestedName, declaration.qualifiedName);
    }

    const baseContext: InternalJavaContext = {
        packageName,
        imports,
        primaryClassName: rawDeclarations[0]?.simpleName,
        visibleTypes,
        typeDeclarations: rawDeclarations
    };
    const typeDeclarations = rawDeclarations.map((declaration) => ({
        ...declaration,
        parentTypes: declaration.parentTypes
            .map((parentType) => resolveTypeName(parentType, baseContext) ?? normalizeType(parentType))
    }));

    return {
        packageName,
        imports,
        primaryClassName: rawDeclarations[0]?.simpleName,
        visibleTypes,
        typeDeclarations
    };
}

function scanTypeDeclarations(text: string, packageName: string): JavaTypeDeclaration[] {
    const declarations: ScannedJavaTypeDeclaration[] = [];
    const headerPattern = /\b(class|interface|enum)\s+([A-Za-z_$][\w$]*)\b([^{};]*)\{/g;
    const stack: ScannedJavaTypeDeclaration[] = [];
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

    for (let match = headerPattern.exec(text); match !== null; match = headerPattern.exec(text)) {
        depth += countBraceDelta(text.slice(cursor, match.index));
        closeCompletedDeclarations(match.index);

        const simpleName = match[2];
        const parentTypes = parseParentTypeNames(match[3]);
        const enclosingType = stack[stack.length - 1];
        const qualifiedName = enclosingType
            ? `${enclosingType.qualifiedName}.${simpleName}`
            : packageName.length > 0
                ? `${packageName}.${simpleName}`
                : simpleName;
        const declaration: ScannedJavaTypeDeclaration = {
            simpleName,
            qualifiedName,
            parentTypes,
            startOffset: match.index,
            bodyStartOffset: headerPattern.lastIndex - 1,
            bodyEndOffset: text.length,
            bodyDepth: depth + 1
        };
        declarations.push(declaration);
        stack.push(declaration);
        depth++;
        cursor = headerPattern.lastIndex;
    }

    depth += countBraceDelta(text.slice(cursor));
    closeCompletedDeclarations(text.length);
    return declarations.map((declaration) => ({
        simpleName: declaration.simpleName,
        qualifiedName: declaration.qualifiedName,
        parentTypes: declaration.parentTypes,
        startOffset: declaration.startOffset,
        bodyStartOffset: declaration.bodyStartOffset,
        bodyEndOffset: declaration.bodyEndOffset
    }));
}

function parseParentTypeNames(signatureTail: string): string[] {
    const parents: string[] = [];
    const extendsMatch = signatureTail.match(/\bextends\s+(.+?)(?=\bimplements\b|$)/);
    if (extendsMatch) {
        parents.push(...splitTopLevel(extendsMatch[1].trim(), ','));
    }
    const implementsMatch = signatureTail.match(/\bimplements\s+(.+)$/);
    if (implementsMatch) {
        parents.push(...splitTopLevel(implementsMatch[1].trim(), ','));
    }
    return parents
        .map((parentType) => normalizeType(parentType))
        .filter((parentType) => parentType.length > 0);
}

function countBraceDelta(fragment: string): number {
    return countStructuralBraceDelta(fragment);
}

function parseTargetTypes(
    exAnnotation: string | undefined,
    parameters: JavaParameterInfo[],
    context: InternalJavaContext,
    methodTypeBounds: Map<string, string>
): string[] {
    const explicitTargets = parseClassArray(exAnnotation, 'ex', context, methodTypeBounds);
    if (explicitTargets.length > 0) {
        return explicitTargets;
    }
    if (parameters.length === 0) {
        return [];
    }
    return [resolveMethodTargetType(parameters[0].type, context, methodTypeBounds)];
}

function parseClassArray(
    annotationText: string | undefined,
    attributeName: string,
    context: InternalJavaContext,
    methodTypeBounds: Map<string, string> = new Map<string, string>()
): string[] {
    if (!annotationText) {
        return [];
    }
    const attributeMatch = annotationText.match(new RegExp(`${attributeName}\\s*=\\s*(\\{[^}]*\\}|[^,)]*)`));
    if (!attributeMatch) {
        return [];
    }

    let raw = attributeMatch[1].trim();
    if (raw.startsWith('{') && raw.endsWith('}')) {
        raw = raw.slice(1, -1);
    }

    return raw.split(',')
        .map((part) => part.trim())
        .filter((part) => part.length > 0)
        .map((part) => part.replace(/\.class$/, '').trim())
        .map((part) => resolveMethodTargetType(part, context, methodTypeBounds));
}

function parseMethodTypeParameterBounds(
    rawTypeParameters: string | undefined,
    context: InternalJavaContext
): Map<string, string> {
    const bounds = new Map<string, string>();
    if (!rawTypeParameters) {
        return bounds;
    }
    const body = rawTypeParameters.trim().replace(/^</, '').replace(/>$/, '');
    if (body.length === 0) {
        return bounds;
    }
    for (const item of splitTopLevel(body, ',')) {
        const normalizedItem = normalizeType(item);
        const match = normalizedItem.match(/^([A-Za-z_$][\w$]*)(?:\s+extends\s+(.+))?$/);
        if (!match) {
            continue;
        }
        const typeName = match[1];
        const rawBound = match[2]?.split('&')[0].trim();
        const resolvedBound = rawBound
            ? (resolveTypeName(rawBound, context) ?? normalizeType(rawBound))
            : 'java.lang.Object';
        bounds.set(typeName, resolvedBound);
    }
    return bounds;
}

function resolveMethodTargetType(
    rawType: string,
    context: InternalJavaContext,
    methodTypeBounds: Map<string, string>
): string {
    const normalizedType = normalizeType(rawType);
    if (methodTypeBounds.has(normalizedType)) {
        return methodTypeBounds.get(normalizedType) ?? normalizedType;
    }
    return normalizeType(resolveTypeName(normalizedType, context) ?? normalizedType);
}

async function collectDependencyIndexEntries(
    descriptorMap: Map<string, ExMethodDescriptor[]>,
    typeMap: Map<string, JavaTypeDeclaration[]>,
    virtualDocumentContents: Map<string, string>,
    output: vscode.OutputChannel,
    dependencyJarCache: Map<string, DependencyJarCacheEntry>
): Promise<DependencyIndexStats> {
    const jarPaths = await findDependencyJarCandidates(output);
    if (jarPaths.length === 0) {
        return { documentCount: 0, jarCount: 0 };
    }

    const sourceArtifactKeys = new Set<string>();
    const sourceJars: string[] = [];
    const binaryJars: string[] = [];
    for (const jarPath of jarPaths) {
        if (jarPath.toLowerCase().endsWith('-sources.jar')) {
            sourceArtifactKeys.add(toJarArtifactKey(jarPath));
        }
    }
    for (const jarPath of jarPaths) {
        if (jarPath.toLowerCase().endsWith('-sources.jar')) {
            sourceJars.push(jarPath);
            sourceArtifactKeys.add(toJarArtifactKey(jarPath));
            continue;
        }
        if (!sourceArtifactKeys.has(toJarArtifactKey(jarPath))) {
            binaryJars.push(jarPath);
        }
    }

    let documentCount = 0;
    const activeCacheKeys = new Set<string>();
    const binaryIndexBudget: BinaryIndexBudget = {
        remainingClasses: MAX_BINARY_EXTENSION_CLASSES_PER_REBUILD,
        didLogLimit: false
    };
    let processedJars = 0;
    for (const jarPath of sourceJars) {
        documentCount += await applyCachedDependencyJar(
            jarPath,
            'source',
            descriptorMap,
            typeMap,
            virtualDocumentContents,
            output,
            dependencyJarCache,
            activeCacheKeys
        );
        processedJars++;
        if (processedJars % 2 === 0) {
            await yieldToEventLoop();
        }
    }
    for (const jarPath of binaryJars) {
        if (binaryIndexBudget.remainingClasses === 0) {
            if (!binaryIndexBudget.didLogLimit) {
                output.appendLine(`[Zircon] Binary dependency indexing stopped after ${MAX_BINARY_EXTENSION_CLASSES_PER_REBUILD} extension classes.`);
                binaryIndexBudget.didLogLimit = true;
            }
            break;
        }
        documentCount += await applyCachedDependencyJar(
            jarPath,
            'binary',
            descriptorMap,
            typeMap,
            virtualDocumentContents,
            output,
            dependencyJarCache,
            activeCacheKeys,
            binaryIndexBudget
        );
        processedJars++;
        if (processedJars % 2 === 0) {
            await yieldToEventLoop();
        }
    }
    for (const cacheKey of [...dependencyJarCache.keys()]) {
        if (!activeCacheKeys.has(cacheKey)) {
            dependencyJarCache.delete(cacheKey);
        }
    }
    return { documentCount, jarCount: sourceJars.length + binaryJars.length };
}

async function findDependencyJarCandidates(output: vscode.OutputChannel): Promise<string[]> {
    const jarPaths = new Set<string>();
    let redhatJarCount = 0;
    for (const jarPath of await collectJavaProjectDependencyJars(output)) {
        if (shouldSkipDependencyJar(jarPath)) {
            continue;
        }
        jarPaths.add(jarPath);
        redhatJarCount++;
    }

    for (const jarPath of await findClasspathFileJarCandidates()) {
        if (!shouldSkipDependencyJar(jarPath)) {
            jarPaths.add(jarPath);
        }
    }

    const searchPatterns = [
        '**/build/libs/*.jar',
        '**/lib/**/*.jar',
        '**/libs/**/*.jar'
    ];
    const matches = await Promise.all(searchPatterns.map((pattern) => {
        return vscode.workspace.findFiles(pattern, DEPENDENCY_SEARCH_EXCLUDE, MAX_DEPENDENCY_JARS);
    }));
    for (const uri of matches.flat()) {
        if (!shouldSkipDependencyJar(uri.fsPath)) {
            jarPaths.add(uri.fsPath);
        }
    }

    const enrichedJarPaths = new Set<string>();
    for (const jarPath of jarPaths) {
        enrichedJarPaths.add(jarPath);
        const companionSourceJar = await findCompanionSourceJar(jarPath);
        if (companionSourceJar && !shouldSkipDependencyJar(companionSourceJar)) {
            enrichedJarPaths.add(companionSourceJar);
        }
    }

    const sortedJarPaths = [...enrichedJarPaths]
        .sort(compareDependencyJarPriority);
    const limitedJarPaths = sortedJarPaths.slice(0, MAX_DEPENDENCY_JARS);
    if (sortedJarPaths.length > limitedJarPaths.length) {
        output.appendLine(`[Zircon] Dependency jar candidates capped at ${MAX_DEPENDENCY_JARS}; skipped ${sortedJarPaths.length - limitedJarPaths.length} lower-priority jars.`);
    }
    output.appendLine(`[Zircon] Dependency jar candidates: total=${limitedJarPaths.length}, redhat.java=${redhatJarCount}.`);
    return limitedJarPaths;
}

function compareDependencyJarPriority(left: string, right: string): number {
    const priority = (jarPath: string): number => {
        const normalized = jarPath.replace(/\\/g, '/').toLowerCase();
        if (normalized.includes('zircon')) {
            return 0;
        }
        if (normalized.endsWith('-sources.jar')) {
            return 1;
        }
        return 2;
    };
    const priorityDifference = priority(left) - priority(right);
    return priorityDifference !== 0 ? priorityDifference : left.localeCompare(right);
}

function shouldSkipDependencyJar(jarPath: string): boolean {
    const normalized = jarPath.replace(/\//g, '\\');
    if (!normalized.endsWith('.jar') || normalized.endsWith('-javadoc.jar')) {
        return true;
    }
    return normalized.includes('\\vscode_plugin\\server\\')
        || normalized.endsWith('\\tools.jar')
        || normalized.endsWith('\\gradle-wrapper.jar')
        || normalized.includes('\\.vscode-devhost-data\\');
}

export function toJarArtifactKey(jarPath: string): string {
    const artifactName = path.basename(jarPath)
        .replace(/-sources\.jar$/i, '')
        .replace(/\.jar$/i, '')
        .toLowerCase();
    const parentDirectory = path.dirname(jarPath);
    const coordinateDirectory = /^[a-f0-9]{20,}$/i.test(path.basename(parentDirectory))
        ? path.dirname(parentDirectory)
        : parentDirectory;
    return `${coordinateDirectory.replace(/\\/g, '/').toLowerCase()}|${artifactName}`;
}

async function applyCachedDependencyJar(
    jarPath: string,
    jarKind: 'source' | 'binary',
    descriptorMap: Map<string, ExMethodDescriptor[]>,
    typeMap: Map<string, JavaTypeDeclaration[]>,
    virtualDocumentContents: Map<string, string>,
    output: vscode.OutputChannel,
    dependencyJarCache: Map<string, DependencyJarCacheEntry>,
    activeCacheKeys: Set<string>,
    binaryIndexBudget?: BinaryIndexBudget
): Promise<number> {
    const cacheKey = `${jarKind}:${jarPath}`;
    activeCacheKeys.add(cacheKey);
    const stamp = await buildDependencyJarStamp(jarPath);
    if (stamp) {
        const cached = dependencyJarCache.get(cacheKey);
        if (cached?.stamp === stamp) {
            applyIndexedDependencyDocuments(cached.documents, descriptorMap, typeMap, virtualDocumentContents);
            return cached.documents.length;
        }
    }

    const documents = jarKind === 'source'
        ? await indexSourceJar(jarPath, output)
        : await indexBinaryJar(jarPath, output, binaryIndexBudget);
    applyIndexedDependencyDocuments(documents, descriptorMap, typeMap, virtualDocumentContents);

    if (stamp) {
        dependencyJarCache.set(cacheKey, { stamp, documents });
    }
    return documents.length;
}

function applyIndexedDependencyDocuments(
    documents: readonly IndexedDependencyDocument[],
    descriptorMap: Map<string, ExMethodDescriptor[]>,
    typeMap: Map<string, JavaTypeDeclaration[]>,
    virtualDocumentContents: Map<string, string>
): void {
    for (const document of documents) {
        descriptorMap.set(document.uri.toString(), document.descriptors);
        typeMap.set(document.uri.toString(), document.typeDeclarations);
        if (document.content !== undefined) {
            virtualDocumentContents.set(document.uri.toString(), document.content);
        }
    }
}

async function buildDependencyJarStamp(jarPath: string): Promise<string | undefined> {
    try {
        const jarStat = await stat(jarPath);
        return `${jarStat.size}:${jarStat.mtimeMs}`;
    } catch {
        return undefined;
    }
}

async function indexSourceJar(
    jarPath: string,
    output: vscode.OutputChannel
): Promise<IndexedDependencyDocument[]> {
    try {
        const zip = new AdmZip(await readFile(jarPath));
        const documents: IndexedDependencyDocument[] = [];
        let inspectedEntries = 0;
        for (const entry of zip.getEntries()) {
            inspectedEntries++;
            if (inspectedEntries > MAX_SOURCE_JAR_ENTRIES) {
                output.appendLine(`[Zircon] Source dependency ${jarPath} exceeded ${MAX_SOURCE_JAR_ENTRIES} entries; remaining entries skipped.`);
                break;
            }
            if (inspectedEntries % 256 === 0) {
                await yieldToEventLoop();
            }
            if (entry.isDirectory || !entry.entryName.endsWith('.java')) {
                continue;
            }
            const className = entry.entryName.replace(/\.java$/, '').replace(/\//g, '.');
            if (shouldSkipDependencyClassName(className)) {
                continue;
            }
            const text = entry.getData().toString('utf8');
            if (!text.includes('@ExMethod') && !text.includes('@ExMethodIDE')) {
                continue;
            }
            const uri = createDependencyUri(jarPath, entry.entryName);
            const source = createVirtualJavaSource(uri, path.basename(entry.entryName), text);
            documents.push({
                uri,
                descriptors: parseExMethods(source),
                typeDeclarations: scanJavaSource(text).typeDeclarations,
                content: text
            });
            if (documents.length % 24 === 0) {
                await yieldToEventLoop();
            }
        }
        return documents;
    } catch (error) {
        output.appendLine(`[Zircon] Failed to index dependency sources from ${jarPath}: ${String(error)}`);
        return [];
    }
}

async function indexBinaryJar(
    jarPath: string,
    output: vscode.OutputChannel,
    budget: BinaryIndexBudget | undefined
): Promise<IndexedDependencyDocument[]> {
    if (!budget || budget.remainingClasses === 0) {
        return [];
    }
    try {
        const zip = new AdmZip(await readFile(jarPath));
        const documents: IndexedDependencyDocument[] = [];
        let inspectedEntries = 0;
        for (const entry of zip.getEntries()) {
            inspectedEntries++;
            if (inspectedEntries > MAX_BINARY_JAR_ENTRIES) {
                output.appendLine(`[Zircon] Binary dependency ${jarPath} exceeded ${MAX_BINARY_JAR_ENTRIES} entries; remaining entries skipped.`);
                break;
            }
            if (inspectedEntries % 512 === 0) {
                await yieldToEventLoop();
            }
            if (budget.remainingClasses === 0) {
                break;
            }
            if (entry.isDirectory || !entry.entryName.endsWith('.class') || entry.entryName.includes('$')) {
                continue;
            }
            const classBytes = entry.getData();
            if (!classBytes.includes(Buffer.from('zircon/ExMethod'))
                && !classBytes.includes(Buffer.from('zircon/ExMethodIDE'))) {
                continue;
            }
            const className = entry.entryName.replace(/\.class$/, '').replace(/\//g, '.');
            if (shouldSkipDependencyClassName(className)) {
                continue;
            }
            budget.remainingClasses--;
            const content = await buildVirtualSourceFromClass(jarPath, className, output);
            if (!content) {
                continue;
            }
            const uri = createDependencyUri(jarPath, entry.entryName.replace(/\.class$/, '.java'));
            const source = createVirtualJavaSource(uri, `${simpleNameOf(className)}.java`, content);
            documents.push({
                uri,
                descriptors: parseExMethods(source),
                typeDeclarations: scanJavaSource(content).typeDeclarations,
                content
            });
            if (documents.length % 24 === 0) {
                await yieldToEventLoop();
            }
        }
        return documents;
    } catch (error) {
        output.appendLine(`[Zircon] Failed to index dependency classes from ${jarPath}: ${String(error)}`);
        return [];
    }
}

async function findClasspathFileJarCandidates(): Promise<string[]> {
    const classpathFiles = await vscode.workspace.findFiles('**/.classpath', SEARCH_EXCLUDE);
    const jarPaths = new Set<string>();
    for (const uri of classpathFiles) {
        try {
            const text = Buffer.from(await vscode.workspace.fs.readFile(uri)).toString('utf8');
            const baseDirectory = path.dirname(uri.fsPath);
            for (const entry of [...text.matchAll(/<classpathentry\b[^>]*\bpath="([^"]+)"[^>]*>/g)]) {
                const resolvedPath = resolveClasspathEntryPath(baseDirectory, entry[1]);
                if (resolvedPath && resolvedPath.toLowerCase().endsWith('.jar')) {
                    jarPaths.add(resolvedPath);
                }
                const sourceMatch = entry[0].match(/\bsourcepath="([^"]+)"/);
                const sourcePath = sourceMatch?.[1]
                    ? resolveClasspathEntryPath(baseDirectory, sourceMatch[1])
                    : undefined;
                if (sourcePath && sourcePath.toLowerCase().endsWith('.jar')) {
                    jarPaths.add(sourcePath);
                }
            }
        } catch {
            // ignore malformed .classpath files
        }
    }
    return [...jarPaths];
}

async function yieldToEventLoop(): Promise<void> {
    await new Promise<void>((resolve) => {
        setTimeout(resolve, 0);
    });
}

function resolveClasspathEntryPath(baseDirectory: string, classpathEntry: string): string | undefined {
    if (classpathEntry.length === 0) {
        return undefined;
    }
    if (/^[A-Za-z]:[\\/]/.test(classpathEntry)) {
        return classpathEntry;
    }
    if (/^[a-zA-Z][\w+.-]*:/.test(classpathEntry)) {
        return vscode.Uri.parse(classpathEntry).fsPath;
    }
    if (classpathEntry.startsWith('/')) {
        return undefined;
    }
    return path.resolve(baseDirectory, classpathEntry);
}

async function findCompanionSourceJar(jarPath: string): Promise<string | undefined> {
    if (!jarPath.toLowerCase().endsWith('.jar') || jarPath.toLowerCase().endsWith('-sources.jar')) {
        return undefined;
    }
    const sourceJarPath = jarPath.replace(/\.jar$/i, '-sources.jar');
    return fs.existsSync(sourceJarPath) ? sourceJarPath : undefined;
}

function createDependencyUri(jarPath: string, entryName: string): vscode.Uri {
    return vscode.Uri.from({
        scheme: DEPENDENCY_DOCUMENT_SCHEME,
        authority: path.basename(jarPath),
        path: `/${entryName}`,
        query: Buffer.from(jarPath, 'utf8').toString('hex')
    });
}

function createVirtualJavaSource(uri: vscode.Uri, fileName: string, text: string): JavaTextSource {
    const lines = text.split(/\r?\n/);
    return {
        uri,
        fileName,
        getText() {
            return text;
        },
        lineAt(line: number) {
            return { text: lines[line] ?? '' };
        }
    };
}

async function buildVirtualSourceFromClass(
    jarPath: string,
    className: string,
    output: vscode.OutputChannel
): Promise<string | undefined> {
    try {
        const result = await execFile('javap', ['-classpath', jarPath, '-v', className], {
            maxBuffer: 16 * 1024 * 1024,
            timeout: JAVAP_TIMEOUT_MS
        });
        return buildVirtualSourceFromJavapOutput(className, String(result.stdout));
    } catch (error) {
        output.appendLine(`[Zircon] Failed to inspect ${className} in ${jarPath}: ${String(error)}`);
        return undefined;
    }
}

function buildVirtualSourceFromJavapOutput(className: string, output: string): string | undefined {
    const simpleClassName = className.substring(className.lastIndexOf('.') + 1);
    const packageName = className.includes('.') ? className.slice(0, className.lastIndexOf('.')) : '';
    const methods = extractAnnotatedMethodsFromJavap(output, simpleClassName);
    if (methods.length === 0) {
        return undefined;
    }

    const lines: string[] = [];
    if (packageName.length > 0) {
        lines.push(`package ${packageName};`, '');
    }
    lines.push(`public class ${simpleClassName} {`, '');
    for (const method of methods) {
        lines.push(`    ${method.exAnnotation}`);
        if (method.ideAnnotation) {
            lines.push(`    ${method.ideAnnotation}`);
        }
        lines.push(`    ${method.signature}`, '');
    }
    lines.push('}');
    return lines.join('\n');
}

function shouldSkipDependencyClassName(className: string): boolean {
    return SKIPPED_SCAN_PREFIXES.some((prefix) => className.startsWith(prefix));
}

function extractAnnotatedMethodsFromJavap(
    output: string,
    simpleClassName: string
): Array<{ signature: string; exAnnotation: string; ideAnnotation?: string }> {
    const methods: Array<{ signature: string; exAnnotation: string; ideAnnotation?: string }> = [];
    const lines = output.split(/\r?\n/);
    let currentHeader: string | undefined;
    let currentHasExMethod = false;
    let currentCover = false;
    let currentDirectOnly = false;
    let currentTargetTypes: string[] = [];

    const flush = (): void => {
        if (!currentHeader || !currentHasExMethod) {
            currentHeader = undefined;
            currentHasExMethod = false;
            currentCover = false;
            currentDirectOnly = false;
            currentTargetTypes = [];
            return;
        }
        const signature = buildStubSignatureFromJavapHeader(currentHeader, simpleClassName);
        if (signature) {
            const exArguments: string[] = [];
            if (currentTargetTypes.length > 0) {
                exArguments.push(`ex = { ${currentTargetTypes.map((typeName) => `${typeName}.class`).join(', ')} }`);
            }
            if (currentCover) {
                exArguments.push('cover = true');
            }
            methods.push({
                signature,
                exAnnotation: exArguments.length > 0 ? `@ExMethod(${exArguments.join(', ')})` : '@ExMethod',
                ideAnnotation: currentDirectOnly ? '@ExMethodIDE(shouldInvokeDirectly = true)' : undefined
            });
        }
        currentHeader = undefined;
        currentHasExMethod = false;
        currentCover = false;
        currentDirectOnly = false;
        currentTargetTypes = [];
    };

    for (const line of lines) {
        const trimmed = line.trim();
        if (isJavapMethodHeader(trimmed)) {
            flush();
            currentHeader = trimmed;
            continue;
        }
        if (!currentHeader) {
            continue;
        }
        if (trimmed.includes('zircon.ExMethod')) {
            currentHasExMethod = true;
        }
        if (trimmed.includes('cover=true')) {
            currentCover = true;
        }
        if (trimmed.includes('shouldInvokeDirectly=true') || trimmed.includes('shouldInvokeDirectly = true')) {
            currentDirectOnly = true;
        }
        if (trimmed.includes('ex=[')) {
            currentTargetTypes.push(...extractJavapAnnotationTypes(trimmed));
        }
    }
    flush();
    return methods;
}

function isJavapMethodHeader(line: string): boolean {
    return /^(public|protected|private)\s+.*\)\s*;$/.test(line);
}

function buildStubSignatureFromJavapHeader(header: string, simpleClassName: string): string | undefined {
    const match = header.match(/^(.*\s)([A-Za-z_$][\w$]*)\((.*)\);\s*$/);
    if (!match) {
        return undefined;
    }
    const prefix = match[1];
    const methodName = match[2];
    if (methodName === simpleClassName) {
        return undefined;
    }
    const parameterBlock = match[3].trim();
    const parameterTypes = parameterBlock.length > 0 ? splitTopLevel(parameterBlock, ',') : [];
    const parameters = parameterTypes.map((parameterType, index) => `${normalizeType(parameterType)} arg${index}`);
    return `${prefix}${methodName}(${parameters.join(', ')});`;
}

function extractJavapAnnotationTypes(line: string): string[] {
    const matches = [...line.matchAll(/class\s+([^,\]]+)/g)];
    return matches
        .map((match) => normalizeJavapTypeName(match[1]))
        .filter((value) => value.length > 0);
}

function normalizeJavapTypeName(typeName: string): string {
    const normalized = typeName.trim();
    if (normalized.startsWith('L') && normalized.endsWith(';')) {
        return normalized.slice(1, -1).replace(/\//g, '.');
    }
    return normalized.replace(/\//g, '.');
}

function hasExplicitEx(annotationText: string | undefined): boolean {
    return annotationText !== undefined && /\bex\s*=/.test(annotationText);
}

function parseParameter(input: string): JavaParameterInfo | undefined {
    const normalized = input
        .replace(/@\w+(?:\([^)]*\))?\s*/g, '')
        .replace(/\bfinal\s+/g, '')
        .trim();
    if (normalized.length === 0) {
        return undefined;
    }

    const parts = normalized.split(/\s+/);
    if (parts.length < 2) {
        return undefined;
    }
    const name = parts[parts.length - 1];
    const type = normalizeType(parts.slice(0, -1).join(' '));
    return { name, type };
}

export function normalizeType(input: string): string {
    return input
        .replace(/\s+/g, ' ')
        .replace(/\s*([<>,[\]()])\s*/g, '$1')
        .replace(/\?\s+extends\s+/g, '? extends ')
        .replace(/\?\s+super\s+/g, '? super ')
        .trim();
}

export function splitTopLevel(input: string, separator: string): string[] {
    const parts: string[] = [];
    let depthAngle = 0;
    let depthParen = 0;
    let depthBracket = 0;
    let current = '';

    for (const char of input) {
        if (char === '<') {
            depthAngle++;
        } else if (char === '>') {
            depthAngle = Math.max(depthAngle - 1, 0);
        } else if (char === '(') {
            depthParen++;
        } else if (char === ')') {
            depthParen = Math.max(depthParen - 1, 0);
        } else if (char === '[') {
            depthBracket++;
        } else if (char === ']') {
            depthBracket = Math.max(depthBracket - 1, 0);
        }

        if (char === separator && depthAngle === 0 && depthParen === 0 && depthBracket === 0) {
            parts.push(current);
            current = '';
            continue;
        }
        current += char;
    }

    if (current.trim().length > 0) {
        parts.push(current);
    }
    return parts.map((part) => part.trim()).filter((part) => part.length > 0);
}

export function findDeclaredVariableType(textBefore: string, token: string, context: InternalJavaContext): string | undefined {
    const escapedToken = escapeRegExp(token);
    const modifiers = '(?:(?:public|protected|private|static|final|transient|volatile|synchronized)\\s+)*';
    const typePattern = '([A-Za-z_$][\\w$.]*(?:\\s*<[^;=(){}]+>)?(?:\\[\\])*(?:\\.\\.\\.)?)';
    const patterns = [
        new RegExp(`${modifiers}${typePattern}\\s+${escapedToken}\\s*(?:=|;|,|\\))`, 'g'),
        new RegExp(`for\\s*\\(\\s*${modifiers}${typePattern}\\s+${escapedToken}\\s*:`, 'g'),
        new RegExp(`\\(${modifiers}${typePattern}\\s+${escapedToken}(?:\\s*,|\\s*\\))`, 'g'),
        new RegExp(`catch\\s*\\(${modifiers}${typePattern}\\s+${escapedToken}\\s*\\)`, 'g')
    ];

    for (const pattern of patterns) {
        const matches = [...textBefore.matchAll(pattern)];
        const lastMatch = matches.length > 0 ? matches[matches.length - 1] : undefined;
        if (lastMatch?.[1]) {
            return resolveTypeName(normalizeType(lastMatch[1]), context) ?? normalizeType(lastMatch[1]);
        }
    }
    return undefined;
}

export function resolveTypeName(typeName: string, context: InternalJavaContext): string | undefined {
    const normalized = normalizeType(typeName);
    if (normalized.length === 0) {
        return undefined;
    }
    if (PRIMITIVE_TYPES.has(normalized) || normalized === '?') {
        return normalized;
    }
    if (normalized.endsWith('...')) {
        const elementType = resolveTypeName(normalized.slice(0, -3), context);
        return elementType ? `${elementType}[]` : normalized.replace(/\.\.\.$/, '[]');
    }
    if (normalized.endsWith('[]')) {
        const elementType = resolveTypeName(normalized.slice(0, -2), context);
        return elementType ? `${elementType}[]` : normalized;
    }
    if (normalized.startsWith('? extends ')) {
        const boundedType = resolveTypeName(normalized.slice('? extends '.length), context);
        return boundedType ? `? extends ${boundedType}` : normalized;
    }
    if (normalized.startsWith('? super ')) {
        const boundedType = resolveTypeName(normalized.slice('? super '.length), context);
        return boundedType ? `? super ${boundedType}` : normalized;
    }

    const genericStart = findTopLevelGenericStart(normalized);
    if (genericStart > 0 && normalized.endsWith('>')) {
        const rawType = normalized.slice(0, genericStart);
        const genericArguments = normalized.slice(genericStart + 1, -1);
        const resolvedRawType = resolveSimpleOrQualifiedType(rawType, context) ?? rawType;
        const resolvedArguments = splitTopLevel(genericArguments, ',')
            .map((argument) => resolveTypeName(argument, context) ?? normalizeType(argument));
        return `${resolvedRawType}<${resolvedArguments.join(', ')}>`;
    }

    return resolveSimpleOrQualifiedType(normalized, context);
}

function resolveSimpleOrQualifiedType(typeName: string, context: InternalJavaContext): string | undefined {
    const normalized = normalizeType(typeName);
    if (normalized.length === 0) {
        return undefined;
    }
    if (PRIMITIVE_TYPES.has(normalized)) {
        return normalized;
    }
    if (context.visibleTypes.has(normalized)) {
        return context.visibleTypes.get(normalized);
    }
    if (context.imports.has(normalized)) {
        return context.imports.get(normalized);
    }
    if (JAVA_LANG_TYPES.has(normalized)) {
        return `java.lang.${normalized}`;
    }
    if (isFullyQualifiedTypeName(normalized)) {
        return normalized;
    }
    if (normalized.includes('.')) {
        const parts = normalized.split('.');
        const head = parts.shift();
        if (!head) {
            return normalized;
        }
        const resolvedHead = resolveSimpleOrQualifiedType(head, context);
        if (resolvedHead) {
            return `${resolvedHead}.${parts.join('.')}`;
        }
    }
    if (context.packageName.length > 0) {
        return `${context.packageName}.${normalized}`;
    }
    return normalized;
}

export function buildClassType(typeName: string): string {
    return `java.lang.Class<${normalizeType(typeName)}>`;
}

function parseClassLikeType(typeName: string): { rawType: string; argument?: string } | undefined {
    const normalized = normalizeType(typeName);
    const genericStart = findTopLevelGenericStart(normalized);
    const rawType = genericStart > 0 ? normalized.slice(0, genericStart) : normalized;
    const rawSimpleName = simpleNameOf(rawType);
    if (rawSimpleName !== 'Class') {
        return undefined;
    }
    if (genericStart < 0 || !normalized.endsWith('>')) {
        return { rawType: eraseType(rawType) };
    }
    const genericArguments = splitTopLevel(normalized.slice(genericStart + 1, -1), ',');
    return {
        rawType: eraseType(rawType),
        argument: genericArguments[0]
    };
}

function findTopLevelGenericStart(typeName: string): number {
    let depth = 0;
    for (let index = 0; index < typeName.length; index++) {
        const char = typeName[index];
        if (char === '<') {
            if (depth === 0) {
                return index;
            }
            depth++;
        } else if (char === '>') {
            depth = Math.max(depth - 1, 0);
        }
    }
    return -1;
}

export function eraseType(typeName: string): string {
    return normalizeType(typeName)
        .replace(/<.*>/g, '')
        .replace(/\.\.\.$/, '[]');
}

export function simpleNameOf(typeName: string): string {
    const base = eraseType(typeName);
    const clean = base.endsWith('[]') ? base.slice(0, -2) : base;
    return clean.substring(clean.lastIndexOf('.') + 1);
}

function looksLikeQualifiedTypeExpression(token: string): boolean {
    return /^[A-Z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*$/.test(token);
}

function normalizeReceiverTypeList(receiverTypesInput: readonly string[] | string | undefined): string[] {
    if (!receiverTypesInput) {
        return [];
    }
    const receiverTypes = Array.isArray(receiverTypesInput) ? receiverTypesInput : [receiverTypesInput];
    const normalized = new Set<string>();
    for (const receiverType of receiverTypes) {
        const value = normalizeType(receiverType);
        if (value.length > 0) {
            normalized.add(value);
        }
    }
    return [...normalized];
}

function isFullyQualifiedTypeName(typeName: string): boolean {
    return /^[a-z_]\w*(?:\.[a-z_]\w*)+\.[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*$/.test(typeName);
}

function escapeRegExp(value: string): string {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export function readWordAt(document: vscode.TextDocument, position: vscode.Position): string | undefined {
    const range = document.getWordRangeAtPosition(position);
    if (!range) {
        return undefined;
    }
    return document.getText(range);
}

const JAVA_LANG_TYPES = new Set([
    'Object', 'String', 'Long', 'Integer', 'Short', 'Byte', 'Boolean', 'Character', 'Double', 'Float',
    'Class', 'Thread', 'Runnable', 'Number', 'Exception', 'RuntimeException', 'Iterable', 'Void'
]);

const PRIMITIVE_TYPES = new Set([
    'byte', 'short', 'int', 'long', 'float', 'double', 'boolean', 'char', 'void'
]);

const BUILTIN_PARENT_RELATIONS: Record<string, string[]> = {
    'java.lang.String': ['java.lang.Object', 'java.lang.CharSequence'],
    'java.lang.Integer': ['java.lang.Number', 'java.lang.Object'],
    'java.lang.Long': ['java.lang.Number', 'java.lang.Object'],
    'java.lang.Short': ['java.lang.Number', 'java.lang.Object'],
    'java.lang.Byte': ['java.lang.Number', 'java.lang.Object'],
    'java.lang.Double': ['java.lang.Number', 'java.lang.Object'],
    'java.lang.Float': ['java.lang.Number', 'java.lang.Object'],
    'java.lang.Boolean': ['java.lang.Object'],
    'java.lang.Character': ['java.lang.Object'],
    'java.lang.Class': ['java.lang.Object'],
    'java.util.Collection': ['java.lang.Iterable', 'java.lang.Object'],
    'java.util.List': ['java.util.Collection'],
    'java.util.Set': ['java.util.Collection'],
    'java.util.Queue': ['java.util.Collection'],
    'java.util.Deque': ['java.util.Queue'],
    'java.util.ArrayList': ['java.util.List'],
    'java.util.LinkedList': ['java.util.List', 'java.util.Deque'],
    'java.util.HashSet': ['java.util.Set'],
    'java.util.LinkedHashSet': ['java.util.Set'],
    'java.util.Map': ['java.lang.Object'],
    'java.util.HashMap': ['java.util.Map'],
    'java.util.LinkedHashMap': ['java.util.Map'],
    'java.util.TreeMap': ['java.util.Map'],
    'java.util.Optional': ['java.lang.Object'],
    'java.util.stream.Stream': ['java.lang.Object'],
    'java.util.Iterator': ['java.lang.Object']
};
