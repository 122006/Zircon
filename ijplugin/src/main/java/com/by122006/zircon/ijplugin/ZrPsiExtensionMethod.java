package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.lang.Language;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignature;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@SuppressWarnings("UnstableApiUsage")
class ZrPsiExtensionMethod extends LightMethodBuilder implements PsiExtensionMethod {
    boolean isStatic;
    PsiClass targetClass;
    PsiMethod targetMethod;
    @Nullable PsiSubstitutor sitePsiSubstitutor;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ZrPsiExtensionMethod that = (ZrPsiExtensionMethod) o;

        if (isStatic != that.isStatic) return false;
        if (targetClass != null ? !targetClass.equals(that.targetClass) : that.targetClass != null) return false;
        return targetMethod.equals(that.targetMethod);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (isStatic ? 1 : 0);
        result = 31 * result + (targetClass != null ? targetClass.hashCode() : 0);
        result = 31 * result + targetMethod.hashCode();
        return result;
    }

    public ZrPsiExtensionMethod(boolean isStatic, PsiClass targetClass, PsiMethod targetMethod, PsiManager manager, Language language, @NlsSafe @NotNull String name, PsiParameterList parameterList, PsiModifierList modifierList, PsiReferenceList throwsList, PsiTypeParameterList typeParameterList) {
        super(manager, language, name, parameterList, modifierList, throwsList, typeParameterList);
        this.isStatic = isStatic;
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
    }

    @Override
    public @NotNull MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
        if (sitePsiSubstitutor != null) substitutor = substitutor.putAll(sitePsiSubstitutor);
        return super.getSignature(substitutor);
    }

    @Override
    public @NotNull PsiElement getNavigationElement() {
        return this;
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(targetClass.getManager().getProject());
        return new PsiReference[]{getReference(), elementFactory.createClassReferenceElement(targetClass)};
    }

    @Override
    public PsiReference getReference() {
        final PsiClass containingClass = targetMethod.getContainingClass();
        if (containingClass == null) return null;
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(targetClass.getManager()
                .getProject());
        return elementFactory.createClassReferenceElement(containingClass);
    }

    @Override
    public @NotNull GlobalSearchScope getResolveScope() {
        return super.getResolveScope();
    }


    @Override
    public void accept(@NotNull PsiElementVisitor visitor) {
        super.accept(visitor);
        if (visitor instanceof HighlightVisitorImpl) {
            HighlightVisitorImpl highlightVisitor = (HighlightVisitorImpl) visitor;
            try {
                final Field myRefCountHolder = highlightVisitor.getClass().getDeclaredField("myRefCountHolder");
                myRefCountHolder.setAccessible(true);
                final Object refCountHolder = myRefCountHolder.get(highlightVisitor);
                if (refCountHolder != null) {
                    final Method registerImportStatement = refCountHolder.getClass()
                            .getDeclaredMethod("registerImportStatement", PsiReference.class, PsiImportStatementBase.class);
                    final PsiReference reference = getReference();
                    final PsiClass containingClass = targetMethod.getContainingClass();
                    if (containingClass == null) return;
                    final PsiImportStatement importStatement = PsiElementFactory.getInstance(getProject())
                            .createImportStatement(containingClass);
                    registerImportStatement.invoke(registerImportStatement, reference, importStatement);
                }
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public @NotNull PsiMethod getTargetMethod() {
        return targetMethod;
    }

    @Override
    public @Nullable PsiParameter getTargetReceiverParameter() {
        return isStatic ? null : targetMethod.getParameterList().getParameter(0);
    }

    @Override
    public @Nullable PsiParameter getTargetParameter(int index) {
        return targetMethod.getParameterList().getParameter(isStatic ? index : (index + 1));
    }

//    public JvmAnnotation @NotNull [] getAnnotations(){
//        return super.getAnnotations();
//    }
}
