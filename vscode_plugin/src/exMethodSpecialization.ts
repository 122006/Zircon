import { eraseType, ExMethodIndex, normalizeType, splitTopLevel } from './exMethodIndex';
import { ExMethodDescriptor, JavaParameterInfo } from './exMethodModel';
import { getVisibleParameters } from './exMethodPresentation';

export interface SpecializedExMethodDescriptor {
    receiverType?: string;
    visibleParameters: JavaParameterInfo[];
    returnType: string;
}

export function specializeExMethodDescriptor(
    index: ExMethodIndex,
    descriptor: ExMethodDescriptor,
    receiverTypesInput: readonly string[] | string | undefined
): SpecializedExMethodDescriptor {
    const receiverTypes = normalizeReceiverTypes(receiverTypesInput);
    const visibleParameters = getVisibleParameters(descriptor);
    const typeParameters = parseMethodTypeParameters(descriptor.signature);
    if (descriptor.isStaticExtension || typeParameters.size === 0 || descriptor.parameters.length === 0 || receiverTypes.length === 0) {
        return {
            receiverType: receiverTypes[0],
            visibleParameters,
            returnType: descriptor.returnType
        };
    }

    const receiverType = pickReceiverType(index, descriptor, receiverTypes);
    if (!receiverType) {
        return {
            receiverType: receiverTypes[0],
            visibleParameters,
            returnType: descriptor.returnType
        };
    }

    const substitutions = buildReceiverTypeSubstitutions(
        descriptor.parameters[0].type,
        receiverType,
        typeParameters,
        index
    );
    if (substitutions.size === 0) {
        return {
            receiverType,
            visibleParameters,
            returnType: descriptor.returnType
        };
    }

    return {
        receiverType,
        visibleParameters: visibleParameters.map((parameter) => ({
            ...parameter,
            type: substituteTypeVariables(parameter.type, substitutions, typeParameters)
        })),
        returnType: substituteTypeVariables(descriptor.returnType, substitutions, typeParameters)
    };
}

function normalizeReceiverTypes(receiverTypesInput: readonly string[] | string | undefined): string[] {
    if (!receiverTypesInput) {
        return [];
    }
    const values = Array.isArray(receiverTypesInput) ? receiverTypesInput : [receiverTypesInput];
    const normalized = new Set<string>();
    for (const value of values) {
        const typeName = normalizeType(value);
        if (typeName.length > 0) {
            normalized.add(typeName);
        }
    }
    return [...normalized];
}

function parseMethodTypeParameters(signature: string): Set<string> {
    const match = signature.match(/^(?:public|protected|private|static|final|synchronized|abstract|native|default|strictfp|\s)*(?:(<.*?>)\s+)?/);
    const typeParameterBlock = match?.[1];
    if (!typeParameterBlock) {
        return new Set<string>();
    }
    return new Set(
        splitTopLevel(typeParameterBlock.trim().replace(/^</, '').replace(/>$/, ''), ',')
            .map((item) => normalizeType(item))
            .map((item) => item.match(/^([A-Za-z_$][\w$]*)/)?.[1] ?? '')
            .filter((item) => item.length > 0)
    );
}

function pickReceiverType(index: ExMethodIndex, descriptor: ExMethodDescriptor, receiverTypes: readonly string[]): string | undefined {
    const compatible = receiverTypes.filter((receiverType) => descriptor.targetTypes.some((targetType) => index.isTypeAssignable(receiverType, targetType)));
    const candidates = compatible.length > 0 ? compatible : receiverTypes;
    const formalReceiverType = normalizeType(descriptor.parameters[0]?.type ?? '');
    const formalRawType = eraseType(formalReceiverType);
    return [...candidates].sort((left, right) => receiverSpecificityScore(right, formalRawType) - receiverSpecificityScore(left, formalRawType))[0];
}

function receiverSpecificityScore(receiverType: string, formalRawType: string): number {
    const receiverRawType = eraseType(receiverType);
    if (receiverRawType === formalRawType) {
        return 3;
    }
    if (simpleTypeName(receiverRawType) === simpleTypeName(formalRawType)) {
        return 2;
    }
    if (receiverType.includes('<')) {
        return 1;
    }
    return 0;
}

function buildReceiverTypeSubstitutions(
    formalReceiverType: string,
    actualReceiverType: string,
    typeParameters: Set<string>,
    index: ExMethodIndex
): Map<string, string> {
    const substitutions = new Map<string, string>();
    collectTypeVariableMappings(
        normalizeType(formalReceiverType),
        normalizeType(actualReceiverType),
        typeParameters,
        index,
        substitutions
    );
    return substitutions;
}

