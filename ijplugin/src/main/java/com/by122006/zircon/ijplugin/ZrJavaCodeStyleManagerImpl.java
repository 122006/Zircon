package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrPluginUtil;
import com.by122006.zircon.util.ZrUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.JavaCodeStyleManagerImpl;
import com.sun.tools.javac.parser.Formatter;
import com.sun.tools.javac.parser.ZrStringModel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class ZrJavaCodeStyleManagerImpl extends JavaCodeStyleManagerImpl {
    private static final Logger LOG = Logger.getInstance(ZrJavaCodeStyleManagerImpl.class);

    public ZrJavaCodeStyleManagerImpl(Project project) {
        super(project);
    }

    @Override
    public PsiImportList prepareOptimizeImportsResult(@NotNull PsiJavaFile file) {
        if (!ZrPluginUtil.hasZrPlugin(file.getProject())) return super.prepareOptimizeImportsResult(file);
        return new ZrImportHelper(JavaCodeStyleSettings.getInstance(file)).prepareOptimizeImportsResult(file);
    }

    @Override
    @Nullable
    public Collection<PsiImportStatementBase> findRedundantImports(@NotNull final PsiJavaFile file) {
        final Collection<PsiImportStatementBase> redundant = super.findRedundantImports(file);
        if (redundant == null) return null;
        final List<PsiFile> roots = file.getViewProvider().getAllFiles();
        for (PsiElement root : roots) {
            root.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitMethodCallExpression(PsiMethodCallExpression element) {
                    super.visitMethodCallExpression(element);
                    final PsiMethod method = element.resolveMethod();
                    if (method instanceof ZrPsiAugmentProvider.ZrPsiExtensionMethod) {
                        final ZrPsiAugmentProvider.ZrPsiExtensionMethod zrMethod = (ZrPsiAugmentProvider.ZrPsiExtensionMethod) method;
                        final PsiClass containingClass = zrMethod.getTargetMethod().getContainingClass();
                        if (containingClass != null && !inTheSamePackage(file, containingClass)) {
                            redundant.removeIf(a -> {
                                final PsiJavaCodeReferenceElement importReference = a.getImportReference();
                                return importReference != null && Objects.equals(containingClass.getQualifiedName(), importReference.getQualifiedName());
                            });
                        }
                    }
                }

                @Override
                public void visitMethodReferenceExpression(PsiMethodReferenceExpression element) {
                    super.visitMethodReferenceExpression(element);
                }

                @Override
                public void visitIdentifier(PsiIdentifier identifier) {
                    super.visitIdentifier(identifier);
                }

                @Override
                public void visitLiteralExpression(PsiLiteralExpression expression) {
                    super.visitLiteralExpression(expression);
                    final Formatter formatter = ZrUtil.checkPsiLiteralExpression(expression);
                    if (formatter == null) return;
                    final ZrStringModel build = formatter.build(expression.getText());
                    build.getList().stream().filter(a -> a.codeStyle == 1)
                         .map(a -> JavaPsiFacade.getElementFactory(expression.getProject())
                                                .createExpressionFromText(a.stringVal.trim(), expression))
                         .filter(a -> a instanceof PsiMethodCallExpression)
                         .forEach(exp -> visitMethodCallExpression((PsiMethodCallExpression) exp));
                }

                private boolean inTheSamePackage(PsiJavaFile file, PsiElement element) {
                    if (element instanceof PsiClass) {
                        final PsiFile containingFile = element.getContainingFile();
                        return containingFile instanceof PsiClassOwner && Comparing.strEqual(file.getPackageName(), ((PsiClassOwner) containingFile).getPackageName());
                    }
                    return false;
                }
            });
        }
        return redundant;
    }

}
