import type { ExMethodDescriptor } from './exMethodModel';
import type { ExMethodInvocationSource } from './exMethodUsage';

export function extensionToNormalInvocation(
    invocation: ExMethodInvocationSource,
    descriptor: ExMethodDescriptor
): string {
    if (descriptor.isStaticExtension) {
        return `${descriptor.qualifiedDeclaringClass}.${descriptor.methodName}(${invocation.argumentsText})`;
    }
    const separator = invocation.argumentsText.trim().length > 0 ? ', ' : '';
    return `${descriptor.qualifiedDeclaringClass}.${descriptor.methodName}(`
        + `${invocation.receiverExpression}${separator}${invocation.argumentsText})`;
}

export function normalToExtensionInvocation(
    argumentsText: string,
    descriptor: ExMethodDescriptor
): string | undefined {
    const args = splitArguments(argumentsText);
    if (descriptor.isStaticExtension) {
        const target = descriptor.targetTypes[0];
        return target ? `${simpleTypeName(target)}.${descriptor.methodName}(${argumentsText})` : undefined;
    }
    if (args.length === 0) {
        return undefined;
    }
    return `${args[0]}.${descriptor.methodName}(${args.slice(1).join(', ')})`;
}

export function splitInvocationArguments(text: string): string[] {
    return splitArguments(text);
}

function simpleTypeName(typeName: string): string {
    const normalized = typeName.replace(/\$/g, '.');
    return normalized.slice(normalized.lastIndexOf('.') + 1);
}

function splitArguments(text: string): string[] {
    if (text.trim().length === 0) {
        return [];
    }
    const parts: string[] = [];
    let start = 0;
    let depth = 0;
    for (let index = 0; index < text.length; index += 1) {
        const char = text[index];
        if (char === '"' || char === '\'') {
            const quote = char;
            index += 1;
            while (index < text.length) {
                if (text[index] === '\\') {
                    index += 2;
                    continue;
                }
                if (text[index] === quote) {
                    break;
                }
                index += 1;
            }
        } else if ('([{<'.includes(char)) {
            depth += 1;
        } else if (')]}>'.includes(char)) {
            depth = Math.max(0, depth - 1);
        } else if (char === ',' && depth === 0) {
            parts.push(text.slice(start, index).trim());
            start = index + 1;
        }
    }
    parts.push(text.slice(start).trim());
    return parts;
}
