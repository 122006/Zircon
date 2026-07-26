import { execFile as execFileCallback } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import { promisify } from 'util';
import * as vscode from 'vscode';
import * as yauzl from 'yauzl';
import { ExMethodDescriptor, JavaDocumentContext, JavaParameterInfo } from './exMethodModel';
import { countStructuralBraceDelta } from './javaLexing';
import { collectJavaProjectDependencyJars } from './javaProjectClasspath';

const SEARCH_EXCLUDE = '**/{node_modules,build,out,.git,.gradle}/**';
const DEPENDENCY_SEARCH_EXCLUDE = '**/{node_modules,.git,.gradle,out}/**';
const execFile = promisify(execFileCallback);
const stat = promisify(fs.stat);

export const DEPENDENCY_DOCUMENT_SCHEME = 'zircon-dependency';

interface JavaTypeDeclaration {
    simpleName: string;
    qualifiedName: string;
    parentTypes: string[];
    annotations: string[];
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
    jarPath: string;
    uri: vscode.Uri;
    descriptors: ExMethodDescriptor[];
    typeDeclarations: JavaTypeDeclaration[];
    content?: string;
}

interface DependencyJarCacheEntry {
    stamp: string;
    documents: IndexedDependencyDocument[];
}

interface DependencyJarInventoryEntry {
    stamp: string;
    entries: Set<string>;
}

export interface ImportedDependencyTargets {
    exactTypes: Set<string>;
    wildcardPackages: Set<string>;
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
    private readonly dependencyJarInventories = new Map<string, DependencyJarInventoryEntry>();
    private readonly targetedDependencyCache = new Map<string, DependencyJarCacheEntry>();
    private readonly loadedDependencyDocuments = new Map<string, IndexedDependencyDocument>();
    private dependencyJarPaths: string[] = [];
    private dependencyJarStamps = new Map<string, string>();
    private dependencyCandidatesReady = false;
    private allDependenciesIndexed = false;
    private dependencyLoadQueue: Promise<void> = Promise.resolve();
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

    /** Load only dependency extension containers explicitly visible to this Java source. */
    public async ensureImportedDependencies(document: vscode.TextDocument): Promise<void> {
        if (document.languageId !== 'java' || this.allDependenciesIndexed) {
            return;
        }
        const targets = collectImportedDependencyTargets(document.getText());
        if (targets.exactTypes.size === 0 && targets.wildcardPackages.size === 0) {
            return;
        }
        await this.waitForActiveRebuild();
        await this.enqueueDependencyLoad(async () => {
            if (this.allDependenciesIndexed) {
                return;
            }
            await this.ensureDependencyCandidates();
            const documents = await indexImportedDependencyTargets(
                this.dependencyJarPaths,
                targets,
                this.output,
                this.dependencyJarInventories,
                this.targetedDependencyCache
            );
            if (documents.length === 0) {
                return;
            }
            applyIndexedDependencyDocuments(
                documents,
                this.descriptors,
                this.typeDeclarations,
                this.virtualDocumentContents,
                this.loadedDependencyDocuments
            );
            this.rebuildHierarchyCache();
        });
    }

