package com.by122006.zircon.ijplugin;

import com.by122006.zircon.util.ZrUtil;
import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.FormatUtils;
import com.sun.tools.javac.parser.*;
import com.sun.tools.javac.parser.Formatter;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class ZrAnnotator implements Annotator {
    private static final Logger LOG = Logger.getInstance(ZrAnnotator.class.getName());

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getLanguage() != JavaLanguage.INSTANCE) return;
        if (element instanceof PsiPolyadicExpression && Arrays.stream(element.getChildren()).anyMatch(ZrUtil::isJavaStringLiteral)) {
            registerChange2SStringIntentionAction((PsiPolyadicExpression) element, holder);
            return;
        }
        if (ZrUtil.isJavaStringLiteral(element)) {
            if (!element.getText().startsWith("\"")) {
                registerChange2NormalIntentionAction(element, holder, element.getText());
                final FStringFormatter fStringFormatter = new FStringFormatter();
                if (element.getText().startsWith(fStringFormatter.prefix())) {
                    try {
                        final ZrStringModel build = fStringFormatter.build(element.getText());
                        checkFStringError(element, build, holder);
                    } catch (Exception e) {
                        LOG.warn(e);
                    }
                }
                final SStringFormatter SStringFormatter = new SStringFormatter();
                if (element.getText().startsWith(SStringFormatter.prefix())) {
                    try {
                        final ZrStringModel build = fStringFormatter.build(element.getText());
                        checkSStringChange2FString(element, build, holder);
                    } catch (Exception e) {
                        LOG.warn(e);
                    }
                }
            }else {
                checkNeedChange2SString(element, holder);
            }
            return;
        }
        if (element instanceof PsiMethodCallExpressionImpl) {
//  public static final CallMatcher STRING_FORMATTED = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "formatted")
//    .parameterTypes("java.lang.Object...");
            if (FormatUtils.isFormatCall((PsiMethodCallExpression) element)) {
                registerChangeFromFormatIntentionAction((PsiMethodCallExpressionImpl) element, holder);
            }
            return;
        }