function collectTypeVariableMappings(
    formalType: string,
    actualType: string,
    typeParameters: Set<string>,
    index: ExMethodIndex,
    substitutions: Map<string, string>
): void {
    if (formalType.length === 0 || actualType.length === 0) {
        return;
    }
    if (typeParameters.has(formalType)) {
        mergeTypeVariableMapping(formalType, actualType, substitutions);
        return;
    }

    if (formalType.endsWith('...')) {
        const actualElementType = actualType.endsWith('...')
            ? actualType.slice(0, -3)
            : actualType.endsWith('[]')
                ? actualType.slice(0, -2)
                : actualType;
        collectTypeVariableMappings(formalType.slice(0, -3), actualElementType, typeParameters, index, substitutions);
        return;
    }
    if (formalType.endsWith('[]') && actualType.endsWith('[]')) {
        collectTypeVariableMappings(formalType.slice(0, -2), actualType.slice(0, -2), typeParameters, index, substitutions);
        return;
    }
    if (formalType.startsWith('? extends ')) {
        const actualBound = actualType.startsWith('? extends ') ? actualType.slice('? extends '.length) : actualType;
        collectTypeVariableMappings(formalType.slice('? extends '.length), actualBound, typeParameters, index, substitutions);
        return;
    }
    if (formalType.startsWith('? super ')) {
        const actualBound = actualType.startsWith('? super ') ? actualType.slice('? super '.length) : actualType;
        collectTypeVariableMappings(formalType.slice('? super '.length), actualBound, typeParameters, index, substitutions);
        return;
    }

    const formalParameterized = parseParameterizedType(formalType);
    const actualParameterized = parseParameterizedType(actualType);
    if (formalParameterized && actualParameterized
        && formalParameterized.arguments.length === actualParameterized.arguments.length
        && parameterizedTypesAreCompatible(index, actualParameterized.rawType, formalParameterized.rawType)) {
        for (let indexOffset = 0; indexOffset < formalParameterized.arguments.length; indexOffset++) {
            collectTypeVariableMappings(
                formalParameterized.arguments[indexOffset],
                actualParameterized.arguments[indexOffset],
                typeParameters,
                index,
                substitutions
            );
        }
    }
}

function mergeTypeVariableMapping(typeParameter: string, actualType: string, substitutions: Map<string, string>): void {
    const normalizedActualType = normalizeType(actualType);
    if (normalizedActualType.length === 0) {
        return;
    }
    const existing = substitutions.get(typeParameter);
    if (!existing || existing === normalizedActualType) {
        substitutions.set(typeParameter, normalizedActualType);
    }
}

function substituteTypeVariables(
    typeText: string,
    substitutions: Map<string, string>,
    typeParameters: Set<string>
): string {
    const normalizedType = normalizeType(typeText);
    if (normalizedType.length === 0) {
        return normalizedType;
    }
    if (typeParameters.has(normalizedType)) {
        return substitutions.get(normalizedType) ?? normalizedType;
    }
    if (normalizedType.endsWith('...')) {
        return `${substituteTypeVariables(normalizedType.slice(0, -3), substitutions, typeParameters)}...`;
    }
    if (normalizedType.endsWith('[]')) {
        return `${substituteTypeVariables(normalizedType.slice(0, -2), substitutions, typeParameters)}[]`;
    }
    if (normalizedType.startsWith('? extends ')) {
        return `? extends ${substituteTypeVariables(normalizedType.slice('? extends '.length), substitutions, typeParameters)}`;
    }
    if (normalizedType.startsWith('? super ')) {
        return `? super ${substituteTypeVariables(normalizedType.slice('? super '.length), substitutions, typeParameters)}`;
    }

    const parameterized = parseParameterizedType(normalizedType);
    if (!parameterized) {
        return normalizedType;
    }
    const substitutedArguments = parameterized.arguments
        .map((argument) => substituteTypeVariables(argument, substitutions, typeParameters));
    return `${parameterized.rawType}<${substitutedArguments.join(', ')}>`;
}

function parseParameterizedType(typeName: string): { rawType: string; arguments: string[] } | undefined {
    const genericStart = findTopLevelGenericStart(typeName);
    if (genericStart <= 0 || !typeName.endsWith('>')) {
        return undefined;
    }
    return {
        rawType: normalizeType(typeName.slice(0, genericStart)),
        arguments: splitTopLevel(typeName.slice(genericStart + 1, -1), ',')
            .map((argument) => normalizeType(argument))
            .filter((argument) => argument.length > 0)
    };
}

function parameterizedTypesAreCompatible(index: ExMethodIndex, actualRawType: string, formalRawType: string): boolean {
    const normalizedActualRawType = normalizeType(actualRawType);
    const normalizedFormalRawType = normalizeType(formalRawType);
    return normalizedActualRawType === normalizedFormalRawType
        || simpleTypeName(normalizedActualRawType) === simpleTypeName(normalizedFormalRawType)
        || index.isTypeAssignable(normalizedActualRawType, normalizedFormalRawType);
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

function simpleTypeName(typeName: string): string {
    const normalized = normalizeType(typeName);
    const base = eraseType(normalized).replace(/\[]$/, '');
    return base.slice(base.lastIndexOf('.') + 1);
}