    /** Completion is the one operation allowed to pay for a full dependency scan. */
    public async ensureAllDependenciesIndexed(): Promise<void> {
        if (this.allDependenciesIndexed) {
            return;
        }
        await this.waitForActiveRebuild();
        await this.enqueueDependencyLoad(async () => {
            if (this.allDependenciesIndexed) {
                return;
            }
            await this.ensureDependencyCandidates();
            const stats = await collectDependencyIndexEntries(
                this.descriptors,
                this.typeDeclarations,
                this.virtualDocumentContents,
                this.output,
                this.dependencyJarCache,
                this.loadedDependencyDocuments,
                this.dependencyJarPaths
            );
            this.allDependenciesIndexed = true;
            this.rebuildHierarchyCache();
            this.output.appendLine(
                `[Zircon] Completion loaded ${stats.documentCount} dependency documents from ${stats.jarCount} jars.`
            );
        });
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
            if (descriptor.filterAnnotations.length > 0
                && !receiverTypes.some((receiverType) => {
                    return this.typeHasDirectAnnotations(receiverType, descriptor.filterAnnotations);
                })) {
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
        this.dependencyJarInventories.clear();
        this.targetedDependencyCache.clear();
        this.loadedDependencyDocuments.clear();
    }

    private typeHasDirectAnnotations(receiverType: string, requiredAnnotations: readonly string[]): boolean {
        const receiverBase = eraseType(receiverType);
        const receiverCandidates = this.getCanonicalTypeCandidates(receiverBase);
        const declarations = [...this.typeDeclarations.values()].flat().filter((declaration) => {
            return receiverCandidates.has(declaration.qualifiedName)
                || receiverCandidates.has(declaration.simpleName)
                || simpleNameOf(receiverBase) === declaration.simpleName;
        });
        if (declarations.length === 0) {
            // The native JDT completion path can inspect binary annotations.
            // The lightweight provider must not offer an annotation-filtered
            // method when it cannot prove the receiver satisfies the filter.
            return false;
        }
        return declarations.some((declaration) => requiredAnnotations.every((required) => {
            const requiredSimple = simpleNameOf(required);
            return declaration.annotations.some((actual) => {
                return actual === required || simpleNameOf(actual) === requiredSimple;
            });
        }));
    }

    private async runRebuildLoop(): Promise<void> {
        do {
            this.rebuildRequestedWhileRunning = false;
            await this.doRebuild();
        } while (this.rebuildRequestedWhileRunning);
    }

    private async doRebuild(): Promise<void> {
        // A classpath rebuild and a lazy dependency load must not replace each
        // other's maps. New lazy loads already wait for rebuildPromise.
        await this.dependencyLoadQueue;
        const javaFiles = await vscode.workspace.findFiles('**/*.java', SEARCH_EXCLUDE);
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
        // Rebuild only discovers the classpath. Dependency contents stay lazy:
        // imported containers are loaded on demand, completion may request all.
        await this.refreshDependencyCandidates(await findDependencyJarCandidates(this.output));
        applyIndexedDependencyDocuments(
            [...this.loadedDependencyDocuments.values()],
            nextDescriptors,
            nextTypes,
            nextVirtualDocumentContents
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
            + `${this.loadedDependencyDocuments.size} lazily loaded dependency documents; `
            + `${this.dependencyJarPaths.length} dependency jars are available for completion.`
        );
    }

    private async waitForActiveRebuild(): Promise<void> {
        const activeRebuild = this.rebuildPromise;
        if (activeRebuild) {
            await activeRebuild;
        }
    }

    private async enqueueDependencyLoad(task: () => Promise<void>): Promise<void> {
        const current = this.dependencyLoadQueue.then(task, task);
        this.dependencyLoadQueue = current.then(() => undefined, () => undefined);
        await current;
    }

    private async ensureDependencyCandidates(): Promise<void> {
        if (!this.dependencyCandidatesReady) {
            await this.refreshDependencyCandidates(await findDependencyJarCandidates(this.output));
        }
    }

    private async refreshDependencyCandidates(jarPaths: string[]): Promise<void> {
        const nextStamps = new Map<string, string>();
        await Promise.all(jarPaths.map(async (jarPath) => {
            nextStamps.set(jarPath, await buildDependencyJarStamp(jarPath) ?? 'missing');
        }));
        const changed = jarPaths.length !== this.dependencyJarPaths.length
            || jarPaths.some((jarPath, index) => jarPath !== this.dependencyJarPaths[index])
            || jarPaths.some((jarPath) => nextStamps.get(jarPath) !== this.dependencyJarStamps.get(jarPath));
        this.dependencyJarPaths = jarPaths;
        this.dependencyJarStamps = nextStamps;
        this.dependencyCandidatesReady = true;
        if (!changed) {
            return;
        }
        this.allDependenciesIndexed = false;
        this.loadedDependencyDocuments.clear();
        this.dependencyJarCache.clear();
        this.targetedDependencyCache.clear();
        this.dependencyJarInventories.clear();
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
    const normalized = rawSignature
        .replace(/\s+/g, ' ')
        .replace(/\s*\{.*$/, '')
        // javap-backed virtual sources use declaration stubs ending in ';'.
        // Source methods end in a body, so the old parser accidentally discarded
        // every extension method loaded from a binary dependency.
        .replace(/;\s*$/, '')
        .trim();
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

    const importPattern = /^\s*import\s+(static\s+)?([\w.*]+)\s*;/gm;
    let wildcardImportIndex = 0;
    for (let match = importPattern.exec(text); match !== null; match = importPattern.exec(text)) {
        const isStatic = match[1] !== undefined;
        const importedName = match[2];
        const qualifiedName = isStatic
            ? (importedName.endsWith('.*')
                ? importedName.slice(0, -2)
                : importedName.slice(0, importedName.lastIndexOf('.')))
            : importedName;
        if (!isStatic && qualifiedName.endsWith('.*')) {
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
            .map((parentType) => resolveTypeName(parentType, baseContext) ?? normalizeType(parentType)),
        annotations: declaration.annotations
            .map((annotation) => resolveTypeName(annotation, baseContext) ?? normalizeType(annotation))
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
        const annotations = collectTypeAnnotations(text, match.index);
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
            annotations,
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
        annotations: declaration.annotations,
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

/**
 * Extension containers follow normal Java visibility rules: a dependency class
 * must be explicitly imported (or covered by a wildcard import). Static imports
 * are accepted as well so older Zircon source styles remain discoverable.
 */
export function collectImportedDependencyTargets(source: string): ImportedDependencyTargets {
    const exactTypes = new Set<string>();
    const wildcardPackages = new Set<string>();
    const importPattern = /^\s*import\s+(static\s+)?([A-Za-z_$][\w$]*(?:\.[A-Za-z_$*][\w$*]*)*)\s*;/gm;
    for (let match = importPattern.exec(source); match; match = importPattern.exec(source)) {
        const isStatic = match[1] !== undefined;
        const importedName = match[2];
        if (!isStatic && importedName.endsWith('.*')) {
            wildcardPackages.add(importedName.slice(0, -2));
            continue;
        }
        if (isStatic) {
            const withoutWildcard = importedName.endsWith('.*')
                ? importedName.slice(0, -2)
                : importedName.slice(0, importedName.lastIndexOf('.'));
            if (withoutWildcard.length > 0) {
                exactTypes.add(withoutWildcard);
            }
            continue;
        }
        exactTypes.add(importedName);
    }
    return { exactTypes, wildcardPackages };
}

async function indexImportedDependencyTargets(
    jarPaths: readonly string[],
    targets: ImportedDependencyTargets,
    output: vscode.OutputChannel,
    inventories: Map<string, DependencyJarInventoryEntry>,
    targetedCache: Map<string, DependencyJarCacheEntry>
): Promise<IndexedDependencyDocument[]> {
    const { sourceJars, binaryJars } = partitionDependencyJars(jarPaths);
    const documents: IndexedDependencyDocument[] = [];
    for (const [jarKind, candidates] of [
        ['source', sourceJars] as const,
        ['binary', binaryJars] as const
    ]) {
        for (const jarPath of candidates) {
            const inventory = await getDependencyJarInventory(jarPath, inventories, output);
            if (!inventory) {
                continue;
            }
            const matchedEntries = matchImportedJarEntries(inventory.entries, targets, jarKind);
            if (matchedEntries.length === 0) {
                continue;
            }
            documents.push(...await indexTargetedJarEntries(
                jarPath,
                jarKind,
                matchedEntries,
                inventory.stamp,
                targetedCache,
                output
            ));
        }
    }
    return dedupeIndexedDependencyDocuments(documents);
}

function partitionDependencyJars(jarPaths: readonly string[]): { sourceJars: string[]; binaryJars: string[] } {
    const sourceArtifactKeys = new Set(jarPaths
        .filter((jarPath) => jarPath.toLowerCase().endsWith('-sources.jar'))
        .map(toJarArtifactKey));
    return {
        sourceJars: jarPaths.filter((jarPath) => jarPath.toLowerCase().endsWith('-sources.jar')),
        binaryJars: jarPaths.filter((jarPath) => {
            return !jarPath.toLowerCase().endsWith('-sources.jar')
                && !sourceArtifactKeys.has(toJarArtifactKey(jarPath));
        })
    };
}

function matchImportedJarEntries(
    entries: ReadonlySet<string>,
    targets: ImportedDependencyTargets,
    jarKind: 'source' | 'binary'
): string[] {
    const suffix = jarKind === 'source' ? '.java' : '.class';
    const matches = new Set<string>();
    for (const qualifiedType of targets.exactTypes) {
        const directEntry = `${qualifiedType.replace(/\./g, '/')}${suffix}`;
        if (entries.has(directEntry)) {
            matches.add(directEntry);
        }
        // Explicit imports can name a nested extension container. Package and
        // type boundaries are not encoded in an import, so try the finite set
        // of possible boundaries and retain only paths present in the jar.
        const parts = qualifiedType.split('.');
        if (jarKind === 'binary') {
            for (let classStart = parts.length - 2; classStart >= 0; classStart--) {
                const nestedEntry = `${parts.slice(0, classStart).join('/')}${classStart > 0 ? '/' : ''}`
                    + `${parts.slice(classStart).join('$')}.class`;
                if (entries.has(nestedEntry)) {
                    matches.add(nestedEntry);
                }
            }
        } else {
            for (let classStart = parts.length - 2; classStart >= 0; classStart--) {
                const nestedSourceEntry = `${parts.slice(0, classStart).join('/')}${classStart > 0 ? '/' : ''}`
                    + `${parts[classStart]}.java`;
                if (entries.has(nestedSourceEntry)) {
                    matches.add(nestedSourceEntry);
                }
            }
        }
    }
    for (const packageName of targets.wildcardPackages) {
        const prefix = `${packageName.replace(/\./g, '/')}/`;
        for (const entry of entries) {
            const remainder = entry.startsWith(prefix) ? entry.slice(prefix.length) : '';
            if (remainder.length === 0 || remainder.includes('/') || !remainder.endsWith(suffix)) {
                continue;
            }
            if (jarKind === 'binary' && remainder.includes('$')) {
                continue;
            }
            matches.add(entry);
        }
    }
    return [...matches].sort();
}

async function indexTargetedJarEntries(
    jarPath: string,
    jarKind: 'source' | 'binary',
    entryNames: readonly string[],
    stamp: string,
    targetedCache: Map<string, DependencyJarCacheEntry>,
    output: vscode.OutputChannel
): Promise<IndexedDependencyDocument[]> {
    const documents: IndexedDependencyDocument[] = [];
    const missingEntries: string[] = [];
    for (const entryName of entryNames) {
        const cacheKey = `${jarKind}:${jarPath}:${entryName}`;
        const cached = targetedCache.get(cacheKey);
        if (cached?.stamp === stamp) {
            documents.push(...cached.documents);
        } else {
            missingEntries.push(entryName);
        }
    }
    if (missingEntries.length === 0) {
        return documents;
    }
    try {
        const pendingEntries = new Set(missingEntries);
        await forEachZipEntry(jarPath, async (zip, entry) => {
            const entryName = entry.fileName;
            if (!pendingEntries.delete(entryName)) {
                return;
            }
            const cacheKey = `${jarKind}:${jarPath}:${entryName}`;
            let indexed: IndexedDependencyDocument[] = [];
            if (!isZipDirectory(entry)) {
                if (jarKind === 'source') {
                    const text = (await readZipEntryBuffer(zip, entry)).toString('utf8');
                    if (text.includes('@ExMethod') || text.includes('@ExMethodIDE')) {
                        const uri = createDependencyUri(jarPath, entryName);
                        const source = createVirtualJavaSource(uri, path.basename(entryName), text);
                        indexed = [{
                            jarPath,
                            uri,
                            descriptors: parseExMethods(source),
                            typeDeclarations: scanJavaSource(text).typeDeclarations,
                            content: text
                        }];
                    }
                } else {
                    const classBytes = await readZipEntryBuffer(zip, entry);
                    if (classBytes.includes(Buffer.from('zircon/ExMethod'))
                        || classBytes.includes(Buffer.from('zircon/ExMethodIDE'))) {
                        const className = entryName.replace(/\.class$/, '').replace(/\//g, '.');
                        const content = await buildVirtualSourceFromClass(jarPath, className, output);
                        if (content) {
                            const uri = createDependencyUri(jarPath, entryName.replace(/\.class$/, '.java'));
                            const source = createVirtualJavaSource(uri, `${simpleNameOf(className)}.java`, content);
                            indexed = [{
                                jarPath,
                                uri,
                                descriptors: parseExMethods(source),
                                typeDeclarations: scanJavaSource(content).typeDeclarations,
                                content
                            }];
                        }
                    }
                }
            }
            targetedCache.set(cacheKey, { stamp, documents: indexed });
            documents.push(...indexed);
        });
        for (const entryName of pendingEntries) {
            const cacheKey = `${jarKind}:${jarPath}:${entryName}`;
            targetedCache.set(cacheKey, { stamp, documents: [] });
        }
    } catch (error) {
        output.appendLine(`[Zircon] Failed to load imported dependency entries from ${jarPath}: ${String(error)}`);
    }
    return documents;
}

async function getDependencyJarInventory(
    jarPath: string,
    cache: Map<string, DependencyJarInventoryEntry>,
    output: vscode.OutputChannel
): Promise<DependencyJarInventoryEntry | undefined> {
    const stamp = await buildDependencyJarStamp(jarPath);
    if (!stamp) {
        return undefined;
    }
    const cached = cache.get(jarPath);
    if (cached?.stamp === stamp) {
        return cached;
    }
    try {
        let entries: Set<string>;
        try {
            entries = await readZipCentralDirectory(jarPath);
        } catch {
            // ZIP64 and unusual archives take the compatibility path. Normal
            // jars stay on the central-directory-only fast path.
            entries = new Set<string>();
            await forEachZipEntry(jarPath, (_zip, entry) => {
                entries.add(entry.fileName);
            });
        }
        const inventory = { stamp, entries };
        cache.set(jarPath, inventory);
        return inventory;
    } catch (error) {
        output.appendLine(`[Zircon] Failed to read dependency directory ${jarPath}: ${String(error)}`);
        return undefined;
    }
}

async function readZipCentralDirectory(jarPath: string): Promise<Set<string>> {
    const handle = await fs.promises.open(jarPath, 'r');
    try {
        const fileStat = await handle.stat();
        const tailLength = Math.min(fileStat.size, 65_557);
        const tail = Buffer.alloc(tailLength);
        await handle.read(tail, 0, tailLength, fileStat.size - tailLength);
        let eocdOffset = -1;
        for (let offset = tail.length - 22; offset >= 0; offset--) {
            if (tail.readUInt32LE(offset) === 0x06054b50) {
                eocdOffset = offset;
                break;
            }
        }
        if (eocdOffset < 0) {
            throw new Error('ZIP end-of-central-directory record not found');
        }
        const directorySize = tail.readUInt32LE(eocdOffset + 12);
        const directoryOffset = tail.readUInt32LE(eocdOffset + 16);
        if (directorySize === 0xffffffff || directoryOffset === 0xffffffff) {
            throw new Error('ZIP64 dependency jars are not supported by the fast directory reader');
        }
        const directory = Buffer.alloc(directorySize);
        await handle.read(directory, 0, directorySize, directoryOffset);
        const entries = new Set<string>();
        for (let offset = 0; offset + 46 <= directory.length;) {
            if (directory.readUInt32LE(offset) !== 0x02014b50) {
                break;
            }
            const nameLength = directory.readUInt16LE(offset + 28);
            const extraLength = directory.readUInt16LE(offset + 30);
            const commentLength = directory.readUInt16LE(offset + 32);
            entries.add(directory.toString('utf8', offset + 46, offset + 46 + nameLength));
            offset += 46 + nameLength + extraLength + commentLength;
        }
        return entries;
    } finally {
        await handle.close();
    }
}

const MAX_DEPENDENCY_ENTRY_BYTES = 64 * 1024 * 1024;

async function forEachZipEntry(
    jarPath: string,
    visitor: (zip: yauzl.ZipFile, entry: yauzl.Entry) => void | Promise<void>
): Promise<void> {
    await new Promise<void>((resolve, reject) => {
        yauzl.open(jarPath, { lazyEntries: true, autoClose: true }, (openError, zip) => {
            if (openError || !zip) {
                reject(openError ?? new Error(`Unable to open dependency archive ${jarPath}`));
                return;
            }
            let settled = false;
            const fail = (error: unknown): void => {
                if (settled) {
                    return;
                }
                settled = true;
                zip.close();
                reject(error);
            };
            zip.once('error', fail);
            zip.once('end', () => {
                if (!settled) {
                    settled = true;
                    resolve();
                }
            });
            zip.on('entry', entry => {
                Promise.resolve(visitor(zip, entry))
                    .then(() => {
                        if (!settled) {
                            zip.readEntry();
                        }
                    })
                    .catch(fail);
            });
            zip.readEntry();
        });
    });
}

async function readZipEntryBuffer(zip: yauzl.ZipFile, entry: yauzl.Entry): Promise<Buffer> {
    if (entry.uncompressedSize > MAX_DEPENDENCY_ENTRY_BYTES) {
        throw new Error(
            `Dependency archive entry ${entry.fileName} is too large (${entry.uncompressedSize} bytes)`
        );
    }
    return await new Promise<Buffer>((resolve, reject) => {
        zip.openReadStream(entry, (openError, stream) => {
            if (openError || !stream) {
                reject(openError ?? new Error(`Unable to read dependency archive entry ${entry.fileName}`));
                return;
            }
            const chunks: Buffer[] = [];
            let totalBytes = 0;
            stream.on('data', chunk => {
                const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
                totalBytes += buffer.length;
                if (totalBytes > MAX_DEPENDENCY_ENTRY_BYTES) {
                    stream.destroy(new Error(
                        `Dependency archive entry ${entry.fileName} exceeded the extraction limit`
                    ));
                    return;
                }
                chunks.push(buffer);
            });
            stream.once('error', reject);
            stream.once('end', () => resolve(Buffer.concat(chunks, totalBytes)));
        });
    });
}

function isZipDirectory(entry: yauzl.Entry): boolean {
    return /\/$/.test(entry.fileName);
}

function dedupeIndexedDependencyDocuments(
    documents: readonly IndexedDependencyDocument[]
): IndexedDependencyDocument[] {
    const unique = new Map<string, IndexedDependencyDocument>();
    for (const document of documents) {
        unique.set(document.uri.toString(), document);
    }
    return [...unique.values()];
}

async function collectDependencyIndexEntries(
    descriptorMap: Map<string, ExMethodDescriptor[]>,
    typeMap: Map<string, JavaTypeDeclaration[]>,
    virtualDocumentContents: Map<string, string>,
    output: vscode.OutputChannel,
    dependencyJarCache: Map<string, DependencyJarCacheEntry>,
    loadedDependencyDocuments: Map<string, IndexedDependencyDocument>,
    knownJarPaths?: readonly string[]
): Promise<DependencyIndexStats> {
    const jarPaths = knownJarPaths ?? await findDependencyJarCandidates(output);
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
            activeCacheKeys,
            loadedDependencyDocuments
        );
        processedJars++;
        if (processedJars % 2 === 0) {
            await yieldToEventLoop();
        }
    }
    for (const jarPath of binaryJars) {
        documentCount += await applyCachedDependencyJar(
            jarPath,
            'binary',
            descriptorMap,
            typeMap,
            virtualDocumentContents,
            output,
            dependencyJarCache,
            activeCacheKeys,
            loadedDependencyDocuments
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
        return vscode.workspace.findFiles(pattern, DEPENDENCY_SEARCH_EXCLUDE);
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

    const sortedJarPaths = [...enrichedJarPaths].sort(compareDependencyJarPriority);
    output.appendLine(`[Zircon] Dependency jar candidates: total=${sortedJarPaths.length}, redhat.java=${redhatJarCount}.`);
    return sortedJarPaths;
}

function collectTypeAnnotations(text: string, classKeywordOffset: number): string[] {
    let start = classKeywordOffset - 1;
    while (start >= 0 && text[start] !== ';' && text[start] !== '{' && text[start] !== '}') {
        start -= 1;
    }
    const prefix = text.slice(start + 1, classKeywordOffset)
        .replace(/\/\*[\s\S]*?\*\//g, ' ')
        .replace(/\/\/[^\r\n]*/g, ' ');
    const annotations: string[] = [];
    const pattern = /@([A-Za-z_$][\w$.]*)\b/g;
    for (let match = pattern.exec(prefix); match; match = pattern.exec(prefix)) {
        if (match[1] !== 'interface') {
            annotations.push(match[1]);
        }
    }
    return annotations;
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
    loadedDependencyDocuments: Map<string, IndexedDependencyDocument>
): Promise<number> {
    const cacheKey = `${jarKind}:${jarPath}`;
    activeCacheKeys.add(cacheKey);
    const stamp = await buildDependencyJarStamp(jarPath);
    if (stamp) {
        const cached = dependencyJarCache.get(cacheKey);
        if (cached?.stamp === stamp) {
            applyIndexedDependencyDocuments(
                cached.documents,
                descriptorMap,
                typeMap,
                virtualDocumentContents,
                loadedDependencyDocuments
            );
            return cached.documents.length;
        }
    }

    const documents = jarKind === 'source'
        ? await indexSourceJar(jarPath, output)
        : await indexBinaryJar(jarPath, output);
    applyIndexedDependencyDocuments(
        documents,
        descriptorMap,
        typeMap,
        virtualDocumentContents,
        loadedDependencyDocuments
    );

    if (stamp) {
        dependencyJarCache.set(cacheKey, { stamp, documents });
    }
    return documents.length;
}

function applyIndexedDependencyDocuments(
    documents: readonly IndexedDependencyDocument[],
    descriptorMap: Map<string, ExMethodDescriptor[]>,
    typeMap: Map<string, JavaTypeDeclaration[]>,
    virtualDocumentContents: Map<string, string>,
    loadedDependencyDocuments?: Map<string, IndexedDependencyDocument>
): void {
    for (const document of documents) {
        descriptorMap.set(document.uri.toString(), document.descriptors);
        typeMap.set(document.uri.toString(), document.typeDeclarations);
        if (document.content !== undefined) {
            virtualDocumentContents.set(document.uri.toString(), document.content);
        }
        loadedDependencyDocuments?.set(document.uri.toString(), document);
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
        const documents: IndexedDependencyDocument[] = [];
        let inspectedEntries = 0;
        await forEachZipEntry(jarPath, async (zip, entry) => {
            inspectedEntries++;
            if (inspectedEntries % 256 === 0) {
                await yieldToEventLoop();
            }
            if (isZipDirectory(entry) || !entry.fileName.endsWith('.java')) {
                return;
            }
            const text = (await readZipEntryBuffer(zip, entry)).toString('utf8');
            if (!text.includes('@ExMethod') && !text.includes('@ExMethodIDE')) {
                return;
            }
            const uri = createDependencyUri(jarPath, entry.fileName);
            const source = createVirtualJavaSource(uri, path.basename(entry.fileName), text);
            documents.push({
                jarPath,
                uri,
                descriptors: parseExMethods(source),
                typeDeclarations: scanJavaSource(text).typeDeclarations,
                content: text
            });
            if (documents.length % 24 === 0) {
                await yieldToEventLoop();
            }
        });
        return documents;
    } catch (error) {
        output.appendLine(`[Zircon] Failed to index dependency sources from ${jarPath}: ${String(error)}`);
        return [];
    }
}

async function indexBinaryJar(
    jarPath: string,
    output: vscode.OutputChannel
): Promise<IndexedDependencyDocument[]> {
    try {
        const documents: IndexedDependencyDocument[] = [];
        let inspectedEntries = 0;
        await forEachZipEntry(jarPath, async (zip, entry) => {
            inspectedEntries++;
            if (inspectedEntries % 512 === 0) {
                await yieldToEventLoop();
            }
            if (isZipDirectory(entry) || !entry.fileName.endsWith('.class')) {
                return;
            }
            const classBytes = await readZipEntryBuffer(zip, entry);
            if (!classBytes.includes(Buffer.from('zircon/ExMethod'))
                && !classBytes.includes(Buffer.from('zircon/ExMethodIDE'))) {
                return;
            }
            const className = entry.fileName.replace(/\.class$/, '').replace(/\//g, '.');
            const content = await buildVirtualSourceFromClass(jarPath, className, output);
            if (!content) {
                return;
            }
            const uri = createDependencyUri(jarPath, entry.fileName.replace(/\.class$/, '.java'));
            const source = createVirtualJavaSource(uri, `${simpleNameOf(className)}.java`, content);
            documents.push({
                jarPath,
                uri,
                descriptors: parseExMethods(source),
                typeDeclarations: scanJavaSource(content).typeDeclarations,
                content
            });
            if (documents.length % 24 === 0) {
                await yieldToEventLoop();
            }
        });
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
            maxBuffer: 16 * 1024 * 1024
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