//        String.format(Locale.CHINA,"1%02d22",1+ 1,""+"",3);
    }

    private void checkNeedChange2SString(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getText().matches(".*\\$\\{.*")){
            holder.newAnnotation(HighlightSeverity.INFORMATION, "[ZrString]: 替换为'$-string'模板字符串")
                    .range(element)
                    .highlightType(ProblemHighlightType.INFORMATION)
                    .withFix(new IntentionAction() {
                        @Override
                        public @IntentionName
                        @NotNull String getText() {
                            return "[ZrString]: 替换为'$-string'模板字符串";
                        }

                        @Override
                        public @NotNull
                        @IntentionFamilyName String getFamilyName() {
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
                                final String text = "$"+ element.getText();
                                @NotNull PsiExpression codeBlockFromText = elementFactory.createExpressionFromText(text, element);
                                element.replace(codeBlockFromText);
                            }
                        }

                        @Override
                        public boolean startInWriteAction() {
                            return true;
                        }
                    })
                    .create();
        }
    }

    private void checkSStringChange2FString(PsiElement element, ZrStringModel build, AnnotationHolder holder) {
        final boolean change2FString = build.getList().stream().anyMatch(a -> a.codeStyle == 2);
        if (change2FString){
            holder.newAnnotation(HighlightSeverity.WARNING, "[ZrString]: 请使用'F-string'以格式化字符串")
                    .range(element)
                    .highlightType(ProblemHighlightType.WARNING)
                    .withFix(new IntentionAction() {
                        @Override
                        public @IntentionName
                        @NotNull String getText() {
                            return "[ZrString]: 替换为'F-string'";
                        }

                        @Override
                        public @NotNull
                        @IntentionFamilyName String getFamilyName() {
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
                                final String text = "f"+element.getText().substring(1);
                                @NotNull PsiExpression codeBlockFromText = elementFactory.createExpressionFromText(text, element);
                                element.replace(codeBlockFromText);
                            }
                        }

                        @Override
                        public boolean startInWriteAction() {
                            return false;
                        }
                    })
                    .create();
        }
    }

    private void checkFStringError(@NotNull PsiElement element, ZrStringModel build, AnnotationHolder holder) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        final Class<?> aClass = Class.forName("com.siyeh.ig.bugs.FormatDecode");
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
            return JavaPsiFacade
                    .getElementFactory(element.getProject())
                    .createExpressionFromText(build.getOriginalString().substring(a.startIndex, a.endIndex), element);
        }).toArray(PsiExpression[]::new);
        for (int i = 0; i < arguments.length; i++) {
            final StringRange formatRange = formatList.get(i);
            Object invoke = null;
            try {
                invoke = decode.invoke(null, build.getOriginalString().substring(formatRange.startIndex, formatRange.endIndex), argumentCount);
            } catch (InvocationTargetException e) {
                final Throwable targetException = e.getTargetException();
                final TextRange textRange = TextRange.create(formatRange.startIndex, formatRange.endIndex)
                        .shiftRight(element.getTextOffset());
                holder.newAnnotation(HighlightSeverity.ERROR, "[ZrString]: "+targetException.getMessage())
                        .range(textRange)
                        .highlightType(ProblemHighlightType.ERROR)
                        .create();
                continue;
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (invoke == null || !invoke.getClass().isArray() || ((Object[]) invoke).length == 0) {
                final TextRange textRange = TextRange.create(formatRange.startIndex, formatRange.endIndex)
                        .shiftRight(element.getTextOffset());
                holder.newAnnotation(HighlightSeverity.ERROR, "[ZrString]: 请输入合法的'String.format()'标识符")
                        .range(textRange)
                        .highlightType(ProblemHighlightType.ERROR)
                        .create();
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
                PsiType preciseType = TypeConstraint.fromDfType(CommonDataflow.getDfType(argument)).getPsiType(element.getProject());
                if (preciseType == null || !valid.test(validator, preciseType)) {
                    final TextRange textRange = argumentRange.get(i)
                            .shiftRight(element.getTextOffset());
                    holder.newAnnotation(HighlightSeverity.ERROR, "[ZrString]: 参数类型不匹配。当前传入:"+argumentType.getCanonicalText())
                            .range(textRange)
                            .highlightType(ProblemHighlightType.ERROR)
                            .create();
                }
            }
        }
    }

    private void registerChangeFromFormatIntentionAction(@NotNull PsiMethodCallExpressionImpl element, @NotNull AnnotationHolder holder) {
        final PsiElement child = element.getChildren()[1];
        if (!(child instanceof PsiExpressionList)) return;
        final PsiExpressionList formatExpression = (PsiExpressionList) child;
        final PsiElement[] children = formatExpression.getChildren();
        if (children.length == 0) return;
        final List<PsiElement> collect = Arrays.stream(children).filter(a -> !(a instanceof PsiJavaToken || a instanceof PsiWhiteSpace)).collect(Collectors.toList());
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
                .range((PsiElement) element)
                .tooltip(text)
                .highlightType(ProblemHighlightType.INFORMATION)
                .withFix(new Change2FStringQuickFix(text, element))
                .create();
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
        final ZrStringModel model = formatter.build(text);
        List<StringRange> build = model.getList();
        String printOut = formatter.printOut(build, text);
        if (element.getParent() instanceof PsiExpressionList) {
            if (printOut.startsWith("(") && printOut.endsWith(")")) {
                printOut = printOut.substring(1, printOut.length() - 1);
            }
        }
        holder.newAnnotation(HighlightSeverity.INFORMATION, "[ZrString]: Replace with normal string")
                .range(element)
                .tooltip(printOut)
                .highlightType(ProblemHighlightType.INFORMATION)
                .withFix(new Change2NormalStringQuickFix(printOut, element))
                .create();
        if (element.getTextOffset() != 0)
            holder.newAnnotation(HighlightSeverity.INFORMATION, "[ZrString]: Fold line string code")
                    .range(element)
                    .tooltip(printOut)
                    .highlightType(ProblemHighlightType.INFORMATION)
                    .withFix(new FoldCodeQuickFix(element, element.getTextOffset()))
                    .create();

    }

    private void registerChange2SStringIntentionAction(@NotNull PsiPolyadicExpression element, @NotNull AnnotationHolder holder) {
        ProblemHighlightType information = ProblemHighlightType.INFORMATION;
        HighlightSeverity severity = HighlightSeverity.INFORMATION;
        StringBuilder printOut = new StringBuilder("$\"");
        List<PsiElement> collect = Arrays.stream(element.getChildren())
                .filter(a -> !(a instanceof PsiWhiteSpace
                        || (a instanceof PsiJavaToken && ((PsiJavaToken) a).getTokenType() == JavaTokenType.PLUS)
                        || (Objects.equals(a.getText(), "\"\""))))
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
                final String replace = itemText.replaceAll("([^\\\\])'", "$1\\'")
                        .replace("\n", "")
                        .replace("\r", "");
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
        holder.newAnnotation(severity, "[ZrString]: Replace '+' with '$-string'")
                .range(element)
                .tooltip(text)
                .highlightType(information)
                .withFix(new Change2SStringQuickFix(text, element))
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
        public @IntentionName
        @NotNull
        String getText() {
            return "[ZrString]: Replace with normal string";
        }

        @Override
        public @NotNull
        @IntentionFamilyName String getFamilyName() {
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
        public @IntentionName
        @NotNull
        String getText() {
            return "[ZrString]: Replace with $-string";
        }

        @Override
        public @NotNull
        @IntentionFamilyName String getFamilyName() {
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
        public @IntentionName
        @NotNull
        String getText() {
            return "[ZrString]: Replace 'String.format' with F-string";
        }

        @Override
        public @NotNull
        @IntentionFamilyName String getFamilyName() {
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
        public @IntentionName
        @NotNull
        String getText() {
            return "[ZrString]: Fold line string code";
        }

        @Override
        public @NotNull
        @IntentionFamilyName String getFamilyName() {
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

