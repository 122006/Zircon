import { normalizeType, simpleNameOf, splitTopLevel } from './exMethodIndex';
import { ExMethodDescriptor, JavaParameterInfo } from './exMethodModel';

export function getVisibleParameters(descriptor: ExMethodDescriptor): JavaParameterInfo[] {
    return descriptor.isStaticExtension ? descriptor.parameters : descriptor.parameters.slice(1);
}

export function formatParameter(parameter: JavaParameterInfo, index: number): string {
    const type = formatTypeForDisplay(parameter.type);
    const fallbackName = `arg${index + 1}`;
    const name = parameter.name.trim() || fallbackName;
    return type.length > 0 ? `${type} ${name}` : name;
}

export function formatVisibleParameterList(visibleParameters: readonly JavaParameterInfo[]): string {
    return `(${visibleParameters.map((parameter, index) => formatParameter(parameter, index)).join(', ')})`;
}

export function formatTypeForDisplay(typeName: string): string {
    const normalized = normalizeType(typeName);
    if (normalized.length === 0) {
        return normalized;
    }
    if (normalized.endsWith('...')) {
        return `${formatTypeForDisplay(normalized.slice(0, -3))}...`;
    }
    if (normalized.endsWith('[]')) {
        return `${formatTypeForDisplay(normalized.slice(0, -2))}[]`;
    }
    if (normalized.startsWith('? extends ')) {
        return `? extends ${formatTypeForDisplay(normalized.slice('? extends '.length))}`;
    }
    if (normalized.startsWith('? super ')) {
        return `? super ${formatTypeForDisplay(normalized.slice('? super '.length))}`;
    }
    const genericStart = findTopLevelGenericStart(normalized);
    if (genericStart > 0 && normalized.endsWith('>')) {
        const rawType = normalized.slice(0, genericStart);
        const genericArguments = splitTopLevel(normalized.slice(genericStart + 1, -1), ',')
            .map((argument) => formatTypeForDisplay(argument));
        return `${simpleNameOf(rawType)}<${genericArguments.join(', ')}>`;
    }
    return simpleNameOf(normalized);
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
            continue;
        }
        if (char === '>') {
            depth = Math.max(depth - 1, 0);
        }
    }
    return -1;
}
