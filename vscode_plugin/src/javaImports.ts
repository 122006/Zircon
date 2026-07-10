import * as vscode from 'vscode';
import { ExMethodDescriptor } from './exMethodModel';
import { buildDocumentContext } from './exMethodIndex';

const IMPORT_PATTERN = /^\s*import\s+([\w.*]+)\s*;/gm;
const PACKAGE_PATTERN = /^\s*package\s+[\w.]+\s*;/m;

interface ImportStatement {
    qualifiedName: string;
    range: vscode.Range;
}

export function buildExMethodImportTextEdits(
    document: vscode.TextDocument,
    descriptor: ExMethodDescriptor
): vscode.TextEdit[] {
    const importEdit = buildExMethodImportTextEdit(document, descriptor);
    return importEdit ? [importEdit] : [];
}

function buildExMethodImportTextEdit(
    document: vscode.TextDocument,
    descriptor: ExMethodDescriptor
): vscode.TextEdit | undefined {
    const qualifiedDeclaringClass = descriptor.qualifiedDeclaringClass;
    if (!shouldAutoImport(document, descriptor, qualifiedDeclaringClass)) {
        return undefined;
    }

    const text = document.getText();
    const lineBreak = text.includes('\r\n') ? '\r\n' : '\n';
    const imports = collectImports(document, text);

    if (imports.length > 0) {
        const insertBefore = imports.find((statement) => statement.qualifiedName.localeCompare(qualifiedDeclaringClass) > 0);
        if (insertBefore) {
            return vscode.TextEdit.insert(insertBefore.range.start, `import ${qualifiedDeclaringClass};${lineBreak}`);
        }
        return vscode.TextEdit.insert(imports[imports.length - 1].range.end, `${lineBreak}import ${qualifiedDeclaringClass};`);
    }

    const packageMatch = PACKAGE_PATTERN.exec(text);
    if (packageMatch) {
        return vscode.TextEdit.insert(
            document.positionAt(packageMatch.index + packageMatch[0].length),
            `${lineBreak}${lineBreak}import ${qualifiedDeclaringClass};${lineBreak}`
        );
    }

    const fallbackOffset = findLeadingDeclarationOffset(text);
    return vscode.TextEdit.insert(
        document.positionAt(fallbackOffset),
        `import ${qualifiedDeclaringClass};${lineBreak}${lineBreak}`
    );
}

function shouldAutoImport(
    document: vscode.TextDocument,
    descriptor: ExMethodDescriptor,
    qualifiedDeclaringClass: string
): boolean {
    if (descriptor.packageName.length === 0 || descriptor.uri.toString() === document.uri.toString()) {
        return false;
    }

    const context = buildDocumentContext(document, new vscode.Position(0, 0));
    if (descriptor.packageName === context.packageName) {
        return false;
    }

    const visibleType = context.visibleTypes.get(descriptor.declaringClass);
    if (visibleType === qualifiedDeclaringClass) {
        return false;
    }
    if (visibleType && visibleType !== qualifiedDeclaringClass) {
        return false;
    }

    for (const importedType of context.imports.values()) {
        if (importedType === qualifiedDeclaringClass) {
            return false;
        }
        if (importedType.endsWith('.*')) {
            if (importedType.slice(0, -2) === descriptor.packageName) {
                return false;
            }
            continue;
        }
        if (simpleNameOf(importedType) === descriptor.declaringClass && importedType !== qualifiedDeclaringClass) {
            return false;
        }
    }

    return true;
}

function collectImports(document: vscode.TextDocument, text: string): ImportStatement[] {
    const imports: ImportStatement[] = [];
    for (let match = IMPORT_PATTERN.exec(text); match !== null; match = IMPORT_PATTERN.exec(text)) {
        imports.push({
            qualifiedName: match[1],
            range: new vscode.Range(
                document.positionAt(match.index),
                document.positionAt(match.index + match[0].length)
            )
        });
    }
    return imports;
}

function findLeadingDeclarationOffset(text: string): number {
    let offset = 0;
    while (offset < text.length) {
        const current = text[offset];
        if (/\s/.test(current)) {
            offset++;
            continue;
        }
        if (text.startsWith('//', offset)) {
            const nextLine = text.indexOf('\n', offset);
            offset = nextLine < 0 ? text.length : nextLine + 1;
            continue;
        }
        if (text.startsWith('/*', offset)) {
            const blockEnd = text.indexOf('*/', offset + 2);
            offset = blockEnd < 0 ? text.length : blockEnd + 2;
            continue;
        }
        return offset;
    }
    return text.length;
}

function simpleNameOf(typeName: string): string {
    return typeName.substring(typeName.lastIndexOf('.') + 1);
}
