const assert = require('assert');
const Module = require('module');

const originalLoad = Module._load;
Module._load = function patchedLoad(request, parent, isMain) {
    if (request === 'vscode') {
        return {};
    }
    return originalLoad.call(this, request, parent, isMain);
};

try {
    const { isDocumentInZirconProject } = require('../out/projectDetector');
    const baseInfo = {
        hasWorkspace: true,
        hasJavaFiles: true,
        hasZirconMarkers: true,
        markers: ['JDT classpath: zircon.ExMethod'],
        javaFileCount: 2,
        buildFiles: [],
        zirconProjectRoots: ['D:\\workspace\\zircon-module'],
        classpathProjectCount: 1,
        classpathDetectionReady: true
    };
    const document = (fileName) => ({
        languageId: 'java',
        uri: { scheme: 'file', fsPath: fileName }
    });

    assert.strictEqual(
        isDocumentInZirconProject(document('D:\\workspace\\zircon-module\\src\\Main.java'), baseInfo),
        true
    );
    assert.strictEqual(
        isDocumentInZirconProject(document('D:\\workspace\\plain-module\\src\\Main.java'), baseInfo),
        false
    );
    assert.strictEqual(
        isDocumentInZirconProject(document('D:\\workspace\\plain-module\\src\\Main.java'), {
            ...baseInfo,
            zirconProjectRoots: [],
            classpathProjectCount: 0,
            classpathDetectionReady: false
        }),
        true,
        'text markers should remain a startup fallback before JDT exposes project classpaths'
    );
    assert.strictEqual(
        isDocumentInZirconProject(document('D:\\workspace\\plain-module\\src\\Main.java'), {
            ...baseInfo,
            zirconProjectRoots: [],
            classpathProjectCount: 0,
            classpathDetectionReady: true
        }),
        false,
        'an authoritative JDT classpath without Zircon must disable the module'
    );
    assert.strictEqual(
        isDocumentInZirconProject(document('D:\\workspace\\zircon-module-other\\Main.java'), baseInfo),
        false,
        'module prefix must not match a sibling directory'
    );
    console.log('Zircon per-project detection validation passed.');
} finally {
    Module._load = originalLoad;
}
