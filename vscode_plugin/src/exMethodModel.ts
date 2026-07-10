import * as vscode from 'vscode';

export interface JavaParameterInfo {
    name: string;
    type: string;
}

export interface ExMethodDescriptor {
    uri: vscode.Uri;
    packageName: string;
    declaringClass: string;
    qualifiedDeclaringClass: string;
    methodName: string;
    returnType: string;
    parameters: JavaParameterInfo[];
    targetTypes: string[];
    filterAnnotations: string[];
    isStaticExtension: boolean;
    cover: boolean;
    shouldInvokeDirectly: boolean;
    line: number;
    nameRange: vscode.Range;
    signature: string;
}

export interface JavaDocumentContext {
    packageName: string;
    imports: Map<string, string>;
    currentClassName?: string;
}
