const assert = require('assert');
const {
    applyConversions,
    collectToJavaChanges,
    collectToTemplateChanges,
    findTemplateAtOffset,
    findTemplateLiterals
} = require('../out/stringConversions');
const { splitTemplateString, TEMPLATE_CODE, TEMPLATE_FORMAT } = require('../out/templateStringSplitter');

function toTemplates(source) {
    return applyConversions(source, collectToTemplateChanges(source));
}

function toJava(source) {
    return applyConversions(source, collectToJavaChanges(source));
}

assert.strictEqual(
    toTemplates('String value = "hello " + name + "!";'),
    'String value = $"hello ${name}!";'
);
assert.strictEqual(
    toTemplates('String value = String.format("x=%d, y=%04.2f%%", x, y);'),
    'String value = f"x=${x}, y=${%04.2f:y}%";'
);
assert.strictEqual(
    toTemplates('String value = "hello ${name}";'),
    'String value = $"hello ${name}";'
);
assert.strictEqual(
    toJava('String value = $"hello ${user.name}!";'),
    'String value = ("hello " + (user.name) + "!");'
);
assert.strictEqual(
    toJava('String value = f"amount=${%04.2f:amount}";'),
    'String value = String.format("amount=%04.2f", amount);'
);

const nested = '$"value=${map.get("key")}";';
const templates = findTemplateLiterals(nested);
assert.strictEqual(templates.length, 1);
assert.strictEqual(templates[0].end, nested.length - 1);
assert.ok(templates[0].ranges.some((range) => range.style === TEMPLATE_CODE));
assert.ok(findTemplateAtOffset(nested, nested.indexOf('map.get') + 2));

const afterBroken = '$"broken ${value.\nString ok = $"still ${works}";';
const recoveredTemplates = findTemplateLiterals(afterBroken);
assert.strictEqual(recoveredTemplates.length, 2);
assert.strictEqual(recoveredTemplates[0].closed, false);
assert.strictEqual(recoveredTemplates[1].closed, true);

const formatted = splitTemplateString('f"${%04.2f:value}";', 'f', 'format');
assert.ok(formatted.ranges.some((range) => range.style === TEMPLATE_FORMAT));
assert.ok(formatted.ranges.some((range) => range.style === TEMPLATE_CODE));

console.log('Zircon editor feature validation passed.');
