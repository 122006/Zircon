package com.by122006.zircon.ijplugin;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import formatter.Formatter;
import formatter.StringRange;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class ZrAnnotator implements Annotator {
    private static final Logger LOG = Logger.getInstance(ZrAnnotator.class.getName());

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getLanguage() != JavaLanguage.INSTANCE) return;
        if (!ZrElementUtil.isJavaStringLiteral(element)) return;
        String text = element.getText();
        if (text.startsWith("\"")) {
            registerChange2SStringIntentionAction(element, holder);
            return;
        }
        registerChange2NormalIntentionAction(element, holder, text);
    }

    private void registerChange2NormalIntentionAction(@NotNull PsiElement element, @NotNull AnnotationHolder holder, String text) {
        List<Formatter> allFormatters = Formatter.getAllFormatters();
        int endIndex = text.indexOf("\"");
        if (endIndex == -1) {
            LOG.info(element +
                    "字符串前缀无法识别: " + text);
            return;
        }
        if (endIndex == 0) {
            return;
        }
        String prefix = text.substring(0, endIndex).replace("\\", "");
        if (prefix.contains("\\")) return;
        Formatter formatter = allFormatters.stream()
                .filter(a -> a.prefix().equals(prefix)).findFirst().orElse(null);
        if (formatter == null) {
            LOG.info("未识别的字符串前缀: " + text);
            return;
        }
        List<StringRange> build = formatter.build(text);
        String printOut = formatter.printOut(build, text);
        holder.newAnnotation(HighlightSeverity.INFORMATION, "[ZrString]: Replace with normal string")
                .range(element)
                .tooltip(printOut)
                .highlightType(ProblemHighlightType.INFORMATION)
                .withFix(new Change2NormalStringQuickFix(printOut, element))
                .create();
    }

    private void registerChange2SStringIntentionAction(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        ProblemHighlightType information = ProblemHighlightType.INFORMATION;
        HighlightSeverity severity = HighlightSeverity.INFORMATION;
        StringBuilder printOut = new StringBuilder("$\"");
        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiPolyadicExpression)) return;
        List<PsiElement> collect = Arrays.stream(parent.getChildren())
                .filter(a -> !(a instanceof PsiWhiteSpace
                        || (a instanceof PsiJavaToken && ((PsiJavaToken) a).getTokenType() == JavaTokenType.PLUS)
                        || (Objects.equals(a.getText(), "\"\""))))
                .collect(Collectors.toList());
        if (collect.size() == 1) return;
        List<PsiElement> firstCollect = new ArrayList<>();
        for (PsiElement psiElement : collect) {
            if (!ZrElementUtil.isJavaStringLiteral(psiElement)) {
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
            if (ZrElementUtil.isJavaStringLiteral(item)) {
                if (itemText.startsWith("\"")){
                    printOut.append(itemText, 1, itemText.length()-1);
                }else {
                    printOut.append(itemText, itemText.indexOf("\""), itemText.length()-1);
                }
            } else {
                int appendType = 0;
                if (itemText.matches("[A-Za-z_\\u4e00-\\u9fa5$]{1}[A-Za-z_\\u4e00-\\u9fa5$().]+")) {
                    if (i + 1 < collect1.size() - 1) {
                        PsiElement nextItem = collect1.get(i + 1);
                        if (ZrElementUtil.isJavaStringLiteral(item)) {
                            if (nextItem.getText().matches("^[A-Za-z_\\u4e00-\\u9fa5$]+.*")) {
                                appendType = 1;
                            }else {
                                appendType=0;
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
                if (item instanceof PsiParenthesizedExpression){
                    if (itemText.length()<=2) continue;
                    itemText=itemText.substring(1,itemText.length()-1).trim();
                }
                if (appendType == 1) {
                    printOut.append("${");

                    printOut.append(itemText);
                    printOut.append("}");
                } else {
                    printOut.append("$");
                    printOut.append(itemText);
                }
            }
        }
        printOut.append("\"");
//            if( collect.stream().filter(a->(!ZrElementUtil.isJavaStringLiteral(a))).allMatch(a->a.getText().length()<10)){
//                information=ProblemHighlightType.INFORMATION;
//                severity=HighlightSeverity.INFORMATION;
//            }
        holder.newAnnotation(severity, "[ZrString]: Replace '+' with '$-string'")
                .range(element)
                .tooltip(printOut.toString())
                .highlightType(information)
                .withFix(new Change2SStringQuickFix(printOut.toString(), element))
                .create();
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
            element.getParent().replace(codeBlockFromText);
        }

        @Override
        public boolean startInWriteAction() {
            return true;
        }
    }
}

