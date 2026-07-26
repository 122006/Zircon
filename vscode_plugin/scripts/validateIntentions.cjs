const assert = require('assert');

const {
    extensionToNormalInvocation,
    normalToExtensionInvocation,
    splitInvocationArguments
} = require('../out/exMethodConversions');

const descriptor = {
    qualifiedDeclaringClass: 'demo.CollectionExtensions',
    declaringClass: 'CollectionExtensions',
    methodName: 'map',
    isStaticExtension: false,
    targetTypes: ['java.util.List']
};
const invocation = {
    receiverExpression: 'values',
    receiverStart: 0,
    methodName: 'map',
    methodStart: 7,
    methodEnd: 10,
    argumentsText: 'value -> pair(value, ",")',
    callStart: 0,
    callEnd: 34
};

assert.strictEqual(
    extensionToNormalInvocation(invocation, descriptor),
    'demo.CollectionExtensions.map(values, value -> pair(value, ","))'
);
assert.strictEqual(
    normalToExtensionInvocation('values, value -> pair(value, ",")', descriptor),
    'values.map(value -> pair(value, ","))'
);
assert.deepStrictEqual(
    splitInvocationArguments('values, pair(1, 2), "a,b"'),
    ['values', 'pair(1, 2)', '"a,b"']
);

const staticDescriptor = {
    ...descriptor,
    methodName: 'create',
    isStaticExtension: true,
    targetTypes: ['java.util.List']
};
assert.strictEqual(
    normalToExtensionInvocation('1, 2, 3', staticDescriptor),
    'List.create(1, 2, 3)'
);

console.log('Zircon extension-method intention validation passed.');
