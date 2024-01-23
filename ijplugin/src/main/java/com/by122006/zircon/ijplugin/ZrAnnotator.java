package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrPluginUtil;
import com.by122006.zircon.util.ZrUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.formatting.service.FormattingServiceUtil;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.java.PsiMethodReferenceExpressionImpl;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.FormatUtils;
import com.siyeh.ig.psiutils.ImportUtils;
import com.sun.tools.javac.parser.FStringFormatter;
import com.sun.tools.javac.parser.Formatter;
import com.sun.tools.javac.parser.SStringFormatter;
import com.sun.tools.javac.parser.StringRange;
import com.sun.tools.javac.parser.ZrStringModel;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Font;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import zircon.ExMethod;


public class ZrAnnotator implements Annotator {
    private static final Logger LOG = Logger.getInstance(ZrAnnotator.class.getName());

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        try {
            if (!ZrPluginUtil.hasZrPlugin(element.getProject())) return;
            if (element.getLanguage() != JavaLanguage.INSTANCE) return;
            if (element instanceof PsiMethodReferenceExpression) {
                registerLimitMemberReference(element, holder);
                return;
            }
            if (element instanceof PsiPolyadicExpression && Arrays.stream(element.getChildren())
                                                                  .anyMatch(ZrUtil::isJavaStringLiteral)) {
                registerChange2SStringIntentionAction((PsiPolyadicExpression) element, holder);
                return;
            }
            if (ZrUtil.isJavaStringLiteral(element)) {
                registerBackfStringIntentionAction(element, holder);
                return;
            }
            if (element instanceof PsiMethod) {
                registerCheckZrExMethod((PsiMethod) element, holder);
                return;
            }
            if (element instanceof PsiMethodCallExpression) {
                if (FormatUtils.isFormatCall((PsiMethodCallExpression) element)) {
                    registerChangeFromFormatIntentionAction((PsiMethodCallExpression) element, holder);
                }
                final PsiMethod method = ((PsiMethodCallExpression) element).resolveMethod();
                if (method instanceof ZrPsiExtensionMethod) {
                    registerZrMethodUsage((PsiMethodCallExpression) element, (ZrPsiExtensionMethod) method, holder);
                }
                return;
            }
        } catch (ProcessCanceledException e) {
            throw e;
        }
    }

    private void registerZrMethodUsage(@NotNull PsiMethodCallExpression element, ZrPsiExtensionMethod method, @NotNull AnnotationHolder holder) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        final String collect = Arrays.stream(parameters).map(psiParameter -> psiParameter.getType().getCanonicalText())
                                     .collect(Collectors.joining(" , "));
        final PsiClass containingClass = method.targetMethod.getContainingClass();
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element.getMethodExpression().getLastChild())
              .textAttributes(ZrExMethodTargetSiteUsage)
              .tooltip("extension method: " + method.targetClass.getName() + "." + method.getName() + "( " + collect + " )")
              .withFix(new IntentionAction() {
                  @Override
                  public @IntentionName @NotNull String getText() {
                      return "[ZrExMethod]: replace with normal method";
                  }

                  @Override
                  public @NotNull @IntentionFamilyName String getFamilyName() {
                      return "ZrExMethod";
                  }

                  @Override
                  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
                      return true;
                  }

                  @Override
                  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
                      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
                      String s;
                      if (containingClass == null) return;
                      final String collect = Arrays.stream(element.getArgumentList().getExpressions())
                                                   .map(PsiElement::getText).collect(Collectors.joining(","));
                      if (method.isStatic) {
                          s = containingClass.getQualifiedName() + "." + method.getName() + "(" + collect + ")";
                      } else {
                          final PsiElement site = element.getMethodExpression().getFirstChild();
                          final String siteText = site.getText();
                          if (collect.isEmpty()) {
                              s = containingClass.getQualifiedName() + "." + method.getName() + "(" + siteText + ")";
                          } else {
                              s = containingClass.getQualifiedName() + "." + method.getName() + "(" + (siteText.isEmpty() ? "this" : siteText) + "," + collect + ")";
                          }
                      }
                      @NotNull PsiExpression codeBlockFromText = elementFactory.createExpressionFromText(s, element);
                      element.replace(codeBlockFromText);
                  }

                  @Override
                  public boolean startInWriteAction() {
                      return true;
                  }
              }).create();
        if (containingClass != null) {
            final String qualifiedName = containingClass.getQualifiedName();
            final PsiFile originalFile = element.getContainingFile().getOriginalFile();
            final boolean canBeImported = ImportUtils.nameCanBeImported(qualifiedName, originalFile) && canImport(containingClass, originalFile);
            if (canBeImported) {
                holder.newSilentAnnotation(HighlightSeverity.ERROR).range(element.getMethodExpression().getLastChild())
                      .textAttributes(ZrExMethodNeedImport).tooltip("extension method need import " + qualifiedName)
                      .withFix(new IntentionAction() {
                          @Override
                          public @IntentionName @NotNull String getText() {
                              return "[ZrExMethod]: import " + qualifiedName;
                          }

                          @Override
                          public @NotNull @IntentionFamilyName String getFamilyName() {
                              return "ZrExMethod";
                          }

                          @Override
                          public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
                              return true;
                          }

                          @Override
                          public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
                              if (!FileModificationService.getInstance().prepareFileForWrite(psiFile)) return;
//                            ApplicationManager.getApplication().runWriteAction(() -> {
                              if (!(editor instanceof EditorEx)) {
                                  ImportUtils.addImportIfNeeded(containingClass, originalFile);
                              } else {
                                  final VirtualFile virtualFile = ((EditorEx) editor).getVirtualFile();
                                  final PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
                                  if (file != null) {
                                      ImportUtils.addImportIfNeeded(containingClass, file);
                                  }
                                  FormattingServiceUtil.formatElement(element, false);
                              }
//                            });
                          }

                          @Override
                          public boolean startInWriteAction() {
                              return true;
                          }
                      }).create();
            }
        }
    }

    public static boolean isInsideClassBody(@NotNull PsiElement element, @Nullable PsiClass outerClass) {
        final PsiElement brace = outerClass != null ? outerClass.getLBrace() : null;
        return brace != null && brace.getTextOffset() < element.getTextOffset();
    }

    public static boolean canImport(@NotNull PsiClass aClass, @NotNull PsiElement context) {
        final PsiFile file = context.getContainingFile();
        if (!(file instanceof PsiJavaFile)) {
            return false;
        }
        final PsiJavaFile javaFile = (PsiJavaFile) file;
        final PsiClass outerClass = aClass.getContainingClass();
        if (outerClass == null) {
            if (PsiTreeUtil.isAncestor(javaFile, aClass, true)) {
                return false;
            }
        } else {
            if (PsiTreeUtil.isAncestor(outerClass, context, true) && isInsideClassBody(context, outerClass))
                return false;
        }
        final String qualifiedName = aClass.getQualifiedName();
        if (qualifiedName == null) {
            return false;
        }
        final PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return false;
        }
        final String containingPackageName = javaFile.getPackageName();
        @NonNls final String packageName = ClassUtil.extractPackageName(qualifiedName);
        if (CommonClassNames.DEFAULT_PACKAGE.equals(packageName)) {
            return false;
        }
        if (containingPackageName.equals(packageName) || importList.findSingleClassImportStatement(qualifiedName) != null) {
            return false;
        }
        if (importList.findOnDemandImportStatement(packageName) != null && !ImportUtils.hasOnDemandImportConflict(qualifiedName, javaFile)) {
            return false;
        }
        return true;
    }

    private void registerCheckZrExMethod(@NotNull PsiMethod method, @NotNull AnnotationHolder holder) {
        if (!method.isValid()) return;
        final PsiAnnotation[] annotations = method.getAnnotations();
        final List<PsiAnnotation> collect = Arrays.stream(annotations)
                                                  .filter(a -> Objects.equals(a.getQualifiedName(), ExMethod.class.getName()))
                                                  .collect(Collectors.toList());
        if (collect.isEmpty()) return;
        if (collect.size() > 1) {
            collect.stream().skip(1).forEach(annotation -> {
                holder.newAnnotation(HighlightSeverity.WARNING, "Duplicate annotation").range(annotation)
                      .highlightType(ProblemHighlightType.WARNING).create();
            });
            return;
        }
        if (!method.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
            holder.newAnnotation(HighlightSeverity.ERROR, "must static method").range(method.getModifierList())
                  .highlightType(ProblemHighlightType.ERROR).withFix(new IntentionAction() {
                      @Override
                      public @IntentionName @NotNull String getText() {
                          return "[ZrExMethod]: set static";
                      }

                      @Override
                      public @NotNull @IntentionFamilyName String getFamilyName() {
                          return "ZrExMethod";
                      }

                      @Override
                      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
                          return true;
                      }

                      @Override
                      public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
                          method.getModifierList().setModifierProperty(PsiModifier.STATIC, true);

                      }

                      @Override
                      public boolean startInWriteAction() {
                          return true;
                      }
                  }).create();
        }
        final boolean b = ZrPsiAugmentProvider.getCachedAllMethod(method.getProject()).stream()
                                              .noneMatch(a -> a.method.isValid() && a.method.isEquivalentTo(method));
        if (b) {
            holder.newSilentAnnotation(HighlightSeverity.WARNING).range(method).tooltip("need refresh cache")
                  .highlightType(ProblemHighlightType.WARNING).withFix(new IntentionAction() {
                      @Override
                      public @IntentionName @NotNull String getText() {
                          return "[ZrExMethod]: refresh cache";
                      }

                      @Override
                      public @NotNull @IntentionFamilyName String getFamilyName() {
                          return "ZrExMethod";
                      }

                      @Override
                      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
                          return true;
                      }

                      @Override
                      public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
                          try {
                              method.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
                              ZrPsiAugmentProvider.freshCachedAllMethod(project);
                              FormattingServiceUtil.formatElement(method.getContainingFile(), false);
                          } catch (Exception e) {
                              e.printStackTrace();
                          }
                      }

                      @Override
                      public boolean startInWriteAction() {
                          return true;
                      }
                  }).create();
        }
        final PsiAnnotation annotation = annotations[0];
        PsiAnnotationMemberValue ex = annotation.findDeclaredAttributeValue("ex");
        if (ex != null) {
            if (ex instanceof PsiClassObjectAccessExpression) {
                registerSiteAnnotation(holder, ((PsiClassObjectAccessExpression) ex).getOperand(), null, null);
            } else if (ex instanceof PsiArrayInitializerMemberValue) {
                final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue) ex).getInitializers();
                List<PsiType> types = Arrays.stream(initializers).map(a -> {
                    final PsiTypeElement childOfType = PsiTreeUtil.getChildOfType(a, PsiTypeElement.class);
                    if (childOfType == null) return null;
                    registerSiteAnnotation(holder, childOfType, null, null);
                    return childOfType.getType();
                }).filter(Objects::nonNull).collect(Collectors.toList());
                if (types.isEmpty()) ex = null;
            }
        }
        if (ex == null) {
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList.isEmpty()) {
                holder.newSilentAnnotation(HighlightSeverity.WARNING).range(method).tooltip("!非静态拓展方法需要设置首个入参作为代理类")
                      .highlightType(ProblemHighlightType.WARNING).create();
            } else {
                final PsiParameter parameter = parameterList.getParameter(0);
                if (parameter != null && parameter.isValid()) {
                    final PsiTypeElement typeElement = parameter.getTypeElement();
                    if (typeElement != null) {
                        final PsiType type = parameter.getType();
                        if (type instanceof PsiArrayType) {
                            final PsiType deepComponentType = type.getDeepComponentType();
                            registerSiteAnnotation(holder, typeElement, deepComponentType.getCanonicalText() + "[]", null);
                        } else if (type instanceof PsiPrimitiveType) {
                            final PsiClassType boxedType = ((PsiPrimitiveType) type).getBoxedType(parameter);
                            registerSiteAnnotation(holder, typeElement, boxedType == null ? "unknown" : boxedType.getClassName(), null);
                        } else if (parameter.isVarArgs()) {
                            holder.newSilentAnnotation(HighlightSeverity.ERROR).range(typeElement)
                                  .tooltip("!请不要定义代理类为可变参数").highlightType(ProblemHighlightType.ERROR).create();
                        } else {
                            registerSiteAnnotation(holder, typeElement, null, null);
                        }

                    }
                }
            }
        }

    }

    private void registerSiteAnnotation(@NotNull AnnotationHolder holder, @NotNull PsiTypeElement element, @Nullable String className, @Nullable TextRange textRange) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
              .range(textRange == null ? element.getTextRange() : textRange)
              .tooltip("This extension method will proxy the site: " + (className != null ? className : TypeConversionUtil
                      .erasure(element.getType()).getCanonicalText())).textAttributes(ZrExMethodSite)
              .highlightType(ProblemHighlightType.INFORMATION).create();
    }

    private void registerBackfStringIntentionAction(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        final String text = element.getText();
        if (!text.startsWith("\"")) {
            final Formatter formatter = registerChange2NormalIntentionAction(element, holder, text);
            if (formatter != null) {
                final ZrStringModel model = formatter.build(text);
                String printOut = replace2NormalString(element, holder, text, formatter, model);
                foldCode(element, holder, printOut);
                boldSpecialChar(element, holder, formatter, model);
                model.getList().stream().filter(a -> a.codeStyle == 1).forEach(a -> {
                    final TextRange textRange = TextRange.create(a.startIndex, a.endIndex)
                                                         .shiftRight(element.getTextOffset());
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(textRange)
                          .highlightType(ProblemHighlightType.INFORMATION).textAttributes(ZrStringTextCodeStyle1)
                          .create();
                });
                final String previewString = model.getList().stream().map(a -> {
                    if (a.codeStyle == 0) {
                        return model.getOriginalString().substring(a.startIndex, a.endIndex);
                    } else if (a.codeStyle == 1) {
                        return "__";
                    }
                    return "";
                }).collect(Collectors.joining());
                holder.newAnnotation(HighlightSeverity.INFORMATION, previewString).range(element)
                      .highlightType(ProblemHighlightType.INFORMATION).create();
                if (formatter instanceof FStringFormatter) {
                    try {
                        checkFStringError(element, model, holder);
                    } catch (ProcessCanceledException e) {
                        throw e;
                    } catch (Exception e) {
                        LOG.warn(e);
                    }
                }
                if (formatter instanceof SStringFormatter) {
                    try {
                        final ZrStringModel build = new FStringFormatter().build(text);
                        checkSStringChange2FString(element, build, holder);
                    } catch (ProcessCanceledException e) {
                        throw e;
                    } catch (Exception e) {
                        LOG.warn(e);
                    }
                }
            }

        } else {
            checkNeedChange2SString(element, holder, text);
        }
    }

    private boolean registerLimitMemberReference(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        final PsiMethodReferenceExpressionImpl expression = (PsiMethodReferenceExpressionImpl) element.getReference();
        if (expression == null) return true;
        if ((((PsiMethodReferenceExpression) expression).getQualifier() instanceof PsiReferenceExpression)) {
            return true;
        }
        final PsiReference reference = expression.getReference();
        if (reference == null) return true;
        final PsiElement resolve;
        try {
            resolve = reference.resolve();
        } catch (ProcessCanceledException e) {
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
//        if (resolve instanceof ZrPsiAugmentProvider.ZrPsiExtensionMethod
//                && !((ZrPsiAugmentProvider.ZrPsiExtensionMethod) resolve).isStatic) {
//            holder.newAnnotation(HighlightSeverity.ERROR, "[ZrString]:暂不支持拓展方法应用于非静态成员方法引用")
//                    .range(element)
//                    .highlightType(ProblemHighlightType.ERROR)
//                    .withFix(new IntentionAction() {
//                        @Override
//                        public @IntentionName
//                        @NotNull
//                        String getText() {
//                            return "[ZrExMethod]: replace with lambda tree";
//                        }
//
//                        @Override
//                        public @NotNull
//                        @IntentionFamilyName String getFamilyName() {
//                            return "ZrExMethod";
//                        }
//
//                        @Override
//                        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
//                            return true;
//                        }
//
//                        @Override
//                        public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
//                            PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
//                            final PsiParameterList parameterList = ((ZrPsiAugmentProvider.ZrPsiExtensionMethod) resolve).getParameterList();
//                            final String collect = Arrays.stream(parameterList.getParameters()).map(PsiParameter::getName).collect(Collectors.joining(","));
//                            final String s = "(" + collect + ")->" + element.getText().replace("::", ".") + "(" + collect + ")";
//                            @NotNull PsiExpression codeBlockFromText = elementFactory.createExpressionFromText(s, element);
//                            element.replace(codeBlockFromText);
//                        }
//
//                        @Override
//                        public boolean startInWriteAction() {
//                            return true;
//                        }
//                    })
//                    .create();
//        }
        return false;
    }

    static @NotNull TextAttributesKey ZrExMethodSite = createTextAttributesKey("ZrExMethodSite", new TextAttributes(null, null, new JBColor(0x98C1CB, 0xBBEBF6), EffectType.BOLD_LINE_UNDERSCORE, Font.PLAIN), null);
    static @NotNull TextAttributesKey ZrExMethodTargetSiteUsage = createTextAttributesKey("ZrExMethodTargetSiteUsage", new TextAttributes(null, null, null, null, Font.ITALIC), null);
    static @NotNull TextAttributesKey ZrExMethodNeedImport = createTextAttributesKey("ZrExMethodNeedImport", new TextAttributes(new JBColor(0xEAFF4538, 0xEAFF4538), null, new JBColor(0xEAFF4538, 0xEAFF4538), EffectType.LINE_UNDERSCORE, Font.ITALIC), null);
    static @NotNull TextAttributesKey ZrStringTextCodeStyleP1 = createTextAttributesKey("ZrStringTextCodeStyleP1", new TextAttributes(new JBColor(0x999999, 0x696969), null, null, null, Font.ITALIC), null);
    static @NotNull TextAttributesKey ZrStringTextCodeStyleSplit = createTextAttributesKey("ZrStringTextCodeStyleSplit", new TextAttributes(new JBColor(0xbbbbbb, 0x696969), null, new JBColor(0x999999, 0x696969), EffectType.LINE_UNDERSCORE, Font.PLAIN), null);
    static @NotNull TextAttributesKey ZrStringTextCodeStyle2 = createTextAttributesKey("ZrStringTextCodeStyle2", new TextAttributes(new JBColor(0x00627A, 0x78BDB0), null, new JBColor(0x999999, 0x696969), EffectType.LINE_UNDERSCORE, Font.ITALIC), null);
    static @NotNull TextAttributesKey ZrStringTextCodeStyle1 = createTextAttributesKey("ZrStringTextCodeStyle1", new TextAttributes(null, new JBColor(new Color(255, 255, 255, 0), new Color(0, 0, 0, 0)), new JBColor(0x999999, 0x696969), EffectType.LINE_UNDERSCORE, Font.PLAIN), null);

    private void boldSpecialChar(PsiElement element, AnnotationHolder holder, Formatter formatter, ZrStringModel model) {
        String text = model.getOriginalString();
        {
            final TextRange textRange = TextRange.create(0, text.indexOf("\"")).shiftRight(element.getTextOffset());
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(textRange)
                  .highlightType(ProblemHighlightType.INFORMATION).textAttributes(ZrStringTextCodeStyleP1).create();
        }
        {
            int lastItemEndIndex = text.indexOf("\"") + 1;
            final List<StringRange> list = new ArrayList<>(model.getList());
            list.add(StringRange.of(0, model.getEndQuoteIndex(), model.getEndQuoteIndex()));
            for (StringRange stringRange : list) {
                String sub = text.substring(lastItemEndIndex, stringRange.startIndex).trim();
                {
                    final TextRange textRange = TextRange.create(lastItemEndIndex, stringRange.startIndex)
                                                         .shiftRight(element.getTextOffset());
                    if (formatter instanceof FStringFormatter & sub.equals(":")) {
                        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(textRange)
                              .highlightType(ProblemHighlightType.INFORMATION)
                              .textAttributes(ZrStringTextCodeStyleSplit).create();
                    } else if (sub.length() > 0) {
                        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(textRange)
                              .highlightType(ProblemHighlightType.INFORMATION)
                              .textAttributes(ZrStringTextCodeStyleSplit).create();
                    }
                }
                if (stringRange.codeStyle == 2) {
                    final TextRange textRange = TextRange.create(stringRange.startIndex, stringRange.endIndex)
                                                         .shiftRight(element.getTextOffset());
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(textRange)
                          .textAttributes(ZrStringTextCodeStyle2).create();
                }
                lastItemEndIndex = stringRange.endIndex;
            }

        }
    }

    public static TextAttributesKey createTextAttributesKey(@NotNull String externalName, TextAttributes defaultAttributes, TextAttributesKey fallbackAttributeKey) {
        final Constructor<?> constructor = Arrays.stream(TextAttributesKey.class.getDeclaredConstructors())
                                                 .filter(a -> a.getParameterCount() == 3).findFirst()
                                                 .orElseThrow(() -> new RuntimeException("不支持的idea版本"));
        constructor.setAccessible(true);
        try {
            return (TextAttributesKey) constructor.newInstance(externalName, defaultAttributes, fallbackAttributeKey);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(new RuntimeException("不支持的idea版本"));
        }
    }

    private void checkNeedChange2SString(@NotNull PsiElement element, @NotNull AnnotationHolder holder, String text) {
        if (text.matches(".*\\$\\{.*")) {
            holder.newAnnotation(HighlightSeverity.INFORMATION, "[ZrString]:replace with '$-string'").range(element)
                  .highlightType(ProblemHighlightType.INFORMATION).withFix(new IntentionAction() {
                      @Override
                      public @IntentionName @NotNull String getText() {
                          return "[ZrString]: replace with '$-string'";
                      }

                      @Override
                      public @NotNull @IntentionFamilyName String getFamilyName() {
                          return "ZrString";
                      }

                      @Override
                      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
                          return true;
                      }

                      @Override
                      public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
                          if (editor instanceof EditorEx) {
                              PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
                              final String text = "$" + element.getText();
                              @NotNull PsiExpression codeBlockFromText = elementFactory.createExpressionFromText(text, element);
                              element.replace(codeBlockFromText);
                          }
                      }

                      @Override
                      public boolean startInWriteAction() {
                          return true;
                      }
                  }).create();
        }
    }

    private void checkSStringChange2FString(PsiElement element, ZrStringModel build, AnnotationHolder holder) {
        final boolean change2FString = build.getList().stream().anyMatch(a -> a.codeStyle == 2);
        if (change2FString) {
            holder.newAnnotation(HighlightSeverity.WEAK_WARNING, "[ZrString]:replace with 'F-string'").range(element)
                  .highlightType(ProblemHighlightType.WEAK_WARNING).withFix(new IntentionAction() {
                      @Override
                      public @IntentionName @NotNull String getText() {
                          return "[ZrString]: replace with 'F-string'";
                      }

                      @Override
                      public @NotNull @IntentionFamilyName String getFamilyName() {
                          return "ZrString";
                      }

                      @Override
                      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
                          return true;
                      }

                      @Override
                      public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
                          if (editor instanceof EditorEx) {
                              PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
                              final String text = "f" + element.getText().substring(1);
                              @NotNull PsiExpression codeBlockFromText = elementFactory.createExpressionFromText(text, element);
                              element.replace(codeBlockFromText);
                          }
                      }

                      @Override
                      public boolean startInWriteAction() {
                          return true;
                      }
                  }).create();
        }
    }

    private void checkFStringError(@NotNull PsiElement element, ZrStringModel build, AnnotationHolder holder) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Class<?> aClass;
        try {
            aClass = Class.forName("com.siyeh.ig.bugs.FormatDecode");
        } catch (ClassNotFoundException e) {
            aClass = Class.forName("com.siyeh.ig.FormatDecode");
        }
        final Method decode = aClass.getMethod("decode", String.class, int.class);
        decode.setAccessible(true);
        build.getList().stream().map(a -> {
            switch (a.codeStyle) {
                case -1:
                    break;
                case 0:
                    return a.stringVal;
                case 1:
                    break;
                case 2:
                    return build.getOriginalString().substring(a.startIndex, a.endIndex);
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.joining());
        final List<StringRange> argumentString = new ArrayList<>();
        final List<TextRange> argumentRange = new ArrayList<>();
        List<StringRange> list = build.getList();
        List<StringRange> formatList = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            StringRange stringRange = list.get(i);
            if (stringRange.codeStyle == 0) {
            }
            if (stringRange.codeStyle == 2 && i != list.size() - 1) {
                final StringRange stringRangeNext = list.get(i + 1);
                if (stringRangeNext.codeStyle == 1) {
                    argumentString.add(stringRangeNext);
                    argumentRange.add(TextRange.create(stringRange.startIndex, Math.min(stringRangeNext.endIndex, build.getEndQuoteIndex())));
                    formatList.add(stringRange);
                    i++;
                }
            }
        }
        final int argumentCount = argumentString.size();
        PsiExpression[] arguments = argumentString.stream().map(a -> {
            return JavaPsiFacade.getElementFactory(element.getProject())
                                .createExpressionFromText(build.getOriginalString()
                                                               .substring(a.startIndex, a.endIndex), element);
        }).toArray(PsiExpression[]::new);
        for (int i = 0; i < arguments.length; i++) {
            final StringRange formatRange = formatList.get(i);
            Object invoke = null;
            try {
                invoke = decode.invoke(null, build.getOriginalString()
                                                  .substring(formatRange.startIndex, formatRange.endIndex), argumentCount);
            } catch (InvocationTargetException e) {
                final Throwable targetException = e.getTargetException();
                final TextRange textRange = TextRange.create(formatRange.startIndex, formatRange.endIndex)
                                                     .shiftRight(element.getTextOffset());
                holder.newAnnotation(HighlightSeverity.ERROR, "[ZrString]: " + targetException.getMessage())
                      .range(textRange).highlightType(ProblemHighlightType.ERROR).create();
                continue;
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (invoke == null || !invoke.getClass().isArray() || ((Object[]) invoke).length == 0) {
                final TextRange textRange = TextRange.create(formatRange.startIndex, formatRange.endIndex)
                                                     .shiftRight(element.getTextOffset());
                holder.newAnnotation(HighlightSeverity.ERROR, "[ZrString]: 请输入合法的'String.format()'标识符").range(textRange)
                      .highlightType(ProblemHighlightType.ERROR).create();
                continue;
            }
            final Object validator = ((Object[]) invoke)[0];
            final PsiExpression argument = arguments[i];
            final PsiType argumentType = argument.getType();
            if (argumentType == null) {
                continue;
            }
            BiPredicate<Object, PsiType> valid = (o, psiType) -> {
                try {
                    final Method method = o.getClass().getMethod("valid", PsiType.class);
                    method.setAccessible(true);
                    return (boolean) method.invoke(o, psiType);
                } catch (Exception e) {
//                    final TextRange textRange = TextRange.create(formatRange.startIndex,formatRange.endIndex)
//                            .shiftRight(element.getTextOffset());
//                    holder.newAnnotation(HighlightSeverity.ERROR, "[ZrString]: 请输入合法的'String.format()'标识符")
//                            .range(textRange)
//                            .highlightType(ProblemHighlightType.ERROR)
//                            .create();
                }
                return false;
            };
            if (validator != null && !valid.test(validator, argumentType)) {
                PsiType preciseType = TypeConstraint.fromDfType(CommonDataflow.getDfType(argument))
                                                    .getPsiType(element.getProject());
                if (preciseType == null || !valid.test(validator, preciseType)) {
                    final TextRange textRange = argumentRange.get(i).shiftRight(element.getTextOffset());
                    holder
                            .newAnnotation(HighlightSeverity.ERROR, "[ZrString]: 参数类型不匹配。当前传入:" + argumentType.getCanonicalText())
                            .range(textRange).highlightType(ProblemHighlightType.ERROR).create();
                }
            }
        }
    }

    private void registerChangeFromFormatIntentionAction(@NotNull PsiMethodCallExpression element, @NotNull AnnotationHolder holder) {
        final PsiElement child = element.getChildren()[1];
        if (!(child instanceof PsiExpressionList)) return;
        final PsiExpressionList formatExpression = (PsiExpressionList) child;
        final PsiElement[] children = formatExpression.getChildren();
        if (children.length == 0) return;
        final List<PsiElement> collect = Arrays.stream(children)
                                               .filter(a -> !(a instanceof PsiJavaToken || a instanceof PsiWhiteSpace))
                                               .collect(Collectors.toList());
        if (!ZrUtil.isJavaStringLiteral(collect.get(0))) {
            collect.remove(0);
        }
        if (!ZrUtil.isJavaStringLiteral(collect.get(0))) {
            // error format
            return;
        }

        String text = "f" + collect.get(0).getText();
        collect.remove(0);
        text = text.replace("%n", "\\n").replace("$", "\\$");
        Matcher m = Pattern.compile("%(\\d+\\$)?([-#+ 0,(<]*)?(\\d+)?(\\.\\d*)?([tT])?([a-zA-Z%])").matcher(text);
        final Iterator<PsiElement> iterator = collect.iterator();
        StringBuilder stringBuilder = new StringBuilder();
        int lastIndex = 0;
        while (m.find()) {
            final int start = m.start();
            stringBuilder.append(text, lastIndex, start);
            String group = m.group(0);
            final String s;
            if (iterator.hasNext()) {
                if ("%%".equals(group)) {
                    s = "%";
                } else if ("%n".equals(group)) {
                    s = "\n";
                } else if ("%s".equals(group) || "%d".equals(group)) {
                    final String replace = iterator.next().getText()
                                                   .replaceAll("\\s*([^\"\\s](?:\".*?\")|(?:[^\"\\s])[^\"\\s]*?)\\s*", "$1")
                                                   .replace("\n", "");
                    s = "${" + replace + "}";
                } else {
                    final String replace = iterator.next().getText()
                                                   .replaceAll("\\s*([^\"\\s](?:\".*?\")|(?:[^\"\\s])[^\"\\s]*?)\\s*", "$1")
                                                   .replace("\n", "");
                    s = "${" + group + ":" + replace + "}";
                }
            } else {
                s = "${}";
            }
            stringBuilder.append(s);
            lastIndex = m.end();
        }
        stringBuilder.append(text, lastIndex, text.length());
        text = stringBuilder.toString();
        holder.newAnnotation(HighlightSeverity.INFORMATION, "[ZrString]: Replace 'String.format' with 'F-string'")
              .range((PsiElement) element).tooltip(text).highlightType(ProblemHighlightType.INFORMATION)
              .withFix(new Change2FStringQuickFix(text, element)).create();
    }

    @Nullable
    private Formatter registerChange2NormalIntentionAction(@NotNull PsiElement element, @NotNull AnnotationHolder holder, String text) {
        List<Formatter> allFormatters = Formatter.getAllFormatters();
        int endIndex = text.indexOf("\"");
        if (endIndex == -1) {
            LOG.info(element + "字符串前缀无法识别: " + text);
            return null;
        }
        if (endIndex == 0) {
            return null;
        }
        String prefix = text.substring(0, endIndex).replace("\\", "");
        if (prefix.contains("\\")) return null;
        Formatter formatter = allFormatters.stream().filter(a -> a.prefix().equals(prefix)).findFirst().orElse(null);
        if (formatter == null) {
            LOG.info("未识别的字符串前缀: " + text);
            return null;
        }
        return formatter;
    }

    private void foldCode(@NotNull PsiElement element, @NotNull AnnotationHolder holder, String printOut) {
        if (element.getTextOffset() != 0) holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element)
                                                .highlightType(ProblemHighlightType.INFORMATION)
                                                .withFix(new FoldCodeQuickFix(element, element.getTextOffset()))
                                                .create();
    }

    @NotNull
    private String replace2NormalString(@NotNull PsiElement element, @NotNull AnnotationHolder holder, String text, Formatter formatter, ZrStringModel model) {
        List<StringRange> build = model.getList();
        String printOut = formatter.printOut(build, text);
        if (element.getParent() instanceof PsiExpressionList) {
            if (printOut.startsWith("(") && printOut.endsWith(")")) {
                printOut = printOut.substring(1, printOut.length() - 1);
            }
        }
        holder.newAnnotation(HighlightSeverity.INFORMATION, "[ZrString]: Replace with normal string").range(element)
              .tooltip(printOut).highlightType(ProblemHighlightType.INFORMATION)
              .withFix(new Change2NormalStringQuickFix(printOut, element)).create();
        return printOut;
    }

    private void registerChange2SStringIntentionAction(@NotNull PsiPolyadicExpression element, @NotNull AnnotationHolder holder) {
        ProblemHighlightType information = ProblemHighlightType.INFORMATION;
        HighlightSeverity severity = HighlightSeverity.INFORMATION;
        StringBuilder printOut = new StringBuilder("$\"");
        List<PsiElement> collect = Arrays.stream(element.getChildren())
                                         .filter(a -> !(a instanceof PsiWhiteSpace || (a instanceof PsiJavaToken && ((PsiJavaToken) a).getTokenType() == JavaTokenType.PLUS) || (Objects.equals(a.getText(), "\"\""))))
                                         .collect(Collectors.toList());
        if (collect.size() == 1) return;
        List<PsiElement> firstCollect = new ArrayList<>();
        for (PsiElement psiElement : collect) {
            if (!ZrUtil.isJavaStringLiteral(psiElement)) {
                firstCollect.add(psiElement);
            } else {
                break;
            }
        }
        if (!firstCollect.isEmpty()) {
            printOut.append("${");
            printOut.append(firstCollect.stream().map(PsiElement::getText).collect(Collectors.joining("+")));
            printOut.append("}");
        }
        List<PsiElement> collect1 = collect.stream().skip(firstCollect.size()).collect(Collectors.toList());
        for (int i = 0; i < collect1.size(); i++) {
            PsiElement item = collect1.get(i);
            String itemText = item.getText();
            if (ZrUtil.isJavaStringLiteral(item)) {
                if (itemText.startsWith("\"")) {
                    printOut.append(itemText, 1, itemText.length() - 1);
                } else {
                    printOut.append(itemText, itemText.indexOf("\"") + 1, itemText.length() - 1);
                }
            } else {
                int appendType = 0;
                if (itemText.matches("[A-Za-z_\\u4e00-\\u9fa5$]{1}[0-9A-Za-z_\\u4e00-\\u9fa5$().]+")) {
                    if (i + 1 < collect1.size()) {
                        PsiElement nextItem = collect1.get(i + 1);
                        if (ZrUtil.isJavaStringLiteral(nextItem)) {
                            if (nextItem.getText().matches("\"[0-9A-Za-z_\\u4e00-\\u9fa5$.]+.*")) {
                                appendType = 1;
                            } else {
                                appendType = 0;
                            }
                        } else {
                            appendType = 1;
                        }
                    } else {
                        appendType = 0;
                    }
                } else {
                    appendType = 1;
                }
                if (item instanceof PsiParenthesizedExpression) {
                    if (itemText.length() <= 2) continue;
                    itemText = itemText.substring(1, itemText.length() - 1).trim();
                }
                final String replace = itemText.replaceAll("([^\\\\])'", "$1\\'").replace("\n", "").replace("\r", "");
                if (replace.endsWith("))")) appendType = 1;
                if (appendType == 1) {
                    printOut.append("${");
                    printOut.append(replace);
                    printOut.append("}");
                } else {
                    printOut.append("$");
                    printOut.append(replace);
                }
            }
        }
        printOut.append("\"");
//            if( collect.stream().filter(a->(!ZrElementUtil.isJavaStringLiteral(a))).allMatch(a->a.getText().length()<10)){
//                information=ProblemHighlightType.INFORMATION;
//                severity=HighlightSeverity.INFORMATION;
//            }
        String text = printOut.toString();
        holder.newAnnotation(severity, "[ZrString]: Replace '+' with '$-string'").range(element).tooltip(text)
              .highlightType(information).withFix(new Change2SStringQuickFix(text, element)).create();
    }

    private static class Change2NormalStringQuickFix implements IntentionAction {

        private String printOut;
        private PsiElement element;

        public Change2NormalStringQuickFix(String printOut, PsiElement element) {
            this.printOut = printOut;
            this.element = element;
        }

        @Override
        public @IntentionName @NotNull String getText() {
            return "[ZrString]: Replace with normal string";
        }

        @Override
        public @NotNull @IntentionFamilyName String getFamilyName() {
            return "ZrString";
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return true;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
            @NotNull PsiExpression codeBlockFromText = elementFactory.createExpressionFromText(printOut, element);
            element.replace(codeBlockFromText);
//            CodeStyleManager.getInstance(project).reformat(codeBlockFromText);
        }

        @Override
        public boolean startInWriteAction() {
            return true;
        }
    }

    private static class Change2SStringQuickFix implements IntentionAction {

        private String printOut;
        private PsiElement element;

        public Change2SStringQuickFix(String printOut, PsiElement element) {
            this.printOut = printOut;
            this.element = element;
        }

        @Override
        public @IntentionName @NotNull String getText() {
            return "[ZrString]: Replace with $-string";
        }

        @Override
        public @NotNull @IntentionFamilyName String getFamilyName() {
            return "ZrString";
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return true;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
            @NotNull PsiExpression codeBlockFromText = elementFactory.createExpressionFromText(printOut, element);
            element.replace(codeBlockFromText);
        }

        @Override
        public boolean startInWriteAction() {
            return true;
        }
    }

    private static class Change2FStringQuickFix implements IntentionAction {

        private String printOut;
        private PsiElement element;

        public Change2FStringQuickFix(String printOut, PsiElement element) {
            this.printOut = printOut;
            this.element = element;
        }

        @Override
        public @IntentionName @NotNull String getText() {
            return "[ZrString]: Replace 'String.format' with F-string";
        }

        @Override
        public @NotNull @IntentionFamilyName String getFamilyName() {
            return "ZrString";
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return true;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
            @NotNull PsiExpression codeBlockFromText = elementFactory.createExpressionFromText(printOut, element);
            element.replace(codeBlockFromText);
        }

        @Override
        public boolean startInWriteAction() {
            return true;
        }
    }

    private class FoldCodeQuickFix implements IntentionAction {
        private PsiElement element;
        private int textOffset;

        public FoldCodeQuickFix(@NotNull PsiElement element, int textOffset) {
            this.element = element;
            this.textOffset = textOffset;
        }

        @Override
        public @IntentionName @NotNull String getText() {
            return "[ZrString]: Fold line string code";
        }

        @Override
        public @NotNull @IntentionFamilyName String getFamilyName() {
            return "ZrString";
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return true;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
            if (editor instanceof EditorEx) {
                final FoldingModelEx foldingModel = ((EditorEx) editor).getFoldingModel();
                foldingModel.runBatchFoldingOperation(() -> {
                    final int line = editor.getDocument().getLineNumber(textOffset);
                    FoldRegion region = FoldingUtil.findFoldRegionStartingAtLine(editor, line);
                    if (region != null) region.setExpanded(false);
                });
            }
        }

        @Override
        public boolean startInWriteAction() {
            return true;
        }
    }
}

