package com.by122006.zircon.vsplugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public class ZirconCore {
    private static final String EX_METHOD_ANNOTATION = "zircon.ExMethod";
    private static final String EX_METHOD_IDE_ANNOTATION = "zircon.ExMethodIDE";
    private static final String JDT_SEARCH_REQUESTOR_CLASS =
            "org.eclipse.jdt.core.search.ZirconExMethodSearchRequestor";
    private static final String JDT_SEARCH_REQUESTOR_RESOURCE =
            "/org/eclipse/jdt/core/search/ZirconExMethodSearchRequestor.class";
    private static final long JDT_EXTENSION_INDEX_CACHE_MILLIS = 15_000L;
    private static final long JDT_EXTENSION_INDEX_COLD_WAIT_MILLIS = 1_200L;
    private static final int SESSION_DIAGNOSTIC_CACHE_LIMIT = 32_768;
    private static final int ZIRCON_REQUIRED_IMPORT_PROPOSAL_FLAG = 0x4000_0000;
    private static final String METHOD_BINDING_CLASS = "org.eclipse.jdt.internal.compiler.lookup.MethodBinding";
    private static final String PARAMETERIZED_METHOD_BINDING_CLASS = "org.eclipse.jdt.internal.compiler.lookup.ParameterizedMethodBinding";
    private static final String REFERENCE_BINDING_CLASS = "org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding";
    private static final String SCOPE_CLASS = "org.eclipse.jdt.internal.compiler.lookup.Scope";
    private static final String SUBSTITUTION_CLASS = "org.eclipse.jdt.internal.compiler.lookup.Substitution";
    private static final String TYPE_BINDING_CLASS = "org.eclipse.jdt.internal.compiler.lookup.TypeBinding";
    private static final String BI_OP_QUALIFIED_NAME = "zircon.BiOp";
    private static final String ELVIS_MARKER_SELECTOR = "$$elvisExpr";
    // Debug/trace switches stay property-driven so selector/file targeting is configurable rather than hard-coded.
    private static final String TRACE_SELECTORS_PROPERTY = "zircon.trace.selectors";
    private static final String DEBUG_SELECTORS_PROPERTY = "zircon.debug.selectors";
    private static final String DEBUG_PROBLEM_FILES_PROPERTY = "zircon.debug.problemFiles";
    private static final String SKIP_ANONYMOUS_IMPLICIT_SELECTORS_PROPERTY = "zircon.skip.anonymousImplicitSelectors";
    // Binary/source lookup caches avoid repeated jar probing during a single language-server session.
    private static final Map<String, String> DISK_BINARY_LOCATION_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> DISK_BINARY_LOOKUP_MISSES = newBoundedConcurrentSet();
    private static final Map<String, Object[]> BINARY_METHOD_ANNOTATION_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Object[]> BINARY_TYPE_ANNOTATION_CACHE = new ConcurrentHashMap<>();
    // Per-compilation-unit caches keep reflection-heavy extension lookup stable without rescanning the same scope.
    private static final Map<Object, List<Object>> COMPILATION_UNIT_CANDIDATE_TYPES_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Map<String, List<Object>>> SELECTOR_EXTENSION_METHOD_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    // JDT already persists source/binary annotation references. Keep only the
    // resulting extension owner/selector keys here; no classpath files are scanned.
    private static final Map<Object, JdtExtensionIndexSnapshot> JDT_EXTENSION_INDEX_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, CompletableFuture<JdtExtensionIndexSnapshot>> JDT_EXTENSION_INDEX_REFRESHES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ClassLoader, Class<?>> JDT_SEARCH_REQUESTOR_CLASSES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Long, List<JdtIndexedExtensionMethod>> JDT_SEARCH_RESULTS = new ConcurrentHashMap<>();
    private static final Set<Long> JDT_METHOD_DECLARATION_SEARCH_REQUESTS = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> JDT_BINARY_TYPE_LOCATIONS = new ConcurrentHashMap<>();
    private static final Set<String> JDT_APPLICATION_LIBRARY_LOCATIONS = newBoundedConcurrentSet();
    private static final AtomicLong JDT_SEARCH_REQUEST_IDS = new AtomicLong();
    private static final ExecutorService JDT_EXTENSION_INDEX_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "zircon-jdt-extension-index");
        thread.setDaemon(true);
        return thread;
    });
    // JDT's Java search resolves candidate calls again after its index has narrowed the files.
    // Facade bindings intentionally look like receiver methods, while the SearchPattern points at
    // the original static @ExMethod declaration. Keep a weak identity-like bridge so MethodLocator
    // can compare the stable declaration without retaining compilation environments indefinitely.
    private static final Map<Object, Object> JDT_SEARCH_ORIGINAL_METHODS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Object> JDT_SEARCH_ACCURATE_REFERENCE_NODES =
            Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
    // These de-dup sets only gate debug logging so repeated JDT callbacks do not flood the console.
    private static final Set<String> MESSAGE_SEND_GENERATE_CODE_FAILURE_KEYS = newBoundedConcurrentSet();
    private static final Set<String> MESSAGE_SEND_GENERATE_CODE_PREPARE_KEYS = newBoundedConcurrentSet();
    private static final Set<String> MESSAGE_SEND_ANALYSE_CODE_FAILURE_KEYS = newBoundedConcurrentSet();
    private static final Set<String> MESSAGE_SEND_ANALYSE_CODE_STACK_KEYS = newBoundedConcurrentSet();
    private static final Set<String> MESSAGE_SEND_RESOLVE_TYPE_FAILURE_KEYS = newBoundedConcurrentSet();
    private static final Set<String> LAMBDA_GENERATE_CODE_PREPARE_KEYS = newBoundedConcurrentSet();
    private static final Set<String> LAMBDA_GENERATE_CODE_FAILURE_KEYS = newBoundedConcurrentSet();
    private static final Set<String> LAMBDA_CAPTURE_INFERENCE_KEYS = newBoundedConcurrentSet();
    private static final Set<String> LAMBDA_ANALYSE_SCOPE_KEYS = newBoundedConcurrentSet();
    private static final Set<String> AST_INSPECTION_FAILURE_KEYS = newBoundedConcurrentSet();
    private static final Set<String> FUNCTIONAL_REFRESH_BINDING_KEYS = newBoundedConcurrentSet();
    private static final Set<String> CONDITIONAL_ANALYSE_DIAGNOSTIC_KEYS = newBoundedConcurrentSet();
    private static final Set<String> RECEIVER_REWRITE_DIAGNOSTIC_KEYS = newBoundedConcurrentSet();
    private static final Set<String> FUNCTIONAL_EXPECTED_TYPE_DIAGNOSTIC_KEYS = newBoundedConcurrentSet();
    private static final Set<String> PROBLEM_REPORT_KEYS = newBoundedConcurrentSet();
    private static final Set<String> RECORDED_PROBLEM_KEYS = newBoundedConcurrentSet();
    // Successful range records let later JDT diagnostics be suppressed only for invocations we already rebound safely.
    private static final Set<String> SUCCESSFUL_CLASS_TARGET_EXTENSION_RANGES = newBoundedConcurrentSet();
    private static final Set<String> SUCCESSFUL_EXTENSION_UNDEFINED_RANGES = newBoundedConcurrentSet();
    private static final Set<String> SUCCESSFUL_FUNCTIONAL_WRAPPER_RANGES = newBoundedConcurrentSet();
    private static final Set<String> PROVEN_EXTENSION_FUNCTIONAL_TARGET_RANGES = newBoundedConcurrentSet();
    private static final Set<String> PROVEN_LOCAL_REFERENCE_RANGES = newBoundedConcurrentSet();
    private static final Set<String> SEMANTIC_TYPE_VARIABLE_TARGET_RANGES = newBoundedConcurrentSet();
    private static final Set<String> SEMANTIC_NESTED_TYPE_VARIABLE_TARGET_RANGES = newBoundedConcurrentSet();
    private static final Map<String, List<Object>> SUCCESSFUL_EXTENSION_FUNCTIONAL_PARAMETER_TYPES = new ConcurrentHashMap<>();
    private static final Map<String, List<Object>> SUCCESSFUL_EXTENSION_FUNCTIONAL_RETURN_TYPES = new ConcurrentHashMap<>();
    private static final Map<String, String> SUCCESSFUL_EXTENSION_UNDEFINED_SELECTORS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Integer> MESSAGE_SEND_RESOLVE_RECOVERY_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Map<String, String>> COMPLETION_REQUIRED_IMPORTS = new ThreadLocal<>();
    // Reflection caches keep repeated field lookup cheap across different JDT binding implementations.
    private static final Map<Class<?>, Map<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Set<String>> FIELD_MISS_CACHE = new ConcurrentHashMap<>();

    private ZirconCore() {
    }

    private static <E> Set<E> newBoundedConcurrentSet() {
        return new BoundedConcurrentSet<>(SESSION_DIAGNOSTIC_CACHE_LIMIT);
    }

    private static <K, V> void putBoundedConcurrentMap(Map<K, V> map, K key, V value) {
        map.put(key, value);
        if (map.size() <= SESSION_DIAGNOSTIC_CACHE_LIMIT) {
            return;
        }
        synchronized (map) {
            int removeCount = Math.max(1, map.size() - SESSION_DIAGNOSTIC_CACHE_LIMIT);
            Iterator<K> iterator = map.keySet().iterator();
            while (removeCount-- > 0 && iterator.hasNext()) {
                map.remove(iterator.next());
            }
        }
    }

    private static final class BoundedConcurrentSet<E> extends AbstractSet<E> {
        private final int maximumSize;
        private final ConcurrentHashMap<E, Boolean> values = new ConcurrentHashMap<>();
        private final ConcurrentLinkedQueue<E> insertionOrder = new ConcurrentLinkedQueue<>();

        private BoundedConcurrentSet(int maximumSize) {
            this.maximumSize = Math.max(1, maximumSize);
        }

        @Override
        public boolean add(E value) {
            if (values.putIfAbsent(value, Boolean.TRUE) != null) {
                return false;
            }
            insertionOrder.add(value);
            trimToMaximumSize();
            return true;
        }

        @Override
        public boolean remove(Object value) {
            return values.remove(value) != null;
        }

        @Override
        public boolean contains(Object value) {
            return values.containsKey(value);
        }

        @Override
        public Iterator<E> iterator() {
            return values.keySet().iterator();
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public void clear() {
            values.clear();
            insertionOrder.clear();
        }

        private void trimToMaximumSize() {
            while (values.size() > maximumSize) {
                E oldest = insertionOrder.poll();
                if (oldest == null) {
                    return;
                }
                values.remove(oldest);
            }
        }
    }

    private static final class CandidateBinding {
        private final Object binding;
        private final Object originalMethod;
        private final Object targetType;
        private final String ownerClassName;
        private final boolean cover;
        private boolean completionImportRequired;

        private CandidateBinding(Object binding, Object originalMethod, Object targetType, String ownerClassName, boolean cover) {
            this.binding = binding;
            this.originalMethod = originalMethod;
            this.targetType = targetType;
            this.ownerClassName = ownerClassName;
            this.cover = cover;
        }
    }

    private static final class FacadeTypeVariableState {
        private final Object[] typeVariables;
        private final Map<Object, Object> substitutions;

        private FacadeTypeVariableState(Object[] typeVariables, Map<Object, Object> substitutions) {
            this.typeVariables = typeVariables;
            this.substitutions = substitutions;
        }
    }

    private static final class RangeAstMatch {
        private final Object node;
        private final Object enclosingLambda;
        private final Object scope;
        private final int span;

        private RangeAstMatch(Object node, Object enclosingLambda, Object scope, int span) {
            this.node = node;
            this.enclosingLambda = enclosingLambda;
            this.scope = scope;
            this.span = span;
        }
    }

    private static final class InvocationArgumentMatch {
        private final Object invocation;
        private final int argumentIndex;
        private final int span;

        private InvocationArgumentMatch(Object invocation, int argumentIndex, int span) {
            this.invocation = invocation;
            this.argumentIndex = argumentIndex;
            this.span = span;
        }
    }

    private static final class InvocationRewriteState {
        private final Object receiver;
        private final Object arguments;
        private final Object argumentTypes;
        private final Object binding;
        private final Object actualReceiverType;
        private final Object resolvedType;
        private final Object constant;
        private final Object argumentsHaveErrors;

        private InvocationRewriteState(
                Object receiver,
                Object arguments,
                Object argumentTypes,
                Object binding,
                Object actualReceiverType,
                Object resolvedType,
                Object constant,
                Object argumentsHaveErrors
        ) {
            this.receiver = receiver;
            this.arguments = arguments;
            this.argumentTypes = argumentTypes;
            this.binding = binding;
            this.actualReceiverType = actualReceiverType;
            this.resolvedType = resolvedType;
            this.constant = constant;
            this.argumentsHaveErrors = argumentsHaveErrors;
        }
    }

    private static final class LabeledBlock {
        private final String label;
        private final String body;

        private LabeledBlock(String label, String body) {
            this.label = label;
            this.body = body;
        }
    }

    public static Object findExtension(
            Object scope,
            Object receiverType,
            String methodName,
            Object[] argumentTypes,
        Object invocationSite
    ) {
        try {
            if (Util.isTraceEnabled()) {
                Util.log("[ZirconCore] unresolved lookup: selector=" + methodName
                        + ", receiver=" + getTypeName(receiverType)
                        + ", argCount=" + (argumentTypes == null ? 0 : argumentTypes.length));
            }
            if (scope == null || receiverType == null || methodName == null || methodName.isEmpty()) {
                return null;
            }
            if (!isScopeInConfiguredZirconProject(scope)) {
                return null;
            }
            if (shouldSkipAnonymousImplicitCompilePath(scope, receiverType, methodName, invocationSite)) {
                return null;
            }

            Object compilationUnitScope = getCompilationUnitScope(scope);
            if (compilationUnitScope == null) {
                return null;
            }

            List<Object> holderTypes = collectCandidateTypes(compilationUnitScope);
            if (shouldTraceSelector(methodName)) {
                Util.log("[ZirconCore] trace selector=" + methodName
                        + ", receiver=" + getTypeName(receiverType)
                        + ", holderTypes=" + holderTypes.size());
            }
            List<CandidateBinding> candidates = collectExtensionCandidates(compilationUnitScope, methodName, receiverType, scope, invocationSite);
            if (shouldTraceSelector(methodName)) {
                Util.log("[ZirconCore] trace selector=" + methodName
                        + ", receiver=" + getTypeName(receiverType)
                        + ", candidates=" + candidates.size());
            }
            if (candidates.isEmpty()) {
                return null;
            }
            Object[] safeArgumentTypes = argumentTypes != null ? argumentTypes : emptyTypeBindingArray(scope);
            List<CandidateBinding> compatibleBindings = new ArrayList<>();
            for (CandidateBinding candidate : candidates) {
                CandidateBinding compatible = computeCompatibleCandidate(scope, candidate, receiverType, safeArgumentTypes, invocationSite);
                if (compatible != null) {
                    compatibleBindings.add(compatible);
                }
            }
            if (shouldTraceSelector(methodName)) {
                Util.log("[ZirconCore] trace selector=" + methodName
                        + ", compatible=" + compatibleBindings.size());
            }
            if (compatibleBindings.isEmpty()) {
                return null;
            }
            if (compatibleBindings.size() == 1) {
                Object finalized = finalizeSelectedBinding(scope, receiverType, invocationSite, compatibleBindings.get(0).binding);
                if (shouldTraceSelector(methodName)) {
                    Util.log("[ZirconCore] trace selector=" + methodName + ", resolved=single");
                }
                return finalized;
            }

            Object selected = selectPreferredBinding(
                    compatibleBindings,
                    receiverType,
                    scope,
                    compilationUnitScope,
                    safeArgumentTypes,
                    invocationSite
            );
            if (shouldTraceSelector(methodName)) {
                Util.log("[ZirconCore] trace selector=" + methodName
                        + ", selectedProblem=" + isProblem(selected));
            }
            if (!isProblem(selected)) {
                selected = finalizeSelectedBinding(scope, receiverType, invocationSite, selected);
            }
            return isProblem(selected) ? null : selected;
        } catch (Exception e) {
            Throwable cause = e.getCause();
            StringBuilder message = new StringBuilder("[ZirconCore] findExtension error")
                    .append(": selector=").append(methodName)
                    .append(", receiver=").append(getTypeName(receiverType))
                    .append(", error=").append(e.getClass().getName()).append(": ").append(e.getMessage());
            if (cause != null) {
                message.append(" | cause=").append(cause.getClass().getName()).append(": ").append(cause.getMessage());
            }
            Util.log(message.toString());
            Util.log(Util.stackTrace(cause != null ? cause : e));
            return null;
        }
    }

    private static Object finalizeSelectedBinding(
            Object scope,
            Object receiverType,
            Object invocationSite,
            Object binding
    ) throws Exception {
        if (binding == null) {
            return null;
        }
        refreshInvocationArguments(scope, invocationSite, binding);
        Object finalized = finalizeImplicitStaticRewriteAfterSelection(scope, receiverType, invocationSite, binding);
        Object result = finalized != null ? finalized : binding;
        rememberSuccessfulExtensionUndefinedRange(scope, result, invocationSite);
        return result;
    }

    private static final class JdtIndexedExtensionMethod {
        private final String ownerQualifiedName;
        private final String selector;

        private JdtIndexedExtensionMethod(String ownerQualifiedName, String selector) {
            this.ownerQualifiedName = ownerQualifiedName;
            this.selector = selector;
        }

        private String key() {
            return ownerQualifiedName + "#" + selector;
        }
    }

    private static final class JdtExtensionIndexSnapshot {
        private final long createdAtMillis;
        private final long projectModificationStamp;
        private final List<JdtIndexedExtensionMethod> methods;

        private JdtExtensionIndexSnapshot(
                long createdAtMillis,
                long projectModificationStamp,
                List<JdtIndexedExtensionMethod> methods
        ) {
            this.createdAtMillis = createdAtMillis;
            this.projectModificationStamp = projectModificationStamp;
            this.methods = methods;
        }
    }


    public static void logMessageSendGenerateCodeFailure(Object messageSend, boolean valueRequired, Throwable throwable) {
        if (messageSend == null || throwable == null) {
            return;
        }
        try {
            Object binding = getFieldValue(messageSend, "binding");
            Object originalBinding = binding == null ? null : invokeOptionalMethod(binding, "original");
            Object receiver = getFieldValue(messageSend, "receiver");
            Object[] arguments = getFieldValue(messageSend, "arguments") instanceof Object[]
                    ? (Object[]) getFieldValue(messageSend, "arguments")
                    : emptyExpressionArray(messageSend);
            Object[] parameters = binding != null && getFieldValue(binding, "parameters") instanceof Object[]
                    ? (Object[]) getFieldValue(binding, "parameters")
                    : new Object[0];
            String selectorName = getSelectorName(binding);
            int sourceStart = readIntField(messageSend, "sourceStart");
            int sourceEnd = readIntField(messageSend, "sourceEnd");
            String key = selectorName + "|" + sourceStart + "|" + sourceEnd + "|" + throwable.getClass().getName();
            if (!MESSAGE_SEND_GENERATE_CODE_FAILURE_KEYS.add(key)) {
                return;
            }
            Util.log("[ZirconCore] generateCode failure"
                    + ": selector=" + selectorName
                    + ", source=" + sourceStart + "-" + sourceEnd
                    + ", valueRequired=" + valueRequired
                    + ", binding=" + describeMethodBindingDetailed(binding)
                    + ", original=" + describeMethodBindingDetailed(originalBinding)
                    + ", messageSendId=" + System.identityHashCode(messageSend)
                    + ", receiverClass=" + (receiver == null ? "null" : receiver.getClass().getSimpleName())
                    + ", receiverId=" + (receiver == null ? "null" : System.identityHashCode(receiver))
                    + ", receiverBinding=" + describeBinding(getFieldValue(receiver, "binding"))
                    + ", receiverBindingId=" + describeIdentity(getFieldValue(receiver, "binding"))
                    + ", receiverBindingState=" + describeLocalBindingState(getFieldValue(receiver, "binding"))
                    + ", receiverBindingType=" + describeTypeDebug(getFieldValue(getFieldValue(receiver, "binding"), "type"))
                    + ", receiverType=" + describeTypeDebug(getFieldValue(receiver, "resolvedType"))
                    + ", actualReceiverType=" + describeTypeDebug(getFieldValue(messageSend, "actualReceiverType"))
                    + ", argCount=" + arguments.length
                    + ", paramCount=" + parameters.length
                    + ", argumentTypes=" + describeTypeArrayDetailed(getFieldValue(messageSend, "argumentTypes") instanceof Object[]
                    ? (Object[]) getFieldValue(messageSend, "argumentTypes")
                    : new Object[0])
                    + ", descriptors=" + describeArgumentDescriptors(arguments)
                    + ", error=" + throwable.getClass().getName() + ": " + throwable.getMessage()
                    + ", stack=" + describeThrowableStack(throwable, 12));
        } catch (Exception e) {
            Util.log("[ZirconCore] generateCode failure logging error: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public static void logMessageSendResolveTypeFailure(Object messageSend, Object currentScope, Throwable throwable) {
        if (messageSend == null || throwable == null || !Util.isDebugEnabled()) {
            return;
        }
        try {
            Object binding = getFieldValue(messageSend, "binding");
            Object originalBinding = binding == null ? null : invokeOptionalMethod(binding, "original");
            Object receiver = getFieldValue(messageSend, "receiver");
            Object[] arguments = getFieldValue(messageSend, "arguments") instanceof Object[]
                    ? (Object[]) getFieldValue(messageSend, "arguments")
                    : emptyExpressionArray(messageSend);
            Object[] parameters = binding != null && getFieldValue(binding, "parameters") instanceof Object[]
                    ? (Object[]) getFieldValue(binding, "parameters")
                    : new Object[0];
            Object[] argumentTypes = getFieldValue(messageSend, "argumentTypes") instanceof Object[]
                    ? (Object[]) getFieldValue(messageSend, "argumentTypes")
                    : new Object[0];
            String selectorName = getSelectorName(binding);
            if (selectorName == null || selectorName.isEmpty()) {
                selectorName = getSelectorName(messageSend);
            }
            int sourceStart = readIntField(messageSend, "sourceStart");
            int sourceEnd = readIntField(messageSend, "sourceEnd");
            String key = selectorName + "|" + sourceStart + "|" + sourceEnd + "|" + throwable.getClass().getName();
            if (!MESSAGE_SEND_RESOLVE_TYPE_FAILURE_KEYS.add(key)) {
                return;
            }
            String fileName = describeCompilationUnitFileName(resolveCompilationResultFromScope(currentScope));
            Util.log("[ZirconCore] resolveType failure"
                    + ": file=" + fileName
                    + ", selector=" + selectorName
                    + ", source=" + sourceStart + "-" + sourceEnd
                    + ", binding=" + describeMethodBindingDetailed(binding)
                    + ", original=" + describeMethodBindingDetailed(originalBinding)
                    + ", receiver=" + describeAstNodeState(receiver, 1)
                    + ", actualReceiverType=" + describeTypeDebug(getFieldValue(messageSend, "actualReceiverType"))
                    + ", argCount=" + arguments.length
                    + ", argTypeCount=" + argumentTypes.length
                    + ", paramCount=" + parameters.length
                    + ", argumentTypes=" + describeTypeArrayDetailed(argumentTypes)
                    + ", descriptors=" + describeArgumentDescriptors(arguments)
                    + ", firstArg=" + (arguments.length > 0 ? describeAstNodeState(arguments[0], 1) : "null")
                    + ", error=" + throwable.getClass().getName() + ": " + throwable.getMessage()
                    + ", stack=" + describeThrowableStack(throwable, 12));
        } catch (Exception e) {
            Util.log("[ZirconCore] resolveType failure logging error: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public static Object tryRecoverMessageSendResolveTypeFailure(Object messageSend, Object currentScope, Throwable throwable) {
        if (messageSend == null || throwable == null) {
            return null;
        }
        try {
            if (!(throwable instanceof ArrayIndexOutOfBoundsException) && !(throwable instanceof NullPointerException)) {
                return null;
            }
            Object binding = getFieldValue(messageSend, "binding");
            if (binding == null || isProblem(binding)) {
                return null;
            }
            Object returnType = getFieldValue(binding, "returnType");
            if (returnType == null || isProblem(returnType)) {
                return null;
            }
            Object[] arguments = getFieldValue(messageSend, "arguments") instanceof Object[]
                    ? (Object[]) getFieldValue(messageSend, "arguments")
                    : emptyExpressionArray(messageSend);
            Object[] argumentTypes = getFieldValue(messageSend, "argumentTypes") instanceof Object[]
                    ? (Object[]) getFieldValue(messageSend, "argumentTypes")
                    : new Object[0];
            Object[] parameters = getFieldValue(binding, "parameters") instanceof Object[]
                    ? (Object[]) getFieldValue(binding, "parameters")
                    : new Object[0];
            Object actualReceiverType = getFieldValue(messageSend, "actualReceiverType");
            boolean hiddenReceiverShape = actualReceiverType != null
                    && (parameters.length == arguments.length + 1 || argumentTypes.length == arguments.length + 1);
            boolean missingVisibleArgumentType = false;
            boolean missingConstantState = throwable.getMessage() != null && throwable.getMessage().contains("this.constant");
            for (int index = 0; index < arguments.length; index++) {
                Object argumentType = index < argumentTypes.length ? argumentTypes[index] : null;
                if (argumentType == null || isProblem(argumentType)) {
                    Object resolvedArgumentType = resolveRecoverableInvocationArgumentType(null, arguments[index]);
                    if (resolvedArgumentType != null && !isProblem(resolvedArgumentType)) {
                        missingVisibleArgumentType = true;
                        break;
                    }
                }
            }
            if (!hiddenReceiverShape && !missingVisibleArgumentType && !missingConstantState) {
                return null;
            }
            Object[] repairedArgumentTypes = newTypedFieldArray(messageSend, "argumentTypes", arguments.length, null);
            for (int index = 0; index < arguments.length; index++) {
                int sourceIndex = hiddenReceiverShape && argumentTypes.length > arguments.length
                        ? index + (argumentTypes.length - arguments.length)
                        : index;
                Object repairedType = sourceIndex < argumentTypes.length ? argumentTypes[sourceIndex] : null;
                repairedType = resolveRecoverableInvocationArgumentType(repairedType, arguments[index]);
                if ((repairedType == null || isProblem(repairedType)) && sourceIndex < parameters.length) {
                    repairedType = resolveRecoverableInvocationArgumentType(parameters[sourceIndex], arguments[index]);
                }
                if (repairedType == null || isProblem(repairedType)) {
                    return null;
                }
                repairedArgumentTypes[index] = repairedType;
            }
            if (findField(messageSend.getClass(), "argumentTypes") != null) {
                setFieldValue(messageSend, "argumentTypes", repairedArgumentTypes);
            }
            if (missingConstantState) {
                normalizeExpressionConstantsForCodegen(messageSend, new IdentityHashMap<>(), 0);
            }
            if (findField(messageSend.getClass(), "resolvedType") != null) {
                setFieldValue(messageSend, "resolvedType", returnType);
            }
            if (findField(messageSend.getClass(), "argumentsHaveErrors") != null) {
                setFieldValue(messageSend, "argumentsHaveErrors", false);
            }
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconCore] resolveType recovered"
                        + ": file=" + describeCompilationUnitFileName(resolveCompilationResultFromScope(currentScope))
                        + ", selector=" + getSelectorName(binding)
                        + ", source=" + readIntField(messageSend, "sourceStart") + "-" + readIntField(messageSend, "sourceEnd")
                        + ", hiddenReceiverShape=" + hiddenReceiverShape
                        + ", repairedArgumentTypes=" + describeTypeArrayDetailed(repairedArgumentTypes)
                        + ", returnType=" + describeTypeDebug(returnType)
                        + ", error=" + throwable.getClass().getName() + ": " + throwable.getMessage());
            }
            return returnType;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Throwable unwrapInvocationFailure(Throwable error) {
        if (error instanceof java.lang.reflect.InvocationTargetException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    private static boolean tryRecoverAndRetryMessageSendResolveType(
            Object messageSend,
            Object currentScope,
            Throwable error,
            InvocationRewriteState originalState
    ) {
        if (messageSend == null || currentScope == null || error == null) {
            return false;
        }
        InvocationRewriteState state = originalState;
        try {
            if (state == null) {
                state = captureInvocationRewriteState(messageSend);
            }
            Object recovered = tryRecoverMessageSendResolveTypeFailure(
                    messageSend,
                    currentScope,
                    unwrapInvocationFailure(error)
            );
            if (recovered == null) {
                if (tryResolveMessageSendWithRecoveredMethodBinding(
                        messageSend,
                        currentScope,
                        unwrapInvocationFailure(error),
                        state
                )) {
                    return true;
                }
                restoreInvocationRewriteState(messageSend, state);
                return false;
            }
            invokeMethod(messageSend, "resolveType", currentScope);
            Object binding = getFieldValue(messageSend, "binding");
            if (binding != null && !isProblem(binding)) {
                return true;
            }
            Object resolvedType = findField(messageSend.getClass(), "resolvedType") != null
                    ? getFieldValue(messageSend, "resolvedType")
                    : null;
            return resolvedType != null && !isProblem(resolvedType);
        } catch (Exception ignored) {
        }
        try {
            if (state != null) {
                restoreInvocationRewriteState(messageSend, state);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static Object tryResolveMessageSendWithDirectExtensionBinding(
            Object messageSend,
            Object currentScope,
            Throwable error,
            InvocationRewriteState entryState
    ) {
        if (messageSend == null || currentScope == null || error == null) {
            return null;
        }
        Throwable unwrapped = unwrapInvocationFailure(error);
        if (!(unwrapped instanceof ArrayIndexOutOfBoundsException) && !(unwrapped instanceof NullPointerException)) {
            return null;
        }
        InvocationRewriteState rollbackState = null;
        try {
            String selectorName = extractInvocationSelectorName(messageSend);
            if (selectorName == null || selectorName.isEmpty()) {
                return null;
            }
            Object receiverType = getFieldValue(messageSend, "actualReceiverType");
            if ((receiverType == null || isProblem(receiverType)) && entryState != null) {
                receiverType = entryState.actualReceiverType;
            }
            if (receiverType == null || isProblem(receiverType)) {
                receiverType = resolveInvocationReceiverType(currentScope, messageSend);
            }
            if (receiverType == null || isProblem(receiverType)) {
                return null;
            }
            Object[] arguments = getFieldValue(messageSend, "arguments") instanceof Object[]
                    ? (Object[]) getFieldValue(messageSend, "arguments")
                    : emptyExpressionArray(messageSend);
            Object[] currentArgumentTypes = getFieldValue(messageSend, "argumentTypes") instanceof Object[]
                    ? (Object[]) getFieldValue(messageSend, "argumentTypes")
                    : new Object[0];
            Object[] entryArgumentTypes = entryState != null && entryState.argumentTypes instanceof Object[]
                    ? (Object[]) entryState.argumentTypes
                    : new Object[0];
            Object parameterBinding = getFieldValue(messageSend, "binding");
            if ((parameterBinding == null || isProblem(parameterBinding)) && entryState != null) {
                parameterBinding = entryState.binding;
            }
            Object[] parameters = parameterBinding != null && getFieldValue(parameterBinding, "parameters") instanceof Object[]
                    ? (Object[]) getFieldValue(parameterBinding, "parameters")
                    : new Object[0];
            boolean hiddenReceiverShape = receiverType != null
                    && (parameters.length == arguments.length + 1
                    || currentArgumentTypes.length == arguments.length + 1
                    || entryArgumentTypes.length == arguments.length + 1);
            Object[] repairedArgumentTypes = newTypedFieldArray(messageSend, "argumentTypes", arguments.length, null);
            for (int index = 0; index < arguments.length; index++) {
                int shiftedIndex = hiddenReceiverShape ? index + 1 : index;
                Object repairedType = index < currentArgumentTypes.length ? currentArgumentTypes[index] : null;
                if ((repairedType == null || isProblem(repairedType)) && shiftedIndex < currentArgumentTypes.length) {
                    repairedType = currentArgumentTypes[shiftedIndex];
                }
                if ((repairedType == null || isProblem(repairedType)) && index < entryArgumentTypes.length) {
                    repairedType = entryArgumentTypes[index];
                }
                if ((repairedType == null || isProblem(repairedType)) && shiftedIndex < entryArgumentTypes.length) {
                    repairedType = entryArgumentTypes[shiftedIndex];
                }
                repairedType = resolveRecoverableInvocationArgumentType(repairedType, arguments[index]);
                if ((repairedType == null || isProblem(repairedType)) && shiftedIndex < parameters.length) {
                    repairedType = resolveRecoverableInvocationArgumentType(parameters[shiftedIndex], arguments[index]);
                }
                if (repairedType == null || isProblem(repairedType)) {
                    return null;
                }
                repairedArgumentTypes[index] = repairedType;
            }
            rollbackState = captureInvocationRewriteState(messageSend);
            if (findField(messageSend.getClass(), "argumentTypes") != null) {
                setFieldValue(messageSend, "argumentTypes", repairedArgumentTypes);
            }
            Object recoveredBinding = findExtension(
                    currentScope,
                    receiverType,
                    selectorName,
                    repairedArgumentTypes,
                    messageSend
            );
            if (recoveredBinding == null || isProblem(recoveredBinding)) {
                restoreInvocationRewriteState(messageSend, rollbackState);
                return null;
            }
            if (findField(messageSend.getClass(), "binding") != null) {
                setFieldValue(messageSend, "binding", recoveredBinding);
            }
            if (findField(messageSend.getClass(), "actualReceiverType") != null) {
                setFieldValue(messageSend, "actualReceiverType", receiverType);
            }
            Object returnType = getFieldValue(recoveredBinding, "returnType");
            if ((returnType == null || isProblem(returnType)) && entryState != null) {
                returnType = entryState.resolvedType;
            }
            if (returnType == null || isProblem(returnType)) {
                restoreInvocationRewriteState(messageSend, rollbackState);
                return null;
            }
            if (findField(messageSend.getClass(), "resolvedType") != null) {
                setFieldValue(messageSend, "resolvedType", returnType);
            }
            if (findField(messageSend.getClass(), "argumentsHaveErrors") != null) {
                setFieldValue(messageSend, "argumentsHaveErrors", false);
            }
            if (findField(messageSend.getClass(), "constant") != null && getFieldValue(messageSend, "constant") == null) {
                Object notAConstant = getNotAConstant(messageSend);
                if (notAConstant != null) {
                    setFieldValue(messageSend, "constant", notAConstant);
                }
            }
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconCore] resolveType directRecovered"
                        + ": file=" + describeCompilationUnitFileName(resolveCompilationResultFromScope(currentScope))
                        + ", selector=" + selectorName
                        + ", source=" + readIntField(messageSend, "sourceStart") + "-" + readIntField(messageSend, "sourceEnd")
                        + ", receiverType=" + describeTypeDebug(receiverType)
                        + ", argumentTypes=" + describeTypeArrayDetailed(repairedArgumentTypes)
                        + ", binding=" + describeMethodBindingDetailed(recoveredBinding)
                        + ", error=" + unwrapped.getClass().getName() + ": " + unwrapped.getMessage());
            }
            return returnType;
        } catch (Exception ignored) {
        }
        try {
            if (rollbackState != null) {
                restoreInvocationRewriteState(messageSend, rollbackState);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean tryResolveMessageSendWithRecoveredMethodBinding(
            Object messageSend,
            Object currentScope,
            Throwable error,
            InvocationRewriteState entryState
    ) {
        if (messageSend == null || currentScope == null || error == null) {
            return false;
        }
        if (!(error instanceof ArrayIndexOutOfBoundsException)
                && !(error instanceof NullPointerException)
                && !(error instanceof StackOverflowError)) {
            return false;
        }
        InvocationRewriteState rollbackState = null;
        try {
            String selectorName = extractInvocationSelectorName(messageSend);
            if (selectorName == null || selectorName.isEmpty()) {
                return false;
            }
            Object receiverType = findField(messageSend.getClass(), "actualReceiverType") != null
                    ? getFieldValue(messageSend, "actualReceiverType")
                    : null;
            if ((receiverType == null || isProblem(receiverType)) && entryState != null) {
                receiverType = entryState.actualReceiverType;
            }
            if (receiverType == null || isProblem(receiverType)) {
                receiverType = resolveInvocationReceiverType(currentScope, messageSend);
            }
            if (receiverType == null || isProblem(receiverType)) {
                return false;
            }
            Object[] arguments = getFieldValue(messageSend, "arguments") instanceof Object[]
                    ? (Object[]) getFieldValue(messageSend, "arguments")
                    : emptyExpressionArray(messageSend);
            Object[] currentArgumentTypes = getFieldValue(messageSend, "argumentTypes") instanceof Object[]
                    ? (Object[]) getFieldValue(messageSend, "argumentTypes")
                    : new Object[0];
            Object[] repairedArgumentTypes = newTypedFieldArray(messageSend, "argumentTypes", arguments.length, null);
            Object[] parameterTypes = null;
            boolean requiresFallbackParameters = false;
            for (int index = 0; index < arguments.length; index++) {
                Object repairedType = index < currentArgumentTypes.length ? currentArgumentTypes[index] : null;
                repairedType = resolveRecoverableInvocationArgumentType(repairedType, arguments[index]);
                if (repairedType == null || isProblem(repairedType)) {
                    requiresFallbackParameters = true;
                    break;
                }
                repairedArgumentTypes[index] = repairedType;
            }
            Object recoveredBinding = null;
            if (!requiresFallbackParameters) {
                recoveredBinding = invokeMethod(
                        currentScope,
                        "getMethod",
                        receiverType,
                        selectorName.toCharArray(),
                        repairedArgumentTypes,
                        messageSend
                );
                if (isProblem(recoveredBinding)) {
                    recoveredBinding = null;
                }
            }
            if (recoveredBinding == null) {
                recoveredBinding = findSingleInstanceMethodBinding(receiverType, selectorName, arguments.length);
                if (recoveredBinding == null || isProblem(recoveredBinding)) {
                    return false;
                }
                parameterTypes = getFieldValue(recoveredBinding, "parameters") instanceof Object[]
                        ? (Object[]) getFieldValue(recoveredBinding, "parameters")
                        : new Object[0];
                if (parameterTypes.length != arguments.length) {
                    return false;
                }
                for (int index = 0; index < arguments.length; index++) {
                    Object repairedType = resolveRecoverableInvocationArgumentType(parameterTypes[index], arguments[index]);
                    if (repairedType == null || isProblem(repairedType)) {
                        return false;
                    }
                    repairedArgumentTypes[index] = repairedType;
                }
                Object scopedBinding = invokeMethod(
                        currentScope,
                        "getMethod",
                        receiverType,
                        selectorName.toCharArray(),
                        repairedArgumentTypes,
                        messageSend
                );
                if (scopedBinding != null && !isProblem(scopedBinding)) {
                    recoveredBinding = scopedBinding;
                }
            }
            parameterTypes = parameterTypes != null
                    ? parameterTypes
                    : (getFieldValue(recoveredBinding, "parameters") instanceof Object[]
                    ? (Object[]) getFieldValue(recoveredBinding, "parameters")
                    : new Object[0]);
            rollbackState = captureInvocationRewriteState(messageSend);
            applyRecoveredMessageSendReceiverState(messageSend, receiverType);
            if (findField(messageSend.getClass(), "argumentTypes") != null) {
                setFieldValue(messageSend, "argumentTypes", repairedArgumentTypes);
            }
            for (int index = 0; index < arguments.length && index < parameterTypes.length; index++) {
                Object argument = arguments[index];
                Object parameterType = parameterTypes[index];
                if (isLambdaExpression(argument) && parameterType != null && !isProblem(parameterType)) {
                    stabilizeFunctionalArgumentWithoutReresolve(argument, parameterType, currentScope);
                    rememberSuccessfulFunctionalArgumentRange(currentScope, parameterType, argument);
                }
            }
            if (findField(messageSend.getClass(), "binding") != null) {
                setFieldValue(messageSend, "binding", recoveredBinding);
            }
            Object returnType = getFieldValue(recoveredBinding, "returnType");
            if (returnType == null || isProblem(returnType)) {
                restoreInvocationRewriteState(messageSend, rollbackState);
                return false;
            }
            if (findField(messageSend.getClass(), "resolvedType") != null) {
                setFieldValue(messageSend, "resolvedType", returnType);
            }
            if (findField(messageSend.getClass(), "argumentsHaveErrors") != null) {
                setFieldValue(messageSend, "argumentsHaveErrors", false);
            }
            if (findField(messageSend.getClass(), "constant") != null && getFieldValue(messageSend, "constant") == null) {
                Object notAConstant = getNotAConstant(messageSend);
                if (notAConstant != null) {
                    setFieldValue(messageSend, "constant", notAConstant);
                }
            }
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconCore] resolveType regularRecovered"
                        + ": file=" + describeCompilationUnitFileName(resolveCompilationResultFromScope(currentScope))
                        + ", selector=" + selectorName
                        + ", source=" + readIntField(messageSend, "sourceStart") + "-" + readIntField(messageSend, "sourceEnd")
                        + ", receiverType=" + describeTypeDebug(receiverType)
                        + ", argumentTypes=" + describeTypeArrayDetailed(repairedArgumentTypes)
                        + ", binding=" + describeMethodBindingDetailed(recoveredBinding)
                        + ", error=" + error.getClass().getName() + ": " + error.getMessage());
            }
            return true;
        } catch (Exception ignored) {
        }
        try {
            if (rollbackState != null) {
                restoreInvocationRewriteState(messageSend, rollbackState);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void applyRecoveredMessageSendReceiverState(Object messageSend, Object receiverType) throws Exception {
        if (messageSend == null || receiverType == null || isProblem(receiverType)) {
            return;
        }
        if (findField(messageSend.getClass(), "actualReceiverType") != null) {
            setFieldValue(messageSend, "actualReceiverType", receiverType);
        }
        Object receiver = findField(messageSend.getClass(), "receiver") != null
                ? getFieldValue(messageSend, "receiver")
                : null;
        if (receiver == null) {
            return;
        }
        if (findField(receiver.getClass(), "resolvedType") != null) {
            Object resolvedType = getFieldValue(receiver, "resolvedType");
            if (resolvedType == null || isProblem(resolvedType)) {
                setFieldValue(receiver, "resolvedType", receiverType);
            }
        }
        if (findField(receiver.getClass(), "actualReceiverType") != null) {
            Object actualReceiverType = getFieldValue(receiver, "actualReceiverType");
            if (actualReceiverType == null || isProblem(actualReceiverType)) {
                setFieldValue(receiver, "actualReceiverType", receiverType);
            }
        }
        if (findField(receiver.getClass(), "constant") != null && getFieldValue(receiver, "constant") == null) {
            Object notAConstant = getNotAConstant(messageSend);
            if (notAConstant != null) {
                setFieldValue(receiver, "constant", notAConstant);
            }
        }
    }

    public static Object captureMessageSendResolveTypeEntryState(Object messageSend) {
        try {
            return captureInvocationRewriteState(messageSend);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Object tryRecoverMessageSendResolveTypeOnExit(
            Object messageSend,
            Object currentScope,
            Throwable throwable,
            Object entryState
    ) {
        if (messageSend == null || currentScope == null || throwable == null) {
            return null;
        }
        int depth = MESSAGE_SEND_RESOLVE_RECOVERY_DEPTH.get();
        if (depth > 0) {
            return null;
        }
        try {
            MESSAGE_SEND_RESOLVE_RECOVERY_DEPTH.set(depth + 1);
            InvocationRewriteState state = entryState instanceof InvocationRewriteState
                    ? (InvocationRewriteState) entryState
                    : null;
            Object anonymousRecovered = tryRecoverAnonymousLocalInvocationResolveType(
                    messageSend,
                    currentScope,
                    throwable,
                    state
            );
            if (anonymousRecovered != null) {
                return anonymousRecovered;
            }
            if (!tryRecoverAndRetryMessageSendResolveType(messageSend, currentScope, throwable, state)) {
                Object directRecovered = tryResolveMessageSendWithDirectExtensionBinding(messageSend, currentScope, throwable, state);
                if (directRecovered == null) {
                    return null;
                }
                return directRecovered;
            }
            Object resolvedType = findField(messageSend.getClass(), "resolvedType") != null
                    ? getFieldValue(messageSend, "resolvedType")
                    : null;
            if ((resolvedType == null || isProblem(resolvedType))
                    && findField(messageSend.getClass(), "binding") != null) {
                Object binding = getFieldValue(messageSend, "binding");
                resolvedType = binding != null ? getFieldValue(binding, "returnType") : null;
            }
            return resolvedType;
        } catch (Exception ignored) {
            return null;
        } finally {
            MESSAGE_SEND_RESOLVE_RECOVERY_DEPTH.set(depth);
        }
    }

    private static Object tryRecoverAnonymousLocalInvocationResolveType(
            Object messageSend,
            Object currentScope,
            Throwable throwable,
            InvocationRewriteState entryState
    ) {
        if (messageSend == null || currentScope == null || throwable == null) {
            return null;
        }
        Throwable unwrapped = unwrapInvocationFailure(throwable);
        if (!(unwrapped instanceof NullPointerException)
                || unwrapped.getMessage() == null
                || !unwrapped.getMessage().contains("MethodScope.isInsideInitializer")) {
            return null;
        }
        try {
            String selectorName = extractInvocationSelectorName(messageSend);
            if (selectorName == null || selectorName.isEmpty()) {
                return null;
            }
            Object receiver = getFieldValue(messageSend, "receiver");
            if (receiver == null) {
                return null;
            }
            Object anonymousType = findField(receiver.getClass(), "anonymousType") != null
                    ? getFieldValue(receiver, "anonymousType")
                    : null;
            if (anonymousType == null) {
                return null;
            }
            Object[] arguments = getFieldValue(messageSend, "arguments") instanceof Object[]
                    ? (Object[]) getFieldValue(messageSend, "arguments")
                    : emptyExpressionArray(messageSend);
            Object methodBinding = findAnonymousTypeMethodBinding(anonymousType, selectorName, arguments.length);
            Object returnType = methodBinding != null ? getFieldValue(methodBinding, "returnType") : null;
            if (returnType == null || isProblem(returnType)) {
                returnType = findAnonymousTypeMethodReturnType(anonymousType, selectorName, arguments.length, messageSend);
            }
            if (returnType == null || isProblem(returnType)) {
                return null;
            }
            if (methodBinding != null && !isProblem(methodBinding) && findField(messageSend.getClass(), "binding") != null) {
                setFieldValue(messageSend, "binding", methodBinding);
            }
            Object actualReceiverType = findField(messageSend.getClass(), "actualReceiverType") != null
                    ? getFieldValue(messageSend, "actualReceiverType")
                    : null;
            if ((actualReceiverType == null || isProblem(actualReceiverType)) && entryState != null) {
                actualReceiverType = entryState.actualReceiverType;
            }
            if ((actualReceiverType == null || isProblem(actualReceiverType))
                    && findField(receiver.getClass(), "resolvedType") != null) {
                actualReceiverType = getFieldValue(receiver, "resolvedType");
            }
            if (actualReceiverType != null
                    && !isProblem(actualReceiverType)
                    && findField(messageSend.getClass(), "actualReceiverType") != null) {
                setFieldValue(messageSend, "actualReceiverType", actualReceiverType);
            }
            if (findField(messageSend.getClass(), "resolvedType") != null) {
                setFieldValue(messageSend, "resolvedType", returnType);
            }
            if (findField(messageSend.getClass(), "argumentsHaveErrors") != null) {
                setFieldValue(messageSend, "argumentsHaveErrors", false);
            }
            if (findField(messageSend.getClass(), "constant") != null && getFieldValue(messageSend, "constant") == null) {
                Object notAConstant = getNotAConstant(messageSend);
                if (notAConstant != null) {
                    setFieldValue(messageSend, "constant", notAConstant);
                }
            }
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconCore] resolveType anonymousRecovered"
                        + ": file=" + describeCompilationUnitFileName(resolveCompilationResultFromScope(currentScope))
                        + ", selector=" + selectorName
                        + ", source=" + readIntField(messageSend, "sourceStart") + "-" + readIntField(messageSend, "sourceEnd")
                        + ", binding=" + describeMethodBindingDetailed(methodBinding)
                        + ", returnType=" + describeTypeDebug(returnType)
                        + ", error=" + unwrapped.getClass().getName() + ": " + unwrapped.getMessage());
            }
            return returnType;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object findAnonymousTypeMethodBinding(
            Object anonymousType,
            String selectorName,
            int argumentCount
    ) throws Exception {
        Object[] methods = getFieldValue(anonymousType, "methods") instanceof Object[]
                ? (Object[]) getFieldValue(anonymousType, "methods")
                : null;
        if (methods == null) {
            return null;
        }
        for (Object method : methods) {
            if (!matchesMethodSelector(method, selectorName)) {
                continue;
            }
            Object binding = findField(method.getClass(), "binding") != null
                    ? getFieldValue(method, "binding")
                    : null;
            if (binding == null || isProblem(binding)) {
                continue;
            }
            Object[] parameters = getFieldValue(binding, "parameters") instanceof Object[]
                    ? (Object[]) getFieldValue(binding, "parameters")
                    : null;
            int parameterCount = parameters == null ? 0 : parameters.length;
            if (parameterCount == argumentCount) {
                return binding;
            }
        }
        return null;
    }

    private static Object findAnonymousTypeMethodReturnType(
            Object anonymousType,
            String selectorName,
            int argumentCount,
            Object anchor
    ) throws Exception {
        Object[] methods = getFieldValue(anonymousType, "methods") instanceof Object[]
                ? (Object[]) getFieldValue(anonymousType, "methods")
                : null;
        if (methods == null) {
            return null;
        }
        for (Object method : methods) {
            if (!matchesMethodSelector(method, selectorName)) {
                continue;
            }
            Object[] arguments = getFieldValue(method, "arguments") instanceof Object[]
                    ? (Object[]) getFieldValue(method, "arguments")
                    : null;
            int parameterCount = arguments == null ? 0 : arguments.length;
            if (parameterCount != argumentCount) {
                continue;
            }
            Object returnType = findField(method.getClass(), "returnType") != null
                    ? getFieldValue(method, "returnType")
                    : null;
            if (returnType == null || isVoidTypeReference(returnType)) {
                return resolveVoidTypeBinding(anchor);
            }
            Object resolvedType = findField(returnType.getClass(), "resolvedType") != null
                    ? getFieldValue(returnType, "resolvedType")
                    : null;
            if (resolvedType != null && !isProblem(resolvedType)) {
                return resolvedType;
            }
        }
        return null;
    }

    private static boolean matchesMethodSelector(Object methodDeclaration, String selectorName) throws Exception {
        if (methodDeclaration == null || selectorName == null || selectorName.isEmpty()) {
            return false;
        }
        Object selector = findField(methodDeclaration.getClass(), "selector") != null
                ? getFieldValue(methodDeclaration, "selector")
                : null;
        return selector instanceof char[] && selectorName.equals(new String((char[]) selector));
    }

    private static boolean isVoidTypeReference(Object typeReference) throws Exception {
        if (typeReference == null) {
            return false;
        }
        Object token = findField(typeReference.getClass(), "token") != null
                ? getFieldValue(typeReference, "token")
                : null;
        if (token instanceof char[]) {
            return "void".equals(new String((char[]) token));
        }
        Object tokens = findField(typeReference.getClass(), "tokens") != null
                ? getFieldValue(typeReference, "tokens")
                : null;
        return tokens instanceof char[][]
                && ((char[][]) tokens).length == 1
                && "void".equals(new String(((char[][]) tokens)[0]));
    }

    private static Object resolveVoidTypeBinding(Object anchor) throws Exception {
        Class<?> typeBindingClass = loadClass(TYPE_BINDING_CLASS, anchor);
        Field voidField = findField(typeBindingClass, "VOID");
        return voidField != null ? voidField.get(null) : null;
    }

    public static void prepareMessageSendGenerateCode(Object messageSend, Object currentScope) {
        if (messageSend == null || currentScope == null) {
            return;
        }
        try {
            Object receiver = getFieldValue(messageSend, "receiver");
            if (receiver == null || !isAstNode(receiver)) {
                return;
            }
            String simpleName = receiver.getClass().getSimpleName();
            if (!"SingleNameReference".equals(simpleName) && !"QualifiedNameReference".equals(simpleName)) {
                return;
            }
            Object binding = getFieldValue(receiver, "binding");
            if (!isLocalVariableBinding(binding)) {
                return;
            }
            String referenceName = describeReferenceName(receiver, binding);
            Object syntheticBinding = findSyntheticBindingInScope(currentScope, binding);
            if (syntheticBinding == null || syntheticBinding == binding) {
                if (isInterestingCapturedReference(referenceName)) {
                    String key = "miss|" + describeSourceRange(messageSend)
                            + "|" + System.identityHashCode(receiver)
                            + "|" + describeIdentity(binding);
                    if (MESSAGE_SEND_GENERATE_CODE_PREPARE_KEYS.add(key)) {
                        Util.log("[ZirconCore] messageSendGenerateCode prepare miss"
                                + ": selector=" + getSelectorName(getFieldValue(messageSend, "binding"))
                                + ", source=" + describeSourceRange(messageSend)
                                + ", receiverSource=" + describeSourceRange(receiver)
                                + ", receiverId=" + System.identityHashCode(receiver)
                                + ", name=" + referenceName
                                + ", binding=" + describeBinding(binding)
                                + "#" + describeIdentity(binding)
                                + ", scopeChain=" + describeScopeSyntheticChain(currentScope));
                    }
                }
                return;
            }
            normalizeReceiverBindingForGenerateCode(currentScope, syntheticBinding);
            setFieldValue(receiver, "binding", syntheticBinding);
            if (findField(receiver.getClass(), "resolvedType") != null) {
                setFieldValue(receiver, "resolvedType", getFieldValue(syntheticBinding, "type"));
            }
            if (findField(messageSend.getClass(), "actualReceiverType") != null) {
                setFieldValue(messageSend, "actualReceiverType", getFieldValue(syntheticBinding, "type"));
            }
            if (isInterestingCapturedReference(referenceName)) {
                String key = describeSourceRange(messageSend)
                        + "|" + System.identityHashCode(receiver)
                        + "|" + describeIdentity(binding)
                        + "|" + describeIdentity(syntheticBinding);
                if (MESSAGE_SEND_GENERATE_CODE_PREPARE_KEYS.add(key)) {
                    Util.log("[ZirconCore] messageSendGenerateCode prepare"
                            + ": selector=" + getSelectorName(getFieldValue(messageSend, "binding"))
                            + ", source=" + describeSourceRange(messageSend)
                            + ", receiverSource=" + describeSourceRange(receiver)
                            + ", receiverId=" + System.identityHashCode(receiver)
                            + ", name=" + referenceName
                            + ", from=" + describeBinding(binding)
                            + "#" + describeIdentity(binding)
                            + ", to=" + describeBinding(syntheticBinding)
                            + "#" + describeIdentity(syntheticBinding)
                            + ", toState=" + describeLocalBindingState(syntheticBinding)
                            + ", scope=" + describeScopeDebug(currentScope));
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static Object tryShortCircuitMessageSendAnalyseCode(
            Object messageSend,
            Object currentScope,
            Object flowContext,
            Object flowInfo
    ) {
        if (messageSend == null) {
            return null;
        }
        String selectorName = getSelectorName(messageSend);
        if (ELVIS_MARKER_SELECTOR.equals(selectorName)) {
            if (currentScope != null) {
                try {
                    prepareElvisMarkerInvocation(messageSend, currentScope);
                } catch (Exception ignored) {
                }
            }
            return flowInfo;
        }
        try {
            if (currentScope != null) {
                rebindProblemFieldLocalReferences(
                        messageSend,
                        currentScope,
                        currentScope,
                        new IdentityHashMap<>(),
                        0
                );
                stabilizeMessageSendFunctionalArguments(messageSend, currentScope);
            }
            Object binding = getFieldValue(messageSend, "binding");
            if (binding != null && !isProblem(binding)) {
                return null;
            }
            if (currentScope == null) {
                logMessageSendAnalyseCodeState("null-scope", messageSend, null, null);
                return flowInfo;
            }
            if (!hydrateMessageSendArgumentTypes(messageSend)) {
                // Lambda inference can ask analyseCode to inspect a copied AST
                // before every captured name has a binding. Calling resolveType
                // in that state enters JDT's invocation checks with a null
                // argumentType and throws. The enclosing inference pass will
                // revisit this node after bindings have been established.
                return flowInfo;
            }
            InvocationRewriteState resolveState = captureInvocationRewriteState(messageSend);
            try {
                invokeMethod(messageSend, "resolveType", currentScope);
            } catch (Exception e) {
                if (!tryRecoverAndRetryMessageSendResolveType(messageSend, currentScope, e, resolveState)) {
                    throw e;
                }
            }
            Object resolvedBinding = getFieldValue(messageSend, "binding");
            if (resolvedBinding != null && !isProblem(resolvedBinding)) {
                return null;
            }
            logMessageSendAnalyseCodeState("unresolved", messageSend, currentScope, null);
            return flowInfo;
        } catch (Exception e) {
            logMessageSendAnalyseCodeState("prepare-failed", messageSend, currentScope, e);
            return flowInfo;
        }
    }

    public static void prepareMessageSendAnalyseCode(Object messageSend, Object currentScope) {
        tryShortCircuitMessageSendAnalyseCode(messageSend, currentScope, null, null);
    }

    public static Integer tryShortCircuitMessageSendNullStatus(Object messageSend) {
        if (messageSend == null || !Util.getBooleanProperty("zircon.vscode", false)) {
            return null;
        }
        try {
            if (getFieldValue(messageSend, "binding") != null) {
                return null;
            }
            if (Util.isDebugEnabled()) {
                Object receiver = getFieldValue(messageSend, "receiver");
                Util.log("[ZirconCore] shortCircuitMessageSendNullStatus"
                        + ": selector=" + extractInvocationSelectorName(messageSend)
                        + ", source=" + describeSourceRange(messageSend)
                        + ", receiver=" + (receiver == null ? "null" : receiver.getClass().getSimpleName())
                        + ", actualReceiverType=" + describeTypeDebug(getFieldValue(messageSend, "actualReceiverType")));
            }
            return 0;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void prepareElvisMarkerInvocation(Object messageSend, Object currentScope) throws Exception {
        if (messageSend == null || currentScope == null || !isElvisMarkerInvocation(messageSend)) {
            return;
        }
        normalizeElvisMarkerInvocationShape(messageSend, currentScope);
        hydrateElvisMarkerInvocationBinding(messageSend, currentScope);
    }

    private static void normalizeElvisMarkerInvocationShape(Object messageSend, Object currentScope) throws Exception {
        if (messageSend == null) {
            return;
        }
        Object receiver = getFieldValue(messageSend, "receiver");
        if (receiver == null) {
            Object normalizedReceiver = createQualifiedNameReference(
                    messageSend,
                    toCompoundName(BI_OP_QUALIFIED_NAME),
                    null
            );
            if (normalizedReceiver != null) {
                setFieldValue(messageSend, "receiver", normalizedReceiver);
            }
        }
        Object selector = getFieldValue(messageSend, "selector");
        if (!(selector instanceof char[]) || !ELVIS_MARKER_SELECTOR.equals(new String((char[]) selector))) {
            setFieldValue(messageSend, "selector", ELVIS_MARKER_SELECTOR.toCharArray());
        }
        if (!(getFieldValue(messageSend, "arguments") instanceof Object[])) {
            setFieldValue(messageSend, "arguments", emptyExpressionArray(messageSend));
        }
        if (findField(messageSend.getClass(), "argumentTypes") != null
                && !(getFieldValue(messageSend, "argumentTypes") instanceof Object[])) {
            setFieldValue(messageSend, "argumentTypes", emptyTypeBindingArray(currentScope != null ? currentScope : messageSend));
        }
        Object notAConstant = getNotAConstant(messageSend);
        if (notAConstant != null && findField(messageSend.getClass(), "constant") != null) {
            setFieldValue(messageSend, "constant", notAConstant);
        }
    }

    private static void hydrateElvisMarkerInvocationBinding(Object messageSend, Object currentScope) throws Exception {
        if (messageSend == null || currentScope == null) {
            return;
        }
        Object binding = getFieldValue(messageSend, "binding");
        if (binding != null && !isProblem(binding)) {
            return;
        }
        Object biOpClass = null;
        try {
            biOpClass = resolveTypeBindingByName(currentScope, BI_OP_QUALIFIED_NAME);
        } catch (Exception ignored) {
            biOpClass = null;
        }
        if (biOpClass != null) {
            Object receiver = getFieldValue(messageSend, "receiver");
            if (receiver != null) {
                if (findField(receiver.getClass(), "binding") != null) {
                    setFieldValue(receiver, "binding", biOpClass);
                }
                if (findField(receiver.getClass(), "resolvedType") != null) {
                    setFieldValue(receiver, "resolvedType", biOpClass);
                }
                if (findField(receiver.getClass(), "actualReceiverType") != null) {
                    setFieldValue(receiver, "actualReceiverType", biOpClass);
                }
            }
            if (findField(messageSend.getClass(), "actualReceiverType") != null) {
                setFieldValue(messageSend, "actualReceiverType", biOpClass);
            }
            Object markerBinding = null;
            try {
                markerBinding = findStaticMethodBinding(biOpClass, ELVIS_MARKER_SELECTOR, 0);
            } catch (Exception ignored) {
                markerBinding = null;
            }
            if (markerBinding != null && !isProblem(markerBinding)) {
                setFieldValue(messageSend, "binding", markerBinding);
                Object returnType = getFieldValue(markerBinding, "returnType");
                if (returnType != null && findField(messageSend.getClass(), "resolvedType") != null) {
                    setFieldValue(messageSend, "resolvedType", returnType);
                }
                return;
            }
        }
        Object booleanType = resolveBooleanTypeBinding(currentScope, messageSend);
        if (booleanType != null && findField(messageSend.getClass(), "resolvedType") != null) {
            setFieldValue(messageSend, "resolvedType", booleanType);
        }
    }

    private static Object resolveBooleanTypeBinding(Object scope, Object anchor) throws Exception {
        if (scope == null || anchor == null) {
            return null;
        }
        try {
            Object literal = createBooleanLiteralExpression(anchor, false);
            return literal != null ? invokeMethod(literal, "resolveType", scope) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void stabilizeMessageSendFunctionalArguments(Object messageSend, Object currentScope) throws Exception {
        if (messageSend == null || currentScope == null) {
            return;
        }
        Object[] arguments = getFieldValue(messageSend, "arguments") instanceof Object[]
                ? (Object[]) getFieldValue(messageSend, "arguments")
                : new Object[0];
        if (arguments.length == 0) {
            return;
        }
        Object binding = getFieldValue(messageSend, "binding");
        Object[] parameters = binding != null && getFieldValue(binding, "parameters") instanceof Object[]
                ? (Object[]) getFieldValue(binding, "parameters")
                : new Object[0];
        for (int index = 0; index < arguments.length; index++) {
            Object argument = arguments[index];
            if (argument == null || !isFunctionalInvocationArgument(argument)) {
                continue;
            }
            Object expectedType = index < parameters.length ? parameters[index] : getFieldValue(argument, "expectedType");
            stabilizeFunctionalArgumentState(argument, expectedType, currentScope);
        }
    }

    private static void logMessageSendAnalyseCodeState(String kind, Object messageSend, Object currentScope, Exception error) {
        String key = kind + "|" + getSelectorName(messageSend) + "|" + describeSourceRange(messageSend);
        if (!MESSAGE_SEND_ANALYSE_CODE_FAILURE_KEYS.add(key)) {
            return;
        }
        try {
            Object[] arguments = getFieldValue(messageSend, "arguments") instanceof Object[]
                    ? (Object[]) getFieldValue(messageSend, "arguments")
                    : new Object[0];
            StringBuilder builder = new StringBuilder("[ZirconCore] messageSendAnalyseCode ")
                    .append(kind)
                    .append(": selector=").append(getSelectorName(messageSend))
                    .append(", source=").append(describeSourceRange(messageSend))
                    .append(", node=").append(describeAstNodeState(messageSend, 1))
                    .append(", receiver=").append(describeAstNodeState(getFieldValue(messageSend, "receiver"), 1))
                    .append(", argCount=").append(arguments.length)
                    .append(", firstArg=").append(arguments.length > 0 ? describeAstNodeState(arguments[0], 1) : "null");
            if (currentScope != null) {
                builder.append(", scope=").append(describeScopeDebug(currentScope));
            }
            if (error != null) {
                builder.append(", error=").append(error.getClass().getName()).append(": ").append(error.getMessage());
            }
            Util.log(builder.toString());
            if (error != null && "prepare-failed".equals(kind)) {
                String selectorKey = getSelectorName(messageSend);
                if (MESSAGE_SEND_ANALYSE_CODE_STACK_KEYS.add(selectorKey)) {
                    Throwable root = error instanceof java.lang.reflect.InvocationTargetException
                            && error.getCause() != null
                            ? error.getCause()
                            : error;
                    Util.log("[ZirconCore] messageSendAnalyseCode " + kind + " stack: selector=" + selectorKey);
                    Util.log(Util.stackTrace(root));
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void prepareLambdaGenerateCode(Object lambdaExpression, Object currentScope) {
        if (!isLambdaExpression(lambdaExpression) || currentScope == null) {
            return;
        }
        try {
            Object lambdaBinding = getFieldValue(lambdaExpression, "binding");
            if (lambdaBinding == null || isProblem(lambdaBinding)) {
                lambdaBinding = getFieldValue(lambdaExpression, "descriptor");
            }
            if (lambdaBinding != null && !isProblem(lambdaBinding)) {
                syncLambdaArgumentBindingTypes(lambdaExpression, lambdaBinding);
            }
            normalizeLambdaLocalBindingsForCodegen(lambdaExpression);
            rebindLambdaArgumentReferences(lambdaExpression);
            normalizeExpressionConstantsForCodegen(getFieldValue(lambdaExpression, "body"), new IdentityHashMap<>(), 0);
            normalizeCastTypeBindingsForCodegen(getFieldValue(lambdaExpression, "body"), new IdentityHashMap<>(), 0);
            Object methodScope = findNearestMethodScope(currentScope);
            if (methodScope == null) {
                return;
            }
            Object currentLambda = findField(methodScope.getClass(), "referenceContext") != null
                    ? getFieldValue(methodScope, "referenceContext")
                    : null;
            if (!isLambdaExpression(currentLambda)) {
                return;
            }
            Object[] outerLocals = getFieldValue(lambdaExpression, "outerLocalVariables") instanceof Object[]
                    ? (Object[]) getFieldValue(lambdaExpression, "outerLocalVariables")
                    : new Object[0];
            for (Object outerLocal : outerLocals) {
                if (outerLocal == null || findField(outerLocal.getClass(), "actualOuterLocalVariable") == null) {
                    continue;
                }
                Object actualOuter = getFieldValue(outerLocal, "actualOuterLocalVariable");
                Object visibleBinding = findVisibleBindingInLambdaScope(currentLambda, methodScope, actualOuter);
                if (visibleBinding != null && visibleBinding != actualOuter) {
                    setFieldValue(outerLocal, "actualOuterLocalVariable", visibleBinding);
                    String key = describeSourceRange(lambdaExpression)
                            + "|" + describeIdentity(outerLocal)
                            + "|" + describeIdentity(actualOuter)
                            + "|" + describeIdentity(visibleBinding);
                    if (LAMBDA_GENERATE_CODE_PREPARE_KEYS.add(key)) {
                        Util.log("[ZirconCore] lambdaGenerateCode prepare"
                                + ": source=" + describeSourceRange(lambdaExpression)
                                + ", outerLocal=" + describeBinding(outerLocal)
                                + "#" + describeIdentity(outerLocal)
                                + ", from=" + describeBinding(actualOuter)
                                + "#" + describeIdentity(actualOuter)
                                + ", to=" + describeBinding(visibleBinding)
                                + "#" + describeIdentity(visibleBinding)
                                + ", toState=" + describeLocalBindingState(visibleBinding)
                                + ", scope=" + describeScopeDebug(methodScope));
                    }
                }
                Object effectiveOuter = getFieldValue(outerLocal, "actualOuterLocalVariable");
                normalizeReceiverBindingForGenerateCode(currentScope, effectiveOuter);
                Object path = null;
                try {
                    path = invokeMethod(currentScope, "getEmulationPath", effectiveOuter);
                } catch (Exception ignored) {
                    path = null;
                }
                String pathKey = "path|" + describeSourceRange(lambdaExpression)
                        + "|" + describeIdentity(outerLocal)
                        + "|" + describeIdentity(effectiveOuter)
                        + "|" + describeEmulationPath(path);
                if (LAMBDA_GENERATE_CODE_PREPARE_KEYS.add(pathKey)) {
                    Util.log("[ZirconCore] lambdaGenerateCode path"
                            + ": source=" + describeSourceRange(lambdaExpression)
                            + ", outerLocal=" + describeBinding(outerLocal)
                            + "#" + describeIdentity(outerLocal)
                            + ", actualOuter=" + describeBinding(effectiveOuter)
                            + "#" + describeIdentity(effectiveOuter)
                            + ", actualOuterState=" + describeLocalBindingState(effectiveOuter)
                            + ", path=" + describeEmulationPath(path)
                            + ", scope=" + describeScopeDebug(currentScope));
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void prepareLambdaAnalyseCode(Object lambdaExpression, Object currentScope) {
        if (!isLambdaExpression(lambdaExpression)) {
            return;
        }
        try {
            if (shouldAvoidReresolvingFunctionalArgument(lambdaExpression)) {
                return;
            }
            Object expressionScope = getFieldValue(lambdaExpression, "scope");
            Object enclosingScope = getFieldValue(lambdaExpression, "enclosingScope");
            if (expressionScope == null || enclosingScope == null || currentScope == null) {
                logLambdaAnalyseScopeIssue(lambdaExpression, currentScope, expressionScope, enclosingScope, "enter");
            }
            Object effectiveScope = expressionScope != null ? expressionScope : (currentScope != null ? currentScope : enclosingScope);
            if (effectiveScope == null) {
                logLambdaAnalyseScopeIssue(lambdaExpression, currentScope, expressionScope, enclosingScope, "missing-effective-scope");
                return;
            }
            ensureLambdaAnalyseScope(lambdaExpression, effectiveScope);
            Object repairedScope = getFieldValue(lambdaExpression, "scope");
            if (expressionScope == null && repairedScope != null) {
                logLambdaAnalyseScopeIssue(lambdaExpression, currentScope, repairedScope, enclosingScope, "repaired-scope");
            }
            if (repairedScope == null) {
                logLambdaAnalyseScopeIssue(lambdaExpression, currentScope, expressionScope, enclosingScope, "missing-lambda-scope");
            }
            if (getFieldValue(lambdaExpression, "binding") != null) {
                return;
            }
            Object expectedType = getFieldValue(lambdaExpression, "expectedType");
            ensureLambdaBindingResolved(lambdaExpression, expectedType, effectiveScope);
            if (getFieldValue(lambdaExpression, "binding") != null) {
                return;
            }
            Object descriptor = getFieldValue(lambdaExpression, "descriptor");
            if (descriptor != null && !isProblem(descriptor)) {
                primeMethodBindingState(descriptor);
                setFieldValue(lambdaExpression, "binding", descriptor);
                String key = "fallback|" + describeSourceRange(lambdaExpression) + "|" + describeSourceRange(getFieldValue(lambdaExpression, "body"));
                if (FUNCTIONAL_REFRESH_BINDING_KEYS.add(key)) {
                    Util.log("[ZirconCore] lambdaAnalyseCode fallback"
                            + ": source=" + describeSourceRange(lambdaExpression)
                            + ", bodySource=" + describeSourceRange(getFieldValue(lambdaExpression, "body"))
                            + ", expectedType=" + describeTypeDebug(expectedType)
                            + ", resolvedType=" + describeTypeDebug(getFieldValue(lambdaExpression, "resolvedType"))
                            + ", descriptor=" + describeMethodBindingDetailed(descriptor)
                            + ", scope=" + describeScopeDebug(effectiveScope));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void logLambdaAnalyseScopeIssue(
            Object lambdaExpression,
            Object currentScope,
            Object expressionScope,
            Object enclosingScope,
            String phase
    ) {
        if (lambdaExpression == null) {
            return;
        }
        try {
            Object body = getFieldValue(lambdaExpression, "body");
            String key = phase
                    + "|" + describeSourceRange(lambdaExpression)
                    + "|" + describeSourceRange(body)
                    + "|" + describeScopeDebug(currentScope)
                    + "|" + describeScopeDebug(expressionScope)
                    + "|" + describeScopeDebug(enclosingScope);
            if (!LAMBDA_ANALYSE_SCOPE_KEYS.add(key)) {
                return;
            }
            Util.log("[ZirconCore] lambdaAnalyseCode scope"
                    + ": phase=" + phase
                    + ", source=" + describeSourceRange(lambdaExpression)
                    + ", bodySource=" + describeSourceRange(body)
                    + ", descriptor=" + describeMethodBindingDetailed(getFieldValue(lambdaExpression, "descriptor"))
                    + ", binding=" + describeMethodBindingDetailed(getFieldValue(lambdaExpression, "binding"))
                    + ", expectedType=" + describeTypeDebug(getFieldValue(lambdaExpression, "expectedType"))
                    + ", resolvedType=" + describeTypeDebug(getFieldValue(lambdaExpression, "resolvedType"))
                    + ", currentScope=" + describeScopeDebug(currentScope)
                    + ", expressionScope=" + describeScopeDebug(expressionScope)
                    + ", enclosingScope=" + describeScopeDebug(enclosingScope)
                    + ", bodyState=" + describeAstNodeState(body, 1));
        } catch (Exception e) {
            Util.log("[ZirconCore] lambdaAnalyseCode scope logging error: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private static void ensureLambdaAnalyseScope(Object lambdaExpression, Object currentScope) throws Exception {
        if (!isLambdaExpression(lambdaExpression) || currentScope == null) {
            return;
        }
        if (shouldAvoidReresolvingFunctionalArgument(lambdaExpression)) {
            return;
        }
        Field enclosingScopeField = findField(lambdaExpression.getClass(), "enclosingScope");
        if (enclosingScopeField != null
                && getFieldValue(lambdaExpression, "enclosingScope") == null
                && enclosingScopeField.getType().isInstance(currentScope)) {
            setFieldValue(lambdaExpression, "enclosingScope", currentScope);
        }
        Object lambdaScope = getFieldValue(lambdaExpression, "scope");
        if (lambdaScope == null) {
            Object methodScope = findNearestMethodScope(currentScope);
            if (methodScope != null) {
                lambdaScope = recreateLambdaScope(lambdaExpression, methodScope);
            }
            if (!isCompatibleLambdaScope(lambdaExpression, lambdaScope)) {
                Object accessorScope = invokeOptionalMethod(lambdaExpression, "getScope");
                if (isCompatibleLambdaScope(lambdaExpression, accessorScope)) {
                    lambdaScope = accessorScope;
                } else {
                    lambdaScope = null;
                }
            }
            if (lambdaScope != null) {
                setFieldValue(lambdaExpression, "scope", lambdaScope);
            }
        }
        initializeLambdaScope(lambdaExpression, lambdaScope);
    }

    private static boolean isCompatibleLambdaScope(Object lambdaExpression, Object candidateScope) {
        Field scopeField = lambdaExpression == null ? null : findField(lambdaExpression.getClass(), "scope");
        return scopeField != null
                && candidateScope != null
                && scopeField.getType().isInstance(candidateScope);
    }

    private static void initializeLambdaScope(Object lambdaExpression, Object lambdaScope) throws Exception {
        if (lambdaExpression == null || lambdaScope == null) {
            return;
        }
        Object body = getFieldValue(lambdaExpression, "body");
        if (findField(lambdaScope.getClass(), "referenceContext") != null) {
            setFieldValue(lambdaScope, "referenceContext", lambdaExpression);
        }
        if (findField(lambdaScope.getClass(), "blockStatement") != null) {
            setFieldValue(lambdaScope, "blockStatement", body);
        }
        stabilizeNestedLambdaScopes(body, lambdaScope, new IdentityHashMap<>(), 0);
    }

    public static void logProblemReport(Object problemReporter, Object[] arguments) {
        if (!Util.isDebugEnabled() || problemReporter == null || arguments == null || arguments.length < 5) {
            return;
        }
        try {
            Integer problemId = arguments[0] instanceof Integer ? (Integer) arguments[0] : null;
            String[] problemArguments = arguments[1] instanceof String[] ? (String[]) arguments[1] : null;
            int elaborationId = 0;
            String[] messageArguments = null;
            Integer severity = null;
            Integer start = null;
            Integer end = null;
            Object compilationResult = null;
            if (arguments.length == 7) {
                elaborationId = arguments[2] instanceof Integer ? (Integer) arguments[2] : 0;
                messageArguments = arguments[3] instanceof String[] ? (String[]) arguments[3] : null;
                severity = arguments[4] instanceof Integer ? (Integer) arguments[4] : null;
                start = arguments[5] instanceof Integer ? (Integer) arguments[5] : null;
                end = arguments[6] instanceof Integer ? (Integer) arguments[6] : null;
            } else if (arguments.length == 6) {
                messageArguments = arguments[2] instanceof String[] ? (String[]) arguments[2] : null;
                start = arguments[3] instanceof Integer ? (Integer) arguments[3] : null;
                end = arguments[4] instanceof Integer ? (Integer) arguments[4] : null;
                if (arguments[5] instanceof Integer) {
                    severity = (Integer) arguments[5];
                } else {
                    compilationResult = arguments[5];
                }
            } else if (arguments.length == 5) {
                messageArguments = arguments[2] instanceof String[] ? (String[]) arguments[2] : null;
                start = arguments[3] instanceof Integer ? (Integer) arguments[3] : null;
                end = arguments[4] instanceof Integer ? (Integer) arguments[4] : null;
            }
            if (compilationResult == null) {
                Object referenceContext = getFieldValue(problemReporter, "referenceContext");
                compilationResult = invokeOptionalMethod(referenceContext, "compilationResult");
                if (compilationResult == null) {
                    compilationResult = getFieldValue(referenceContext, "compilationResult");
                }
            }
            String fileName = describeCompilationUnitFileName(compilationResult);
            if (!isProblemFileLoggingEnabled(fileName)) {
                return;
            }
            Object problemFactory = getFieldValue(problemReporter, "problemFactory");
            String message = null;
            if (problemFactory != null && problemId != null) {
                Object localized = null;
                try {
                    localized = arguments.length == 7
                            ? invokeMethod(problemFactory, "getLocalizedMessage", problemId, elaborationId, messageArguments)
                            : invokeMethod(problemFactory, "getLocalizedMessage", problemId, messageArguments);
                } catch (Exception ignored) {
                    localized = null;
                }
                message = localized == null ? null : String.valueOf(localized);
            }
            String key = fileName + "|" + problemId + "|" + start + "|" + end + "|" + message;
            if (!PROBLEM_REPORT_KEYS.add(key)) {
                return;
            }
            Util.log("[ZirconCore] problemReport"
                    + ": file=" + fileName
                    + ", id=" + problemId
                    + ", severity=" + severity
                    + ", range=" + start + "-" + end
                    + ", message=" + message
                    + ", problemArgs=" + Arrays.toString(problemArguments)
                    + ", messageArgs=" + Arrays.toString(messageArguments));
            logUnresolvedLocalProblemDetails(compilationResult, problemId, start, end, message, "problemReport");
        } catch (Exception ignored) {
        }
    }

    public static boolean shouldSuppressExplicitThisFunctionalDirectCallProblem(Object problemReporter, Object[] arguments) {
        if (problemReporter == null || arguments == null || arguments.length < 5) {
            return false;
        }
        try {
            Integer problemId = arguments[0] instanceof Integer ? (Integer) arguments[0] : null;
            if (!isExplicitThisFunctionalDirectCallProblemId(problemId)) {
                return false;
            }
            Integer start = null;
            Integer end = null;
            Object compilationResult = null;
            if (arguments.length == 7) {
                start = arguments[5] instanceof Integer ? (Integer) arguments[5] : null;
                end = arguments[6] instanceof Integer ? (Integer) arguments[6] : null;
            } else if (arguments.length == 6) {
                start = arguments[3] instanceof Integer ? (Integer) arguments[3] : null;
                end = arguments[4] instanceof Integer ? (Integer) arguments[4] : null;
                if (!(arguments[5] instanceof Integer)) {
                    compilationResult = arguments[5];
                }
            } else if (arguments.length == 5) {
                start = arguments[3] instanceof Integer ? (Integer) arguments[3] : null;
                end = arguments[4] instanceof Integer ? (Integer) arguments[4] : null;
            }
            if (start == null || end == null) {
                return false;
            }
            if (compilationResult == null) {
                Object referenceContext = getFieldValue(problemReporter, "referenceContext");
                compilationResult = invokeOptionalMethod(referenceContext, "compilationResult");
                if (compilationResult == null) {
                    compilationResult = getFieldValue(referenceContext, "compilationResult");
                }
            }
            return shouldSuppressExplicitThisFunctionalDirectCallProblem(compilationResult, problemId, start, end);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean shouldSuppressExplicitThisFunctionalDirectCallRecordedProblem(Object compilationResult, Object problem) {
        if (compilationResult == null || problem == null) {
            return false;
        }
        try {
            Object rawId = invokeMethod(problem, "getID");
            Object rawStart = invokeMethod(problem, "getSourceStart");
            Object rawEnd = invokeMethod(problem, "getSourceEnd");
            Integer problemId = rawId instanceof Integer ? (Integer) rawId : null;
            Integer start = rawStart instanceof Integer ? (Integer) rawStart : null;
            Integer end = rawEnd instanceof Integer ? (Integer) rawEnd : null;
            return shouldSuppressExplicitThisFunctionalDirectCallProblem(compilationResult, problemId, start, end);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean shouldSuppressSuccessfulClassTargetRecordedProblem(Object compilationResult, Object problem) {
        if (compilationResult == null || problem == null) {
            return false;
        }
        try {
            Object rawId = invokeMethod(problem, "getID");
            Object rawMessage = invokeMethod(problem, "getMessage");
            Object rawStart = invokeMethod(problem, "getSourceStart");
            Object rawEnd = invokeMethod(problem, "getSourceEnd");
            Integer problemId = rawId instanceof Integer ? (Integer) rawId : null;
            Integer start = rawStart instanceof Integer ? (Integer) rawStart : null;
            Integer end = rawEnd instanceof Integer ? (Integer) rawEnd : null;
            String message = rawMessage == null ? null : String.valueOf(rawMessage);
            return shouldSuppressSuccessfulClassTargetProblem(compilationResult, problemId, start, end, message);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean shouldSuppressSuccessfulClassTargetReportedProblem(Object problemReporter, Object[] arguments) {
        if (problemReporter == null || arguments == null || arguments.length < 5) {
            return false;
        }
        try {
            Integer problemId = arguments[0] instanceof Integer ? (Integer) arguments[0] : null;
            String[] messageArguments = null;
            Integer elaborationId = 0;
            Integer start = null;
            Integer end = null;
            Object compilationResult = null;
            if (arguments.length == 7) {
                elaborationId = arguments[2] instanceof Integer ? (Integer) arguments[2] : 0;
                messageArguments = arguments[3] instanceof String[] ? (String[]) arguments[3] : null;
                start = arguments[5] instanceof Integer ? (Integer) arguments[5] : null;
                end = arguments[6] instanceof Integer ? (Integer) arguments[6] : null;
            } else if (arguments.length == 6) {
                messageArguments = arguments[2] instanceof String[] ? (String[]) arguments[2] : null;
                start = arguments[3] instanceof Integer ? (Integer) arguments[3] : null;
                end = arguments[4] instanceof Integer ? (Integer) arguments[4] : null;
                if (!(arguments[5] instanceof Integer)) {
                    compilationResult = arguments[5];
                }
            } else if (arguments.length == 5) {
                messageArguments = arguments[2] instanceof String[] ? (String[]) arguments[2] : null;
                start = arguments[3] instanceof Integer ? (Integer) arguments[3] : null;
                end = arguments[4] instanceof Integer ? (Integer) arguments[4] : null;
            }
            if (start == null || end == null) {
                return false;
            }
            if (compilationResult == null) {
                Object referenceContext = getFieldValue(problemReporter, "referenceContext");
                compilationResult = invokeOptionalMethod(referenceContext, "compilationResult");
                if (compilationResult == null) {
                    compilationResult = getFieldValue(referenceContext, "compilationResult");
                }
            }
            if (compilationResult == null) {
                return false;
            }
            String message = null;
            Object problemFactory = getFieldValue(problemReporter, "problemFactory");
            if (problemFactory != null && problemId != null) {
                Object localized;
                try {
                    localized = arguments.length == 7
                            ? invokeMethod(problemFactory, "getLocalizedMessage", problemId, elaborationId, messageArguments)
                            : invokeMethod(problemFactory, "getLocalizedMessage", problemId, messageArguments);
                } catch (Exception ignored) {
                    localized = null;
                }
                message = localized == null ? null : String.valueOf(localized);
            }
            return shouldSuppressSuccessfulClassTargetProblem(compilationResult, problemId, start, end, message);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean shouldSuppressSuccessfulClassTargetProblem(
            Object compilationResult,
            Integer problemId,
            Integer start,
            Integer end,
            String message
    ) throws Exception {
        if (problemId == null || start == null || end == null || message == null) {
            return false;
        }
        String fileName = describeCompilationUnitFileName(compilationResult);
        if (fileName == null || !hasSuccessfulRangeCovering(SUCCESSFUL_CLASS_TARGET_EXTENSION_RANGES, fileName, start, end)) {
            return false;
        }
        String source = getCompilationUnitSource(compilationResult);
        String normalizedContext = removeWhitespace(extractSourceContext(source, start, end, 96));
        boolean functionalContext = looksLikeFunctionalProblemContext(compilationResult, start, end, normalizedContext);
        boolean shouldSuppress = (problemId == 16777235
                && message.contains("cannot convert from Class<")
                && isConversionTargetBackedByTypeVariable(compilationResult, start, end, message, true))
                || (functionalContext && (isFunctionalTargetTypeProblemMessage(message)
                || isFunctionalVoidReturnProblem(problemId, message)
                || isFunctionalTypeVariableReturnProblem(compilationResult, problemId, start, end, message)
                || isFunctionalLambdaReturnProblem(message)));
        if (shouldSuppress && Util.isDebugEnabled()) {
            Util.log("[ZirconCore] suppressClassTargetProblem"
                    + ": file=" + fileName
                    + ", range=" + start + "-" + end
                    + ", message=" + message
                    + ", context=" + normalizedContext);
        }
        return shouldSuppress;
    }

    public static boolean shouldSuppressSuccessfulExtensionUndefinedRecordedProblem(Object compilationResult, Object problem) {
        if (compilationResult == null || problem == null) {
            return false;
        }
        try {
            Object rawId = invokeMethod(problem, "getID");
            Object rawMessage = invokeMethod(problem, "getMessage");
            Object rawStart = invokeMethod(problem, "getSourceStart");
            Object rawEnd = invokeMethod(problem, "getSourceEnd");
            Integer problemId = rawId instanceof Integer ? (Integer) rawId : null;
            Integer start = rawStart instanceof Integer ? (Integer) rawStart : null;
            Integer end = rawEnd instanceof Integer ? (Integer) rawEnd : null;
            String message = rawMessage == null ? null : String.valueOf(rawMessage);
            return shouldSuppressSuccessfulExtensionUndefinedProblem(compilationResult, problemId, start, end, message);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean shouldSuppressSuccessfulExtensionUndefinedReportedProblem(Object problemReporter, Object[] arguments) {
        if (problemReporter == null || arguments == null || arguments.length < 5) {
            return false;
        }
        try {
            Integer problemId = arguments[0] instanceof Integer ? (Integer) arguments[0] : null;
            String[] messageArguments = null;
            Integer elaborationId = 0;
            Integer start = null;
            Integer end = null;
            Object compilationResult = null;
            if (arguments.length == 7) {
                elaborationId = arguments[2] instanceof Integer ? (Integer) arguments[2] : 0;
                messageArguments = arguments[3] instanceof String[] ? (String[]) arguments[3] : null;
                start = arguments[5] instanceof Integer ? (Integer) arguments[5] : null;
                end = arguments[6] instanceof Integer ? (Integer) arguments[6] : null;
            } else if (arguments.length == 6) {
                messageArguments = arguments[2] instanceof String[] ? (String[]) arguments[2] : null;
                start = arguments[3] instanceof Integer ? (Integer) arguments[3] : null;
                end = arguments[4] instanceof Integer ? (Integer) arguments[4] : null;
                if (!(arguments[5] instanceof Integer)) {
                    compilationResult = arguments[5];
                }
            } else if (arguments.length == 5) {
                messageArguments = arguments[2] instanceof String[] ? (String[]) arguments[2] : null;
                start = arguments[3] instanceof Integer ? (Integer) arguments[3] : null;
                end = arguments[4] instanceof Integer ? (Integer) arguments[4] : null;
            }
            if (start == null || end == null) {
                return false;
            }
            if (compilationResult == null) {
                Object referenceContext = getFieldValue(problemReporter, "referenceContext");
                compilationResult = invokeOptionalMethod(referenceContext, "compilationResult");
                if (compilationResult == null) {
                    compilationResult = getFieldValue(referenceContext, "compilationResult");
                }
            }
            if (compilationResult == null) {
                return false;
            }
            String message = null;
            Object problemFactory = getFieldValue(problemReporter, "problemFactory");
            if (problemFactory != null && problemId != null) {
                Object localized;
                try {
                    localized = arguments.length == 7
                            ? invokeMethod(problemFactory, "getLocalizedMessage", problemId, elaborationId, messageArguments)
                            : invokeMethod(problemFactory, "getLocalizedMessage", problemId, messageArguments);
                } catch (Exception ignored) {
                    localized = null;
                }
                message = localized == null ? null : String.valueOf(localized);
            }
            return shouldSuppressSuccessfulExtensionUndefinedProblem(compilationResult, problemId, start, end, message);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean shouldSuppressSuccessfulExtensionFunctionalReturnRecordedProblem(Object compilationResult, Object problem) {
        if (compilationResult == null || problem == null) {
            return false;
        }
        try {
            Object rawId = invokeMethod(problem, "getID");
            Object rawMessage = invokeMethod(problem, "getMessage");
            Object rawStart = invokeMethod(problem, "getSourceStart");
            Object rawEnd = invokeMethod(problem, "getSourceEnd");
            Integer problemId = rawId instanceof Integer ? (Integer) rawId : null;
            Integer start = rawStart instanceof Integer ? (Integer) rawStart : null;
            Integer end = rawEnd instanceof Integer ? (Integer) rawEnd : null;
            String message = rawMessage == null ? null : String.valueOf(rawMessage);
            return shouldSuppressSuccessfulExtensionFunctionalReturnProblem(compilationResult, problemId, start, end, message);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean shouldSuppressSuccessfulExtensionFunctionalReturnReportedProblem(Object problemReporter, Object[] arguments) {
        if (problemReporter == null || arguments == null || arguments.length < 5) {
            return false;
        }
        try {
            Integer problemId = arguments[0] instanceof Integer ? (Integer) arguments[0] : null;
            String[] messageArguments = null;
            Integer elaborationId = 0;
            Integer start = null;
            Integer end = null;
            Object compilationResult = null;
            if (arguments.length == 7) {
                elaborationId = arguments[2] instanceof Integer ? (Integer) arguments[2] : 0;
                messageArguments = arguments[3] instanceof String[] ? (String[]) arguments[3] : null;
                start = arguments[5] instanceof Integer ? (Integer) arguments[5] : null;
                end = arguments[6] instanceof Integer ? (Integer) arguments[6] : null;
            } else if (arguments.length == 6) {
                messageArguments = arguments[2] instanceof String[] ? (String[]) arguments[2] : null;
                start = arguments[3] instanceof Integer ? (Integer) arguments[3] : null;
                end = arguments[4] instanceof Integer ? (Integer) arguments[4] : null;
                if (!(arguments[5] instanceof Integer)) {
                    compilationResult = arguments[5];
                }
            } else if (arguments.length == 5) {
                messageArguments = arguments[2] instanceof String[] ? (String[]) arguments[2] : null;
                start = arguments[3] instanceof Integer ? (Integer) arguments[3] : null;
                end = arguments[4] instanceof Integer ? (Integer) arguments[4] : null;
            }
            if (start == null || end == null) {
                return false;
            }
            if (compilationResult == null) {
                Object referenceContext = getFieldValue(problemReporter, "referenceContext");
                compilationResult = invokeOptionalMethod(referenceContext, "compilationResult");
                if (compilationResult == null) {
                    compilationResult = getFieldValue(referenceContext, "compilationResult");
                }
            }
            if (compilationResult == null) {
                return false;
            }
            String message = null;
            Object problemFactory = getFieldValue(problemReporter, "problemFactory");
            if (problemFactory != null && problemId != null) {
                Object localized;
                try {
                    localized = arguments.length == 7
                            ? invokeMethod(problemFactory, "getLocalizedMessage", problemId, elaborationId, messageArguments)
                            : invokeMethod(problemFactory, "getLocalizedMessage", problemId, messageArguments);
                } catch (Exception ignored) {
                    localized = null;
                }
                message = localized == null ? null : String.valueOf(localized);
            }
            return shouldSuppressSuccessfulExtensionFunctionalReturnProblem(compilationResult, problemId, start, end, message);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean shouldSuppressSuccessfulFunctionalWrapperReportedProblem(Object problemReporter, Object[] arguments) {
        if (problemReporter == null || arguments == null || arguments.length < 5) {
            return false;
        }
        try {
            Integer problemId = arguments[0] instanceof Integer ? (Integer) arguments[0] : null;
            String[] messageArguments = null;
            Integer elaborationId = 0;
            Integer start = null;
            Integer end = null;
            Object compilationResult = null;
            if (arguments.length == 7) {
                elaborationId = arguments[2] instanceof Integer ? (Integer) arguments[2] : 0;
                messageArguments = arguments[3] instanceof String[] ? (String[]) arguments[3] : null;
                start = arguments[5] instanceof Integer ? (Integer) arguments[5] : null;
                end = arguments[6] instanceof Integer ? (Integer) arguments[6] : null;
            } else if (arguments.length == 6) {
                messageArguments = arguments[2] instanceof String[] ? (String[]) arguments[2] : null;
                start = arguments[3] instanceof Integer ? (Integer) arguments[3] : null;
                end = arguments[4] instanceof Integer ? (Integer) arguments[4] : null;
                if (!(arguments[5] instanceof Integer)) {
                    compilationResult = arguments[5];
                }
            } else if (arguments.length == 5) {
                messageArguments = arguments[2] instanceof String[] ? (String[]) arguments[2] : null;
                start = arguments[3] instanceof Integer ? (Integer) arguments[3] : null;
                end = arguments[4] instanceof Integer ? (Integer) arguments[4] : null;
            }
            if (start == null || end == null) {
                return false;
            }
            if (compilationResult == null) {
                Object referenceContext = getFieldValue(problemReporter, "referenceContext");
                compilationResult = invokeOptionalMethod(referenceContext, "compilationResult");
                if (compilationResult == null) {
                    compilationResult = getFieldValue(referenceContext, "compilationResult");
                }
            }
            if (compilationResult == null) {
                return false;
            }
            String message = null;
            Object problemFactory = getFieldValue(problemReporter, "problemFactory");
            if (problemFactory != null && problemId != null) {
                Object localized;
                try {
                    localized = arguments.length == 7
                            ? invokeMethod(problemFactory, "getLocalizedMessage", problemId, elaborationId, messageArguments)
                            : invokeMethod(problemFactory, "getLocalizedMessage", problemId, messageArguments);
                } catch (Exception ignored) {
                    localized = null;
                }
                message = localized == null ? null : String.valueOf(localized);
            }
            return shouldSuppressSuccessfulFunctionalWrapperProblem(compilationResult, problemId, start, end, message);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean shouldSuppressSuccessfulFunctionalLocalFieldReportedProblem(Object problemReporter, Object[] arguments) {
        if (problemReporter == null || arguments == null || arguments.length < 5) {
            return false;
        }
        try {
            Object compilationResult = findField(problemReporter.getClass(), "referenceContext") != null
                    ? getFieldValue(problemReporter, "referenceContext")
                    : null;
            if (compilationResult == null) {
                compilationResult = findField(problemReporter.getClass(), "compilationResult") != null
                        ? getFieldValue(problemReporter, "compilationResult")
                        : null;
            }
            Object rawId = arguments.length > 0 ? arguments[0] : null;
            Object rawStart = arguments.length > 1 ? arguments[1] : null;
            Object rawEnd = arguments.length > 2 ? arguments[2] : null;
            Object rawMessage = arguments.length > 4 ? arguments[4] : null;
            Integer problemId = rawId instanceof Integer ? (Integer) rawId : null;
            Integer start = rawStart instanceof Integer ? (Integer) rawStart : null;
            Integer end = rawEnd instanceof Integer ? (Integer) rawEnd : null;
            String message = rawMessage == null ? null : String.valueOf(rawMessage);
            if ((message == null || message.isEmpty()) && arguments.length > 3) {
                Object localized = arguments[3];
                if (localized instanceof char[]) {
                    message = new String((char[]) localized);
                } else if (localized != null) {
                    message = String.valueOf(localized);
                }
            }
            return shouldSuppressSuccessfulFunctionalLocalFieldProblem(compilationResult, problemId, start, end, message);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean shouldSuppressResolvableLocalReferenceReportedProblem(Object problemReporter, Object[] arguments) {
        if (problemReporter == null || arguments == null || arguments.length < 5) {
            return false;
        }
        try {
            Integer problemId = arguments[0] instanceof Integer ? (Integer) arguments[0] : null;
            int elaborationId = 0;
            String[] messageArguments = null;
            Integer start = null;
            Integer end = null;
            Object compilationResult = null;
            if (arguments.length == 7) {
                elaborationId = arguments[2] instanceof Integer ? (Integer) arguments[2] : 0;
                messageArguments = arguments[3] instanceof String[] ? (String[]) arguments[3] : null;
                start = arguments[5] instanceof Integer ? (Integer) arguments[5] : null;
                end = arguments[6] instanceof Integer ? (Integer) arguments[6] : null;
            } else {
                messageArguments = arguments[2] instanceof String[] ? (String[]) arguments[2] : null;
                start = arguments[3] instanceof Integer ? (Integer) arguments[3] : null;
                end = arguments[4] instanceof Integer ? (Integer) arguments[4] : null;
                if (arguments.length == 6 && !(arguments[5] instanceof Integer)) {
                    compilationResult = arguments[5];
                }
            }
            if (compilationResult == null) {
                Object referenceContext = findField(problemReporter.getClass(), "referenceContext") != null
                        ? getFieldValue(problemReporter, "referenceContext")
                        : null;
                compilationResult = referenceContext == null
                        ? null
                        : invokeOptionalMethod(referenceContext, "compilationResult");
                if (compilationResult == null && referenceContext != null) {
                    compilationResult = getFieldValue(referenceContext, "compilationResult");
                }
            }
            if (compilationResult == null && findField(problemReporter.getClass(), "compilationResult") != null) {
                compilationResult = getFieldValue(problemReporter, "compilationResult");
            }
            String message = null;
            Object problemFactory = getFieldValue(problemReporter, "problemFactory");
            if (problemFactory != null && problemId != null) {
                Object localized;
                try {
                    localized = arguments.length == 7
                            ? invokeMethod(problemFactory, "getLocalizedMessage", problemId, elaborationId, messageArguments)
                            : invokeMethod(problemFactory, "getLocalizedMessage", problemId, messageArguments);
                } catch (Exception ignored) {
                    localized = null;
                }
                message = localized == null ? null : String.valueOf(localized);
            }
            return shouldSuppressResolvableLocalReferenceProblem(compilationResult, problemId, start, end, message);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean shouldSuppressOptionalFunctionalReportedProblem(Object problemReporter, Object[] arguments) {
        if (problemReporter == null || arguments == null || arguments.length < 5) {
            return false;
        }
        try {
            Object compilationResult = null;
            Object referenceContext = findField(problemReporter.getClass(), "referenceContext") != null
                    ? getFieldValue(problemReporter, "referenceContext")
                    : null;
            if (referenceContext != null) {
                compilationResult = invokeOptionalMethod(referenceContext, "compilationResult");
                if (compilationResult == null) {
                    compilationResult = getFieldValue(referenceContext, "compilationResult");
                }
            }
            if (compilationResult == null) {
                compilationResult = findField(problemReporter.getClass(), "compilationResult") != null
                        ? getFieldValue(problemReporter, "compilationResult")
                        : null;
            }
            Object rawId = arguments.length > 0 ? arguments[0] : null;
            Object rawStart = arguments.length > 1 ? arguments[1] : null;
            Object rawEnd = arguments.length > 2 ? arguments[2] : null;
            Object rawMessage = arguments.length > 4 ? arguments[4] : null;
            Integer problemId = rawId instanceof Integer ? (Integer) rawId : null;
            Integer start = rawStart instanceof Integer ? (Integer) rawStart : null;
            Integer end = rawEnd instanceof Integer ? (Integer) rawEnd : null;
            String message = rawMessage == null ? null : String.valueOf(rawMessage);
            if ((message == null || message.isEmpty()) && arguments.length > 3) {
                Object localized = arguments[3];
                if (localized instanceof char[]) {
                    message = new String((char[]) localized);
                } else if (localized != null) {
                    message = String.valueOf(localized);
                }
            }
            return shouldSuppressOptionalFunctionalProblem(compilationResult, problemId, start, end, message);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hydrateMessageSendArgumentTypes(Object messageSend) throws Exception {
        Object[] arguments = getFieldValue(messageSend, "arguments") instanceof Object[]
                ? (Object[]) getFieldValue(messageSend, "arguments")
                : new Object[0];
        for (Object argument : arguments) {
            if (argument == null || isFunctionalInvocationArgument(argument)) {
                continue;
            }
            Object resolvedType = getFieldValue(argument, "resolvedType");
            if (resolvedType != null) {
                continue;
            }
            Object argumentBinding = getFieldValue(argument, "binding");
            Object bindingType = argumentBinding == null ? null : getFieldValue(argumentBinding, "type");
            if (bindingType != null && findField(argument.getClass(), "resolvedType") != null) {
                setFieldValue(argument, "resolvedType", bindingType);
                resolvedType = bindingType;
            }
            if (resolvedType == null) {
                return false;
            }
        }
        return true;
    }

    public static boolean shouldSuppressSuccessfulFunctionalWrapperRecordedProblem(Object compilationResult, Object problem) {
        if (compilationResult == null || problem == null) {
            return false;
        }
        try {
            Object rawId = invokeMethod(problem, "getID");
            Object rawMessage = invokeMethod(problem, "getMessage");
            Object rawStart = invokeMethod(problem, "getSourceStart");
            Object rawEnd = invokeMethod(problem, "getSourceEnd");
            Integer problemId = rawId instanceof Integer ? (Integer) rawId : null;
            Integer start = rawStart instanceof Integer ? (Integer) rawStart : null;
            Integer end = rawEnd instanceof Integer ? (Integer) rawEnd : null;
            String message = rawMessage == null ? null : String.valueOf(rawMessage);
            return shouldSuppressSuccessfulFunctionalWrapperProblem(compilationResult, problemId, start, end, message);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean shouldSuppressSuccessfulFunctionalLocalFieldRecordedProblem(Object compilationResult, Object problem) {
        if (compilationResult == null || problem == null) {
            return false;
        }
        try {
            Object rawId = invokeMethod(problem, "getID");
            Object rawStart = invokeMethod(problem, "getSourceStart");
            Object rawEnd = invokeMethod(problem, "getSourceEnd");
            Object rawMessage = invokeMethod(problem, "getMessage");
            Integer problemId = rawId instanceof Integer ? (Integer) rawId : null;
            Integer start = rawStart instanceof Integer ? (Integer) rawStart : null;
            Integer end = rawEnd instanceof Integer ? (Integer) rawEnd : null;
            String message = rawMessage == null ? null : String.valueOf(rawMessage);
            return shouldSuppressSuccessfulFunctionalLocalFieldProblem(compilationResult, problemId, start, end, message);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean shouldSuppressResolvableLocalReferenceRecordedProblem(Object compilationResult, Object problem) {
        if (compilationResult == null || problem == null) {
            return false;
        }
        try {
            Object rawId = invokeMethod(problem, "getID");
            Object rawStart = invokeMethod(problem, "getSourceStart");
            Object rawEnd = invokeMethod(problem, "getSourceEnd");
            Object rawMessage = invokeMethod(problem, "getMessage");
            Integer problemId = rawId instanceof Integer ? (Integer) rawId : null;
            Integer start = rawStart instanceof Integer ? (Integer) rawStart : null;
            Integer end = rawEnd instanceof Integer ? (Integer) rawEnd : null;
            String message = rawMessage == null ? null : String.valueOf(rawMessage);
            return shouldSuppressResolvableLocalReferenceProblem(compilationResult, problemId, start, end, message);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean shouldSuppressOptionalFunctionalRecordedProblem(Object compilationResult, Object problem) {
        if (compilationResult == null || problem == null) {
            return false;
        }
        try {
            Object rawId = invokeMethod(problem, "getID");
            Object rawStart = invokeMethod(problem, "getSourceStart");
            Object rawEnd = invokeMethod(problem, "getSourceEnd");
            Object rawMessage = invokeMethod(problem, "getMessage");
            Integer problemId = rawId instanceof Integer ? (Integer) rawId : null;
            Integer start = rawStart instanceof Integer ? (Integer) rawStart : null;
            Integer end = rawEnd instanceof Integer ? (Integer) rawEnd : null;
            String message = rawMessage == null ? null : String.valueOf(rawMessage);
            return shouldSuppressOptionalFunctionalProblem(compilationResult, problemId, start, end, message);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean shouldSuppressSuccessfulFunctionalWrapperProblem(
            Object compilationResult,
            Integer problemId,
            Integer start,
            Integer end,
            String message
    ) throws Exception {
        if (problemId == null || start == null || end == null || message == null) {
            return false;
        }
        String fileName = describeCompilationUnitFileName(compilationResult);
        if (fileName == null || !hasSuccessfulRangeCovering(SUCCESSFUL_FUNCTIONAL_WRAPPER_RANGES, fileName, start, end)) {
            return false;
        }
        String source = getCompilationUnitSource(compilationResult);
        String normalizedContext = removeWhitespace(extractSourceContext(source, start, end, 96));
        if (!looksLikeFunctionalProblemContext(compilationResult, start, end, normalizedContext)) {
            return false;
        }
        boolean shouldSuppress = false;
        if (problemId == 67108964
                && message.startsWith("The method ")
                && message.contains(" is undefined for the type ")) {
            shouldSuppress = normalizedContext.contains(".") && normalizedContext.contains("(");
        } else if (isFunctionalTargetTypeProblemMessage(message)
                || isFunctionalVoidReturnProblem(problemId, message)
                || isFunctionalTypeVariableReturnProblem(compilationResult, problemId, start, end, message)) {
            shouldSuppress = true;
        }
        if (shouldSuppress && Util.isDebugEnabled()) {
            Util.log("[ZirconCore] suppressFunctionalWrapperProblem"
                    + ": file=" + fileName
                    + ", range=" + start + "-" + end
                    + ", message=" + message
                    + ", context=" + normalizedContext);
        }
        return shouldSuppress;
    }

    private static boolean shouldSuppressSuccessfulFunctionalLocalFieldProblem(
            Object compilationResult,
            Integer problemId,
            Integer start,
            Integer end,
            String message
    ) throws Exception {
        if (problemId == null || start == null || end == null || message == null) {
            return false;
        }
        boolean localFieldProblem = problemId == 33554502
                && message.endsWith("cannot be resolved or is not a field");
        boolean localVariableProblem = (problemId == 570425394
                && message.endsWith("cannot be resolved"))
                || (problemId == 33554515
                && message.endsWith("cannot be resolved to a variable"));
        if (!localFieldProblem && !localVariableProblem) {
            return false;
        }
        String fileName = describeCompilationUnitFileName(compilationResult);
        if (fileName == null) {
            return false;
        }
        boolean coveredBySuccessfulRange = hasSuccessfulRangeCovering(SUCCESSFUL_EXTENSION_UNDEFINED_RANGES, fileName, start, end)
                || hasSuccessfulRangeCovering(SUCCESSFUL_FUNCTIONAL_WRAPPER_RANGES, fileName, start, end)
                || !getSuccessfulExtensionFunctionalReturnTypesCovering(fileName, start, end).isEmpty();
        if (!coveredBySuccessfulRange) {
            return false;
        }
        String source = getCompilationUnitSource(compilationResult);
        String normalizedContext = removeWhitespace(extractSourceContext(source, start, end, 96));
        if (!looksLikeFunctionalProblemContext(compilationResult, start, end, normalizedContext)) {
            return false;
        }
        if (Util.isDebugEnabled()) {
            Util.log("[ZirconCore] suppressSuccessfulFunctionalLocalFieldProblem"
                    + ": file=" + fileName
                    + ", range=" + start + "-" + end
                    + ", message=" + message
                    + ", context=" + normalizedContext);
        }
        return true;
    }

    private static boolean shouldSuppressResolvableLocalReferenceProblem(
            Object compilationResult,
            Integer problemId,
            Integer start,
            Integer end,
            String message
    ) throws Exception {
        if (compilationResult == null || problemId == null || start == null || end == null || message == null) {
            return false;
        }
        boolean localFieldProblem = problemId == 33554502
                && message.endsWith("cannot be resolved or is not a field");
        boolean localVariableProblem = (problemId == 570425394
                && message.endsWith("cannot be resolved"))
                || (problemId == 33554515
                && message.endsWith("cannot be resolved to a variable"));
        if (!localFieldProblem && !localVariableProblem) {
            return false;
        }
        String fileName = describeCompilationUnitFileName(compilationResult);
        if (fileName != null && hasSuccessfulRangeCovering(PROVEN_LOCAL_REFERENCE_RANGES, fileName, start, end)) {
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconCore] suppressResolvableLocalReferenceProblem"
                        + ": file=" + fileName
                        + ", range=" + start + "-" + end
                        + ", message=" + message
                        + ", mode=proven-scope-binding");
            }
            return true;
        }
        String referenceName = extractProblemReferenceName(message);
        if (referenceName == null || referenceName.isEmpty()) {
            return false;
        }
        String source = getCompilationUnitSource(compilationResult);
        String diskSource = readCompilationUnitDiskSource(compilationResult);
        if (hasVisibleLocalDeclarationSource(source, start, referenceName)
                || hasVisibleLocalDeclarationSource(diskSource, start, referenceName)) {
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconCore] suppressResolvableLocalReferenceProblem"
                        + ": range=" + start + "-" + end
                        + ", message=" + message
                        + ", reference=" + referenceName
                        + ", mode=lexical-block");
            }
            return true;
        }
        Object compilationUnit = invokeOptionalMethod(compilationResult, "getCompilationUnit");
        if (compilationUnit == null) {
            compilationUnit = getFieldValue(compilationResult, "compilationUnit");
        }
        if (compilationUnit == null) {
            compilationUnit = getFieldValue(compilationResult, "compilationUnitDeclaration");
        }
        if (compilationUnit == null) {
            return false;
        }
        RangeAstMatch match = findAstNodeCoveringRange(compilationUnit, start, end, null, null, new IdentityHashMap<>(), 0);
        if (match == null || match.node == null) {
            return false;
        }
        String simpleName = match.node.getClass().getSimpleName();
        referenceName = ("SingleNameReference".equals(simpleName) || "QualifiedNameReference".equals(simpleName))
                ? readReferenceSimpleName(match.node, getFieldValue(match.node, "binding"))
                : null;
        if (referenceName == null || referenceName.isEmpty() || "unknown".equals(referenceName)) {
            referenceName = extractProblemReferenceName(message);
            if (referenceName == null || referenceName.isEmpty()) {
                return false;
            }
        }
        if (hasNearbyLocalDeclarationSource(source, start, referenceName)
                || hasNearbyLocalDeclarationSource(diskSource, start, referenceName)
                || hasSimpleLocalAssignmentSource(source, referenceName)
                || hasSimpleLocalAssignmentSource(diskSource, referenceName)
                || hasAnyLocalDeclarationSource(source, referenceName)
                || hasAnyLocalDeclarationSource(diskSource, referenceName)) {
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconCore] suppressResolvableLocalReferenceProblem"
                        + ": range=" + start + "-" + end
                        + ", message=" + message
                        + ", reference=" + referenceName
                        + ", mode=source");
            }
            return true;
        }
        Object localBinding = match.scope != null ? findLocalBindingInScopeChain(match.scope, referenceName) : null;
        if (localBinding == null && match.enclosingLambda != null) {
            Object lambdaScope = getFieldValue(match.enclosingLambda, "scope");
            if (lambdaScope != null && lambdaScope != match.scope) {
                localBinding = findLocalBindingInScopeChain(lambdaScope, referenceName);
            }
        }
        if (localBinding == null) {
            Object searchRoot = match.enclosingLambda != null ? getFieldValue(match.enclosingLambda, "body") : compilationUnit;
            if (!hasEarlierMatchingLocalDeclaration(searchRoot, referenceName, start, new IdentityHashMap<>(), 0)) {
                return false;
            }
        } else {
            int declarationStart = readIntField(localBinding, "declarationSourceStart");
            if (declarationStart < 0) {
                declarationStart = readIntField(localBinding, "sourceStart");
            }
            if (declarationStart >= 0 && declarationStart > start) {
                return false;
            }
        }
        if (Util.isDebugEnabled()) {
            Util.log("[ZirconCore] suppressResolvableLocalReferenceProblem"
                    + ": range=" + start + "-" + end
                    + ", message=" + message
                    + ", reference=" + referenceName
                    + ", scope=" + describeScopeDebug(match.scope));
        }
        return true;
    }

    /**
     * Proves that a reported name has an earlier declaration in the same
     * lexical block. This is used only for JDT's transient local/field lookup
     * diagnostics emitted while resolving synthetic Elvis expressions and
     * cached lambda copies.
     */
    private static boolean hasVisibleLocalDeclarationSource(String source, int referenceStart, String referenceName) {
        if (source == null
                || source.isEmpty()
                || referenceName == null
                || referenceName.isEmpty()
                || referenceStart <= 0) {
            return false;
        }
        int safeStart = Math.min(referenceStart, source.length());
        int blockStart = findInnermostLexicalBlockStart(source, safeStart);
        int segmentStart = Math.max(0, blockStart + 1);
        String visiblePrefix = maskNestedBlocksAndNonCode(source, segmentStart, safeStart);
        if (visiblePrefix.isEmpty()) {
            return false;
        }
        String type = "(?:var|[A-Za-z_$][\\w$]*(?:\\s*\\.\\s*[A-Za-z_$][\\w$]*)*"
                + "(?:\\s*<[^;{}=()]*>)?(?:\\s*\\[\\s*\\])*)";
        java.util.regex.Pattern declaration = java.util.regex.Pattern.compile(
                "(?s)(?:^|[;({])\\s*(?:final\\s+)?"
                        + "(?!(?:return|throw|case|new|if|while|for|switch)\\b)"
                        + type
                        + "\\s+"
                        + java.util.regex.Pattern.quote(referenceName)
                        + "\\s*(?==|;|,)"
        );
        return declaration.matcher(visiblePrefix).find();
    }

    private static int findInnermostLexicalBlockStart(String source, int endExclusive) {
        List<Integer> blockStarts = new ArrayList<>();
        int state = 0;
        int limit = Math.min(Math.max(0, endExclusive), source.length());
        for (int index = 0; index < limit; index++) {
            char current = source.charAt(index);
            char next = index + 1 < limit ? source.charAt(index + 1) : '\0';
            if (state == 1 || state == 2) {
                if (current == '\\') {
                    index++;
                } else if ((state == 1 && current == '\'') || (state == 2 && current == '"')) {
                    state = 0;
                }
                continue;
            }
            if (state == 3) {
                if (current == '\r' || current == '\n') {
                    state = 0;
                }
                continue;
            }
            if (state == 4) {
                if (current == '*' && next == '/') {
                    state = 0;
                    index++;
                }
                continue;
            }
            if (current == '\'') {
                state = 1;
            } else if (current == '"') {
                state = 2;
            } else if (current == '/' && next == '/') {
                state = 3;
                index++;
            } else if (current == '/' && next == '*') {
                state = 4;
                index++;
            } else if (current == '{') {
                blockStarts.add(index);
            } else if (current == '}' && !blockStarts.isEmpty()) {
                blockStarts.remove(blockStarts.size() - 1);
            }
        }
        return blockStarts.isEmpty() ? -1 : blockStarts.get(blockStarts.size() - 1);
    }

    private static String maskNestedBlocksAndNonCode(String source, int start, int endExclusive) {
        int safeStart = Math.max(0, Math.min(start, source.length()));
        int safeEnd = Math.max(safeStart, Math.min(endExclusive, source.length()));
        StringBuilder visible = new StringBuilder(safeEnd - safeStart);
        int state = 0;
        int nestedBlockDepth = 0;
        for (int index = safeStart; index < safeEnd; index++) {
            char current = source.charAt(index);
            char next = index + 1 < safeEnd ? source.charAt(index + 1) : '\0';
            if (state == 1 || state == 2) {
                visible.append(' ');
                if (current == '\\' && index + 1 < safeEnd) {
                    visible.append(' ');
                    index++;
                } else if ((state == 1 && current == '\'') || (state == 2 && current == '"')) {
                    state = 0;
                }
                continue;
            }
            if (state == 3) {
                visible.append(current == '\r' || current == '\n' ? current : ' ');
                if (current == '\r' || current == '\n') {
                    state = 0;
                }
                continue;
            }
            if (state == 4) {
                visible.append(' ');
                if (current == '*' && next == '/') {
                    visible.append(' ');
                    index++;
                    state = 0;
                }
                continue;
            }
            if (current == '\'') {
                visible.append(' ');
                state = 1;
            } else if (current == '"') {
                visible.append(' ');
                state = 2;
            } else if (current == '/' && next == '/') {
                visible.append("  ");
                index++;
                state = 3;
            } else if (current == '/' && next == '*') {
                visible.append("  ");
                index++;
                state = 4;
            } else if (current == '{') {
                nestedBlockDepth++;
                visible.append(' ');
            } else if (current == '}') {
                if (nestedBlockDepth > 0) {
                    nestedBlockDepth--;
                }
                visible.append(' ');
            } else {
                visible.append(nestedBlockDepth == 0 ? current : ' ');
            }
        }
        return visible.toString();
    }

    private static String readCompilationUnitDiskSource(Object compilationResult) {
        if (compilationResult == null) {
            return "";
        }
        try {
            String fileName = describeCompilationUnitFileName(compilationResult);
            if (fileName == null || fileName.isEmpty()) {
                return "";
            }
            Path path = resolveCompilationUnitFilePath(fileName);
            if (!Files.exists(path) || Files.isDirectory(path)) {
                return "";
            }
            return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static Path resolveCompilationUnitFilePath(String fileName) {
        Path direct = Paths.get(fileName);
        if (Files.exists(direct)) {
            return direct;
        }
        String normalized = fileName.replace('/', File.separatorChar).replace('\\', File.separatorChar);
        Path cwd = Paths.get(System.getProperty("user.dir", "")).toAbsolutePath().normalize();
        if (!normalized.isEmpty() && (normalized.charAt(0) == File.separatorChar)) {
            String relative = normalized.substring(1);
            Path relativePath = Paths.get(relative);
            if (relativePath.getNameCount() > 0 && cwd.getFileName() != null) {
                String firstSegment = String.valueOf(relativePath.getName(0));
                String cwdName = String.valueOf(cwd.getFileName());
                if (cwdName.equals(firstSegment)) {
                    Path candidate = cwd.resolve(relativePath.subpath(1, relativePath.getNameCount())).normalize();
                    if (Files.exists(candidate)) {
                        return candidate;
                    }
                }
            }
            Path candidate = cwd.resolve(relative).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return direct;
    }

    private static boolean hasNearbyLocalDeclarationSource(String source, int start, String referenceName) {
        if (source == null || source.isEmpty() || referenceName == null || referenceName.isEmpty() || start <= 0) {
            return false;
        }
        int safeStart = Math.min(start, source.length());
        int windowStart = Math.max(0, safeStart - 320);
        String prefix = source.substring(windowStart, safeStart);
        int blockStart = prefix.lastIndexOf('{');
        if (blockStart >= 0) {
            prefix = prefix.substring(blockStart + 1);
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?s)\\b(?:final\\s+)?(?:[\\w$<>\\[\\],.?]+\\s+)+" + java.util.regex.Pattern.quote(referenceName) + "\\s*="
        );
        return pattern.matcher(prefix).find();
    }

    private static boolean hasAnyLocalDeclarationSource(String source, String referenceName) {
        if (source == null || source.isEmpty() || referenceName == null || referenceName.isEmpty()) {
            return false;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?s)\\b(?:final\\s+)?(?:[\\w$<>\\[\\],.?]+\\s+)+" + java.util.regex.Pattern.quote(referenceName) + "\\s*="
        );
        return pattern.matcher(source).find();
    }

    private static boolean hasSimpleLocalAssignmentSource(String source, String referenceName) {
        if (source == null || source.isEmpty() || referenceName == null || referenceName.isEmpty()) {
            return false;
        }
        String normalized = removeWhitespace(source);
        return normalized.contains(referenceName + "=");
    }

    private static String extractProblemReferenceName(String message) {
        if (message == null) {
            return null;
        }
        int separator = message.indexOf(" cannot be resolved");
        if (separator <= 0) {
            return null;
        }
        return message.substring(0, separator).trim();
    }

    private static void logUnresolvedLocalProblemDetails(
            Object compilationResult,
            Integer problemId,
            Integer start,
            Integer end,
            String message,
            String phase
    ) {
        if (compilationResult == null
                || problemId == null
                || start == null
                || end == null
                || message == null) {
            return;
        }
        try {
            boolean localFieldProblem = problemId == 33554502
                    && message.endsWith("cannot be resolved or is not a field");
            boolean localVariableProblem = (problemId == 570425394
                    && message.endsWith("cannot be resolved"))
                    || (problemId == 33554515
                    && message.endsWith("cannot be resolved to a variable"));
            if (!localFieldProblem && !localVariableProblem) {
                return;
            }
            Object compilationUnit = invokeOptionalMethod(compilationResult, "getCompilationUnit");
            if (compilationUnit == null) {
                compilationUnit = getFieldValue(compilationResult, "compilationUnit");
            }
            if (compilationUnit == null) {
                compilationUnit = getFieldValue(compilationResult, "compilationUnitDeclaration");
            }
            if (compilationUnit == null) {
                return;
            }
            RangeAstMatch match = findAstNodeCoveringRange(compilationUnit, start, end, null, null, new IdentityHashMap<>(), 0);
            String source = getCompilationUnitSource(compilationResult);
            String referenceName = extractProblemReferenceName(message);
            Util.log("[ZirconCore] unresolvedLocalDetail"
                    + ": phase=" + phase
                    + ", range=" + start + "-" + end
                    + ", message=" + message
                    + ", reference=" + referenceName
                    + ", context=" + removeWhitespace(extractSourceContext(source, start, end, 96))
                    + ", node=" + (match == null ? "null" : describeAstNodeState(match.node, 2))
                    + ", lambda=" + (match == null ? "null" : describeAstNodeState(match.enclosingLambda, 2))
                    + ", scope=" + (match == null ? "null" : describeScopeDebug(match.scope)));
        } catch (Exception ignored) {
        }
    }

    private static boolean hasEarlierMatchingLocalDeclaration(
            Object node,
            String referenceName,
            int referenceStart,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || referenceName == null || referenceName.isEmpty() || depth > 20) {
            return false;
        }
        if (node instanceof Object[]) {
            for (Object element : (Object[]) node) {
                if (hasEarlierMatchingLocalDeclaration(element, referenceName, referenceStart, visited, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        if (!isAstNode(node) || visited.put(node, Boolean.TRUE) != null) {
            return false;
        }
        if ("LocalDeclaration".equals(node.getClass().getSimpleName())) {
            String declarationName = readLocalDeclarationName(node);
            int declarationStart = readIntField(node, "sourceStart");
            if (referenceName.equals(declarationName)
                    && declarationStart >= 0
                    && declarationStart < referenceStart) {
                return true;
            }
        }
        for (Object child : getAstChildren(node)) {
            if (hasEarlierMatchingLocalDeclaration(child, referenceName, referenceStart, visited, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static String readLocalDeclarationName(Object node) throws Exception {
        if (node == null) {
            return null;
        }
        Object name = getFieldValue(node, "name");
        if (name instanceof char[]) {
            return new String((char[]) name);
        }
        Object binding = getFieldValue(node, "binding");
        return binding != null ? describeReferenceName(node, binding) : null;
    }

    private static boolean shouldSuppressOptionalFunctionalProblem(
            Object compilationResult,
            Integer problemId,
            Integer start,
            Integer end,
            String message
    ) throws Exception {
        if (compilationResult == null
                || problemId == null
                || start == null
                || end == null
                || message == null
                || !isOptionalFunctionalProblem(compilationResult, problemId, start, end, message)) {
            return false;
        }
        String source = getCompilationUnitSource(compilationResult);
        String normalizedContext = removeWhitespace(extractSourceContext(source, start, end, 96));
        if (!looksLikeFunctionalProblemContext(compilationResult, start, end, normalizedContext)
                || !containsOptionalSyntaxInFunctionalContext(compilationResult, start, end, source, normalizedContext)) {
            return false;
        }
        if (Util.isDebugEnabled()) {
            Util.log("[ZirconCore] suppressOptionalFunctionalProblem"
                    + ": file=" + describeCompilationUnitFileName(compilationResult)
                    + ", range=" + start + "-" + end
                    + ", id=" + problemId
                    + ", message=" + message
                    + ", context=" + normalizedContext);
        }
        return true;
    }

    public static boolean shouldSuppressFalseReturnRecordedProblem(Object compilationResult, Object problem) {
        if (compilationResult == null || problem == null) {
            return false;
        }
        try {
            Object rawId = invokeMethod(problem, "getID");
            Object rawStart = invokeMethod(problem, "getSourceStart");
            Object rawEnd = invokeMethod(problem, "getSourceEnd");
            Object rawMessage = invokeMethod(problem, "getMessage");
            Integer problemId = rawId instanceof Integer ? (Integer) rawId : null;
            Integer start = rawStart instanceof Integer ? (Integer) rawStart : null;
            Integer end = rawEnd instanceof Integer ? (Integer) rawEnd : null;
            String message = rawMessage == null ? null : String.valueOf(rawMessage);
            if (problemId == null || problemId != 603979884 || start == null || end == null || message == null) {
                return false;
            }
            if (!message.startsWith("This method must return a result of type ")) {
                return false;
            }
            String source = getCompilationUnitSource(compilationResult);
            boolean shouldSuppress = looksLikeGuaranteedMethodExit(source, start)
                    || looksLikeGuaranteedMethodExitFromDeclaration(source, start, end);
            if (shouldSuppress && Util.isDebugEnabled()) {
                Util.log("[ZirconCore] suppressFalseReturnProblem"
                        + ": file=" + describeCompilationUnitFileName(compilationResult)
                        + ", range=" + start + "-" + end
                        + ", message=" + message
                        + ", context=" + removeWhitespace(extractSourceContext(source, start, end, 128)));
            }
            return shouldSuppress;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean looksLikeGuaranteedMethodExit(String source, Integer position) {
        if (source == null || source.isEmpty() || position == null || position < 0) {
            return false;
        }
        int methodBodyStart = findEnclosingMethodBodyStart(source, position);
        if (methodBodyStart < 0) {
            return false;
        }
        int methodBodyEnd = findMatchingBraceForward(source, methodBodyStart);
        if (methodBodyEnd < 0 || methodBodyEnd <= methodBodyStart) {
            return false;
        }
        return blockAlwaysExits(source.substring(methodBodyStart + 1, methodBodyEnd));
    }

    private static boolean looksLikeGuaranteedMethodExitFromDeclaration(String source, Integer start, Integer end) {
        if (source == null || source.isEmpty() || start == null || end == null || start < 0 || end < start) {
            return false;
        }
        int searchStart = Math.min(Math.max(end + 1, start), source.length() - 1);
        int methodBodyStart = -1;
        for (int index = searchStart; index < source.length(); index++) {
            if (source.charAt(index) != '{') {
                continue;
            }
            if (!isLikelyMethodBodyStart(source, index)) {
                break;
            }
            methodBodyStart = index;
            break;
        }
        if (methodBodyStart < 0) {
            return false;
        }
        int methodBodyEnd = findMatchingBraceForward(source, methodBodyStart);
        if (methodBodyEnd < 0 || methodBodyEnd <= methodBodyStart) {
            return false;
        }
        return blockAlwaysExits(source.substring(methodBodyStart + 1, methodBodyEnd));
    }

    private static int findEnclosingMethodBodyStart(String source, int position) {
        int braceDepth = 0;
        for (int index = Math.min(position, source.length() - 1); index >= 0; index--) {
            char current = source.charAt(index);
            if (current == '}') {
                braceDepth++;
                continue;
            }
            if (current != '{') {
                continue;
            }
            if (braceDepth > 0) {
                braceDepth--;
                continue;
            }
            if (isLikelyMethodBodyStart(source, index)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isLikelyMethodBodyStart(String source, int braceIndex) {
        int previous = skipWhitespaceBackward(source, braceIndex - 1);
        if (previous < 0 || source.charAt(previous) != ')') {
            return false;
        }
        int openParen = findMatchingOpenParenBackward(source, previous);
        if (openParen < 0) {
            return false;
        }
        int identifierEnd = skipWhitespaceBackward(source, openParen - 1);
        if (identifierEnd < 0) {
            return false;
        }
        int identifierStart = scanIdentifierStart(source, identifierEnd);
        if (identifierStart < 0) {
            return false;
        }
        String identifier = source.substring(identifierStart, identifierEnd + 1);
        if (identifier.isEmpty() || isControlBlockKeyword(identifier)) {
            return false;
        }
        int beforeIdentifier = skipWhitespaceBackward(source, identifierStart - 1);
        String previousIdentifier = readPreviousIdentifier(source, beforeIdentifier);
        return !"new".equals(previousIdentifier);
    }

    private static boolean blockAlwaysExits(String blockSource) {
        String lastStatement = extractLastTopLevelStatement(blockSource);
        return statementAlwaysExits(lastStatement);
    }

    private static String extractLastTopLevelStatement(String blockSource) {
        if (blockSource == null || blockSource.isEmpty()) {
            return "";
        }
        int braceDepth = 0;
        int parenDepth = 0;
        int bracketDepth = 0;
        int statementStart = 0;
        String lastStatement = "";
        for (int index = 0; index < blockSource.length(); index++) {
            char current = blockSource.charAt(index);
            switch (current) {
                case '(':
                    parenDepth++;
                    break;
                case ')':
                    parenDepth = Math.max(0, parenDepth - 1);
                    break;
                case '[':
                    bracketDepth++;
                    break;
                case ']':
                    bracketDepth = Math.max(0, bracketDepth - 1);
                    break;
                case '{':
                    braceDepth++;
                    break;
                case '}':
                    braceDepth = Math.max(0, braceDepth - 1);
                    if (braceDepth == 0 && parenDepth == 0 && bracketDepth == 0) {
                        String candidate = blockSource.substring(statementStart, index + 1).trim();
                        if (!candidate.isEmpty()
                                && startsWithBlockStatementKeyword(candidate)
                                && !isBlockStatementContinuation(blockSource, index + 1)) {
                            lastStatement = candidate;
                            statementStart = index + 1;
                        }
                    }
                    break;
                case ';':
                    if (braceDepth == 0 && parenDepth == 0 && bracketDepth == 0) {
                        String candidate = blockSource.substring(statementStart, index + 1).trim();
                        if (!candidate.isEmpty()) {
                            lastStatement = candidate;
                        }
                        statementStart = index + 1;
                    }
                    break;
                default:
                    break;
            }
        }
        String tail = blockSource.substring(Math.min(statementStart, blockSource.length())).trim();
        return tail.isEmpty() ? lastStatement : tail;
    }

    private static boolean startsWithBlockStatementKeyword(String statement) {
        if (statement == null || statement.isEmpty()) {
            return false;
        }
        String trimmed = statement.trim();
        return trimmed.startsWith("if")
                || trimmed.startsWith("for")
                || trimmed.startsWith("while")
                || trimmed.startsWith("switch")
                || trimmed.startsWith("try")
                || trimmed.startsWith("synchronized")
                || trimmed.startsWith("do");
    }

    private static boolean isBlockStatementContinuation(String source, int index) {
        String nextToken = readNextIdentifier(source, skipWhitespaceForward(source, index));
        return "catch".equals(nextToken)
                || "finally".equals(nextToken)
                || "else".equals(nextToken);
    }

    private static boolean statementAlwaysExits(String statement) {
        if (statement == null) {
            return false;
        }
        String trimmed = statement.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.startsWith("return") || trimmed.startsWith("throw")) {
            return true;
        }
        return trimmed.startsWith("try") && tryStatementAlwaysExits(trimmed);
    }

    private static boolean tryStatementAlwaysExits(String statement) {
        List<LabeledBlock> blocks = extractTopLevelBlocks(statement);
        if (blocks.isEmpty() || !"try".equals(blocks.get(0).label) || !blockAlwaysExits(blocks.get(0).body)) {
            return false;
        }
        for (int index = 1; index < blocks.size(); index++) {
            LabeledBlock block = blocks.get(index);
            if ("catch".equals(block.label) && !blockAlwaysExits(block.body)) {
                return false;
            }
        }
        return true;
    }

    private static List<LabeledBlock> extractTopLevelBlocks(String source) {
        List<LabeledBlock> blocks = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return blocks;
        }
        for (int index = 0; index < source.length(); index++) {
            if (source.charAt(index) != '{') {
                continue;
            }
            int end = findMatchingBraceForward(source, index);
            if (end < 0) {
                break;
            }
            blocks.add(new LabeledBlock(readBlockLeaderKeyword(source, index), source.substring(index + 1, end)));
            index = end;
        }
        return blocks;
    }

    private static String readBlockLeaderKeyword(String source, int braceIndex) {
        int previous = skipWhitespaceBackward(source, braceIndex - 1);
        if (previous < 0) {
            return "";
        }
        if (source.charAt(previous) == ')') {
            int openParen = findMatchingOpenParenBackward(source, previous);
            if (openParen < 0) {
                return "";
            }
            return readPreviousIdentifier(source, skipWhitespaceBackward(source, openParen - 1));
        }
        return readPreviousIdentifier(source, previous);
    }

    private static boolean isControlBlockKeyword(String identifier) {
        return "if".equals(identifier)
                || "for".equals(identifier)
                || "while".equals(identifier)
                || "switch".equals(identifier)
                || "catch".equals(identifier)
                || "synchronized".equals(identifier)
                || "do".equals(identifier)
                || "try".equals(identifier);
    }

    private static int findMatchingOpenParenBackward(String source, int closeParenIndex) {
        int depth = 0;
        for (int index = closeParenIndex; index >= 0; index--) {
            char current = source.charAt(index);
            if (current == ')') {
                depth++;
            } else if (current == '(') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static int findMatchingBraceForward(String source, int openBraceIndex) {
        int depth = 0;
        for (int index = openBraceIndex; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static int skipWhitespaceBackward(String source, int index) {
        int current = index;
        while (current >= 0 && Character.isWhitespace(source.charAt(current))) {
            current--;
        }
        return current;
    }

    private static int skipWhitespaceForward(String source, int index) {
        int current = Math.max(0, index);
        while (current < source.length() && Character.isWhitespace(source.charAt(current))) {
            current++;
        }
        return current;
    }

    private static int scanIdentifierStart(String source, int identifierEnd) {
        int current = identifierEnd;
        while (current >= 0 && Character.isJavaIdentifierPart(source.charAt(current))) {
            current--;
        }
        return current == identifierEnd ? -1 : current + 1;
    }

    private static String readPreviousIdentifier(String source, int index) {
        if (source == null || index < 0) {
            return "";
        }
        int identifierEnd = skipWhitespaceBackward(source, index);
        if (identifierEnd < 0) {
            return "";
        }
        int identifierStart = scanIdentifierStart(source, identifierEnd);
        if (identifierStart < 0) {
            return "";
        }
        return source.substring(identifierStart, identifierEnd + 1);
    }

    private static String readNextIdentifier(String source, int index) {
        if (source == null || index < 0 || index >= source.length()) {
            return "";
        }
        int current = index;
        while (current < source.length() && !Character.isJavaIdentifierStart(source.charAt(current))) {
            current++;
        }
        if (current >= source.length()) {
            return "";
        }
        int end = current;
        while (end < source.length() && Character.isJavaIdentifierPart(source.charAt(end))) {
            end++;
        }
        return source.substring(current, end);
    }

    private static void rememberSuccessfulClassTargetExtension(
            Object scope,
            Object originalMethod,
            Object compatibleBinding,
            Object invocationSite
    ) throws Exception {
        if (scope == null || originalMethod == null || compatibleBinding == null || invocationSite == null) {
            return;
        }
        Object[] parameters = getFieldValue(originalMethod, "parameters") instanceof Object[]
                ? (Object[]) getFieldValue(originalMethod, "parameters")
                : null;
        Object hiddenReceiverType = parameters != null && parameters.length > 0 ? parameters[0] : null;
        if (!isClassType(hiddenReceiverType)) {
            return;
        }
        String bindingClassName = compatibleBinding.getClass().getSimpleName();
        if (!bindingClassName.contains("ParameterizedGenericMethodBinding")) {
            return;
        }
        Object compilationResult = resolveCompilationResultFromScope(scope);
        String fileName = describeCompilationUnitFileName(compilationResult);
        if (fileName == null) {
            return;
        }
        int start = readIntField(invocationSite, "sourceStart");
        int end = readIntField(invocationSite, "sourceEnd");
        if (start < 0 || end < start) {
            return;
        }
        SUCCESSFUL_CLASS_TARGET_EXTENSION_RANGES.add(fileName + "|" + start + "|" + end);
    }

    private static void rememberSuccessfulExtensionUndefinedRange(
            Object scope,
            Object binding,
            Object invocationSite
    ) throws Exception {
        if (scope == null || binding == null || invocationSite == null) {
            return;
        }
        Object compilationResult = resolveCompilationResultFromScope(scope);
        String fileName = describeCompilationUnitFileName(compilationResult);
        if (fileName == null) {
            return;
        }
        int start = readIntField(invocationSite, "sourceStart");
        int end = readIntField(invocationSite, "sourceEnd");
        if (start < 0 || end < start) {
            return;
        }
        String rangeKey = fileName + "|" + start + "|" + end;
        SUCCESSFUL_EXTENSION_UNDEFINED_RANGES.add(rangeKey);
        String selectorName = getSelectorName(invocationSite);
        if (selectorName.isEmpty()) {
            selectorName = getSelectorName(binding);
        }
        if (!selectorName.isEmpty()) {
            putBoundedConcurrentMap(SUCCESSFUL_EXTENSION_UNDEFINED_SELECTORS, rangeKey, selectorName);
        }
        rememberSuccessfulExtensionFunctionalParameterTypes(scope, binding, invocationSite, rangeKey);
    }

    private static void rememberSuccessfulFunctionalWrapper(
            Object scope,
            Object binding,
            Object invocationSite
    ) throws Exception {
        if (scope == null || binding == null || invocationSite == null) {
            return;
        }
        if (!isReceiverFunctionalWrapperBinding(scope, binding)) {
            return;
        }
        Object compilationResult = resolveCompilationResultFromScope(scope);
        String fileName = describeCompilationUnitFileName(compilationResult);
        if (fileName == null) {
            return;
        }
        int start = readIntField(invocationSite, "sourceStart");
        int end = readIntField(invocationSite, "sourceEnd");
        if (start < 0 || end < start) {
            return;
        }
        SUCCESSFUL_FUNCTIONAL_WRAPPER_RANGES.add(fileName + "|" + start + "|" + end);
    }

    private static boolean hasSuccessfulRangeCovering(Set<String> ranges, String fileName, int start, int end) {
        if (ranges == null || ranges.isEmpty() || fileName == null) {
            return false;
        }
        String prefix = fileName + "|";
        for (String entry : ranges) {
            if (entry == null || !entry.startsWith(prefix)) {
                continue;
            }
            int separator = entry.indexOf('|', prefix.length());
            if (separator < 0) {
                continue;
            }
            try {
                int recordedStart = Integer.parseInt(entry.substring(prefix.length(), separator));
                int recordedEnd = Integer.parseInt(entry.substring(separator + 1));
                if (start >= recordedStart && end <= recordedEnd) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    private static boolean hasSuccessfulRangeNestedWithin(Set<String> ranges, String fileName, int start, int end) {
        if (ranges == null || ranges.isEmpty() || fileName == null) {
            return false;
        }
        String prefix = fileName + "|";
        for (String entry : ranges) {
            if (entry == null || !entry.startsWith(prefix)) {
                continue;
            }
            int separator = entry.indexOf('|', prefix.length());
            if (separator < 0) {
                continue;
            }
            try {
                int recordedStart = Integer.parseInt(entry.substring(prefix.length(), separator));
                int recordedEnd = Integer.parseInt(entry.substring(separator + 1));
                if (recordedStart >= start && recordedEnd <= end) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    private static boolean hasSuccessfulExtensionSelectorCovering(String fileName, int start, int end, String methodName) {
        if (fileName == null || methodName == null || methodName.isEmpty() || SUCCESSFUL_EXTENSION_UNDEFINED_SELECTORS.isEmpty()) {
            return false;
        }
        String prefix = fileName + "|";
        for (Map.Entry<String, String> entry : SUCCESSFUL_EXTENSION_UNDEFINED_SELECTORS.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(prefix) || !methodName.equals(entry.getValue())) {
                continue;
            }
            int separator = key.indexOf('|', prefix.length());
            if (separator < 0) {
                continue;
            }
            try {
                int recordedStart = Integer.parseInt(key.substring(prefix.length(), separator));
                int recordedEnd = Integer.parseInt(key.substring(separator + 1));
                if (start >= recordedStart && end <= recordedEnd) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    private static void rememberSuccessfulExtensionFunctionalParameterTypes(
            Object scope,
            Object binding,
            Object invocationSite,
            String rangeKey
    ) throws Exception {
        if (scope == null || binding == null || invocationSite == null || rangeKey == null || rangeKey.isEmpty()) {
            return;
        }
        Object rawParameters = getFieldValue(binding, "parameters");
        Object rawArguments = getFieldValue(invocationSite, "arguments");
        Object rawArgumentTypes = getFieldValue(invocationSite, "argumentTypes");
        if (!(rawParameters instanceof Object[]) || !(rawArguments instanceof Object[])) {
            return;
        }
        Object[] parameters = (Object[]) rawParameters;
        Object[] arguments = (Object[]) rawArguments;
        Object actualReceiverType = resolveReceiverWrapperActualType(scope, invocationSite, parameters);
        Object[] rawResolvedArgumentTypes = rawArgumentTypes instanceof Object[] ? (Object[]) rawArgumentTypes : null;
        boolean hiddenReceiverShape = parameters.length == arguments.length + 1
                && (actualReceiverType != null || hasInvocationReceiverExpression(invocationSite));
        Object[] visibleParameters = hiddenReceiverShape ? Arrays.copyOfRange(parameters, 1, parameters.length) : parameters;
        Object templateBinding = resolveReceiverFunctionalWrapperTemplateBinding(scope, binding);
        Object[] templateParameters = templateBinding != null && getFieldValue(templateBinding, "parameters") instanceof Object[]
                ? (Object[]) getFieldValue(templateBinding, "parameters")
                : null;
        Object[] templateVisibleParameters = hiddenReceiverShape
                && templateParameters != null
                && templateParameters.length > 1
                ? Arrays.copyOfRange(templateParameters, 1, templateParameters.length)
                : templateParameters;
        Object[] visibleArgumentTypes = rawResolvedArgumentTypes;
        if (hiddenReceiverShape
                && rawResolvedArgumentTypes != null
                && rawResolvedArgumentTypes.length == arguments.length + 1) {
            visibleArgumentTypes = Arrays.copyOfRange(rawResolvedArgumentTypes, 1, rawResolvedArgumentTypes.length);
        }
        Object[] argumentTypes = computeEffectiveInvocationArgumentTypes(
                visibleParameters,
                visibleArgumentTypes,
                arguments
        );
        int limit = Math.min(visibleParameters.length, Math.min(arguments.length, argumentTypes.length));
        if (limit == 0) {
            return;
        }
        LinkedHashSet<Object> functionalParameterTypes = new LinkedHashSet<>();
        LinkedHashSet<Object> functionalReturnTypes = new LinkedHashSet<>();
        Map<Object, Object> receiverWrapperSubstitutions = Collections.emptyMap();
        if (actualReceiverType != null && isReceiverFunctionalWrapperBinding(scope, binding)) {
            receiverWrapperSubstitutions = buildReceiverFunctionalWrapperSubstitutions(
                    scope,
                    binding,
                    parameters[0],
                    actualReceiverType,
                    parameters
            );
        }
        for (int index = 0; index < limit; index++) {
            if (!isFunctionalInvocationArgument(arguments[index]) || visibleParameters[index] == null) {
                continue;
            }
            Object expectedType = templateVisibleParameters != null
                    && index < templateVisibleParameters.length
                    && templateVisibleParameters[index] != null
                    ? templateVisibleParameters[index]
                    : visibleParameters[index];
            if (!receiverWrapperSubstitutions.isEmpty()) {
                Object receiverSpecializedType = substituteType(scope, expectedType, receiverWrapperSubstitutions);
                if (receiverSpecializedType != null && !isProblem(receiverSpecializedType)) {
                    expectedType = receiverSpecializedType;
                }
            }
            expectedType = specializeFunctionalExpectedTypeFromArgumentTypes(
                    scope,
                    expectedType,
                    visibleParameters,
                    argumentTypes,
                    arguments,
                    index,
                    false
            );
            if (expectedType == null || isProblem(expectedType)) {
                continue;
            }
            Object descriptor = invokeMethod(expectedType, "getSingleAbstractMethod", scope, true);
            if (descriptor == null || isProblem(descriptor)) {
                continue;
            }
            Object descriptorReturnType = getFieldValue(descriptor, "returnType");
            if (descriptorReturnType != null
                    && !isProblem(descriptorReturnType)
                    && !isTypeVariable(descriptorReturnType)) {
                functionalReturnTypes.add(descriptorReturnType);
            }
            Object[] descriptorParameters = (Object[]) getFieldValue(descriptor, "parameters");
            if (descriptorParameters != null) {
                for (Object descriptorParameter : descriptorParameters) {
                    Object normalizedParameterType = normalizeMethodLookupType(descriptorParameter);
                    if (normalizedParameterType != null) {
                        functionalParameterTypes.add(normalizedParameterType);
                    }
                }
            }
        }
        if (!functionalParameterTypes.isEmpty()) {
            putBoundedConcurrentMap(
                    SUCCESSFUL_EXTENSION_FUNCTIONAL_PARAMETER_TYPES,
                    rangeKey,
                    Collections.unmodifiableList(new ArrayList<>(functionalParameterTypes))
            );
        }
        if (!functionalReturnTypes.isEmpty()) {
            putBoundedConcurrentMap(
                    SUCCESSFUL_EXTENSION_FUNCTIONAL_RETURN_TYPES,
                    rangeKey,
                    Collections.unmodifiableList(new ArrayList<>(functionalReturnTypes))
            );
        }
    }

    private static List<Object> getSuccessfulExtensionFunctionalParameterTypesCovering(String fileName, int start, int end) {
        if (fileName == null || SUCCESSFUL_EXTENSION_FUNCTIONAL_PARAMETER_TYPES.isEmpty()) {
            return Collections.emptyList();
        }
        String prefix = fileName + "|";
        LinkedHashSet<Object> result = new LinkedHashSet<>();
        for (Map.Entry<String, List<Object>> entry : SUCCESSFUL_EXTENSION_FUNCTIONAL_PARAMETER_TYPES.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(prefix)) {
                continue;
            }
            int separator = key.indexOf('|', prefix.length());
            if (separator < 0) {
                continue;
            }
            try {
                int recordedStart = Integer.parseInt(key.substring(prefix.length(), separator));
                int recordedEnd = Integer.parseInt(key.substring(separator + 1));
                if (start >= recordedStart && end <= recordedEnd && entry.getValue() != null) {
                    result.addAll(entry.getValue());
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return result.isEmpty() ? Collections.emptyList() : new ArrayList<>(result);
    }

    private static List<Object> getSuccessfulExtensionFunctionalReturnTypesCovering(String fileName, int start, int end) {
        if (fileName == null || SUCCESSFUL_EXTENSION_FUNCTIONAL_RETURN_TYPES.isEmpty()) {
            return Collections.emptyList();
        }
        String prefix = fileName + "|";
        LinkedHashSet<Object> result = new LinkedHashSet<>();
        for (Map.Entry<String, List<Object>> entry : SUCCESSFUL_EXTENSION_FUNCTIONAL_RETURN_TYPES.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(prefix)) {
                continue;
            }
            int separator = key.indexOf('|', prefix.length());
            if (separator < 0) {
                continue;
            }
            try {
                int recordedStart = Integer.parseInt(key.substring(prefix.length(), separator));
                int recordedEnd = Integer.parseInt(key.substring(separator + 1));
                if (start >= recordedStart && end <= recordedEnd && entry.getValue() != null) {
                    result.addAll(entry.getValue());
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return result.isEmpty() ? Collections.emptyList() : new ArrayList<>(result);
    }

    private static Object resolveCompilationResultFromScope(Object scope) throws Exception {
        Object compilationUnitScope = getCompilationUnitScope(scope);
        if (compilationUnitScope == null) {
            return null;
        }
        Object referenceContext = getFieldValue(compilationUnitScope, "referenceContext");
        if (referenceContext == null) {
            return null;
        }
        Object compilationResult = invokeOptionalMethod(referenceContext, "compilationResult");
        if (compilationResult == null) {
            compilationResult = getFieldValue(referenceContext, "compilationResult");
        }
        return compilationResult;
    }

    private static boolean isScopeInConfiguredZirconProject(Object scope) {
        String configuredRoots = Util.getProperty("zircon.project.roots", "").trim();
        if (configuredRoots.isEmpty()) {
            return true;
        }
        try {
            String fileName = describeCompilationUnitFileName(resolveCompilationResultFromScope(scope));
            if (fileName == null || fileName.trim().isEmpty()) {
                return false;
            }
            Path sourcePath = Paths.get(fileName).toAbsolutePath().normalize();
            for (String configuredRoot : configuredRoots.split(
                    java.util.regex.Pattern.quote(File.pathSeparator)
            )) {
                String trimmed = configuredRoot.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                Path root = Paths.get(trimmed).toAbsolutePath().normalize();
                if (sourcePath.startsWith(root)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private static boolean shouldSuppressExplicitThisFunctionalDirectCallProblem(
            Object compilationResult,
            Integer problemId,
            Integer start,
            Integer end
    ) {
        if (!isExplicitThisFunctionalDirectCallProblemId(problemId)
                || start == null
                || end == null) {
            return false;
        }
        String source = getCompilationUnitSource(compilationResult);
        if (source == null || source.isEmpty()) {
            return false;
        }
        String normalizedContext = removeWhitespace(extractSourceContext(source, start, end, 64));
        boolean shouldSuppress = normalizedContext.contains("this.")
                && normalizedContext.contains("(()->");
        if (shouldSuppress && Util.isDebugEnabled()) {
            Util.log("[ZirconCore] suppressProblem"
                    + ": id=" + problemId
                    + ", file=" + describeCompilationUnitFileName(compilationResult)
                    + ", range=" + start + "-" + end
                    + ", context=" + normalizedContext);
        }
        return shouldSuppress;
    }

    private static boolean shouldSuppressSuccessfulExtensionFunctionalReturnProblem(
            Object compilationResult,
            Integer problemId,
            Integer start,
            Integer end,
            String message
    ) throws Exception {
        if (!isSuccessfulExtensionFunctionalReturnProblemId(problemId)
                || start == null
                || end == null
                || message == null) {
            return false;
        }
        String fileName = describeCompilationUnitFileName(compilationResult);
        if (isFunctionalTargetTypeProblemMessage(message)
                && fileName != null
                && hasSuccessfulRangeCovering(PROVEN_EXTENSION_FUNCTIONAL_TARGET_RANGES, fileName, start, end)) {
            return true;
        }
        if (shouldSuppressProvableFunctionalExtensionProblem(compilationResult, problemId, start, end, message)) {
            return true;
        }
        if (fileName == null || !hasSuccessfulRangeCovering(SUCCESSFUL_EXTENSION_UNDEFINED_RANGES, fileName, start, end)) {
            return false;
        }
        String source = getCompilationUnitSource(compilationResult);
        String normalizedContext = removeWhitespace(extractSourceContext(source, start, end, 80));
        if (!looksLikeFunctionalProblemContext(compilationResult, start, end, normalizedContext)) {
            return false;
        }
        List<Object> functionalReturnTypes = getSuccessfulExtensionFunctionalReturnTypesCovering(fileName, start, end);
        if (functionalReturnTypes.isEmpty()) {
            return false;
        }
        boolean shouldSuppress = false;
        if (isFunctionalTargetTypeProblemMessage(message)) {
            shouldSuppress = hasUsableFunctionalReturnType(functionalReturnTypes);
        } else if (isFunctionalVoidReturnProblem(problemId, message)) {
            shouldSuppress = hasNonVoidFunctionalReturnType(functionalReturnTypes);
        } else if (isFunctionalTypeVariableReturnProblem(compilationResult, problemId, start, end, message)) {
            shouldSuppress = hasUsableFunctionalReturnType(functionalReturnTypes);
        }
        if (shouldSuppress && Util.isDebugEnabled()) {
            Util.log("[ZirconCore] suppressSuccessfulExtensionFunctionalReturnProblem"
                    + ": file=" + fileName
                    + ", range=" + start + "-" + end
                    + ", message=" + message
                    + ", returnTypes=" + describeTypeCollection(functionalReturnTypes)
                    + ", context=" + normalizedContext);
        }
        return shouldSuppress;
    }

    private static boolean shouldSuppressProvableFunctionalExtensionProblem(
            Object compilationResult,
            Integer problemId,
            Integer start,
            Integer end,
            String message
    ) throws Exception {
        if (compilationResult == null
                || problemId == null
                || start == null
                || end == null
                || message == null
                || (!isSuccessfulExtensionFunctionalReturnProblemId(problemId) && problemId != 67108964)) {
            return false;
        }
        Object compilationUnit = invokeOptionalMethod(compilationResult, "getCompilationUnit");
        if (compilationUnit == null) {
            compilationUnit = getFieldValue(compilationResult, "compilationUnit");
        }
        if (compilationUnit == null) {
            compilationUnit = getFieldValue(compilationResult, "compilationUnitDeclaration");
        }
        if (compilationUnit == null) {
            return false;
        }
        RangeAstMatch match = findAstNodeCoveringRange(compilationUnit, start, end, null, null, new IdentityHashMap<>(), 0);
        if (match == null || match.scope == null) {
            return false;
        }
        Object lambdaNode = match.enclosingLambda != null ? match.enclosingLambda : (isLambdaExpression(match.node) ? match.node : null);
        Object proofRoot = lambdaNode != null ? getFieldValue(lambdaNode, "body") : match.node;
        String fileName = describeCompilationUnitFileName(compilationResult);
        String normalizedContext = removeWhitespace(extractSourceContext(getCompilationUnitSource(compilationResult), start, end, 96));
        boolean nestedSuccessfulExtension = lambdaNode != null
                && fileName != null
                && (hasSuccessfulRangeNestedWithin(SUCCESSFUL_EXTENSION_UNDEFINED_RANGES, fileName, start, end)
                || hasSuccessfulRangeNestedWithin(SUCCESSFUL_CLASS_TARGET_EXTENSION_RANGES, fileName, start, end)
                || hasSuccessfulRangeNestedWithin(SUCCESSFUL_FUNCTIONAL_WRAPPER_RANGES, fileName, start, end));
        Object provedBinding = findProvableExtensionBindingInNode(match.scope, proofRoot != null ? proofRoot : match.node, new IdentityHashMap<>(), 0);
        if ((provedBinding == null || isProblem(provedBinding))
                && problemId == 67108964
                && lambdaNode != null
                && shouldSuppressReceiverWrapperLambdaUndefinedProblem(
                compilationUnit,
                match.scope,
                lambdaNode,
                extractUndefinedMethodName(message)
        )) {
            return true;
        }
        if (provedBinding == null || isProblem(provedBinding)) {
            if (!nestedSuccessfulExtension
                    || !looksLikeFunctionalProblemContext(compilationResult, start, end, normalizedContext)) {
                return false;
            }
            if (problemId == 67108964) {
                return true;
            }
            if (isFunctionalTargetTypeProblemMessage(message)
                    || isFunctionalVoidReturnProblem(problemId, message)
                    || isFunctionalTypeVariableReturnProblem(compilationResult, problemId, start, end, message)
                    || isFunctionalLambdaReturnProblem(message)) {
                if (Util.isDebugEnabled()) {
                    Util.log("[ZirconCore] suppressNestedSuccessfulFunctionalProblem"
                            + ": file=" + fileName
                            + ", range=" + start + "-" + end
                            + ", message=" + message
                            + ", nestedExtension=true");
                }
                return true;
            }
            return false;
        }
        List<Object> functionalReturnTypes = Collections.emptyList();
        if (lambdaNode != null) {
            Object expectedType = getFieldValue(lambdaNode, "expectedType");
            if (expectedType != null && !isProblem(expectedType)) {
                rememberSuccessfulFunctionalArgumentRange(match.scope, expectedType, lambdaNode);
                functionalReturnTypes = getFunctionalReturnTypesFromExpectedType(match.scope, expectedType);
            }
        }
        boolean shouldSuppress = false;
        if (problemId == 67108964) {
            shouldSuppress = true;
        } else if (isFunctionalTargetTypeProblemMessage(message)) {
            shouldSuppress = functionalReturnTypes.isEmpty() || hasUsableFunctionalReturnType(functionalReturnTypes);
        } else if (isFunctionalVoidReturnProblem(problemId, message)) {
            shouldSuppress = !functionalReturnTypes.isEmpty() && hasNonVoidFunctionalReturnType(functionalReturnTypes);
        } else if (isFunctionalTypeVariableReturnProblem(compilationResult, problemId, start, end, message)) {
            shouldSuppress = !functionalReturnTypes.isEmpty() && hasUsableFunctionalReturnType(functionalReturnTypes);
        }
        if (shouldSuppress && Util.isDebugEnabled()) {
            Util.log("[ZirconCore] suppressProvableFunctionalExtensionProblem"
                    + ": file=" + describeCompilationUnitFileName(compilationResult)
                    + ", range=" + start + "-" + end
                    + ", message=" + message
                    + ", proofBinding=" + describeMethodBindingDetailed(provedBinding)
                    + ", expectedReturns=" + describeTypeCollection(functionalReturnTypes));
        }
        return shouldSuppress;
    }

    private static List<Object> getFunctionalReturnTypesFromExpectedType(Object scope, Object expectedType) throws Exception {
        if (scope == null || expectedType == null || isProblem(expectedType)) {
            return Collections.emptyList();
        }
        Object descriptor = invokeMethod(expectedType, "getSingleAbstractMethod", scope, true);
        if (descriptor == null || isProblem(descriptor)) {
            return Collections.emptyList();
        }
        Object descriptorReturnType = getFieldValue(descriptor, "returnType");
        if (descriptorReturnType == null || isProblem(descriptorReturnType) || isTypeVariable(descriptorReturnType)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(descriptorReturnType);
    }

    private static RangeAstMatch findAstNodeCoveringRange(
            Object node,
            int start,
            int end,
            Object inheritedLambda,
            Object inheritedScope,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 24) {
            return null;
        }
        if (node instanceof Object[]) {
            RangeAstMatch best = null;
            for (Object element : (Object[]) node) {
                RangeAstMatch candidate = findAstNodeCoveringRange(
                        element,
                        start,
                        end,
                        inheritedLambda,
                        inheritedScope,
                        visited,
                        depth + 1
                );
                if (candidate != null && (best == null || candidate.span < best.span)) {
                    best = candidate;
                }
            }
            return best;
        }
        if (!isAstNode(node) || visited.put(node, Boolean.TRUE) != null) {
            return null;
        }
        int nodeStart = readIntField(node, "sourceStart");
        int nodeEnd = readIntField(node, "sourceEnd");
        Object currentLambda = isLambdaExpression(node) ? node : inheritedLambda;
        Object currentScope = inheritedScope;
        Object nodeScope = findField(node.getClass(), "scope") != null ? getFieldValue(node, "scope") : null;
        if (nodeScope == null && isLambdaExpression(node)) {
            nodeScope = getFieldValue(node, "enclosingScope");
        }
        if (nodeScope != null) {
            currentScope = nodeScope;
        }
        RangeAstMatch best = null;
        if (nodeStart >= 0 && nodeEnd >= nodeStart && start >= nodeStart && end <= nodeEnd) {
            best = new RangeAstMatch(node, currentLambda, currentScope, nodeEnd - nodeStart);
        }
        for (Object child : getAstChildren(node)) {
            RangeAstMatch candidate = findAstNodeCoveringRange(
                    child,
                    start,
                    end,
                    currentLambda,
                    currentScope,
                    visited,
                    depth + 1
            );
            if (candidate != null && (best == null || candidate.span < best.span)) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean shouldSuppressReceiverWrapperLambdaUndefinedProblem(
            Object compilationUnit,
            Object scope,
            Object lambdaNode,
            String methodName
    ) throws Exception {
        if (compilationUnit == null
                || scope == null
                || lambdaNode == null
                || methodName == null
                || methodName.isEmpty()) {
            return false;
        }
        InvocationArgumentMatch invocationMatch = findEnclosingInvocationArgumentMatch(
                compilationUnit,
                lambdaNode,
                new IdentityHashMap<>(),
                0
        );
        if (invocationMatch == null || invocationMatch.invocation == null) {
            return false;
        }
        Object binding = findField(invocationMatch.invocation.getClass(), "binding") != null
                ? getFieldValue(invocationMatch.invocation, "binding")
                : null;
        if (binding == null || isProblem(binding) || !isReceiverFunctionalWrapperBinding(scope, binding)) {
            return false;
        }
        Object[] parameters = getFieldValue(binding, "parameters") instanceof Object[]
                ? (Object[]) getFieldValue(binding, "parameters")
                : null;
        Object[] arguments = getFieldValue(invocationMatch.invocation, "arguments") instanceof Object[]
                ? (Object[]) getFieldValue(invocationMatch.invocation, "arguments")
                : null;
        if (parameters == null || arguments == null) {
            return false;
        }
        boolean hiddenReceiverShape = parameters.length == arguments.length + 1
                && (resolveReceiverWrapperActualType(scope, invocationMatch.invocation, parameters) != null
                || hasInvocationReceiverExpression(invocationMatch.invocation));
        int parameterIndex = hiddenReceiverShape ? invocationMatch.argumentIndex + 1 : invocationMatch.argumentIndex;
        if (parameterIndex < 0 || parameterIndex >= parameters.length) {
            return false;
        }
        Object expectedType = specializeFunctionalExpectedTypeForReceiverWrapper(
                scope,
                binding,
                invocationMatch.invocation,
                parameters[parameterIndex],
                parameters
        );
        if (expectedType == null || isProblem(expectedType)) {
            return false;
        }
        Object descriptor = invokeMethod(expectedType, "getSingleAbstractMethod", scope, true);
        if (descriptor == null || isProblem(descriptor)) {
            return false;
        }
        Object[] descriptorParameters = getFieldValue(descriptor, "parameters") instanceof Object[]
                ? (Object[]) getFieldValue(descriptor, "parameters")
                : null;
        if (descriptorParameters == null) {
            return false;
        }
        for (Object descriptorParameter : descriptorParameters) {
            Object normalized = normalizeMethodLookupType(descriptorParameter);
            if (normalized != null && typeDefinesMethodNamed(normalized, methodName)) {
                return true;
            }
        }
        return false;
    }

    private static InvocationArgumentMatch findEnclosingInvocationArgumentMatch(
            Object node,
            Object targetArgument,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || targetArgument == null || depth > 24) {
            return null;
        }
        if (node instanceof Object[]) {
            InvocationArgumentMatch best = null;
            for (Object element : (Object[]) node) {
                InvocationArgumentMatch candidate = findEnclosingInvocationArgumentMatch(
                        element,
                        targetArgument,
                        visited,
                        depth + 1
                );
                if (candidate != null && (best == null || candidate.span < best.span)) {
                    best = candidate;
                }
            }
            return best;
        }
        if (!isAstNode(node) || visited.put(node, Boolean.TRUE) != null) {
            return null;
        }
        InvocationArgumentMatch best = null;
        if ("MessageSend".equals(node.getClass().getSimpleName())) {
            Object rawArguments = findField(node.getClass(), "arguments") != null ? getFieldValue(node, "arguments") : null;
            if (rawArguments instanceof Object[]) {
                Object[] arguments = (Object[]) rawArguments;
                for (int index = 0; index < arguments.length; index++) {
                    if (arguments[index] == targetArgument) {
                        int nodeStart = readIntField(node, "sourceStart");
                        int nodeEnd = readIntField(node, "sourceEnd");
                        int span = nodeStart >= 0 && nodeEnd >= nodeStart ? nodeEnd - nodeStart : Integer.MAX_VALUE;
                        best = new InvocationArgumentMatch(node, index, span);
                        break;
                    }
                }
            }
        }
        for (Object child : getAstChildren(node)) {
            InvocationArgumentMatch candidate = findEnclosingInvocationArgumentMatch(
                    child,
                    targetArgument,
                    visited,
                    depth + 1
            );
            if (candidate != null && (best == null || candidate.span < best.span)) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isSuccessfulExtensionFunctionalReturnProblemId(Integer problemId) {
        if (problemId == null) {
            return false;
        }
        switch (problemId) {
            case 16777233:
            case 16777235:
            case 553648781:
            case 67108969:
                return true;
            default:
                return false;
        }
    }

    private static boolean looksLikeFunctionalSourceContext(String normalizedContext) {
        return normalizedContext != null
                && (!normalizedContext.isEmpty())
                && (normalizedContext.contains("->") || normalizedContext.contains("::"));
    }

    private static boolean looksLikeFunctionalProblemContext(
            Object compilationResult,
            int start,
            int end,
            String normalizedContext
    ) throws Exception {
        if (looksLikeFunctionalSourceContext(normalizedContext)) {
            return true;
        }
        if (compilationResult == null) {
            return false;
        }
        Object compilationUnit = invokeOptionalMethod(compilationResult, "getCompilationUnit");
        if (compilationUnit == null) {
            compilationUnit = getFieldValue(compilationResult, "compilationUnit");
        }
        if (compilationUnit == null) {
            compilationUnit = getFieldValue(compilationResult, "compilationUnitDeclaration");
        }
        if (compilationUnit == null) {
            return false;
        }
        RangeAstMatch match = findAstNodeCoveringRange(compilationUnit, start, end, null, null, new IdentityHashMap<>(), 0);
        if (match == null) {
            return false;
        }
        return match.enclosingLambda != null
                || isLambdaExpression(match.node)
                || match.node.getClass().getSimpleName().endsWith("ReferenceExpression");
    }

    private static boolean containsOptionalSyntaxInFunctionalContext(
            Object compilationResult,
            int start,
            int end,
            String source,
            String normalizedContext
    ) throws Exception {
        if (containsOptionalSyntax(normalizedContext)) {
            return true;
        }
        if (compilationResult == null || source == null || source.isEmpty()) {
            return false;
        }
        Object compilationUnit = invokeOptionalMethod(compilationResult, "getCompilationUnit");
        if (compilationUnit == null) {
            compilationUnit = getFieldValue(compilationResult, "compilationUnit");
        }
        if (compilationUnit == null) {
            compilationUnit = getFieldValue(compilationResult, "compilationUnitDeclaration");
        }
        if (compilationUnit == null) {
            return false;
        }
        RangeAstMatch match = findAstNodeCoveringRange(compilationUnit, start, end, null, null, new IdentityHashMap<>(), 0);
        if (match == null) {
            return false;
        }
        if (containsOptionalSyntax(extractNodeNormalizedSource(source, match.node))) {
            return true;
        }
        return containsOptionalSyntax(extractNodeNormalizedSource(source, match.enclosingLambda));
    }

    private static String extractNodeNormalizedSource(String source, Object node) {
        if (source == null || source.isEmpty() || node == null) {
            return "";
        }
        try {
            int sourceStart = readIntField(node, "sourceStart");
            int sourceEnd = readIntField(node, "sourceEnd");
            if (sourceStart < 0 || sourceEnd < sourceStart || sourceStart >= source.length()) {
                return "";
            }
            int safeEnd = Math.min(source.length() - 1, sourceEnd);
            return removeWhitespace(source.substring(sourceStart, safeEnd + 1));
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean containsOptionalSyntax(String normalizedContext) {
        return normalizedContext != null
                && (!normalizedContext.isEmpty())
                && (normalizedContext.contains("?.") || normalizedContext.contains("?:"));
    }

    private static boolean isOptionalFunctionalProblem(
            Object compilationResult,
            Integer problemId,
            Integer start,
            Integer end,
            String message
    ) throws Exception {
        if (problemId == null || message == null) {
            return false;
        }
        if ((problemId == 33554502 && message.endsWith("cannot be resolved or is not a field"))
                || (problemId == 570425394 && message.endsWith("cannot be resolved"))
                || (problemId == 33554515 && message.endsWith("cannot be resolved to a variable"))
                || (problemId == 67108964 && message.startsWith("The method ") && message.contains(" is undefined for the type "))
                || (problemId == 1610612958 && "Invalid expression as statement".equals(message))) {
            return true;
        }
        return isFunctionalTargetTypeProblemMessage(message)
                || isFunctionalVoidReturnProblem(problemId, message)
                || isFunctionalTypeVariableReturnProblem(compilationResult, problemId, start, end, message)
                || isFunctionalLambdaReturnProblem(message);
    }

    private static boolean isFunctionalTargetTypeProblemMessage(String message) {
        return "The target type of this expression must be a functional interface".equals(message);
    }

    private static boolean isFunctionalLambdaReturnProblem(String message) {
        return message != null
                && message.startsWith("This lambda expression must return a result of type ");
    }

    private static boolean isFunctionalVoidReturnProblem(Integer problemId, String message) {
        if (problemId == null || message == null) {
            return false;
        }
        if (problemId == 67108969) {
            return message.startsWith("Void methods cannot return a value");
        }
        if (problemId == 67108970) {
            return message.startsWith("Cannot return a void result");
        }
        return problemId == 16777233
                && message.startsWith("Type mismatch: cannot convert from ")
                && "void".equals(extractConversionTargetTypeName(message));
    }

    private static boolean isFunctionalTypeVariableReturnProblem(
            Object compilationResult,
            Integer problemId,
            Integer start,
            Integer end,
            String message
    ) throws Exception {
        return problemId != null
                && (problemId == 16777235 || problemId == 16777233)
                && message != null
                && message.startsWith("Type mismatch: cannot convert from ")
                && isConversionTargetBackedByTypeVariable(compilationResult, start, end, message, false);
    }

    private static String extractConversionTargetTypeName(String message) {
        if (message == null) {
            return "";
        }
        int marker = message.lastIndexOf(" to ");
        return marker < 0 ? "" : message.substring(marker + " to ".length()).trim();
    }

    private static boolean isConversionTargetBackedByTypeVariable(
            Object compilationResult,
            Integer start,
            Integer end,
            String message,
            boolean includeNestedTypeArguments
    ) throws Exception {
        if (compilationResult == null || start == null || end == null || message == null) {
            return false;
        }
        String fileName = describeCompilationUnitFileName(compilationResult);
        Set<String> semanticRanges = includeNestedTypeArguments
                ? SEMANTIC_NESTED_TYPE_VARIABLE_TARGET_RANGES
                : SEMANTIC_TYPE_VARIABLE_TARGET_RANGES;
        Object compilationUnit = invokeOptionalMethod(compilationResult, "getCompilationUnit");
        if (compilationUnit == null) {
            compilationUnit = getFieldValue(compilationResult, "compilationUnit");
        }
        if (compilationUnit == null) {
            compilationUnit = getFieldValue(compilationResult, "compilationUnitDeclaration");
        }
        if (compilationUnit == null) {
            return fileName != null && hasSuccessfulRangeCovering(semanticRanges, fileName, start, end);
        }
        RangeAstMatch match = findAstNodeCoveringRange(
                compilationUnit,
                start,
                end,
                null,
                null,
                new IdentityHashMap<>(),
                0
        );
        if (match == null || match.node == null) {
            return fileName != null && hasSuccessfulRangeCovering(semanticRanges, fileName, start, end);
        }

        Object targetBinding = findExpectedTypeBindingForProblemNode(match.node);
        Object lambdaReturnType = match.enclosingLambda != null
                ? findLambdaReturnTypeBinding(match.enclosingLambda)
                : null;
        if (targetBinding != null && !isProblem(targetBinding)) {
            boolean semanticTypeVariable = includeNestedTypeArguments
                    ? containsTypeVariableBinding(targetBinding, new IdentityHashMap<>(), 0)
                    : isActualTypeVariableBinding(targetBinding);
            if (semanticTypeVariable) {
                return true;
            }
            // A diagnostic whose smallest covering node is the lambda itself sees
            // Supplier<T> as expectedType. The conversion target is the SAM return
            // binding, not the functional-interface binding.
            if (!isLambdaExpression(match.node)) {
                return false;
            }
        }
        boolean lambdaTypeVariable = lambdaReturnType != null
                && (includeNestedTypeArguments
                ? containsTypeVariableBinding(lambdaReturnType, new IdentityHashMap<>(), 0)
                : isActualTypeVariableBinding(lambdaReturnType));
        return lambdaTypeVariable
                || (fileName != null && hasSuccessfulRangeCovering(semanticRanges, fileName, start, end));
    }

    private static Object findExpectedTypeBindingForProblemNode(Object node) throws Exception {
        if (node == null) {
            return null;
        }
        if (findField(node.getClass(), "expectedType") != null) {
            Object expectedType = getFieldValue(node, "expectedType");
            if (expectedType != null && !isProblem(expectedType)) {
                return expectedType;
            }
        }
        String simpleName = node.getClass().getSimpleName();
        if ("LocalDeclaration".equals(simpleName)) {
            Object binding = getFieldValue(node, "binding");
            Object type = binding != null ? getFieldValue(binding, "type") : null;
            if (type != null && !isProblem(type)) {
                return type;
            }
            Object typeReference = getFieldValue(node, "type");
            Object resolvedType = typeReference != null ? getFieldValue(typeReference, "resolvedType") : null;
            if (resolvedType != null && !isProblem(resolvedType)) {
                return resolvedType;
            }
        }
        if ("Assignment".equals(simpleName)) {
            Object lhs = getFieldValue(node, "lhs");
            Object lhsType = lhs != null ? getFieldValue(lhs, "resolvedType") : null;
            if (lhsType != null && !isProblem(lhsType)) {
                return lhsType;
            }
            Object lhsBinding = lhs != null ? getFieldValue(lhs, "binding") : null;
            Object bindingType = lhsBinding != null ? getFieldValue(lhsBinding, "type") : null;
            if (bindingType != null && !isProblem(bindingType)) {
                return bindingType;
            }
        }
        return null;
    }

    private static Object findLambdaReturnTypeBinding(Object lambdaExpression) throws Exception {
        if (!isLambdaExpression(lambdaExpression)) {
            return null;
        }
        Object descriptor = getFieldValue(lambdaExpression, "descriptor");
        if (descriptor == null || isProblem(descriptor)) {
            Object expectedType = getFieldValue(lambdaExpression, "expectedType");
            Object scope = getFieldValue(lambdaExpression, "scope");
            if (expectedType != null && scope != null && !isProblem(expectedType)) {
                descriptor = invokeMethod(expectedType, "getSingleAbstractMethod", scope, true);
            }
        }
        Object returnType = descriptor != null && !isProblem(descriptor)
                ? getFieldValue(descriptor, "returnType")
                : null;
        return returnType != null && !isProblem(returnType) ? returnType : null;
    }

    private static boolean containsTypeVariableBinding(
            Object typeBinding,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (typeBinding == null || depth > 8 || visited.put(typeBinding, Boolean.TRUE) != null) {
            return false;
        }
        if (isActualTypeVariableBinding(typeBinding)) {
            return true;
        }
        Object[] typeArguments = safeGetTypeArguments(typeBinding);
        if (typeArguments != null) {
            for (Object typeArgument : typeArguments) {
                if (containsTypeVariableBinding(typeArgument, visited, depth + 1)) {
                    return true;
                }
            }
        }
        Object bound = findField(typeBinding.getClass(), "bound") != null
                ? getFieldValue(typeBinding, "bound")
                : null;
        return bound != null && bound != typeBinding
                && containsTypeVariableBinding(bound, visited, depth + 1);
    }

    private static boolean isActualTypeVariableBinding(Object typeBinding) throws Exception {
        if (typeBinding == null || isProblem(typeBinding)) {
            return false;
        }
        if (isTypeVariable(typeBinding)) {
            return true;
        }
        String className = typeBinding.getClass().getName();
        return className.endsWith("InferenceVariable") || className.endsWith("TypeVariableBinding");
    }

    private static boolean hasUsableFunctionalReturnType(List<Object> functionalReturnTypes) throws Exception {
        if (functionalReturnTypes == null || functionalReturnTypes.isEmpty()) {
            return false;
        }
        for (Object functionalReturnType : functionalReturnTypes) {
            if (functionalReturnType != null
                    && !isProblem(functionalReturnType)
                    && !isTypeVariable(functionalReturnType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNonVoidFunctionalReturnType(List<Object> functionalReturnTypes) throws Exception {
        if (functionalReturnTypes == null || functionalReturnTypes.isEmpty()) {
            return false;
        }
        for (Object functionalReturnType : functionalReturnTypes) {
            if (functionalReturnType == null
                    || isProblem(functionalReturnType)
                    || isTypeVariable(functionalReturnType)) {
                continue;
            }
            String readableName = getReadableTypeName(functionalReturnType);
            String simpleName = getTypeName(functionalReturnType);
            if (!"void".equals(readableName) && !"void".equals(simpleName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExplicitThisFunctionalDirectCallProblemId(Integer problemId) {
        if (problemId == null) {
            return false;
        }
        switch (problemId) {
            case 16777233:
            case 16777235:
            case 553648781:
            case 67108964:
            case 67108969:
                return true;
            default:
                return false;
        }
    }

    public static void logRecordedProblem(Object compilationResult, Object problem) {
        if (!Util.isDebugEnabled() || compilationResult == null || problem == null) {
            return;
        }
        try {
            String fileName = describeCompilationUnitFileName(compilationResult);
            if (!isProblemFileLoggingEnabled(fileName)) {
                return;
            }
            Object id = invokeMethod(problem, "getID");
            Object message = invokeMethod(problem, "getMessage");
            Object start = invokeMethod(problem, "getSourceStart");
            Object end = invokeMethod(problem, "getSourceEnd");
            String key = fileName + "|" + id + "|" + start + "|" + end + "|" + message;
            if (!RECORDED_PROBLEM_KEYS.add(key)) {
                return;
            }
            Util.log("[ZirconCore] recordedProblem"
                    + ": file=" + fileName
                    + ", id=" + id
                    + ", range=" + start + "-" + end
                    + ", message=" + message);
            Integer numericId = id instanceof Integer ? (Integer) id : null;
            Integer numericStart = start instanceof Integer ? (Integer) start : null;
            Integer numericEnd = end instanceof Integer ? (Integer) end : null;
            logUnresolvedLocalProblemDetails(compilationResult, numericId, numericStart, numericEnd, message == null ? null : String.valueOf(message), "recordedProblem");
        } catch (Exception ignored) {
        }
    }

    public static void logLambdaGenerateCodeFailure(Object lambdaExpression, Throwable throwable) {
        if (lambdaExpression == null || throwable == null) {
            return;
        }
        try {
            int sourceStart = readIntField(lambdaExpression, "sourceStart");
            int sourceEnd = readIntField(lambdaExpression, "sourceEnd");
            String key = sourceStart + "|" + sourceEnd + "|" + throwable.getClass().getName();
            if (!LAMBDA_GENERATE_CODE_FAILURE_KEYS.add(key)) {
                return;
            }
            Object descriptor = getFieldValue(lambdaExpression, "descriptor");
            Object binding = getFieldValue(lambdaExpression, "binding");
            Object body = getFieldValue(lambdaExpression, "body");
            Object[] arguments = getFieldValue(lambdaExpression, "arguments") instanceof Object[]
                    ? (Object[]) getFieldValue(lambdaExpression, "arguments")
                    : new Object[0];
            Object[] outerLocalVariables = getFieldValue(lambdaExpression, "outerLocalVariables") instanceof Object[]
                    ? (Object[]) getFieldValue(lambdaExpression, "outerLocalVariables")
                    : new Object[0];
            Util.log("[ZirconCore] lambdaGenerateCode failure"
                    + ": source=" + sourceStart + "-" + sourceEnd
                    + ", descriptor=" + describeMethodBindingDetailed(descriptor)
                    + ", binding=" + describeMethodBindingDetailed(binding)
                    + ", expectedType=" + describeTypeDebug(getFieldValue(lambdaExpression, "expectedType"))
                    + ", resolvedType=" + describeTypeDebug(getFieldValue(lambdaExpression, "resolvedType"))
                    + ", scope=" + describeScopeDebug(getFieldValue(lambdaExpression, "scope"))
                    + ", enclosingScope=" + describeScopeDebug(getFieldValue(lambdaExpression, "enclosingScope"))
                    + ", bodyClass=" + (body == null ? "null" : body.getClass().getName())
                    + ", bodySource=" + describeSourceRange(body)
                    + ", bodyState=" + describeAstNodeState(body, 2)
                    + ", arguments=" + describeLambdaArguments(arguments)
                    + ", outerLocals=" + describeOuterLocalVariables(outerLocalVariables)
                    + ", outerSlotSize=" + getFieldValue(lambdaExpression, "outerLocalVariablesSlotSize")
                    + ", nestedLambdas=" + describeNestedLambdaStates(body)
                    + ", shouldCaptureInstance=" + getFieldValue(lambdaExpression, "shouldCaptureInstance")
                    + ", error=" + throwable.getClass().getName() + ": " + throwable.getMessage()
                    + ", stack=" + describeThrowableStack(throwable, 12));
        } catch (Exception e) {
            Util.log("[ZirconCore] lambdaGenerateCode failure logging error: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public static boolean shouldSuppressExMethodStaticAccessWarning(Object methodBinding) {
        if (methodBinding == null) {
            return false;
        }
        try {
            return isExMethodBinding(methodBinding, new IdentityHashMap<>());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void refreshInvocationArguments(Object scope, Object invocationSite, Object binding) throws Exception {
        if (scope == null || invocationSite == null || binding == null) {
            return;
        }
        String selectorName = getSelectorName(binding);
        Object rawArguments = getFieldValue(invocationSite, "arguments");
        Object rawParameters = getFieldValue(binding, "parameters");
        if (!(rawArguments instanceof Object[]) || !(rawParameters instanceof Object[])) {
            return;
        }
        Object[] arguments = (Object[]) rawArguments;
        Object[] parameters = (Object[]) rawParameters;
        if (arguments.length == 0 || parameters.length == 0) {
            return;
        }
        if (shouldDebugFacadeSelector(selectorName)) {
            Object originalBinding = invokeOptionalMethod(binding, "original");
            Util.log("[ZirconCore] postSelectionRefresh selector=" + selectorName
                    + ", binding=" + describeMethodBindingDetailed(binding)
                    + ", original=" + describeMethodBindingDetailed(originalBinding)
                    + ", static=" + invokeOptionalMethod(binding, "isStatic")
                    + ", originalStatic=" + invokeOptionalMethod(originalBinding, "isStatic")
                    + ", argCount=" + arguments.length
                    + ", paramCount=" + parameters.length
                    + ", descriptors(before)=" + describeArgumentDescriptors(arguments));
        }

        Object[] argumentTypes = computeEffectiveInvocationArgumentTypes(
                parameters,
                getFieldValue(invocationSite, "argumentTypes") instanceof Object[]
                        ? (Object[]) getFieldValue(invocationSite, "argumentTypes")
                        : null,
                arguments
        );
        boolean replacedArguments = false;
        boolean refreshedTypes = false;
        for (int index = 0; index < arguments.length && index < parameters.length; index++) {
            Object argument = arguments[index];
            Object expectedType = parameters[index];
            if (argument == null || expectedType == null) {
                continue;
            }
            if (!shouldRefreshFunctionalArgument(scope, expectedType, invocationSite, binding, index)) {
                continue;
            }
            expectedType = specializeFunctionalExpectedTypeForReceiverWrapper(
                    scope,
                    binding,
                    invocationSite,
                    expectedType,
                    parameters
            );
            Object specializedExpectedType = specializeFunctionalExpectedTypeFromArgumentTypes(
                    scope,
                    expectedType,
                    parameters,
                    argumentTypes,
                    arguments,
                    index,
                    false
            );
            if (specializedExpectedType != null) {
                expectedType = specializedExpectedType;
            }
            logFunctionalRefreshIfExpectedTypeStillGeneric(
                    selectorName,
                    invocationSite,
                    index,
                    expectedType,
                    parameters,
                    argumentTypes
            );
            if (isAnonymousOrLocalFunctionalContext(argument)) {
                continue;
            }
            if (hasReferenceExpressionFunctionalBody(argument)) {
                continue;
            }
            if (shouldAvoidReresolvingFunctionalArgument(argument)) {
                stabilizeFunctionalArgumentWithoutReresolve(argument, expectedType, scope);
                rememberSuccessfulFunctionalArgumentRange(scope, expectedType, argument, invocationSite, binding);
                Object resolvedType = getFieldValue(argument, "resolvedType");
                if (resolvedType != null && index < argumentTypes.length) {
                    argumentTypes[index] = resolvedType;
                    refreshedTypes = true;
                }
                continue;
            }
            if (shouldDeferComplexPostRewriteFunctionalRefresh(argument, expectedType)) {
                stabilizeFunctionalArgumentWithoutReresolve(argument, expectedType, scope);
                rememberSuccessfulFunctionalArgumentRange(scope, expectedType, argument, invocationSite, binding);
                Object resolvedType = getFieldValue(argument, "resolvedType");
                if (resolvedType != null && index < argumentTypes.length) {
                    argumentTypes[index] = resolvedType;
                    refreshedTypes = true;
                }
                continue;
            }
            if (shouldPreferStabilizeOnlyFunctionalRefresh(scope, argument, expectedType)) {
                stabilizeFunctionalArgumentWithoutReresolve(argument, expectedType, scope);
                rememberSuccessfulFunctionalArgumentRange(scope, expectedType, argument, invocationSite, binding);
                Object resolvedType = getFieldValue(argument, "resolvedType");
                if (resolvedType != null && index < argumentTypes.length) {
                    argumentTypes[index] = resolvedType;
                    refreshedTypes = true;
                }
                continue;
            }
            Object reResolved;
            try {
                reResolved = invokeMethod(argument, "resolveExpressionExpecting", expectedType, scope);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (shouldIgnoreFunctionalRefreshFailure(e.getCause())) {
                    stabilizeFunctionalArgumentState(argument, expectedType, scope);
                    continue;
                }
                throw e;
            } catch (NullPointerException e) {
                if (shouldIgnoreFunctionalRefreshFailure(e)) {
                    stabilizeFunctionalArgumentState(argument, expectedType, scope);
                    continue;
                }
                throw e;
            }
            if (reResolved == null) {
                continue;
            }
            logFunctionalRefreshNestedLambdaState(selectorName, "preRewrite", index, argument, reResolved, expectedType);
            ensureLambdaBindingResolved(reResolved, expectedType, scope);
            stabilizeFunctionalArgumentState(reResolved, expectedType, scope);
            rebindProblemFieldLocalReferences(reResolved, scope, scope, new IdentityHashMap<>(), 0);
            restoreOriginalLocalReferenceBindings(reResolved, argument, 0);
            rememberSuccessfulFunctionalArgumentRange(scope, expectedType, reResolved, invocationSite, binding);
            boolean promotedProblematicLambda = false;
            boolean retainProblematicResolvedLambda = false;
            if (containsProblemAstState(scope, reResolved, new IdentityHashMap<>(), 0)) {
                retainProblematicResolvedLambda = shouldKeepProblematicResolvedFunctionalArgument(scope, reResolved);
            }
            if (!retainProblematicResolvedLambda
                    && containsProblemAstState(scope, reResolved, new IdentityHashMap<>(), 0)
                    && (!repairFunctionalArgumentProblemState(reResolved, expectedType, scope)
                    || containsProblemAstState(scope, reResolved, new IdentityHashMap<>(), 0))) {
                retainProblematicResolvedLambda = shouldKeepProblematicResolvedFunctionalArgument(scope, reResolved);
                if (!retainProblematicResolvedLambda) {
                    promotedProblematicLambda = tryPromoteProblematicResolvedLambda(scope, argument, reResolved, expectedType);
                }
                if (!promotedProblematicLambda && !retainProblematicResolvedLambda) {
                    if (!containsProblemAstState(scope, reResolved, new IdentityHashMap<>(), 0)) {
                        retainProblematicResolvedLambda = true;
                    } else {
                        logFunctionalRefreshProblemState(scope, selectorName, "preRewrite", index, argument, reResolved, expectedType);
                        continue;
                    }
                }
            }
            if (promotedProblematicLambda) {
                reResolved = argument;
            }
            logNullLambdaBindingIfNeeded(selectorName, "preRewrite", index, argument, reResolved, expectedType);
            if (!retainProblematicResolvedLambda && shouldRetainOriginalFunctionalArgument(argument, reResolved)) {
                mergeResolvedFunctionalArgumentState(argument, reResolved);
                reResolved = argument;
            }
            stabilizeFunctionalArgumentState(reResolved, expectedType, scope);
            if (reResolved != argument) {
                arguments[index] = reResolved;
                replacedArguments = true;
            }
            Object resolvedType = getFieldValue(arguments[index], "resolvedType");
            if (resolvedType != null && index < argumentTypes.length) {
                argumentTypes[index] = resolvedType;
                refreshedTypes = true;
            }
        }
        if (replacedArguments) {
            setFieldValue(invocationSite, "arguments", arguments);
        }
        if (refreshedTypes) {
            setFieldValue(invocationSite, "argumentTypes", argumentTypes);
        }
        rebindProblemFieldLocalReferences(invocationSite, scope, scope, new IdentityHashMap<>(), 0);
        if (shouldClearInvocationArgumentErrors(scope, arguments)) {
            setFieldValue(invocationSite, "argumentsHaveErrors", false);
        }
        if (replacedArguments || refreshedTypes) {
            primeArgumentDescriptorState(arguments);
        }
        if ((replacedArguments || refreshedTypes)
                && (shouldTraceSelector(selectorName) || shouldDebugFacadeSelector(selectorName))) {
            Util.log("[ZirconCore] refreshResult selector=" + selectorName
                    + ", replaced=" + replacedArguments
                    + ", refreshedTypes=" + refreshedTypes
                    + ", argumentTypes=" + describeTypeArrayDetailed(argumentTypes)
                    + ", descriptors=" + describeArgumentDescriptors(arguments));
        }
    }

    private static boolean shouldIgnoreFunctionalRefreshFailure(Throwable error) {
        if (error == null) {
            return false;
        }
        if (!(error instanceof NullPointerException) || error.getMessage() == null) {
            return false;
        }
        String message = error.getMessage();
        return message.contains("this.constant")
                || message.contains("this.enclosingScope")
                || message.contains("currentScope")
                || message.contains("originalValueIfTrueType")
                || message.contains("originalValueIfFalseType");
    }

    private static boolean shouldRetainOriginalFunctionalArgument(Object originalArgument, Object resolvedArgument) {
        if (originalArgument == resolvedArgument
                || !isLambdaExpression(originalArgument)
                || !isLambdaExpression(resolvedArgument)) {
            return false;
        }
        try {
            if (isAnonymousOrLocalFunctionalContext(originalArgument)
                    || isAnonymousOrLocalFunctionalContext(resolvedArgument)) {
                return false;
            }
            return !containsOutOfBandReceiverInvocationState(originalArgument, new IdentityHashMap<>(), 0)
                    && !containsOutOfBandReceiverInvocationState(resolvedArgument, new IdentityHashMap<>(), 0);
        } catch (Exception ignored) {
            return true;
        }
    }

    private static boolean isAnonymousOrLocalFunctionalContext(Object lambdaExpression) throws Exception {
        if (!isLambdaExpression(lambdaExpression)) {
            return false;
        }
        Object lambdaScope = getFieldValue(lambdaExpression, "scope");
        if (lambdaScope != null && isAnonymousOrLocalContext(lambdaScope, null)) {
            return true;
        }
        Object enclosingScope = getFieldValue(lambdaExpression, "enclosingScope");
        return enclosingScope != null && isAnonymousOrLocalContext(enclosingScope, null);
    }

    private static boolean shouldPreferStabilizeOnlyFunctionalRefresh(
            Object scope,
            Object argument,
            Object expectedType
    ) throws Exception {
        if (scope == null || expectedType == null || !isLambdaExpression(argument)) {
            return false;
        }
        if (isAnonymousOrLocalFunctionalContext(argument)) {
            return true;
        }
        Object descriptor = invokeMethod(expectedType, "getSingleAbstractMethod", scope, true);
        if (descriptor == null || isProblem(descriptor)) {
            return false;
        }
        Object[] descriptorParameters = getFieldValue(descriptor, "parameters") instanceof Object[]
                ? (Object[]) getFieldValue(descriptor, "parameters")
                : null;
        return descriptorParameters != null
                && descriptorParameters.length == 0
                && lambdaReturnShapeMatchesExpectedType(argument, descriptor)
                && !containsProblemAstState(scope, argument, new IdentityHashMap<>(), 0);
    }

    private static boolean shouldAvoidReresolvingFunctionalArgument(Object argument) throws Exception {
        if (!isLambdaExpression(argument)) {
            return false;
        }
        Object body = getFieldValue(argument, "body");
        return isBlockLambdaBody(body)
                && containsLocalArrayAllocationDeclaration(body, new IdentityHashMap<>(), 0);
    }

    private static boolean containsLocalArrayAllocationDeclaration(
            Object node,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 16 || visited.put(node, Boolean.TRUE) != null || !isAstNode(node)) {
            return false;
        }
        if ("LocalDeclaration".equals(node.getClass().getSimpleName())) {
            Object initialization = getFieldValue(node, "initialization");
            if (initialization != null) {
                String initName = initialization.getClass().getSimpleName();
                if ("ArrayAllocationExpression".equals(initName) || "ArrayInitializer".equals(initName)) {
                    return true;
                }
            }
        }
        for (Object child : getAstChildren(node)) {
            if (containsLocalArrayAllocationDeclaration(child, visited, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean lambdaReturnShapeMatchesExpectedType(Object lambdaExpression, Object descriptor) throws Exception {
        if (!isLambdaExpression(lambdaExpression) || descriptor == null || isProblem(descriptor)) {
            return false;
        }
        Object descriptorReturnType = getFieldValue(descriptor, "returnType");
        boolean expectsVoid = descriptorReturnType != null
                && "VoidTypeBinding".equals(descriptorReturnType.getClass().getSimpleName());
        Object returnsValue = findField(lambdaExpression.getClass(), "returnsValue") != null
                ? getFieldValue(lambdaExpression, "returnsValue")
                : null;
        Object valueCompatible = findField(lambdaExpression.getClass(), "valueCompatible") != null
                ? getFieldValue(lambdaExpression, "valueCompatible")
                : null;
        Object voidCompatible = findField(lambdaExpression.getClass(), "voidCompatible") != null
                ? getFieldValue(lambdaExpression, "voidCompatible")
                : null;
        Object body = getFieldValue(lambdaExpression, "body");
        boolean expressionBody = body != null && !body.getClass().getName().endsWith("Block");
        if (!expectsVoid) {
            if (returnsValue instanceof Boolean) {
                return (Boolean) returnsValue;
            }
            if (valueCompatible instanceof Boolean) {
                return (Boolean) valueCompatible;
            }
            return expressionBody;
        }
        if (returnsValue instanceof Boolean && (Boolean) returnsValue) {
            return false;
        }
        if (expressionBody) {
            return false;
        }
        if (voidCompatible instanceof Boolean) {
            return (Boolean) voidCompatible;
        }
        return true;
    }

    private static void stabilizeFunctionalArgumentWithoutReresolve(Object argument, Object expectedType, Object scope) {
        if (argument == null || expectedType == null || scope == null) {
            return;
        }
        try {
            stabilizeFunctionalArgumentState(argument, expectedType, scope);
            rebindProblemFieldLocalReferences(argument, scope, scope, new IdentityHashMap<>(), 0);
            if (!isLambdaExpression(argument)) {
                return;
            }
            Object descriptor = getFieldValue(argument, "descriptor");
            if (descriptor == null || isProblem(descriptor)) {
                Object expectedDescriptor = invokeMethod(expectedType, "getSingleAbstractMethod", scope, true);
                if (expectedDescriptor != null && !isProblem(expectedDescriptor)) {
                    primeMethodBindingState(expectedDescriptor);
                    setFieldValue(argument, "descriptor", expectedDescriptor);
                    Object binding = getFieldValue(argument, "binding");
                    if (binding == null || isProblem(binding)) {
                        setFieldValue(argument, "binding", expectedDescriptor);
                    }
                }
            }
            Object resolvedType = getFieldValue(argument, "resolvedType");
            if (resolvedType == null
                    || isProblem(resolvedType)
                    || (!shouldUseConcreteArgumentTypeForSubstitution(resolvedType)
                    && shouldUseConcreteArgumentTypeForSubstitution(expectedType))) {
                setFieldValue(argument, "resolvedType", expectedType);
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isLambdaExpression(Object expression) {
        return expression != null
                && expression.getClass().getName().endsWith("LambdaExpression");
    }

    private static boolean hasReferenceExpressionFunctionalBody(Object argument) throws Exception {
        if (!isLambdaExpression(argument)) {
            return false;
        }
        Object body = getFieldValue(argument, "body");
        return containsReferenceExpressionAst(body != null ? body : argument, new IdentityHashMap<>(), 0);
    }

    private static boolean containsReferenceExpressionAst(
            Object node,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 12 || visited.put(node, Boolean.TRUE) != null) {
            return false;
        }
        if (node.getClass().getSimpleName().endsWith("ReferenceExpression")) {
            return true;
        }
        for (Object child : getAstChildren(node)) {
            if (containsReferenceExpressionAst(child, visited, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNestedFunctionalRepairBoundary(Object node, int depth) {
        if (node == null || depth <= 0 || !isAstNode(node)) {
            return false;
        }
        if (isLambdaExpression(node)) {
            return true;
        }
        String simpleName = node.getClass().getSimpleName();
        return simpleName.endsWith("TypeDeclaration");
    }

    private static boolean isNestedTypeDeclarationBoundary(Object node, int depth) {
        return node != null
                && depth > 0
                && isAstNode(node)
                && node.getClass().getSimpleName().endsWith("TypeDeclaration");
    }

    private static void ensureLambdaBindingResolved(Object expression, Object expectedType, Object scope) {
        if (!isLambdaExpression(expression) || scope == null) {
            return;
        }
        try {
            if (shouldAvoidReresolvingFunctionalArgument(expression)) {
                return;
            }
            if (expectedType != null) {
                setFieldValue(expression, "expectedType", expectedType);
            }
            if (getFieldValue(expression, "binding") == null) {
                invokeMethod(expression, "resolveType", scope);
            }
        } catch (Exception ignored) {
        }
    }

    private static void refreshLambdaMethodBindingIfPossible(Object expression) {
        if (!isLambdaExpression(expression)) {
            return;
        }
        try {
            if (shouldSkipLambdaMethodBindingRefresh(expression)) {
                return;
            }
            Object binding = getFieldValue(expression, "binding");
            Object descriptor = getFieldValue(expression, "descriptor");
            if ((binding != null && !isProblem(binding)) || (descriptor != null && !isProblem(descriptor))) {
                refreshLambdaMethodBinding(expression);
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean shouldSkipLambdaMethodBindingRefresh(Object expression) throws Exception {
        if (!isLambdaExpression(expression)) {
            return false;
        }
        Object binding = getFieldValue(expression, "binding");
        Object descriptor = getFieldValue(expression, "descriptor");
        if (binding == null || isProblem(binding) || descriptor == null || isProblem(descriptor)) {
            return false;
        }
        Object[] descriptorParameters = getFieldValue(descriptor, "parameters") instanceof Object[]
                ? (Object[]) getFieldValue(descriptor, "parameters")
                : null;
        if (descriptorParameters == null || descriptorParameters.length != 0) {
            return false;
        }
        Object outerLocalVariables = getFieldValue(expression, "outerLocalVariables");
        return !(outerLocalVariables instanceof Object[]) || ((Object[]) outerLocalVariables).length == 0;
    }

    private static void stabilizeFunctionalArgumentState(Object argument, Object expectedType, Object scope) {
        if (argument == null || scope == null) {
            return;
        }
        try {
            if (shouldAvoidReresolvingFunctionalArgument(argument)) {
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            stabilizeNestedLambdaScopes(argument, scope, new IdentityHashMap<>(), 0);
            if (isLambdaExpression(argument)) {
                boolean blockLambda = isBlockLambdaBody(getFieldValue(argument, "body"));
                boolean riskyBlockLambda = blockLambda && shouldAvoidReresolvingFunctionalArgument(argument);
                if (expectedType != null) {
                    setFieldValue(argument, "expectedType", expectedType);
                }
                if (!riskyBlockLambda) {
                    Object lambdaScope = getFieldValue(argument, "scope");
                    Object enclosingScope = getFieldValue(argument, "enclosingScope");
                    Object effectiveScope = lambdaScope != null ? lambdaScope : (enclosingScope != null ? enclosingScope : scope);
                    ensureLambdaAnalyseScope(argument, effectiveScope);
                    ensureLambdaBindingResolved(argument, expectedType, effectiveScope);
                    refreshLambdaMethodBindingIfPossible(argument);
                }
            }
            Object root = isLambdaExpression(argument) ? getFieldValue(argument, "body") : argument;
            normalizeExpressionConstantsForCodegen(root != null ? root : argument, new IdentityHashMap<>(), 0);
            ensureNestedLambdaCaptureStates(root != null ? root : argument, scope, new IdentityHashMap<>(), 0);
        } catch (Exception ignored) {
        }
    }

    private static void stabilizeNestedLambdaScopes(
            Object node,
            Object fallbackScope,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null
                || fallbackScope == null
                || depth > 16
                || visited.put(node, Boolean.TRUE) != null
                || !isAstNode(node)) {
            return;
        }
        if (isNestedTypeDeclarationBoundary(node, depth)) {
            return;
        }
        Object nextScope = fallbackScope;
        if (isLambdaExpression(node)) {
            Object lambdaScope = getFieldValue(node, "scope");
            Object enclosingScope = getFieldValue(node, "enclosingScope");
            Object effectiveScope = lambdaScope != null ? lambdaScope : (enclosingScope != null ? enclosingScope : fallbackScope);
            ensureLambdaAnalyseScope(node, effectiveScope);
            ensureLambdaBindingResolved(node, getFieldValue(node, "expectedType"), effectiveScope);
            refreshLambdaMethodBindingIfPossible(node);
            rebindLambdaArgumentReferences(node);
            if (lambdaScope != null) {
                nextScope = lambdaScope;
            } else if (effectiveScope != null) {
                nextScope = effectiveScope;
            }
        }
        for (Object child : getAstChildren(node)) {
            stabilizeNestedLambdaScopes(child, nextScope, visited, depth + 1);
        }
    }

    private static boolean isBlockLambdaBody(Object body) {
        return body != null && "Block".equals(body.getClass().getSimpleName());
    }

    private static void logNullLambdaBindingIfNeeded(
            String selectorName,
            String phase,
            int index,
            Object originalArgument,
            Object resolvedArgument,
            Object expectedType
    ) {
        if (!Util.isDebugEnabled()
                || !shouldDebugFacadeSelector(selectorName)
                || !isLambdaExpression(resolvedArgument)) {
            return;
        }
        try {
            Object binding = getFieldValue(resolvedArgument, "binding");
            if (binding != null) {
                return;
            }
            String key = phase + "|" + index + "|" + describeSourceRange(resolvedArgument) + "|" + describeSourceRange(getFieldValue(resolvedArgument, "body"));
            if (!FUNCTIONAL_REFRESH_BINDING_KEYS.add(key)) {
                return;
            }
            Util.log("[ZirconCore] nullLambdaBinding selector=" + selectorName
                    + ", phase=" + phase
                    + ", index=" + index
                    + ", sameNode=" + (originalArgument == resolvedArgument)
                    + ", source=" + describeSourceRange(resolvedArgument)
                    + ", bodySource=" + describeSourceRange(getFieldValue(resolvedArgument, "body"))
                    + ", expectedType=" + describeTypeDebug(expectedType)
                    + ", resolvedType=" + describeTypeDebug(getFieldValue(resolvedArgument, "resolvedType"))
                    + ", descriptor=" + describeMethodBindingDetailed(getFieldValue(resolvedArgument, "descriptor"))
                    + ", binding=" + describeMethodBindingDetailed(binding));
        } catch (Exception ignored) {
        }
    }

    private static void rememberSuccessfulFunctionalArgumentRange(
            Object scope,
            Object expectedType,
            Object argument
    ) {
        rememberSuccessfulFunctionalArgumentRange(scope, expectedType, argument, null, null);
    }

    private static void rememberSuccessfulFunctionalArgumentRange(
            Object scope,
            Object expectedType,
            Object argument,
            Object invocationSite,
            Object binding
    ) {
        if (scope == null || expectedType == null || argument == null || !isLambdaExpression(argument)) {
            return;
        }
        try {
            Object provedBinding = findProvableExtensionBindingInNode(scope, getFieldValue(argument, "body"), new IdentityHashMap<>(), 0);
            if ((provedBinding == null || isProblem(provedBinding)) && binding != null && !isProblem(binding)) {
                provedBinding = binding;
            }
            if (provedBinding == null || isProblem(provedBinding)) {
                return;
            }
            Object compilationResult = resolveCompilationResultFromScope(scope);
            String fileName = describeCompilationUnitFileName(compilationResult);
            if (fileName == null) {
                return;
            }
            int start = readIntField(argument, "sourceStart");
            int end = readIntField(argument, "sourceEnd");
            if (start < 0 || end < start) {
                return;
            }
            String rangeKey = fileName + "|" + start + "|" + end;
            SUCCESSFUL_EXTENSION_UNDEFINED_RANGES.add(rangeKey);
            if (isReceiverFunctionalWrapperBinding(scope, provedBinding)) {
                SUCCESSFUL_FUNCTIONAL_WRAPPER_RANGES.add(rangeKey);
            }
            String selectorName = getSelectorName(provedBinding);
            if (!selectorName.isEmpty()) {
                putBoundedConcurrentMap(SUCCESSFUL_EXTENSION_UNDEFINED_SELECTORS, rangeKey, selectorName);
            }
            Object descriptor = invokeMethod(expectedType, "getSingleAbstractMethod", scope, true);
            if (descriptor == null || isProblem(descriptor)) {
                return;
            }
            Object descriptorReturnType = getFieldValue(descriptor, "returnType");
            rememberSemanticTypeVariableTargetRange(rangeKey, descriptorReturnType);
            if (descriptorReturnType != null
                    && !isProblem(descriptorReturnType)
                    && !isTypeVariable(descriptorReturnType)) {
                putBoundedConcurrentMap(
                        SUCCESSFUL_EXTENSION_FUNCTIONAL_RETURN_TYPES,
                        rangeKey,
                        Collections.unmodifiableList(Collections.singletonList(descriptorReturnType))
                );
            }
            Object[] descriptorParameters = getFieldValue(descriptor, "parameters") instanceof Object[]
                    ? (Object[]) getFieldValue(descriptor, "parameters")
                    : null;
            if (descriptorParameters != null && descriptorParameters.length > 0) {
                List<Object> normalizedParameters = new ArrayList<>(descriptorParameters.length);
                for (Object descriptorParameter : descriptorParameters) {
                    Object normalized = normalizeMethodLookupType(descriptorParameter);
                    if (normalized != null && !isProblem(normalized)) {
                        normalizedParameters.add(normalized);
                    }
                }
                if (!normalizedParameters.isEmpty()) {
                    putBoundedConcurrentMap(
                            SUCCESSFUL_EXTENSION_FUNCTIONAL_PARAMETER_TYPES,
                            rangeKey,
                            Collections.unmodifiableList(normalizedParameters)
                    );
                }
            }
            pruneResolvedExtensionProblems(compilationResult);
        } catch (Exception ignored) {
        }
    }

    private static void rememberSemanticTypeVariableTargetRange(
            String fileName,
            int start,
            int end,
            Object targetBinding
    ) throws Exception {
        if (fileName == null || targetBinding == null || isProblem(targetBinding) || start < 0 || end < start) {
            return;
        }
        rememberSemanticTypeVariableTargetRange(fileName + "|" + start + "|" + end, targetBinding);
    }

    private static void rememberSemanticTypeVariableTargetRange(
            String rangeKey,
            Object targetBinding
    ) throws Exception {
        if (rangeKey == null || rangeKey.isEmpty() || targetBinding == null || isProblem(targetBinding)) {
            return;
        }
        if (isActualTypeVariableBinding(targetBinding)) {
            SEMANTIC_TYPE_VARIABLE_TARGET_RANGES.add(rangeKey);
        }
        if (containsTypeVariableBinding(targetBinding, new IdentityHashMap<>(), 0)) {
            SEMANTIC_NESTED_TYPE_VARIABLE_TARGET_RANGES.add(rangeKey);
        }
    }

    private static void rememberSemanticTypeVariableTargetRange(Object scope, Object expression) {
        if (scope == null || expression == null) {
            return;
        }
        try {
            Object expectedType = findField(expression.getClass(), "expectedType") != null
                    ? getFieldValue(expression, "expectedType")
                    : null;
            Object compilationResult = resolveCompilationResultFromScope(scope);
            rememberSemanticTypeVariableTargetRange(
                    describeCompilationUnitFileName(compilationResult),
                    readIntField(expression, "sourceStart"),
                    readIntField(expression, "sourceEnd"),
                    expectedType
            );
        } catch (Exception ignored) {
        }
    }


    /**
     * Feeds Zircon facade bindings into CompletionEngine's own proposal pipeline.
     *
     * <p>The advice passes the arguments of CompletionEngine#findMethods, immediately
     * before JDT enumerates the receiver hierarchy. Calling
     * findLocalMethods keeps JDT responsible for name matching, visibility, proposal
     * shape, relevance, signatures and generic presentation.</p>
     */
    public static void contributeExtensionMethodCompletions(Object completionEngine, Object[] arguments) {
        try {
            if (Util.isTraceEnabled()) {
                Util.log("[ZirconCore] completion hook args="
                        + (arguments == null ? -1 : arguments.length));
            }
            if (completionEngine == null || arguments == null || arguments.length != 20) {
                return;
            }
            Object receiverType = arguments[3];
            Object scope = arguments[4];
            Object methodsFound = arguments[5];
            Object invocationSite = arguments[8];
            if (receiverType == null || scope == null || methodsFound == null || isProblem(receiverType)) {
                return;
            }
            if (!isScopeInConfiguredZirconProject(scope)) {
                return;
            }
            Object receiverReferenceBinding = asReferenceBinding(receiverType);
            Object compilationUnitScope = getCompilationUnitScope(scope);
            if (receiverReferenceBinding == null || compilationUnitScope == null) {
                return;
            }

            char[] token = arguments[0] instanceof char[] ? (char[]) arguments[0] : new char[0];
            List<CandidateBinding> candidates = collectCompletionExtensionCandidates(
                    completionEngine,
                    compilationUnitScope,
                    token,
                    receiverType,
                    scope,
                    invocationSite
            );
            if (Util.isTraceEnabled()) {
                Util.log("[ZirconCore] completion candidates receiver=" + getTypeName(receiverType)
                        + ", receiverDetail=" + describeTypeDebug(receiverType)
                        + ", token=" + new String(token)
                        + ", direct=" + isDirectCompletionInvocation(invocationSite)
                        + ", invocationSite=" + (invocationSite == null
                                ? "null"
                                : invocationSite.getClass().getSimpleName())
                        + ", invocationReceiver=" + describeBinding(
                                invocationSite == null ? null : getFieldValue(invocationSite, "receiver")
                        )
                        + ", count=" + candidates.size());
            }
            if (candidates.isEmpty()) {
                return;
            }

            Class<?> methodBindingClass = loadClass(METHOD_BINDING_CLASS, receiverType);
            Object facadeBindings = java.lang.reflect.Array.newInstance(methodBindingClass, candidates.size());
            for (int index = 0; index < candidates.size(); index++) {
                java.lang.reflect.Array.set(facadeBindings, index, candidates.get(index).binding);
            }

            // These three booleans mirror findFieldsAndMethods' normal call to
            // findMethods for an explicit receiver: onlyStatic=false,
            // exactMatch=false, canBePrefixed=false.
            Map<String, String> requiredImports = buildCompletionRequiredImports(candidates);
            if (!requiredImports.isEmpty()) {
                COMPLETION_REQUIRED_IMPORTS.set(requiredImports);
            }
            try {
                invokeMethod(
                        completionEngine,
                        "findLocalMethods",
                        token,
                        arguments[1],
                        arguments[2],
                        facadeBindings,
                        scope,
                        methodsFound,
                        arguments[6],
                        arguments[7],
                        receiverReferenceBinding,
                        invocationSite,
                        arguments[9],
                        arguments[10],
                        arguments[11],
                        arguments[12],
                        arguments[13],
                        arguments[14],
                        arguments[15],
                        arguments[16],
                        arguments[17],
                        arguments[18],
                        arguments[19]
                );
            } finally {
                COMPLETION_REQUIRED_IMPORTS.remove();
            }
            if (Util.isTraceEnabled()) {
                Util.log("[ZirconCore] completion receiver=" + getTypeName(receiverType)
                        + ", token=" + new String(token)
                        + ", extensionCandidates=" + candidates.size());
            }
        } catch (Throwable throwable) {
            Util.log("[ZirconCore] extension completion failed: "
                    + throwable.getClass().getName() + ": " + throwable.getMessage());
            if (Util.isTraceEnabled()) {
                Util.log(Util.stackTrace(throwable));
            }
        }
    }

    private static void pruneResolvedExtensionProblems(Object compilationResult) throws Exception {
        if (compilationResult == null || findField(compilationResult.getClass(), "problems") == null) {
            return;
        }
        Object problems = getFieldValue(compilationResult, "problems");
        if (problems == null || !problems.getClass().isArray()) {
            return;
        }
        Object filtered = filterResolvedExtensionProblems(compilationResult, problems);
        if (filtered == problems) {
            return;
        }
        int originalLength = Array.getLength(problems);
        int retainedCount = Array.getLength(filtered);
        for (int index = 0; index < retainedCount; index++) {
            Array.set(problems, index, Array.get(filtered, index));
        }
        for (int index = retainedCount; index < originalLength; index++) {
            Array.set(problems, index, null);
        }
        if (findField(compilationResult.getClass(), "problemCount") != null) {
            setFieldValue(compilationResult, "problemCount", retainedCount);
        }
    }

    private static Object findProvableExtensionBindingInNode(
            Object scope,
            Object node,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (scope == null || node == null || depth > 16) {
            return null;
        }
        if (node instanceof Object[]) {
            for (Object element : (Object[]) node) {
                Object proved = findProvableExtensionBindingInNode(scope, element, visited, depth + 1);
                if (proved != null && !isProblem(proved)) {
                    return proved;
                }
            }
            return null;
        }
        if (!isAstNode(node) || visited.put(node, Boolean.TRUE) != null) {
            return null;
        }
        if (isInvocationAstNode(node)) {
            Object binding = probeCompatibleExtensionBinding(scope, node);
            if (binding != null && !isProblem(binding)) {
                return binding;
            }
        }
        for (Object child : getAstChildren(node)) {
            Object proved = findProvableExtensionBindingInNode(scope, child, visited, depth + 1);
            if (proved != null && !isProblem(proved)) {
                return proved;
            }
        }
        return null;
    }

    private static Object probeCompatibleExtensionBinding(Object scope, Object invocationSite) throws Exception {
        if (scope == null || invocationSite == null || !isInvocationAstNode(invocationSite)) {
            return null;
        }
        String selectorName = extractInvocationSelectorName(invocationSite);
        if (selectorName.isEmpty()) {
            return null;
        }
        Object receiverType = resolveInvocationReceiverType(scope, invocationSite);
        if (receiverType == null || isProblem(receiverType)) {
            return null;
        }
        Object compilationUnitScope = getCompilationUnitScope(scope);
        if (compilationUnitScope == null) {
            return null;
        }
        Object[] visibleArgumentTypes = resolveInvocationArgumentTypes(scope, invocationSite);
        List<CandidateBinding> candidates = collectExtensionCandidates(
                compilationUnitScope,
                selectorName,
                receiverType,
                scope,
                invocationSite
        );
        if (candidates.isEmpty()) {
            return null;
        }
        InvocationRewriteState state = captureInvocationRewriteState(invocationSite);
        try {
            for (CandidateBinding candidate : candidates) {
                CandidateBinding compatible = computeCompatibleCandidate(
                        scope,
                        candidate,
                        receiverType,
                        visibleArgumentTypes,
                        invocationSite,
                        true
                );
                restoreInvocationRewriteState(invocationSite, state);
                if (compatible != null && compatible.binding != null && !isProblem(compatible.binding)) {
                    return compatible.binding;
                }
            }
            return null;
        } finally {
            restoreInvocationRewriteState(invocationSite, state);
        }
    }

    private static void logFunctionalRefreshProblemState(
            Object scope,
            String selectorName,
            String phase,
            int index,
            Object originalArgument,
            Object resolvedArgument,
            Object expectedType
    ) {
        if (!Util.isDebugEnabled() || !isLambdaExpression(resolvedArgument)) {
            return;
        }
        try {
            String key = "problem|" + selectorName
                    + "|" + phase
                    + "|" + index
                    + "|" + describeSourceRange(resolvedArgument)
                    + "|" + describeSourceRange(getFieldValue(resolvedArgument, "body"));
            if (!FUNCTIONAL_REFRESH_BINDING_KEYS.add(key)) {
                return;
            }
            Util.log("[ZirconCore] functionalRefresh problemState"
                    + ": selector=" + selectorName
                    + ", phase=" + phase
                    + ", index=" + index
                    + ", sameNode=" + (originalArgument == resolvedArgument)
                    + ", source=" + describeSourceRange(resolvedArgument)
                    + ", bodySource=" + describeSourceRange(getFieldValue(resolvedArgument, "body"))
                    + ", expectedType=" + describeTypeDebug(expectedType)
                    + ", resolvedType=" + describeTypeDebug(getFieldValue(resolvedArgument, "resolvedType"))
                    + ", descriptor=" + describeMethodBindingDetailed(getFieldValue(resolvedArgument, "descriptor"))
                    + ", binding=" + describeMethodBindingDetailed(getFieldValue(resolvedArgument, "binding"))
                    + ", arguments=" + describeLambdaArguments(getFieldValue(resolvedArgument, "arguments") instanceof Object[]
                    ? (Object[]) getFieldValue(resolvedArgument, "arguments")
                    : new Object[0])
                    + ", nestedLambdas=" + describeNestedLambdaStates(getFieldValue(resolvedArgument, "body"))
                    + ", firstProblemNode=" + describeFirstProblemAstNode(scope, resolvedArgument));
        } catch (Exception ignored) {
        }
    }

    private static String describeFirstProblemAstNode(Object scope, Object node) throws Exception {
        String description = describeFirstProblemAstNode(scope, node, new IdentityHashMap<>(), 0);
        return description != null ? description : "none";
    }

    private static String describeFirstProblemAstNode(
            Object scope,
            Object node,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 16) {
            return null;
        }
        if (node instanceof Object[]) {
            for (Object element : (Object[]) node) {
                String description = describeFirstProblemAstNode(scope, element, visited, depth + 1);
                if (description != null) {
                    return description;
                }
            }
            return null;
        }
        if (!isAstNode(node) || visited.put(node, Boolean.TRUE) != null) {
            return null;
        }
        boolean invocationProblem = hasInvocationProblemState(node);
        boolean resolvableExtension = invocationProblem && isResolvableExtensionInvocation(scope, node);
        boolean bindingProblem = hasProblemBindingField(node, "binding")
                || hasProblemBindingField(node, "resolvedType")
                || hasProblemBindingField(node, "actualReceiverType")
                || hasProblemBindingField(node, "descriptor");
        if ((invocationProblem && !resolvableExtension) || (!invocationProblem && bindingProblem)) {
            Object binding = findField(node.getClass(), "binding") != null ? getFieldValue(node, "binding") : null;
            Object resolvedType = findField(node.getClass(), "resolvedType") != null ? getFieldValue(node, "resolvedType") : null;
            Object actualReceiverType = findField(node.getClass(), "actualReceiverType") != null ? getFieldValue(node, "actualReceiverType") : null;
            Object descriptor = findField(node.getClass(), "descriptor") != null ? getFieldValue(node, "descriptor") : null;
            Object[] arguments = findField(node.getClass(), "arguments") != null && getFieldValue(node, "arguments") instanceof Object[]
                    ? (Object[]) getFieldValue(node, "arguments")
                    : new Object[0];
            Object[] argumentTypes = findField(node.getClass(), "argumentTypes") != null && getFieldValue(node, "argumentTypes") instanceof Object[]
                    ? (Object[]) getFieldValue(node, "argumentTypes")
                    : new Object[0];
            Object[] parameters = binding != null && getFieldValue(binding, "parameters") instanceof Object[]
                    ? (Object[]) getFieldValue(binding, "parameters")
                    : new Object[0];
            return node.getClass().getSimpleName()
                    + "{source=" + describeSourceRange(node)
                    + ", invocationProblem=" + invocationProblem
                    + ", resolvableExtension=" + resolvableExtension
                    + ", bindingProblem=" + bindingProblem
                    + ", binding=" + describeMethodBindingDetailed(binding)
                    + ", resolvedType=" + describeTypeDebug(resolvedType)
                    + ", actualReceiverType=" + describeTypeDebug(actualReceiverType)
                    + ", descriptor=" + describeMethodBindingDetailed(descriptor)
                    + ", argCount=" + arguments.length
                    + ", argTypeCount=" + argumentTypes.length
                    + ", paramCount=" + parameters.length
                    + "}";
        }
        for (Object child : getAstChildren(node)) {
            String description = describeFirstProblemAstNode(scope, child, visited, depth + 1);
            if (description != null) {
                return description;
            }
        }
        return null;
    }

    private static void logFunctionalRefreshNestedLambdaState(
            String selectorName,
            String phase,
            int index,
            Object originalArgument,
            Object resolvedArgument,
            Object expectedType
    ) {
        if (!Util.isDebugEnabled()) {
            return;
        }
        try {
            if (!isLambdaExpression(resolvedArgument)
                    || !containsNestedLambdaExpression(resolvedArgument, false, new IdentityHashMap<>(), 0)) {
                return;
            }
            String key = "nested|" + selectorName
                    + "|" + phase
                    + "|" + index
                    + "|" + describeSourceRange(resolvedArgument)
                    + "|" + describeSourceRange(getFieldValue(resolvedArgument, "body"))
                    + "|" + (originalArgument == resolvedArgument);
            if (!FUNCTIONAL_REFRESH_BINDING_KEYS.add(key)) {
                return;
            }
            Util.log("[ZirconCore] functionalRefresh nestedLambda"
                    + ": selector=" + selectorName
                    + ", phase=" + phase
                    + ", index=" + index
                    + ", sameNode=" + (originalArgument == resolvedArgument)
                    + ", source=" + describeSourceRange(resolvedArgument)
                    + ", bodySource=" + describeSourceRange(getFieldValue(resolvedArgument, "body"))
                    + ", expectedType=" + describeTypeDebug(expectedType)
                    + ", resolvedType=" + describeTypeDebug(getFieldValue(resolvedArgument, "resolvedType"))
                    + ", descriptor=" + describeMethodBindingDetailed(getFieldValue(resolvedArgument, "descriptor"))
                    + ", binding=" + describeMethodBindingDetailed(getFieldValue(resolvedArgument, "binding"))
                    + ", arguments=" + describeLambdaArguments(getFieldValue(resolvedArgument, "arguments") instanceof Object[]
                    ? (Object[]) getFieldValue(resolvedArgument, "arguments")
                    : new Object[0])
                    + ", nestedLambdas=" + describeNestedLambdaStates(getFieldValue(resolvedArgument, "body")));
        } catch (Exception ignored) {
        }
    }

    private static boolean repairFunctionalArgumentProblemState(Object argument, Object expectedType, Object scope) {
        if (!isLambdaExpression(argument) || scope == null) {
            return false;
        }
        try {
            if (expectedType != null) {
                setFieldValue(argument, "expectedType", expectedType);
            }
            Object lambdaScope = getFieldValue(argument, "scope");
            Object enclosingScope = getFieldValue(argument, "enclosingScope");
            Object effectiveScope = lambdaScope != null
                    ? lambdaScope
                    : (enclosingScope != null ? enclosingScope : scope);
            refreshLambdaMethodBindingIfPossible(argument);
            rebindLambdaArgumentReferences(argument);
            Object body = getFieldValue(argument, "body");
            normalizeResolvedFunctionalAstState(body != null ? body : argument, new IdentityHashMap<>(), 0);
            if (containsOutOfBandReceiverInvocationState(body != null ? body : argument, new IdentityHashMap<>(), 0)) {
                return false;
            }
            clearProblemAstBindings(body, new IdentityHashMap<>(), 0);
            normalizeResolvedFunctionalAstState(body != null ? body : argument, new IdentityHashMap<>(), 0);
            normalizeExpressionConstantsForCodegen(body != null ? body : argument, new IdentityHashMap<>(), 0);
            if (body != null && effectiveScope != null) {
                tryResolveAstNode(body, effectiveScope);
            }
            refreshLambdaMethodBindingIfPossible(argument);
            stabilizeFunctionalArgumentState(argument, expectedType, scope);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void clearProblemAstBindings(Object node, Map<Object, Boolean> visited, int depth) throws Exception {
        if (node == null || depth > 16 || visited.put(node, Boolean.TRUE) != null || !isAstNode(node)) {
            return;
        }
        if (isNestedFunctionalRepairBoundary(node, depth)) {
            return;
        }
        clearProblemBindingFieldIfPresent(node, "binding");
        clearProblemBindingFieldIfPresent(node, "resolvedType");
        clearProblemBindingFieldIfPresent(node, "actualReceiverType");
        clearProblemBindingFieldIfPresent(node, "descriptor");
        if (hasInvocationArgumentErrors(node)) {
            setFieldValue(node, "argumentsHaveErrors", false);
        }
        for (Object child : getAstChildren(node)) {
            clearProblemAstBindings(child, visited, depth + 1);
        }
    }

    private static void clearProblemBindingFieldIfPresent(Object node, String fieldName) throws Exception {
        if (node == null || findField(node.getClass(), fieldName) == null) {
            return;
        }
        Object value = getFieldValue(node, fieldName);
        if (value != null && isProblem(value)) {
            setFieldValue(node, fieldName, null);
        }
    }

    private static void tryResolveAstNode(Object node, Object scope) {
        tryResolveAstNode(node, scope, new IdentityHashMap<>(), 0);
    }

    private static void tryResolveAstNode(
            Object node,
            Object scope,
            Map<Object, Boolean> visited,
            int depth
    ) {
        if (node == null || scope == null || depth > 16 || visited.put(node, Boolean.TRUE) != null) {
            return;
        }
        if (isNestedFunctionalRepairBoundary(node, depth)) {
            return;
        }
        boolean shouldRetryAfterChildren = false;
        try {
            invokeMethod(node, "resolveType", scope);
            try {
                if (!containsProblemAstState(scope, node, new IdentityHashMap<>(), 0)) {
                    return;
                }
                shouldRetryAfterChildren = true;
            } catch (Exception ignored) {
                return;
            }
        } catch (Exception error) {
            shouldRetryAfterChildren = true;
            try {
                if ("MessageSend".equals(node.getClass().getSimpleName())
                        && tryRecoverAndRetryMessageSendResolveType(node, scope, error, null)) {
                    try {
                        if (!containsProblemAstState(scope, node, new IdentityHashMap<>(), 0)) {
                            return;
                        }
                    } catch (Exception ignored) {
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        try {
            for (Object child : getAstChildren(node)) {
                tryResolveAstNode(child, scope, visited, depth + 1);
            }
        } catch (Exception ignored) {
        }
        if (!shouldRetryAfterChildren) {
            return;
        }
        try {
            if ("MessageSend".equals(node.getClass().getSimpleName())) {
                InvocationRewriteState resolveState = captureInvocationRewriteState(node);
                try {
                    invokeMethod(node, "resolveType", scope);
                } catch (Exception error) {
                    tryRecoverAndRetryMessageSendResolveType(node, scope, error, resolveState);
                }
            } else {
                invokeMethod(node, "resolveType", scope);
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean tryPromoteProblematicResolvedLambda(
            Object scope,
            Object originalArgument,
            Object resolvedArgument,
            Object expectedType
    ) {
        if (!shouldRetainOriginalFunctionalArgument(originalArgument, resolvedArgument)) {
            return false;
        }
        try {
            mergeResolvedFunctionalArgumentState(originalArgument, resolvedArgument);
            if (containsOutOfBandReceiverInvocationState(originalArgument, new IdentityHashMap<>(), 0)) {
                stabilizeFunctionalArgumentState(originalArgument, expectedType, scope);
                return true;
            }
            if (!repairFunctionalArgumentProblemState(originalArgument, expectedType, scope)) {
                return false;
            }
            return !containsProblemAstState(scope, originalArgument, new IdentityHashMap<>(), 0);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean shouldKeepProblematicResolvedFunctionalArgument(Object scope, Object resolvedArgument) {
        if (!isLambdaExpression(resolvedArgument)) {
            return false;
        }
        try {
            return containsOutOfBandReceiverInvocationState(resolvedArgument, new IdentityHashMap<>(), 0)
                    || isSuccessfulFunctionalWrapperProblemLambda(scope, resolvedArgument);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isSuccessfulFunctionalWrapperProblemLambda(Object scope, Object resolvedArgument) throws Exception {
        if (scope == null || resolvedArgument == null) {
            return false;
        }
        Object compilationResult = resolveCompilationResultFromScope(scope);
        String fileName = describeCompilationUnitFileName(compilationResult);
        if (fileName == null) {
            return false;
        }
        int start = readIntField(resolvedArgument, "sourceStart");
        int end = readIntField(resolvedArgument, "sourceEnd");
        if (start < 0 || end < start) {
            return false;
        }
        if (!hasSuccessfulRangeCovering(SUCCESSFUL_FUNCTIONAL_WRAPPER_RANGES, fileName, start, end)) {
            return false;
        }
        if (Util.isDebugEnabled()) {
            Util.log("[ZirconCore] retainSuccessfulWrapperProblemLambda"
                    + ": file=" + fileName
                    + ", range=" + start + "-" + end);
        }
        return true;
    }

    private static boolean isSuccessfulExtensionFunctionalProblemLambda(Object scope, Object resolvedArgument) throws Exception {
        if (scope == null || resolvedArgument == null) {
            return false;
        }
        Object compilationResult = resolveCompilationResultFromScope(scope);
        String fileName = describeCompilationUnitFileName(compilationResult);
        if (fileName == null) {
            return false;
        }
        int start = readIntField(resolvedArgument, "sourceStart");
        int end = readIntField(resolvedArgument, "sourceEnd");
        if (start < 0 || end < start) {
            return false;
        }
        if (!hasSuccessfulRangeCovering(SUCCESSFUL_EXTENSION_UNDEFINED_RANGES, fileName, start, end)) {
            return false;
        }
        List<Object> functionalReturnTypes = getSuccessfulExtensionFunctionalReturnTypesCovering(fileName, start, end);
        if (!hasUsableFunctionalReturnType(functionalReturnTypes)) {
            return false;
        }
        if (Util.isDebugEnabled()) {
            Util.log("[ZirconCore] retainSuccessfulFunctionalProblemLambda"
                    + ": file=" + fileName
                    + ", range=" + start + "-" + end
                    + ", returnTypes=" + describeTypeCollection(functionalReturnTypes));
        }
        return true;
    }

    private static boolean containsOutOfBandReceiverInvocationState(
            Object node,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 16 || visited.put(node, Boolean.TRUE) != null || !isAstNode(node)) {
            return false;
        }
        if ("MessageSend".equals(node.getClass().getSimpleName())) {
            Object actualReceiverType = getFieldValue(node, "actualReceiverType");
            Object[] arguments = getFieldValue(node, "arguments") instanceof Object[]
                    ? (Object[]) getFieldValue(node, "arguments")
                    : new Object[0];
            Object[] argumentTypes = getFieldValue(node, "argumentTypes") instanceof Object[]
                    ? (Object[]) getFieldValue(node, "argumentTypes")
                    : new Object[0];
            Object binding = getFieldValue(node, "binding");
            Object[] parameters = binding != null && getFieldValue(binding, "parameters") instanceof Object[]
                    ? (Object[]) getFieldValue(binding, "parameters")
                    : new Object[0];
            if ((actualReceiverType != null || binding != null)
                    && (parameters.length > arguments.length || argumentTypes.length > arguments.length)) {
                return true;
            }
        }
        if (depth > 0 && isLambdaExpression(node)) {
            return false;
        }
        for (Object child : getAstChildren(node)) {
            if (containsOutOfBandReceiverInvocationState(child, visited, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static void mergeResolvedFunctionalArgumentState(Object target, Object source) throws Exception {
        if (target == null || source == null || target == source) {
            return;
        }
        copyFieldIfPresent(source, target, "descriptor");
        copyFieldIfPresent(source, target, "resolvedType");
        copyFieldIfPresent(source, target, "expectedType");
        copyFieldIfPresent(source, target, "binding");
        copyFieldIfPresent(source, target, "enclosingScope");
        syncLambdaScope(source, target);
        copyFieldIfPresent(source, target, "argumentsTypeElided");
        copyFieldIfPresent(source, target, "voidCompatible");
        copyFieldIfPresent(source, target, "valueCompatible");
        copyFieldIfPresent(source, target, "shapeAnalysisComplete");
        copyFieldIfPresent(source, target, "returnsValue");
        copyLambdaGenerationState(source, target);
        mergeResolvedLambdaArguments(target, source);
        Object targetBody = getFieldValue(target, "body");
        Object sourceBody = getFieldValue(source, "body");
        if (!containsProblemFieldNameReference(sourceBody, new IdentityHashMap<>(), 0)) {
            mergeResolvedAstState(targetBody, sourceBody, 0);
        }
        restabilizeMergedFunctionalArgumentState(target);
        ensureNestedLambdaCaptureStates(targetBody, getFieldValue(target, "scope"), new IdentityHashMap<>(), 0);
    }

    private static void mergeResolvedLambdaArguments(Object target, Object source) throws Exception {
        Object targetArguments = getFieldValue(target, "arguments");
        Object sourceArguments = getFieldValue(source, "arguments");
        if (!(targetArguments instanceof Object[]) || !(sourceArguments instanceof Object[])) {
            return;
        }
        Object[] targetArray = (Object[]) targetArguments;
        Object[] sourceArray = (Object[]) sourceArguments;
        if (targetArray.length != sourceArray.length) {
            return;
        }
        for (int index = 0; index < targetArray.length; index++) {
            Object targetArgument = targetArray[index];
            Object sourceArgument = sourceArray[index];
            if (targetArgument == null || sourceArgument == null) {
                continue;
            }
            copyFieldIfPresent(sourceArgument, targetArgument, "binding");
            copyFieldIfPresent(sourceArgument, targetArgument, "resolvedType");
        }
    }

    private static void mergeResolvedAstState(Object target, Object source, int depth) throws Exception {
        if (target == null || source == null || depth > 12) {
            return;
        }
        if (target instanceof Object[] && source instanceof Object[]) {
            Object[] targetArray = (Object[]) target;
            Object[] sourceArray = (Object[]) source;
            int limit = Math.min(targetArray.length, sourceArray.length);
            for (int index = 0; index < limit; index++) {
                mergeResolvedAstState(targetArray[index], sourceArray[index], depth + 1);
            }
            return;
        }
        if (!target.getClass().getName().equals(source.getClass().getName())) {
            return;
        }
        if (isNestedFunctionalRepairBoundary(target, depth) || isNestedFunctionalRepairBoundary(source, depth)) {
            return;
        }
        if (shouldSkipResolvedAstStateMerge(target, source)) {
            return;
        }
        if (shouldPreserveTargetLocalReferenceState(target, source)) {
            normalizeLocalNameReferenceState(target);
            return;
        }
        copyFieldIfPresent(source, target, "binding");
        copyFieldIfPresent(source, target, "resolvedType");
        copyFieldIfPresent(source, target, "actualReceiverType");
        copyFieldIfPresent(source, target, "argumentTypes");
        copyFieldIfPresent(source, target, "expectedType");
        copyFieldIfPresent(source, target, "descriptor");
        if (isLambdaExpression(target)) {
            copyFieldIfPresent(source, target, "enclosingScope");
            syncLambdaScope(source, target);
        }
        copyFieldIfPresent(source, target, "constant");
        copyLambdaGenerationState(source, target);

        mergeResolvedAstState(getFieldValue(target, "body"), getFieldValue(source, "body"), depth + 1);
        mergeResolvedAstState(getFieldValue(target, "receiver"), getFieldValue(source, "receiver"), depth + 1);
        mergeResolvedAstState(getFieldValue(target, "expression"), getFieldValue(source, "expression"), depth + 1);
        mergeResolvedAstState(getFieldValue(target, "statements"), getFieldValue(source, "statements"), depth + 1);
        mergeResolvedAstState(getFieldValue(target, "arguments"), getFieldValue(source, "arguments"), depth + 1);
    }

    private static boolean shouldSkipResolvedAstStateMerge(Object target, Object source) {
        if (target == null || source == null) {
            return false;
        }
        String simpleName = target.getClass().getSimpleName();
        if (!simpleName.equals(source.getClass().getSimpleName())) {
            return false;
        }
        return "LocalDeclaration".equals(simpleName)
                || "AllocationExpression".equals(simpleName)
                || "QualifiedAllocationExpression".equals(simpleName)
                || "ArrayAllocationExpression".equals(simpleName)
                || "ArrayInitializer".equals(simpleName);
    }

    private static boolean shouldPreserveTargetLocalReferenceState(Object target, Object source) throws Exception {
        if (target == null || source == null) {
            return false;
        }
        String simpleName = target.getClass().getSimpleName();
        if (!"SingleNameReference".equals(simpleName) && !"QualifiedNameReference".equals(simpleName)) {
            return false;
        }
        Object targetBinding = getFieldValue(target, "binding");
        if (!isLocalVariableBinding(targetBinding)) {
            return false;
        }
        Object sourceBinding = getFieldValue(source, "binding");
        return sourceBinding != null
                && sourceBinding.getClass().getName().endsWith("ProblemFieldBinding");
    }

    private static void restoreOriginalLocalReferenceBindings(Object target, Object source, int depth) throws Exception {
        if (target == null || source == null || depth > 12) {
            return;
        }
        if (target instanceof Object[] && source instanceof Object[]) {
            Object[] targetArray = (Object[]) target;
            Object[] sourceArray = (Object[]) source;
            int limit = Math.min(targetArray.length, sourceArray.length);
            for (int index = 0; index < limit; index++) {
                restoreOriginalLocalReferenceBindings(targetArray[index], sourceArray[index], depth + 1);
            }
            return;
        }
        if (!target.getClass().getName().equals(source.getClass().getName())) {
            return;
        }
        String simpleName = target.getClass().getSimpleName();
        if ("SingleNameReference".equals(simpleName) || "QualifiedNameReference".equals(simpleName)) {
            Object targetBinding = getFieldValue(target, "binding");
            Object sourceBinding = getFieldValue(source, "binding");
            if (targetBinding != null
                    && targetBinding.getClass().getName().endsWith("ProblemFieldBinding")
                    && isLocalVariableBinding(sourceBinding)) {
                copyFieldIfPresent(source, target, "binding");
                copyFieldIfPresent(source, target, "resolvedType");
                if (findField(target.getClass(), "actualReceiverType") != null) {
                    setFieldValue(target, "actualReceiverType", null);
                }
                normalizeLocalNameReferenceState(target);
                return;
            }
        }
        restoreOriginalLocalReferenceBindings(getFieldValue(target, "body"), getFieldValue(source, "body"), depth + 1);
        restoreOriginalLocalReferenceBindings(getFieldValue(target, "receiver"), getFieldValue(source, "receiver"), depth + 1);
        restoreOriginalLocalReferenceBindings(getFieldValue(target, "expression"), getFieldValue(source, "expression"), depth + 1);
        restoreOriginalLocalReferenceBindings(getFieldValue(target, "statements"), getFieldValue(source, "statements"), depth + 1);
        restoreOriginalLocalReferenceBindings(getFieldValue(target, "arguments"), getFieldValue(source, "arguments"), depth + 1);
    }

    private static void rebindProblemFieldLocalReferences(
            Object node,
            Object currentScope,
            Object rootScope,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 16 || visited.put(node, Boolean.TRUE) != null || !isAstNode(node)) {
            return;
        }
        if (isNestedTypeDeclarationBoundary(node, depth)) {
            return;
        }
        Object nextScope = currentScope;
        if (isLambdaExpression(node)) {
            Object lambdaScope = getFieldValue(node, "scope");
            Object enclosingScope = getFieldValue(node, "enclosingScope");
            nextScope = lambdaScope != null ? lambdaScope : (enclosingScope != null ? enclosingScope : currentScope);
        }
        String simpleName = node.getClass().getSimpleName();
        if ("SingleNameReference".equals(simpleName) || "QualifiedNameReference".equals(simpleName)) {
            Object binding = getFieldValue(node, "binding");
            if (binding == null || isProblem(binding)) {
                String referenceName = readReferenceSimpleName(node, binding);
                Object localBinding = findLocalBindingInScopeChain(nextScope, referenceName);
                if (localBinding == null && rootScope != null && rootScope != nextScope) {
                    localBinding = findLocalBindingInScopeChain(rootScope, referenceName);
                }
                if (localBinding != null) {
                    setFieldValue(node, "binding", localBinding);
                    Object localType = getFieldValue(localBinding, "type");
                    if (localType != null && findField(node.getClass(), "resolvedType") != null) {
                        setFieldValue(node, "resolvedType", localType);
                    }
                    if (findField(node.getClass(), "actualReceiverType") != null) {
                        setFieldValue(node, "actualReceiverType", null);
                    }
                    if (findField(node.getClass(), "constant") != null) {
                        Object notAConstant = getNotAConstant(node);
                        if (notAConstant != null) {
                            setFieldValue(node, "constant", notAConstant);
                        }
                    }
                    normalizeLocalNameReferenceState(node);
                    rememberProvenLocalReferenceRange(nextScope, node, localBinding);
                }
            }
        }
        for (Object child : getAstChildren(node)) {
            rebindProblemFieldLocalReferences(child, nextScope, rootScope, visited, depth + 1);
        }
    }

    private static void rememberProvenLocalReferenceRange(Object scope, Object node, Object localBinding) throws Exception {
        if (scope == null || node == null || !isLocalVariableBinding(localBinding)) {
            return;
        }
        Object localType = getFieldValue(localBinding, "type");
        if (localType == null || isProblem(localType)) {
            return;
        }
        int start = readIntField(node, "sourceStart");
        int end = readIntField(node, "sourceEnd");
        if (start < 0 || end < start) {
            return;
        }
        Object compilationResult = resolveCompilationResultFromScope(scope);
        String fileName = describeCompilationUnitFileName(compilationResult);
        if (fileName != null) {
            PROVEN_LOCAL_REFERENCE_RANGES.add(fileName + "|" + start + "|" + end);
        }
    }

    private static String readReferenceSimpleName(Object node, Object binding) throws Exception {
        String referenceName = describeReferenceName(node, binding);
        if (referenceName != null && !referenceName.isEmpty() && !"unknown".equals(referenceName)) {
            return referenceName;
        }
        Object token = findField(node.getClass(), "token") != null ? getFieldValue(node, "token") : null;
        if (token instanceof char[]) {
            return new String((char[]) token);
        }
        Object tokens = findField(node.getClass(), "tokens") != null ? getFieldValue(node, "tokens") : null;
        if (tokens instanceof char[][] && ((char[][]) tokens).length > 0 && ((char[][]) tokens)[0] != null) {
            return new String(((char[][]) tokens)[0]);
        }
        return referenceName == null ? "" : referenceName;
    }

    private static Object findLocalBindingInScopeChain(Object scope, String referenceName) throws Exception {
        if (scope == null || referenceName == null || referenceName.isEmpty() || "unknown".equals(referenceName)) {
            return null;
        }
        for (int depth = 0; scope != null && depth < 8; depth++) {
            Object localBinding = findLocalBindingInScopeLevel(scope, referenceName);
            if (localBinding != null) {
                return localBinding;
            }
            scope = findField(scope.getClass(), "parent") != null ? getFieldValue(scope, "parent") : null;
        }
        return null;
    }

    private static Object findLocalBindingInScopeLevel(Object scope, String referenceName) throws Exception {
        if (scope == null || referenceName == null || referenceName.isEmpty()) {
            return null;
        }
        Object[] locals = findField(scope.getClass(), "locals") != null && getFieldValue(scope, "locals") instanceof Object[]
                ? (Object[]) getFieldValue(scope, "locals")
                : new Object[0];
        int limit = locals.length;
        Object localIndexValue = findField(scope.getClass(), "localIndex") != null ? getFieldValue(scope, "localIndex") : null;
        if (localIndexValue instanceof Number) {
            int localIndex = ((Number) localIndexValue).intValue();
            if (localIndex >= 0 && localIndex < limit) {
                limit = localIndex;
            }
        }
        for (int index = 0; index < limit; index++) {
            Object local = locals[index];
            if (local == null || !isLocalVariableBinding(local)) {
                continue;
            }
            if (referenceName.equals(readBindingSimpleName(local))) {
                return local;
            }
        }
        // During early conditional-expression recovery MethodScope.locals may
        // not yet contain its formal parameters. Resolve them from the scope's
        // actual method/lambda reference context, whose Argument nodes already
        // carry the canonical LocalVariableBinding (including method type
        // variables and the enclosing type's substitutions).
        Object referenceContext = findField(scope.getClass(), "referenceContext") != null
                ? getFieldValue(scope, "referenceContext")
                : null;
        Object contextArguments = referenceContext != null
                && findField(referenceContext.getClass(), "arguments") != null
                ? getFieldValue(referenceContext, "arguments")
                : null;
        if (contextArguments instanceof Object[]) {
            for (Object argument : (Object[]) contextArguments) {
                Object argumentBinding = argument != null
                        && findField(argument.getClass(), "binding") != null
                        ? getFieldValue(argument, "binding")
                        : null;
                if (argumentBinding != null
                        && isLocalVariableBinding(argumentBinding)
                        && referenceName.equals(readBindingSimpleName(argumentBinding))) {
                    return argumentBinding;
                }
            }
        }
        Object extraSyntheticArguments = findField(scope.getClass(), "extraSyntheticArguments") != null
                ? getFieldValue(scope, "extraSyntheticArguments")
                : null;
        if (extraSyntheticArguments instanceof Object[]) {
            for (Object synthetic : (Object[]) extraSyntheticArguments) {
                if (synthetic == null || !isLocalVariableBinding(synthetic)) {
                    continue;
                }
                if (referenceName.equals(readBindingSimpleName(synthetic))) {
                    return synthetic;
                }
                Object actualOuterLocal = findField(synthetic.getClass(), "actualOuterLocalVariable") != null
                        ? getFieldValue(synthetic, "actualOuterLocalVariable")
                        : null;
                if (actualOuterLocal != null && referenceName.equals(readBindingSimpleName(actualOuterLocal))) {
                    return synthetic;
                }
            }
        }
        Object lambdaOuterLocals = isLambdaExpression(referenceContext)
                ? getFieldValue(referenceContext, "outerLocalVariables")
                : null;
        if (lambdaOuterLocals instanceof Object[]) {
            for (Object outerLocal : (Object[]) lambdaOuterLocals) {
                if (outerLocal == null || !isLocalVariableBinding(outerLocal)) {
                    continue;
                }
                if (referenceName.equals(readBindingSimpleName(outerLocal))) {
                    return outerLocal;
                }
                Object actualOuterLocal = findField(outerLocal.getClass(), "actualOuterLocalVariable") != null
                        ? getFieldValue(outerLocal, "actualOuterLocalVariable")
                        : null;
                if (actualOuterLocal != null && referenceName.equals(readBindingSimpleName(actualOuterLocal))) {
                    return outerLocal;
                }
            }
        }
        return null;
    }

    private static String readBindingSimpleName(Object binding) throws Exception {
        if (binding == null) {
            return "";
        }
        Object name = findField(binding.getClass(), "name") != null ? getFieldValue(binding, "name") : null;
        return name instanceof char[] ? new String((char[]) name) : "";
    }

    private static boolean containsProblemFieldNameReference(Object node, Map<Object, Boolean> visited, int depth) throws Exception {
        if (node == null || depth > 16) {
            return false;
        }
        if (node instanceof Object[]) {
            for (Object element : (Object[]) node) {
                if (containsProblemFieldNameReference(element, visited, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        if (!isAstNode(node) || visited.put(node, Boolean.TRUE) != null) {
            return false;
        }
        String simpleName = node.getClass().getSimpleName();
        if ("SingleNameReference".equals(simpleName) || "QualifiedNameReference".equals(simpleName)) {
            Object binding = getFieldValue(node, "binding");
            if (binding != null && binding.getClass().getName().endsWith("ProblemFieldBinding")) {
                return true;
            }
        }
        for (Object child : getAstChildren(node)) {
            if (containsProblemFieldNameReference(child, visited, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static void copyLambdaGenerationState(Object source, Object target) throws Exception {
        if (!isLambdaExpression(source) || !isLambdaExpression(target)) {
            return;
        }
        copyFieldIfPresent(source, target, "outerLocalVariables");
        copyFieldIfPresent(source, target, "outerLocalVariablesSlotSize");
        copyFieldIfPresent(source, target, "shouldCaptureInstance");
        copyFieldIfPresent(source, target, "requiresGenericSignature");
        copyFieldIfPresent(source, target, "bootstrapMethodNumber");
        ensureLambdaCaptureState(target);
    }

    private static void syncLambdaScope(Object source, Object target) throws Exception {
        if (!isLambdaExpression(source) || !isLambdaExpression(target)) {
            return;
        }
        Object sourceScope = getFieldValue(source, "scope");
        if (sourceScope == null) {
            return;
        }
        Object targetScope = getFieldValue(target, "scope");
        if (targetScope == null || !targetScope.getClass().equals(sourceScope.getClass())) {
            targetScope = recreateLambdaScope(target, sourceScope);
            if (targetScope == null) {
                return;
            }
            setFieldValue(target, "scope", targetScope);
        }
        copyFieldIfPresent(sourceScope, targetScope, "locals");
        copyFieldIfPresent(sourceScope, targetScope, "localIndex");
        copyFieldIfPresent(sourceScope, targetScope, "startIndex");
        copyFieldIfPresent(sourceScope, targetScope, "offset");
        copyFieldIfPresent(sourceScope, targetScope, "maxOffset");
        copyFieldIfPresent(sourceScope, targetScope, "shiftScopes");
        copyFieldIfPresent(sourceScope, targetScope, "subscopes");
        copyFieldIfPresent(sourceScope, targetScope, "subscopeCount");
        copyFieldIfPresent(sourceScope, targetScope, "enclosingCase");
        copyFieldIfPresent(sourceScope, targetScope, "insideTypeDeclarationAnnotations");
        copyFieldIfPresent(sourceScope, targetScope, "resolvingGuardExpression");
        copyFieldIfPresent(sourceScope, targetScope, "finallyInfo");
        copyFieldIfPresent(sourceScope, targetScope, "isStatic");
        copyFieldIfPresent(sourceScope, targetScope, "isConstructorCall");
        copyFieldIfPresent(sourceScope, targetScope, "initializedField");
        copyFieldIfPresent(sourceScope, targetScope, "lastVisibleFieldID");
        copyFieldIfPresent(sourceScope, targetScope, "analysisIndex");
        copyFieldIfPresent(sourceScope, targetScope, "isPropagatingInnerClassEmulation");
        copyFieldIfPresent(sourceScope, targetScope, "lastIndex");
        copyFieldIfPresent(sourceScope, targetScope, "definiteInits");
        copyFieldIfPresent(sourceScope, targetScope, "extraDefiniteInits");
        copyFieldIfPresent(sourceScope, targetScope, "extraSyntheticArguments");
        copyFieldIfPresent(sourceScope, targetScope, "hasMissingSwitchDefault");
        if (findField(targetScope.getClass(), "referenceContext") != null) {
            setFieldValue(targetScope, "referenceContext", target);
        }
        if (findField(targetScope.getClass(), "blockStatement") != null) {
            setFieldValue(targetScope, "blockStatement", getFieldValue(target, "body"));
        }
        initializeLambdaScope(target, targetScope);
    }

    private static void restabilizeMergedFunctionalArgumentState(Object argument) throws Exception {
        if (argument == null) {
            return;
        }
        Object fallbackScope = getFieldValue(argument, "scope");
        if (fallbackScope == null) {
            fallbackScope = getFieldValue(argument, "enclosingScope");
        }
        if (fallbackScope == null) {
            return;
        }
        stabilizeNestedLambdaScopes(argument, fallbackScope, new IdentityHashMap<>(), 0);
        Object root = isLambdaExpression(argument) ? getFieldValue(argument, "body") : argument;
        if (root != null) {
            stabilizeNestedLambdaScopes(root, fallbackScope, new IdentityHashMap<>(), 0);
            normalizeResolvedFunctionalAstState(root, new IdentityHashMap<>(), 0);
            normalizeExpressionConstantsForCodegen(root, new IdentityHashMap<>(), 0);
        }
    }

    private static Object recreateLambdaScope(Object lambdaExpression, Object sourceScope) throws Exception {
        if (lambdaExpression == null || sourceScope == null) {
            return null;
        }
        Object parentScope = getFieldValue(lambdaExpression, "enclosingScope");
        if (parentScope == null) {
            parentScope = getFieldValue(sourceScope, "parent");
        }
        if (parentScope == null) {
            return null;
        }
        boolean isStatic = Boolean.TRUE.equals(getFieldValue(sourceScope, "isStatic"));
        for (java.lang.reflect.Constructor<?> constructor : sourceScope.getClass().getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length == 3
                    && parameterTypes[0].isInstance(parentScope)
                    && parameterTypes[1].isInstance(lambdaExpression)
                    && parameterTypes[2] == boolean.class) {
                constructor.setAccessible(true);
                return constructor.newInstance(parentScope, lambdaExpression, isStatic);
            }
            if (parameterTypes.length == 4
                    && parameterTypes[0].isInstance(parentScope)
                    && parameterTypes[1].isInstance(lambdaExpression)
                    && parameterTypes[2] == boolean.class
                    && (parameterTypes[3] == int.class || parameterTypes[3] == Integer.TYPE)) {
                constructor.setAccessible(true);
                return constructor.newInstance(parentScope, lambdaExpression, isStatic, 0);
            }
        }
        return null;
    }

    private static void ensureNestedLambdaCaptureStates(Object node, Map<Object, Boolean> visited, int depth) throws Exception {
        ensureNestedLambdaCaptureStates(node, null, visited, depth);
    }

    private static void ensureNestedLambdaCaptureStates(
            Object node,
            Object fallbackScope,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 16 || visited.put(node, Boolean.TRUE) != null || !isAstNode(node)) {
            return;
        }
        if (isNestedTypeDeclarationBoundary(node, depth)) {
            return;
        }
        Object nextScope = fallbackScope;
        if (isLambdaExpression(node)) {
            Object lambdaScope = getFieldValue(node, "scope");
            Object enclosingScope = getFieldValue(node, "enclosingScope");
            Object effectiveScope = lambdaScope != null ? lambdaScope : (enclosingScope != null ? enclosingScope : fallbackScope);
            if (effectiveScope != null) {
                ensureLambdaAnalyseScope(node, effectiveScope);
                ensureLambdaBindingResolved(node, getFieldValue(node, "expectedType"), effectiveScope);
                refreshLambdaMethodBindingIfPossible(node);
                rebindLambdaArgumentReferences(node);
            }
            if (lambdaScope != null) {
                nextScope = lambdaScope;
            } else if (effectiveScope != null) {
                nextScope = effectiveScope;
            }
            ensureLambdaCaptureState(node);
        }
        for (Object child : getAstChildren(node)) {
            ensureNestedLambdaCaptureStates(child, nextScope, visited, depth + 1);
        }
    }

    private static void ensureLambdaCaptureState(Object lambdaExpression) throws Exception {
        if (!isLambdaExpression(lambdaExpression)) {
            return;
        }
        Object outerLocalVariables = getFieldValue(lambdaExpression, "outerLocalVariables");
        if (outerLocalVariables instanceof Object[] && ((Object[]) outerLocalVariables).length > 0) {
            refreshLambdaMethodBinding(lambdaExpression);
            return;
        }
        Object body = getFieldValue(lambdaExpression, "body");
        if (body == null) {
            return;
        }
        LinkedHashSet<Object> excludedBindings = new LinkedHashSet<>();
        collectExcludedLambdaBindings(lambdaExpression, excludedBindings, new IdentityHashMap<>(), 0);
        LinkedHashSet<Object> capturedBindings = new LinkedHashSet<>();
        collectCapturedLocalBindings(body, excludedBindings, capturedBindings, new IdentityHashMap<>(), 0);
        if (capturedBindings.isEmpty()) {
            logLambdaCaptureInference(lambdaExpression, excludedBindings, capturedBindings, false, null);
            return;
        }
        boolean syntheticArgumentsAdded = false;
        Throwable syntheticArgumentError = null;
        for (Object capturedBinding : capturedBindings) {
            try {
                invokeMethod(lambdaExpression, "addSyntheticArgument", capturedBinding);
                syntheticArgumentsAdded = true;
            } catch (Exception ignored) {
                syntheticArgumentError = ignored;
            }
        }
        if (syntheticArgumentsAdded) {
            Object refreshedOuterLocals = getFieldValue(lambdaExpression, "outerLocalVariables");
            if (refreshedOuterLocals instanceof Object[] && ((Object[]) refreshedOuterLocals).length > 0) {
                refreshLambdaMethodBinding(lambdaExpression);
                logLambdaCaptureInference(lambdaExpression, excludedBindings, capturedBindings, true, null);
                return;
            }
        }
        Object[] typedCaptures = createLambdaOuterLocalVariableArray(lambdaExpression, capturedBindings);
        if (typedCaptures == null) {
            logLambdaCaptureInference(lambdaExpression, excludedBindings, capturedBindings, syntheticArgumentsAdded, syntheticArgumentError);
            return;
        }
        int index = 0;
        int slotSize = 0;
        for (Object capturedBinding : capturedBindings) {
            typedCaptures[index++] = adaptLambdaOuterLocalVariable(typedCaptures.getClass().getComponentType(), capturedBinding, lambdaExpression);
            slotSize += computeLocalBindingSlotSize(capturedBinding);
        }
        setFieldValue(lambdaExpression, "outerLocalVariables", typedCaptures);
        if (findField(lambdaExpression.getClass(), "outerLocalVariablesSlotSize") != null) {
            setFieldValue(lambdaExpression, "outerLocalVariablesSlotSize", slotSize);
        }
        refreshLambdaMethodBinding(lambdaExpression);
        logLambdaCaptureInference(lambdaExpression, excludedBindings, capturedBindings, syntheticArgumentsAdded, syntheticArgumentError);
    }

    private static void collectExcludedLambdaBindings(
            Object node,
            Set<Object> excludedBindings,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 16 || visited.put(node, Boolean.TRUE) != null) {
            return;
        }
        if (isNestedTypeDeclarationBoundary(node, depth)) {
            return;
        }
        if (isAstNode(node)) {
            String simpleName = node.getClass().getSimpleName();
            if ("Argument".equals(simpleName) || "LocalDeclaration".equals(simpleName)) {
                Object binding = getFieldValue(node, "binding");
                if (isLocalVariableBinding(binding)) {
                    excludedBindings.add(binding);
                }
            }
        }
        for (Object child : getAstChildren(node)) {
            collectExcludedLambdaBindings(child, excludedBindings, visited, depth + 1);
        }
    }

    private static void collectCapturedLocalBindings(
            Object node,
            Set<Object> excludedBindings,
            Set<Object> capturedBindings,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 16 || visited.put(node, Boolean.TRUE) != null || !isAstNode(node)) {
            return;
        }
        if (isNestedTypeDeclarationBoundary(node, depth)) {
            return;
        }
        String simpleName = node.getClass().getSimpleName();
        if ("SingleNameReference".equals(simpleName) || "QualifiedNameReference".equals(simpleName)) {
            Object binding = getFieldValue(node, "binding");
            if (isLocalVariableBinding(binding) && !excludedBindings.contains(binding)) {
                capturedBindings.add(binding);
            }
        }
        for (Object child : getAstChildren(node)) {
            collectCapturedLocalBindings(child, excludedBindings, capturedBindings, visited, depth + 1);
        }
    }

    private static boolean isAstNode(Object value) {
        return value != null && value.getClass().getName().startsWith("org.eclipse.jdt.internal.compiler.ast.");
    }

    private static boolean isLocalVariableBinding(Object value) {
        return value != null && value.getClass().getName().endsWith("LocalVariableBinding");
    }

    private static List<Object> getAstChildren(Object node) throws Exception {
        List<Object> children = new ArrayList<>();
        if (node == null) {
            return children;
        }
        Class<?> type = node.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(node);
                if (value == null) {
                    continue;
                }
                if (isAstNode(value)) {
                    children.add(value);
                    continue;
                }
                if (!value.getClass().isArray()) {
                    continue;
                }
                int length = Array.getLength(value);
                for (int index = 0; index < length; index++) {
                    Object element = Array.get(value, index);
                    if (isAstNode(element)) {
                        children.add(element);
                    }
                }
            }
            type = type.getSuperclass();
        }
        return children;
    }

    private static int computeLocalBindingSlotSize(Object localBinding) {
        try {
            Object type = getFieldValue(localBinding, "type");
            String signature = getTypeSignatureDebug(type);
            return "J".equals(signature) || "D".equals(signature) ? 2 : 1;
        } catch (Exception ignored) {
            return 1;
        }
    }

    private static Object[] createLambdaOuterLocalVariableArray(Object lambdaExpression, Set<Object> capturedBindings) throws Exception {
        Field field = findField(lambdaExpression.getClass(), "outerLocalVariables");
        Class<?> componentType = field != null && field.getType().isArray()
                ? field.getType().getComponentType()
                : Object.class;
        for (Object capturedBinding : capturedBindings) {
            if (adaptLambdaOuterLocalVariable(componentType, capturedBinding, lambdaExpression) == null) {
                return null;
            }
        }
        return (Object[]) Array.newInstance(componentType, capturedBindings.size());
    }

    private static Object adaptLambdaOuterLocalVariable(Class<?> componentType, Object capturedBinding, Object lambdaExpression) throws Exception {
        if (componentType == null || capturedBinding == null) {
            return null;
        }
        if (componentType.isInstance(capturedBinding)) {
            return capturedBinding;
        }
        Object lambdaScope = lambdaExpression == null ? null : getFieldValue(lambdaExpression, "scope");
        for (java.lang.reflect.Constructor<?> constructor : componentType.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length == 1 && parameterTypes[0].isInstance(capturedBinding)) {
                constructor.setAccessible(true);
                return constructor.newInstance(capturedBinding);
            }
            if (parameterTypes.length == 2
                    && parameterTypes[0].isInstance(capturedBinding)
                    && lambdaScope != null
                    && parameterTypes[1].isInstance(lambdaScope)) {
                constructor.setAccessible(true);
                return constructor.newInstance(capturedBinding, lambdaScope);
            }
        }
        return null;
    }

    private static void logLambdaCaptureInference(
            Object lambdaExpression,
            Set<Object> excludedBindings,
            Set<Object> capturedBindings,
            boolean syntheticArgumentsAdded,
            Throwable error
    ) throws Exception {
        if (!Util.isDebugEnabled() || lambdaExpression == null) {
            return;
        }
        String key = describeSourceRange(lambdaExpression);
        if (!LAMBDA_CAPTURE_INFERENCE_KEYS.add(key)) {
            return;
        }
        Object outerLocals = getFieldValue(lambdaExpression, "outerLocalVariables");
        Field outerLocalField = findField(lambdaExpression.getClass(), "outerLocalVariables");
        Util.log("[ZirconCore] lambdaCaptureInference"
                + ": source=" + key
                + ", excluded=" + describeOuterLocalVariables(excludedBindings.toArray())
                + ", captured=" + describeOuterLocalVariables(capturedBindings.toArray())
                + ", syntheticAdded=" + syntheticArgumentsAdded
                + ", fieldType=" + (outerLocalField == null ? "null" : outerLocalField.getType().getTypeName())
                + ", outerLocals=" + describeOuterLocalVariables(outerLocals instanceof Object[] ? (Object[]) outerLocals : new Object[0])
                + ", nestedLambdas=" + describeNestedLambdaStates(getFieldValue(lambdaExpression, "body"))
                + ", error=" + (error == null ? "null" : error.getClass().getName() + ": " + error.getMessage()));
    }

    private static void refreshLambdaMethodBinding(Object lambdaExpression) throws Exception {
        if (!isLambdaExpression(lambdaExpression)) {
            return;
        }
        Object binding = getFieldValue(lambdaExpression, "binding");
        Object originalBinding = binding;
        if (binding == null || isProblem(binding) || !isSyntheticMethodBinding(binding)) {
            Object syntheticBinding = createLambdaSyntheticMethodBinding(lambdaExpression);
            if (syntheticBinding != null && !isProblem(syntheticBinding)) {
                binding = syntheticBinding;
                setFieldValue(lambdaExpression, "binding", binding);
                clearLambdaActualMethodBinding(lambdaExpression);
            } else if (originalBinding != null && !isProblem(originalBinding)) {
                binding = originalBinding;
                setFieldValue(lambdaExpression, "binding", binding);
                clearLambdaActualMethodBinding(lambdaExpression);
            } else {
                Object rebound = invokeMethod(lambdaExpression, "getMethodBinding");
                if (rebound == null || isProblem(rebound)) {
                    return;
                }
                binding = rebound;
                setFieldValue(lambdaExpression, "binding", binding);
                clearLambdaActualMethodBinding(lambdaExpression);
            }
        }
        if (binding == null || isProblem(binding)) {
            return;
        }
        alignLambdaSyntheticBindingFlags(lambdaExpression, binding);
        rebindCapturedLambdaReferences(lambdaExpression);
        syncLambdaMethodBindingCaptures(lambdaExpression, binding);
        syncLambdaArgumentBindingTypes(lambdaExpression, binding);
        rebindLambdaArgumentReferences(lambdaExpression);
        normalizeLambdaLocalBindingsForCodegen(lambdaExpression);
        reconcileNestedLambdaActualOuterBindings(lambdaExpression);
        resetMethodBindingCaches(binding);
        primeMethodBindingState(binding);
    }

    private static Object createLambdaSyntheticMethodBinding(Object lambdaExpression) throws Exception {
        Object scope = getFieldValue(lambdaExpression, "scope");
        if (scope == null) {
            try {
                scope = invokeMethod(lambdaExpression, "getScope");
            } catch (Exception ignored) {
                scope = null;
            }
        }
        if (scope == null) {
            return null;
        }
        Object enclosingSourceType;
        try {
            enclosingSourceType = invokeMethod(scope, "enclosingSourceType");
        } catch (Exception ignored) {
            enclosingSourceType = null;
        }
        if (enclosingSourceType == null) {
            return null;
        }
        Object syntheticBinding = invokeMethod(enclosingSourceType, "addSyntheticMethod", lambdaExpression);
        if (syntheticBinding != null && isSyntheticMethodBinding(syntheticBinding)) {
            setFieldValue(lambdaExpression, "binding", syntheticBinding);
        }
        return syntheticBinding;
    }

    private static void clearLambdaActualMethodBinding(Object lambdaExpression) throws Exception {
        if (findField(lambdaExpression.getClass(), "actualMethodBinding") != null) {
            setFieldValue(lambdaExpression, "actualMethodBinding", null);
        }
    }

    private static void alignLambdaSyntheticBindingFlags(Object lambdaExpression, Object binding) throws Exception {
        if (!isSyntheticMethodBinding(binding)) {
            return;
        }
        boolean shouldCaptureInstance = Boolean.TRUE.equals(getFieldValue(lambdaExpression, "shouldCaptureInstance"));
        Field modifiersField = findField(binding.getClass(), "modifiers");
        if (modifiersField != null) {
            int modifiers = ((Number) modifiersField.get(binding)).intValue();
            if (shouldCaptureInstance) {
                modifiers &= ~0x0008;
            } else {
                modifiers |= 0x0008;
            }
            modifiersField.setAccessible(true);
            modifiersField.set(binding, modifiers);
        }
        Object scope = getFieldValue(lambdaExpression, "scope");
        Field scopeStaticField = scope == null ? null : findField(scope.getClass(), "isStatic");
        if (scopeStaticField != null) {
            scopeStaticField.setAccessible(true);
            scopeStaticField.set(scope, !shouldCaptureInstance);
        }
        if (scope != null) {
            Object outerLocals = getFieldValue(lambdaExpression, "outerLocalVariables");
            Field extraSyntheticArgumentsField = findField(scope.getClass(), "extraSyntheticArguments");
            if (extraSyntheticArgumentsField != null && outerLocals != null && outerLocals.getClass().isArray()) {
                extraSyntheticArgumentsField.setAccessible(true);
                extraSyntheticArgumentsField.set(scope, outerLocals);
            }
        }
        Field typeVariablesField = findField(binding.getClass(), "typeVariables");
        if (typeVariablesField != null) {
            typeVariablesField.setAccessible(true);
            typeVariablesField.set(binding, emptyTypeVariableBindingArray(binding));
        }
    }

    private static void rebindCapturedLambdaReferences(Object lambdaExpression) throws Exception {
        if (!isLambdaExpression(lambdaExpression)) {
            return;
        }
        Object[] outerLocals = getFieldValue(lambdaExpression, "outerLocalVariables") instanceof Object[]
                ? (Object[]) getFieldValue(lambdaExpression, "outerLocalVariables")
                : new Object[0];
        if (outerLocals.length == 0) {
            return;
        }
        Map<Object, Object> syntheticByCapturedBinding = new IdentityHashMap<>();
        for (Object outerLocal : outerLocals) {
            if (outerLocal == null) {
                continue;
            }
            Object capturedBinding = findField(outerLocal.getClass(), "actualOuterLocalVariable") != null
                    ? getFieldValue(outerLocal, "actualOuterLocalVariable")
                    : null;
            if (capturedBinding != null) {
                syntheticByCapturedBinding.put(capturedBinding, outerLocal);
            }
        }
        if (syntheticByCapturedBinding.isEmpty()) {
            return;
        }
        if (shouldLogLambdaRebind(lambdaExpression)) {
            Util.log("[ZirconCore] lambdaRebind start"
                    + ": source=" + describeSourceRange(lambdaExpression)
                    + ", lambdaId=" + System.identityHashCode(lambdaExpression)
                    + ", outerLocals=" + describeOuterLocalVariables(outerLocals)
                    + ", captures=" + describeCapturedSyntheticBindings(syntheticByCapturedBinding));
        }
        Object body = getFieldValue(lambdaExpression, "body");
        rebindCapturedLambdaReferences(body, syntheticByCapturedBinding, new IdentityHashMap<>(), 0);
    }

    private static void rebindCapturedLambdaReferences(
            Object node,
            Map<Object, Object> syntheticByCapturedBinding,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 16 || visited.put(node, Boolean.TRUE) != null || !isAstNode(node)) {
            return;
        }
        if (depth > 0 && isLambdaExpression(node)) {
            return;
        }
        if (isNestedTypeDeclarationBoundary(node, depth)) {
            return;
        }
        String simpleName = node.getClass().getSimpleName();
        if ("SingleNameReference".equals(simpleName) || "QualifiedNameReference".equals(simpleName)) {
            Object binding = getFieldValue(node, "binding");
            String referenceName = describeReferenceName(node, binding);
            Object syntheticBinding = syntheticByCapturedBinding.get(binding);
            if (syntheticBinding == null && binding != null) {
                syntheticBinding = findMatchingSyntheticBinding(binding, syntheticByCapturedBinding);
            }
            if (isInterestingCapturedReference(referenceName)) {
                Util.log("[ZirconCore] lambdaRebind visit"
                        + ": source=" + describeSourceRange(node)
                        + ", nodeId=" + System.identityHashCode(node)
                        + ", name=" + referenceName
                        + ", binding=" + describeBinding(binding)
                        + ", bindingId=" + describeIdentity(binding)
                        + ", matched=" + describeBinding(syntheticBinding)
                        + ", matchedId=" + describeIdentity(syntheticBinding)
                        + ", captures=" + describeCapturedSyntheticBindings(syntheticByCapturedBinding));
            }
            if (syntheticBinding != null) {
                setFieldValue(node, "binding", syntheticBinding);
                if (findField(node.getClass(), "resolvedType") != null) {
                    setFieldValue(node, "resolvedType", getFieldValue(syntheticBinding, "type"));
                }
                if (isInterestingCapturedReference(referenceName)) {
                    Util.log("[ZirconCore] lambdaRebind applied"
                            + ": source=" + describeSourceRange(node)
                            + ", nodeId=" + System.identityHashCode(node)
                            + ", name=" + referenceName
                            + ", binding=" + describeBinding(getFieldValue(node, "binding"))
                            + ", bindingId=" + describeIdentity(getFieldValue(node, "binding")));
                }
            }
        }
        for (Object child : getAstChildren(node)) {
            rebindCapturedLambdaReferences(child, syntheticByCapturedBinding, visited, depth + 1);
        }
    }

    private static Object findMatchingSyntheticBinding(Object binding, Map<Object, Object> syntheticByCapturedBinding) throws Exception {
        if (binding == null || syntheticByCapturedBinding.isEmpty()) {
            return null;
        }
        Object bindingName = getFieldValue(binding, "name");
        Object bindingType = getFieldValue(binding, "type");
        String bindingSignature = getTypeSignatureDebug(bindingType);
        for (Map.Entry<Object, Object> entry : syntheticByCapturedBinding.entrySet()) {
            Object capturedBinding = entry.getKey();
            if (capturedBinding == null) {
                continue;
            }
            Object capturedName = getFieldValue(capturedBinding, "name");
            if (!Arrays.equals(bindingName instanceof char[] ? (char[]) bindingName : null,
                    capturedName instanceof char[] ? (char[]) capturedName : null)) {
                continue;
            }
            Object capturedType = getFieldValue(capturedBinding, "type");
            String capturedSignature = getTypeSignatureDebug(capturedType);
            if (bindingType == capturedType || bindingSignature.equals(capturedSignature)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Object findSyntheticBindingInScope(Object currentScope, Object binding) throws Exception {
        Object scope = currentScope;
        for (int depth = 0; scope != null && depth < 8; depth++) {
            Object syntheticBinding = findSyntheticBindingInScopeLevel(scope, binding);
            if (syntheticBinding != null) {
                return syntheticBinding;
            }
            scope = findField(scope.getClass(), "parent") != null ? getFieldValue(scope, "parent") : null;
        }
        return null;
    }

    private static Object findSyntheticBindingInScopeLevel(Object scope, Object binding) throws Exception {
        if (scope == null || binding == null) {
            return null;
        }
        Map<Object, Object> syntheticByCapturedBinding = new IdentityHashMap<>();
        Object extraSyntheticArguments = findField(scope.getClass(), "extraSyntheticArguments") != null
                ? getFieldValue(scope, "extraSyntheticArguments")
                : null;
        collectSyntheticBindings(syntheticByCapturedBinding, extraSyntheticArguments);
        Object referenceContext = findField(scope.getClass(), "referenceContext") != null
                ? getFieldValue(scope, "referenceContext")
                : null;
        if (isLambdaExpression(referenceContext)) {
            collectSyntheticBindings(syntheticByCapturedBinding, getFieldValue(referenceContext, "outerLocalVariables"));
        }
        if (syntheticByCapturedBinding.isEmpty()) {
            return null;
        }
        Object syntheticBinding = syntheticByCapturedBinding.get(binding);
        if (syntheticBinding != null) {
            return syntheticBinding;
        }
        return findMatchingSyntheticBinding(binding, syntheticByCapturedBinding);
    }

    private static void collectSyntheticBindings(Map<Object, Object> syntheticByCapturedBinding, Object syntheticArguments) throws Exception {
        if (syntheticByCapturedBinding == null || !(syntheticArguments instanceof Object[])) {
            return;
        }
        for (Object outerLocal : (Object[]) syntheticArguments) {
            if (outerLocal == null) {
                continue;
            }
            Object capturedBinding = findField(outerLocal.getClass(), "actualOuterLocalVariable") != null
                    ? getFieldValue(outerLocal, "actualOuterLocalVariable")
                    : null;
            if (capturedBinding != null) {
                syntheticByCapturedBinding.put(capturedBinding, outerLocal);
            }
        }
    }

    private static String describeScopeSyntheticChain(Object currentScope) {
        if (currentScope == null) {
            return "[]";
        }
        List<String> states = new ArrayList<>();
        Object scope = currentScope;
        for (int depth = 0; scope != null && depth < 8; depth++) {
            try {
                Object extraSyntheticArguments = findField(scope.getClass(), "extraSyntheticArguments") != null
                        ? getFieldValue(scope, "extraSyntheticArguments")
                        : null;
                Object referenceContext = findField(scope.getClass(), "referenceContext") != null
                        ? getFieldValue(scope, "referenceContext")
                        : null;
                Object lambdaOuterLocals = isLambdaExpression(referenceContext)
                        ? getFieldValue(referenceContext, "outerLocalVariables")
                        : null;
                states.add(describeScopeDebug(scope)
                        + "{extra=" + describeOuterLocalVariables(extraSyntheticArguments instanceof Object[] ? (Object[]) extraSyntheticArguments : new Object[0])
                        + ", lambdaOuter=" + describeOuterLocalVariables(lambdaOuterLocals instanceof Object[] ? (Object[]) lambdaOuterLocals : new Object[0])
                        + "}");
                scope = findField(scope.getClass(), "parent") != null ? getFieldValue(scope, "parent") : null;
            } catch (Exception e) {
                states.add("error=" + e.getClass().getName() + ": " + e.getMessage());
                break;
            }
        }
        return states.toString();
    }

    private static boolean isInterestingCapturedReference(String referenceName) {
        return Util.isDebugEnabled()
                && isSelectorEnabledForProperty(referenceName, "zircon.debug.capturedRefs");
    }

    private static boolean shouldLogLambdaRebind(Object lambdaExpression) {
        if (lambdaExpression == null) {
            return false;
        }
        try {
            Object body = getFieldValue(lambdaExpression, "body");
            return containsInterestingCapturedReference(body, new IdentityHashMap<>(), 0);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean containsInterestingCapturedReference(Object node, Map<Object, Boolean> visited, int depth) throws Exception {
        if (node == null || depth > 16 || visited.put(node, Boolean.TRUE) != null || !isAstNode(node)) {
            return false;
        }
        String simpleName = node.getClass().getSimpleName();
        if ("SingleNameReference".equals(simpleName) || "QualifiedNameReference".equals(simpleName)) {
            if (isInterestingCapturedReference(describeReferenceName(node, getFieldValue(node, "binding")))) {
                return true;
            }
        }
        for (Object child : getAstChildren(node)) {
            if (containsInterestingCapturedReference(child, visited, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static String describeReferenceName(Object node, Object binding) {
        try {
            Object token = findField(node.getClass(), "token") != null ? getFieldValue(node, "token") : null;
            if (token instanceof char[]) {
                return new String((char[]) token);
            }
            Object tokens = findField(node.getClass(), "tokens") != null ? getFieldValue(node, "tokens") : null;
            if (tokens instanceof char[][] && ((char[][]) tokens).length > 0) {
                char[][] segments = (char[][]) tokens;
                StringBuilder builder = new StringBuilder();
                for (int index = 0; index < segments.length; index++) {
                    if (index > 0) {
                        builder.append('.');
                    }
                    builder.append(segments[index] == null ? "null" : new String(segments[index]));
                }
                return builder.toString();
            }
            Object name = binding == null ? null : getFieldValue(binding, "name");
            if (name instanceof char[]) {
                return new String((char[]) name);
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private static String describeCapturedSyntheticBindings(Map<Object, Object> syntheticByCapturedBinding) {
        if (syntheticByCapturedBinding == null || syntheticByCapturedBinding.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<Object, Object> entry : syntheticByCapturedBinding.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(describeBinding(entry.getKey()))
                    .append('#')
                    .append(describeIdentity(entry.getKey()))
                    .append(" -> ")
                    .append(describeBinding(entry.getValue()))
                    .append('#')
                    .append(describeIdentity(entry.getValue()));
        }
        builder.append(']');
        return builder.toString();
    }

    private static String describeIdentity(Object value) {
        return value == null ? "null" : Integer.toHexString(System.identityHashCode(value));
    }

    private static String describeLocalBindingState(Object binding) {
        if (binding == null) {
            return "null";
        }
        try {
            Object resolvedPosition = findField(binding.getClass(), "resolvedPosition") != null
                    ? getFieldValue(binding, "resolvedPosition")
                    : null;
            Object declaringScope = findField(binding.getClass(), "declaringScope") != null
                    ? getFieldValue(binding, "declaringScope")
                    : null;
            Object accessingScope = findField(binding.getClass(), "accessingScope") != null
                    ? getFieldValue(binding, "accessingScope")
                    : null;
            Object actualOuterLocalVariable = findField(binding.getClass(), "actualOuterLocalVariable") != null
                    ? getFieldValue(binding, "actualOuterLocalVariable")
                    : null;
            Object useFlag = findField(binding.getClass(), "useFlag") != null
                    ? getFieldValue(binding, "useFlag")
                    : null;
            Object type = findField(binding.getClass(), "type") != null
                    ? getFieldValue(binding, "type")
                    : null;
            String actualOuterState = "null";
            if (actualOuterLocalVariable != null) {
                Object actualOuterResolvedPosition = findField(actualOuterLocalVariable.getClass(), "resolvedPosition") != null
                        ? getFieldValue(actualOuterLocalVariable, "resolvedPosition")
                        : null;
                Object actualOuterDeclaringScope = findField(actualOuterLocalVariable.getClass(), "declaringScope") != null
                        ? getFieldValue(actualOuterLocalVariable, "declaringScope")
                        : null;
                actualOuterState = "{resolvedPosition=" + actualOuterResolvedPosition
                        + ", declaringScope=" + describeScopeDebug(actualOuterDeclaringScope)
                        + "}";
            }
            return "{resolvedPosition=" + resolvedPosition
                    + ", type=" + describeTypeDebug(type)
                    + ", declaringScope=" + describeScopeDebug(declaringScope)
                    + ", accessingScope=" + describeScopeDebug(accessingScope)
                    + ", actualOuter=" + describeBinding(actualOuterLocalVariable)
                    + "#" + describeIdentity(actualOuterLocalVariable)
                    + ", actualOuterState=" + actualOuterState
                    + ", useFlag=" + useFlag
                    + "}";
        } catch (Exception ignored) {
            return "{error}";
        }
    }

    private static String describeThrowableStack(Throwable throwable, int maxFrames) {
        if (throwable == null) {
            return "[]";
        }
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        if (stackTrace == null || stackTrace.length == 0 || maxFrames <= 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(stackTrace.length, maxFrames);
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            StackTraceElement frame = stackTrace[index];
            builder.append(frame.getClassName())
                    .append('#')
                    .append(frame.getMethodName())
                    .append(':')
                    .append(frame.getLineNumber());
        }
        if (stackTrace.length > limit) {
            builder.append(" | ...");
        }
        builder.append(']');
        return builder.toString();
    }

    private static String describeEmulationPath(Object path) {
        if (!(path instanceof Object[])) {
            return String.valueOf(path);
        }
        Object[] bindings = (Object[]) path;
        if (bindings.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < bindings.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            Object binding = bindings[index];
            builder.append(describeBinding(binding))
                    .append('#')
                    .append(describeIdentity(binding))
                    .append('/')
                    .append(describeLocalBindingState(binding));
        }
        builder.append(']');
        return builder.toString();
    }

    private static String describeAstNodeState(Object node, int depth) {
        if (node == null) {
            return "null";
        }
        if (!isAstNode(node) || depth < 0) {
            return String.valueOf(node);
        }
        try {
            StringBuilder builder = new StringBuilder(node.getClass().getSimpleName())
                    .append("{source=").append(describeSourceRange(node));
            if (findField(node.getClass(), "resolvedType") != null) {
                builder.append(", resolvedType=").append(describeTypeDebug(getFieldValue(node, "resolvedType")));
            }
            if (findField(node.getClass(), "expectedType") != null) {
                builder.append(", expectedType=").append(describeTypeDebug(getFieldValue(node, "expectedType")));
            }
            if (findField(node.getClass(), "binding") != null) {
                Object binding = getFieldValue(node, "binding");
                builder.append(", binding=").append(describeBinding(binding));
                if (isLocalVariableBinding(binding)) {
                    builder.append('/').append(describeLocalBindingState(binding));
                } else {
                    builder.append('/').append(describeMethodBindingDetailed(binding));
                }
            }
            if (findField(node.getClass(), "constant") != null) {
                builder.append(", constant=").append(describeConstantState(getFieldValue(node, "constant")));
            }
            if (findField(node.getClass(), "bits") != null) {
                builder.append(", bits=").append(getFieldValue(node, "bits"));
            }
            if (depth > 0) {
                if (findField(node.getClass(), "expression") != null) {
                    builder.append(", expression=").append(describeAstNodeState(getFieldValue(node, "expression"), depth - 1));
                }
                if (findField(node.getClass(), "type") != null) {
                    builder.append(", type=").append(describeAstNodeState(getFieldValue(node, "type"), depth - 1));
                }
            }
            builder.append('}');
            return builder.toString();
        } catch (Exception ignored) {
            return node.getClass().getSimpleName() + "{error}";
        }
    }

    private static String describeConstantState(Object constant) {
        if (constant == null) {
            return "null";
        }
        return constant.getClass().getSimpleName() + "#" + describeIdentity(constant) + ":" + constant;
    }

    private static void syncLambdaMethodBindingCaptures(Object lambdaExpression, Object binding) throws Exception {
        Object[] outerLocals = getFieldValue(lambdaExpression, "outerLocalVariables") instanceof Object[]
                ? (Object[]) getFieldValue(lambdaExpression, "outerLocalVariables")
                : new Object[0];
        if (outerLocals.length == 0) {
            return;
        }
        Object[] explicitArguments = getFieldValue(lambdaExpression, "arguments") instanceof Object[]
                ? (Object[]) getFieldValue(lambdaExpression, "arguments")
                : new Object[0];
        Object[] existingParameters = getFieldValue(binding, "parameters") instanceof Object[]
                ? (Object[]) getFieldValue(binding, "parameters")
                : emptyTypeBindingArray(binding);
        int expectedParameterCount = explicitArguments.length + outerLocals.length;
        if (existingParameters.length >= expectedParameterCount) {
            return;
        }
        Object sampleType = outerLocals[0] != null ? getFieldValue(outerLocals[0], "type") : null;
        Object[] rewrittenParameters = newTypedFieldArray(binding, "parameters", expectedParameterCount, sampleType);
        int index = 0;
        for (Object outerLocal : outerLocals) {
            rewrittenParameters[index++] = getFieldValue(outerLocal, "type");
        }
        for (Object existingParameter : existingParameters) {
            rewrittenParameters[index++] = existingParameter;
        }
        setFieldValue(binding, "parameters", rewrittenParameters);

        char[][] existingParameterNames = getFieldValue(binding, "parameterNames") instanceof char[][]
                ? (char[][]) getFieldValue(binding, "parameterNames")
                : new char[0][];
        char[][] rewrittenParameterNames = new char[expectedParameterCount][];
        index = 0;
        for (Object outerLocal : outerLocals) {
            Object name = getFieldValue(outerLocal, "name");
            rewrittenParameterNames[index++] = name instanceof char[] ? (char[]) name : null;
        }
        for (char[] existingParameterName : existingParameterNames) {
            if (index >= rewrittenParameterNames.length) {
                break;
            }
            rewrittenParameterNames[index++] = existingParameterName;
        }
        setFieldValue(binding, "parameterNames", rewrittenParameterNames);
    }

    private static void syncLambdaArgumentBindingTypes(Object lambdaExpression, Object binding) throws Exception {
        Object[] explicitArguments = getFieldValue(lambdaExpression, "arguments") instanceof Object[]
                ? (Object[]) getFieldValue(lambdaExpression, "arguments")
                : new Object[0];
        if (explicitArguments.length == 0) {
            return;
        }
        Object[] parameters = getFieldValue(binding, "parameters") instanceof Object[]
                ? (Object[]) getFieldValue(binding, "parameters")
                : new Object[0];
        Object[] outerLocals = getFieldValue(lambdaExpression, "outerLocalVariables") instanceof Object[]
                ? (Object[]) getFieldValue(lambdaExpression, "outerLocalVariables")
                : new Object[0];
        if (parameters.length < outerLocals.length + explicitArguments.length) {
            return;
        }
        for (int index = 0; index < explicitArguments.length; index++) {
            Object argument = explicitArguments[index];
            Object parameterType = parameters[outerLocals.length + index];
            if (argument == null || parameterType == null) {
                continue;
            }
            Object argumentBinding = getFieldValue(argument, "binding");
            String argumentName = describeReferenceName(argument, argumentBinding);
            if (argumentBinding != null && findField(argumentBinding.getClass(), "type") != null) {
                setFieldValue(argumentBinding, "type", parameterType);
            }
            Object reboundBinding = syncLambdaArgumentReferenceTypes(
                    getFieldValue(lambdaExpression, "body"),
                    argumentName,
                    parameterType,
                    new IdentityHashMap<>(),
                    0
            );
            if (argumentBinding == null && reboundBinding != null && findField(argument.getClass(), "binding") != null) {
                setFieldValue(argument, "binding", reboundBinding);
                argumentBinding = reboundBinding;
            }
            if (argumentBinding != null && findField(argumentBinding.getClass(), "type") != null) {
                setFieldValue(argumentBinding, "type", parameterType);
            }
            if (findField(argument.getClass(), "resolvedType") != null) {
                setFieldValue(argument, "resolvedType", parameterType);
            }
        }
    }

    private static Object syncLambdaArgumentReferenceTypes(
            Object node,
            String argumentName,
            Object parameterType,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null
                || argumentName == null
                || argumentName.isEmpty()
                || "unknown".equals(argumentName)
                || depth > 16
                || visited.put(node, Boolean.TRUE) != null
                || !isAstNode(node)) {
            return null;
        }
        if (depth > 0 && isLambdaExpression(node)) {
            return null;
        }
        Object matchedBinding = null;
        String simpleName = node.getClass().getSimpleName();
        if ("SingleNameReference".equals(simpleName) || "QualifiedNameReference".equals(simpleName)) {
            Object binding = getFieldValue(node, "binding");
            if (isLocalVariableBinding(binding) && argumentName.equals(describeReferenceName(node, binding))) {
                if (findField(binding.getClass(), "type") != null) {
                    setFieldValue(binding, "type", parameterType);
                }
                if (findField(node.getClass(), "resolvedType") != null) {
                    setFieldValue(node, "resolvedType", parameterType);
                }
                if (findField(node.getClass(), "constant") != null) {
                    Object notAConstant = getNotAConstant(node);
                    if (notAConstant != null) {
                        setFieldValue(node, "constant", notAConstant);
                    }
                }
                normalizeLocalReferenceBitsForCodegen(node);
                matchedBinding = binding;
            }
        }
        for (Object child : getAstChildren(node)) {
            Object childBinding = syncLambdaArgumentReferenceTypes(child, argumentName, parameterType, visited, depth + 1);
            if (matchedBinding == null && childBinding != null) {
                matchedBinding = childBinding;
            }
        }
        return matchedBinding;
    }

    private static void rebindLambdaArgumentReferences(Object lambdaExpression) throws Exception {
        if (!isLambdaExpression(lambdaExpression)) {
            return;
        }
        Object[] explicitArguments = getFieldValue(lambdaExpression, "arguments") instanceof Object[]
                ? (Object[]) getFieldValue(lambdaExpression, "arguments")
                : new Object[0];
        if (explicitArguments.length == 0) {
            return;
        }
        Map<String, Object> argumentBindingsByName = new LinkedHashMap<>();
        for (Object argument : explicitArguments) {
            Object binding = argument == null ? null : getFieldValue(argument, "binding");
            String argumentName = argument == null ? "" : describeReferenceName(argument, binding);
            if (binding == null || argumentName.isEmpty() || "unknown".equals(argumentName)) {
                continue;
            }
            argumentBindingsByName.putIfAbsent(argumentName, binding);
        }
        if (argumentBindingsByName.isEmpty()) {
            return;
        }
        rebindLambdaArgumentReferences(
                getFieldValue(lambdaExpression, "body"),
                argumentBindingsByName,
                new IdentityHashMap<>(),
                0
        );
    }

    private static void rebindLambdaArgumentReferences(
            Object node,
            Map<String, Object> argumentBindingsByName,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 16 || visited.put(node, Boolean.TRUE) != null || !isAstNode(node)) {
            return;
        }
        if (depth > 0 && isLambdaExpression(node)) {
            return;
        }
        String simpleName = node.getClass().getSimpleName();
        if ("SingleNameReference".equals(simpleName) || "QualifiedNameReference".equals(simpleName)) {
            Object binding = getFieldValue(node, "binding");
            String referenceName = describeReferenceName(node, binding);
            Object canonicalBinding = argumentBindingsByName.get(referenceName);
            if (canonicalBinding != null) {
                if (canonicalBinding != binding) {
                    setFieldValue(node, "binding", canonicalBinding);
                }
                Object canonicalType = getFieldValue(canonicalBinding, "type");
                if (canonicalType != null && findField(node.getClass(), "resolvedType") != null) {
                    setFieldValue(node, "resolvedType", canonicalType);
                }
                if (findField(node.getClass(), "constant") != null) {
                    Object notAConstant = getNotAConstant(node);
                    if (notAConstant != null) {
                        setFieldValue(node, "constant", notAConstant);
                    }
                }
                normalizeLocalReferenceBitsForCodegen(node);
            }
        }
        for (Object child : getAstChildren(node)) {
            rebindLambdaArgumentReferences(child, argumentBindingsByName, visited, depth + 1);
        }
    }

    private static void normalizeLocalReferenceBitsForCodegen(Object node) throws Exception {
        Field bitsField = findField(node.getClass(), "bits");
        if (bitsField == null) {
            return;
        }
        Object bitsValue = getFieldValue(node, "bits");
        if (!(bitsValue instanceof Integer)) {
            return;
        }
        int bits = (Integer) bitsValue;
        int restrictiveFlagMask = 0x7;
        int bindingLocal = 0x2;
        if ((bits & restrictiveFlagMask) != bindingLocal) {
            setFieldValue(node, "bits", (bits & ~restrictiveFlagMask) | bindingLocal);
        }
    }

    private static void normalizeResolvedFunctionalAstState(
            Object node,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 16 || visited.put(node, Boolean.TRUE) != null || !isAstNode(node)) {
            return;
        }
        ensureExpressionConstantInitialized(node);
        for (Object child : getAstChildren(node)) {
            normalizeResolvedFunctionalAstState(child, visited, depth + 1);
        }
        String simpleName = node.getClass().getSimpleName();
        if ("SingleNameReference".equals(simpleName) || "QualifiedNameReference".equals(simpleName)) {
            normalizeLocalNameReferenceState(node);
        } else if ("MessageSend".equals(simpleName)) {
            normalizeMessageSendArgumentState(node);
        }
    }

    private static void ensureExpressionConstantInitialized(Object node) throws Exception {
        Field constantField = findField(node.getClass(), "constant");
        if (constantField == null) {
            return;
        }
        Object constant = getFieldValue(node, "constant");
        if (constant != null) {
            return;
        }
        Object notAConstant = getNotAConstant(node);
        if (notAConstant != null) {
            setFieldValue(node, "constant", notAConstant);
        }
    }

    private static void normalizeLocalNameReferenceState(Object node) throws Exception {
        Object binding = getFieldValue(node, "binding");
        if (!isLocalVariableBinding(binding)) {
            return;
        }
        Object bitsValue = getFieldValue(node, "bits");
        boolean hasFieldBit = bitsValue instanceof Integer && (((Integer) bitsValue) & 0x1) != 0;
        Object resolvedType = findField(node.getClass(), "resolvedType") != null
                ? getFieldValue(node, "resolvedType")
                : null;
        if (!hasFieldBit && resolvedType != null && !isProblem(resolvedType)) {
            return;
        }
        Object bindingType = getFieldValue(binding, "type");
        if (bindingType != null
                && !isProblem(bindingType)
                && findField(node.getClass(), "resolvedType") != null) {
            if (resolvedType == null || isProblem(resolvedType) || resolvedType != bindingType) {
                setFieldValue(node, "resolvedType", bindingType);
            }
        }
        if (findField(node.getClass(), "constant") != null) {
            Object notAConstant = getNotAConstant(node);
            if (notAConstant != null) {
                setFieldValue(node, "constant", notAConstant);
            }
        }
        normalizeLocalReferenceBitsForCodegen(node);
    }

    private static void normalizeMessageSendArgumentState(Object messageSend) throws Exception {
        if (messageSend == null || !"MessageSend".equals(messageSend.getClass().getSimpleName())) {
            return;
        }
        Object[] arguments = getFieldValue(messageSend, "arguments") instanceof Object[]
                ? (Object[]) getFieldValue(messageSend, "arguments")
                : new Object[0];
        Object[] argumentTypes = getFieldValue(messageSend, "argumentTypes") instanceof Object[]
                ? (Object[]) getFieldValue(messageSend, "argumentTypes")
                : new Object[0];
        Object binding = getFieldValue(messageSend, "binding");
        Object actualReceiverType = findField(messageSend.getClass(), "actualReceiverType") != null
                ? getFieldValue(messageSend, "actualReceiverType")
                : null;
        if (arguments.length == 0 || (binding == null && actualReceiverType == null && argumentTypes.length == 0)) {
            return;
        }
        Object[] parameters = binding != null && getFieldValue(binding, "parameters") instanceof Object[]
                ? (Object[]) getFieldValue(binding, "parameters")
                : new Object[0];
        int leadingSyntheticCount = Math.max(0, argumentTypes.length - arguments.length);
        boolean hiddenReceiverShape = actualReceiverType != null && leadingSyntheticCount == 1;
        boolean missingVisibleArgumentType = false;
        for (int index = 0; index < arguments.length; index++) {
            Object currentType = index < argumentTypes.length ? argumentTypes[index] : null;
            if (currentType == null || isProblem(currentType)) {
                Object resolvedArgumentType = resolveConcreteInvocationArgumentType(null, arguments[index]);
                if (resolvedArgumentType != null && !isProblem(resolvedArgumentType)) {
                    missingVisibleArgumentType = true;
                    break;
                }
            }
        }
        if (!hiddenReceiverShape && !missingVisibleArgumentType) {
            return;
        }
        Object[] repairedArgumentTypes = new Object[arguments.length];
        boolean changed = argumentTypes.length != arguments.length || hiddenReceiverShape;
        for (int index = 0; index < arguments.length; index++) {
            int sourceIndex = hiddenReceiverShape ? index + leadingSyntheticCount : index;
            Object repairedType = sourceIndex < argumentTypes.length
                    ? argumentTypes[sourceIndex]
                    : index < argumentTypes.length
                    ? argumentTypes[index]
                    : null;
            repairedType = resolveConcreteInvocationArgumentType(repairedType, arguments[index]);
            if ((repairedType == null || isProblem(repairedType)) && sourceIndex < parameters.length) {
                repairedType = resolveConcreteInvocationArgumentType(parameters[sourceIndex], arguments[index]);
            }
            repairedArgumentTypes[index] = repairedType;
            Object currentType = index < argumentTypes.length ? argumentTypes[index] : null;
            if (!Objects.equals(currentType, repairedType)) {
                changed = true;
            }
        }
        if (changed && findField(messageSend.getClass(), "argumentTypes") != null) {
            setFieldValue(messageSend, "argumentTypes", repairedArgumentTypes);
        }
    }

    private static void normalizeExpressionConstantsForCodegen(
            Object node,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 16 || visited.put(node, Boolean.TRUE) != null || !isAstNode(node)) {
            return;
        }
        Field constantField = findField(node.getClass(), "constant");
        if (constantField != null) {
            Object constant = getFieldValue(node, "constant");
            if (constant == null) {
                Object notAConstant = getNotAConstant(node);
                if (notAConstant != null) {
                    setFieldValue(node, "constant", notAConstant);
                }
            }
        }
        if (depth > 0 && isLambdaExpression(node)) {
            return;
        }
        for (Object child : getAstChildren(node)) {
            normalizeExpressionConstantsForCodegen(child, visited, depth + 1);
        }
    }

    private static void normalizeCastTypeBindingsForCodegen(
            Object node,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 16 || visited.put(node, Boolean.TRUE) != null || !isAstNode(node)) {
            return;
        }
        if ("CastExpression".equals(node.getClass().getSimpleName())) {
            Object typeReference = getFieldValue(node, "type");
            if (typeReference != null && findField(typeReference.getClass(), "resolvedType") != null) {
                Object typeReferenceResolvedType = getFieldValue(typeReference, "resolvedType");
                if (typeReferenceResolvedType == null || isProblem(typeReferenceResolvedType)) {
                    Object castResolvedType = getFieldValue(node, "resolvedType");
                    if (castResolvedType == null || isProblem(castResolvedType)) {
                        castResolvedType = getFieldValue(node, "expectedType");
                    }
                    if (castResolvedType != null && !isProblem(castResolvedType)) {
                        setFieldValue(typeReference, "resolvedType", castResolvedType);
                    }
                }
            }
        }
        if (depth > 0 && isLambdaExpression(node)) {
            return;
        }
        for (Object child : getAstChildren(node)) {
            normalizeCastTypeBindingsForCodegen(child, visited, depth + 1);
        }
    }

    private static void normalizeLambdaLocalBindingsForCodegen(Object lambdaExpression) throws Exception {
        if (!isLambdaExpression(lambdaExpression)) {
            return;
        }
        Object scope = getFieldValue(lambdaExpression, "scope");
        if (scope == null) {
            return;
        }
        Object[] outerLocals = getFieldValue(lambdaExpression, "outerLocalVariables") instanceof Object[]
                ? (Object[]) getFieldValue(lambdaExpression, "outerLocalVariables")
                : new Object[0];
        Object[] explicitArguments = getFieldValue(lambdaExpression, "arguments") instanceof Object[]
                ? (Object[]) getFieldValue(lambdaExpression, "arguments")
                : new Object[0];
        boolean shouldCaptureInstance = Boolean.TRUE.equals(getFieldValue(lambdaExpression, "shouldCaptureInstance"));
        int resolvedPosition = shouldCaptureInstance ? 1 : 0;
        for (Object outerLocal : outerLocals) {
            resolvedPosition = normalizeLambdaLocalBinding(scope, outerLocal, resolvedPosition, true);
        }
        for (Object argument : explicitArguments) {
            Object argumentBinding = argument == null ? null : getFieldValue(argument, "binding");
            resolvedPosition = normalizeLambdaLocalBinding(scope, argumentBinding, resolvedPosition, false);
        }
        if (findField(scope.getClass(), "offset") != null) {
            Object offset = getFieldValue(scope, "offset");
            if (!(offset instanceof Integer) || ((Integer) offset) < resolvedPosition) {
                setFieldValue(scope, "offset", resolvedPosition);
            }
        }
        if (findField(scope.getClass(), "maxOffset") != null) {
            Object maxOffset = getFieldValue(scope, "maxOffset");
            if (!(maxOffset instanceof Integer) || ((Integer) maxOffset) < resolvedPosition) {
                setFieldValue(scope, "maxOffset", resolvedPosition);
            }
        }
    }

    private static int normalizeLambdaLocalBinding(Object scope, Object binding, int resolvedPosition, boolean syntheticOuter) throws Exception {
        if (binding == null) {
            return resolvedPosition;
        }
        if (findField(binding.getClass(), "declaringScope") != null) {
            setFieldValue(binding, "declaringScope", scope);
        }
        if (findField(binding.getClass(), "resolvedPosition") != null) {
            setFieldValue(binding, "resolvedPosition", resolvedPosition);
        }
        if (findField(binding.getClass(), "useFlag") != null) {
            Object useFlag = getFieldValue(binding, "useFlag");
            if (!(useFlag instanceof Integer) || ((Integer) useFlag) == 0) {
                setFieldValue(binding, "useFlag", 1);
            }
        }
        if (syntheticOuter && findField(binding.getClass(), "accessingScope") != null) {
            setFieldValue(binding, "accessingScope", null);
        }
        return resolvedPosition + computeLocalBindingSlotSize(binding);
    }

    private static void reconcileNestedLambdaActualOuterBindings(Object lambdaExpression) throws Exception {
        if (!isLambdaExpression(lambdaExpression)) {
            return;
        }
        Object enclosingLambda = findEnclosingLambdaExpression(lambdaExpression);
        if (!isLambdaExpression(enclosingLambda)) {
            return;
        }
        Map<String, Object> canonicalBindings = collectLambdaCanonicalBindings(enclosingLambda);
        if (canonicalBindings.isEmpty()) {
            return;
        }
        Object[] outerLocals = getFieldValue(lambdaExpression, "outerLocalVariables") instanceof Object[]
                ? (Object[]) getFieldValue(lambdaExpression, "outerLocalVariables")
                : new Object[0];
        for (Object outerLocal : outerLocals) {
            if (outerLocal == null || findField(outerLocal.getClass(), "actualOuterLocalVariable") == null) {
                continue;
            }
            Object actualOuter = getFieldValue(outerLocal, "actualOuterLocalVariable");
            Object canonical = findCanonicalLambdaBinding(actualOuter, canonicalBindings);
            if (canonical != null && canonical != actualOuter) {
                setFieldValue(outerLocal, "actualOuterLocalVariable", canonical);
            }
        }
    }

    private static Object findEnclosingLambdaExpression(Object lambdaExpression) throws Exception {
        if (!isLambdaExpression(lambdaExpression)) {
            return null;
        }
        Object scope = getFieldValue(lambdaExpression, "scope");
        Object parent = scope != null && findField(scope.getClass(), "parent") != null
                ? getFieldValue(scope, "parent")
                : null;
        for (int depth = 0; parent != null && depth < 8; depth++) {
            Object referenceContext = findField(parent.getClass(), "referenceContext") != null
                    ? getFieldValue(parent, "referenceContext")
                    : null;
            if (referenceContext != null && referenceContext != lambdaExpression && isLambdaExpression(referenceContext)) {
                return referenceContext;
            }
            parent = findField(parent.getClass(), "parent") != null ? getFieldValue(parent, "parent") : null;
        }
        return null;
    }

    private static Map<String, Object> collectLambdaCanonicalBindings(Object lambdaExpression) throws Exception {
        Map<String, Object> canonicalBindings = new LinkedHashMap<>();
        if (!isLambdaExpression(lambdaExpression)) {
            return canonicalBindings;
        }
        Object[] explicitArguments = getFieldValue(lambdaExpression, "arguments") instanceof Object[]
                ? (Object[]) getFieldValue(lambdaExpression, "arguments")
                : new Object[0];
        for (Object argument : explicitArguments) {
            Object binding = argument == null ? null : getFieldValue(argument, "binding");
            putCanonicalLambdaBinding(canonicalBindings, binding);
        }
        Object[] outerLocals = getFieldValue(lambdaExpression, "outerLocalVariables") instanceof Object[]
                ? (Object[]) getFieldValue(lambdaExpression, "outerLocalVariables")
                : new Object[0];
        for (Object outerLocal : outerLocals) {
            Object actualOuter = outerLocal != null && findField(outerLocal.getClass(), "actualOuterLocalVariable") != null
                    ? getFieldValue(outerLocal, "actualOuterLocalVariable")
                    : null;
            putCanonicalLambdaBinding(canonicalBindings, actualOuter != null ? actualOuter : outerLocal, outerLocal);
        }
        return canonicalBindings;
    }

    private static void putCanonicalLambdaBinding(Map<String, Object> canonicalBindings, Object binding) throws Exception {
        putCanonicalLambdaBinding(canonicalBindings, binding, binding);
    }

    private static void putCanonicalLambdaBinding(Map<String, Object> canonicalBindings, Object bindingKey, Object bindingValue) throws Exception {
        if (canonicalBindings == null || bindingKey == null || bindingValue == null) {
            return;
        }
        canonicalBindings.putIfAbsent(describeCanonicalLambdaBindingKey(bindingKey), bindingValue);
    }

    private static Object findCanonicalLambdaBinding(Object binding, Map<String, Object> canonicalBindings) throws Exception {
        if (binding == null || canonicalBindings == null || canonicalBindings.isEmpty()) {
            return null;
        }
        Object direct = canonicalBindings.get(describeCanonicalLambdaBindingKey(binding));
        return direct != null ? direct : null;
    }

    private static String describeCanonicalLambdaBindingKey(Object binding) throws Exception {
        if (binding == null) {
            return "null";
        }
        Object name = getFieldValue(binding, "name");
        return (name instanceof char[] ? new String((char[]) name) : String.valueOf(name))
                + "|"
                + getTypeSignatureDebug(getFieldValue(binding, "type"));
    }

    private static void normalizeReceiverBindingForGenerateCode(Object currentScope, Object binding) throws Exception {
        if (binding == null) {
            return;
        }
        Object methodScope = findNearestMethodScope(currentScope);
        if (methodScope == null) {
            methodScope = currentScope;
        }
        if (findField(binding.getClass(), "declaringScope") != null) {
            setFieldValue(binding, "declaringScope", methodScope);
        }
        if (findField(binding.getClass(), "accessingScope") != null) {
            setFieldValue(binding, "accessingScope", null);
        }
        int resolvedPosition = computeLambdaBindingResolvedPosition(methodScope, binding);
        if (resolvedPosition >= 0 && findField(binding.getClass(), "resolvedPosition") != null) {
            setFieldValue(binding, "resolvedPosition", resolvedPosition);
        }
        if (findField(binding.getClass(), "useFlag") != null) {
            Object useFlag = getFieldValue(binding, "useFlag");
            if (!(useFlag instanceof Integer) || ((Integer) useFlag) == 0) {
                setFieldValue(binding, "useFlag", 1);
            }
        }
    }

    private static Object findNearestMethodScope(Object currentScope) throws Exception {
        Object scope = currentScope;
        for (int depth = 0; scope != null && depth < 8; depth++) {
            if (scope.getClass().getName().endsWith("MethodScope")) {
                return scope;
            }
            scope = findField(scope.getClass(), "parent") != null ? getFieldValue(scope, "parent") : null;
        }
        return null;
    }

    private static int computeLambdaBindingResolvedPosition(Object methodScope, Object binding) throws Exception {
        if (methodScope == null || binding == null) {
            return -1;
        }
        Object referenceContext = findField(methodScope.getClass(), "referenceContext") != null
                ? getFieldValue(methodScope, "referenceContext")
                : null;
        if (!isLambdaExpression(referenceContext)) {
            Object existing = findField(binding.getClass(), "resolvedPosition") != null
                    ? getFieldValue(binding, "resolvedPosition")
                    : null;
            return existing instanceof Integer ? (Integer) existing : -1;
        }
        boolean shouldCaptureInstance = Boolean.TRUE.equals(getFieldValue(referenceContext, "shouldCaptureInstance"));
        int resolvedPosition = shouldCaptureInstance ? 1 : 0;
        Object[] outerLocals = getFieldValue(referenceContext, "outerLocalVariables") instanceof Object[]
                ? (Object[]) getFieldValue(referenceContext, "outerLocalVariables")
                : new Object[0];
        for (Object outerLocal : outerLocals) {
            if (outerLocal == binding) {
                return resolvedPosition;
            }
            resolvedPosition += computeLocalBindingSlotSize(outerLocal);
        }
        Object[] explicitArguments = getFieldValue(referenceContext, "arguments") instanceof Object[]
                ? (Object[]) getFieldValue(referenceContext, "arguments")
                : new Object[0];
        for (Object argument : explicitArguments) {
            Object argumentBinding = argument == null ? null : getFieldValue(argument, "binding");
            if (argumentBinding == binding) {
                return resolvedPosition;
            }
            resolvedPosition += computeLocalBindingSlotSize(argumentBinding);
        }
        Object existing = findField(binding.getClass(), "resolvedPosition") != null
                ? getFieldValue(binding, "resolvedPosition")
                : null;
        return existing instanceof Integer ? (Integer) existing : -1;
    }

    private static Object findVisibleBindingInLambdaScope(Object currentLambda, Object methodScope, Object binding) throws Exception {
        if (!isLambdaExpression(currentLambda) || binding == null) {
            return null;
        }
        try {
            Object syntheticBinding = invokeMethod(currentLambda, "getSyntheticArgument", binding);
            if (syntheticBinding != null) {
                return syntheticBinding;
            }
        } catch (Exception ignored) {
        }
        Object[] explicitArguments = getFieldValue(currentLambda, "arguments") instanceof Object[]
                ? (Object[]) getFieldValue(currentLambda, "arguments")
                : new Object[0];
        String bindingKey = describeCanonicalLambdaBindingKey(binding);
        for (Object argument : explicitArguments) {
            Object argumentBinding = argument == null ? null : getFieldValue(argument, "binding");
            if (argumentBinding != null && bindingKey.equals(describeCanonicalLambdaBindingKey(argumentBinding))) {
                return argumentBinding;
            }
        }
        Object[] outerLocals = getFieldValue(currentLambda, "outerLocalVariables") instanceof Object[]
                ? (Object[]) getFieldValue(currentLambda, "outerLocalVariables")
                : new Object[0];
        for (Object outerLocal : outerLocals) {
            if (outerLocal == null) {
                continue;
            }
            if (bindingKey.equals(describeCanonicalLambdaBindingKey(outerLocal))) {
                return outerLocal;
            }
            Object actualOuter = findField(outerLocal.getClass(), "actualOuterLocalVariable") != null
                    ? getFieldValue(outerLocal, "actualOuterLocalVariable")
                    : null;
            if (actualOuter != null && bindingKey.equals(describeCanonicalLambdaBindingKey(actualOuter))) {
                return outerLocal;
            }
        }
        Object declaringScope = findField(binding.getClass(), "declaringScope") != null
                ? getFieldValue(binding, "declaringScope")
                : null;
        if (declaringScope == null || methodScope == findNearestMethodScope(declaringScope)) {
            return binding;
        }
        return null;
    }

    private static boolean isSyntheticMethodBinding(Object binding) {
        return binding != null && binding.getClass().getName().endsWith("SyntheticMethodBinding");
    }

    private static void primeArgumentDescriptorState(Object[] arguments) throws Exception {
        if (arguments == null) {
            return;
        }
        for (Object argument : arguments) {
            if (argument == null) {
                continue;
            }
            Object descriptor = getFieldValue(argument, "descriptor");
            if (descriptor != null) {
                primeMethodBindingState(descriptor);
            }
        }
    }

    private static boolean shouldRefreshFunctionalArgument(
            Object scope,
            Object expectedType,
            Object invocationSite,
            Object binding,
            int parameterIndex
    ) throws Exception {
        if (scope == null || expectedType == null) {
            return false;
        }
        Object descriptor = invokeMethod(expectedType, "getSingleAbstractMethod", scope, true);
        if (descriptor == null || isProblem(descriptor)) {
            return false;
        }
        Object[] descriptorParameters = (Object[]) getFieldValue(descriptor, "parameters");
        if (descriptorParameters == null) {
            return false;
        }
        return descriptorParameters.length > 0
                || shouldRefreshZeroArityFunctionalArgument(invocationSite, binding, parameterIndex);
    }

    private static boolean shouldTraceSelector(String methodName) {
        return Util.isTraceEnabled() && isSelectorEnabledForProperty(methodName, TRACE_SELECTORS_PROPERTY);
    }

    private static boolean shouldDebugFacadeSelector(String methodName) {
        return Util.isDebugEnabled() && isSelectorEnabledForProperty(methodName, DEBUG_SELECTORS_PROPERTY);
    }

    private static boolean shouldDebugClassTargetSelector(String methodName) {
        return shouldDebugFacadeSelector(methodName);
    }

    private static boolean isSelectorEnabledForProperty(String methodName, String propertyName) {
        if (methodName == null || methodName.isEmpty() || propertyName == null || propertyName.isEmpty()) {
            return false;
        }
        String configured = System.getProperty(propertyName, "").trim();
        if (configured.isEmpty()) {
            return false;
        }
        for (String token : configured.split("[,;\\s]+")) {
            String candidate = token == null ? "" : token.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if ("*".equals(candidate) || methodName.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProblemFileLoggingEnabled(String fileName) {
        return Util.isDebugEnabled()
                && fileName != null
                && isSelectorEnabledForProperty(fileName, DEBUG_PROBLEM_FILES_PROPERTY);
    }

    private static boolean shouldDeferFacadeCompatibilityForInvocation(Object invocationSite) throws Exception {
        return invocationSite != null && hasNestedLambdaArgument(invocationSite);
    }

    private static boolean hasFunctionalInvocationArgument(Object invocationSite) throws Exception {
        Object rawArguments = invocationSite == null ? null : getFieldValue(invocationSite, "arguments");
        if (!(rawArguments instanceof Object[])) {
            return false;
        }
        for (Object argument : (Object[]) rawArguments) {
            if (isFunctionalInvocationArgument(argument)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldRefreshZeroArityFunctionalArgument(
            Object invocationSite,
            Object binding,
            int parameterIndex
    ) throws Exception {
        if (invocationSite == null || binding == null || parameterIndex < 0) {
            return false;
        }
        Object rawArguments = getFieldValue(invocationSite, "arguments");
        if (!(rawArguments instanceof Object[]) || parameterIndex >= ((Object[]) rawArguments).length) {
            return false;
        }
        Object argument = ((Object[]) rawArguments)[parameterIndex];
        if (!isFunctionalInvocationArgument(argument)) {
            return false;
        }
        Object effectiveBinding = resolveLinkedOriginalMethod(binding);
        if (effectiveBinding == null) {
            effectiveBinding = binding;
        }
        if (isImplicitInvocation(invocationSite)) {
            return canRewriteImplicitInvocationToStaticCall(effectiveBinding);
        }
        if (isSelectionInvocation(invocationSite)
                || isReferenceInvocation(invocationSite)
                || isTypeAccessInvocation(invocationSite)) {
            return false;
        }
        return canRewriteReceiverStyleInvocationToStaticCall(effectiveBinding);
    }

    private static boolean hasNestedLambdaArgument(Object invocationSite) throws Exception {
        Object rawArguments = invocationSite == null ? null : getFieldValue(invocationSite, "arguments");
        if (!(rawArguments instanceof Object[])) {
            return false;
        }
        for (Object argument : (Object[]) rawArguments) {
            if (containsNestedLambdaExpression(argument, false, new IdentityHashMap<>(), 0)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsNestedLambdaExpression(
            Object node,
            boolean seenLambda,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 16 || !isAstNode(node) || visited.put(node, Boolean.TRUE) != null) {
            return false;
        }
        boolean lambda = isLambdaExpression(node);
        if (lambda && seenLambda) {
            return true;
        }
        boolean nextSeenLambda = seenLambda || lambda;
        for (Object child : getAstChildren(node)) {
            if (containsNestedLambdaExpression(child, nextSeenLambda, visited, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canRewriteImplicitInvocationToStaticCall(Object binding) throws Exception {
        return binding != null
                && !hasExplicitExtensionTargets(binding)
                && hasReceiverParameter(binding);
    }

    private static boolean canRewriteReceiverStyleInvocationToStaticCall(Object binding) throws Exception {
        return binding != null
                && (hasExplicitExtensionTargets(binding) || hasReceiverParameter(binding));
    }

    private static boolean hasExplicitExtensionTargets(Object binding) throws Exception {
        if (binding == null) {
            return false;
        }
        if (!getAnnotationClassTargets(binding, "ex").isEmpty()) {
            return true;
        }
        Object originalMethod = resolveLinkedOriginalMethod(binding);
        return originalMethod != null && !getAnnotationClassTargets(originalMethod, "ex").isEmpty();
    }

    private static boolean hasReceiverParameter(Object binding) throws Exception {
        if (binding == null) {
            return false;
        }
        Object parameters = getFieldValue(binding, "parameters");
        return parameters instanceof Object[] && ((Object[]) parameters).length > 0;
    }

    private static boolean shouldSkipReceiverStyleStaticRewriteProbe(
            Object scope,
            Object originalMethod,
            Object compatibleBinding,
            Object invocationSite
    ) throws Exception {
        if (originalMethod == null || compatibleBinding == null || invocationSite == null) {
            return false;
        }
        if (isTypeAccessInvocation(invocationSite)) {
            return true;
        }
        Object[] parameters = getFieldValue(originalMethod, "parameters") instanceof Object[]
                ? (Object[]) getFieldValue(originalMethod, "parameters")
                : null;
        Object hiddenReceiverType = parameters != null && parameters.length > 0 ? parameters[0] : null;
        if (!isClassType(hiddenReceiverType)) {
            return false;
        }
        if (Util.getBooleanProperty("zircon.vscode", false)
                && scope != null
                && isAnonymousOrLocalContext(scope, null)
                && hasLambdaReferenceContext(scope)) {
            return true;
        }
        String bindingClassName = compatibleBinding.getClass().getSimpleName();
        return bindingClassName.contains("ParameterizedGenericMethodBinding");
    }

    private static boolean hasLambdaReferenceContext(Object scope) throws Exception {
        Object currentScope = scope;
        for (int depth = 0; depth < 8 && currentScope != null; depth++) {
            Object referenceContext = getFieldValue(currentScope, "referenceContext");
            if (isLambdaExpression(referenceContext)) {
                return true;
            }
            currentScope = getFieldValue(currentScope, "parent");
        }
        return false;
    }

    private static List<Object> collectCandidateTypes(Object compilationUnitScope) throws Exception {
        List<Object> cached = COMPILATION_UNIT_CANDIDATE_TYPES_CACHE.get(compilationUnitScope);
        if (cached != null) {
            return cached;
        }
        Set<Object> result = new LinkedHashSet<>();
        addTypesFromPackage(result, getFieldValue(compilationUnitScope, "fPackage"));

        Object[] imports = (Object[]) getFieldValue(compilationUnitScope, "imports");
        if (imports == null) {
            invokeMethod(compilationUnitScope, "faultInImports");
            imports = (Object[]) getFieldValue(compilationUnitScope, "imports");
        }
        if (imports != null) {
            for (Object importBinding : imports) {
                if (importBinding == null) {
                    continue;
                }
                Object resolvedImport = invokeMethod(importBinding, "getResolvedImport");
                if (resolvedImport == null) {
                    continue;
                }
                if (isInstanceOf(resolvedImport, REFERENCE_BINDING_CLASS)) {
                    result.add(resolvedImport);
                    continue;
                }
                if (resolvedImport.getClass().getName().endsWith("PackageBinding")) {
                    addTypesFromPackage(result, resolvedImport);
                }
            }
        }
        addTypesFromImportReferences(result, compilationUnitScope);
        List<Object> resolved = Collections.unmodifiableList(new ArrayList<>(result));
        synchronized (COMPILATION_UNIT_CANDIDATE_TYPES_CACHE) {
            List<Object> existing = COMPILATION_UNIT_CANDIDATE_TYPES_CACHE.get(compilationUnitScope);
            if (existing != null) {
                return existing;
            }
            COMPILATION_UNIT_CANDIDATE_TYPES_CACHE.put(compilationUnitScope, resolved);
        }
        return resolved;
    }

    private static void addTypesFromImportReferences(Set<Object> result, Object compilationUnitScope) throws Exception {
        Object referenceContext = getFieldValue(compilationUnitScope, "referenceContext");
        if (referenceContext == null) {
            return;
        }
        Object importReferences = getFieldValue(referenceContext, "imports");
        if (!(importReferences instanceof Object[])) {
            return;
        }
        for (Object importReference : (Object[]) importReferences) {
            if (importReference == null) {
                continue;
            }
            Object isStaticImport = invokeMethod(importReference, "isStatic");
            if (isStaticImport instanceof Boolean && (Boolean) isStaticImport) {
                continue;
            }
            Object trailingStarPosition = getFieldValue(importReference, "trailingStarPosition");
            if (trailingStarPosition instanceof Integer && ((Integer) trailingStarPosition) > 0) {
                continue;
            }
            Object importName = invokeMethod(importReference, "getImportName");
            if (!(importName instanceof char[][])) {
                continue;
            }
            String importNameString = toQualifiedName((char[][]) importName);
            Object environment = invokeMethod(compilationUnitScope, "environment");
            Object module = invokeMethod(compilationUnitScope, "module");
            char[][] importNameParts = (char[][]) importName;
            Object importedType = environment != null
                    ? invokeMethod(environment, "getType", importNameParts, module)
                    : null;
            if (importedType == null && environment != null) {
                importedType = invokeMethod(environment, "getType", (Object) importNameParts);
            }
            if (isProblem(importedType)) {
                importedType = null;
            }
            if (importedType == null && environment != null && importNameParts.length > 1) {
                char[][] packageName = Arrays.copyOf(importNameParts, importNameParts.length - 1);
                char[] simpleName = importNameParts[importNameParts.length - 1];
                Object packageBinding = invokeMethod(environment, "createPackage", (Object) packageName);
                if (packageBinding != null && !isProblem(packageBinding)) {
                    importedType = invokeMethod(environment, "askForType", packageBinding, simpleName, module);
                    if (isProblem(importedType)) {
                        importedType = null;
                    }
                    if (importedType == null) {
                        importedType = invokeMethod(packageBinding, "getType", simpleName, module);
                    }
                    if (isProblem(importedType)) {
                        importedType = null;
                    }
                    if (importedType == null) {
                        Object typeOrPackage = invokeMethod(packageBinding, "getTypeOrPackage", simpleName, module, false);
                        if (!isProblem(typeOrPackage) && isInstanceOf(typeOrPackage, REFERENCE_BINDING_CLASS)) {
                            importedType = typeOrPackage;
                        }
                    }
                }
            }
            if (importedType == null && environment != null) {
                importedType = invokeMethod(environment, "askForType", importNameParts, module);
            }
            if (isProblem(importedType)) {
                importedType = null;
            }
            if (importedType == null) {
                Object importBinding = invokeMethod(compilationUnitScope, "getImport", importNameParts, false, 0);
                if (importBinding != null && !isProblem(importBinding)) {
                    Object resolvedImport = invokeMethod(importBinding, "getResolvedImport");
                    importedType = resolvedImport != null ? resolvedImport : importBinding;
                }
            }
            if (isProblem(importedType)) {
                importedType = null;
            }
            if (importedType == null) {
                importedType = invokeMethod(compilationUnitScope, "getType", importNameParts, importNameParts.length);
            }
            if (isProblem(importedType)) {
                continue;
            }
            Object referenceBinding = asReferenceBinding(importedType);
            referenceBinding = realizeBinaryType(referenceBinding, environment, importNameParts);
            if (referenceBinding != null) {
                result.add(referenceBinding);
            }
        }
    }

    private static void addTypesFromPackage(Set<Object> result, Object packageBinding) throws Exception {
        if (packageBinding == null) {
            return;
        }
        Object knownTypes = getFieldValue(packageBinding, "knownTypes");
        if (knownTypes == null) {
            return;
        }
        Object[] valueTable = (Object[]) getFieldValue(knownTypes, "valueTable");
        if (valueTable == null) {
            return;
        }
        for (Object value : valueTable) {
            if (value != null && isInstanceOf(value, REFERENCE_BINDING_CLASS)) {
                result.add(value);
            }
        }
    }

    private static List<CandidateBinding> collectExtensionCandidates(
            Object compilationUnitScope,
            String methodName,
            Object receiverType,
            Object scope,
            Object invocationSite
    ) throws Exception {
        List<CandidateBinding> candidates = new ArrayList<>();
        for (Object methodBinding : collectPotentialExtensionMethods(compilationUnitScope, methodName, scope)) {
            if (!matchesExtensionCandidate(methodBinding, receiverType, scope)) {
                continue;
            }
            CandidateBinding facade = createCandidateBinding(methodBinding, methodBinding, receiverType, scope, invocationSite);
            if (facade != null) {
                candidates.add(facade);
            }
        }
        return candidates;
    }

    private static List<CandidateBinding> collectCompletionExtensionCandidates(
            Object completionEngine,
            Object compilationUnitScope,
            char[] completionToken,
            Object receiverType,
            Object scope,
            Object invocationSite
    ) throws Exception {
        String prefix = completionToken == null ? "" : new String(completionToken);
        List<CandidateBinding> candidates = new ArrayList<>();
        Set<String> seenBindings = new LinkedHashSet<>();
        boolean directInvocation = isDirectCompletionInvocation(invocationSite);
        for (Object candidateType : collectCandidateTypes(compilationUnitScope)) {
            Object holderType = normalizeHolderType(candidateType, scope);
            if (holderType == null || isProblem(holderType)) {
                continue;
            }
            Object resolvedMethods = invokeMethod(holderType, "availableMethods");
            if (!(resolvedMethods instanceof Object[])) {
                resolvedMethods = invokeMethod(holderType, "methods");
            }
            if (!(resolvedMethods instanceof Object[])) {
                continue;
            }
            for (Object methodBinding : (Object[]) resolvedMethods) {
                String selector = getSelectorName(methodBinding);
                if (!matchesCompletionPrefix(selector, prefix)) {
                    continue;
                }
                addCompletionExtensionCandidate(
                        methodBinding,
                        receiverType,
                        scope,
                        candidates,
                        seenBindings,
                        false,
                        directInvocation
                );
            }
        }

        // Imported/package bindings above keep the common path immediate. The
        // global completion path consumes JDT's persisted annotation-reference
        // index and hydrates only @ExMethod declaring types.
        for (JdtIndexedExtensionMethod indexed : getJdtIndexedExtensionMethods(completionEngine)) {
            if (!matchesCompletionPrefix(indexed.selector, prefix)) {
                continue;
            }
            Object holderType = resolveTypeBindingByName(compilationUnitScope, indexed.ownerQualifiedName);
            holderType = normalizeHolderType(holderType, scope);
            if (holderType == null || isProblem(holderType)) {
                if (Util.isTraceEnabled()) {
                    Util.log("[ZirconCore] indexed completion holder unresolved: owner="
                            + indexed.ownerQualifiedName + ", selector=" + indexed.selector);
                }
                continue;
            }
            Object resolvedMethods = invokeMethod(holderType, "getMethods", indexed.selector.toCharArray());
            if (!(resolvedMethods instanceof Object[])) {
                if (Util.isTraceEnabled()) {
                    Util.log("[ZirconCore] indexed completion methods unavailable: owner="
                            + indexed.ownerQualifiedName + ", selector=" + indexed.selector
                            + ", holder=" + describeTypeDebug(holderType));
                }
                continue;
            }
            if (Util.isTraceEnabled()) {
                Util.log("[ZirconCore] indexed completion method hydration: owner="
                        + indexed.ownerQualifiedName + ", selector=" + indexed.selector
                        + ", methods=" + ((Object[]) resolvedMethods).length);
            }
            for (Object methodBinding : (Object[]) resolvedMethods) {
                addCompletionExtensionCandidate(
                        methodBinding,
                        receiverType,
                        scope,
                        candidates,
                        seenBindings,
                        true,
                        directInvocation
                );
            }
        }
        return candidates;
    }

    private static void addCompletionExtensionCandidate(
            Object methodBinding,
            Object receiverType,
            Object scope,
            List<CandidateBinding> candidates,
            Set<String> seenBindings,
            boolean requireImport,
            boolean directInvocation
    ) throws Exception {
        if (!isPotentialExtensionMethod(methodBinding)
                || !matchesExtensionCandidate(methodBinding, receiverType, scope)
                || (isDirectOnlyCompletionMethod(methodBinding) && !directInvocation)) {
            return;
        }
        CandidateBinding facade = createCandidateBinding(
                methodBinding,
                methodBinding,
                receiverType,
                scope,
                // CompletionOnMessageSendName does not expose the receiver AST
                // through the normal receiver field.
                null
        );
        if (facade == null) {
            return;
        }
        Object completionOwner = asReferenceBinding(receiverType);
        if (completionOwner != null) {
            setFieldValue(facade.binding, "declaringClass", completionOwner);
            setFieldValue(facade.binding, "receiver", completionOwner);
            resetMethodBindingCaches(facade.binding);
        }
        String key = getOwnerClassName(getFieldValue(methodBinding, "declaringClass"))
                + "#" + describeMethodBindingDetailed(methodBinding);
        if (seenBindings.add(key)) {
            facade.completionImportRequired = requireImport;
            candidates.add(facade);
        }
    }

    private static boolean isDirectCompletionInvocation(Object invocationSite) {
        if (invocationSite == null) {
            return false;
        }
        try {
            Object implicit = invokeOptionalMethod(invocationSite, "receiverIsImplicitThis");
            if (implicit instanceof Boolean && (Boolean) implicit) {
                return true;
            }
            Object receiver = getFieldValue(invocationSite, "receiver");
            if (receiver == null) {
                return invocationSite.getClass().getSimpleName().contains("SingleName");
            }
            Object receiverImplicit = invokeOptionalMethod(receiver, "isImplicitThis");
            return receiverImplicit instanceof Boolean && (Boolean) receiverImplicit;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isDirectOnlyCompletionMethod(Object methodBinding) throws Exception {
        return getAnnotationBooleanFlag(
                methodBinding,
                EX_METHOD_IDE_ANNOTATION,
                "shouldInvokeDirectly"
        );
    }

    private static Map<String, String> buildCompletionRequiredImports(List<CandidateBinding> candidates) {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, String> selectorOwners = new LinkedHashMap<>();
        Set<String> ambiguousSelectors = new LinkedHashSet<>();
        for (CandidateBinding candidate : candidates) {
            if (!candidate.completionImportRequired
                    || candidate.ownerClassName == null
                    || candidate.ownerClassName.isEmpty()) {
                continue;
            }
            try {
                String selector = getSelectorName(candidate.binding);
                Object signatureValue = invokeMethod(candidate.binding, "signature");
                String signature = signatureValue instanceof char[] ? new String((char[]) signatureValue) : "";
                if (!selector.isEmpty() && !signature.isEmpty()) {
                    result.put(selector + "#" + signature, candidate.ownerClassName);
                }
                String existingOwner = selectorOwners.putIfAbsent(selector, candidate.ownerClassName);
                if (existingOwner != null && !existingOwner.equals(candidate.ownerClassName)) {
                    ambiguousSelectors.add(selector);
                }
            } catch (Exception ignored) {
            }
        }
        for (Map.Entry<String, String> entry : selectorOwners.entrySet()) {
            if (!ambiguousSelectors.contains(entry.getKey())) {
                result.put("selector#" + entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /** Adds the declaring extension-container import to a native JDT proposal. */
    public static void attachJdtExtensionCompletionImport(Object proposal) {
        Map<String, String> requiredImports = COMPLETION_REQUIRED_IMPORTS.get();
        if (requiredImports == null || requiredImports.isEmpty() || proposal == null) {
            return;
        }
        try {
            Object kindValue = invokeMethod(proposal, "getKind");
            int kind = kindValue instanceof Integer ? (Integer) kindValue : -1;
            if (kind != 6 && kind != 24) {
                return;
            }
            Object nameValue = invokeMethod(proposal, "getName");
            Object signatureValue = invokeMethod(proposal, "getSignature");
            String selector = nameValue instanceof char[] ? new String((char[]) nameValue) : "";
            String signature = signatureValue instanceof char[] ? new String((char[]) signatureValue) : "";
            String ownerQualifiedName = requiredImports.get(selector + "#" + signature);
            if (ownerQualifiedName == null) {
                ownerQualifiedName = requiredImports.get("selector#" + selector);
            }
            if (ownerQualifiedName == null || ownerQualifiedName.isEmpty()) {
                return;
            }

            Class<?> proposalClass = proposal.getClass();
            Object completionLocationValue = invokeMethod(proposal, "getCompletionLocation");
            int completionLocation = completionLocationValue instanceof Integer
                    ? (Integer) completionLocationValue
                    : 0;
            Object requiredType = invokeStaticMethod(proposalClass, "create", 9, completionLocation);
            if (requiredType == null) {
                return;
            }
            String sourceQualifiedName = ownerQualifiedName.replace('$', '.');
            int lastDot = sourceQualifiedName.lastIndexOf('.');
            String packageName = lastDot >= 0 ? sourceQualifiedName.substring(0, lastDot) : "";
            String simpleName = lastDot >= 0 ? sourceQualifiedName.substring(lastDot + 1) : sourceQualifiedName;
            invokeMethod(requiredType, "setDeclarationSignature", packageName.toCharArray());
            invokeMethod(requiredType, "setPackageName", packageName.toCharArray());
            invokeMethod(requiredType, "setTypeName", simpleName.toCharArray());
            invokeMethod(requiredType, "setCompletion", sourceQualifiedName.toCharArray());
            invokeMethod(requiredType, "setName", simpleName.toCharArray());
            invokeMethod(requiredType, "setAdditionalFlags", ZIRCON_REQUIRED_IMPORT_PROPOSAL_FLAG);
            invokeMethod(
                    requiredType,
                    "setSignature",
                    ("L" + sourceQualifiedName + ";").toCharArray()
            );
            Object requiredArray = Array.newInstance(proposalClass, 1);
            Array.set(requiredArray, 0, requiredType);
            invokeMethod(proposal, "setRequiredProposals", requiredArray);
            if (Util.isTraceEnabled()) {
                Util.log("[ZirconCore] completion import attached: selector=" + selector
                        + ", owner=" + sourceQualifiedName);
            }
        } catch (Throwable error) {
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconCore] completion import attachment failed: "
                        + error.getClass().getName() + ": " + error.getMessage());
            }
        }
    }

    /** Removes the synthetic type replacement while retaining ImportRewrite's edit. */
    public static void sanitizeJdtExtensionCompletionEdits(Object proposal, Object completionItem) {
        if (proposal == null || completionItem == null) {
            return;
        }
        try {
            Object requiredValue = invokeMethod(proposal, "getRequiredProposals");
            if (!(requiredValue instanceof Object[])) {
                return;
            }
            Set<String> syntheticTypeTexts = new LinkedHashSet<>();
            for (Object required : (Object[]) requiredValue) {
                Object flagsValue = invokeMethod(required, "getAdditionalFlags");
                int flags = flagsValue instanceof Integer ? (Integer) flagsValue : 0;
                if ((flags & ZIRCON_REQUIRED_IMPORT_PROPOSAL_FLAG) == 0) {
                    continue;
                }
                Object nameValue = invokeMethod(required, "getName");
                if (nameValue instanceof char[]) {
                    syntheticTypeTexts.add(new String((char[]) nameValue));
                }
            }
            if (syntheticTypeTexts.isEmpty()) {
                return;
            }
            Object editsValue = invokeMethod(completionItem, "getAdditionalTextEdits");
            if (!(editsValue instanceof List<?>)) {
                return;
            }
            @SuppressWarnings("unchecked")
            List<Object> edits = (List<Object>) editsValue;
            edits.removeIf(edit -> {
                try {
                    Object newText = invokeMethod(edit, "getNewText");
                    return newText != null && syntheticTypeTexts.contains(String.valueOf(newText));
                } catch (Exception ignored) {
                    return false;
                }
            });
        } catch (Throwable error) {
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconCore] completion edit sanitization failed: "
                        + error.getClass().getName() + ": " + error.getMessage());
            }
        }
    }

    private static List<JdtIndexedExtensionMethod> getJdtIndexedExtensionMethods(Object completionEngine) {
        try {
            Object javaProject = getFieldValue(completionEngine, "javaProject");
            if (javaProject == null) {
                return Collections.emptyList();
            }
            long now = System.currentTimeMillis();
            long modificationStamp = readJavaProjectModificationStamp(javaProject);
            JdtExtensionIndexSnapshot snapshot = JDT_EXTENSION_INDEX_CACHE.get(javaProject);
            boolean fresh = snapshot != null
                    && now - snapshot.createdAtMillis < JDT_EXTENSION_INDEX_CACHE_MILLIS
                    && (modificationStamp < 0 || snapshot.projectModificationStamp == modificationStamp);
            if (!fresh) {
                CompletableFuture<JdtExtensionIndexSnapshot> refresh =
                        scheduleJdtExtensionIndexRefresh(javaProject, modificationStamp);
                if (snapshot == null && refresh != null) {
                    try {
                        snapshot = refresh.get(JDT_EXTENSION_INDEX_COLD_WAIT_MILLIS, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException ignored) {
                        // SearchEngine can be waiting for a workspace build.
                        // Keep it in the background rather than stalling JDT
                        // completion indefinitely.
                    } catch (Exception ignored) {
                    }
                }
            }
            return snapshot != null ? snapshot.methods : Collections.emptyList();
        } catch (Throwable error) {
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconCore] JDT extension index lookup failed: "
                        + error.getClass().getName() + ": " + error.getMessage());
            }
            return Collections.emptyList();
        }
    }

    private static CompletableFuture<JdtExtensionIndexSnapshot> scheduleJdtExtensionIndexRefresh(
            Object javaProject,
            long modificationStamp
    ) {
        final CompletableFuture<JdtExtensionIndexSnapshot> refresh;
        synchronized (JDT_EXTENSION_INDEX_REFRESHES) {
            CompletableFuture<JdtExtensionIndexSnapshot> running =
                    JDT_EXTENSION_INDEX_REFRESHES.get(javaProject);
            if (running != null && !running.isDone()) {
                return running;
            }
            refresh = new CompletableFuture<>();
            JDT_EXTENSION_INDEX_REFRESHES.put(javaProject, refresh);
        }
        JDT_EXTENSION_INDEX_EXECUTOR.execute(() -> {
            try {
                long startedAt = System.nanoTime();
                List<JdtIndexedExtensionMethod> methods = searchJdtExMethodIndex(javaProject);
                JdtExtensionIndexSnapshot snapshot = new JdtExtensionIndexSnapshot(
                        System.currentTimeMillis(),
                        modificationStamp,
                        methods
                );
                JDT_EXTENSION_INDEX_CACHE.put(javaProject, snapshot);
                refresh.complete(snapshot);
                if (Util.isTraceEnabled()) {
                    long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
                    Util.log("[ZirconCore] JDT @ExMethod index refreshed: project="
                            + describeJavaProject(javaProject)
                            + ", methods=" + methods.size()
                            + ", elapsedMs=" + elapsedMillis);
                    for (JdtIndexedExtensionMethod method : methods) {
                        Util.log("[ZirconCore]   indexed @ExMethod "
                                + method.ownerQualifiedName + "#" + method.selector);
                    }
                }
            } catch (Throwable error) {
                refresh.completeExceptionally(error);
                Util.log("[ZirconCore] JDT @ExMethod index refresh failed: "
                        + error.getClass().getName() + ": " + error.getMessage());
                if (Util.isTraceEnabled()) {
                    Util.log(Util.stackTrace(error));
                }
            } finally {
                synchronized (JDT_EXTENSION_INDEX_REFRESHES) {
                    if (JDT_EXTENSION_INDEX_REFRESHES.get(javaProject) == refresh) {
                        JDT_EXTENSION_INDEX_REFRESHES.remove(javaProject);
                    }
                }
            }
        });
        return refresh;
    }

    private static List<JdtIndexedExtensionMethod> searchJdtExMethodIndex(Object javaProject) throws Exception {
        Object annotationType = invokeMethod(javaProject, "findType", EX_METHOD_ANNOTATION);
        if (annotationType == null || !Boolean.TRUE.equals(invokeMethod(annotationType, "exists"))) {
            return Collections.emptyList();
        }

        Class<?> iJavaElementClass = loadClass("org.eclipse.jdt.core.IJavaElement", javaProject);
        Object projectElements = Array.newInstance(iJavaElementClass, 1);
        Array.set(projectElements, 0, javaProject);
        Class<?> searchEngineClass = loadClass("org.eclipse.jdt.core.search.SearchEngine", javaProject);
        // Source annotation references are persisted by JDT. System libraries
        // cannot depend on Zircon, so leave them outside the scope.
        Object searchScope = invokeStaticMethod(searchEngineClass, "createJavaSearchScope", projectElements, 11);
        if (searchScope == null) {
            return Collections.emptyList();
        }

        Class<?> constantsClass = loadClass("org.eclipse.jdt.core.search.IJavaSearchConstants", javaProject);
        int references = readStaticIntField(constantsClass, "REFERENCES", 1);
        Class<?> searchPatternClass = loadClass("org.eclipse.jdt.core.search.SearchPattern", javaProject);
        Object pattern = invokeStaticMethod(searchPatternClass, "createPattern", annotationType, references);
        if (pattern == null) {
            return Collections.emptyList();
        }

        Object participant = invokeStaticMethod(searchEngineClass, "getDefaultSearchParticipant");
        Class<?> participantClass = loadClass("org.eclipse.jdt.core.search.SearchParticipant", javaProject);
        Object participants = Array.newInstance(participantClass, 1);
        Array.set(participants, 0, participant);

        List<JdtIndexedExtensionMethod> collected = Collections.synchronizedList(new ArrayList<>());
        Class<?> requestorClass = ensureJdtSearchRequestorClass(javaProject);
        Object searchEngine = searchEngineClass.getDeclaredConstructor().newInstance();
        long requestId = JDT_SEARCH_REQUEST_IDS.incrementAndGet();
        JDT_SEARCH_RESULTS.put(requestId, collected);
        try {
            Object requestor = requestorClass.getDeclaredConstructor(long.class).newInstance(requestId);
            invokeMethod(searchEngine, "search", pattern, participants, searchScope, requestor, null);
        } finally {
            JDT_SEARCH_RESULTS.remove(requestId);
        }

        // JDT's annotation-reference index does not expose method annotations
        // from every binary jar. Search only application-library declarations
        // and inspect their IMethod annotation handles; this remains an indexed
        // SearchEngine query and never scans dependency files ourselves.
        Object applicationLibraryScope =
                invokeStaticMethod(searchEngineClass, "createJavaSearchScope", projectElements, 2);
        int methodKind = readStaticIntField(constantsClass, "METHOD", 1);
        int declarations = readStaticIntField(constantsClass, "DECLARATIONS", 0);
        int patternMatch = readStaticIntField(searchPatternClass, "R_PATTERN_MATCH", 1);
        Object binaryMethodPattern = invokeStaticMethod(
                searchPatternClass,
                "createPattern",
                "*",
                methodKind,
                declarations,
                patternMatch
        );
        if (applicationLibraryScope != null && binaryMethodPattern != null) {
            long binaryRequestId = JDT_SEARCH_REQUEST_IDS.incrementAndGet();
            JDT_SEARCH_RESULTS.put(binaryRequestId, collected);
            JDT_METHOD_DECLARATION_SEARCH_REQUESTS.add(binaryRequestId);
            try {
                Object requestor = requestorClass.getDeclaredConstructor(long.class).newInstance(binaryRequestId);
                invokeMethod(
                        searchEngine,
                        "search",
                        binaryMethodPattern,
                        participants,
                        applicationLibraryScope,
                        requestor,
                        null
                );
            } finally {
                JDT_METHOD_DECLARATION_SEARCH_REQUESTS.remove(binaryRequestId);
                JDT_SEARCH_RESULTS.remove(binaryRequestId);
            }
        }

        Map<String, JdtIndexedExtensionMethod> unique = new LinkedHashMap<>();
        synchronized (collected) {
            for (JdtIndexedExtensionMethod method : collected) {
                unique.put(method.key(), method);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(unique.values()));
    }

    /** Called by the requestor class defined inside JDT Core's OSGi class loader. */
    public static void acceptJdtExMethodSearchMatch(long requestId, Object searchMatch) {
        List<JdtIndexedExtensionMethod> results = JDT_SEARCH_RESULTS.get(requestId);
        if (results == null || searchMatch == null) {
            return;
        }
        try {
            boolean filterMethodDeclaration = JDT_METHOD_DECLARATION_SEARCH_REQUESTS.contains(requestId);
            Object element = invokeMethod(searchMatch, "getElement");
            for (int depth = 0; depth < 8 && element != null; depth++) {
                Object declaringType = invokeMethod(element, "getDeclaringType");
                Object elementName = invokeMethod(element, "getElementName");
                if (declaringType != null && elementName != null) {
                    if (filterMethodDeclaration && !hasJdtModelExMethodAnnotation(element)) {
                        return;
                    }
                    Object qualifiedName = invokeMethod(declaringType, "getFullyQualifiedName", '$');
                    if (qualifiedName == null) {
                        qualifiedName = invokeMethod(declaringType, "getFullyQualifiedName");
                    }
                    String owner = qualifiedName == null ? "" : String.valueOf(qualifiedName);
                    String selector = String.valueOf(elementName);
                    if (!owner.isEmpty() && !selector.isEmpty()) {
                        rememberJdtBinaryTypeLocation(element, owner);
                        results.add(new JdtIndexedExtensionMethod(owner, selector));
                    }
                    return;
                }
                element = invokeMethod(element, "getParent");
            }
        } catch (Throwable error) {
            if (Util.isTraceEnabled()) {
                Util.log("[ZirconCore] ignored malformed JDT annotation search match: "
                        + error.getClass().getName() + ": " + error.getMessage());
            }
        }
    }

    private static boolean hasJdtModelExMethodAnnotation(Object methodElement) {
        try {
            Object annotations = invokeMethod(methodElement, "getAnnotations");
            if (!(annotations instanceof Object[])) {
                return false;
            }
            for (Object annotation : (Object[]) annotations) {
                Object name = invokeMethod(annotation, "getElementName");
                String annotationName = name == null ? "" : String.valueOf(name);
                if ("ExMethod".equals(annotationName)
                        || EX_METHOD_ANNOTATION.equals(annotationName)
                        || annotationName.endsWith(".ExMethod")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void rememberJdtBinaryTypeLocation(Object methodElement, String ownerQualifiedName) {
        try {
            Object binary = invokeMethod(methodElement, "isBinary");
            if (!Boolean.TRUE.equals(binary)) {
                return;
            }
            // IJavaElement.PACKAGE_FRAGMENT_ROOT == 3. The returned IPath is
            // JDT's resolved classpath location, so no filesystem discovery is
            // needed for later classfile metadata hydration.
            Object root = invokeMethod(methodElement, "getAncestor", 3);
            Object rootPath = root == null ? null : invokeMethod(root, "getPath");
            Object osPath = rootPath == null ? null : invokeMethod(rootPath, "toOSString");
            String location = osPath == null ? "" : String.valueOf(osPath);
            Object resource = root == null ? null : invokeMethod(root, "getResource");
            Object resourceLocation = resource == null ? null : invokeMethod(resource, "getLocation");
            Object resourceOsPath =
                    resourceLocation == null ? null : invokeMethod(resourceLocation, "toOSString");
            if (resourceOsPath != null && !String.valueOf(resourceOsPath).isEmpty()) {
                location = String.valueOf(resourceOsPath);
            }
            if (!location.isEmpty()) {
                putBoundedConcurrentMap(JDT_BINARY_TYPE_LOCATIONS, ownerQualifiedName, location);
                JDT_APPLICATION_LIBRARY_LOCATIONS.add(location);
                if (Util.isTraceEnabled()) {
                    Util.log("[ZirconCore] JDT binary type location: owner="
                            + ownerQualifiedName + ", location=" + location);
                }
            }
        } catch (Exception ignored) {
            if (Util.isTraceEnabled()) {
                Util.log("[ZirconCore] JDT binary type location unavailable: owner="
                        + ownerQualifiedName + ", error="
                        + ignored.getClass().getName() + ": " + ignored.getMessage());
            }
        }
    }

    private static Class<?> ensureJdtSearchRequestorClass(Object anchor) throws Exception {
        Class<?> searchRequestorClass = loadClass("org.eclipse.jdt.core.search.SearchRequestor", anchor);
        ClassLoader targetLoader = searchRequestorClass.getClassLoader();
        Class<?> cached = JDT_SEARCH_REQUESTOR_CLASSES.get(targetLoader);
        if (cached != null) {
            return cached;
        }
        synchronized (JDT_SEARCH_REQUESTOR_CLASSES) {
            cached = JDT_SEARCH_REQUESTOR_CLASSES.get(targetLoader);
            if (cached != null) {
                return cached;
            }
            byte[] classBytes;
            try (InputStream input = ZirconCore.class.getResourceAsStream(JDT_SEARCH_REQUESTOR_RESOURCE)) {
                if (input == null) {
                    throw new IOException("missing bridge resource " + JDT_SEARCH_REQUESTOR_RESOURCE);
                }
                classBytes = readAllBytes(input);
            }
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                        searchRequestorClass,
                        MethodHandles.lookup()
                );
                cached = lookup.defineClass(classBytes);
            } catch (LinkageError alreadyDefined) {
                cached = Class.forName(JDT_SEARCH_REQUESTOR_CLASS, false, targetLoader);
            }
            JDT_SEARCH_REQUESTOR_CLASSES.put(targetLoader, cached);
            return cached;
        }
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        byte[] buffer = new byte[8_192];
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        for (int count = input.read(buffer); count >= 0; count = input.read(buffer)) {
            if (count > 0) {
                output.write(buffer, 0, count);
            }
        }
        return output.toByteArray();
    }

    private static long readJavaProjectModificationStamp(Object javaProject) {
        try {
            Object project = invokeMethod(javaProject, "getProject");
            Object stamp = project == null ? null : invokeMethod(project, "getModificationStamp");
            return stamp instanceof Long ? (Long) stamp : -1L;
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private static String describeJavaProject(Object javaProject) {
        try {
            Object name = invokeMethod(javaProject, "getElementName");
            return name == null ? javaProject.getClass().getSimpleName() : String.valueOf(name);
        } catch (Exception ignored) {
            return javaProject.getClass().getSimpleName();
        }
    }

    private static int readStaticIntField(Class<?> type, String fieldName, int fallback) {
        try {
            Field field = type.getField(fieldName);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean matchesCompletionPrefix(String selector, String prefix) {
        if (selector == null || selector.isEmpty() || prefix == null || prefix.isEmpty()) {
            return selector != null && !selector.isEmpty();
        }
        if (selector.regionMatches(true, 0, prefix, 0, Math.min(selector.length(), prefix.length()))) {
            return prefix.length() <= selector.length();
        }
        // JDT also supports camel-case and subsequence matching. Passing a slightly
        // broader candidate set lets findLocalMethods apply the configured JDT rules.
        int selectorIndex = 0;
        for (int prefixIndex = 0; prefixIndex < prefix.length() && selectorIndex < selector.length(); prefixIndex++) {
            char wanted = Character.toLowerCase(prefix.charAt(prefixIndex));
            while (selectorIndex < selector.length()
                    && Character.toLowerCase(selector.charAt(selectorIndex)) != wanted) {
                selectorIndex++;
            }
            if (selectorIndex >= selector.length()) {
                return false;
            }
            selectorIndex++;
        }
        return true;
    }

    private static List<Object> collectPotentialExtensionMethods(
            Object compilationUnitScope,
            String methodName,
            Object scope
    ) throws Exception {
        Map<String, List<Object>> selectorCache = getSelectorExtensionMethodCache(compilationUnitScope);
        List<Object> cached = selectorCache.get(methodName);
        if (cached != null) {
            return cached;
        }

        List<Object> methods = new ArrayList<>();
        for (Object candidateType : collectCandidateTypes(compilationUnitScope)) {
            Object holderType = normalizeHolderType(candidateType, scope);
            if (holderType == null || isProblem(holderType)) {
                continue;
            }
            Object resolvedMethods = invokeMethod(holderType, "getMethods", methodName.toCharArray());
            if (shouldTraceSelector(methodName)) {
                String holderName = getQualifiedTypeName(holderType);
                int methodCount = resolvedMethods instanceof Object[] ? ((Object[]) resolvedMethods).length : -1;
                Util.log("[ZirconCore] trace selector=" + methodName
                        + ", holder=" + holderName
                        + ", holderClass=" + holderType.getClass().getName()
                        + ", getMethods=" + methodCount);
            }
            if (!(resolvedMethods instanceof Object[])) {
                continue;
            }
            for (Object methodBinding : (Object[]) resolvedMethods) {
                if (isPotentialExtensionMethod(methodBinding)) {
                    methods.add(methodBinding);
                }
            }
        }

        List<Object> resolved = Collections.unmodifiableList(methods);
        List<Object> previous = selectorCache.putIfAbsent(methodName, resolved);
        return previous != null ? previous : resolved;
    }

    private static Map<String, List<Object>> getSelectorExtensionMethodCache(Object compilationUnitScope) {
        synchronized (SELECTOR_EXTENSION_METHOD_CACHE) {
            Map<String, List<Object>> selectorCache = SELECTOR_EXTENSION_METHOD_CACHE.get(compilationUnitScope);
            if (selectorCache != null) {
                return selectorCache;
            }
            Map<String, List<Object>> created = new ConcurrentHashMap<>();
            SELECTOR_EXTENSION_METHOD_CACHE.put(compilationUnitScope, created);
            return created;
        }
    }

    private static Object normalizeHolderType(Object holderType, Object scope) throws Exception {
        if (holderType == null || !"MissingTypeBinding".equals(holderType.getClass().getSimpleName())) {
            return holderType;
        }
        Object compoundName = getFieldValue(holderType, "compoundName");
        if (!(compoundName instanceof char[][])) {
            return holderType;
        }
        Object hydratedHolderType = resolveMissingTypeBinding(holderType, scope, (char[][]) compoundName);
        if (hydratedHolderType == null || isProblem(hydratedHolderType)) {
            hydratedHolderType = realizeBinaryType(
                    holderType,
                    getFieldValue(holderType, "environment"),
                    (char[][]) compoundName);
        }
        return hydratedHolderType != null ? hydratedHolderType : holderType;
    }

    private static Object resolveMissingTypeBinding(Object holderType, Object scope, char[][] compoundName) throws Exception {
        if (holderType == null || scope == null || compoundName == null || compoundName.length == 0) {
            return holderType;
        }
        Object environment = getFieldValue(holderType, "environment");
        if (environment == null) {
            environment = invokeMethod(scope, "environment");
        }
        if (environment == null) {
            return holderType;
        }
        Object resolved = invokeMethod(environment, "getResolvedType", (Object) compoundName, scope);
        if (isProblem(resolved)) {
            resolved = null;
        }
        if (resolved == null) {
            Object module = invokeMethod(scope, "module");
            resolved = invokeMethod(environment, "getResolvedType", (Object) compoundName, module, scope, true);
            if (isProblem(resolved)) {
                resolved = null;
            }
        }
        return resolved != null ? resolved : holderType;
    }

    private static boolean isPotentialExtensionMethod(Object methodBinding) throws Exception {
        if (methodBinding == null) {
            return false;
        }
        Object isStatic = invokeMethod(methodBinding, "isStatic");
        boolean staticMethod = isStatic instanceof Boolean && (Boolean) isStatic;
        if (!staticMethod) {
            return false;
        }
        boolean annotated = hasAnnotation(methodBinding, EX_METHOD_ANNOTATION);
        if (shouldTraceSelector(getSelectorName(methodBinding))) {
            Util.log("[ZirconCore] extension annotation check: selector="
                    + getSelectorName(methodBinding)
                    + ", annotated=" + annotated
                    + ", binding=" + describeMethodBindingDetailed(methodBinding));
        }
        return annotated;
    }

    private static boolean matchesExtensionCandidate(Object methodBinding, Object receiverType, Object scope) throws Exception {
        if (methodBinding == null) {
            return false;
        }
        String ownerClassName = getOwnerClassName(getFieldValue(methodBinding, "declaringClass"));
        boolean trace = shouldTraceSelector(getSelectorName(methodBinding));
        boolean matchedFilterAnnotations = matchesFilterAnnotations(methodBinding, receiverType);
        if (!matchedFilterAnnotations) {
            if (trace) {
                Util.log("[ZirconCore] trace selector=" + getSelectorName(methodBinding)
                        + ", rejected=filterAnnotation"
                        + ", binding=" + describeMethodBindingDetailed(methodBinding));
            }
            return false;
        }
        List<Object> explicitTargets = getAnnotationClassTargets(methodBinding, "ex");
        if (!explicitTargets.isEmpty()) {
            for (Object explicitTarget : explicitTargets) {
                if (isObjectType(explicitTarget) || isCompatibleReceiver(receiverType, explicitTarget, scope)) {
                    return true;
                }
            }
            if (trace) {
                Util.log("[ZirconCore] trace selector=" + getSelectorName(methodBinding)
                        + ", rejected=explicitTarget"
                        + ", receiver=" + describeTypeDebug(receiverType)
                        + ", explicitTargets=" + describeTypeArrayDetailed(explicitTargets.toArray()));
            }
            return false;
        }
        Object[] parameters = (Object[]) getFieldValue(methodBinding, "parameters");
        if (parameters == null || parameters.length == 0) {
            if (trace) {
                Util.log("[ZirconCore] trace selector=" + getSelectorName(methodBinding)
                        + ", rejected=noParameters"
                        + ", binding=" + describeMethodBindingDetailed(methodBinding));
            }
            return false;
        }
        Object hiddenReceiverType = parameters[0];
        if (hiddenReceiverType == null) {
            if (trace) {
                Util.log("[ZirconCore] trace selector=" + getSelectorName(methodBinding)
                        + ", rejected=nullHiddenReceiver"
                        + ", binding=" + describeMethodBindingDetailed(methodBinding));
            }
            return false;
        }
        if (isObjectType(hiddenReceiverType)) {
            return true;
        }
        boolean compatibleReceiver = isCompatibleReceiver(receiverType, hiddenReceiverType, scope);
        if (!compatibleReceiver && trace) {
            Util.log("[ZirconCore] trace selector=" + getSelectorName(methodBinding)
                    + ", rejected=receiverMismatch"
                    + ", receiver=" + describeTypeDebug(receiverType)
                    + ", hiddenReceiver=" + describeTypeDebug(hiddenReceiverType)
                    + ", binding=" + describeMethodBindingDetailed(methodBinding));
        }
        return compatibleReceiver;
    }

    private static CandidateBinding createCandidateBinding(
            Object resolvedMethod,
            Object metadataMethod,
            Object receiverType,
            Object scope,
            Object invocationSite
    ) throws Exception {
        String selectorName = getSelectorName(resolvedMethod);
        Object[] parameters = (Object[]) getFieldValue(resolvedMethod, "parameters");
        List<Object> explicitTargets = getAnnotationClassTargets(metadataMethod, "ex");
        boolean usesExplicitTargets = !explicitTargets.isEmpty();
        Object hiddenReceiverType = !usesExplicitTargets && parameters != null && parameters.length > 0 ? parameters[0] : null;
        boolean classReceiverStaticAccess = !usesExplicitTargets
                && isClassType(hiddenReceiverType)
                && isTypeAccessInvocation(invocationSite);
        Object selectedTargetType = usesExplicitTargets
                ? selectMostSpecificTarget(explicitTargets, receiverType, scope)
                : hiddenReceiverType;
        Object projectedReceiverType = usesExplicitTargets
                ? receiverType
                : projectReceiverType(receiverType, hiddenReceiverType, scope);
        Map<Object, Object> receiverSubstitutions = buildReceiverSubstitutions(hiddenReceiverType, projectedReceiverType);
        Object effectiveReceiverType = normalizeExtensionReceiverType(receiverType, hiddenReceiverType, scope);
        Object receiverReferenceBinding = asReferenceBinding(effectiveReceiverType);
        Object originalDeclaringClass = getFieldValue(metadataMethod, "declaringClass");
        if (receiverReferenceBinding == null) {
            receiverReferenceBinding = originalDeclaringClass;
        }
        if (receiverReferenceBinding == null) {
            return null;
        }

        Map<Object, Object> facadeSeedSubstitutions = new LinkedHashMap<>(receiverSubstitutions);
        facadeSeedSubstitutions.putAll(buildReceiverFunctionalWrapperSubstitutions(
                scope,
                resolvedMethod,
                hiddenReceiverType,
                effectiveReceiverType,
                parameters
        ));

        Class<?> facadeClass = loadClass(METHOD_BINDING_CLASS, resolvedMethod);
        java.lang.reflect.Constructor<?> facadeConstructor = facadeClass.getDeclaredConstructor();
        facadeConstructor.setAccessible(true);
        Object facade = facadeConstructor.newInstance();
        Object[] originalTypeVariables = (Object[]) getFieldValue(resolvedMethod, "typeVariables");
        FacadeTypeVariableState facadeTypeVariables = prepareFacadeTypeVariables(scope, facade, originalTypeVariables, facadeSeedSubstitutions);

        Object[] visibleParameters = usesExplicitTargets
                ? parameters
                : parameters.length <= 1
                ? emptyTypeBindingArray(resolvedMethod)
                : Arrays.copyOfRange(parameters, 1, parameters.length);
        visibleParameters = substituteTypeBindings(scope, visibleParameters, facadeTypeVariables.substitutions);
        Object returnType = substituteType(scope, getFieldValue(resolvedMethod, "returnType"), facadeTypeVariables.substitutions);
        Object[] thrownExceptions = substituteTypeBindings(scope, (Object[]) getFieldValue(resolvedMethod, "thrownExceptions"), facadeTypeVariables.substitutions);
        maybeRewriteImplicitClassReceiverInvocation(scope, receiverType, hiddenReceiverType, projectedReceiverType, invocationSite);
        boolean implicitInvocation = isImplicitInvocation(invocationSite);
        boolean explicitStaticNoArgInvocation = implicitInvocation
                && usesExplicitTargets
                && (parameters == null || parameters.length == 0);
        Object implicitClassFacadeOwner = implicitInvocation && isClassType(hiddenReceiverType)
                ? asReferenceBinding(receiverType)
                : null;
        Object facadeOwnerBinding = implicitClassFacadeOwner != null ? implicitClassFacadeOwner : receiverReferenceBinding;
        Object facadeDeclaringClass = (implicitInvocation && !explicitStaticNoArgInvocation)
                ? facadeOwnerBinding
                : (originalDeclaringClass != null ? originalDeclaringClass : receiverReferenceBinding);
        int originalModifiers = (Integer) getFieldValue(resolvedMethod, "modifiers");
        char[][] parameterNames = (char[][]) getFieldValue(resolvedMethod, "parameterNames");
        byte[] parameterFlowBits = (byte[]) getFieldValue(resolvedMethod, "parameterFlowBits");
        char[][] visibleParameterNames = trimVisibleParameterNames(parameterNames, usesExplicitTargets || classReceiverStaticAccess);
        byte[] visibleParameterFlowBits = trimVisibleParameterFlowBits(parameterFlowBits, usesExplicitTargets || classReceiverStaticAccess);
        setFieldValue(facade, "modifiers", (usesExplicitTargets || classReceiverStaticAccess) ? originalModifiers : (originalModifiers & ~8));
        setFieldValue(facade, "selector", getFieldValue(resolvedMethod, "selector"));
        setFieldValue(facade, "returnType", returnType);
        setFieldValue(facade, "parameters", visibleParameters);
        setFieldValue(facade, "thrownExceptions", thrownExceptions);
        setFieldValue(facade, "declaringClass", facadeDeclaringClass);
        setFieldValue(facade, "receiver", facadeOwnerBinding);
        setFieldValue(facade, "typeVariables", facadeTypeVariables.typeVariables);
        setFieldValue(facade, "parameterFlowBits", visibleParameterFlowBits);
        setFieldValue(facade, "defaultNullness", getFieldValue(resolvedMethod, "defaultNullness"));
        setFieldValue(facade, "parameterNames", visibleParameterNames);
        Object tagBits = getFieldValue(resolvedMethod, "tagBits");
        if (tagBits != null) {
            setFieldValue(facade, "tagBits", tagBits);
        }
        resetMethodBindingCaches(facade);
        if (shouldTraceSelector(selectorName) || shouldDebugFacadeSelector(selectorName)) {
            Util.log("[ZirconCore] trace selector=" + selectorName
                    + ", facadeCreated"
                    + ", resolved=" + describeMethodBindingDetailed(resolvedMethod)
                    + ", facade=" + describeMethodBindingDetailed(facade)
                    + ", implicitInvocation=" + implicitInvocation
                    + ", hiddenReceiver=" + describeTypeDebug(hiddenReceiverType)
                    + ", projectedReceiver=" + describeTypeDebug(projectedReceiverType)
                    + ", substitutions=" + describeSubstitutions(facadeTypeVariables.substitutions));
        }
        linkOriginalMethodIfSupported(facade, metadataMethod);
        return new CandidateBinding(
                facade,
                metadataMethod,
                selectedTargetType != null ? selectedTargetType : receiverReferenceBinding,
                getOwnerClassName(originalDeclaringClass),
                getAnnotationBooleanFlag(metadataMethod, "cover")
        );
    }

    private static void resetMethodBindingCaches(Object binding) throws Exception {
        setFieldValue(binding, "signature", null);
        setFieldValue(binding, "genericSignature", null);
        setFieldValue(binding, "uniqueKey", null);
    }

    private static char[][] trimVisibleParameterNames(char[][] parameterNames, boolean keepAllParameters) {
        if (parameterNames == null || keepAllParameters || parameterNames.length <= 1) {
            return parameterNames;
        }
        return Arrays.copyOfRange(parameterNames, 1, parameterNames.length);
    }

    private static byte[] trimVisibleParameterFlowBits(byte[] parameterFlowBits, boolean keepAllParameters) {
        if (parameterFlowBits == null || keepAllParameters || parameterFlowBits.length <= 1) {
            return parameterFlowBits;
        }
        return Arrays.copyOfRange(parameterFlowBits, 1, parameterFlowBits.length);
    }

    private static FacadeTypeVariableState prepareFacadeTypeVariables(
            Object scope,
            Object facade,
            Object[] originalTypeVariables,
            Map<Object, Object> seedSubstitutions
    ) throws Exception {
        if (originalTypeVariables == null || originalTypeVariables.length == 0) {
            return new FacadeTypeVariableState(originalTypeVariables, seedSubstitutions);
        }

        List<Object> visibleOriginals = new ArrayList<>(originalTypeVariables.length);
        for (Object originalTypeVariable : originalTypeVariables) {
            if (!isReceiverBoundTypeVariable(originalTypeVariable, seedSubstitutions)) {
                visibleOriginals.add(originalTypeVariable);
            }
        }

        Object[] copiedTypeVariables = newTypedArrayLike(originalTypeVariables, visibleOriginals.size());
        Map<Object, Object> substitutions = new LinkedHashMap<>(seedSubstitutions);
        if (visibleOriginals.isEmpty()) {
            return new FacadeTypeVariableState(copiedTypeVariables, substitutions);
        }

        Class<?> typeVariableClass = originalTypeVariables.getClass().getComponentType();
        Class<?> bindingClass = loadClass("org.eclipse.jdt.internal.compiler.lookup.Binding", facade);
        Object environment = invokeMethod(scope, "environment");
        java.lang.reflect.Constructor<?> constructor = findTypeVariableConstructor(typeVariableClass, bindingClass, environment);
        for (int index = 0; index < visibleOriginals.size(); index++) {
            Object originalTypeVariable = visibleOriginals.get(index);
            char[] sourceName = (char[]) getFieldValue(originalTypeVariable, "sourceName");
            Object copiedTypeVariable = instantiateTypeVariable(constructor, sourceName, facade, index, environment);
            copyTypeVariableMetadata(originalTypeVariable, copiedTypeVariable);
            copiedTypeVariables[index] = copiedTypeVariable;
            substitutions.put(originalTypeVariable, copiedTypeVariable);
        }

        for (int index = 0; index < visibleOriginals.size(); index++) {
            initializeCopiedTypeVariable(scope, visibleOriginals.get(index), copiedTypeVariables[index], substitutions);
        }
        return new FacadeTypeVariableState(copiedTypeVariables, substitutions);
    }

    private static java.lang.reflect.Constructor<?> findTypeVariableConstructor(
            Class<?> typeVariableClass,
            Class<?> bindingClass,
            Object environment
    ) throws Exception {
        Class<?> environmentClass = environment != null
                ? loadClass("org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment", environment)
                : null;
        if (environmentClass != null) {
            try {
                java.lang.reflect.Constructor<?> constructor = typeVariableClass.getDeclaredConstructor(char[].class, bindingClass, int.class, environmentClass);
                constructor.setAccessible(true);
                return constructor;
            } catch (NoSuchMethodException ignored) {
                // try legacy constructor below
            }
        }
        java.lang.reflect.Constructor<?> constructor = typeVariableClass.getDeclaredConstructor(char[].class, bindingClass, int.class);
        constructor.setAccessible(true);
        return constructor;
    }

    private static Object instantiateTypeVariable(
            java.lang.reflect.Constructor<?> constructor,
            char[] sourceName,
            Object declaringElement,
            int rank,
            Object environment
    ) throws Exception {
        if (constructor.getParameterCount() == 4) {
            return constructor.newInstance(sourceName, declaringElement, rank, environment);
        }
        return constructor.newInstance(sourceName, declaringElement, rank);
    }

    private static Object[] newTypedArrayLike(Object[] sourceArray, int size) {
        return (Object[]) Array.newInstance(sourceArray.getClass().getComponentType(), size);
    }

    private static void copyTypeVariableMetadata(Object source, Object target) throws Exception {
        Object modifiers = getFieldValue(source, "modifiers");
        if (modifiers != null) {
            setFieldValue(target, "modifiers", modifiers);
        }
        Object tagBits = getFieldValue(source, "tagBits");
        if (tagBits != null) {
            setFieldValue(target, "tagBits", tagBits);
        }
    }

    private static void initializeCopiedTypeVariable(
            Object scope,
            Object sourceTypeVariable,
            Object copiedTypeVariable,
            Map<Object, Object> substitutions
    ) throws Exception {
        Object superclass = substituteType(scope, getFieldValue(sourceTypeVariable, "superclass"), substitutions);
        Object[] superInterfaces = substituteTypeBindings(scope, (Object[]) getFieldValue(sourceTypeVariable, "superInterfaces"), substitutions);
        Object firstBound = substituteType(scope, getFieldValue(sourceTypeVariable, "firstBound"), substitutions);
        if (superclass != null) {
            setFieldValue(copiedTypeVariable, "superclass", superclass);
        }
        if (superInterfaces != null) {
            setFieldValue(copiedTypeVariable, "superInterfaces", superInterfaces);
        }
        if (firstBound != null) {
            setFieldValue(copiedTypeVariable, "firstBound", firstBound);
        }
    }

    private static boolean isReceiverBoundTypeVariable(Object typeVariable, Map<Object, Object> receiverSubstitutions) throws Exception {
        for (Object receiverTypeVariable : receiverSubstitutions.keySet()) {
            if (sameTypeVariable(typeVariable, receiverTypeVariable)) {
                return true;
            }
        }
        return false;
    }

    private static Map<Object, Object> buildReceiverFunctionalWrapperSubstitutions(
            Object scope,
            Object resolvedMethod,
            Object hiddenReceiverType,
            Object effectiveReceiverType,
            Object[] parameters
    ) throws Exception {
        Map<Object, Object> substitutions = new LinkedHashMap<>();
        if (scope == null
                || resolvedMethod == null
                || hiddenReceiverType == null
                || effectiveReceiverType == null
                || parameters == null
                || parameters.length != 2) {
            return substitutions;
        }
        Object templateBinding = resolveReceiverFunctionalWrapperTemplateBinding(scope, resolvedMethod);
        Object[] templateParameters = templateBinding != null && getFieldValue(templateBinding, "parameters") instanceof Object[]
                ? (Object[]) getFieldValue(templateBinding, "parameters")
                : parameters;
        Object templateHiddenReceiverType = templateParameters != null && templateParameters.length > 0 ? templateParameters[0] : hiddenReceiverType;
        String selectorName = getSelectorName(templateBinding != null ? templateBinding : resolvedMethod);
        if (!isObjectType(templateHiddenReceiverType)) {
            return substitutions;
        }
        Object visibleParameterType = templateParameters != null && templateParameters.length > 1 ? templateParameters[1] : null;
        Object returnType = getFieldValue(templateBinding != null ? templateBinding : resolvedMethod, "returnType");
        Object sharedTypeVariable = extractSharedSingleTypeVariableArgument(visibleParameterType, returnType);
        if (sharedTypeVariable == null) {
            return substitutions;
        }
        Object descriptor = invokeMethod(visibleParameterType, "getSingleAbstractMethod", scope, true);
        if (descriptor == null || isProblem(descriptor)) {
            return substitutions;
        }
        substitutions.put(sharedTypeVariable, effectiveReceiverType);
        if (shouldDebugFacadeSelector(selectorName)) {
            Util.log("[ZirconCore] debugReceiverWrapperSubstitution selector=" + selectorName
                    + ", hiddenReceiver=" + describeTypeDebug(templateHiddenReceiverType)
                    + ", effectiveReceiver=" + describeTypeDebug(effectiveReceiverType)
                    + ", visibleParameter=" + describeTypeDebug(visibleParameterType)
                    + ", returnType=" + describeTypeDebug(returnType)
                    + ", substitution=" + describeSubstitutions(substitutions));
        }
        return substitutions;
    }

    private static boolean isReceiverFunctionalWrapperBinding(Object scope, Object binding) throws Exception {
        return resolveReceiverFunctionalWrapperTemplateBinding(scope, binding) != null;
    }

    private static Object resolveReceiverFunctionalWrapperTemplateBinding(Object scope, Object binding) throws Exception {
        if (scope == null || binding == null) {
            return null;
        }
        Object[] parameters = getFieldValue(binding, "parameters") instanceof Object[]
                ? (Object[]) getFieldValue(binding, "parameters")
                : null;
        if (parameters == null || parameters.length != 2) {
            return null;
        }
        Object hiddenReceiverType = parameters[0];
        if (!isObjectType(hiddenReceiverType)) {
            return null;
        }
        Object visibleParameterType = parameters[1];
        Object returnType = getFieldValue(binding, "returnType");
        if (extractSharedSingleTypeVariableArgument(visibleParameterType, returnType) != null) {
            Object descriptor = invokeMethod(visibleParameterType, "getSingleAbstractMethod", scope, true);
            return descriptor != null && !isProblem(descriptor) ? binding : null;
        }
        Object originalBinding = resolveLinkedOriginalMethod(binding);
        if (originalBinding == null || originalBinding == binding) {
            return null;
        }
        return resolveReceiverFunctionalWrapperTemplateBinding(scope, originalBinding);
    }

    private static Object specializeFunctionalExpectedTypeForReceiverWrapper(
            Object scope,
            Object binding,
            Object invocationSite,
            Object expectedType,
            Object[] parameters
    ) throws Exception {
        if (scope == null
                || binding == null
                || invocationSite == null
                || expectedType == null
                || parameters == null
                || parameters.length < 2
                || !isReceiverFunctionalWrapperBinding(scope, binding)) {
            return expectedType;
        }
        Object actualReceiverType = resolveReceiverWrapperActualType(scope, invocationSite, parameters);
        if (actualReceiverType == null || isProblem(actualReceiverType)) {
            return expectedType;
        }
        Object templateBinding = resolveReceiverFunctionalWrapperTemplateBinding(scope, binding);
        Object[] templateParameters = templateBinding != null && getFieldValue(templateBinding, "parameters") instanceof Object[]
                ? (Object[]) getFieldValue(templateBinding, "parameters")
                : parameters;
        Object templateExpectedType = templateParameters != null && templateParameters.length > 1 && templateParameters[1] != null
                ? templateParameters[1]
                : expectedType;
        Map<Object, Object> substitutions = buildReceiverFunctionalWrapperSubstitutions(
                scope,
                binding,
                templateParameters != null && templateParameters.length > 0 ? templateParameters[0] : parameters[0],
                actualReceiverType,
                templateParameters != null ? templateParameters : parameters
        );
        if (substitutions.isEmpty()) {
            return expectedType;
        }
        Object specializedType = substituteType(scope, templateExpectedType, substitutions);
        return specializedType != null && !isProblem(specializedType) ? specializedType : expectedType;
    }

    private static Object resolveReceiverWrapperActualType(
            Object scope,
            Object invocationSite,
            Object[] parameters
    ) throws Exception {
        if (scope == null || invocationSite == null || parameters == null || parameters.length < 2) {
            return null;
        }
        Object actualReceiverType = findField(invocationSite.getClass(), "actualReceiverType") != null
                ? pickConcreteArgumentType(getFieldValue(invocationSite, "actualReceiverType"))
                : null;
        if (actualReceiverType != null) {
            return actualReceiverType;
        }
        Object recoveredReceiverType = pickConcreteArgumentType(resolveInvocationReceiverType(scope, invocationSite));
        if (recoveredReceiverType != null) {
            return recoveredReceiverType;
        }
        Object[] invocationArgumentTypes = resolveInvocationArgumentTypes(scope, invocationSite);
        if (invocationArgumentTypes != null && invocationArgumentTypes.length == parameters.length) {
            Object leadingArgumentType = pickConcreteArgumentType(invocationArgumentTypes[0]);
            if (leadingArgumentType != null) {
                return leadingArgumentType;
            }
        }
        Object[] arguments = getFieldValue(invocationSite, "arguments") instanceof Object[]
                ? (Object[]) getFieldValue(invocationSite, "arguments")
                : null;
        if (arguments != null && arguments.length == parameters.length && arguments.length > 0) {
            return resolveRecoverableInvocationArgumentType(
                    invocationArgumentTypes != null && invocationArgumentTypes.length > 0 ? invocationArgumentTypes[0] : null,
                    arguments[0]
            );
        }
        return null;
    }

    private static boolean hasInvocationReceiverExpression(Object invocationSite) throws Exception {
        return invocationSite != null
                && findField(invocationSite.getClass(), "receiver") != null
                && getFieldValue(invocationSite, "receiver") != null;
    }

    private static Object extractSharedSingleTypeVariableArgument(Object leftType, Object rightType) throws Exception {
        if (leftType == null || rightType == null || !isSameType(leftType, rightType)) {
            return null;
        }
        Object[] leftArguments = safeGetTypeArguments(leftType);
        Object[] rightArguments = safeGetTypeArguments(rightType);
        if (leftArguments == null || rightArguments == null || leftArguments.length != 1 || rightArguments.length != 1) {
            return null;
        }
        Object leftArgument = leftArguments[0];
        Object rightArgument = rightArguments[0];
        if (leftArgument == null || rightArgument == null) {
            return null;
        }
        if (!isTypeVariable(leftArgument) || !isTypeVariable(rightArgument) || !sameTypeVariable(leftArgument, rightArgument)) {
            return null;
        }
        return leftArgument;
    }

    private static CandidateBinding computeCompatibleCandidate(
            Object scope,
            CandidateBinding candidate,
            Object receiverType,
            Object[] argumentTypes,
            Object invocationSite
    ) throws Exception {
        return computeCompatibleCandidate(scope, candidate, receiverType, argumentTypes, invocationSite, false);
    }

    private static CandidateBinding computeCompatibleCandidate(
            Object scope,
            CandidateBinding candidate,
            Object receiverType,
            Object[] argumentTypes,
            Object invocationSite,
            boolean probeOnly
    ) throws Exception {
        if (probeOnly) {
            CandidateBinding facadeCompatible = computeCompatibleFacadeCandidate(
                    scope,
                    candidate,
                    receiverType,
                    argumentTypes,
                    invocationSite,
                    true
            );
            if (facadeCompatible != null) {
                return facadeCompatible;
            }
            try {
                Object[] invocationArgumentTypes = buildInvocationArgumentTypes(
                        candidate.originalMethod,
                        receiverType,
                        scope,
                        argumentTypes
                );
                Object compatible = invokeMethod(
                        scope,
                        "computeCompatibleMethod",
                        candidate.originalMethod,
                        invocationArgumentTypes,
                        invocationSite
                );
                if (compatible != null && !isProblem(compatible)) {
                    return new CandidateBinding(
                            compatible,
                            candidate.originalMethod,
                            candidate.targetType,
                            candidate.ownerClassName,
                            candidate.cover
                    );
                }
            } catch (Exception ignored) {
            }
            return null;
        }
        if (isSelectionInvocation(invocationSite)) {
            return computeSelectionCandidate(scope, candidate, receiverType, argumentTypes, invocationSite);
        }
        if (!isReferenceInvocation(invocationSite) && isImplicitInvocation(invocationSite)) {
            CandidateBinding rewrittenImplicit = computeImplicitStaticRewriteCandidate(
                    scope,
                    candidate,
                    receiverType,
                    argumentTypes,
                    invocationSite
            );
            if (rewrittenImplicit != null) {
                return rewrittenImplicit;
            }
        }
        CandidateBinding deferredReceiverRewrite = computeDeferredReceiverStaticRewriteCandidate(
                scope,
                candidate,
                receiverType,
                invocationSite
        );
        if (deferredReceiverRewrite != null) {
            return deferredReceiverRewrite;
        }
        if (isReferenceInvocation(invocationSite) || isImplicitInvocation(invocationSite)) {
            return computeCompatibleFacadeCandidate(scope, candidate, receiverType, argumentTypes, invocationSite, false);
        }
        if (shouldPreferFacadeCompatibility(scope, invocationSite, candidate.binding)) {
            try {
                CandidateBinding facadeCompatible = computeCompatibleFacadeCandidate(
                        scope,
                        candidate,
                        receiverType,
                        argumentTypes,
                        invocationSite,
                        false
                );
                if (facadeCompatible != null) {
                    return facadeCompatible;
                }
            } catch (Exception ignored) {
                // Nested lambda probing can still destabilize ECJ's poly expression state.
                // Fall back to original-method compatibility when that happens.
            }
        }

        try {
            Object[] invocationArgumentTypes = buildInvocationArgumentTypes(candidate.originalMethod, receiverType, scope, argumentTypes);
            Object compatible = invokeMethod(scope, "computeCompatibleMethod", candidate.originalMethod, invocationArgumentTypes, invocationSite);
            if (isProblem(compatible)) {
                return computeCompatibleFacadeCandidate(scope, candidate, receiverType, argumentTypes, invocationSite, false);
            }
            if (rewriteReceiverStyleInvocationToStaticCall(scope, invocationSite, compatible, receiverType)) {
                compatible = resynchronizeRewrittenStaticInvocation(scope, invocationSite, compatible);
                return new CandidateBinding(
                        compatible,
                        candidate.originalMethod,
                        candidate.targetType,
                        candidate.ownerClassName,
                        candidate.cover
                );
            }
            return createCandidateBinding(compatible, candidate.originalMethod, receiverType, scope, invocationSite);
        } catch (Exception ignored) {
            return computeCompatibleFacadeCandidate(scope, candidate, receiverType, argumentTypes, invocationSite, false);
        }
    }

    private static CandidateBinding computeImplicitStaticRewriteCandidate(
            Object scope,
            CandidateBinding candidate,
            Object receiverType,
            Object[] argumentTypes,
            Object invocationSite
    ) throws Exception {
        if (scope == null
                || candidate == null
                || candidate.originalMethod == null
                || !shouldAttemptImplicitStaticRewrite(candidate.originalMethod, invocationSite)) {
            return null;
        }
        Object[] invocationArgumentTypes = buildInvocationArgumentTypes(candidate.originalMethod, receiverType, scope, argumentTypes);
        Object compatible;
        try {
            compatible = invokeMethod(scope, "computeCompatibleMethod", candidate.originalMethod, invocationArgumentTypes, invocationSite);
        } catch (Exception ignored) {
            return null;
        }
        if (compatible == null || isProblem(compatible)) {
            return null;
        }
        if (!rewriteImplicitInvocationToStaticCall(scope, invocationSite, compatible, receiverType)) {
            return null;
        }
        compatible = resynchronizeRewrittenStaticInvocation(scope, invocationSite, compatible);
        return new CandidateBinding(
                compatible,
                candidate.originalMethod,
                candidate.targetType,
                candidate.ownerClassName,
                candidate.cover
        );
    }

    private static boolean shouldAttemptImplicitStaticRewrite(Object binding, Object invocationSite) throws Exception {
        return Util.getBooleanProperty("zircon.vscode", false)
                && binding != null
                && invocationSite != null
                && !isSelectionInvocation(invocationSite)
                && !isReferenceInvocation(invocationSite)
                && isImplicitInvocation(invocationSite)
                && hasFunctionalInvocationArgument(invocationSite)
                && canRewriteImplicitInvocationToStaticCall(binding);
    }

    private static CandidateBinding computeDeferredReceiverStaticRewriteCandidate(
            Object scope,
            CandidateBinding candidate,
            Object receiverType,
            Object invocationSite
    ) throws Exception {
        if (scope == null
                || candidate == null
                || candidate.originalMethod == null
                || !shouldAttemptDeferredReceiverStaticRewrite(candidate.originalMethod, invocationSite)) {
            return null;
        }
        InvocationRewriteState state = captureInvocationRewriteState(invocationSite);
        String selectorName = getSelectorName(candidate.originalMethod);
        try {
            if (!rewriteReceiverStyleInvocationToStaticCall(scope, invocationSite, candidate.originalMethod, receiverType)) {
                restoreInvocationRewriteState(invocationSite, state);
                return null;
            }
            Object rebound = resynchronizeRewrittenStaticInvocation(scope, invocationSite, candidate.originalMethod);
            if (rebound == null || isProblem(rebound)) {
                if (shouldDebugFacadeSelector(selectorName)) {
                    Util.log("[ZirconCore] debugDeferredRewrite selector=" + selectorName
                            + ", stage=initialResyncProblem"
                            + ", rebound=" + describeMethodBindingDetailed(rebound));
                }
                restoreInvocationRewriteState(invocationSite, state);
                return null;
            }
            rememberSuccessfulFunctionalWrapper(scope, rebound, invocationSite);
            refreshPostRewriteFunctionalArguments(scope, invocationSite, rebound);
            Object refreshedRebound = resynchronizeRewrittenStaticInvocation(scope, invocationSite, rebound);
            if (refreshedRebound != null && !isProblem(refreshedRebound)) {
                rebound = refreshedRebound;
            }
            if (shouldDebugFacadeSelector(selectorName)) {
                Util.log("[ZirconCore] debugDeferredRewrite selector=" + selectorName
                        + ", stage=success"
                        + ", rebound=" + describeMethodBindingDetailed(rebound)
                        + ", invocationArgumentTypes=" + describeTypeArrayDetailed(getFieldValue(invocationSite, "argumentTypes") instanceof Object[] ? (Object[]) getFieldValue(invocationSite, "argumentTypes") : new Object[0]));
            }
            rememberSuccessfulFunctionalWrapper(scope, rebound, invocationSite);
            return new CandidateBinding(
                    rebound,
                    candidate.originalMethod,
                    candidate.targetType,
                    candidate.ownerClassName,
                    candidate.cover
            );
        } catch (Exception e) {
            restoreInvocationRewriteState(invocationSite, state);
            throw e;
        }
    }

    private static boolean shouldAttemptDeferredReceiverStaticRewrite(Object binding, Object invocationSite) throws Exception {
        return Util.getBooleanProperty("zircon.vscode", false)
                && binding != null
                && invocationSite != null
                && !isSelectionInvocation(invocationSite)
                && !isReferenceInvocation(invocationSite)
                && !isImplicitInvocation(invocationSite)
                && !isTypeAccessInvocation(invocationSite)
                && canRewriteReceiverStyleInvocationToStaticCall(binding);
    }

    private static boolean shouldPreferFacadeCompatibility(Object scope, Object invocationSite, Object binding) throws Exception {
        if (scope == null || invocationSite == null || binding == null) {
            return false;
        }
        if (shouldDeferFacadeCompatibilityForInvocation(invocationSite)) {
            return false;
        }
        Object rawArguments = getFieldValue(invocationSite, "arguments");
        Object rawParameters = getFieldValue(binding, "parameters");
        if (!(rawArguments instanceof Object[]) || !(rawParameters instanceof Object[])) {
            return false;
        }
        Object[] arguments = (Object[]) rawArguments;
        Object[] parameters = (Object[]) rawParameters;
        for (int index = 0; index < arguments.length && index < parameters.length; index++) {
            if (arguments[index] == null || parameters[index] == null) {
                continue;
            }
            if (shouldRefreshFunctionalArgument(scope, parameters[index], invocationSite, binding, index)) {
                return true;
            }
        }
        return false;
    }

    private static boolean rewriteReceiverStyleInvocationToStaticCall(
            Object scope,
            Object invocationSite,
            Object compatibleBinding,
            Object receiverType
    ) throws Exception {
        if (scope == null
                || invocationSite == null
                || compatibleBinding == null
                || !Util.getBooleanProperty("zircon.vscode", false)
                || isSelectionInvocation(invocationSite)
                || isReferenceInvocation(invocationSite)
                || isImplicitInvocation(invocationSite)
                || isTypeAccessInvocation(invocationSite)
                || !invocationSite.getClass().getName().endsWith("MessageSend")) {
            return false;
        }

        Object receiverExpression = getFieldValue(invocationSite, "receiver");
        if (receiverExpression == null || isImplicitThisReference(receiverExpression)) {
            return false;
        }
        normalizeExpressionConstantsForCodegen(receiverExpression, new IdentityHashMap<>(), 0);

        String selectorName = getSelectorName(compatibleBinding);
        if (!canRewriteReceiverStyleInvocationToStaticCall(compatibleBinding)) {
            return false;
        }
        boolean explicitTargetInvocation = hasExplicitExtensionTargets(compatibleBinding);
        Object[] parameters = getFieldValue(compatibleBinding, "parameters") instanceof Object[]
                ? (Object[]) getFieldValue(compatibleBinding, "parameters")
                : new Object[0];

        Object rawArguments = getFieldValue(invocationSite, "arguments");
        Object[] originalArguments = rawArguments instanceof Object[] ? (Object[]) rawArguments : emptyExpressionArray(invocationSite);
        Object rawArgumentTypes = getFieldValue(invocationSite, "argumentTypes");
        Object[] originalArgumentTypes = rawArgumentTypes instanceof Object[]
                ? (Object[]) rawArgumentTypes
                : emptyTypeBindingArray(scope);
        Object[] rewrittenArguments;
        Object[] rewrittenArgumentTypes;
        Object preservedActualReceiverType = receiverType != null
                ? receiverType
                : resolveRewrittenInvocationActualReceiverType(
                        scope,
                        invocationSite,
                        receiverExpression,
                        null
                );
        Object rewrittenReceiverArgumentType = preservedActualReceiverType != null && !isProblem(preservedActualReceiverType)
                ? preservedActualReceiverType
                : resolveConcreteInvocationArgumentType(null, receiverExpression);
        if (explicitTargetInvocation) {
            rewrittenArguments = newTypedFieldArray(invocationSite, "arguments", originalArguments.length, receiverExpression);
            if (originalArguments.length > 0) {
                System.arraycopy(originalArguments, 0, rewrittenArguments, 0, originalArguments.length);
            }
            rewrittenArgumentTypes = newTypedFieldArray(invocationSite, "argumentTypes", originalArguments.length, receiverType);
            if (originalArgumentTypes.length > 0) {
                System.arraycopy(originalArgumentTypes, 0, rewrittenArgumentTypes, 0, Math.min(originalArgumentTypes.length, rewrittenArgumentTypes.length));
            }
        } else {
            rewrittenArguments = newTypedFieldArray(invocationSite, "arguments", originalArguments.length + 1, receiverExpression);
            rewrittenArguments[0] = receiverExpression;
            System.arraycopy(originalArguments, 0, rewrittenArguments, 1, originalArguments.length);
            rewrittenArgumentTypes = newTypedFieldArray(invocationSite, "argumentTypes", rewrittenArguments.length, receiverType);
            rewrittenArgumentTypes[0] = rewrittenReceiverArgumentType != null
                    ? rewrittenReceiverArgumentType
                    : receiverType != null
                    ? receiverType
                    : parameters[0];
            if (originalArgumentTypes.length > 0) {
                System.arraycopy(originalArgumentTypes, 0, rewrittenArgumentTypes, 1, Math.min(originalArgumentTypes.length, rewrittenArgumentTypes.length - 1));
            }
        }
        logReceiverRewriteIfGenericReceiverType(
                selectorName,
                invocationSite,
                receiverExpression,
                receiverType,
                preservedActualReceiverType,
                rewrittenArguments,
                rewrittenArgumentTypes,
                parameters
        );

        Object declaringClass = getFieldValue(compatibleBinding, "declaringClass");
        Object staticReceiver = createStaticReceiverTypeReference(scope, invocationSite, declaringClass);
        if (staticReceiver == null) {
            return false;
        }

        setFieldValue(invocationSite, "receiver", staticReceiver);
        setFieldValue(invocationSite, "arguments", rewrittenArguments);
        setFieldValue(invocationSite, "argumentTypes", rewrittenArgumentTypes);
        setFieldValue(invocationSite, "binding", compatibleBinding);
        setFieldValue(invocationSite, "actualReceiverType", preservedActualReceiverType);
        Object notAConstant = getNotAConstant(invocationSite);
        if (notAConstant != null) {
            setFieldValue(invocationSite, "constant", notAConstant);
        }
        Object returnType = getFieldValue(compatibleBinding, "returnType");
        if (returnType != null) {
            setFieldValue(invocationSite, "resolvedType", returnType);
        }
        if (shouldDebugFacadeSelector(selectorName)) {
            Util.log("[ZirconCore] staticRewrite selector=" + selectorName
                    + ", binding=" + describeMethodBindingDetailed(compatibleBinding)
                    + ", args=" + rewrittenArguments.length
                    + ", params=" + parameters.length
                    + ", receiverType=" + describeTypeDebug(receiverType)
                    + ", preservedReceiverType=" + describeTypeDebug(preservedActualReceiverType)
                    + ", rewrittenReceiverArgumentType=" + describeTypeDebug(rewrittenArgumentTypes.length > 0 ? rewrittenArgumentTypes[0] : null)
                    + ", storedActualReceiverType=" + describeTypeDebug(getFieldValue(invocationSite, "actualReceiverType"))
                    + ", declaringClass=" + describeTypeDebug(declaringClass));
        }
        refreshPostRewriteFunctionalArguments(scope, invocationSite, compatibleBinding);
        return true;
    }

    private static boolean rewriteImplicitInvocationToStaticCall(
            Object scope,
            Object invocationSite,
            Object compatibleBinding,
            Object receiverType
    ) throws Exception {
        if (scope == null
                || invocationSite == null
                || compatibleBinding == null
                || !Util.getBooleanProperty("zircon.vscode", false)
                || isSelectionInvocation(invocationSite)
                || isReferenceInvocation(invocationSite)
                || !isImplicitInvocation(invocationSite)
                || !invocationSite.getClass().getName().endsWith("MessageSend")
                || !canRewriteImplicitInvocationToStaticCall(compatibleBinding)) {
            return false;
        }
        String selectorName = getSelectorName(compatibleBinding);
        Object hiddenReceiverExpression = createExplicitThisPlaceholder(receiverType, invocationSite);
        if (hiddenReceiverExpression == null) {
            return false;
        }

        Object rawArguments = getFieldValue(invocationSite, "arguments");
        Object[] originalArguments = rawArguments instanceof Object[] ? (Object[]) rawArguments : emptyExpressionArray(invocationSite);
        Object[] rewrittenArguments = newTypedFieldArray(invocationSite, "arguments", originalArguments.length + 1, hiddenReceiverExpression);
        rewrittenArguments[0] = hiddenReceiverExpression;
        System.arraycopy(originalArguments, 0, rewrittenArguments, 1, originalArguments.length);

        Object rawArgumentTypes = getFieldValue(invocationSite, "argumentTypes");
        Object[] originalArgumentTypes = rawArgumentTypes instanceof Object[]
                ? (Object[]) rawArgumentTypes
                : emptyTypeBindingArray(scope);
        Object[] parameters = getFieldValue(compatibleBinding, "parameters") instanceof Object[]
                ? (Object[]) getFieldValue(compatibleBinding, "parameters")
                : new Object[0];
        Object[] rewrittenArgumentTypes = newTypedFieldArray(invocationSite, "argumentTypes", rewrittenArguments.length, receiverType);
        Object preservedActualReceiverType = receiverType != null
                ? receiverType
                : resolveRewrittenInvocationActualReceiverType(
                        scope,
                        invocationSite,
                        hiddenReceiverExpression,
                        null
                );
        Object hiddenReceiverType = resolveConcreteInvocationArgumentType(getFieldValue(hiddenReceiverExpression, "resolvedType"), hiddenReceiverExpression);
        Object rewrittenReceiverArgumentType = preservedActualReceiverType != null && !isProblem(preservedActualReceiverType)
                ? preservedActualReceiverType
                : resolveConcreteInvocationArgumentType(null, hiddenReceiverExpression);
        rewrittenArgumentTypes[0] = hiddenReceiverType != null
                ? hiddenReceiverType
                : rewrittenReceiverArgumentType != null
                ? rewrittenReceiverArgumentType
                : (receiverType != null ? receiverType : (parameters.length > 0 ? parameters[0] : null));
        if (originalArgumentTypes.length > 0) {
            System.arraycopy(originalArgumentTypes, 0, rewrittenArgumentTypes, 1, Math.min(originalArgumentTypes.length, rewrittenArgumentTypes.length - 1));
        }
        logReceiverRewriteIfGenericReceiverType(
                selectorName,
                invocationSite,
                hiddenReceiverExpression,
                receiverType,
                preservedActualReceiverType,
                rewrittenArguments,
                rewrittenArgumentTypes,
                parameters
        );

        Object declaringClass = getFieldValue(compatibleBinding, "declaringClass");
        Object staticReceiver = createStaticReceiverTypeReference(scope, invocationSite, declaringClass);
        if (staticReceiver == null) {
            return false;
        }

        setFieldValue(invocationSite, "receiver", staticReceiver);
        setFieldValue(invocationSite, "arguments", rewrittenArguments);
        setFieldValue(invocationSite, "argumentTypes", rewrittenArgumentTypes);
        setFieldValue(invocationSite, "binding", compatibleBinding);
        setFieldValue(invocationSite, "actualReceiverType", preservedActualReceiverType);
        Object notAConstant = getNotAConstant(invocationSite);
        if (notAConstant != null) {
            setFieldValue(invocationSite, "constant", notAConstant);
        }
        Object returnType = getFieldValue(compatibleBinding, "returnType");
        if (returnType != null) {
            setFieldValue(invocationSite, "resolvedType", returnType);
        }
        refreshPostRewriteFunctionalArguments(scope, invocationSite, compatibleBinding);
        if (Util.isDebugEnabled()) {
            Util.log("[ZirconCore] implicitStaticRewrite selector=" + selectorName
                    + ", binding=" + describeMethodBindingDetailed(compatibleBinding)
                    + ", args=" + rewrittenArguments.length
                    + ", params=" + parameters.length
                    + ", receiverType=" + describeTypeDebug(rewrittenArgumentTypes[0])
                    + ", declaringClass=" + describeTypeDebug(declaringClass));
        }
        return true;
    }

    private static Object createStaticReceiverTypeReference(Object scope, Object invocationSite, Object declaringClass) throws Exception {
        if (scope == null || invocationSite == null || declaringClass == null) {
            return null;
        }
        char[][] compoundName = getFieldValue(declaringClass, "compoundName") instanceof char[][]
                ? (char[][]) getFieldValue(declaringClass, "compoundName")
                : toCompoundName(getQualifiedTypeName(declaringClass));
        if (compoundName == null || compoundName.length == 0) {
            return null;
        }
        int sourceStart = readIntField(invocationSite, "sourceStart");
        int sourceEnd = readIntField(invocationSite, "sourceEnd");
        long position = (((long) sourceStart) << 32) | (sourceEnd & 0xffffffffL);
        Object nameReference;
        if (compoundName.length == 1) {
            Class<?> fallbackClass = loadClass("org.eclipse.jdt.internal.compiler.ast.SingleNameReference", invocationSite);
            nameReference = fallbackClass
                    .getDeclaredConstructor(char[].class, long.class)
                    .newInstance(compoundName[0], position);
        } else {
            long[] positions = new long[compoundName.length];
            Arrays.fill(positions, position);
            Class<?> fallbackClass = loadClass("org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference", invocationSite);
            nameReference = fallbackClass
                    .getDeclaredConstructor(char[][].class, long[].class, int.class, int.class)
                    .newInstance((Object) compoundName, positions, sourceStart, sourceEnd);
        }
        copySourcePositions(invocationSite, nameReference);
        setFieldValue(nameReference, "binding", declaringClass);
        setFieldValue(nameReference, "resolvedType", declaringClass);
        setFieldValue(nameReference, "actualReceiverType", declaringClass);
        Object notAConstant = getNotAConstant(invocationSite);
        if (notAConstant != null) {
            setFieldValue(nameReference, "constant", notAConstant);
        }
        return nameReference;
    }

    public static void prepareConditionalExpression(Object conditionalExpression, Object currentScope) {
        if (!isConditionalExpression(conditionalExpression) || currentScope == null) {
            return;
        }
        try {
            rewriteElvisConditionalExpression(conditionalExpression, currentScope);
            rebindProblemFieldLocalReferences(
                    conditionalExpression,
                    currentScope,
                    currentScope,
                    new IdentityHashMap<>(),
                    0
            );
            ensureElvisConditionBindingResolved(conditionalExpression, currentScope);
            ensureElvisConditionalOriginalBranchTypes(conditionalExpression, currentScope);
            ensureNestedLambdaCaptureStates(conditionalExpression, currentScope, new IdentityHashMap<>(), 0);
            rebindProblemFieldLocalReferences(conditionalExpression, currentScope, currentScope, new IdentityHashMap<>(), 0);
            repairMessageSendResolvedTypes(conditionalExpression, currentScope, new IdentityHashMap<>(), 0);
            repairBinaryExpressionResolvedTypes(conditionalExpression, currentScope, new IdentityHashMap<>(), 0);
            Object resolvedType = findField(conditionalExpression.getClass(), "resolvedType") != null
                    ? getFieldValue(conditionalExpression, "resolvedType")
                    : null;
            if (resolvedType == null || isProblem(resolvedType)) {
                tryResolveConditionalExpressionTypeFallback(conditionalExpression, currentScope);
                ensureNestedLambdaCaptureStates(conditionalExpression, currentScope, new IdentityHashMap<>(), 0);
                rebindProblemFieldLocalReferences(conditionalExpression, currentScope, currentScope, new IdentityHashMap<>(), 0);
                repairMessageSendResolvedTypes(conditionalExpression, currentScope, new IdentityHashMap<>(), 0);
                repairBinaryExpressionResolvedTypes(conditionalExpression, currentScope, new IdentityHashMap<>(), 0);
            }
            rememberSemanticTypeVariableTargetRange(currentScope, conditionalExpression);
            logConditionalExpressionRepairState(conditionalExpression, currentScope, "prepare");
        } catch (Exception e) {
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconCore] prepareConditionalExpression failed: "
                        + e.getClass().getName() + ": " + e.getMessage());
            }
        }
    }

    public static Object tryResolveConditionalExpressionType(Object conditionalExpression, Object currentScope) {
        if (!isConditionalExpression(conditionalExpression) || currentScope == null) {
            return null;
        }
        try {
            boolean rewritten = rewriteElvisConditionalExpression(conditionalExpression, currentScope);
            if (!rewritten
                    && !isElvisConditionalExpression(conditionalExpression)) {
                return null;
            }
            rebindProblemFieldLocalReferences(
                    conditionalExpression,
                    currentScope,
                    currentScope,
                    new IdentityHashMap<>(),
                    0
            );
            ensureElvisConditionBindingResolved(conditionalExpression, currentScope);
            ensureElvisConditionalOriginalBranchTypes(conditionalExpression, currentScope);
            rebindProblemFieldLocalReferences(conditionalExpression, currentScope, currentScope, new IdentityHashMap<>(), 0);
            Object valueIfTrue = getFieldValue(conditionalExpression, "valueIfTrue");
            Object valueIfFalse = getFieldValue(conditionalExpression, "valueIfFalse");
            if (valueIfTrue == null || valueIfFalse == null) {
                return null;
            }
            Object syntheticConditional = createSyntheticConditionalExpressionForResolve(
                    conditionalExpression,
                    valueIfTrue,
                    valueIfFalse
            );
            if (syntheticConditional == null) {
                return null;
            }
            copyConditionalExpressionContext(conditionalExpression, syntheticConditional);
            Object resolvedType = invokeMethod(syntheticConditional, "resolveType", currentScope);
            if (resolvedType == null) {
                return null;
            }
            copyConditionalResolutionState(syntheticConditional, conditionalExpression);
            ensureElvisConditionBindingResolved(conditionalExpression, currentScope);
            rememberSemanticTypeVariableTargetRange(currentScope, conditionalExpression);
            return resolvedType;
        } catch (Exception e) {
            try {
                Object fallbackType = tryResolveConditionalExpressionTypeFallback(conditionalExpression, currentScope);
                if (fallbackType != null) {
                    ensureElvisConditionBindingResolved(conditionalExpression, currentScope);
                    return fallbackType;
                }
            } catch (Exception ignored) {
            }
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconCore] tryResolveConditionalExpressionType failed: "
                        + e.getClass().getName() + ": " + e.getMessage());
                Util.log(Util.stackTrace(e));
            }
            return null;
        }
    }

    public static Object tryRecoverConditionalExpressionTypeOnFailure(
            Object conditionalExpression,
            Object currentScope,
            Throwable error
    ) {
        if (!isConditionalExpression(conditionalExpression)
                || currentScope == null
                || error == null
                || !shouldIgnoreFunctionalRefreshFailure(error)) {
            return null;
        }
        try {
            if (!rewriteElvisConditionalExpression(conditionalExpression, currentScope)
                    && !isElvisConditionalExpression(conditionalExpression)) {
                return null;
            }
            rebindProblemFieldLocalReferences(
                    conditionalExpression,
                    currentScope,
                    currentScope,
                    new IdentityHashMap<>(),
                    0
            );
            ensureElvisConditionalOriginalBranchTypes(conditionalExpression, currentScope);
            Object fallbackType = tryResolveConditionalExpressionTypeFallback(conditionalExpression, currentScope);
            if (fallbackType != null) {
                ensureElvisConditionBindingResolved(conditionalExpression, currentScope);
                return fallbackType;
            }
            Object expectedType = findField(conditionalExpression.getClass(), "expectedType") != null
                    ? getFieldValue(conditionalExpression, "expectedType")
                    : null;
            if (expectedType != null && !isProblem(expectedType)) {
                if (findField(conditionalExpression.getClass(), "resolvedType") != null
                        && getFieldValue(conditionalExpression, "resolvedType") == null) {
                    setFieldValue(conditionalExpression, "resolvedType", expectedType);
                }
                return expectedType;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static Object tryRecoverConditionalExpressionAnalyseCode(
            Object conditionalExpression,
            Object currentScope,
            Object flowContext,
            Object flowInfo,
            Throwable error
    ) {
        if (!isConditionalExpression(conditionalExpression)
                || currentScope == null
                || flowInfo == null
                || error == null) {
            return null;
        }
        Throwable unwrapped = unwrapInvocationFailure(error);
        if (!(unwrapped instanceof NullPointerException)
                && !(unwrapped instanceof ArrayIndexOutOfBoundsException)
                && !(unwrapped instanceof StackOverflowError)) {
            return null;
        }
        try {
            prepareConditionalExpression(conditionalExpression, currentScope);
            String missingNode = describeFirstMissingResolvedTypeNode(
                    conditionalExpression,
                    new IdentityHashMap<>(),
                    0
            );
            if (missingNode == null) {
                if (Util.isDebugEnabled()) {
                    Util.log("[ZirconCore] analyseCode conditionalRecovered"
                            + ": source=" + describeSourceRange(conditionalExpression)
                            + ", error=" + unwrapped.getClass().getName() + ": " + unwrapped.getMessage());
                }
                return flowInfo;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static boolean tryGenerateConditionalExpressionCode(
            Object conditionalExpression,
            Object currentScope,
            Object codeStream,
            boolean valueRequired
    ) {
        if (!isConditionalExpression(conditionalExpression) || currentScope == null || codeStream == null) {
            return false;
        }
        try {
            rewriteElvisConditionalExpression(conditionalExpression, currentScope);
            if (!isElvisConditionalExpression(conditionalExpression)) {
                return false;
            }
            generateElvisConditionalExpressionCode(conditionalExpression, currentScope, codeStream, valueRequired);
            return true;
        } catch (Exception e) {
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconCore] generateElvisConditionalExpressionCode failed: "
                        + e.getClass().getName() + ": " + e.getMessage());
                Util.log(Util.stackTrace(e));
            }
            return false;
        }
    }

    public static boolean tryGenerateConditionalExpressionOptimizedBoolean(
            Object conditionalExpression,
            Object currentScope,
            Object codeStream,
            Object trueLabel,
            Object falseLabel,
            boolean valueRequired
    ) {
        if (!isConditionalExpression(conditionalExpression) || currentScope == null || codeStream == null) {
            return false;
        }
        try {
            rewriteElvisConditionalExpression(conditionalExpression, currentScope);
            if (!isElvisConditionalExpression(conditionalExpression)) {
                return false;
            }
            generateElvisConditionalExpressionOptimizedBoolean(
                    conditionalExpression,
                    currentScope,
                    codeStream,
                    trueLabel,
                    falseLabel,
                    valueRequired
            );
            return true;
        } catch (Exception e) {
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconCore] generateElvisConditionalExpressionOptimizedBoolean failed: "
                        + e.getClass().getName() + ": " + e.getMessage());
                Util.log(Util.stackTrace(e));
            }
            return false;
        }
    }

    private static boolean rewriteElvisConditionalExpression(Object conditionalExpression, Object currentScope) throws Exception {
        if (!isConditionalExpression(conditionalExpression) || currentScope == null) {
            return false;
        }
        if (isElvisConditionalExpression(conditionalExpression)) {
            return true;
        }
        Object valueIfTrue = getFieldValue(conditionalExpression, "valueIfTrue");
        if (!isElvisPlaceholderExpression(valueIfTrue)) {
            return false;
        }
        Object originalCondition = getFieldValue(conditionalExpression, "condition");
        Object markerInvocation = createElvisMarkerInvocation(currentScope, conditionalExpression);
        if (markerInvocation == null) {
            return false;
        }
        prepareElvisMarkerInvocation(markerInvocation, currentScope);
        setFieldValue(conditionalExpression, "condition", markerInvocation);
        setFieldValue(conditionalExpression, "valueIfTrue", originalCondition);
        resetElvisConditionalState(conditionalExpression);
        return true;
    }

    private static void ensureElvisConditionBindingResolved(Object conditionalExpression, Object currentScope) throws Exception {
        if (!isConditionalExpression(conditionalExpression) || currentScope == null || !isElvisConditionalExpression(conditionalExpression)) {
            return;
        }
        Object condition = getFieldValue(conditionalExpression, "condition");
        if (condition == null) {
            return;
        }
        if (isElvisMarkerInvocation(condition)) {
            prepareElvisMarkerInvocation(condition, currentScope);
            return;
        }
        Object binding = getFieldValue(condition, "binding");
        if (binding != null && !isProblem(binding)) {
            return;
        }
        invokeMethod(condition, "resolveType", currentScope);
        rebindProblemFieldLocalReferences(condition, currentScope, currentScope, new IdentityHashMap<>(), 0);
    }

    private static void ensureElvisConditionalOriginalBranchTypes(Object conditionalExpression, Object currentScope) throws Exception {
        if (!isConditionalExpression(conditionalExpression) || currentScope == null || !isElvisConditionalExpression(conditionalExpression)) {
            return;
        }
        boolean needsTrueType = findField(conditionalExpression.getClass(), "originalValueIfTrueType") != null
                && getFieldValue(conditionalExpression, "originalValueIfTrueType") == null;
        boolean needsFalseType = findField(conditionalExpression.getClass(), "originalValueIfFalseType") != null
                && getFieldValue(conditionalExpression, "originalValueIfFalseType") == null;
        if (!needsTrueType && !needsFalseType) {
            return;
        }
        Object expectedType = findField(conditionalExpression.getClass(), "expectedType") != null
                ? getFieldValue(conditionalExpression, "expectedType")
                : null;
        Object valueIfTrue = getFieldValue(conditionalExpression, "valueIfTrue");
        Object valueIfFalse = getFieldValue(conditionalExpression, "valueIfFalse");
        Object trueType = valueIfTrue != null && findField(valueIfTrue.getClass(), "resolvedType") != null
                ? getFieldValue(valueIfTrue, "resolvedType")
                : null;
        Object falseType = valueIfFalse != null && findField(valueIfFalse.getClass(), "resolvedType") != null
                ? getFieldValue(valueIfFalse, "resolvedType")
                : null;
        Object branchFallbackType = expectedType != null && !isProblem(expectedType)
                ? expectedType
                : (trueType != null && !isProblem(trueType) ? trueType : falseType);
        if (needsTrueType && (trueType == null || isProblem(trueType))) {
            trueType = branchFallbackType;
        }
        if (needsFalseType && (falseType == null || isProblem(falseType))) {
            falseType = branchFallbackType;
        }
        if (needsTrueType && trueType != null && !isProblem(trueType)) {
            setFieldValue(conditionalExpression, "originalValueIfTrueType", trueType);
        }
        if (needsFalseType && falseType != null && !isProblem(falseType)) {
            setFieldValue(conditionalExpression, "originalValueIfFalseType", falseType);
        }
        if ((needsTrueType && getFieldValue(conditionalExpression, "originalValueIfTrueType") == null)
                || (needsFalseType && getFieldValue(conditionalExpression, "originalValueIfFalseType") == null)) {
            tryResolveConditionalExpressionTypeFallback(conditionalExpression, currentScope);
        }
    }

    private static void generateElvisConditionalExpressionCode(
            Object conditionalExpression,
            Object currentScope,
            Object codeStream,
            boolean valueRequired
    ) throws Exception {
        Object valueIfTrue = getFieldValue(conditionalExpression, "valueIfTrue");
        Object valueIfFalse = getFieldValue(conditionalExpression, "valueIfFalse");
        if (valueIfTrue == null || valueIfFalse == null) {
            return;
        }
        int startPc = readIntField(codeStream, "position");
        Object trueType = getFieldValue(valueIfTrue, "resolvedType");
        if (isBaseType(trueType)) {
            invokeMethod(valueIfTrue, "generateCode", currentScope, codeStream, valueRequired);
            if (valueRequired) {
                invokeMethod(codeStream, "generateImplicitConversion", readIntField(conditionalExpression, "implicitConversion"));
            }
            invokeMethod(codeStream, "recordPositionsFrom", startPc, readIntField(conditionalExpression, "sourceStart"));
            return;
        }

        int savedTrueImplicitConversion = findField(valueIfTrue.getClass(), "implicitConversion") != null
                ? readIntField(valueIfTrue, "implicitConversion")
                : 0;
        if (findField(valueIfTrue.getClass(), "implicitConversion") != null) {
            setFieldValue(valueIfTrue, "implicitConversion", 0);
        }
        try {
            invokeMethod(valueIfTrue, "generateCode", currentScope, codeStream, true);
        } finally {
            if (findField(valueIfTrue.getClass(), "implicitConversion") != null) {
                setFieldValue(valueIfTrue, "implicitConversion", savedTrueImplicitConversion);
            }
        }

        Object falseBranchLabel = createBranchLabel(codeStream);
        Object endLabel = createBranchLabel(codeStream);
        invokeMethod(codeStream, "dup");
        invokeMethod(codeStream, "ifnull", falseBranchLabel);
        if (valueRequired) {
            if (savedTrueImplicitConversion != 0) {
                invokeMethod(codeStream, "generateImplicitConversion", savedTrueImplicitConversion);
            }
        } else {
            invokeMethod(codeStream, "pop");
        }
        invokeMethod(codeStream, "goto_", endLabel);

        invokeMethod(falseBranchLabel, "place");
        invokeMethod(codeStream, "pop");
        invokeMethod(valueIfFalse, "generateCode", currentScope, codeStream, valueRequired);
        if (valueRequired) {
            normalizeConditionalNullOperandStack(conditionalExpression, codeStream);
        }
        invokeMethod(endLabel, "place");
        if (valueRequired) {
            invokeMethod(codeStream, "generateImplicitConversion", readIntField(conditionalExpression, "implicitConversion"));
        }
        invokeMethod(codeStream, "recordPositionsFrom", startPc, readIntField(conditionalExpression, "sourceStart"));
    }

    private static void generateElvisConditionalExpressionOptimizedBoolean(
            Object conditionalExpression,
            Object currentScope,
            Object codeStream,
            Object trueLabel,
            Object falseLabel,
            boolean valueRequired
    ) throws Exception {
        generateElvisConditionalExpressionCode(conditionalExpression, currentScope, codeStream, true);
        if (trueLabel != null && falseLabel != null) {
            invokeMethod(codeStream, "ifne", trueLabel);
            invokeMethod(codeStream, "goto_", falseLabel);
            return;
        }
        if (trueLabel != null) {
            invokeMethod(codeStream, "ifne", trueLabel);
            return;
        }
        if (falseLabel != null) {
            invokeMethod(codeStream, "ifeq", falseLabel);
            return;
        }
        if (!valueRequired) {
            invokeMethod(codeStream, "pop");
        }
    }

    private static Object createElvisMarkerInvocation(Object scope, Object anchor) throws Exception {
        if (scope == null || anchor == null) {
            return null;
        }
        Class<?> messageSendClass = loadClass("org.eclipse.jdt.internal.compiler.ast.MessageSend", anchor);
        Object messageSend = messageSendClass.getDeclaredConstructor().newInstance();
        copySourcePositions(anchor, messageSend);
        Object biOpClass = null;
        try {
            biOpClass = resolveTypeBindingByName(scope, BI_OP_QUALIFIED_NAME);
        } catch (Exception ignored) {
            biOpClass = null;
        }
        Object receiver = null;
        if (biOpClass != null) {
            try {
                receiver = createStaticReceiverTypeReference(scope, anchor, biOpClass);
            } catch (Exception ignored) {
                receiver = null;
            }
        }
        if (receiver == null) {
            receiver = createQualifiedNameReference(anchor, toCompoundName(BI_OP_QUALIFIED_NAME), biOpClass);
        }
        if (receiver == null) {
            return null;
        }
        setFieldValue(messageSend, "receiver", receiver);
        setFieldValue(messageSend, "selector", ELVIS_MARKER_SELECTOR.toCharArray());
        setFieldValue(messageSend, "arguments", emptyExpressionArray(anchor));
        if (biOpClass != null && findField(messageSend.getClass(), "actualReceiverType") != null) {
            setFieldValue(messageSend, "actualReceiverType", biOpClass);
        }
        Object markerBinding = null;
        if (biOpClass != null) {
            try {
                markerBinding = findStaticMethodBinding(biOpClass, ELVIS_MARKER_SELECTOR, 0);
            } catch (Exception ignored) {
                markerBinding = null;
            }
        }
        if (markerBinding != null) {
            setFieldValue(messageSend, "binding", markerBinding);
            Object returnType = getFieldValue(markerBinding, "returnType");
            if (returnType != null) {
                setFieldValue(messageSend, "resolvedType", returnType);
            }
        } else {
            Object booleanType = resolveBooleanTypeBinding(scope, anchor);
            if (booleanType != null && findField(messageSend.getClass(), "resolvedType") != null) {
                setFieldValue(messageSend, "resolvedType", booleanType);
            }
        }
        if (findField(messageSend.getClass(), "argumentTypes") != null) {
            setFieldValue(messageSend, "argumentTypes", emptyTypeBindingArray(scope));
        }
        Object notAConstant = getNotAConstant(anchor);
        if (notAConstant != null) {
            setFieldValue(messageSend, "constant", notAConstant);
        }
        return messageSend;
    }

    private static Object findStaticMethodBinding(Object typeBinding, String selector, int parameterCount) throws Exception {
        Object lookupType = normalizeMethodLookupType(typeBinding);
        if (lookupType == null || selector == null || selector.isEmpty()) {
            return null;
        }
        Object methods = invokeMethod(lookupType, "getMethods", selector.toCharArray());
        if (!(methods instanceof Object[])) {
            return null;
        }
        for (Object method : (Object[]) methods) {
            if (method == null || isProblem(method)) {
                continue;
            }
            Object parameters = getFieldValue(method, "parameters");
            int methodParameterCount = parameters instanceof Object[] ? ((Object[]) parameters).length : -1;
            if (methodParameterCount != parameterCount) {
                continue;
            }
            boolean isStaticMethod = false;
            try {
                isStaticMethod = Boolean.TRUE.equals(invokeMethod(method, "isStatic"));
            } catch (Exception ignored) {
                isStaticMethod = false;
            }
            if (isStaticMethod) {
                return method;
            }
        }
        return null;
    }

    private static Object createSyntheticConditionalExpressionForResolve(
            Object anchor,
            Object valueIfTrue,
            Object valueIfFalse
    ) throws Exception {
        if (anchor == null || valueIfTrue == null || valueIfFalse == null) {
            return null;
        }
        Class<?> conditionalExpressionClass = loadClass(
                "org.eclipse.jdt.internal.compiler.ast.ConditionalExpression",
                anchor
        );
        Class<?> expressionClass = loadClass("org.eclipse.jdt.internal.compiler.ast.Expression", anchor);
        Object condition = createBooleanLiteralExpression(anchor, false);
        if (condition == null) {
            return null;
        }
        Object syntheticConditional = conditionalExpressionClass
                .getDeclaredConstructor(expressionClass, expressionClass, expressionClass)
                .newInstance(condition, valueIfTrue, valueIfFalse);
        copySourcePositions(anchor, syntheticConditional);
        return syntheticConditional;
    }

    private static Object createBooleanLiteralExpression(Object anchor, boolean value) throws Exception {
        if (anchor == null) {
            return null;
        }
        int sourceStart = readIntField(anchor, "sourceStart");
        int sourceEnd = readIntField(anchor, "sourceEnd");
        String className = value
                ? "org.eclipse.jdt.internal.compiler.ast.TrueLiteral"
                : "org.eclipse.jdt.internal.compiler.ast.FalseLiteral";
        Class<?> literalClass = loadClass(className, anchor);
        Object literal = literalClass
                .getDeclaredConstructor(int.class, int.class)
                .newInstance(sourceStart, sourceEnd);
        copySourcePositions(anchor, literal);
        Object notAConstant = getNotAConstant(anchor);
        if (notAConstant != null && findField(literal.getClass(), "constant") != null) {
            setFieldValue(literal, "constant", notAConstant);
        }
        return literal;
    }

    private static Object createQualifiedNameReference(
            Object anchor,
            char[][] compoundName,
            Object resolvedBinding
    ) throws Exception {
        if (anchor == null || compoundName == null || compoundName.length == 0) {
            return null;
        }
        int sourceStart = readIntField(anchor, "sourceStart");
        int sourceEnd = readIntField(anchor, "sourceEnd");
        long position = (((long) sourceStart) << 32) | (sourceEnd & 0xffffffffL);
        Object reference;
        if (compoundName.length == 1) {
            Class<?> singleNameReferenceClass = loadClass("org.eclipse.jdt.internal.compiler.ast.SingleNameReference", anchor);
            reference = singleNameReferenceClass
                    .getDeclaredConstructor(char[].class, long.class)
                    .newInstance(compoundName[0], position);
        } else {
            long[] positions = new long[compoundName.length];
            Arrays.fill(positions, position);
            Class<?> qualifiedNameReferenceClass = loadClass("org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference", anchor);
            reference = qualifiedNameReferenceClass
                    .getDeclaredConstructor(char[][].class, long[].class, int.class, int.class)
                    .newInstance((Object) compoundName, positions, sourceStart, sourceEnd);
        }
        copySourcePositions(anchor, reference);
        Object notAConstant = getNotAConstant(anchor);
        if (notAConstant != null) {
            setFieldValue(reference, "constant", notAConstant);
        }
        if (resolvedBinding != null) {
            setFieldValue(reference, "binding", resolvedBinding);
            if (findField(reference.getClass(), "resolvedType") != null) {
                setFieldValue(reference, "resolvedType", resolvedBinding);
            }
            if (findField(reference.getClass(), "actualReceiverType") != null) {
                setFieldValue(reference, "actualReceiverType", resolvedBinding);
            }
        }
        return reference;
    }

    private static void resetElvisConditionalState(Object conditionalExpression) throws Exception {
        Object notAConstant = getNotAConstant(conditionalExpression);
        if (notAConstant != null) {
            if (findField(conditionalExpression.getClass(), "constant") != null) {
                setFieldValue(conditionalExpression, "constant", notAConstant);
            }
            if (findField(conditionalExpression.getClass(), "optimizedBooleanConstant") != null) {
                setFieldValue(conditionalExpression, "optimizedBooleanConstant", notAConstant);
            }
            if (findField(conditionalExpression.getClass(), "optimizedIfTrueConstant") != null) {
                setFieldValue(conditionalExpression, "optimizedIfTrueConstant", notAConstant);
            }
            if (findField(conditionalExpression.getClass(), "optimizedIfFalseConstant") != null) {
                setFieldValue(conditionalExpression, "optimizedIfFalseConstant", notAConstant);
            }
        }
        if (findField(conditionalExpression.getClass(), "resolvedType") != null) {
            setFieldValue(conditionalExpression, "resolvedType", null);
        }
        if (findField(conditionalExpression.getClass(), "originalValueIfTrueType") != null) {
            setFieldValue(conditionalExpression, "originalValueIfTrueType", null);
        }
        if (findField(conditionalExpression.getClass(), "originalValueIfFalseType") != null) {
            setFieldValue(conditionalExpression, "originalValueIfFalseType", null);
        }
    }

    private static void copyConditionalExpressionContext(Object source, Object target) throws Exception {
        if (source == null || target == null) {
            return;
        }
        Object expressionContext = null;
        try {
            expressionContext = invokeMethod(source, "getExpressionContext");
        } catch (Exception ignored) {
        }
        if (expressionContext != null) {
            invokeMethod(target, "setExpressionContext", expressionContext);
        }
        Object expectedType = findField(source.getClass(), "expectedType") != null
                ? getFieldValue(source, "expectedType")
                : null;
        if (expectedType != null) {
            invokeMethod(target, "setExpectedType", expectedType);
        }
    }

    private static void copyConditionalResolutionState(Object source, Object target) throws Exception {
        if (source == null || target == null) {
            return;
        }
        copyFieldIfPresent(source, target, "resolvedType");
        copyFieldIfPresent(source, target, "implicitConversion");
        copyFieldIfPresent(source, target, "constant");
        copyFieldIfPresent(source, target, "optimizedBooleanConstant");
        copyFieldIfPresent(source, target, "optimizedIfTrueConstant");
        copyFieldIfPresent(source, target, "optimizedIfFalseConstant");
        copyFieldIfPresent(source, target, "originalValueIfTrueType");
        copyFieldIfPresent(source, target, "originalValueIfFalseType");
        copyFieldIfPresent(source, target, "bits");
    }

    private static Object tryResolveConditionalExpressionTypeFallback(
            Object conditionalExpression,
            Object currentScope
    ) throws Exception {
        if (!isConditionalExpression(conditionalExpression) || currentScope == null) {
            return null;
        }
        Object valueIfTrue = getFieldValue(conditionalExpression, "valueIfTrue");
        Object valueIfFalse = getFieldValue(conditionalExpression, "valueIfFalse");
        if (valueIfTrue == null || valueIfFalse == null) {
            return null;
        }
        copyConditionalExpressionContextToBranch(conditionalExpression, valueIfTrue);
        copyConditionalExpressionContextToBranch(conditionalExpression, valueIfFalse);
        rebindProblemFieldLocalReferences(valueIfTrue, currentScope, currentScope, new IdentityHashMap<>(), 0);
        rebindProblemFieldLocalReferences(valueIfFalse, currentScope, currentScope, new IdentityHashMap<>(), 0);
        Object expectedType = findField(conditionalExpression.getClass(), "expectedType") != null
                ? getFieldValue(conditionalExpression, "expectedType")
                : null;
        Object trueType = resolveConditionalBranchType(valueIfTrue, currentScope);
        Object falseType = resolveConditionalBranchType(valueIfFalse, currentScope);
        Object resolvedType = chooseConditionalExpressionType(trueType, falseType, expectedType, currentScope);
        if (resolvedType == null) {
            return null;
        }
        Object branchType = trueType != null ? trueType : (falseType != null ? falseType : resolvedType);
        if (findField(conditionalExpression.getClass(), "resolvedType") != null) {
            setFieldValue(conditionalExpression, "resolvedType", resolvedType);
        }
        if (findField(conditionalExpression.getClass(), "originalValueIfTrueType") != null) {
            setFieldValue(conditionalExpression, "originalValueIfTrueType", trueType != null ? trueType : branchType);
        }
        if (findField(conditionalExpression.getClass(), "originalValueIfFalseType") != null) {
            setFieldValue(conditionalExpression, "originalValueIfFalseType", falseType != null ? falseType : branchType);
        }
        Object notAConstant = getNotAConstant(conditionalExpression);
        if (notAConstant != null) {
            if (findField(conditionalExpression.getClass(), "constant") != null) {
                setFieldValue(conditionalExpression, "constant", notAConstant);
            }
            if (findField(conditionalExpression.getClass(), "optimizedBooleanConstant") != null) {
                setFieldValue(conditionalExpression, "optimizedBooleanConstant", notAConstant);
            }
            if (findField(conditionalExpression.getClass(), "optimizedIfTrueConstant") != null) {
                setFieldValue(conditionalExpression, "optimizedIfTrueConstant", notAConstant);
            }
            if (findField(conditionalExpression.getClass(), "optimizedIfFalseConstant") != null) {
                setFieldValue(conditionalExpression, "optimizedIfFalseConstant", notAConstant);
            }
        }
        ensureNestedLambdaCaptureStates(conditionalExpression, currentScope, new IdentityHashMap<>(), 0);
        rebindProblemFieldLocalReferences(conditionalExpression, currentScope, currentScope, new IdentityHashMap<>(), 0);
        repairMessageSendResolvedTypes(conditionalExpression, currentScope, new IdentityHashMap<>(), 0);
        repairBinaryExpressionResolvedTypes(conditionalExpression, currentScope, new IdentityHashMap<>(), 0);
        return resolvedType;
    }

    private static void repairMessageSendResolvedTypes(
            Object node,
            Object currentScope,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 16 || visited.put(node, Boolean.TRUE) != null || !isAstNode(node)) {
            return;
        }
        Object nextScope = currentScope;
        if (isLambdaExpression(node)) {
            Object lambdaScope = getFieldValue(node, "scope");
            Object enclosingScope = getFieldValue(node, "enclosingScope");
            nextScope = lambdaScope != null ? lambdaScope : (enclosingScope != null ? enclosingScope : currentScope);
        }
        if ("MessageSend".equals(node.getClass().getSimpleName())
                && findField(node.getClass(), "resolvedType") != null) {
            Object resolvedType = getFieldValue(node, "resolvedType");
            if (resolvedType == null || isProblem(resolvedType)) {
                Object binding = findField(node.getClass(), "binding") != null ? getFieldValue(node, "binding") : null;
                if ((binding == null || isProblem(binding) || resolvedType == null || isProblem(resolvedType))
                        && nextScope != null) {
                    try {
                        tryResolveAstNode(node, nextScope);
                        Object directResolvedType = invokeMethod(node, "resolveType", nextScope);
                        if (directResolvedType != null && !isProblem(directResolvedType)) {
                            resolvedType = directResolvedType;
                        }
                        binding = findField(node.getClass(), "binding") != null ? getFieldValue(node, "binding") : binding;
                    } catch (Exception ignored) {
                    }
                }
                Object repairedType = binding != null && !isProblem(binding) && findField(binding.getClass(), "returnType") != null
                        ? getFieldValue(binding, "returnType")
                        : null;
                if ((repairedType == null || isProblem(repairedType)) && resolvedType != null && !isProblem(resolvedType)) {
                    repairedType = resolvedType;
                }
                if ((repairedType == null || isProblem(repairedType))
                        && "length".equals(readSelectorName(node))
                        && countInvocationArguments(node) == 0) {
                    repairedType = getPrimitiveIntType(node);
                }
                if ((repairedType == null || isProblem(repairedType))
                        && findField(node.getClass(), "actualReceiverType") != null) {
                    repairedType = getFieldValue(node, "actualReceiverType");
                }
                if (repairedType != null && !isProblem(repairedType)) {
                    setFieldValue(node, "resolvedType", repairedType);
                }
            }
        }
        for (Object child : getAstChildren(node)) {
            repairMessageSendResolvedTypes(child, nextScope, visited, depth + 1);
        }
    }

    private static String readSelectorName(Object messageSend) throws Exception {
        if (messageSend == null || findField(messageSend.getClass(), "selector") == null) {
            return "";
        }
        Object selector = getFieldValue(messageSend, "selector");
        return selector instanceof char[] ? new String((char[]) selector) : String.valueOf(selector);
    }

    private static int countInvocationArguments(Object messageSend) throws Exception {
        Object arguments = findField(messageSend.getClass(), "arguments") != null
                ? getFieldValue(messageSend, "arguments")
                : null;
        return arguments instanceof Object[] ? ((Object[]) arguments).length : 0;
    }

    private static Object getPrimitiveIntType(Object anchor) {
        if (anchor == null) {
            return null;
        }
        try {
            Class<?> typeBindingClass = loadClass("org.eclipse.jdt.internal.compiler.lookup.TypeBinding", anchor);
            java.lang.reflect.Field intField = findField(typeBindingClass, "INT");
            return intField == null ? null : intField.get(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void repairBinaryExpressionResolvedTypes(
            Object node,
            Object currentScope,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 16 || visited.put(node, Boolean.TRUE) != null || !isAstNode(node)) {
            return;
        }
        Object nextScope = currentScope;
        if (isLambdaExpression(node)) {
            Object lambdaScope = getFieldValue(node, "scope");
            Object enclosingScope = getFieldValue(node, "enclosingScope");
            nextScope = lambdaScope != null ? lambdaScope : (enclosingScope != null ? enclosingScope : currentScope);
        }
        for (Object child : getAstChildren(node)) {
            repairBinaryExpressionResolvedTypes(child, nextScope, visited, depth + 1);
        }
        if (isBinaryExpressionNode(node)
                && findField(node.getClass(), "resolvedType") != null) {
            Object resolvedType = getFieldValue(node, "resolvedType");
            if (resolvedType == null || isProblem(resolvedType)) {
                if (nextScope != null) {
                    try {
                        tryResolveAstNode(node, nextScope);
                        Object directResolvedType = invokeMethod(node, "resolveType", nextScope);
                        if (directResolvedType != null && !isProblem(directResolvedType)) {
                            resolvedType = directResolvedType;
                        }
                    } catch (Exception ignored) {
                    }
                }
                Object left = findField(node.getClass(), "left") != null ? getFieldValue(node, "left") : null;
                Object right = findField(node.getClass(), "right") != null ? getFieldValue(node, "right") : null;
                Object leftType = left != null && findField(left.getClass(), "resolvedType") != null
                        ? getFieldValue(left, "resolvedType")
                        : null;
                Object rightType = right != null && findField(right.getClass(), "resolvedType") != null
                        ? getFieldValue(right, "resolvedType")
                        : null;
                Object expectedType = findField(node.getClass(), "expectedType") != null
                        ? getFieldValue(node, "expectedType")
                        : null;
                Object repairedType = null;
                if (isConcreteStringType(leftType)) {
                    repairedType = leftType;
                } else if (isConcreteStringType(rightType)) {
                    repairedType = rightType;
                } else if (expectedType != null && !isProblem(expectedType)) {
                    repairedType = expectedType;
                } else if (leftType != null && !isProblem(leftType)) {
                    repairedType = leftType;
                } else if (rightType != null && !isProblem(rightType)) {
                    repairedType = rightType;
                }
                if (repairedType != null) {
                    setFieldValue(node, "resolvedType", repairedType);
                }
            }
        }
    }

    private static boolean isConcreteStringType(Object type) {
        if (type == null || isProblem(type)) {
            return false;
        }
        String typeName = getTypeName(type);
        return "java.lang.String".equals(typeName) || "String".equals(typeName);
    }

    private static void logConditionalExpressionRepairState(
            Object conditionalExpression,
            Object currentScope,
            String phase
    ) {
        if (conditionalExpression == null || currentScope == null || !isConditionalExpression(conditionalExpression)) {
            return;
        }
        try {
            Object resolvedType = findField(conditionalExpression.getClass(), "resolvedType") != null
                    ? getFieldValue(conditionalExpression, "resolvedType")
                    : null;
            Object trueType = findField(conditionalExpression.getClass(), "originalValueIfTrueType") != null
                    ? getFieldValue(conditionalExpression, "originalValueIfTrueType")
                    : null;
            Object falseType = findField(conditionalExpression.getClass(), "originalValueIfFalseType") != null
                    ? getFieldValue(conditionalExpression, "originalValueIfFalseType")
                    : null;
            boolean hasProblemField = containsProblemFieldNameReference(conditionalExpression, new IdentityHashMap<>(), 0);
            boolean missingResolvedType = resolvedType == null || isProblem(resolvedType);
            boolean missingBranchTypes = (trueType == null || isProblem(trueType)) || (falseType == null || isProblem(falseType));
            String firstMissingResolvedTypeNode = describeFirstMissingResolvedTypeNode(conditionalExpression);
            if (!hasProblemField && !missingResolvedType && !missingBranchTypes && "none".equals(firstMissingResolvedTypeNode)) {
                return;
            }
            String key = phase
                    + "|" + describeSourceRange(conditionalExpression)
                    + "|" + describeTypeDebug(resolvedType)
                    + "|" + hasProblemField
                    + "|" + missingBranchTypes
                    + "|" + firstMissingResolvedTypeNode;
            if (!CONDITIONAL_ANALYSE_DIAGNOSTIC_KEYS.add(key)) {
                return;
            }
            Util.log("[ZirconCore] conditionalRepairState"
                    + ": phase=" + phase
                    + ", source=" + describeSourceRange(conditionalExpression)
                    + ", resolvedType=" + describeTypeDebug(resolvedType)
                    + ", trueType=" + describeTypeDebug(trueType)
                    + ", falseType=" + describeTypeDebug(falseType)
                    + ", firstProblemNode=" + describeFirstProblemAstNode(currentScope, conditionalExpression)
                    + ", firstMissingResolvedTypeNode=" + firstMissingResolvedTypeNode
                    + ", nodeState=" + describeAstNodeState(conditionalExpression, 2));
        } catch (Exception ignored) {
        }
    }

    private static String describeFirstMissingResolvedTypeNode(Object node) throws Exception {
        String description = describeFirstMissingResolvedTypeNode(node, new IdentityHashMap<>(), 0);
        return description == null ? "none" : description;
    }

    private static String describeFirstMissingResolvedTypeNode(
            Object node,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (node == null || depth > 16 || visited.put(node, Boolean.TRUE) != null) {
            return null;
        }
        if (node instanceof Object[]) {
            for (Object element : (Object[]) node) {
                String description = describeFirstMissingResolvedTypeNode(element, visited, depth + 1);
                if (description != null) {
                    return description;
                }
            }
            return null;
        }
        if (!isAstNode(node)) {
            return null;
        }
        String simpleName = node.getClass().getSimpleName();
        if (("MessageSend".equals(simpleName) || isBinaryExpressionNode(node))
                && findField(node.getClass(), "resolvedType") != null) {
            Object resolvedType = getFieldValue(node, "resolvedType");
            if (resolvedType == null || isProblem(resolvedType)) {
                return describeAstNodeState(node, 2);
            }
        }
        for (Object child : getAstChildren(node)) {
            String description = describeFirstMissingResolvedTypeNode(child, visited, depth + 1);
            if (description != null) {
                return description;
            }
        }
        return null;
    }

    private static boolean isBinaryExpressionNode(Object node) {
        if (node == null) {
            return false;
        }
        Class<?> current = node.getClass();
        while (current != null) {
            if ("org.eclipse.jdt.internal.compiler.ast.BinaryExpression".equals(current.getName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static void copyConditionalExpressionContextToBranch(Object conditionalExpression, Object branch) throws Exception {
        if (conditionalExpression == null || branch == null) {
            return;
        }
        Object expressionContext = null;
        try {
            expressionContext = invokeMethod(conditionalExpression, "getExpressionContext");
        } catch (Exception ignored) {
        }
        if (expressionContext != null) {
            try {
                invokeMethod(branch, "setExpressionContext", expressionContext);
            } catch (Exception ignored) {
            }
        }
        Object expectedType = findField(conditionalExpression.getClass(), "expectedType") != null
                ? getFieldValue(conditionalExpression, "expectedType")
                : null;
        if (expectedType != null) {
            try {
                invokeMethod(branch, "setExpectedType", expectedType);
            } catch (Exception ignored) {
            }
        }
    }

    private static Object resolveConditionalBranchType(Object branch, Object scope) throws Exception {
        if (branch == null || scope == null) {
            return null;
        }
        try {
            tryResolveAstNode(branch, scope);
            Object resolvedType = findField(branch.getClass(), "resolvedType") != null
                    ? getFieldValue(branch, "resolvedType")
                    : null;
            if (resolvedType != null && !isProblem(resolvedType)) {
                return resolvedType;
            }
            Object expectedType = findField(branch.getClass(), "expectedType") != null
                    ? getFieldValue(branch, "expectedType")
                    : null;
            if (expectedType != null && !isProblem(expectedType)) {
                return expectedType;
            }
            Object directResolvedType = invokeMethod(branch, "resolveType", scope);
            return isProblem(directResolvedType) ? null : directResolvedType;
        } catch (Exception error) {
            Object resolvedType = findField(branch.getClass(), "resolvedType") != null
                    ? getFieldValue(branch, "resolvedType")
                    : null;
            if (resolvedType != null && !isProblem(resolvedType)) {
                return resolvedType;
            }
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconCore] resolveConditionalBranchType failed: "
                        + error.getClass().getName() + ": " + error.getMessage());
            }
            return null;
        }
    }

    private static Object chooseConditionalExpressionType(
            Object trueType,
            Object falseType,
            Object expectedType,
            Object currentScope
    ) throws Exception {
        if (trueType != null && falseType != null) {
            if (trueType == falseType || Objects.equals(getTypeName(trueType), getTypeName(falseType))) {
                return trueType;
            }
            if (expectedType != null
                    && isTypeCompatibleWithScope(trueType, expectedType, currentScope)
                    && isTypeCompatibleWithScope(falseType, expectedType, currentScope)) {
                return expectedType;
            }
            if (isTypeCompatibleWithScope(trueType, falseType, currentScope)) {
                return falseType;
            }
            if (isTypeCompatibleWithScope(falseType, trueType, currentScope)) {
                return trueType;
            }
        }
        if (trueType != null) {
            return trueType;
        }
        if (falseType != null) {
            return falseType;
        }
        return expectedType;
    }

    private static boolean isTypeCompatibleWithScope(Object sourceType, Object targetType, Object scope) throws Exception {
        if (sourceType == null || targetType == null) {
            return false;
        }
        try {
            Object compatible = invokeMethod(sourceType, "isCompatibleWith", targetType, scope);
            if (compatible instanceof Boolean) {
                return (Boolean) compatible;
            }
        } catch (Exception ignored) {
        }
        try {
            Object boxingCompatible = invokeMethod(sourceType, "isBoxingCompatibleWith", targetType, scope);
            if (boxingCompatible instanceof Boolean) {
                return (Boolean) boxingCompatible;
            }
        } catch (Exception ignored) {
        }
        return Objects.equals(getTypeName(sourceType), getTypeName(targetType));
    }

    private static void normalizeConditionalNullOperandStack(Object conditionalExpression, Object codeStream) throws Exception {
        Object operandStack = findField(codeStream.getClass(), "operandStack") != null
                ? getFieldValue(codeStream, "operandStack")
                : null;
        if (operandStack == null) {
            return;
        }
        Object top = invokeMethod(operandStack, "peek");
        Class<?> typeBindingClass = loadClass(TYPE_BINDING_CLASS, conditionalExpression);
        Field nullField = findField(typeBindingClass, "NULL");
        Object nullBinding = nullField != null ? nullField.get(null) : null;
        if (top == nullBinding) {
            Object resolvedType = getFieldValue(conditionalExpression, "resolvedType");
            if (resolvedType != null) {
                invokeMethod(operandStack, "cast", resolvedType);
            }
        }
    }

    private static Object createBranchLabel(Object codeStream) throws Exception {
        Class<?> branchLabelClass = loadClass("org.eclipse.jdt.internal.compiler.codegen.BranchLabel", codeStream);
        Class<?> codeStreamClass = loadClass("org.eclipse.jdt.internal.compiler.codegen.CodeStream", codeStream);
        return branchLabelClass.getDeclaredConstructor(codeStreamClass).newInstance(codeStream);
    }

    private static boolean isConditionalExpression(Object expression) {
        return expression != null && expression.getClass().getName().endsWith("ConditionalExpression");
    }

    private static boolean isElvisConditionalExpression(Object conditionalExpression) throws Exception {
        return conditionalExpression != null
                && isElvisMarkerInvocation(getFieldValue(conditionalExpression, "condition"));
    }

    private static boolean isElvisMarkerInvocation(Object expression) throws Exception {
        if (expression == null || !expression.getClass().getName().endsWith("MessageSend")) {
            return false;
        }
        return ELVIS_MARKER_SELECTOR.equals(getSelectorName(expression));
    }

    private static boolean isElvisPlaceholderExpression(Object expression) throws Exception {
        return matchesQualifiedAstName(expression, BI_OP_QUALIFIED_NAME + "." + ELVIS_MARKER_SELECTOR);
    }

    private static boolean matchesQualifiedAstName(Object expression, String qualifiedName) throws Exception {
        if (expression == null || qualifiedName == null || qualifiedName.isEmpty()) {
            return false;
        }
        String simpleName = expression.getClass().getSimpleName();
        if ("SingleNameReference".equals(simpleName) || "QualifiedNameReference".equals(simpleName)) {
            return qualifiedName.equals(describeReferenceName(expression, getFieldValue(expression, "binding")));
        }
        if ("FieldReference".equals(simpleName)) {
            int split = qualifiedName.lastIndexOf('.');
            if (split < 0) {
                return false;
            }
            String token = describeReferenceName(expression, getFieldValue(expression, "binding"));
            if (!qualifiedName.substring(split + 1).equals(token)) {
                return false;
            }
            return matchesQualifiedAstName(getFieldValue(expression, "receiver"), qualifiedName.substring(0, split));
        }
        return false;
    }

    private static boolean isBaseType(Object typeBinding) {
        if (typeBinding == null) {
            return false;
        }
        try {
            Object result = invokeMethod(typeBinding, "isBaseType");
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Exception ignored) {
        }
        String typeName = getTypeName(typeBinding);
        return "byte".equals(typeName)
                || "short".equals(typeName)
                || "int".equals(typeName)
                || "long".equals(typeName)
                || "float".equals(typeName)
                || "double".equals(typeName)
                || "char".equals(typeName)
                || "boolean".equals(typeName);
    }

    private static Object resolveRewrittenInvocationActualReceiverType(
            Object scope,
            Object invocationSite,
            Object originalReceiverExpression,
            Object fallbackReceiverType
    ) throws Exception {
        // The receiver type passed into extension lookup is captured by Scope before any
        // facade or static-holder rewrite. It is therefore the earliest and most reliable
        // type for generic inference. In particular, a chained MessageSend can have its
        // AST state revisited by JDT while candidates are probed, whereas this value still
        // carries List<E> (or another parameterized receiver) from the original lookup.
        if (fallbackReceiverType != null && !isProblem(fallbackReceiverType)) {
            return fallbackReceiverType;
        }
        // MessageSend.actualReceiverType is owned by JDT's lookup path. During extension
        // lookup it may already contain the synthetic facade/holder type. The receiver
        // expression, however, has been resolved before method lookup and therefore keeps
        // the parameterized result of a chained call (for example List<E> from findAll()).
        // Preserve that type before replacing the receiver with the static extension holder;
        // it is the leading argument used by JDT generic inference for later lambdas.
        Object expressionReceiverType = resolveRecoverableReceiverType(originalReceiverExpression);
        if (expressionReceiverType != null && !isProblem(expressionReceiverType)) {
            return expressionReceiverType;
        }
        if (scope != null && originalReceiverExpression != null && originalReceiverExpression != invocationSite) {
            tryResolveReceiverExpressionBeforeStaticRewrite(originalReceiverExpression, scope);
            expressionReceiverType = resolveRecoverableReceiverType(originalReceiverExpression);
            if (expressionReceiverType != null && !isProblem(expressionReceiverType)) {
                return expressionReceiverType;
            }
        }
        if (invocationSite != null && findField(invocationSite.getClass(), "actualReceiverType") != null) {
            Object actualReceiverType = getFieldValue(invocationSite, "actualReceiverType");
            if (actualReceiverType != null && !isProblem(actualReceiverType)) {
                return actualReceiverType;
            }
        }
        return fallbackReceiverType;
    }

    public static Object filterResolvedExtensionProblems(Object compilationResult, Object problems) {
        if (compilationResult == null || problems == null || !problems.getClass().isArray()) {
            return problems;
        }
        try {
            int length = Array.getLength(problems);
            if (length == 0) {
                return problems;
            }
            List<Object> retained = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                Object problem = Array.get(problems, index);
                boolean suppress = problem != null
                        && (shouldSuppressOptionalFunctionalRecordedProblem(compilationResult, problem)
                        || shouldSuppressSuccessfulClassTargetRecordedProblem(compilationResult, problem)
                        || shouldSuppressSuccessfulFunctionalLocalFieldRecordedProblem(compilationResult, problem)
                        || shouldSuppressResolvableLocalReferenceRecordedProblem(compilationResult, problem)
                        || shouldSuppressSuccessfulExtensionFunctionalReturnRecordedProblem(compilationResult, problem)
                        || shouldSuppressSuccessfulExtensionUndefinedRecordedProblem(compilationResult, problem)
                        || shouldSuppressSuccessfulFunctionalWrapperRecordedProblem(compilationResult, problem));
                if (problem != null && !suppress) {
                    retained.add(problem);
                }
            }
            if (retained.size() == length) {
                return problems;
            }
            Object filtered = Array.newInstance(problems.getClass().getComponentType(), retained.size());
            for (int index = 0; index < retained.size(); index++) {
                Array.set(filtered, index, retained.get(index));
            }
            return filtered;
        } catch (Exception ignored) {
            return problems;
        }
    }

    private static void tryResolveReceiverExpressionBeforeStaticRewrite(Object receiverExpression, Object scope) {
        if (receiverExpression == null || scope == null) {
            return;
        }
        try {
            Object currentType = resolveRecoverableReceiverType(receiverExpression);
            if (currentType != null && !isProblem(currentType)) {
                return;
            }
            // Resolve only the already-parsed receiver subtree. This runs before the outer
            // invocation is rewritten, so JDT never sees a synthetic holder as the receiver
            // from which it should infer the extension method's type variables.
            tryResolveAstNode(receiverExpression, scope);
        } catch (Exception ignored) {
        }
    }

    private static void logReceiverRewriteIfGenericReceiverType(
            String selectorName,
            Object invocationSite,
            Object receiverExpression,
            Object receiverType,
            Object preservedActualReceiverType,
            Object[] rewrittenArguments,
            Object[] rewrittenArgumentTypes,
            Object[] parameters
    ) throws Exception {
        if (invocationSite == null
                || rewrittenArguments == null
                || rewrittenArguments.length <= 1
                || rewrittenArgumentTypes == null
                || rewrittenArgumentTypes.length == 0) {
            return;
        }
        boolean hasFunctionalArgument = false;
        for (int index = 1; index < rewrittenArguments.length; index++) {
            if (isFunctionalInvocationArgument(rewrittenArguments[index])) {
                hasFunctionalArgument = true;
                break;
            }
        }
        if (!hasFunctionalArgument) {
            return;
        }
        Object receiverArgumentType = rewrittenArgumentTypes[0];
        if (receiverArgumentType != null && shouldUseConcreteArgumentTypeForSubstitution(receiverArgumentType)) {
            return;
        }
        String key = selectorName + "|" + describeSourceRange(invocationSite);
        if (!RECEIVER_REWRITE_DIAGNOSTIC_KEYS.add(key)) {
            return;
        }
        Util.log("[ZirconCore] receiverRewrite genericReceiver"
                + ": selector=" + selectorName
                + ", source=" + describeSourceRange(invocationSite)
                + ", receiverType=" + describeTypeDebug(receiverType)
                + ", preservedActualReceiverType=" + describeTypeDebug(preservedActualReceiverType)
                + ", receiverResolvedType=" + describeTypeDebug(getFieldValue(receiverExpression, "resolvedType"))
                + ", receiverActualReceiverType=" + describeTypeDebug(getFieldValue(receiverExpression, "actualReceiverType"))
                + ", rewrittenReceiverArgumentType=" + describeTypeDebug(receiverArgumentType)
                + ", parameter0=" + describeTypeDebug(parameters != null && parameters.length > 0 ? parameters[0] : null)
                + ", argumentTypes=" + describeTypeArrayDetailed(rewrittenArgumentTypes));
    }

    private static void logFunctionalRefreshIfExpectedTypeStillGeneric(
            String selectorName,
            Object invocationSite,
            int argumentIndex,
            Object expectedType,
            Object[] parameters,
            Object[] argumentTypes
    ) throws Exception {
        if (!Util.isDebugEnabled()
                || expectedType == null
                || shouldUseConcreteArgumentTypeForSubstitution(expectedType)) {
            return;
        }
        String key = selectorName + "|" + describeSourceRange(invocationSite) + "|" + argumentIndex;
        if (!FUNCTIONAL_EXPECTED_TYPE_DIAGNOSTIC_KEYS.add(key)) {
            return;
        }
        Util.log("[ZirconCore] functionalRefresh genericExpectedType"
                + ": selector=" + selectorName
                + ", source=" + describeSourceRange(invocationSite)
                + ", index=" + argumentIndex
                + ", expectedType=" + describeTypeDebug(expectedType)
                + ", parameterType=" + describeTypeDebug(parameters != null && argumentIndex < parameters.length ? parameters[argumentIndex] : null)
                + ", receiverParameterType=" + describeTypeDebug(parameters != null && parameters.length > 0 ? parameters[0] : null)
                + ", receiverArgumentType=" + describeTypeDebug(argumentTypes != null && argumentTypes.length > 0 ? argumentTypes[0] : null)
                + ", argumentTypes=" + describeTypeArrayDetailed(argumentTypes));
    }

    private static void refreshPostRewriteFunctionalArguments(Object scope, Object invocationSite, Object binding) throws Exception {
        if (scope == null || invocationSite == null || binding == null) {
            return;
        }
        String selectorName = getSelectorName(binding);
        Object compilationResult = resolveCompilationResultFromScope(scope);
        String fileName = describeCompilationUnitFileName(compilationResult);
        boolean debugProblemFile = isProblemFileLoggingEnabled(fileName);
        Object rawArguments = getFieldValue(invocationSite, "arguments");
        Object rawParameters = getFieldValue(binding, "parameters");
        if (!(rawArguments instanceof Object[]) || !(rawParameters instanceof Object[])) {
            return;
        }
        Object[] arguments = (Object[]) rawArguments;
        Object[] parameters = (Object[]) rawParameters;
        if (arguments.length <= 1 || parameters.length <= 1) {
            return;
        }
        Object[] argumentTypes = computeEffectiveInvocationArgumentTypes(
                parameters,
                getFieldValue(invocationSite, "argumentTypes") instanceof Object[]
                        ? (Object[]) getFieldValue(invocationSite, "argumentTypes")
                        : null,
                arguments
        );
        Object preservedReceiverType = findField(invocationSite.getClass(), "actualReceiverType") != null
                ? getFieldValue(invocationSite, "actualReceiverType")
                : null;
        if (preservedReceiverType != null
                && !isProblem(preservedReceiverType)
                && argumentTypes.length == parameters.length
                && argumentTypes.length > 0) {
            // A parameterized receiver such as List<E> is useful even though E is not a
            // fully concrete type. Do not let generic argument normalization replace it
            // with the synthetic static holder stored on the rewritten receiver AST.
            argumentTypes[0] = preservedReceiverType;
        }
        if (debugProblemFile) {
            Util.log("[ZirconCore] functionalRefreshStart"
                    + ": file=" + fileName
                    + ", selector=" + selectorName
                    + ", source=" + readIntField(invocationSite, "sourceStart") + "-" + readIntField(invocationSite, "sourceEnd")
                    + ", binding=" + describeMethodBindingDetailed(binding)
                    + ", parameters=" + describeTypeArrayDetailed(parameters)
                    + ", argumentTypes=" + describeTypeArrayDetailed(argumentTypes)
                    + ", descriptors=" + describeArgumentDescriptors(arguments));
        }
        boolean replacedArguments = false;
        boolean refreshedTypes = false;
        for (int index = 1; index < arguments.length && index < parameters.length; index++) {
            Object argument = arguments[index];
            Object expectedType = parameters[index];
            if (argument == null || expectedType == null
                    || !shouldRefreshFunctionalArgument(scope, expectedType, invocationSite, binding, index)) {
                continue;
            }
            expectedType = specializeFunctionalExpectedTypeForReceiverWrapper(
                    scope,
                    binding,
                    invocationSite,
                    expectedType,
                    parameters
            );
            if (parameters.length > 0
                    && argumentTypes.length > 0
                    && argumentTypes[0] != null
                    && !isProblem(argumentTypes[0])) {
                Object receiverSpecializedType = substituteReceiverTypeVariables(
                        scope,
                        parameters[0],
                        argumentTypes[0],
                        expectedType
                );
                if (receiverSpecializedType != null && !isProblem(receiverSpecializedType)) {
                    expectedType = receiverSpecializedType;
                }
            }
            Object returnSpecializedType = resolveConcreteFunctionalArgumentTypeFromBody(
                    scope,
                    expectedType,
                    argument
            );
            if (returnSpecializedType != null && !isProblem(returnSpecializedType)) {
                expectedType = returnSpecializedType;
            }
            // The extension binding and its specialized SAM are already proven at this
            // point. Register them before entering JDT's lambda resolver, because JDT may
            // report a transient target-type problem from inside resolveExpressionExpecting
            // before returning the successfully resolved lambda.
            rememberSuccessfulFunctionalArgumentRange(
                    scope,
                    expectedType,
                    argument,
                    invocationSite,
                    binding
            );
            rememberProvenExtensionFunctionalTargetRange(scope, expectedType, argument);
            // Static receiver rewrites keep the original receiver in slot 0, and that slot
            // often carries the concrete type arguments needed by later lambda parameters.
            Object specializedExpectedType = specializeFunctionalExpectedTypeFromArgumentTypes(
                    scope,
                    expectedType,
                    parameters,
                    argumentTypes,
                    arguments,
                    index,
                    false
            );
            if (specializedExpectedType != null) {
                expectedType = specializedExpectedType;
            }
            logFunctionalRefreshIfExpectedTypeStillGeneric(
                    selectorName,
                    invocationSite,
                    index,
                    expectedType,
                    parameters,
                    argumentTypes
            );
            if (debugProblemFile) {
                Util.log("[ZirconCore] functionalRefreshArg"
                        + ": file=" + fileName
                        + ", selector=" + selectorName
                        + ", source=" + readIntField(invocationSite, "sourceStart") + "-" + readIntField(invocationSite, "sourceEnd")
                        + ", index=" + index
                        + ", argumentClass=" + argument.getClass().getName()
                        + ", parameterType=" + describeTypeDebug(parameters[index])
                        + ", effectiveExpectedType=" + describeTypeDebug(expectedType)
                        + ", argumentType=" + (index < argumentTypes.length ? describeTypeDebug(argumentTypes[index]) : "null"));
            }
            if (isAnonymousOrLocalFunctionalContext(argument)) {
                continue;
            }
            if (hasReferenceExpressionFunctionalBody(argument)) {
                continue;
            }
            if (shouldAvoidReresolvingFunctionalArgument(argument)) {
                stabilizeFunctionalArgumentWithoutReresolve(argument, expectedType, scope);
                rememberSuccessfulFunctionalArgumentRange(scope, expectedType, argument, invocationSite, binding);
                Object resolvedType = getFieldValue(argument, "resolvedType");
                if (resolvedType != null && index < argumentTypes.length) {
                    argumentTypes[index] = resolvedType;
                    refreshedTypes = true;
                }
                continue;
            }
            if (shouldDeferComplexPostRewriteFunctionalRefresh(argument, expectedType)) {
                if (debugProblemFile) {
                    Util.log("[ZirconCore] functionalRefresh deferComplexPostRewrite"
                            + ": file=" + fileName
                            + ", selector=" + selectorName
                            + ", source=" + readIntField(invocationSite, "sourceStart") + "-" + readIntField(invocationSite, "sourceEnd")
                            + ", index=" + index
                            + ", expectedType=" + describeTypeDebug(expectedType)
                            + ", argumentSource=" + describeSourceRange(argument));
                }
                stabilizeFunctionalArgumentWithoutReresolve(argument, expectedType, scope);
                rememberSuccessfulFunctionalArgumentRange(scope, expectedType, argument, invocationSite, binding);
                Object resolvedType = getFieldValue(argument, "resolvedType");
                if (resolvedType != null && index < argumentTypes.length) {
                    argumentTypes[index] = resolvedType;
                    refreshedTypes = true;
                }
                continue;
            }
            if (shouldPreferStabilizeOnlyFunctionalRefresh(scope, argument, expectedType)) {
                stabilizeFunctionalArgumentWithoutReresolve(argument, expectedType, scope);
                Object resolvedType = getFieldValue(argument, "resolvedType");
                if (resolvedType != null && index < argumentTypes.length) {
                    argumentTypes[index] = resolvedType;
                    refreshedTypes = true;
                }
                continue;
            }
            Object reResolved;
            try {
                reResolved = invokeMethod(argument, "resolveExpressionExpecting", expectedType, scope);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (shouldIgnoreFunctionalRefreshFailure(e.getCause())) {
                    stabilizeFunctionalArgumentState(argument, expectedType, scope);
                    continue;
                }
                throw e;
            } catch (NullPointerException e) {
                if (shouldIgnoreFunctionalRefreshFailure(e)) {
                    stabilizeFunctionalArgumentState(argument, expectedType, scope);
                    continue;
                }
                throw e;
            }
            if (reResolved == null) {
                continue;
            }
            logFunctionalRefreshNestedLambdaState(selectorName, "postRewrite", index, argument, reResolved, expectedType);
            ensureLambdaBindingResolved(reResolved, expectedType, scope);
            stabilizeFunctionalArgumentState(reResolved, expectedType, scope);
            rebindProblemFieldLocalReferences(reResolved, scope, scope, new IdentityHashMap<>(), 0);
            restoreOriginalLocalReferenceBindings(reResolved, argument, 0);
            rememberSuccessfulFunctionalArgumentRange(scope, expectedType, reResolved, invocationSite, binding);
            boolean promotedProblematicLambda = false;
            boolean retainProblematicResolvedLambda = false;
            if (containsProblemAstState(scope, reResolved, new IdentityHashMap<>(), 0)) {
                retainProblematicResolvedLambda = shouldKeepProblematicResolvedFunctionalArgument(scope, reResolved);
            }
            if (!retainProblematicResolvedLambda
                    && containsProblemAstState(scope, reResolved, new IdentityHashMap<>(), 0)
                    && (!repairFunctionalArgumentProblemState(reResolved, expectedType, scope)
                    || containsProblemAstState(scope, reResolved, new IdentityHashMap<>(), 0))) {
                retainProblematicResolvedLambda = shouldKeepProblematicResolvedFunctionalArgument(scope, reResolved);
                if (!retainProblematicResolvedLambda) {
                    promotedProblematicLambda = tryPromoteProblematicResolvedLambda(scope, argument, reResolved, expectedType);
                }
                if (!promotedProblematicLambda && !retainProblematicResolvedLambda) {
                    if (!containsProblemAstState(scope, reResolved, new IdentityHashMap<>(), 0)) {
                        retainProblematicResolvedLambda = true;
                    } else {
                        logFunctionalRefreshProblemState(scope, selectorName, "postRewrite", index, argument, reResolved, expectedType);
                        continue;
                    }
                }
            }
            if (promotedProblematicLambda) {
                reResolved = argument;
            }
            logNullLambdaBindingIfNeeded(selectorName, "postRewrite", index, argument, reResolved, expectedType);
            if (!retainProblematicResolvedLambda && shouldRetainOriginalFunctionalArgument(argument, reResolved)) {
                mergeResolvedFunctionalArgumentState(argument, reResolved);
                reResolved = argument;
            }
            stabilizeFunctionalArgumentState(reResolved, expectedType, scope);
            if (reResolved != argument) {
                arguments[index] = reResolved;
                replacedArguments = true;
            }
            Object resolvedType = getFieldValue(arguments[index], "resolvedType");
            if (resolvedType != null && index < argumentTypes.length) {
                argumentTypes[index] = resolvedType;
                refreshedTypes = true;
            }
        }
        if (replacedArguments) {
            setFieldValue(invocationSite, "arguments", arguments);
        }
        if (refreshedTypes) {
            setFieldValue(invocationSite, "argumentTypes", argumentTypes);
        }
        rebindProblemFieldLocalReferences(invocationSite, scope, scope, new IdentityHashMap<>(), 0);
        if (replacedArguments || refreshedTypes) {
            primeArgumentDescriptorState(arguments);
            if (shouldClearInvocationArgumentErrors(scope, arguments)) {
                setFieldValue(invocationSite, "argumentsHaveErrors", false);
            }
        }
        if ((replacedArguments || refreshedTypes) && shouldTraceSelector(selectorName)) {
            Util.log("[ZirconCore] trace selector=" + selectorName
                    + ", postRewriteRefreshedArgs"
                    + ", replaced=" + replacedArguments
                    + ", argumentTypes=" + describeTypeArrayDetailed(argumentTypes)
                    + ", descriptors=" + describeArgumentDescriptors(arguments));
        }
    }

    private static void rememberProvenExtensionFunctionalTargetRange(
            Object scope,
            Object expectedType,
            Object argument
    ) throws Exception {
        if (scope == null || expectedType == null || argument == null || !isFunctionalInvocationArgument(argument)) {
            return;
        }
        Object descriptor = invokeMethod(expectedType, "getSingleAbstractMethod", scope, true);
        if (descriptor == null || isProblem(descriptor)) {
            return;
        }
        Object compilationResult = resolveCompilationResultFromScope(scope);
        String fileName = describeCompilationUnitFileName(compilationResult);
        int start = readIntField(argument, "sourceStart");
        int end = readIntField(argument, "sourceEnd");
        if (fileName != null && start >= 0 && end >= start) {
            PROVEN_EXTENSION_FUNCTIONAL_TARGET_RANGES.add(fileName + "|" + start + "|" + end);
        }
    }

    private static boolean shouldDeferComplexPostRewriteFunctionalRefresh(Object argument, Object expectedType) throws Exception {
        if (!isLambdaExpression(argument)
                || expectedType == null
                || shouldUseConcreteArgumentTypeForSubstitution(expectedType)) {
            return false;
        }
        return containsNestedLambdaExpression(argument, false, new IdentityHashMap<>(), 0);
    }

    private static Object specializeFunctionalExpectedTypeFromArgumentTypes(
            Object scope,
            Object expectedType,
            Object[] parameters,
            Object[] argumentTypes,
            Object[] arguments,
            int currentIndex,
            boolean skipLeadingReceiver
    ) throws Exception {
        if (scope == null || expectedType == null || parameters == null || argumentTypes == null || arguments == null) {
            return expectedType;
        }
        Map<Object, Object> substitutions = buildArgumentTypeSubstitutions(
                scope,
                parameters,
                argumentTypes,
                arguments,
                currentIndex,
                skipLeadingReceiver
        );
        if (substitutions.isEmpty()) {
            return expectedType;
        }
        Object specialized = substituteType(scope, expectedType, substitutions);
        return specialized != null ? specialized : expectedType;
    }

    private static Object[] computeEffectiveInvocationArgumentTypes(
            Object[] parameters,
            Object[] argumentTypes,
            Object[] arguments
    ) throws Exception {
        if (arguments == null) {
            return new Object[0];
        }
        Object[] effectiveTypes = argumentTypes != null
                ? Arrays.copyOf(argumentTypes, arguments.length)
                : parameters != null
                ? Arrays.copyOf(parameters, arguments.length)
                : new Object[arguments.length];
        for (int index = 0; index < arguments.length; index++) {
            Object concreteType = resolveConcreteInvocationArgumentType(
                    index < effectiveTypes.length ? effectiveTypes[index] : null,
                    arguments[index]
            );
            if (concreteType != null && index < effectiveTypes.length) {
                effectiveTypes[index] = concreteType;
            }
        }
        return effectiveTypes;
    }

    private static Map<Object, Object> buildArgumentTypeSubstitutions(
            Object scope,
            Object[] parameterTypes,
            Object[] argumentTypes,
            Object[] arguments,
            int skippedIndex,
            boolean skipLeadingReceiver
    ) throws Exception {
        Map<Object, Object> substitutions = new LinkedHashMap<>();
        if (parameterTypes == null || argumentTypes == null || arguments == null) {
            return substitutions;
        }
        int limit = Math.min(parameterTypes.length, Math.min(argumentTypes.length, arguments.length));
        int startIndex = skipLeadingReceiver ? 1 : 0;
        for (int index = startIndex; index < limit; index++) {
            if (index == skippedIndex) {
                continue;
            }
            Object parameterType = parameterTypes[index];
            Object argument = arguments[index];
            Object argumentType = resolveConcreteInvocationArgumentType(argumentTypes[index], argument);
            if ((argumentType == null || !shouldUseConcreteArgumentTypeForSubstitution(argumentType))
                    && scope != null
                    && isFunctionalInvocationArgument(argument)) {
                argumentType = resolveConcreteFunctionalArgumentTypeFromBody(scope, parameterType, argument);
            }
            if (parameterType == null
                    || argumentType == null
                    || argument == null
                    || !shouldUseConcreteArgumentTypeForSubstitution(argumentType)) {
                continue;
            }
            try {
                collectReceiverSubstitutions(parameterType, argumentType, substitutions);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (!(e.getCause() instanceof ClassCastException)) {
                    throw e;
                }
            } catch (ClassCastException ignored) {
            }
        }
        return substitutions;
    }

    private static Object resolveConcreteFunctionalArgumentTypeFromBody(
            Object scope,
            Object parameterType,
            Object argument
    ) throws Exception {
        if (scope == null || parameterType == null || argument == null || !isFunctionalInvocationArgument(argument)) {
            return null;
        }
        Object descriptor = invokeMethod(parameterType, "getSingleAbstractMethod", scope, true);
        if (descriptor == null || isProblem(descriptor)) {
            return null;
        }
        Object descriptorReturnType = getFieldValue(descriptor, "returnType");
        if (descriptorReturnType == null || isProblem(descriptorReturnType)) {
            return null;
        }
        List<Object> returnExpressions = collectLambdaReturnExpressions(argument);
        if (returnExpressions.isEmpty()) {
            return null;
        }
        Map<Object, Object> substitutions = new LinkedHashMap<>();
        for (Object returnExpression : returnExpressions) {
            if (returnExpression == null) {
                continue;
            }
            Object actualReturnType = null;
            if (returnExpression.getClass().getName().endsWith("CastExpression")) {
                Object castTypeReference = getFieldValue(returnExpression, "type");
                if (castTypeReference != null) {
                    actualReturnType = getFieldValue(castTypeReference, "resolvedType");
                    if (actualReturnType == null || isProblem(actualReturnType)) {
                        try {
                            actualReturnType = invokeMethod(castTypeReference, "resolveType", scope);
                        } catch (Exception ignored) {
                            actualReturnType = null;
                        }
                    }
                }
            }
            if (actualReturnType == null || isProblem(actualReturnType)) {
                Object existingReturnType = findField(returnExpression.getClass(), "resolvedType") != null
                        ? getFieldValue(returnExpression, "resolvedType")
                        : null;
                actualReturnType = resolveConcreteInvocationArgumentType(existingReturnType, returnExpression);
            }
            if (actualReturnType == null || isProblem(actualReturnType)) {
                continue;
            }
            collectReceiverSubstitutions(descriptorReturnType, actualReturnType, substitutions);
        }
        if (substitutions.isEmpty()) {
            return null;
        }
        Object specialized = substituteType(scope, parameterType, substitutions);
        return specialized != null ? specialized : null;
    }

    private static List<Object> collectLambdaReturnExpressions(Object lambdaExpression) throws Exception {
        if (!isLambdaExpression(lambdaExpression)) {
            return Collections.emptyList();
        }
        Object body = getFieldValue(lambdaExpression, "body");
        if (body == null) {
            return Collections.emptyList();
        }
        if (!body.getClass().getName().endsWith("Block")) {
            return Collections.singletonList(body);
        }
        Object rawStatements = getFieldValue(body, "statements");
        if (!(rawStatements instanceof Object[])) {
            return Collections.emptyList();
        }
        List<Object> result = new ArrayList<>();
        for (Object statement : (Object[]) rawStatements) {
            if (statement == null || !statement.getClass().getName().endsWith("ReturnStatement")) {
                continue;
            }
            Object expression = getFieldValue(statement, "expression");
            if (expression != null) {
                result.add(expression);
            }
        }
        return result;
    }

    private static Object resolveConcreteInvocationArgumentType(Object candidateType, Object argument) throws Exception {
        Object concreteType = pickConcreteArgumentType(candidateType);
        if (concreteType != null) {
            return concreteType;
        }
        if (argument == null) {
            return null;
        }
        boolean functionalArgument = isFunctionalInvocationArgument(argument);
        concreteType = pickConcreteArgumentType(getFieldValue(argument, "resolvedType"));
        if (concreteType != null) {
            return concreteType;
        }
        concreteType = pickConcreteArgumentType(getFieldValue(argument, "expectedType"));
        if (concreteType != null) {
            return concreteType;
        }
        Object binding = getFieldValue(argument, "binding");
        concreteType = pickConcreteArgumentType(binding != null ? getFieldValue(binding, "type") : null);
        if (concreteType != null) {
            return concreteType;
        }
        if (functionalArgument) {
            return null;
        }
        return pickConcreteArgumentType(getFieldValue(argument, "actualReceiverType"));
    }

    private static Object resolveRecoverableInvocationArgumentType(Object candidateType, Object argument) throws Exception {
        Object concreteType = pickConcreteArgumentType(candidateType);
        if (concreteType != null) {
            return concreteType;
        }
        if (argument == null) {
            return null;
        }
        boolean functionalArgument = isFunctionalInvocationArgument(argument);
        concreteType = pickConcreteArgumentType(getFieldValue(argument, "resolvedType"));
        if (concreteType != null) {
            return concreteType;
        }
        concreteType = pickConcreteArgumentType(getFieldValue(argument, "expectedType"));
        if (concreteType != null) {
            return concreteType;
        }
        Object binding = getFieldValue(argument, "binding");
        concreteType = pickConcreteArgumentType(binding != null ? getFieldValue(binding, "type") : null);
        if (concreteType != null) {
            return concreteType;
        }
        if (functionalArgument) {
            return null;
        }
        return pickConcreteArgumentType(getFieldValue(argument, "actualReceiverType"));
    }

    private static Object pickConcreteArgumentType(Object candidateType) throws Exception {
        if (candidateType == null || !shouldUseConcreteArgumentTypeForSubstitution(candidateType)) {
            return null;
        }
        Object normalizedType = normalizeMethodLookupType(candidateType);
        return normalizedType != null ? normalizedType : candidateType;
    }

    private static Object specializeHeuristicFunctionalFacadeBinding(
            Object scope,
            Object binding,
            Object[] argumentTypes,
            Object invocationSite
    ) throws Exception {
        if (scope == null || binding == null || argumentTypes == null || invocationSite == null) {
            return binding;
        }
        Object rawParameters = getFieldValue(binding, "parameters");
        Object rawArguments = getFieldValue(invocationSite, "arguments");
        if (!(rawParameters instanceof Object[]) || !(rawArguments instanceof Object[])) {
            return binding;
        }
        Object[] parameters = (Object[]) rawParameters;
        Object[] arguments = (Object[]) rawArguments;
        Object[] effectiveArgumentTypes = computeEffectiveInvocationArgumentTypes(parameters, argumentTypes, arguments);
        Map<Object, Object> substitutions = buildArgumentTypeSubstitutions(
                scope,
                parameters,
                effectiveArgumentTypes,
                arguments,
                -1,
                false
        );
        for (Map.Entry<Object, Object> entry : buildReturnTypeExpectedSubstitutions(binding, invocationSite).entrySet()) {
            substitutions.putIfAbsent(entry.getKey(), entry.getValue());
        }
        if (substitutions.isEmpty()) {
            return binding;
        }
        Object[] specializedParameters = substituteTypeBindings(scope, parameters, substitutions);
        Object specializedReturnType = substituteType(scope, getFieldValue(binding, "returnType"), substitutions);
        Object[] specializedThrownExceptions = substituteTypeBindings(scope, (Object[]) getFieldValue(binding, "thrownExceptions"), substitutions);
        if (specializedParameters != null) {
            setFieldValue(binding, "parameters", specializedParameters);
        }
        if (specializedReturnType != null) {
            setFieldValue(binding, "returnType", specializedReturnType);
        }
        if (specializedThrownExceptions != null) {
            setFieldValue(binding, "thrownExceptions", specializedThrownExceptions);
        }
        resetMethodBindingCaches(binding);
        return binding;
    }

    private static Map<Object, Object> buildReturnTypeExpectedSubstitutions(Object binding, Object invocationSite) throws Exception {
        Map<Object, Object> substitutions = new LinkedHashMap<>();
        if (binding == null || invocationSite == null || findField(invocationSite.getClass(), "expectedType") == null) {
            return substitutions;
        }
        Object returnType = getFieldValue(binding, "returnType");
        Object expectedType = getFieldValue(invocationSite, "expectedType");
        if (returnType == null || expectedType == null || isProblem(returnType) || isProblem(expectedType)) {
            return substitutions;
        }
        try {
            collectExpectedTypeSubstitutions(returnType, expectedType, substitutions);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (!(e.getCause() instanceof ClassCastException)) {
                throw e;
            }
        } catch (ClassCastException ignored) {
            return substitutions;
        }
        return substitutions;
    }

    private static void collectExpectedTypeSubstitutions(
            Object templateType,
            Object actualType,
            Map<Object, Object> substitutions
    ) throws Exception {
        if (templateType == null || actualType == null) {
            return;
        }
        Object normalizedActualType = normalizeMethodLookupType(actualType);
        if (normalizedActualType != null) {
            actualType = normalizedActualType;
        }
        if (isTypeVariable(templateType)) {
            if (shouldUseConcreteArgumentTypeForSubstitution(actualType)) {
                substitutions.putIfAbsent(templateType, actualType);
            }
            return;
        }
        Object[] templateArguments = safeGetTypeArguments(templateType);
        Object[] actualArguments = safeGetTypeArguments(actualType);
        if (templateArguments != null && actualArguments != null && templateArguments.length == actualArguments.length) {
            for (int i = 0; i < templateArguments.length; i++) {
                collectExpectedTypeSubstitutions(templateArguments[i], actualArguments[i], substitutions);
            }
            return;
        }
        if (safeIsArrayType(templateType) && safeIsArrayType(actualType)) {
            Object templateLeaf = invokeMethod(templateType, "leafComponentType");
            Object actualLeaf = invokeMethod(actualType, "leafComponentType");
            if (templateLeaf != null && actualLeaf != null) {
                collectExpectedTypeSubstitutions(templateLeaf, actualLeaf, substitutions);
            }
        }
    }

    private static boolean shouldUseConcreteArgumentTypeForSubstitution(Object argumentType) throws Exception {
        return isConcreteSubstitutionType(argumentType, new IdentityHashMap<>(), 0);
    }

    private static boolean isConcreteSubstitutionType(
            Object argumentType,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (argumentType == null
                || depth > 12
                || visited.put(argumentType, Boolean.TRUE) != null
                || isProblem(argumentType)
                || isTypeVariable(argumentType)) {
            return false;
        }
        Object wildcard = invokeOptionalMethod(argumentType, "isWildcard");
        if (wildcard instanceof Boolean && (Boolean) wildcard) {
            return false;
        }
        Object capture = invokeOptionalMethod(argumentType, "isCapture");
        if (capture instanceof Boolean && (Boolean) capture) {
            return false;
        }
        Object captureType = invokeOptionalMethod(argumentType, "isCaptureType");
        if (captureType instanceof Boolean && (Boolean) captureType) {
            return false;
        }
        Object[] typeArguments = safeGetTypeArguments(argumentType);
        if (typeArguments != null) {
            for (Object typeArgument : typeArguments) {
                if (typeArgument != null && !isConcreteSubstitutionType(typeArgument, visited, depth + 1)) {
                    return false;
                }
            }
        }
        if (safeIsArrayType(argumentType)) {
            Object leafComponent = invokeMethod(argumentType, "leafComponentType");
            if (leafComponent != null
                    && leafComponent != argumentType
                    && !isConcreteSubstitutionType(leafComponent, visited, depth + 1)) {
                return false;
            }
        }
        return true;
    }

    private static Object normalizeMethodLookupType(Object typeBinding) throws Exception {
        Object current = typeBinding;
        for (int depth = 0; depth < 4; depth++) {
            if (current == null || isProblem(current)) {
                return null;
            }
            if (shouldUseConcreteArgumentTypeForSubstitution(current)) {
                return current;
            }
            Object lowerBound = getFieldValue(current, "lowerBound");
            if (lowerBound != null && lowerBound != current) {
                current = lowerBound;
                continue;
            }
            Object upperBound = getFieldValue(current, "upperBound");
            if (upperBound != null && upperBound != current) {
                current = upperBound;
                continue;
            }
            Object firstBound = getFieldValue(current, "firstBound");
            if (firstBound != null && firstBound != current) {
                current = firstBound;
                continue;
            }
            Object wildcard = getFieldValue(current, "wildcard");
            if (wildcard != null && wildcard != current) {
                current = wildcard;
                continue;
            }
            return null;
        }
        return null;
    }

    private static boolean isTypeVariableUndefinedProblemMessage(String message) {
        if (message == null
                || !message.startsWith("The method ")
                || !message.contains(" is undefined for the type ")) {
            return false;
        }
        int marker = message.lastIndexOf(" is undefined for the type ");
        if (marker < 0) {
            return false;
        }
        String typeName = message.substring(marker + " is undefined for the type ".length()).trim();
        if (typeName.isEmpty() || typeName.length() > 32 || !Character.isUpperCase(typeName.charAt(0))) {
            return false;
        }
        for (int index = 1; index < typeName.length(); index++) {
            char ch = typeName.charAt(index);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                return false;
            }
        }
        return true;
    }

    private static boolean isFunctionalPlaceholderUndefinedProblemMessage(String message) {
        if (isTypeVariableUndefinedProblemMessage(message)) {
            return true;
        }
        if (message == null
                || !message.startsWith("The method ")
                || !message.contains(" is undefined for the type ")) {
            return false;
        }
        int marker = message.lastIndexOf(" is undefined for the type ");
        if (marker < 0) {
            return false;
        }
        String typeName = message.substring(marker + " is undefined for the type ".length()).trim();
        return "Object".equals(typeName) || typeName.startsWith("capture#") || typeName.startsWith("?");
    }

    private static String extractUndefinedMethodName(String message) {
        if (message == null || !message.startsWith("The method ")) {
            return "";
        }
        int start = "The method ".length();
        int end = message.indexOf('(', start);
        if (end <= start) {
            return "";
        }
        return message.substring(start, end).trim();
    }

    private static boolean typeDefinesMethodNamed(Object typeBinding, String methodName) throws Exception {
        Object lookupType = normalizeMethodLookupType(typeBinding);
        if (lookupType == null || methodName == null || methodName.isEmpty()) {
            return false;
        }
        Object methods = invokeMethod(lookupType, "getMethods", methodName.toCharArray());
        if (!(methods instanceof Object[])) {
            return false;
        }
        for (Object method : (Object[]) methods) {
            if (method != null && !isProblem(method)) {
                return true;
            }
        }
        return false;
    }

    private static Object findSingleInstanceMethodBinding(Object typeBinding, String selector, int parameterCount) throws Exception {
        Object lookupType = normalizeMethodLookupType(typeBinding);
        if (lookupType == null || selector == null || selector.isEmpty()) {
            return null;
        }
        Object methods = invokeMethod(lookupType, "getMethods", selector.toCharArray());
        if (!(methods instanceof Object[])) {
            return null;
        }
        Object match = null;
        for (Object method : (Object[]) methods) {
            if (method == null || isProblem(method)) {
                continue;
            }
            Object parameters = getFieldValue(method, "parameters");
            int methodParameterCount = parameters instanceof Object[] ? ((Object[]) parameters).length : -1;
            if (methodParameterCount != parameterCount) {
                continue;
            }
            boolean isStaticMethod = false;
            try {
                isStaticMethod = Boolean.TRUE.equals(invokeMethod(method, "isStatic"));
            } catch (Exception ignored) {
                isStaticMethod = false;
            }
            if (isStaticMethod) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = method;
        }
        return match;
    }

    private static boolean shouldSuppressSuccessfulExtensionUndefinedProblem(
            Object compilationResult,
            Integer problemId,
            Integer start,
            Integer end,
            String message
    ) throws Exception {
        if (problemId == null || problemId != 67108964 || start == null || end == null || message == null) {
            return false;
        }
        if (shouldSuppressProvableFunctionalExtensionProblem(compilationResult, problemId, start, end, message)) {
            return true;
        }
        String fileName = describeCompilationUnitFileName(compilationResult);
        if (fileName == null) {
            return false;
        }
        String methodName = extractUndefinedMethodName(message);
        boolean shouldSuppress = SUCCESSFUL_EXTENSION_UNDEFINED_RANGES.contains(fileName + "|" + start + "|" + end)
                || hasSuccessfulExtensionSelectorCovering(fileName, start, end, methodName);
        if (!shouldSuppress && isFunctionalPlaceholderUndefinedProblemMessage(message) && !methodName.isEmpty()) {
            String source = getCompilationUnitSource(compilationResult);
            String normalizedContext = removeWhitespace(extractSourceContext(source, start, end, 96));
            if (!looksLikeFunctionalProblemContext(compilationResult, start, end, normalizedContext)) {
                return false;
            }
            for (Object functionalParameterType : getSuccessfulExtensionFunctionalParameterTypesCovering(fileName, start, end)) {
                if (typeDefinesMethodNamed(functionalParameterType, methodName)) {
                    shouldSuppress = true;
                    break;
                }
            }
        }
        if (shouldSuppress && Util.isDebugEnabled()) {
            Util.log("[ZirconCore] suppressSuccessfulExtensionUndefinedProblem"
                    + ": file=" + fileName
                    + ", range=" + start + "-" + end
                    + ", message=" + message);
        }
        return shouldSuppress;
    }

    private static String describeTypeCollection(List<Object> types) {
        if (types == null || types.isEmpty()) {
            return "[]";
        }
        List<String> descriptions = new ArrayList<>(types.size());
        for (Object type : types) {
            descriptions.add(getReadableTypeName(type));
        }
        return descriptions.toString();
    }

    private static Object resynchronizeRewrittenStaticInvocation(Object scope, Object invocationSite, Object binding) throws Exception {
        if (scope == null || invocationSite == null || binding == null) {
            return binding;
        }
        Object rawArgumentTypes = getFieldValue(invocationSite, "argumentTypes");
        Object[] argumentTypes = rawArgumentTypes instanceof Object[] ? (Object[]) rawArgumentTypes : emptyTypeBindingArray(scope);
        Object rebound;
        try {
            rebound = invokeMethod(scope, "computeCompatibleMethod", binding, argumentTypes, invocationSite);
        } catch (Exception ignored) {
            return binding;
        }
        if (rebound == null || isProblem(rebound)) {
            return binding;
        }
        setFieldValue(invocationSite, "binding", rebound);
        Object returnType = getFieldValue(rebound, "returnType");
        if (returnType != null) {
            setFieldValue(invocationSite, "resolvedType", returnType);
        }
        if (shouldTraceSelector(getSelectorName(rebound))) {
            Util.log("[ZirconCore] trace selector=" + getSelectorName(rebound)
                    + ", rewrittenBindingResynced"
                    + ", binding=" + describeMethodBindingDetailed(rebound)
                    + ", argumentTypes=" + describeTypeArrayDetailed(argumentTypes));
        }
        return rebound;
    }

    private static boolean shouldClearInvocationArgumentErrors(Object scope, Object[] arguments) throws Exception {
        if (arguments == null || arguments.length == 0) {
            return true;
        }
        return !containsProblemAstState(scope, arguments, new IdentityHashMap<>(), 0);
    }

    private static boolean containsProblemAstState(
            Object scope,
            Object node,
            Map<Object, Boolean> visited,
            int depth
    ) throws Exception {
        try {
            if (node == null || depth > 16) {
                return false;
            }
            if (node instanceof Object[]) {
                for (Object element : (Object[]) node) {
                    if (containsProblemAstState(scope, element, visited, depth + 1)) {
                        return true;
                    }
                }
                return false;
            }
            if (!isAstNode(node) || visited.put(node, Boolean.TRUE) != null) {
                return false;
            }
            boolean invocationProblem = hasInvocationProblemState(node);
            if (invocationProblem && !isResolvableExtensionInvocation(scope, node)) {
                return true;
            }
            if (!invocationProblem
                    && (hasProblemBindingField(node, "binding")
                    || hasProblemBindingField(node, "resolvedType")
                    || hasProblemBindingField(node, "actualReceiverType")
                    || hasProblemBindingField(node, "descriptor"))) {
                return true;
            }
            for (Object child : getAstChildren(node)) {
                if (containsProblemAstState(scope, child, visited, depth + 1)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            logAstInspectionFailure(scope, node, depth, e);
            return true;
        }
    }

    private static void logAstInspectionFailure(Object scope, Object node, int depth, Throwable error) {
        if (node == null || error == null) {
            return;
        }
        try {
            String key = depth
                    + "|" + describeSourceRange(node)
                    + "|" + node.getClass().getName()
                    + "|" + error.getClass().getName()
                    + "|" + error.getMessage();
            if (!AST_INSPECTION_FAILURE_KEYS.add(key)) {
                return;
            }
            Util.log("[ZirconCore] problemAstInspection failure"
                    + ": depth=" + depth
                    + ", nodeClass=" + node.getClass().getName()
                    + ", source=" + describeSourceRange(node)
                    + ", selector=" + extractInvocationSelectorName(node)
                    + ", scope=" + describeScopeDebug(scope)
                    + ", error=" + error.getClass().getName() + ": " + error.getMessage()
                    + ", stack=" + describeThrowableStack(error, 10));
        } catch (Exception loggingError) {
            Util.log("[ZirconCore] problemAstInspection logging error: "
                    + loggingError.getClass().getName() + ": " + loggingError.getMessage());
        }
    }

    private static boolean hasProblemBindingField(Object node, String fieldName) throws Exception {
        if (node == null || findField(node.getClass(), fieldName) == null) {
            return false;
        }
        Object value = getFieldValue(node, fieldName);
        return value != null && isProblem(value);
    }

    private static boolean hasInvocationArgumentErrors(Object node) throws Exception {
        if (node == null || findField(node.getClass(), "argumentsHaveErrors") == null) {
            return false;
        }
        return Boolean.TRUE.equals(getFieldValue(node, "argumentsHaveErrors"));
    }

    private static boolean hasInvocationProblemState(Object node) throws Exception {
        return hasInvocationArgumentErrors(node)
                || hasUnresolvedInvocationBinding(node)
                || hasProblemBindingField(node, "binding")
                || hasProblemBindingField(node, "resolvedType")
                || hasProblemBindingField(node, "actualReceiverType");
    }

    private static boolean hasUnresolvedInvocationBinding(Object node) throws Exception {
        if (node == null || !isInvocationAstNode(node) || findField(node.getClass(), "binding") == null) {
            return false;
        }
        Object binding = getFieldValue(node, "binding");
        return binding == null || isProblem(binding);
    }

    private static boolean isResolvableExtensionInvocation(Object scope, Object invocationSite) throws Exception {
        if (scope == null || invocationSite == null || !isInvocationAstNode(invocationSite)) {
            return false;
        }
        String selectorName = extractInvocationSelectorName(invocationSite);
        if (selectorName.isEmpty()) {
            return false;
        }
        Object receiverType = resolveInvocationReceiverType(scope, invocationSite);
        if (receiverType == null || isProblem(receiverType)) {
            return false;
        }
        Object compilationUnitScope = getCompilationUnitScope(scope);
        if (compilationUnitScope == null) {
            return false;
        }
        Object[] visibleArgumentTypes = resolveInvocationArgumentTypes(scope, invocationSite);
        List<CandidateBinding> candidates = collectExtensionCandidates(
                compilationUnitScope,
                selectorName,
                receiverType,
                scope,
                invocationSite
        );
        if (candidates.isEmpty()) {
            return false;
        }
        InvocationRewriteState state = captureInvocationRewriteState(invocationSite);
        try {
            for (CandidateBinding candidate : candidates) {
                CandidateBinding compatible = computeCompatibleCandidate(
                        scope,
                        candidate,
                        receiverType,
                        visibleArgumentTypes,
                        invocationSite,
                        true
                );
                restoreInvocationRewriteState(invocationSite, state);
                if (compatible != null) {
                    return true;
                }
            }
            return false;
        } finally {
            restoreInvocationRewriteState(invocationSite, state);
        }
    }

    private static String extractInvocationSelectorName(Object invocationSite) throws Exception {
        if (invocationSite == null || findField(invocationSite.getClass(), "selector") == null) {
            return "";
        }
        Object selector = getFieldValue(invocationSite, "selector");
        return selector instanceof char[] ? new String((char[]) selector) : "";
    }

    private static Object resolveInvocationReceiverType(Object scope, Object invocationSite) throws Exception {
        if (invocationSite == null) {
            return null;
        }
        Object receiver = findField(invocationSite.getClass(), "receiver") != null
                ? getFieldValue(invocationSite, "receiver")
                : null;
        // Before a receiver-style extension invocation is rewritten, the receiver AST is
        // the authoritative source for parameterized chain results. After rewriting, the
        // receiver is the static holder and actualReceiverType intentionally preserves the
        // original type instead.
        if (receiver != null && !isTypeAccessInvocation(invocationSite)) {
            Object recoveredReceiverType = resolveRecoverableReceiverType(receiver);
            if (recoveredReceiverType != null) {
                return recoveredReceiverType;
            }
        }
        Object actualReceiverType = findField(invocationSite.getClass(), "actualReceiverType") != null
                ? getFieldValue(invocationSite, "actualReceiverType")
                : null;
        if (actualReceiverType != null && !isProblem(actualReceiverType)) {
            return actualReceiverType;
        }
        if (receiver != null) {
            Object recoveredReceiverType = resolveRecoverableReceiverType(receiver);
            if (recoveredReceiverType != null) {
                return recoveredReceiverType;
            }
        }
        Object[] implicitReceivers = ScopeAdvice.resolveImplicitReceivers(scope);
        return implicitReceivers.length > 0 ? implicitReceivers[0] : null;
    }

    private static Object resolveRecoverableReceiverType(Object receiver) throws Exception {
        if (receiver == null) {
            return null;
        }
        Object concreteType = pickConcreteArgumentType(getFieldValue(receiver, "resolvedType"));
        if (concreteType != null) {
            return concreteType;
        }
        concreteType = pickConcreteArgumentType(getFieldValue(receiver, "expectedType"));
        if (concreteType != null) {
            return concreteType;
        }
        Object receiverBinding = getFieldValue(receiver, "binding");
        concreteType = pickConcreteArgumentType(receiverBinding != null ? getFieldValue(receiverBinding, "type") : null);
        if (concreteType != null) {
            return concreteType;
        }
        concreteType = pickConcreteArgumentType(receiverBinding != null ? getFieldValue(receiverBinding, "returnType") : null);
        if (concreteType != null) {
            return concreteType;
        }
        return pickConcreteArgumentType(getFieldValue(receiver, "actualReceiverType"));
    }

    private static Object[] resolveInvocationArgumentTypes(Object scope, Object invocationSite) throws Exception {
        Object rawArgumentTypes = findField(invocationSite.getClass(), "argumentTypes") != null
                ? getFieldValue(invocationSite, "argumentTypes")
                : null;
        if (rawArgumentTypes instanceof Object[]) {
            return (Object[]) rawArgumentTypes;
        }
        Object rawArguments = findField(invocationSite.getClass(), "arguments") != null
                ? getFieldValue(invocationSite, "arguments")
                : null;
        Object[] arguments = rawArguments instanceof Object[] ? (Object[]) rawArguments : emptyExpressionArray(invocationSite);
        Object[] argumentTypes = (Object[]) Array.newInstance(loadClass(TYPE_BINDING_CLASS, invocationSite), arguments.length);
        for (int index = 0; index < arguments.length; index++) {
            Object argument = arguments[index];
            if (argument == null || !isAstNode(argument) || findField(argument.getClass(), "resolvedType") == null) {
                continue;
            }
            Object resolvedType = getFieldValue(argument, "resolvedType");
            if (resolvedType != null && !isProblem(resolvedType)) {
                argumentTypes[index] = resolvedType;
            }
        }
        return argumentTypes;
    }

    private static boolean isInvocationAstNode(Object node) {
        if (node == null) {
            return false;
        }
        String simpleName = node.getClass().getSimpleName();
        return "MessageSend".equals(simpleName)
                || "AllocationExpression".equals(simpleName)
                || "ExplicitConstructorCall".equals(simpleName)
                || "ReferenceExpression".equals(simpleName);
    }

    private static Object computeRewrittenStaticCompatibleBinding(
            Object scope,
            Object originalMethod,
            Object receiverType,
            Object[] argumentTypes,
            Object invocationSite
    ) throws Exception {
        if (scope == null || originalMethod == null || invocationSite == null) {
            return null;
        }
        Object[] safeArgumentTypes = argumentTypes != null ? argumentTypes : emptyTypeBindingArray(scope);
        try {
            Object[] invocationArgumentTypes = buildInvocationArgumentTypes(originalMethod, receiverType, scope, safeArgumentTypes);
            Object compatible = invokeMethod(scope, "computeCompatibleMethod", originalMethod, invocationArgumentTypes, invocationSite);
            return isProblem(compatible) ? null : compatible;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean shouldRetainHeuristicOverloadCandidate(Object scope, Object binding, Object invocationSite) throws Exception {
        if (scope == null || binding == null || invocationSite == null
                || isSelectionInvocation(invocationSite)
                || isReferenceInvocation(invocationSite)) {
            return false;
        }
        if (isImplicitInvocation(invocationSite) && hasFunctionalInvocationArgument(invocationSite)) {
            Object originalMethod = resolveLinkedOriginalMethod(binding);
            if (canRewriteImplicitInvocationToStaticCall(originalMethod != null ? originalMethod : binding)) {
                return true;
            }
        }
        Object rawArguments = getFieldValue(invocationSite, "arguments");
        Object rawParameters = getFieldValue(binding, "parameters");
        if (!(rawArguments instanceof Object[]) || !(rawParameters instanceof Object[])) {
            return false;
        }
        Object[] arguments = (Object[]) rawArguments;
        Object[] parameters = (Object[]) rawParameters;
        boolean retainedFunctionalArgument = false;
        for (int index = 0; index < arguments.length && index < parameters.length; index++) {
            if (arguments[index] == null || parameters[index] == null) {
                continue;
            }
            if (isFunctionalInvocationArgument(arguments[index])
                    && shouldRefreshFunctionalArgument(scope, parameters[index], invocationSite, binding, index)) {
                if (!isHeuristicallyCompatibleFunctionalOverloadArgument(scope, arguments[index], parameters[index])) {
                    return false;
                }
                retainedFunctionalArgument = true;
            }
        }
        return retainedFunctionalArgument;
    }

    private static boolean isHeuristicallyCompatibleFunctionalOverloadArgument(
            Object scope,
            Object argument,
            Object parameterType
    ) throws Exception {
        if (scope == null || argument == null || parameterType == null) {
            return false;
        }
        Integer lambdaParameterCount = inferLambdaParameterCount(argument);
        Integer functionalParameterCount = inferFunctionalParameterCount(parameterType, scope);
        if (lambdaParameterCount != null
                && functionalParameterCount != null
                && !lambdaParameterCount.equals(functionalParameterCount)) {
            return false;
        }
        Boolean lambdaReturnsValue = inferLambdaReturnsValue(argument);
        Boolean functionalReturnsValue = inferFunctionalReturnsValue(parameterType, scope);
        if (lambdaReturnsValue == null || functionalReturnsValue == null) {
            return true;
        }
        return lambdaReturnsValue.equals(functionalReturnsValue);
    }

    private static boolean isFunctionalInvocationArgument(Object argument) {
        return isLambdaExpression(argument)
                || (argument != null && argument.getClass().getName().endsWith("ReferenceExpression"));
    }

    private static Object finalizeImplicitStaticRewriteAfterSelection(
            Object scope,
            Object receiverType,
            Object invocationSite,
            Object binding
    ) throws Exception {
        if (scope == null || invocationSite == null || binding == null) {
            return null;
        }
        String selectorName = getSelectorName(binding);
        if (!isImplicitInvocation(invocationSite) || !hasFunctionalInvocationArgument(invocationSite)) {
            return null;
        }
        Object originalMethod = resolveLinkedOriginalMethod(binding);
        if (originalMethod == null || !canRewriteImplicitInvocationToStaticCall(originalMethod)) {
            return null;
        }
        Object[] argumentTypes = getFieldValue(invocationSite, "argumentTypes") instanceof Object[]
                ? (Object[]) getFieldValue(invocationSite, "argumentTypes")
                : emptyTypeBindingArray(scope);
        Object compatible = computeRewrittenStaticCompatibleBinding(scope, originalMethod, receiverType, argumentTypes, invocationSite);
        if (compatible == null || isProblem(compatible)) {
            if (binding != originalMethod) {
                if (Util.isDebugEnabled()) {
                    Util.log("[ZirconCore] heuristicFinalize selector=" + selectorName
                            + ", mode=retainFacadeOnRewriteFailure"
                            + ", binding=" + describeMethodBindingDetailed(binding)
                            + ", original=" + describeMethodBindingDetailed(originalMethod));
                }
                return binding;
            }
            compatible = originalMethod;
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconCore] heuristicFinalize selector=" + selectorName
                        + ", mode=originalMethodFallback"
                        + ", binding=" + describeMethodBindingDetailed(originalMethod));
            }
        }
        if (!rewriteImplicitInvocationToStaticCall(scope, invocationSite, compatible, receiverType)) {
            return null;
        }
        Object rebound = resynchronizeRewrittenStaticInvocation(scope, invocationSite, compatible);
        if (rebound == null || isProblem(rebound)) {
            rebound = compatible;
        }
        if (Util.isDebugEnabled()) {
            Util.log("[ZirconCore] heuristicFinalize selector=" + selectorName
                    + ", binding=" + describeMethodBindingDetailed(rebound));
        }
        return rebound;
    }

    private static Object resolveLinkedOriginalMethod(Object binding) throws Exception {
        if (binding == null) {
            return null;
        }
        Field originalMethodField = findField(binding.getClass(), "originalMethod");
        if (originalMethodField != null) {
            originalMethodField.setAccessible(true);
            Object linked = originalMethodField.get(binding);
            if (linked != null && linked != binding) {
                return linked;
            }
        }
        Object originalBinding = invokeOptionalMethod(binding, "original");
        return originalBinding != binding ? originalBinding : null;
    }

    private static CandidateBinding computeSelectionCandidate(
            Object scope,
            CandidateBinding candidate,
            Object receiverType,
            Object[] argumentTypes,
            Object invocationSite
    ) throws Exception {
        try {
            Object[] invocationArgumentTypes = buildInvocationArgumentTypes(candidate.originalMethod, receiverType, scope, argumentTypes);
            Object compatible = invokeMethod(scope, "computeCompatibleMethod", candidate.originalMethod, invocationArgumentTypes, invocationSite);
            if (!isProblem(compatible)) {
                return new CandidateBinding(
                        compatible,
                        candidate.originalMethod,
                        candidate.targetType,
                        candidate.ownerClassName,
                        candidate.cover
                );
            }
        } catch (Exception ignored) {
            // fall through to the original declaration binding below
        }
        return new CandidateBinding(
                candidate.originalMethod,
                candidate.originalMethod,
                candidate.targetType,
                candidate.ownerClassName,
                candidate.cover
        );
    }

    private static CandidateBinding computeCompatibleFacadeCandidate(
            Object scope,
            CandidateBinding candidate,
            Object receiverType,
            Object[] argumentTypes,
            Object invocationSite
    ) throws Exception {
        return computeCompatibleFacadeCandidate(scope, candidate, receiverType, argumentTypes, invocationSite, false);
    }

    private static CandidateBinding computeCompatibleFacadeCandidate(
            Object scope,
            CandidateBinding candidate,
            Object receiverType,
            Object[] argumentTypes,
            Object invocationSite,
            boolean probeOnly
    ) throws Exception {
        String selectorName = getSelectorName(candidate.binding);
        // Snapshot the original receiver before asking JDT to test the synthetic facade.
        // computeCompatibleMethod may update MessageSend.actualReceiverType to the facade
        // holder, which is too late to use as the hidden leading argument of the static
        // extension method and loses receiver type arguments needed by lambda inference.
        Object facadeReceiverType = receiverType;
        Object facadeReceiverExpression = getFieldValue(invocationSite, "receiver");
        if (facadeReceiverType == null || isProblem(facadeReceiverType)) {
            facadeReceiverType = resolveRecoverableReceiverType(facadeReceiverExpression);
        }
        if (facadeReceiverType == null || isProblem(facadeReceiverType)) {
            facadeReceiverType = resolveInvocationReceiverType(scope, invocationSite);
        }
        Object compatible;
        try {
            compatible = invokeMethod(scope, "computeCompatibleMethod", candidate.binding, argumentTypes, invocationSite);
        } catch (Exception e) {
            if (shouldTraceSelector(selectorName)) {
                Util.log("[ZirconCore] trace selector=" + selectorName
                        + ", facadeCompatible=exception"
                        + ", binding=" + describeMethodBinding(candidate.binding)
                        + ", args=" + describeTypeArray(argumentTypes)
                        + ", error=" + e.getClass().getName() + ": " + e.getMessage());
            }
            if (shouldRetainHeuristicOverloadCandidate(scope, candidate.binding, invocationSite)) {
                Object heuristicBinding = specializeHeuristicFunctionalFacadeBinding(scope, candidate.binding, argumentTypes, invocationSite);
                linkOriginalMethodIfSupported(heuristicBinding, candidate.originalMethod);
                if (Util.isDebugEnabled()) {
                    Util.log("[ZirconCore] heuristicFallback selector=" + selectorName
                            + ", reason=exception"
                            + ", binding=" + describeMethodBindingDetailed(heuristicBinding));
                }
                return new CandidateBinding(
                        heuristicBinding,
                        candidate.originalMethod,
                        candidate.targetType,
                        candidate.ownerClassName,
                        candidate.cover
                );
            }
            return null;
        }
        if (isProblem(compatible)) {
            if (shouldTraceSelector(selectorName)) {
                Util.log("[ZirconCore] trace selector=" + selectorName
                        + ", facadeCompatible=problem"
                        + ", binding=" + describeMethodBinding(candidate.binding)
                        + ", args=" + describeTypeArray(argumentTypes));
            }
            if (shouldRetainHeuristicOverloadCandidate(scope, candidate.binding, invocationSite)) {
                Object heuristicBinding = specializeHeuristicFunctionalFacadeBinding(scope, candidate.binding, argumentTypes, invocationSite);
                linkOriginalMethodIfSupported(heuristicBinding, candidate.originalMethod);
                if (Util.isDebugEnabled()) {
                    Util.log("[ZirconCore] heuristicFallback selector=" + selectorName
                            + ", reason=problem"
                            + ", binding=" + describeMethodBindingDetailed(heuristicBinding));
                }
                return new CandidateBinding(
                        heuristicBinding,
                        candidate.originalMethod,
                        candidate.targetType,
                        candidate.ownerClassName,
                        candidate.cover
                );
            }
            return null;
        }
        if (probeOnly) {
            return new CandidateBinding(
                    compatible,
                    candidate.originalMethod,
                    candidate.targetType,
                    candidate.ownerClassName,
                    candidate.cover
            );
        }
        if (facadeReceiverType == null || isProblem(facadeReceiverType)) {
            facadeReceiverType = getFieldValue(invocationSite, "actualReceiverType");
        }
        if (facadeReceiverType == null || isProblem(facadeReceiverType)) {
            Object receiverExpression = getFieldValue(invocationSite, "receiver");
            if (receiverExpression != null) {
                facadeReceiverType = getFieldValue(receiverExpression, "resolvedType");
            }
        }
        if (shouldDebugFacadeSelector(selectorName)) {
            Util.log("[ZirconCore] debugCompatible selector=" + selectorName
                    + ", source=" + readIntField(invocationSite, "sourceStart") + "-" + readIntField(invocationSite, "sourceEnd")
                    + ", facade=" + describeMethodBindingDetailed(candidate.binding)
                    + ", compatible=" + describeMethodBindingDetailed(compatible)
                    + ", facadeReceiverType=" + describeTypeDebug(facadeReceiverType)
                    + ", expectedType=" + describeTypeDebug(findField(invocationSite.getClass(), "expectedType") != null ? getFieldValue(invocationSite, "expectedType") : null)
                    + ", argumentTypes=" + describeTypeArrayDetailed(argumentTypes));
        }
        if (shouldDebugClassTargetSelector(selectorName)) {
            Util.log("[ZirconCore] classTargetCompatible selector=" + selectorName
                    + ", source=" + readIntField(invocationSite, "sourceStart") + "-" + readIntField(invocationSite, "sourceEnd")
                    + ", facade=" + describeMethodBindingDetailed(candidate.binding)
                    + ", compatible=" + describeMethodBindingDetailed(compatible)
                    + ", facadeReceiverType=" + describeTypeDebug(facadeReceiverType)
                    + ", argumentTypes=" + describeTypeArrayDetailed(argumentTypes));
        }
        rememberSuccessfulClassTargetExtension(scope, candidate.originalMethod, compatible, invocationSite);
        boolean skipReceiverStyleStaticRewriteProbe = shouldSkipReceiverStyleStaticRewriteProbe(
                scope,
                candidate.originalMethod,
                compatible,
                invocationSite
        );
        Object rewrittenStaticBinding = skipReceiverStyleStaticRewriteProbe
                ? null
                : computeRewrittenStaticCompatibleBinding(
                scope,
                candidate.originalMethod,
                facadeReceiverType,
                argumentTypes,
                invocationSite
        );
        if (shouldDebugFacadeSelector(selectorName)) {
            Util.log("[ZirconCore] debugRewrite selector=" + selectorName
                    + ", source=" + readIntField(invocationSite, "sourceStart") + "-" + readIntField(invocationSite, "sourceEnd")
                    + ", original=" + describeMethodBindingDetailed(candidate.originalMethod)
                    + ", rewrittenStaticBinding=" + describeMethodBindingDetailed(rewrittenStaticBinding)
                    + ", skipProbe=" + skipReceiverStyleStaticRewriteProbe
                    + ", facadeReceiverType=" + describeTypeDebug(facadeReceiverType)
                    + ", argumentTypes=" + describeTypeArrayDetailed(argumentTypes));
        }
        if (shouldDebugClassTargetSelector(selectorName)) {
            Util.log("[ZirconCore] classTargetRewrite selector=" + selectorName
                    + ", source=" + readIntField(invocationSite, "sourceStart") + "-" + readIntField(invocationSite, "sourceEnd")
                    + ", original=" + describeMethodBindingDetailed(candidate.originalMethod)
                    + ", rewrittenStaticBinding=" + describeMethodBindingDetailed(rewrittenStaticBinding)
                    + ", skipProbe=" + skipReceiverStyleStaticRewriteProbe
                    + ", facadeReceiverType=" + describeTypeDebug(facadeReceiverType)
                    + ", argumentTypes=" + describeTypeArrayDetailed(argumentTypes));
        }
        if (rewrittenStaticBinding != null
                && rewriteReceiverStyleInvocationToStaticCall(scope, invocationSite, rewrittenStaticBinding, facadeReceiverType)) {
            compatible = resynchronizeRewrittenStaticInvocation(scope, invocationSite, rewrittenStaticBinding);
            if (shouldDebugFacadeSelector(selectorName)) {
                Util.log("[ZirconCore] debugResync selector=" + selectorName
                        + ", source=" + readIntField(invocationSite, "sourceStart") + "-" + readIntField(invocationSite, "sourceEnd")
                        + ", rebound=" + describeMethodBindingDetailed(compatible)
                        + ", invocationArgumentTypes=" + describeTypeArrayDetailed(getFieldValue(invocationSite, "argumentTypes") instanceof Object[] ? (Object[]) getFieldValue(invocationSite, "argumentTypes") : new Object[0]));
            }
            if (shouldDebugClassTargetSelector(selectorName)) {
                Util.log("[ZirconCore] classTargetResync selector=" + selectorName
                        + ", source=" + readIntField(invocationSite, "sourceStart") + "-" + readIntField(invocationSite, "sourceEnd")
                        + ", rebound=" + describeMethodBindingDetailed(compatible)
                        + ", invocationArgumentTypes=" + describeTypeArrayDetailed(getFieldValue(invocationSite, "argumentTypes") instanceof Object[] ? (Object[]) getFieldValue(invocationSite, "argumentTypes") : new Object[0]));
            }
        }
        linkOriginalMethodIfSupported(compatible, candidate.originalMethod);
        if (shouldTraceSelector(selectorName)) {
            Util.log("[ZirconCore] trace selector=" + selectorName
                    + ", facadeCompatible=ok"
                    + ", bindingClass=" + compatible.getClass().getName()
                    + ", binding=" + describeMethodBinding(compatible)
                    + ", detailedBinding=" + describeMethodBindingDetailed(compatible)
                    + ", args=" + describeTypeArray(argumentTypes));
        }
        return new CandidateBinding(
                compatible,
                candidate.originalMethod,
                candidate.targetType,
                candidate.ownerClassName,
                candidate.cover
        );
    }

    private static void linkOriginalMethodIfSupported(Object binding, Object originalMethod) throws Exception {
        if (binding == null || originalMethod == null) {
            return;
        }
        rememberJdtSearchOriginalMethod(binding, originalMethod);
        Field originalMethodField = findField(binding.getClass(), "originalMethod");
        if (originalMethodField == null) {
            return;
        }
        Object previous = originalMethodField.get(binding);
        if (previous == originalMethod) {
            return;
        }
        try {
            originalMethodField.set(binding, originalMethod);
            resetMethodBindingCaches(binding);
            primeMethodBindingState(binding);
            Method computeUniqueKey = findMethod(binding.getClass(), "computeUniqueKey", boolean.class);
            if (computeUniqueKey != null) {
                computeUniqueKey.invoke(binding, false);
            }
            invokeOptionalMethod(binding, "original");
        } catch (Throwable error) {
            originalMethodField.set(binding, previous);
            resetMethodBindingCaches(binding);
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconCore] linkOriginalMethodSkipped selector=" + getSelectorName(binding)
                        + ", binding=" + describeMethodBinding(binding)
                        + ", error=" + error.getClass().getName() + ": " + error.getMessage());
            }
        }
    }

    static void rememberJdtSearchOriginalMethod(Object binding, Object originalMethod) {
        if (binding == null || originalMethod == null || binding == originalMethod) {
            return;
        }
        synchronized (JDT_SEARCH_ORIGINAL_METHODS) {
            JDT_SEARCH_ORIGINAL_METHODS.put(binding, originalMethod);
        }
    }

    public static Object normalizeJdtSearchBinding(Object binding) {
        Object current = binding;
        for (int depth = 0; current != null && depth < 8; depth++) {
            Object original;
            synchronized (JDT_SEARCH_ORIGINAL_METHODS) {
                original = JDT_SEARCH_ORIGINAL_METHODS.get(current);
            }
            if (original == null || original == current) {
                break;
            }
            current = original;
        }
        return current;
    }

    public static void normalizeJdtSearchNodeBinding(Object node) {
        if (node == null) {
            return;
        }
        try {
            Field bindingField = findField(node.getClass(), "binding");
            if (bindingField == null) {
                return;
            }
            Object binding = bindingField.get(node);
            Object normalized = normalizeJdtSearchBinding(binding);
            if (normalized != null && normalized != binding && bindingField.getType().isInstance(normalized)) {
                bindingField.set(node, normalized);
                if (Util.isTraceEnabled()) {
                    Util.log("[ZirconSearch] normalized selector=" + getSelectorName(normalized)
                            + ", node=" + node.getClass().getSimpleName());
                }
            }
        } catch (Throwable error) {
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconSearch] binding normalization failed: "
                        + error.getClass().getName() + ": " + error.getMessage());
            }
        }
    }

    /**
     * JDT's syntactic MethodLocator filter normally requires the invocation and
     * declaration to have identical arity. An extension invocation omits the
     * declaration's first (receiver) parameter, so it would be discarded before
     * binding resolution. Add only that shape back as a possible match for an
     * {@code @ExMethod} focus; resolveLevel then performs JDT's normal precise check.
     */
    public static int expandJdtSearchCandidate(Object locator, Object node, Object nodeSet, int currentLevel) {
        if (currentLevel != 0 || locator == null || node == null || nodeSet == null) {
            return currentLevel;
        }
        try {
            Object pattern = getFieldValue(locator, "pattern");
            if (pattern == null || !isExMethodSearchPattern(pattern)) {
                return currentLevel;
            }
            Object[] parameterNames = (Object[]) getFieldValue(pattern, "parameterSimpleNames");
            int invocationArity = getJdtSearchInvocationArity(node);
            int declarationArity = parameterNames == null ? -1 : parameterNames.length;
            if (declarationArity != invocationArity + 1) {
                return currentLevel;
            }

            char[] patternSelector = (char[]) getFieldValue(pattern, "selector");
            char[] nodeSelector = (char[]) getFieldValue(node, "selector");
            if (patternSelector == null || nodeSelector == null
                    || !java.util.Arrays.equals(patternSelector, nodeSelector)) {
                return currentLevel;
            }

            Method addMatch = findMethod(nodeSet.getClass(), "addMatch", node.getClass(), int.class);
            if (addMatch == null) {
                return currentLevel;
            }
            Object expanded = addMatch.invoke(nodeSet, node, 2);
            int level = expanded instanceof Number ? ((Number) expanded).intValue() : 2;
            if (Util.isTraceEnabled()) {
                Util.log("[ZirconSearch] expanded candidate selector=" + new String(nodeSelector)
                        + ", declarationArity=" + declarationArity
                        + ", invocationArity=" + invocationArity
                        + ", level=" + level);
            }
            return level;
        } catch (Throwable error) {
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconSearch] candidate expansion failed: "
                        + error.getClass().getName() + ": " + error.getMessage());
            }
            return currentLevel;
        }
    }

    private static int getJdtSearchInvocationArity(Object node) throws Exception {
        if (node == null) {
            return 0;
        }
        Object arguments = getFieldValue(node, "arguments");
        if (arguments instanceof Object[]) {
            return ((Object[]) arguments).length;
        }
        if (node.getClass().getName().endsWith("ReferenceExpression")) {
            Object freeParameters = getFieldValue(node, "freeParameters");
            if (freeParameters instanceof Object[]) {
                return ((Object[]) freeParameters).length;
            }
            Object descriptor = getFieldValue(node, "descriptor");
            Object parameters = descriptor == null ? null : getFieldValue(descriptor, "parameters");
            if (parameters instanceof Object[]) {
                return ((Object[]) parameters).length;
            }
        }
        return 0;
    }

    public static void traceJdtSearchResolution(Object node, int level) {
        if (!Util.isTraceEnabled() || node == null) {
            return;
        }
        try {
            Object selector = getFieldValue(node, "selector");
            String selectorName = selector instanceof char[] ? new String((char[]) selector) : "";
            String configured = System.getProperty("zircon.trace.selectors", "");
            if (!configured.isEmpty() && !configured.contains(selectorName) && !configured.contains("*")) {
                return;
            }
            Object binding = getFieldValue(node, "binding");
            int arity = getJdtSearchInvocationArity(node);
            Util.log("[ZirconSearch] resolved selector=" + selectorName
                    + ", invocationArity=" + arity
                    + ", level=" + level
                    + ", source=" + readIntField(node, "sourceStart", -1)
                    + "-" + readIntField(node, "sourceEnd", -1)
                    + ", binding=" + describeMethodBinding(binding));
        } catch (Throwable error) {
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconSearch] resolution trace failed: "
                        + error.getClass().getName() + ": " + error.getMessage());
            }
        }
    }

    public static long prepareJdtSearchReportRange(Object locator, Object messageSend) {
        if (locator == null || messageSend == null) {
            return Long.MIN_VALUE;
        }
        try {
            Object pattern = getFieldValue(locator, "pattern");
            if (pattern == null || !isExMethodSearchPattern(pattern)) {
                if (Util.isTraceEnabled()) {
                    Util.log("[ZirconSearch] report-range skipped: non-extension pattern");
                }
                return Long.MIN_VALUE;
            }
            Object namePositionValue = getFieldValue(messageSend, "nameSourcePosition");
            int selectorStart;
            int selectorEnd;
            if (namePositionValue instanceof Long) {
                long namePosition = (Long) namePositionValue;
                selectorStart = (int) (namePosition >>> 32);
                selectorEnd = (int) namePosition;
            } else {
                selectorStart = readIntField(messageSend, "nameSourceStart", -1);
                String selector = getSelectorName(messageSend);
                selectorEnd = selectorStart < 0 ? -1 : selectorStart + selector.length() - 1;
            }
            int originalSourceStart = readIntField(messageSend, "sourceStart", -1);
            int originalSourceEnd = readIntField(messageSend, "sourceEnd", -1);
            if (Util.isTraceEnabled()) {
                Util.log("[ZirconSearch] report-range selector="
                        + getSelectorName(messageSend)
                        + ", name=" + selectorStart + "-" + selectorEnd
                        + ", sourceEnd=" + originalSourceEnd);
            }
            boolean referenceExpression = messageSend.getClass().getName().endsWith("ReferenceExpression");
            if (selectorStart < 0 || selectorEnd < selectorStart
                    || (originalSourceEnd == selectorEnd
                    && (!referenceExpression || originalSourceStart == selectorStart))) {
                return Long.MIN_VALUE;
            }
            if (referenceExpression) {
                setFieldValue(messageSend, "sourceStart", selectorStart);
            }
            setFieldValue(messageSend, "sourceEnd", selectorEnd);
            return ((long) originalSourceStart << 32) | (originalSourceEnd & 0xffff_ffffL);
        } catch (Throwable error) {
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconSearch] report-range normalization failed: "
                        + error.getClass().getName() + ": " + error.getMessage());
            }
            return Long.MIN_VALUE;
        }
    }

    public static void restoreJdtSearchReportRange(Object messageSend, long originalRange) {
        if (messageSend == null || originalRange == Long.MIN_VALUE) {
            return;
        }
        try {
            int originalSourceStart = (int) (originalRange >>> 32);
            int originalSourceEnd = (int) originalRange;
            setFieldValue(messageSend, "sourceStart", originalSourceStart);
            setFieldValue(messageSend, "sourceEnd", originalSourceEnd);
        } catch (Throwable error) {
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconSearch] report-range restoration failed: "
                        + error.getClass().getName() + ": " + error.getMessage());
            }
        }
    }

    public static int repairJdtSearchReportAccuracy(Object locator, Object node, int currentAccuracy) {
        if (locator == null || node == null
                || !node.getClass().getName().endsWith("ReferenceExpression")) {
            return currentAccuracy;
        }
        if (Util.isTraceEnabled()) {
            Util.log("[ZirconSearch] method-reference report accuracy=" + currentAccuracy);
        }
        if (currentAccuracy == 0) {
            return currentAccuracy;
        }
        if (JDT_SEARCH_ACCURATE_REFERENCE_NODES.contains(node)) {
            if (Util.isTraceEnabled()) {
                Util.log("[ZirconSearch] promoted remembered method-reference accuracy="
                        + currentAccuracy + "->0");
            }
            return 0;
        }
        try {
            Object pattern = getFieldValue(locator, "pattern");
            if (pattern == null || !isExMethodSearchPattern(pattern)) {
                return currentAccuracy;
            }
            Object binding = normalizeJdtSearchBinding(getFieldValue(node, "binding"));
            Object focus = getFieldValue(pattern, "focus");
            if (binding == null || focus == null) {
                return currentAccuracy;
            }
            String selector = getSelectorName(binding);
            Object patternSelector = getFieldValue(pattern, "selector");
            if (!(patternSelector instanceof char[])
                    || !selector.equals(new String((char[]) patternSelector))) {
                return currentAccuracy;
            }
            Object declaringClass = getFieldValue(binding, "declaringClass");
            String bindingOwner = getReadableTypeName(declaringClass);
            Method getDeclaringType = findMethod(focus.getClass(), "getDeclaringType");
            Object declaringType = getDeclaringType == null ? null : getDeclaringType.invoke(focus);
            Method getFullyQualifiedName = declaringType == null
                    ? null
                    : findMethod(declaringType.getClass(), "getFullyQualifiedName");
            Object ownerValue = getFullyQualifiedName == null ? null : getFullyQualifiedName.invoke(declaringType);
            if (!(ownerValue instanceof String) || !bindingOwner.equals(ownerValue)) {
                return currentAccuracy;
            }
            if (Util.isTraceEnabled()) {
                Util.log("[ZirconSearch] promoted method-reference accuracy selector=" + selector
                        + ", owner=" + bindingOwner
                        + ", accuracy=" + currentAccuracy + "->0");
            }
            return 0;
        } catch (Throwable error) {
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconSearch] method-reference accuracy repair failed: "
                        + error.getClass().getName() + ": " + error.getMessage());
            }
            return currentAccuracy;
        }
    }

    public static void rememberAccurateJdtSearchReference(Object locator, Object node, int level) {
        if (level != 3 || locator == null || node == null
                || !node.getClass().getName().endsWith("ReferenceExpression")) {
            return;
        }
        try {
            Object pattern = getFieldValue(locator, "pattern");
            if (pattern != null && isExMethodSearchPattern(pattern)) {
                JDT_SEARCH_ACCURATE_REFERENCE_NODES.add(node);
            }
        } catch (Throwable error) {
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconSearch] method-reference accuracy tracking failed: "
                        + error.getClass().getName() + ": " + error.getMessage());
            }
        }
    }

    /**
     * MethodLocator resolves extension-style calls from a fresh search AST. In
     * that AST JDT can retain a ProblemMethodBinding on the receiver even though
     * normal reconciliation already linked the call to the @ExMethod method.
     * The syntactic candidate was deliberately admitted by
     * expandJdtSearchCandidate; validate its receiver and visible argument
     * types against the focused declaration before promoting it to an accurate
     * match. This keeps SearchEngine, rename, CodeLens and call hierarchy on one
     * native path without a workspace text scan.
     */
    public static int repairJdtSearchResolution(Object locator, Object node, int currentLevel) {
        boolean unresolvedReference = node != null
                && node.getClass().getName().endsWith("ReferenceExpression")
                && currentLevel == 1;
        if ((currentLevel != 0 && !unresolvedReference) || locator == null || node == null) {
            return currentLevel;
        }
        try {
            Object pattern = getFieldValue(locator, "pattern");
            if (pattern == null || !isExMethodSearchPattern(pattern)) {
                return currentLevel;
            }
            char[] patternSelector = (char[]) getFieldValue(pattern, "selector");
            char[] nodeSelector = (char[]) getFieldValue(node, "selector");
            if (patternSelector == null || nodeSelector == null
                    || !java.util.Arrays.equals(patternSelector, nodeSelector)) {
                return currentLevel;
            }

            Object[] parameterNames = (Object[]) getFieldValue(pattern, "parameterSimpleNames");
            int invocationArity = getJdtSearchInvocationArity(node);
            int declarationArity = parameterNames == null ? -1 : parameterNames.length;
            if (declarationArity != invocationArity + 1) {
                return currentLevel;
            }

            Object invocationBinding = getFieldValue(node, "binding");
            if (invocationBinding != null && getFieldValue(invocationBinding, "returnType") != null) {
                // A fully resolved ordinary instance method can share the same
                // selector and visible arity. It is not an extension facade and
                // must never be promoted merely because an @ExMethod exists.
                return currentLevel;
            }

            Object receiverType = getJdtSearchReceiverType(node);
            if (!searchTypeMatchesPattern(receiverType, pattern, 0)) {
                return currentLevel;
            }
            for (int index = 0; index < invocationArity; index++) {
                Object argumentType = getJdtSearchArgumentType(node, index);
                if (!searchTypeMatchesPattern(argumentType, pattern, index + 1)) {
                    return currentLevel;
                }
            }
            if (Util.isTraceEnabled()) {
                Util.log("[ZirconSearch] repaired accurate match selector=" + new String(nodeSelector)
                        + ", declarationArity=" + declarationArity
                        + ", invocationArity=" + invocationArity
                        + ", receiver=" + getReadableTypeName(receiverType));
            }
            return 3;
        } catch (Throwable error) {
            if (Util.isDebugEnabled()) {
                Util.log("[ZirconSearch] accurate-match repair failed: "
                        + error.getClass().getName() + ": " + error.getMessage());
            }
            return currentLevel;
        }
    }

    private static Object getJdtSearchReceiverType(Object node) throws Exception {
        Object receiverType = getFieldValue(node, "actualReceiverType");
        if (receiverType == null) {
            receiverType = getFieldValue(node, "receiverType");
        }
        if (receiverType != null) {
            return receiverType;
        }
        Object receiver = getFieldValue(node, "receiver");
        if (receiver == null) {
            receiver = getFieldValue(node, "lhs");
        }
        return receiver == null ? null : getFieldValue(receiver, "resolvedType");
    }

    private static Object getJdtSearchArgumentType(Object node, int index) throws Exception {
        Object arguments = getFieldValue(node, "arguments");
        if (arguments instanceof Object[] && index < ((Object[]) arguments).length) {
            Object argument = ((Object[]) arguments)[index];
            return argument == null ? null : getFieldValue(argument, "resolvedType");
        }
        Object freeParameters = getFieldValue(node, "freeParameters");
        if (freeParameters instanceof Object[] && index < ((Object[]) freeParameters).length) {
            return ((Object[]) freeParameters)[index];
        }
        Object descriptor = getFieldValue(node, "descriptor");
        Object parameters = descriptor == null ? null : getFieldValue(descriptor, "parameters");
        return parameters instanceof Object[] && index < ((Object[]) parameters).length
                ? ((Object[]) parameters)[index]
                : null;
    }

    private static boolean searchTypeMatchesPattern(Object typeBinding, Object pattern, int parameterIndex)
            throws Exception {
        String declaredType = getSearchFocusParameterType(pattern, parameterIndex);
        if (declaredType != null) {
            if (declaredType.isEmpty()) {
                return true;
            }
            int separator = declaredType.lastIndexOf('.');
            String simple = separator < 0 ? declaredType : declaredType.substring(separator + 1);
            return searchTypeHierarchyContains(typeBinding, declaredType, simple, new java.util.IdentityHashMap<>());
        }
        Object[] simpleNames = (Object[]) getFieldValue(pattern, "parameterSimpleNames");
        Object[] qualifications = (Object[]) getFieldValue(pattern, "parameterQualifications");
        if (simpleNames == null || parameterIndex < 0 || parameterIndex >= simpleNames.length) {
            return true;
        }
        String simpleName = simpleNames[parameterIndex] instanceof char[]
                ? new String((char[]) simpleNames[parameterIndex])
                : "";
        String qualification = qualifications != null
                && parameterIndex < qualifications.length
                && qualifications[parameterIndex] instanceof char[]
                ? new String((char[]) qualifications[parameterIndex])
                : "";
        if (simpleName.isEmpty() || simpleName.length() == 1 && Character.isUpperCase(simpleName.charAt(0))) {
            return true;
        }
        String expected = qualification.isEmpty() ? simpleName : qualification + "." + simpleName;
        return searchTypeHierarchyContains(typeBinding, expected, simpleName, new java.util.IdentityHashMap<>());
    }

    /**
     * SearchPattern parameter names can describe the synthetic receiver facade
     * (and therefore degrade to Object). The Java-model focus still owns the
     * declaration signatures, so use those as the authoritative source.
     */
    private static String getSearchFocusParameterType(Object pattern, int parameterIndex) throws Exception {
        Object focus = getFieldValue(pattern, "focus");
        if (focus == null) {
            return null;
        }
        Method getParameterTypes = findMethod(focus.getClass(), "getParameterTypes");
        if (getParameterTypes == null) {
            return null;
        }
        Object value = getParameterTypes.invoke(focus);
        if (!(value instanceof String[]) || parameterIndex < 0 || parameterIndex >= ((String[]) value).length) {
            return null;
        }
        return eraseJavaModelTypeSignature(((String[]) value)[parameterIndex]);
    }

    private static String eraseJavaModelTypeSignature(String signature) {
        if (signature == null || signature.isEmpty()) {
            return "";
        }
        String value = signature;
        while (value.startsWith("[")) {
            value = value.substring(1);
        }
        if (value.isEmpty() || value.charAt(0) == 'T' || value.charAt(0) == '*'
                || value.charAt(0) == '+' || value.charAt(0) == '-') {
            return "";
        }
        if (value.length() == 1) {
            switch (value.charAt(0)) {
                case 'Z': return "boolean";
                case 'B': return "byte";
                case 'C': return "char";
                case 'D': return "double";
                case 'F': return "float";
                case 'I': return "int";
                case 'J': return "long";
                case 'S': return "short";
                case 'V': return "void";
                default: return "";
            }
        }
        if ((value.charAt(0) == 'L' || value.charAt(0) == 'Q') && value.endsWith(";")) {
            value = value.substring(1, value.length() - 1);
        }
        StringBuilder erased = new StringBuilder(value.length());
        int genericDepth = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '<') {
                genericDepth++;
            } else if (character == '>') {
                genericDepth = Math.max(0, genericDepth - 1);
            } else if (genericDepth == 0) {
                erased.append(character == '/' || character == '$' ? '.' : character);
            }
        }
        return erased.toString();
    }

    private static boolean searchTypeHierarchyContains(
            Object typeBinding,
            String expectedQualifiedName,
            String expectedSimpleName,
            Map<Object, Boolean> visited
    ) throws Exception {
        if (typeBinding == null) {
            // An unresolved argument is not evidence of incompatibility. JDT's
            // normal overload resolution will report the real type error.
            return true;
        }
        if (visited.put(typeBinding, Boolean.TRUE) != null) {
            return false;
        }
        String actualQualified = eraseSearchTypeName(getQualifiedTypeName(typeBinding));
        String actualReadable = eraseSearchTypeName(getReadableTypeName(typeBinding));
        String expectedQualified = eraseSearchTypeName(expectedQualifiedName);
        String expectedSimple = eraseSearchTypeName(expectedSimpleName);
        if (actualQualified.equals(expectedQualified)
                || actualReadable.equals(expectedQualified)
                || actualQualified.endsWith("." + expectedSimple)
                || actualReadable.equals(expectedSimple)
                || "java.lang.Object".equals(expectedQualified)) {
            return true;
        }
        Object superclass = invokeOptionalMethod(typeBinding, "superclass");
        if (searchTypeHierarchyContains(superclass, expectedQualified, expectedSimple, visited)) {
            return true;
        }
        Object interfaces = invokeOptionalMethod(typeBinding, "superInterfaces");
        if (interfaces instanceof Object[]) {
            for (Object interfaceType : (Object[]) interfaces) {
                if (searchTypeHierarchyContains(interfaceType, expectedQualified, expectedSimple, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String eraseSearchTypeName(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('$', '.');
        int generic = normalized.indexOf('<');
        if (generic >= 0) {
            normalized = normalized.substring(0, generic);
        }
        return normalized.trim();
    }

    private static boolean isExMethodSearchPattern(Object pattern) throws Exception {
        Object focus = getFieldValue(pattern, "focus");
        if (focus == null) {
            return false;
        }
        Method getAnnotations = findMethod(focus.getClass(), "getAnnotations");
        if (getAnnotations == null) {
            return false;
        }
        Object value = getAnnotations.invoke(focus);
        if (!(value instanceof Object[])) {
            return false;
        }
        for (Object annotation : (Object[]) value) {
            if (annotation == null) {
                continue;
            }
            Method getElementName = findMethod(annotation.getClass(), "getElementName");
            Object name = getElementName == null ? null : getElementName.invoke(annotation);
            String annotationName = name == null ? "" : name.toString();
            if ("ExMethod".equals(annotationName) || annotationName.endsWith(".ExMethod")) {
                return true;
            }
        }
        return false;
    }

    private static int readIntField(Object target, String fieldName, int fallback) throws Exception {
        Object value = getFieldValue(target, fieldName);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static void primeMethodBindingState(Object binding) throws Exception {
        if (binding == null) {
            return;
        }
        warmTypeBindingState(getFieldValue(binding, "declaringClass"));
        warmTypeBindingState(getFieldValue(binding, "returnType"));
        warmTypeBindingArray((Object[]) getFieldValue(binding, "parameters"));
        warmTypeBindingArray((Object[]) getFieldValue(binding, "typeVariables"));
        invokeOptionalMethod(binding, "signature");
        invokeOptionalMethod(binding, "genericSignature");
    }

    private static void warmTypeBindingArray(Object[] typeBindings) throws Exception {
        if (typeBindings == null) {
            return;
        }
        for (Object typeBinding : typeBindings) {
            warmTypeBindingState(typeBinding);
        }
    }

    private static void warmTypeBindingState(Object typeBinding) throws Exception {
        if (typeBinding == null) {
            return;
        }
        invokeOptionalMethod(typeBinding, "getReadableName");
        invokeOptionalMethod(typeBinding, "readableName");
        invokeOptionalMethod(typeBinding, "genericTypeSignature");
        invokeOptionalMethod(typeBinding, "genericSignature");
    }

    private static Object[] buildInvocationArgumentTypes(
            Object originalMethod,
            Object receiverType,
            Object scope,
            Object[] visibleArgumentTypes
    ) throws Exception {
        List<Object> explicitTargets = getAnnotationClassTargets(originalMethod, "ex");
        if (!explicitTargets.isEmpty()) {
            return visibleArgumentTypes;
        }
        Object[] parameters = (Object[]) getFieldValue(originalMethod, "parameters");
        Object hiddenReceiverType = parameters != null && parameters.length > 0 ? parameters[0] : null;
        if (hiddenReceiverType == null) {
            return visibleArgumentTypes;
        }
        Object effectiveReceiverType = projectReceiverType(receiverType, hiddenReceiverType, scope);
        Object[] invocationArgumentTypes = (Object[]) Array.newInstance(loadClass(TYPE_BINDING_CLASS, originalMethod), visibleArgumentTypes.length + 1);
        invocationArgumentTypes[0] = effectiveReceiverType;
        System.arraycopy(visibleArgumentTypes, 0, invocationArgumentTypes, 1, visibleArgumentTypes.length);
        return invocationArgumentTypes;
    }

    private static Object projectReceiverType(Object receiverType, Object hiddenReceiverType, Object scope) throws Exception {
        Object effectiveReceiverType = normalizeExtensionReceiverType(receiverType, hiddenReceiverType, scope);
        if (effectiveReceiverType == null || hiddenReceiverType == null) {
            return effectiveReceiverType;
        }
        if (isClassType(hiddenReceiverType) && isClassType(effectiveReceiverType)) {
            Object[] effectiveTypeArguments = safeGetTypeArguments(effectiveReceiverType);
            if (effectiveTypeArguments != null && effectiveTypeArguments.length > 0) {
                return effectiveReceiverType;
            }
        }
        Object projectedReceiverType = invokeMethod(effectiveReceiverType, "findSuperTypeOriginatingFrom", hiddenReceiverType);
        return projectedReceiverType != null ? projectedReceiverType : effectiveReceiverType;
    }

    private static Object substituteReceiverTypeVariables(
            Object scope,
            Object hiddenReceiverType,
            Object projectedReceiverType,
            Object typeBinding
    ) throws Exception {
        Map<Object, Object> substitutions = buildReceiverSubstitutions(hiddenReceiverType, projectedReceiverType);
        return substituteType(scope, typeBinding, substitutions);
    }

    private static Object[] substituteReceiverTypeVariables(
            Object scope,
            Object hiddenReceiverType,
            Object projectedReceiverType,
            Object[] typeBindings
    ) throws Exception {
        Map<Object, Object> substitutions = buildReceiverSubstitutions(hiddenReceiverType, projectedReceiverType);
        return substituteTypeBindings(scope, typeBindings, substitutions);
    }

    private static Map<Object, Object> buildReceiverSubstitutions(Object hiddenReceiverType, Object projectedReceiverType) throws Exception {
        Map<Object, Object> substitutions = new LinkedHashMap<>();
        if (hiddenReceiverType == null || projectedReceiverType == null) {
            return substitutions;
        }
        try {
            collectReceiverSubstitutions(hiddenReceiverType, projectedReceiverType, substitutions);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof ClassCastException) {
                return substitutions;
            }
            throw e;
        } catch (ClassCastException ignored) {
            return substitutions;
        }
        return substitutions;
    }

    private static void collectReceiverSubstitutions(Object templateType, Object actualType, Map<Object, Object> substitutions) throws Exception {
        if (templateType == null || actualType == null) {
            return;
        }
        Object actual = actualType;
        if (templateType.getClass().getName().endsWith("WildcardBinding")) {
            Object bound = getFieldValue(templateType, "bound");
            if (bound != null && bound != templateType) {
                collectReceiverSubstitutions(bound, actual, substitutions);
            }
            return;
        }
        if (isTypeVariable(templateType)) {
            substitutions.putIfAbsent(templateType, actual);
            return;
        }
        Object[] templateArguments = safeGetTypeArguments(templateType);
        Object[] actualArguments = safeGetTypeArguments(actual);
        if (templateArguments != null && actualArguments != null && templateArguments.length == actualArguments.length) {
            for (int i = 0; i < templateArguments.length; i++) {
                collectReceiverSubstitutions(templateArguments[i], actualArguments[i], substitutions);
            }
            return;
        }
        if (safeIsArrayType(templateType) && safeIsArrayType(actual)) {
            Object templateLeaf = invokeMethod(templateType, "leafComponentType");
            Object actualLeaf = invokeMethod(actual, "leafComponentType");
            if (templateLeaf != null && actualLeaf != null
                    && templateLeaf != templateType
                    && actualLeaf != actual) {
                collectReceiverSubstitutions(templateLeaf, actualLeaf, substitutions);
            }
        }
    }

    private static Object[] getTypeArguments(Object typeBinding) throws Exception {
        Object result = invokeMethod(typeBinding, "typeArguments");
        return result instanceof Object[] ? (Object[]) result : null;
    }

    private static Object[] safeGetTypeArguments(Object typeBinding) throws Exception {
        try {
            return getTypeArguments(typeBinding);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof ClassCastException) {
                return null;
            }
            throw e;
        } catch (ClassCastException ignored) {
            return null;
        }
    }

    private static boolean isTypeVariable(Object typeBinding) throws Exception {
        Object result = invokeMethod(typeBinding, "isTypeVariable");
        return result instanceof Boolean && (Boolean) result;
    }

    private static boolean isArrayType(Object typeBinding) throws Exception {
        Object result = invokeMethod(typeBinding, "isArrayType");
        return result instanceof Boolean && (Boolean) result;
    }

    private static boolean safeIsArrayType(Object typeBinding) throws Exception {
        try {
            return isArrayType(typeBinding);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof ClassCastException) {
                return false;
            }
            throw e;
        } catch (ClassCastException ignored) {
            return false;
        }
    }

    private static Object substituteType(Object scope, Object typeBinding, Map<Object, Object> substitutions) throws Exception {
        if (typeBinding == null || substitutions.isEmpty()) {
            return typeBinding;
        }
        Object environment = invokeMethod(scope, "environment");
        if (environment == null) {
            return typeBinding;
        }
        Class<?> substitutionClass = loadClass(SUBSTITUTION_CLASS, scope);
        Object substitution = Proxy.newProxyInstance(
                substitutionClass.getClassLoader(),
                new Class<?>[]{substitutionClass},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("substitute".equals(name) && args != null && args.length == 1) {
                        return lookupSubstitution(substitutions, args[0]);
                    }
                    if ("environment".equals(name)) {
                        return environment;
                    }
                    if ("isRawSubstitution".equals(name)) {
                        return false;
                    }
                    if ("toString".equals(name)) {
                        return "ZirconSubstitution";
                    }
                    return null;
                }
        );
        return invokeStaticMethod(loadClass(SCOPE_CLASS, scope), "substitute", substitution, typeBinding);
    }

    private static Object[] substituteTypeBindings(Object scope, Object[] typeBindings, Map<Object, Object> substitutions) throws Exception {
        if (typeBindings == null || typeBindings.length == 0 || substitutions.isEmpty()) {
            return typeBindings;
        }
        Object[] result = Arrays.copyOf(typeBindings, typeBindings.length);
        for (int index = 0; index < result.length; index++) {
            result[index] = substituteType(scope, result[index], substitutions);
        }
        return result;
    }

    private static Object lookupSubstitution(Map<Object, Object> substitutions, Object typeVariable) throws Exception {
        Object direct = substitutions.get(typeVariable);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<Object, Object> entry : substitutions.entrySet()) {
            if (sameTypeVariable(entry.getKey(), typeVariable)) {
                return entry.getValue();
            }
        }
        return typeVariable;
    }

    private static boolean sameTypeVariable(Object left, Object right) throws Exception {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        Object leftRank = getFieldValue(left, "rank");
        Object rightRank = getFieldValue(right, "rank");
        if (leftRank instanceof Integer && rightRank instanceof Integer && !leftRank.equals(rightRank)) {
            return false;
        }
        Object leftSourceName = getFieldValue(left, "sourceName");
        Object rightSourceName = getFieldValue(right, "sourceName");
        if (leftSourceName instanceof char[] && rightSourceName instanceof char[]) {
            return Arrays.equals((char[]) leftSourceName, (char[]) rightSourceName);
        }
        return false;
    }

    private static Object selectPreferredBinding(
            List<CandidateBinding> compatibleBindings,
            Object receiverType,
            Object scope,
            Object compilationUnitScope,
            Object[] argumentTypes,
            Object invocationSite
    ) throws Exception {
        List<CandidateBinding> narrowed = filterCoverCandidates(compatibleBindings);
        narrowed = filterLowestTargetCandidates(narrowed, scope);
        if (narrowed.size() == 1) {
            return narrowed.get(0).binding;
        }

        Object heuristicSelected = selectHeuristicOverloadBinding(narrowed, scope, invocationSite);
        if (heuristicSelected != null && !isProblem(heuristicSelected)) {
            return heuristicSelected;
        }

        Object selected;
        try {
            selected = invokeMostSpecificMethodBinding(narrowed, receiverType, scope, argumentTypes, invocationSite);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (shouldIgnoreFunctionalRefreshFailure(e.getCause())) {
                if (heuristicSelected != null) {
                    return heuristicSelected;
                }
                selected = narrowed.get(narrowed.size() - 1).binding;
            } else {
                throw e;
            }
        } catch (NullPointerException e) {
            if (shouldIgnoreFunctionalRefreshFailure(e)) {
                if (heuristicSelected != null) {
                    return heuristicSelected;
                }
                selected = narrowed.get(narrowed.size() - 1).binding;
            } else {
                throw e;
            }
        }
        if (!isProblem(selected)) {
            return selected;
        }

        String currentPackageName = getQualifiedPackageName(getFieldValue(compilationUnitScope, "fPackage"));
        narrowed.sort((left, right) -> comparePackageCloseness(currentPackageName, left.ownerClassName, right.ownerClassName));
        return narrowed.get(narrowed.size() - 1).binding;
    }

    private static Object selectHeuristicOverloadBinding(
            List<CandidateBinding> candidates,
            Object scope,
            Object invocationSite
    ) throws Exception {
        if (candidates == null || candidates.isEmpty() || scope == null || invocationSite == null) {
            return null;
        }
        Object rawArguments = getFieldValue(invocationSite, "arguments");
        if (!(rawArguments instanceof Object[])) {
            return null;
        }
        Object[] arguments = (Object[]) rawArguments;
        CandidateBinding best = null;
        int bestScore = Integer.MIN_VALUE;
        boolean duplicate = false;
        for (CandidateBinding candidate : candidates) {
            int score = scoreFunctionalOverloadCandidate(candidate.binding, scope, arguments);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
                duplicate = false;
            } else if (score == bestScore) {
                duplicate = true;
            }
        }
        return duplicate || best == null ? null : best.binding;
    }

    private static int scoreFunctionalOverloadCandidate(Object binding, Object scope, Object[] arguments) throws Exception {
        if (binding == null || scope == null || arguments == null) {
            return Integer.MIN_VALUE;
        }
        Object[] parameters = (Object[]) getFieldValue(binding, "parameters");
        if (parameters == null || parameters.length != arguments.length) {
            return Integer.MIN_VALUE;
        }
        int score = 0;
        boolean used = false;
        for (int index = 0; index < arguments.length; index++) {
            Integer lambdaParameterCount = inferLambdaParameterCount(arguments[index]);
            Integer functionalParameterCount = inferFunctionalParameterCount(parameters[index], scope);
            if (lambdaParameterCount != null && functionalParameterCount != null) {
                used = true;
                score += lambdaParameterCount.equals(functionalParameterCount) ? 6 : -6;
            }
            Boolean lambdaReturnsValue = inferLambdaReturnsValue(arguments[index]);
            Boolean functionalReturnsValue = inferFunctionalReturnsValue(parameters[index], scope);
            if (lambdaReturnsValue == null || functionalReturnsValue == null) {
                continue;
            }
            used = true;
            score += lambdaReturnsValue.equals(functionalReturnsValue) ? 4 : -4;
        }
        return used ? score : Integer.MIN_VALUE;
    }

    private static Integer inferLambdaParameterCount(Object argument) throws Exception {
        if (argument == null || !argument.getClass().getName().endsWith("LambdaExpression")) {
            return null;
        }
        Object rawArguments = getFieldValue(argument, "arguments");
        return rawArguments instanceof Object[] ? ((Object[]) rawArguments).length : null;
    }

    private static Boolean inferLambdaReturnsValue(Object argument) throws Exception {
        if (argument == null || !argument.getClass().getName().endsWith("LambdaExpression")) {
            return null;
        }
        Object body = getFieldValue(argument, "body");
        if (body == null) {
            return Boolean.FALSE;
        }
        if (!body.getClass().getName().endsWith("Block")) {
            return Boolean.TRUE;
        }
        Object rawStatements = getFieldValue(body, "statements");
        if (!(rawStatements instanceof Object[])) {
            return Boolean.FALSE;
        }
        for (Object statement : (Object[]) rawStatements) {
            if (statement == null || !statement.getClass().getName().endsWith("ReturnStatement")) {
                continue;
            }
            return getFieldValue(statement, "expression") != null;
        }
        return Boolean.FALSE;
    }

    private static Boolean inferFunctionalReturnsValue(Object parameterType, Object scope) throws Exception {
        if (parameterType == null || scope == null) {
            return null;
        }
        try {
            Object descriptor = invokeMethod(parameterType, "getSingleAbstractMethod", scope, true);
            if (descriptor != null && !isProblem(descriptor)) {
                Object returnType = getFieldValue(descriptor, "returnType");
                if (returnType != null) {
                    String typeName = getTypeName(returnType);
                    if ("void".equals(typeName)) {
                        return Boolean.FALSE;
                    }
                    return Boolean.TRUE;
                }
            }
        } catch (Exception ignored) {
        }
        Object erasure = eraseTypeBinding(parameterType);
        String qualifiedName = getQualifiedTypeName(erasure);
        String simpleName = getTypeName(erasure);
        if (matchesFunctionalVoidType(qualifiedName, simpleName)) {
            return Boolean.FALSE;
        }
        if (matchesFunctionalValueType(qualifiedName, simpleName)) {
            return Boolean.TRUE;
        }
        return null;
    }

    private static Integer inferFunctionalParameterCount(Object parameterType, Object scope) throws Exception {
        if (parameterType == null || scope == null) {
            return null;
        }
        try {
            Object descriptor = invokeMethod(parameterType, "getSingleAbstractMethod", scope, true);
            if (descriptor != null && !isProblem(descriptor)) {
                Object rawParameters = getFieldValue(descriptor, "parameters");
                if (rawParameters instanceof Object[]) {
                    return ((Object[]) rawParameters).length;
                }
            }
        } catch (Exception ignored) {
        }
        Object erasure = eraseTypeBinding(parameterType);
        String qualifiedName = getQualifiedTypeName(erasure);
        String simpleName = getTypeName(erasure);
        if (matchesZeroArityFunctionalType(qualifiedName, simpleName)) {
            return 0;
        }
        if (matchesBinaryFunctionalType(qualifiedName, simpleName)) {
            return 2;
        }
        if (matchesUnaryFunctionalType(qualifiedName, simpleName)) {
            return 1;
        }
        return null;
    }

    private static boolean matchesZeroArityFunctionalType(String qualifiedName, String simpleName) {
        return "java.lang.Runnable".equals(qualifiedName)
                || "java.util.concurrent.Callable".equals(qualifiedName)
                || endsWithTypeName(simpleName, "Runnable")
                || endsWithTypeName(simpleName, "Supplier")
                || endsWithTypeName(simpleName, "Callable");
    }

    private static boolean matchesUnaryFunctionalType(String qualifiedName, String simpleName) {
        return endsWithTypeName(simpleName, "Consumer")
                || endsWithTypeName(simpleName, "Function")
                || endsWithTypeName(simpleName, "Predicate")
                || endsWithTypeName(simpleName, "UnaryOperator")
                || endsWithTypeName(simpleName, "ThrowConsumer")
                || endsWithTypeName(simpleName, "ThrowFunction")
                || endsWithTypeName(simpleName, "ThrowPredicate");
    }

    private static boolean matchesBinaryFunctionalType(String qualifiedName, String simpleName) {
        return endsWithTypeName(simpleName, "BiConsumer")
                || endsWithTypeName(simpleName, "BiFunction")
                || endsWithTypeName(simpleName, "BiPredicate")
                || endsWithTypeName(simpleName, "BinaryOperator")
                || endsWithTypeName(simpleName, "ThrowBiConsumer");
    }

    private static boolean matchesFunctionalVoidType(String qualifiedName, String simpleName) {
        return "java.lang.Runnable".equals(qualifiedName)
                || endsWithTypeName(simpleName, "Runnable")
                || endsWithTypeName(simpleName, "Consumer")
                || endsWithTypeName(simpleName, "BiConsumer")
                || endsWithTypeName(simpleName, "ThrowConsumer");
    }

    private static boolean matchesFunctionalValueType(String qualifiedName, String simpleName) {
        return "java.util.concurrent.Callable".equals(qualifiedName)
                || endsWithTypeName(simpleName, "Supplier")
                || endsWithTypeName(simpleName, "Callable")
                || endsWithTypeName(simpleName, "Function")
                || endsWithTypeName(simpleName, "BiFunction")
                || endsWithTypeName(simpleName, "UnaryOperator")
                || endsWithTypeName(simpleName, "BinaryOperator")
                || endsWithTypeName(simpleName, "Predicate")
                || endsWithTypeName(simpleName, "BiPredicate")
                || endsWithTypeName(simpleName, "ThrowFunction")
                || endsWithTypeName(simpleName, "ThrowPredicate");
    }

    private static boolean endsWithTypeName(String value, String suffix) {
        return value != null && !value.isEmpty() && value.endsWith(suffix);
    }

    private static List<CandidateBinding> filterCoverCandidates(List<CandidateBinding> compatibleBindings) {
        List<CandidateBinding> coverBindings = new ArrayList<>();
        for (CandidateBinding candidate : compatibleBindings) {
            if (candidate.cover) {
                coverBindings.add(candidate);
            }
        }
        return coverBindings.isEmpty() ? compatibleBindings : coverBindings;
    }

    private static List<CandidateBinding> filterLowestTargetCandidates(List<CandidateBinding> candidates, Object scope) throws Exception {
        List<CandidateBinding> narrowed = new ArrayList<>();
        Object lowestTargetType = null;
        for (CandidateBinding candidate : candidates) {
            if (candidate.targetType == null) {
                if (lowestTargetType == null) {
                    narrowed.add(candidate);
                }
                continue;
            }
            if (lowestTargetType == null) {
                lowestTargetType = candidate.targetType;
                narrowed.clear();
                narrowed.add(candidate);
                continue;
            }
            if (isSameType(candidate.targetType, lowestTargetType)) {
                narrowed.add(candidate);
                continue;
            }
            if (isMoreSpecificType(candidate.targetType, lowestTargetType, scope)) {
                lowestTargetType = candidate.targetType;
                narrowed.clear();
                narrowed.add(candidate);
            }
        }
        return narrowed.isEmpty() ? candidates : narrowed;
    }

    private static Object invokeMostSpecificMethodBinding(
            List<CandidateBinding> candidates,
            Object receiverType,
            Object scope,
            Object[] argumentTypes,
            Object invocationSite
    ) throws Exception {
        Object methodBindingArray = Array.newInstance(loadClass(METHOD_BINDING_CLASS, scope), candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            Array.set(methodBindingArray, index, candidates.get(index).binding);
        }
        return invokeMethod(scope,
                "mostSpecificMethodBinding",
                methodBindingArray,
                candidates.size(),
                argumentTypes,
                invocationSite,
                asReferenceBinding(receiverType));
    }

    private static Object selectMostSpecificTarget(List<Object> candidateTargets, Object receiverType, Object scope) throws Exception {
        Object selected = null;
        for (Object candidateTarget : candidateTargets) {
            if (candidateTarget == null) {
                continue;
            }
            if (!isObjectType(candidateTarget) && !isCompatibleReceiver(receiverType, candidateTarget, scope)) {
                continue;
            }
            if (selected == null || isMoreSpecificType(candidateTarget, selected, scope)) {
                selected = candidateTarget;
            }
        }
        return selected;
    }

    private static boolean isMoreSpecificType(Object candidateType, Object currentType, Object scope) throws Exception {
        if (candidateType == null || currentType == null) {
            return false;
        }
        Object candidateErasure = eraseTypeBinding(candidateType);
        Object currentErasure = eraseTypeBinding(currentType);
        if (candidateErasure == null || currentErasure == null) {
            return false;
        }
        Object assignable = invokeMethod(candidateErasure, "isCompatibleWith", currentErasure, scope);
        return assignable instanceof Boolean && (Boolean) assignable;
    }

    private static boolean isSameType(Object leftType, Object rightType) throws Exception {
        if (leftType == null || rightType == null) {
            return false;
        }
        Object leftErasure = eraseTypeBinding(leftType);
        Object rightErasure = eraseTypeBinding(rightType);
        return getQualifiedTypeName(leftErasure).equals(getQualifiedTypeName(rightErasure));
    }

    private static Object eraseTypeBinding(Object typeBinding) throws Exception {
        if (typeBinding == null) {
            return null;
        }
        Object erasure = invokeMethod(typeBinding, "erasure");
        return erasure != null ? erasure : typeBinding;
    }

    private static int comparePackageCloseness(String currentPackageName, String leftOwnerClassName, String rightOwnerClassName) {
        String[] leftParts = leftOwnerClassName.split("\\.");
        String[] rightParts = rightOwnerClassName.split("\\.");
        String[] currentParts = currentPackageName.split("\\.");
        for (int index = 0; index < currentParts.length; index++) {
            String currentPart = currentParts[index];
            boolean leftMatches = leftParts.length > index && leftParts[index].equals(currentPart);
            boolean rightMatches = rightParts.length > index && rightParts[index].equals(currentPart);
            if (leftMatches && rightMatches) {
                continue;
            }
            if (leftMatches) {
                return 1;
            }
            if (rightMatches) {
                return -1;
            }
        }
        if (leftParts.length < rightParts.length) {
            return 1;
        }
        if (leftParts.length > rightParts.length) {
            return -1;
        }
        return 0;
    }

    private static String getOwnerClassName(Object typeBinding) {
        String qualifiedName = getQualifiedTypeName(typeBinding);
        return qualifiedName.isEmpty() ? getTypeName(typeBinding) : qualifiedName;
    }

    private static String getSelectorName(Object methodBinding) {
        try {
            Object selector = getFieldValue(methodBinding, "selector");
            return selector instanceof char[] ? new String((char[]) selector) : String.valueOf(selector);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String describeMethodBinding(Object methodBinding) {
        if (methodBinding == null) {
            return "null";
        }
        try {
            String declaring = getOwnerClassName(getFieldValue(methodBinding, "declaringClass"));
            String selector = getSelectorName(methodBinding);
            return declaring + "#" + selector + "(" + describeTypeArray((Object[]) getFieldValue(methodBinding, "parameters")) + ")"
                    + " -> " + getTypeName(getFieldValue(methodBinding, "returnType"));
        } catch (Exception ignored) {
            return String.valueOf(methodBinding);
        }
    }

    private static String describeMethodBindingDetailed(Object methodBinding) {
        if (methodBinding == null) {
            return "null";
        }
        try {
            String declaring = getOwnerClassName(getFieldValue(methodBinding, "declaringClass"));
            String selector = getSelectorName(methodBinding);
            return declaring + "#" + selector + "(" + describeTypeArrayDetailed((Object[]) getFieldValue(methodBinding, "parameters")) + ")"
                    + " -> " + describeTypeDebug(getFieldValue(methodBinding, "returnType"))
                    + " | typeVars=" + describeTypeArrayDetailed((Object[]) getFieldValue(methodBinding, "typeVariables"))
                    + " | flags=" + describeMethodBindingFlags(methodBinding)
                    + " | class=" + methodBinding.getClass().getName();
        } catch (Exception ignored) {
            return String.valueOf(methodBinding);
        }
    }

    private static String describeMethodBindingFlags(Object methodBinding) {
        if (methodBinding == null) {
            return "null";
        }
        try {
            Object modifiers = getFieldValue(methodBinding, "modifiers");
            Object purpose = findField(methodBinding.getClass(), "purpose") != null
                    ? getFieldValue(methodBinding, "purpose")
                    : null;
            Object fakePaddedParameters = findField(methodBinding.getClass(), "fakePaddedParameters") != null
                    ? getFieldValue(methodBinding, "fakePaddedParameters")
                    : null;
            Object lambda = findField(methodBinding.getClass(), "lambda") != null
                    ? getFieldValue(methodBinding, "lambda")
                    : null;
            boolean isStatic;
            try {
                isStatic = Boolean.TRUE.equals(invokeMethod(methodBinding, "isStatic"));
            } catch (Exception ignored) {
                isStatic = false;
            }
            return "modifiers=" + modifiers
                    + ", static=" + isStatic
                    + ", purpose=" + purpose
                    + ", fakePaddedParameters=" + fakePaddedParameters
                    + ", lambdaSource=" + describeSourceRange(lambda);
        } catch (Exception ignored) {
            return "?";
        }
    }

    private static String describeScopeDebug(Object scope) {
        if (scope == null) {
            return "null";
        }
        try {
            Object kind = getFieldValue(scope, "kind");
            Object referenceContext = findField(scope.getClass(), "referenceContext") != null
                    ? getFieldValue(scope, "referenceContext")
                    : null;
            Object parent = getFieldValue(scope, "parent");
            return scope.getClass().getSimpleName()
                    + "{kind=" + kind
                    + ", ref=" + describeSourceRange(referenceContext)
                    + ", parent=" + (parent == null ? "null" : parent.getClass().getSimpleName() + "#" + getFieldValue(parent, "kind"))
                    + "}";
        } catch (Exception ignored) {
            return scope.getClass().getSimpleName();
        }
    }

    private static String describeTypeArray(Object[] typeBindings) {
        if (typeBindings == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < typeBindings.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(getTypeName(typeBindings[index]));
        }
        return builder.toString();
    }

    private static String describeTypeArrayDetailed(Object[] typeBindings) {
        if (typeBindings == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < typeBindings.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(describeTypeDebug(typeBindings[index]));
        }
        return builder.toString();
    }

    private static String describeTypeDebug(Object typeBinding) {
        if (typeBinding == null) {
            return "null";
        }
        String readable = getReadableTypeName(typeBinding);
        String signature = getTypeSignatureDebug(typeBinding);
        return readable + "{" + typeBinding.getClass().getSimpleName()
                + (signature.isEmpty() ? "" : ", sig=" + signature)
                + "}";
    }

    private static String describeSubstitutions(Map<Object, Object> substitutions) {
        if (substitutions == null || substitutions.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<Object, Object> entry : substitutions.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(describeTypeDebug(entry.getKey()))
                    .append(" -> ")
                    .append(describeTypeDebug(entry.getValue()));
        }
        builder.append('}');
        return builder.toString();
    }

    private static String describeArgumentDescriptors(Object[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < arguments.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            Object argument = arguments[index];
            if (argument == null) {
                builder.append("null");
                continue;
            }
            try {
                Object descriptor = getFieldValue(argument, "descriptor");
                if (descriptor != null) {
                    builder.append(describeMethodBindingDetailed(descriptor));
                } else {
                    builder.append("null");
                }
            } catch (Exception ignored) {
                builder.append("?");
            }
        }
        builder.append(']');
        return builder.toString();
    }

    private static String describeLambdaArguments(Object[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < arguments.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            Object argument = arguments[index];
            if (argument == null) {
                builder.append("null");
                continue;
            }
            try {
                builder.append(String.valueOf(getFieldValue(argument, "name")))
                        .append(':')
                        .append(describeBinding(getFieldValue(argument, "binding")))
                        .append('/')
                        .append(describeTypeDebug(getFieldValue(argument, "type")))
                        .append('/')
                        .append(describeTypeDebug(getFieldValue(argument, "resolvedType")))
                        .append('/')
                        .append(describeLocalBindingState(getFieldValue(argument, "binding")));
            } catch (Exception e) {
                builder.append(argument.getClass().getSimpleName());
            }
        }
        builder.append(']');
        return builder.toString();
    }

    private static String describeOuterLocalVariables(Object[] outerLocalVariables) {
        if (outerLocalVariables == null || outerLocalVariables.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < outerLocalVariables.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            Object local = outerLocalVariables[index];
            if (local == null) {
                builder.append("null");
                continue;
            }
            try {
                builder.append(String.valueOf(getFieldValue(local, "name")))
                        .append(':')
                        .append(describeBinding(local))
                        .append('/')
                        .append(describeTypeDebug(getFieldValue(local, "type")))
                        .append('/')
                        .append(describeLocalBindingState(local));
            } catch (Exception e) {
                builder.append(local.getClass().getSimpleName());
            }
        }
        builder.append(']');
        return builder.toString();
    }

    private static String describeSourceRange(Object target) {
        if (target == null) {
            return "null";
        }
        try {
            return readIntField(target, "sourceStart") + "-" + readIntField(target, "sourceEnd");
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static String describeCompilationUnitFileName(Object compilationResult) {
        if (compilationResult == null) {
            return null;
        }
        try {
            Object fileName = invokeOptionalMethod(compilationResult, "getFileName");
            if (fileName instanceof char[]) {
                return new String((char[]) fileName);
            }
            if (fileName instanceof String) {
                return (String) fileName;
            }
            Object rawFileName = getFieldValue(compilationResult, "fileName");
            if (rawFileName instanceof char[]) {
                return new String((char[]) rawFileName);
            }
            if (rawFileName instanceof String) {
                return (String) rawFileName;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String getCompilationUnitSource(Object compilationResult) {
        if (compilationResult == null) {
            return null;
        }
        try {
            Object compilationUnit = invokeOptionalMethod(compilationResult, "getCompilationUnit");
            if (compilationUnit == null) {
                compilationUnit = getFieldValue(compilationResult, "compilationUnit");
            }
            if (compilationUnit == null) {
                compilationUnit = getFieldValue(compilationResult, "compilationUnitDeclaration");
            }
            if (compilationUnit == null) {
                return null;
            }
            Object contents = invokeOptionalMethod(compilationUnit, "getContents");
            if (contents == null) {
                contents = getFieldValue(compilationUnit, "contents");
            }
            if (contents instanceof char[]) {
                return new String((char[]) contents);
            }
            if (contents instanceof String) {
                return (String) contents;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String extractSourceContext(String source, int start, int end, int radius) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        int safeStart = Math.max(0, Math.min(source.length(), start) - Math.max(0, radius));
        int safeEnd = Math.min(source.length(), Math.max(safeStart, Math.min(source.length(), end + 1) + Math.max(0, radius)));
        return source.substring(safeStart, safeEnd);
    }

    private static String removeWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (!Character.isWhitespace(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String describeNestedLambdaStates(Object root) {
        if (root == null) {
            return "[]";
        }
        List<String> states = new ArrayList<>();
        try {
            collectNestedLambdaStates(root, states, new IdentityHashMap<>(), 0);
        } catch (Exception e) {
            states.add("error=" + e.getClass().getName() + ": " + e.getMessage());
        }
        return states.toString();
    }

    private static void collectNestedLambdaStates(Object node, List<String> states, Map<Object, Boolean> visited, int depth) throws Exception {
        if (node == null || depth > 16 || visited.put(node, Boolean.TRUE) != null || !isAstNode(node)) {
            return;
        }
        if (isLambdaExpression(node)) {
            Object[] outerLocals = getFieldValue(node, "outerLocalVariables") instanceof Object[]
                    ? (Object[]) getFieldValue(node, "outerLocalVariables")
                    : new Object[0];
            Object[] arguments = getFieldValue(node, "arguments") instanceof Object[]
                    ? (Object[]) getFieldValue(node, "arguments")
                    : new Object[0];
            states.add("source=" + describeSourceRange(node)
                    + ", binding=" + describeMethodBindingDetailed(getFieldValue(node, "binding"))
                    + ", scope=" + describeScopeDebug(getFieldValue(node, "scope"))
                    + ", outerSlotSize=" + getFieldValue(node, "outerLocalVariablesSlotSize")
                    + ", arguments=" + describeLambdaArguments(arguments)
                    + ", outerLocals=" + describeOuterLocalVariables(outerLocals));
        }
        for (Object child : getAstChildren(node)) {
            collectNestedLambdaStates(child, states, visited, depth + 1);
        }
    }

    private static boolean getAnnotationBooleanFlag(Object methodBinding, String memberName) throws Exception {
        return getAnnotationBooleanFlag(methodBinding, EX_METHOD_ANNOTATION, memberName);
    }

    private static boolean getAnnotationBooleanFlag(
            Object methodBinding,
            String annotationTypeName,
            String memberName
    ) throws Exception {
        Object[] annotations = getResolvedAnnotations(methodBinding);
        for (Object annotation : annotations) {
            if (annotation == null) {
                continue;
            }
            Object annotationType = invokeMethod(annotation, "getAnnotationType");
            if (!annotationTypeName.equals(getQualifiedTypeName(annotationType))) {
                continue;
            }
            Object pairs = invokeMethod(annotation, "getElementValuePairs");
            if (!(pairs instanceof Object[])) {
                continue;
            }
            for (Object pair : (Object[]) pairs) {
                if (pair == null) {
                    continue;
                }
                Object name = invokeMethod(pair, "getName");
                String pairName = name instanceof char[] ? new String((char[]) name) : String.valueOf(name);
                if (!memberName.equals(pairName)) {
                    continue;
                }
                Boolean parsed = readAnnotationBooleanValue(invokeMethod(pair, "getValue"));
                if (parsed != null) {
                    return parsed;
                }
                return false;
            }
        }
        Boolean binaryValue = readAnnotationBooleanValue(
                getBinaryAnnotationMemberValue(methodBinding, annotationTypeName, memberName)
        );
        if (binaryValue != null) {
            return binaryValue;
        }
        return getSourceAnnotationBooleanFlag(methodBinding, annotationTypeName, memberName);
    }

    private static String getQualifiedPackageName(Object packageBinding) {
        if (packageBinding == null) {
            return "";
        }
        try {
            Object compoundName = getFieldValue(packageBinding, "compoundName");
            if (compoundName instanceof char[][]) {
                StringBuilder builder = new StringBuilder();
                for (char[] part : (char[][]) compoundName) {
                    if (part == null || part.length == 0) {
                        continue;
                    }
                    if (builder.length() > 0) {
                        builder.append('.');
                    }
                    builder.append(part);
                }
                return builder.toString();
            }
        } catch (Exception ignored) {
            // fallback below
        }
        return getTypeName(packageBinding);
    }

    private static boolean isTypeAccessInvocation(Object invocationSite) {
        if (invocationSite == null) {
            return false;
        }
        try {
            Method isTypeAccess = findMethod(invocationSite.getClass(), "isTypeAccess");
            if (isTypeAccess != null) {
                Object result = isTypeAccess.invoke(invocationSite);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            }
        } catch (Exception ignored) {
            // fallback below
        }
        try {
            Object lhs = getFieldValue(invocationSite, "lhs");
            if (lhs != null) {
                return lhs.getClass().getName().endsWith("TypeReference");
            }
            Object receiver = getFieldValue(invocationSite, "receiver");
            if (receiver != null) {
                return receiver.getClass().getName().endsWith("TypeReference");
            }
        } catch (Exception ignored) {
            // give up
        }
        return false;
    }

    private static boolean isReferenceInvocation(Object invocationSite) {
        return invocationSite != null
                && invocationSite.getClass().getName().endsWith("ReferenceExpression");
    }

    private static boolean isSelectionInvocation(Object invocationSite) {
        if (invocationSite == null) {
            return false;
        }
        String className = invocationSite.getClass().getName();
        return className.contains(".select.")
                || className.contains("Selection");
    }

    private static boolean isImplicitInvocation(Object invocationSite) {
        if (invocationSite == null) {
            return false;
        }
        if (invocationSite.getClass().getName().endsWith("ReferenceExpression")) {
            return false;
        }
        try {
            Object receiver = getFieldValue(invocationSite, "receiver");
            if (receiver == null) {
                return true;
            }
            Object implicitThis = invokeOptionalMethod(receiver, "isImplicitThis");
            if (implicitThis instanceof Boolean) {
                return (Boolean) implicitThis;
            }
            return receiver.getClass().getName().endsWith("ThisReference");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean shouldSkipAnonymousImplicitCompilePath(
            Object scope,
            Object receiverType,
            String methodName,
            Object invocationSite
    ) throws Exception {
        boolean implicitThisInvocation = isImplicitThisInvocation(invocationSite);
        boolean selectionInvocation = isSelectionInvocation(invocationSite);
        boolean referenceInvocation = isReferenceInvocation(invocationSite);
        boolean anonymousContext = implicitThisInvocation
                && isAnonymousOrLocalContext(scope, receiverType);
        return implicitThisInvocation
                && !selectionInvocation
                && !referenceInvocation
                && anonymousContext
                && isSelectorEnabledForProperty(methodName, SKIP_ANONYMOUS_IMPLICIT_SELECTORS_PROPERTY);
    }

    private static boolean isImplicitThisInvocation(Object invocationSite) {
        if (invocationSite == null || invocationSite.getClass().getName().endsWith("ReferenceExpression")) {
            return false;
        }
        try {
            Object receiver = getFieldValue(invocationSite, "receiver");
            if (receiver == null) {
                return false;
            }
            Object implicitThis = invokeOptionalMethod(receiver, "isImplicitThis");
            if (implicitThis instanceof Boolean) {
                return (Boolean) implicitThis;
            }
            return receiver.getClass().getName().endsWith("ThisReference");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isExplicitThisInvocation(Object invocationSite) {
        if (invocationSite == null || invocationSite.getClass().getName().endsWith("ReferenceExpression")) {
            return false;
        }
        try {
            Object receiver = getFieldValue(invocationSite, "receiver");
            if (receiver == null || !receiver.getClass().getName().endsWith("ThisReference")) {
                return false;
            }
            Object implicitThis = invokeOptionalMethod(receiver, "isImplicitThis");
            return implicitThis instanceof Boolean && !((Boolean) implicitThis);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void maybeRewriteImplicitClassReceiverInvocation(
            Object scope,
            Object receiverType,
            Object hiddenReceiverType,
            Object projectedReceiverType,
            Object invocationSite
    ) throws Exception {
        if (!Util.getBooleanProperty("zircon.vscode", false)
                || scope == null
                || invocationSite == null
                || !isClassType(hiddenReceiverType)
                || !isImplicitThisInvocation(invocationSite)
                || isSelectionInvocation(invocationSite)
                || isReferenceInvocation(invocationSite)
                || !isAnonymousOrLocalContext(scope, receiverType)
                || !invocationSite.getClass().getName().endsWith("MessageSend")) {
            return;
        }
        Object currentReceiver = getFieldValue(invocationSite, "receiver");
        if (currentReceiver != null && !isImplicitThisReference(currentReceiver)) {
            return;
        }
        Object syntheticReceiver = createSyntheticGetClassReceiver(scope, receiverType, projectedReceiverType, invocationSite);
        if (syntheticReceiver == null) {
            return;
        }
        setFieldValue(invocationSite, "receiver", syntheticReceiver);
        if (projectedReceiverType != null) {
            setFieldValue(invocationSite, "actualReceiverType", projectedReceiverType);
        }
    }

    private static Object createSyntheticGetClassReceiver(
            Object scope,
            Object receiverType,
            Object projectedReceiverType,
            Object invocationSite
    ) throws Exception {
        Class<?> messageSendClass = loadClass("org.eclipse.jdt.internal.compiler.ast.MessageSend", invocationSite);
        Class<?> thisReferenceClass = loadClass("org.eclipse.jdt.internal.compiler.ast.ThisReference", invocationSite);
        Object messageSend = messageSendClass.getDeclaredConstructor().newInstance();
        Object implicitThis = invokeStaticMethod(thisReferenceClass, "implicitThis");
        copySourcePositions(invocationSite, messageSend);
        copySourcePositions(invocationSite, implicitThis);
        Object thisResolvedType = invokeMethod(implicitThis, "resolveType", scope);
        if (thisResolvedType == null) {
            thisResolvedType = receiverType;
        }
        if (thisResolvedType != null) {
            setFieldValue(implicitThis, "resolvedType", thisResolvedType);
        }
        Object notAConstant = getNotAConstant(invocationSite);
        if (notAConstant != null) {
            setFieldValue(implicitThis, "constant", notAConstant);
            setFieldValue(messageSend, "constant", notAConstant);
        }
        setFieldValue(messageSend, "receiver", implicitThis);
        setFieldValue(messageSend, "selector", "getClass".toCharArray());
        setFieldValue(messageSend, "arguments", emptyExpressionArray(invocationSite));
        Object[] noArguments = emptyTypeBindingArray(scope);
        Object binding = invokeMethod(scope, "getMethod", receiverType, "getClass".toCharArray(), noArguments, messageSend);
        if (isProblem(binding)) {
            Object javaLangObject = invokeMethod(scope, "getJavaLangObject");
            Object compilationUnitScope = getCompilationUnitScope(scope);
            if (javaLangObject != null && compilationUnitScope != null) {
                binding = invokeMethod(javaLangObject, "getExactMethod", "getClass".toCharArray(), noArguments, compilationUnitScope);
            }
        }
        if (isProblem(binding)) {
            return null;
        }
        setFieldValue(messageSend, "binding", binding);
        setFieldValue(messageSend, "actualReceiverType", receiverType);
        Object returnType = getFieldValue(binding, "returnType");
        if (returnType == null) {
            returnType = projectedReceiverType;
        }
        if (returnType != null) {
            setFieldValue(messageSend, "resolvedType", returnType);
        }
        return messageSend;
    }

    private static Object createImplicitThisReference(
            Object scope,
            Object receiverType,
            Object invocationSite
    ) throws Exception {
        if (scope == null || invocationSite == null) {
            return null;
        }
        Class<?> thisReferenceClass = loadClass("org.eclipse.jdt.internal.compiler.ast.ThisReference", invocationSite);
        Object implicitThis = invokeStaticMethod(thisReferenceClass, "implicitThis");
        if (implicitThis == null) {
            return null;
        }
        copySourcePositions(invocationSite, implicitThis);
        Object resolvedType = invokeMethod(implicitThis, "resolveType", scope);
        if (resolvedType == null) {
            resolvedType = receiverType;
        }
        if (resolvedType != null) {
            setFieldValue(implicitThis, "resolvedType", resolvedType);
        }
        Object notAConstant = getNotAConstant(invocationSite);
        if (notAConstant != null) {
            setFieldValue(implicitThis, "constant", notAConstant);
        }
        return implicitThis;
    }

    private static Object createExplicitThisPlaceholder(
            Object receiverType,
            Object invocationSite
    ) throws Exception {
        if (invocationSite == null) {
            return null;
        }
        Class<?> thisReferenceClass = loadClass("org.eclipse.jdt.internal.compiler.ast.ThisReference", invocationSite);
        Object explicitThis = thisReferenceClass.getDeclaredConstructor(int.class, int.class).newInstance(0, 0);
        if (receiverType != null) {
            setFieldValue(explicitThis, "resolvedType", receiverType);
        }
        Object notAConstant = getNotAConstant(invocationSite);
        if (notAConstant != null) {
            setFieldValue(explicitThis, "constant", notAConstant);
        }
        return explicitThis;
    }

    private static boolean isImplicitThisReference(Object receiver) {
        if (receiver == null) {
            return false;
        }
        try {
            Object implicitThis = invokeOptionalMethod(receiver, "isImplicitThis");
            if (implicitThis instanceof Boolean) {
                return (Boolean) implicitThis;
            }
        } catch (Exception ignored) {
            // fallback below
        }
        return receiver.getClass().getName().endsWith("ThisReference");
    }

    private static void copySourcePositions(Object source, Object target) throws Exception {
        if (source == null || target == null) {
            return;
        }
        copyFieldIfPresent(source, target, "sourceStart");
        copyFieldIfPresent(source, target, "sourceEnd");
        copyFieldIfPresent(source, target, "statementEnd");
        copyFieldIfPresent(source, target, "bits");
        copyFieldIfPresent(source, target, "nameSourcePosition");
    }

    private static void copyFieldIfPresent(Object source, Object target, String fieldName) throws Exception {
        Field sourceField = findField(source.getClass(), fieldName);
        Field targetField = findField(target.getClass(), fieldName);
        if (sourceField == null || targetField == null) {
            return;
        }
        targetField.set(target, sourceField.get(source));
    }

    private static boolean isAnonymousOrLocalContext(Object scope, Object receiverType) throws Exception {
        if (isAnonymousOrLocalType(receiverType)) {
            return true;
        }
        Object currentScope = scope;
        for (int depth = 0; depth < 8 && currentScope != null; depth++) {
            Object enclosingReceiverType = invokeOptionalMethod(currentScope, "enclosingReceiverType");
            if (isAnonymousOrLocalType(enclosingReceiverType)) {
                return true;
            }
            Object enclosingSourceType = invokeOptionalMethod(currentScope, "enclosingSourceType");
            if (isAnonymousOrLocalType(enclosingSourceType)) {
                return true;
            }
            Object referenceContext = getFieldValue(currentScope, "referenceContext");
            if (referenceContext != null) {
                Object binding = getFieldValue(referenceContext, "binding");
                if (isAnonymousOrLocalType(binding)) {
                    return true;
                }
            }
            currentScope = getFieldValue(currentScope, "parent");
        }
        return false;
    }

    private static boolean isAnonymousOrLocalType(Object receiverType) throws Exception {
        Object referenceBinding = asReferenceBinding(receiverType);
        if (referenceBinding == null) {
            return false;
        }
        Object anonymous = invokeMethod(referenceBinding, "isAnonymousType");
        if (anonymous instanceof Boolean && (Boolean) anonymous) {
            return true;
        }
        Object local = invokeMethod(referenceBinding, "isLocalType");
        return local instanceof Boolean && (Boolean) local;
    }

    private static boolean hasAnnotation(Object methodBinding, String annotationTypeName) throws Exception {
        for (Object annotation : getResolvedAnnotations(methodBinding)) {
            if (annotation == null) {
                continue;
            }
            Object annotationType = invokeMethod(annotation, "getAnnotationType");
            if (annotationTypeName.equals(getQualifiedTypeName(annotationType))) {
                return true;
            }
        }
        if (findSourceMethodAnnotation(methodBinding, annotationTypeName) != null) {
            return true;
        }
        for (Object annotation : getBinaryMethodAnnotations(methodBinding)) {
            if (annotation == null) {
                continue;
            }
            if (matchesTypeName(getBinaryAnnotationTypeName(annotation), annotationTypeName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExMethodBinding(Object methodBinding, Map<Object, Boolean> visited) throws Exception {
        if (methodBinding == null || visited.put(methodBinding, Boolean.TRUE) != null) {
            return false;
        }
        if (hasAnnotation(methodBinding, EX_METHOD_ANNOTATION)) {
            return true;
        }
        Object originalBinding = invokeOptionalMethod(methodBinding, "original");
        if (originalBinding != null && originalBinding != methodBinding && isExMethodBinding(originalBinding, visited)) {
            return true;
        }
        Field originalMethodField = findField(methodBinding.getClass(), "originalMethod");
        if (originalMethodField != null) {
            originalMethodField.setAccessible(true);
            Object linkedOriginalMethod = originalMethodField.get(methodBinding);
            if (linkedOriginalMethod != null
                    && linkedOriginalMethod != methodBinding
                    && isExMethodBinding(linkedOriginalMethod, visited)) {
                return true;
            }
        }
        return false;
    }

    private static Object invokeOptionalMethod(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isEmpty()) {
            return null;
        }
        try {
            return invokeMethod(target, methodName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object[] newTypedFieldArray(Object target, String fieldName, int length, Object fallbackValue) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        Class<?> componentType = null;
        if (field != null && field.getType().isArray()) {
            componentType = field.getType().getComponentType();
        }
        if (componentType == null && fallbackValue != null) {
            componentType = fallbackValue.getClass();
        }
        if (componentType == null) {
            componentType = Object.class;
        }
        return (Object[]) Array.newInstance(componentType, length);
    }

    private static InvocationRewriteState captureInvocationRewriteState(Object invocationSite) throws Exception {
        if (invocationSite == null) {
            return new InvocationRewriteState(null, null, null, null, null, null, null, null);
        }
        return new InvocationRewriteState(
                getFieldValue(invocationSite, "receiver"),
                getFieldValue(invocationSite, "arguments"),
                getFieldValue(invocationSite, "argumentTypes"),
                getFieldValue(invocationSite, "binding"),
                getFieldValue(invocationSite, "actualReceiverType"),
                getFieldValue(invocationSite, "resolvedType"),
                getFieldValue(invocationSite, "constant"),
                findField(invocationSite.getClass(), "argumentsHaveErrors") != null
                        ? getFieldValue(invocationSite, "argumentsHaveErrors")
                        : null
        );
    }

    private static void restoreInvocationRewriteState(Object invocationSite, InvocationRewriteState state) throws Exception {
        if (invocationSite == null || state == null) {
            return;
        }
        setFieldValue(invocationSite, "receiver", state.receiver);
        setFieldValue(invocationSite, "arguments", state.arguments);
        setFieldValue(invocationSite, "argumentTypes", state.argumentTypes);
        setFieldValue(invocationSite, "binding", state.binding);
        setFieldValue(invocationSite, "actualReceiverType", state.actualReceiverType);
        setFieldValue(invocationSite, "resolvedType", state.resolvedType);
        setFieldValue(invocationSite, "constant", state.constant);
        if (findField(invocationSite.getClass(), "argumentsHaveErrors") != null) {
            setFieldValue(invocationSite, "argumentsHaveErrors", state.argumentsHaveErrors);
        }
    }

    private static boolean matchesFilterAnnotations(Object methodBinding, Object receiverType) throws Exception {
        List<Object> filterAnnotations = getAnnotationClassTargets(methodBinding, "filterAnnotation");
        if (filterAnnotations.isEmpty()) {
            return true;
        }
        Object receiverReference = asReferenceBinding(receiverType);
        if (receiverReference == null) {
            return false;
        }
        for (Object filterAnnotation : filterAnnotations) {
            if (!hasDirectAnnotation(receiverReference, getQualifiedTypeName(filterAnnotation))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasDirectAnnotation(Object typeBinding, String annotationTypeName) throws Exception {
        for (Object annotation : getResolvedAnnotations(typeBinding)) {
            if (annotation == null) {
                continue;
            }
            Object annotationType = invokeMethod(annotation, "getAnnotationType");
            if (annotationTypeName.equals(getQualifiedTypeName(annotationType))) {
                return true;
            }
        }
        if (findSourceTypeAnnotation(typeBinding, annotationTypeName) != null) {
            return true;
        }
        for (Object annotation : getBinaryTypeAnnotations(typeBinding)) {
            if (annotation != null
                    && matchesTypeName(getBinaryAnnotationTypeName(annotation), annotationTypeName)) {
                return true;
            }
        }
        return false;
    }

    private static Object[] getBinaryTypeAnnotations(Object typeBinding) throws Exception {
        String qualifiedName = getQualifiedTypeName(typeBinding);
        if (qualifiedName == null || qualifiedName.isEmpty()) {
            return new Object[0];
        }
        Object[] cached = BINARY_TYPE_ANNOTATION_CACHE.get(qualifiedName);
        if (cached != null) {
            return cached;
        }
        Object binaryType = findBinaryTypeInJdtEnvironment(typeBinding);
        if (binaryType == null) {
            putBoundedConcurrentMap(BINARY_TYPE_ANNOTATION_CACHE, qualifiedName, new Object[0]);
            return new Object[0];
        }
        Object annotations = invokeMethod(binaryType, "getAnnotations");
        Object[] resolved = annotations instanceof Object[] ? (Object[]) annotations : new Object[0];
        putBoundedConcurrentMap(BINARY_TYPE_ANNOTATION_CACHE, qualifiedName, resolved);
        return resolved;
    }

    private static List<Object> getAnnotationClassTargets(Object methodBinding, String memberName) throws Exception {
        List<Object> result = new ArrayList<>();
        Object[] annotations = getResolvedAnnotations(methodBinding);
        for (Object annotation : annotations) {
            if (annotation == null) {
                continue;
            }
            Object annotationType = invokeMethod(annotation, "getAnnotationType");
            if (!EX_METHOD_ANNOTATION.equals(getQualifiedTypeName(annotationType))) {
                continue;
            }
            Object pairs = invokeMethod(annotation, "getElementValuePairs");
            if (!(pairs instanceof Object[])) {
                continue;
            }
            for (Object pair : (Object[]) pairs) {
                if (pair == null) {
                    continue;
                }
                Object name = invokeMethod(pair, "getName");
                String pairName = name instanceof char[] ? new String((char[]) name) : String.valueOf(name);
                if (!memberName.equals(pairName)) {
                    continue;
                }
                collectAnnotationClassTargets(result, invokeMethod(pair, "getValue"), methodBinding);
            }
        }
        if (result.isEmpty()) {
            collectAnnotationClassTargets(result, getBinaryAnnotationMemberValue(methodBinding, EX_METHOD_ANNOTATION, memberName), methodBinding);
        }
        if (result.isEmpty()) {
            collectSourceAnnotationClassTargets(result, methodBinding, EX_METHOD_ANNOTATION, memberName);
        }
        return result;
    }

    private static Object getBinaryAnnotationMemberValue(Object methodBinding, String annotationTypeName, String memberName) throws Exception {
        for (Object annotation : getBinaryMethodAnnotations(methodBinding)) {
            if (annotation == null || !matchesTypeName(getBinaryAnnotationTypeName(annotation), annotationTypeName)) {
                continue;
            }
            Object pairs = invokeMethod(annotation, "getElementValuePairs");
            if (!(pairs instanceof Object[])) {
                continue;
            }
            for (Object pair : (Object[]) pairs) {
                if (pair == null) {
                    continue;
                }
                Object name = invokeMethod(pair, "getName");
                String pairName = name instanceof char[] ? new String((char[]) name) : String.valueOf(name);
                if (memberName.equals(pairName)) {
                    return invokeMethod(pair, "getValue");
                }
            }
        }
        return null;
    }

    private static Object[] getResolvedAnnotations(Object binding) throws Exception {
        if (binding == null) {
            return new Object[0];
        }
        try {
            invokeMethod(binding, "getAnnotationTagBits");
        } catch (Exception ignored) {
            // Some bindings do not need or expose eager annotation resolution.
        }
        Object annotations = invokeMethod(binding, "getAnnotations");
        return annotations instanceof Object[] ? (Object[]) annotations : new Object[0];
    }

    private static Object[] getBinaryMethodAnnotations(Object methodBinding) throws Exception {
        if (methodBinding == null) {
            return new Object[0];
        }
        Object declaringClass = getFieldValue(methodBinding, "declaringClass");
        String ownerClassName = getQualifiedTypeName(declaringClass);
        if (ownerClassName == null || ownerClassName.isEmpty()) {
            return new Object[0];
        }
        Object signature = invokeMethod(methodBinding, "signature");
        if (!(signature instanceof char[])) {
            return new Object[0];
        }
        String selector = getSelectorName(methodBinding);
        String signatureString = new String((char[]) signature);
        String cacheKey = ownerClassName + "#" + selector + "#" + signatureString;
        Object[] cached = BINARY_METHOD_ANNOTATION_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // The declaring BinaryTypeBinding already belongs to JDT's configured classpath.
        // Ask its name environment for the IBinaryType instead of recursively walking the
        // workspace, parent directories and every dependency jar for each method lookup.
        Object binaryType = findBinaryTypeInJdtEnvironment(declaringClass);
        if (binaryType == null) {
            if (shouldTraceSelector(selector)) {
                Util.log("[ZirconCore] binary annotation metadata unavailable: owner="
                        + ownerClassName + ", selector=" + selector
                        + ", path=" + String.valueOf(getFieldValue(declaringClass, "path")));
            }
            putBoundedConcurrentMap(BINARY_METHOD_ANNOTATION_CACHE, cacheKey, new Object[0]);
            return new Object[0];
        }
        Object methods = invokeMethod(binaryType, "getMethods");
        if (!(methods instanceof Object[])) {
            putBoundedConcurrentMap(BINARY_METHOD_ANNOTATION_CACHE, cacheKey, new Object[0]);
            return new Object[0];
        }
        char[] selectorChars = selector.toCharArray();
        Object[] parameters = (Object[]) getFieldValue(methodBinding, "parameters");
        int parameterCount = parameters == null ? 0 : parameters.length;
        Object[] parameterCountMatchedAnnotations = null;
        for (Object binaryMethod : (Object[]) methods) {
            if (binaryMethod == null) {
                continue;
            }
            Object binarySelector = invokeMethod(binaryMethod, "getSelector");
            Object binaryDescriptor = invokeMethod(binaryMethod, "getMethodDescriptor");
            if (!(binarySelector instanceof char[]) || !(binaryDescriptor instanceof char[])) {
                continue;
            }
            if (!Arrays.equals(selectorChars, (char[]) binarySelector)) {
                continue;
            }
            if (countMethodDescriptorParameters((char[]) binaryDescriptor) == parameterCount && parameterCountMatchedAnnotations == null) {
                Object annotations = invokeMethod(binaryMethod, "getAnnotations");
                parameterCountMatchedAnnotations = annotations instanceof Object[] ? (Object[]) annotations : new Object[0];
            }
            if (!Arrays.equals((char[]) signature, (char[]) binaryDescriptor)) {
                continue;
            }
            Object annotations = invokeMethod(binaryMethod, "getAnnotations");
            Object[] resolved = annotations instanceof Object[] ? (Object[]) annotations : new Object[0];
            putBoundedConcurrentMap(BINARY_METHOD_ANNOTATION_CACHE, cacheKey, resolved);
            return resolved;
        }
        if (parameterCountMatchedAnnotations != null) {
            putBoundedConcurrentMap(BINARY_METHOD_ANNOTATION_CACHE, cacheKey, parameterCountMatchedAnnotations);
            return parameterCountMatchedAnnotations;
        }
        putBoundedConcurrentMap(BINARY_METHOD_ANNOTATION_CACHE, cacheKey, new Object[0]);
        return new Object[0];
    }

    private static Object findBinaryTypeInJdtEnvironment(Object declaringClass) {
        if (declaringClass == null) {
            return null;
        }
        try {
            Object environment = getFieldValue(declaringClass, "environment");
            if (environment == null) {
                return readBinaryTypeAtJdtBindingPath(declaringClass);
            }
            Object nameEnvironment = getFieldValue(environment, "nameEnvironment");
            Object rawCompoundName = getFieldValue(declaringClass, "compoundName");
            if (nameEnvironment == null || !(rawCompoundName instanceof char[][])) {
                return readBinaryTypeAtJdtBindingPath(declaringClass);
            }
            char[][] compoundName = (char[][]) rawCompoundName;
            if (compoundName.length == 0) {
                return readBinaryTypeAtJdtBindingPath(declaringClass);
            }
            char[][] packageName = compoundName.length > 1
                    ? Arrays.copyOf(compoundName, compoundName.length - 1)
                    : new char[0][];
            char[] simpleName = compoundName[compoundName.length - 1];
            Object answer = invokeMethod(nameEnvironment, "findType", simpleName, packageName);
            if (answer == null || isProblem(answer)) {
                answer = invokeMethod(nameEnvironment, "findType", (Object) compoundName);
            }
            if (answer == null || isProblem(answer)) {
                return readBinaryTypeAtJdtBindingPath(declaringClass);
            }
            Object isBinaryType = invokeMethod(answer, "isBinaryType");
            if (!(isBinaryType instanceof Boolean) || !((Boolean) isBinaryType)) {
                return readBinaryTypeAtJdtBindingPath(declaringClass);
            }
            return invokeMethod(answer, "getBinaryType");
        } catch (Exception ignored) {
            return readBinaryTypeAtJdtBindingPath(declaringClass);
        }
    }

    private static Object readBinaryTypeAtJdtBindingPath(Object declaringClass) {
        try {
            Object rawPath = getFieldValue(declaringClass, "path");
            Object rawCompoundName = getFieldValue(declaringClass, "compoundName");
            if (!(rawCompoundName instanceof char[][])) {
                return null;
            }
            String ownerQualifiedName = toQualifiedName((char[][]) rawCompoundName);
            String indexedLocation = JDT_BINARY_TYPE_LOCATIONS.get(ownerQualifiedName);
            if (rawPath == null
                    && (indexedLocation == null || indexedLocation.isEmpty())
                    && JDT_APPLICATION_LIBRARY_LOCATIONS.isEmpty()) {
                return null;
            }
            List<Path> binaryRoots = new ArrayList<>();
            if (rawPath == null) {
                if (indexedLocation != null && !indexedLocation.isEmpty()) {
                    binaryRoots.add(Paths.get(indexedLocation));
                }
            } else if (rawPath instanceof URI) {
                binaryRoots.add(Paths.get((URI) rawPath));
            } else {
                String pathText = String.valueOf(rawPath);
                binaryRoots.add(pathText.startsWith("file:")
                        ? Paths.get(URI.create(pathText))
                        : Paths.get(pathText));
            }
            String entryName = ownerQualifiedName.replace('.', '/') + ".class";
            for (String libraryLocation : JDT_APPLICATION_LIBRARY_LOCATIONS) {
                Path candidate = Paths.get(libraryLocation);
                if (!binaryRoots.contains(candidate)) {
                    binaryRoots.add(candidate);
                }
            }
            for (Path binaryRoot : binaryRoots) {
                Path binaryPath = Files.isDirectory(binaryRoot)
                        ? binaryRoot.resolve(entryName)
                        : binaryRoot;
                Object binaryType = readBinaryType(binaryPath, entryName, declaringClass);
                if (binaryType != null) {
                    putBoundedConcurrentMap(
                            JDT_BINARY_TYPE_LOCATIONS,
                            ownerQualifiedName,
                            binaryRoot.toString()
                    );
                    return binaryType;
                }
            }
            return null;
        } catch (Exception ignored) {
            if (Util.isTraceEnabled()) {
                Util.log("[ZirconCore] direct binary metadata read failed: "
                        + ignored.getClass().getName() + ": " + ignored.getMessage());
            }
            return null;
        }
    }

    private static Object readBinaryType(Path binaryPath, String entryName, Object anchor) throws Exception {
        if (binaryPath == null || !Files.isRegularFile(binaryPath)) {
            return null;
        }
        Class<?> classFileReaderClass = loadClass("org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader", anchor);
        if (classFileReaderClass == null) {
            return null;
        }
        if (binaryPath.toString().endsWith(".jar")) {
            try (ZipFile zipFile = new ZipFile(binaryPath.toFile())) {
                return invokeStaticMethod(classFileReaderClass, "read", zipFile, entryName);
            } catch (IOException ignored) {
                return null;
            }
        }
        try {
            return invokeStaticMethod(classFileReaderClass, "read", binaryPath.toFile());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getBinaryAnnotationTypeName(Object annotation) throws Exception {
        Object typeName = invokeMethod(annotation, "getTypeName");
        if (!(typeName instanceof char[])) {
            return "";
        }
        String raw = new String((char[]) typeName);
        if (raw.startsWith("L") && raw.endsWith(";")) {
            return raw.substring(1, raw.length() - 1).replace('/', '.');
        }
        return raw.replace('/', '.');
    }

    private static int countMethodDescriptorParameters(char[] descriptorChars) {
        if (descriptorChars == null || descriptorChars.length == 0 || descriptorChars[0] != '(') {
            return -1;
        }
        int count = 0;
        int index = 1;
        while (index < descriptorChars.length && descriptorChars[index] != ')') {
            while (index < descriptorChars.length && descriptorChars[index] == '[') {
                index++;
            }
            if (index >= descriptorChars.length || descriptorChars[index] == ')') {
                break;
            }
            if (descriptorChars[index] == 'L') {
                while (index < descriptorChars.length && descriptorChars[index] != ';') {
                    index++;
                }
                if (index < descriptorChars.length) {
                    index++;
                }
                count++;
                continue;
            }
            index++;
            count++;
        }
        return count;
    }

    private static Object findSourceMethodAnnotation(Object methodBinding, String annotationTypeName) throws Exception {
        Object[] annotations = getSourceMethodAnnotations(methodBinding);
        for (Object annotation : annotations) {
            if (annotation == null) {
                continue;
            }
            if (matchesSourceAnnotationType(annotation, annotationTypeName)) {
                return annotation;
            }
        }
        return null;
    }

    private static Object[] getSourceMethodAnnotations(Object methodBinding) throws Exception {
        if (methodBinding == null) {
            return new Object[0];
        }
        Object sourceMethod;
        try {
            sourceMethod = invokeMethod(methodBinding, "sourceMethod");
        } catch (Exception ignored) {
            return new Object[0];
        }
        if (sourceMethod == null) {
            return new Object[0];
        }
        Object annotations = getFieldValue(sourceMethod, "annotations");
        return annotations instanceof Object[] ? (Object[]) annotations : new Object[0];
    }

    private static boolean matchesSourceAnnotationType(Object annotation, String annotationTypeName) throws Exception {
        Object typeReference = getFieldValue(annotation, "type");
        String actualTypeName = getSourceTypeReferenceName(typeReference);
        if (actualTypeName.isEmpty()) {
            return false;
        }
        return matchesTypeName(actualTypeName, annotationTypeName);
    }

    private static String getSourceTypeReferenceName(Object typeReference) throws Exception {
        if (typeReference == null) {
            return "";
        }
        Object typeName = invokeMethod(typeReference, "getTypeName");
        if (typeName instanceof char[][]) {
            String qualified = toQualifiedName((char[][]) typeName);
            if (!qualified.isEmpty()) {
                return qualified;
            }
        }
        Object resolvedType = getFieldValue(typeReference, "resolvedType");
        String qualifiedResolvedName = getQualifiedTypeName(resolvedType);
        return qualifiedResolvedName != null ? qualifiedResolvedName : "";
    }

    private static boolean matchesTypeName(String actualTypeName, String expectedTypeName) {
        if (actualTypeName == null || expectedTypeName == null) {
            return false;
        }
        String normalizedActual = actualTypeName.replace('$', '.');
        String normalizedExpected = expectedTypeName.replace('$', '.');
        if (normalizedActual.equals(normalizedExpected)) {
            return true;
        }
        return getSimpleTypeName(normalizedActual).equals(getSimpleTypeName(normalizedExpected));
    }

    private static Object findSourceTypeAnnotation(Object typeBinding, String annotationTypeName) throws Exception {
        for (Object annotation : getSourceTypeAnnotations(typeBinding)) {
            if (annotation == null) {
                continue;
            }
            if (matchesSourceAnnotationType(annotation, annotationTypeName)) {
                return annotation;
            }
        }
        return null;
    }

    private static Object[] getSourceTypeAnnotations(Object typeBinding) throws Exception {
        Object sourceTypeBinding = unwrapSourceTypeBinding(typeBinding);
        if (sourceTypeBinding == null) {
            return new Object[0];
        }
        Object scope = getFieldValue(sourceTypeBinding, "scope");
        if (scope == null) {
            return new Object[0];
        }
        Object referenceContext = getFieldValue(scope, "referenceContext");
        if (referenceContext == null) {
            return new Object[0];
        }
        Object annotations = getFieldValue(referenceContext, "annotations");
        return annotations instanceof Object[] ? (Object[]) annotations : new Object[0];
    }

    private static Object unwrapSourceTypeBinding(Object typeBinding) throws Exception {
        if (typeBinding == null) {
            return null;
        }
        Object current = typeBinding;
        Object[] candidates = new Object[]{
                current,
                getFieldValue(current, "prototype"),
                getFieldValue(current, "type"),
                getFieldValue(current, "genericType"),
                getFieldValue(current, "actualType")
        };
        for (Object candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (getFieldValue(candidate, "scope") != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String getSimpleTypeName(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return "";
        }
        int dot = typeName.lastIndexOf('.');
        return dot >= 0 ? typeName.substring(dot + 1) : typeName;
    }

    private static boolean getSourceAnnotationBooleanFlag(Object methodBinding, String annotationTypeName, String memberName) throws Exception {
        Object annotation = findSourceMethodAnnotation(methodBinding, annotationTypeName);
        if (annotation == null) {
            return false;
        }
        for (Object pair : getSourceAnnotationPairs(annotation)) {
            if (pair == null) {
                continue;
            }
            Object name = getFieldValue(pair, "name");
            String pairName = name instanceof char[] ? new String((char[]) name) : String.valueOf(name);
            if (!memberName.equals(pairName)) {
                continue;
            }
            return readBooleanLiteral(getFieldValue(pair, "value"));
        }
        return false;
    }

    private static void collectSourceAnnotationClassTargets(List<Object> result, Object methodBinding, String annotationTypeName, String memberName) throws Exception {
        Object annotation = findSourceMethodAnnotation(methodBinding, annotationTypeName);
        if (annotation == null) {
            return;
        }
        for (Object pair : getSourceAnnotationPairs(annotation)) {
            if (pair == null) {
                continue;
            }
            Object name = getFieldValue(pair, "name");
            String pairName = name instanceof char[] ? new String((char[]) name) : String.valueOf(name);
            if (!memberName.equals(pairName)) {
                continue;
            }
            collectSourceAnnotationClassTargets(result, getFieldValue(pair, "value"), methodBinding);
        }
    }

    private static Object[] getSourceAnnotationPairs(Object annotation) throws Exception {
        if (annotation == null) {
            return new Object[0];
        }
        Object pairs = invokeMethod(annotation, "memberValuePairs");
        return pairs instanceof Object[] ? (Object[]) pairs : new Object[0];
    }

    private static boolean readBooleanLiteral(Object expression) throws Exception {
        if (expression == null) {
            return false;
        }
        String simpleName = expression.getClass().getSimpleName();
        if ("TrueLiteral".equals(simpleName)) {
            return true;
        }
        if ("FalseLiteral".equals(simpleName)) {
            return false;
        }
        try {
            Object source = invokeMethod(expression, "source");
            if (source instanceof char[]) {
                return "true".equalsIgnoreCase(new String((char[]) source));
            }
        } catch (Exception ignored) {
            // fall through
        }
        Object constant = getFieldValue(expression, "constant");
        if (constant != null) {
            Method booleanValue = findMethod(constant.getClass(), "booleanValue");
            if (booleanValue != null) {
                Object value = booleanValue.invoke(constant);
                if (value instanceof Boolean) {
                    return (Boolean) value;
                }
            }
        }
        return false;
    }

    private static void collectSourceAnnotationClassTargets(List<Object> result, Object value, Object methodBinding) throws Exception {
        if (value == null) {
            return;
        }
        if (value instanceof Object[]) {
            for (Object item : (Object[]) value) {
                collectSourceAnnotationClassTargets(result, item, methodBinding);
            }
            return;
        }
        if ("ArrayInitializer".equals(value.getClass().getSimpleName())) {
            Object expressions = getFieldValue(value, "expressions");
            if (expressions instanceof Object[]) {
                for (Object expression : (Object[]) expressions) {
                    collectSourceAnnotationClassTargets(result, expression, methodBinding);
                }
            }
            return;
        }
        if ("ClassLiteralAccess".equals(value.getClass().getSimpleName())) {
            Object typeReference = getFieldValue(value, "type");
            Object resolvedType = resolveSourceTypeReference(typeReference, methodBinding);
            Object referenceBinding = asReferenceBinding(resolvedType);
            if (referenceBinding != null) {
                result.add(referenceBinding);
            }
        }
    }

    private static Object resolveSourceTypeReference(Object typeReference, Object methodBinding) throws Exception {
        if (typeReference == null) {
            return null;
        }
        Object resolvedType = getFieldValue(typeReference, "resolvedType");
        if (resolvedType != null && !isProblem(resolvedType)) {
            return resolvedType;
        }
        Object sourceMethod;
        try {
            sourceMethod = invokeMethod(methodBinding, "sourceMethod");
        } catch (Exception ignored) {
            sourceMethod = null;
        }
        Object methodScope = sourceMethod != null ? getFieldValue(sourceMethod, "scope") : null;
        if (methodScope != null) {
            try {
                Object resolved = invokeMethod(typeReference, "resolveType", methodScope);
                if (resolved != null && !isProblem(resolved)) {
                    return resolved;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        Object declaringClass = getFieldValue(methodBinding, "declaringClass");
        Object classScope = declaringClass != null ? getFieldValue(declaringClass, "scope") : null;
        if (classScope != null) {
            try {
                Object resolved = invokeMethod(typeReference, "resolveType", classScope);
                if (resolved != null && !isProblem(resolved)) {
                    return resolved;
                }
            } catch (Exception ignored) {
                // give up
            }
        }
        return getFieldValue(typeReference, "resolvedType");
    }

    private static void collectAnnotationClassTargets(List<Object> result, Object value, Object anchor) throws Exception {
        if (value == null) {
            return;
        }
        if (value instanceof Object[]) {
            for (Object item : (Object[]) value) {
                collectAnnotationClassTargets(result, item, anchor);
            }
            return;
        }
        Object referenceBinding = asReferenceBinding(value);
        if (referenceBinding != null) {
            result.add(referenceBinding);
            return;
        }
        String qualifiedTypeName = extractBinaryAnnotationClassName(value);
        if (qualifiedTypeName.isEmpty()) {
            return;
        }
        Object resolved = resolveTypeBindingByName(anchor, qualifiedTypeName);
        if (resolved != null) {
            result.add(resolved);
        }
    }

    private static Boolean readAnnotationBooleanValue(Object value) throws Exception {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        Method booleanValue = findMethod(value.getClass(), "booleanValue");
        if (booleanValue != null) {
            Object result = booleanValue.invoke(value);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        }
        String text = String.valueOf(value);
        if ("true".equals(text)) {
            return true;
        }
        if ("false".equals(text)) {
            return false;
        }
        return null;
    }

    private static String extractBinaryAnnotationClassName(Object value) throws Exception {
        if (value == null) {
            return "";
        }
        if (value instanceof char[]) {
            return normalizeBinaryTypeName(new String((char[]) value));
        }
        if (value instanceof String) {
            return normalizeBinaryTypeName((String) value);
        }
        Method getTypeName = findMethod(value.getClass(), "getTypeName");
        if (getTypeName != null) {
            Object typeName = getTypeName.invoke(value);
            if (typeName instanceof char[]) {
                return normalizeBinaryTypeName(new String((char[]) typeName));
            }
            if (typeName instanceof char[][]) {
                return toQualifiedName((char[][]) typeName);
            }
            if (typeName != null) {
                return normalizeBinaryTypeName(String.valueOf(typeName));
            }
        }
        Method getSignature = findMethod(value.getClass(), "getSignature");
        if (getSignature != null) {
            Object signature = getSignature.invoke(value);
            if (signature instanceof char[]) {
                return normalizeBinaryTypeName(new String((char[]) signature));
            }
            if (signature != null) {
                return normalizeBinaryTypeName(String.valueOf(signature));
            }
        }
        Method getClassName = findMethod(value.getClass(), "getClassName");
        if (getClassName != null) {
            Object className = getClassName.invoke(value);
            if (className instanceof char[]) {
                return normalizeBinaryTypeName(new String((char[]) className));
            }
            if (className != null) {
                return normalizeBinaryTypeName(String.valueOf(className));
            }
        }
        return "";
    }

    private static String normalizeBinaryTypeName(String rawTypeName) {
        if (rawTypeName == null) {
            return "";
        }
        String normalized = rawTypeName.trim();
        while (normalized.startsWith("[")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("L") && normalized.endsWith(";")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.endsWith(".class")) {
            normalized = normalized.substring(0, normalized.length() - 6);
        }
        return normalized.replace('/', '.');
    }

    private static Object resolveTypeBindingByName(Object anchor, String qualifiedTypeName) throws Exception {
        if (anchor == null || qualifiedTypeName == null || qualifiedTypeName.isEmpty()) {
            return null;
        }
        char[][] compoundName = toCompoundName(qualifiedTypeName);
        if (compoundName.length == 0) {
            return null;
        }
        Object declaringClass = getFieldValue(anchor, "declaringClass");
        Object environment = declaringClass != null ? getFieldValue(declaringClass, "environment") : null;
        if (environment == null) {
            environment = getFieldValue(anchor, "environment");
        }
        if (environment == null) {
            try {
                environment = invokeMethod(anchor, "environment");
            } catch (Exception ignored) {
                environment = null;
            }
        }
        if (environment == null) {
            return null;
        }
        Object module = null;
        if (declaringClass != null) {
            try {
                module = invokeMethod(declaringClass, "module");
            } catch (Exception ignored) {
                module = null;
            }
        }
        if (module == null) {
            try {
                module = invokeMethod(anchor, "module");
            } catch (Exception ignored) {
                module = null;
            }
        }
        Object resolved = null;
        if (module != null) {
            try {
                resolved = invokeMethod(environment, "getType", compoundName, module);
            } catch (Exception ignored) {
                resolved = null;
            }
        }
        if (isProblem(resolved)) {
            resolved = null;
        }
        if (resolved == null) {
            try {
                resolved = invokeMethod(environment, "getType", (Object) compoundName);
            } catch (Exception ignored) {
                resolved = null;
            }
        }
        if (isProblem(resolved)) {
            resolved = null;
        }
        if (resolved == null && compoundName.length > 1) {
            char[][] packageName = Arrays.copyOf(compoundName, compoundName.length - 1);
            char[] simpleName = compoundName[compoundName.length - 1];
            Object packageBinding = invokeMethod(environment, "createPackage", (Object) packageName);
            if (packageBinding != null && !isProblem(packageBinding)) {
                try {
                    resolved = invokeMethod(environment, "askForType", packageBinding, simpleName, module);
                } catch (Exception ignored) {
                    resolved = null;
                }
                if (isProblem(resolved)) {
                    resolved = null;
                }
                if (resolved == null) {
                    try {
                        resolved = invokeMethod(packageBinding, "getType", simpleName, module);
                    } catch (Exception ignored) {
                        resolved = null;
                    }
                }
                if (isProblem(resolved)) {
                    resolved = null;
                }
                if (resolved == null) {
                    try {
                        Object typeOrPackage = invokeMethod(packageBinding, "getTypeOrPackage", simpleName, module, false);
                        if (!isProblem(typeOrPackage) && isInstanceOf(typeOrPackage, REFERENCE_BINDING_CLASS)) {
                            resolved = typeOrPackage;
                        }
                    } catch (Exception ignored) {
                        resolved = null;
                    }
                }
            }
        }
        return asReferenceBinding(resolved);
    }

    private static char[][] toCompoundName(String qualifiedTypeName) {
        if (qualifiedTypeName == null || qualifiedTypeName.isEmpty()) {
            return new char[0][];
        }
        String[] parts = qualifiedTypeName.split("\\.");
        char[][] compoundName = new char[parts.length][];
        for (int index = 0; index < parts.length; index++) {
            compoundName[index] = parts[index].toCharArray();
        }
        return compoundName;
    }

    private static Object getCompilationUnitScope(Object scope) throws Exception {
        Object direct = invokeMethod(scope, "compilationUnitScope");
        if (direct != null) {
            return direct;
        }
        return getFieldValue(scope, "compilationUnitScope");
    }

    private static boolean isCompatibleReceiver(Object receiverType, Object hiddenReceiverType, Object scope) throws Exception {
        if (receiverType == null || hiddenReceiverType == null) {
            return false;
        }
        Object effectiveReceiverType = normalizeExtensionReceiverType(receiverType, hiddenReceiverType, scope);
        Object actual = invokeMethod(effectiveReceiverType, "erasure");
        Object expected = invokeMethod(hiddenReceiverType, "erasure");
        if (actual == null || expected == null) {
            return false;
        }
        Object direct = invokeMethod(actual, "isCompatibleWith", expected, scope);
        if (direct instanceof Boolean && (Boolean) direct) {
            return true;
        }
        Object reverse = invokeMethod(expected, "isCompatibleWith", actual, scope);
        return reverse instanceof Boolean && (Boolean) reverse;
    }

    private static Object normalizeExtensionReceiverType(Object receiverType, Object hiddenReceiverType, Object scope) throws Exception {
        if (receiverType == null) {
            return null;
        }
        if (!isClassType(hiddenReceiverType)) {
            return receiverType;
        }

        Object receiverErasure = invokeMethod(receiverType, "erasure");
        if (isClassType(receiverErasure)) {
            return receiverType;
        }

        Object javaLangClass = invokeMethod(scope, "getJavaLangClass");
        Object environment = invokeMethod(scope, "environment");
        if (javaLangClass == null || environment == null) {
            return receiverType;
        }

        Object typeArgument = receiverErasure != null ? receiverErasure : receiverType;
        Object typeArguments = Array.newInstance(loadClass(TYPE_BINDING_CLASS, typeArgument), 1);
        Array.set(typeArguments, 0, typeArgument);
        Object parameterizedClass = invokeMethod(environment, "createParameterizedType", javaLangClass, typeArguments, null);
        return parameterizedClass != null ? parameterizedClass : receiverType;
    }

    private static boolean isClassType(Object typeBinding) {
        String qualifiedName = getQualifiedTypeName(typeBinding);
        return "java.lang.Class".equals(qualifiedName) || "Class".equals(qualifiedName);
    }

    private static boolean isObjectType(Object typeBinding) {
        String qualifiedName = getQualifiedTypeName(typeBinding);
        return "java.lang.Object".equals(qualifiedName) || "Object".equals(qualifiedName);
    }

    private static Object asReferenceBinding(Object typeBinding) throws Exception {
        if (typeBinding == null) {
            return null;
        }
        if (isInstanceOf(typeBinding, REFERENCE_BINDING_CLASS)) {
            return typeBinding;
        }
        Object erasure = invokeMethod(typeBinding, "erasure");
        return isInstanceOf(erasure, REFERENCE_BINDING_CLASS) ? erasure : null;
    }

    private static Object realizeBinaryType(Object referenceBinding, Object environment, char[][] compoundName) throws Exception {
        if (referenceBinding == null || environment == null || compoundName == null || compoundName.length == 0) {
            if (referenceBinding == null || compoundName == null || compoundName.length == 0) {
                return referenceBinding;
            }
        }
        if (!referenceBinding.getClass().getSimpleName().equals("MissingTypeBinding")) {
            return referenceBinding;
        }
        Object effectiveEnvironment = environment != null ? environment : getFieldValue(referenceBinding, "environment");
        if (effectiveEnvironment == null) {
            return referenceBinding;
        }
        Object nameEnvironment = getFieldValue(effectiveEnvironment, "nameEnvironment");
        if (nameEnvironment == null) {
            return referenceBinding;
        }
        char[][] packageName = compoundName.length > 1
                ? Arrays.copyOf(compoundName, compoundName.length - 1)
                : new char[0][];
        char[] simpleName = compoundName[compoundName.length - 1];
        Object answer = invokeMethod(nameEnvironment, "findType", simpleName, packageName);
        if (answer == null || isProblem(answer)) {
            answer = invokeMethod(nameEnvironment, "findType", (Object) compoundName);
        }
        if (answer == null || isProblem(answer)) {
            Object diskHydrated = loadBinaryTypeFromDisk(referenceBinding, effectiveEnvironment, compoundName);
            if (diskHydrated != null) {
                return diskHydrated;
            }
            return referenceBinding;
        }
        Object isBinaryType = invokeMethod(answer, "isBinaryType");
        if (!(isBinaryType instanceof Boolean) || !((Boolean) isBinaryType)) {
            return referenceBinding;
        }
        Object binaryType = invokeMethod(answer, "getBinaryType");
        if (binaryType == null) {
            return referenceBinding;
        }
        Object hydrated = createDetachedBinaryType(referenceBinding, binaryType, effectiveEnvironment);
        return hydrated != null ? hydrated : referenceBinding;
    }

    private static Object loadBinaryTypeFromDisk(Object referenceBinding, Object environment, char[][] compoundName) throws Exception {
        if (referenceBinding == null || environment == null || compoundName == null || compoundName.length == 0) {
            return null;
        }
        String qualifiedName = toQualifiedName(compoundName);
        if (qualifiedName.isEmpty()) {
            return null;
        }
        String entryName = qualifiedName.replace('.', '/') + ".class";
        String location = DISK_BINARY_LOCATION_CACHE.get(qualifiedName);
        if (location == null) {
            if (DISK_BINARY_LOOKUP_MISSES.contains(qualifiedName)) {
                return null;
            }
            Path locatedPath = locateBinaryTypeOnDisk(entryName);
            if (locatedPath == null) {
                DISK_BINARY_LOOKUP_MISSES.add(qualifiedName);
                return null;
            }
            location = locatedPath.toString();
            putBoundedConcurrentMap(DISK_BINARY_LOCATION_CACHE, qualifiedName, location);
        }
        Path binaryPath = Paths.get(location);
        if (!Files.isRegularFile(binaryPath)) {
            DISK_BINARY_LOCATION_CACHE.remove(qualifiedName);
            DISK_BINARY_LOOKUP_MISSES.add(qualifiedName);
            return null;
        }
        Class<?> classFileReaderClass = loadClass("org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader", environment);
        if (classFileReaderClass == null) {
            return null;
        }
        if (binaryPath.toString().endsWith(".jar")) {
            try (ZipFile zipFile = new ZipFile(binaryPath.toFile())) {
                Object binaryType = invokeStaticMethod(classFileReaderClass, "read", zipFile, entryName);
                if (binaryType == null) {
                    return null;
                }
                return createDetachedBinaryType(referenceBinding, binaryType, environment);
            } catch (IOException ignored) {
                return null;
            }
        }
        try {
            Object binaryType = invokeStaticMethod(classFileReaderClass, "read", binaryPath.toFile());
            if (binaryType == null) {
                return null;
            }
            return createDetachedBinaryType(referenceBinding, binaryType, environment);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Path locateBinaryTypeOnDisk(String entryName) {
        String normalizedEntryName = entryName.replace('/', File.separatorChar);
        for (Path root : getBinarySearchRoots()) {
            if (root == null || !Files.exists(root)) {
                continue;
            }
            Path directClassPath = root.resolve(normalizedEntryName);
            if (Files.isRegularFile(directClassPath)) {
                return directClassPath;
            }
            int maxDepth = isWorkspaceRoot(root) ? 10 : 8;
            try (Stream<Path> stream = Files.walk(root, maxDepth)) {
                Path found = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".jar")
                                ? jarContainsEntry(path, entryName)
                                : path.toString().endsWith(normalizedEntryName))
                        .findFirst()
                        .orElse(null);
                if (found != null) {
                    return found;
                }
            } catch (IOException ignored) {
                // continue
            }
        }
        return null;
    }

    private static boolean jarContainsEntry(Path jarPath, String entryName) {
        try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
            return zipFile.getEntry(entryName) != null;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static List<Path> getBinarySearchRoots() {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        String workspaceRoots = Util.getProperty("zircon.workspace.roots", "").trim();
        if (!workspaceRoots.isEmpty()) {
            for (String root : workspaceRoots.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                String trimmed = root.trim();
                if (!trimmed.isEmpty()) {
                    Path workspaceRoot = Paths.get(trimmed);
                    roots.add(workspaceRoot);
                    Path parent = workspaceRoot.getParent();
                    if (parent != null) {
                        roots.add(parent);
                        Path grandParent = parent.getParent();
                        if (grandParent != null) {
                            roots.add(grandParent);
                        }
                    }
                }
            }
        }
        String userHome = System.getProperty("user.home", "").trim();
        if (!userHome.isEmpty()) {
            roots.add(Paths.get(userHome, ".gradle", "caches", "modules-2", "files-2.1"));
            roots.add(Paths.get(userHome, ".m2", "repository"));
        }
        String userDir = System.getProperty("user.dir", "").trim();
        if (!userDir.isEmpty()) {
            roots.add(Paths.get(userDir));
        }
        return new ArrayList<>(roots);
    }

    private static boolean isWorkspaceRoot(Path root) {
        String workspaceRoots = Util.getProperty("zircon.workspace.roots", "").trim();
        if (workspaceRoots.isEmpty()) {
            return false;
        }
        String rootString = root.toString();
        for (String workspaceRoot : workspaceRoots.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (rootString.equals(workspaceRoot.trim())) {
                return true;
            }
        }
        return false;
    }

    private static Object createDetachedBinaryType(Object referenceBinding, Object binaryType, Object environment) throws Exception {
        Object packageBinding = getFieldValue(referenceBinding, "fPackage");
        if (packageBinding == null) {
            return null;
        }
        Class<?> binaryTypeBindingClass = loadClass("org.eclipse.jdt.internal.compiler.lookup.BinaryTypeBinding", referenceBinding);
        Class<?> packageBindingClass = loadClass("org.eclipse.jdt.internal.compiler.lookup.PackageBinding", referenceBinding);
        Class<?> iBinaryTypeClass = loadClass("org.eclipse.jdt.internal.compiler.env.IBinaryType", referenceBinding);
        Class<?> lookupEnvironmentClass = loadClass("org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment", environment);
        try {
            return binaryTypeBindingClass
                    .getConstructor(packageBindingClass, iBinaryTypeClass, lookupEnvironmentClass, boolean.class)
                    .newInstance(packageBinding, binaryType, environment, true);
        } catch (NoSuchMethodException ignored) {
            return binaryTypeBindingClass
                    .getConstructor(packageBindingClass, iBinaryTypeClass, lookupEnvironmentClass)
                    .newInstance(packageBinding, binaryType, environment);
        }
    }

    private static Object[] emptyTypeBindingArray(Object anchor) throws Exception {
        return (Object[]) Array.newInstance(loadClass(TYPE_BINDING_CLASS, anchor), 0);
    }

    private static Object[] emptyTypeVariableBindingArray(Object anchor) throws Exception {
        return (Object[]) Array.newInstance(loadClass("org.eclipse.jdt.internal.compiler.lookup.TypeVariableBinding", anchor), 0);
    }

    private static Object[] emptyExpressionArray(Object anchor) throws Exception {
        return (Object[]) Array.newInstance(loadClass("org.eclipse.jdt.internal.compiler.ast.Expression", anchor), 0);
    }

    private static boolean isProblem(Object binding) {
        if (binding == null) {
            return true;
        }
        return binding.getClass().getSimpleName().startsWith("Problem");
    }

    private static String describeBinding(Object binding) {
        if (binding == null) {
            return "null";
        }
        try {
            return binding.getClass().getSimpleName() + ":" + getQualifiedTypeName(binding);
        } catch (Exception ignored) {
            return binding.getClass().getSimpleName();
        }
    }

    private static boolean isInstanceOf(Object value, String className) throws Exception {
        return value != null && loadClass(className, value).isInstance(value);
    }

    private static Class<?> loadClass(String className, Object anchor) throws Exception {
        ClassLoader anchorLoader = anchor != null ? anchor.getClass().getClassLoader() : null;
        if (anchorLoader != null) {
            return Class.forName(className, false, anchorLoader);
        }
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            return Class.forName(className, false, contextLoader);
        }
        ClassLoader fallbackLoader = ZirconCore.class.getClassLoader();
        if (fallbackLoader != null) {
            return Class.forName(className, false, fallbackLoader);
        }
        return Class.forName(className);
    }

    private static Object getFieldValue(Object target, String fieldName) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return null;
        }
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setFieldValue(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return;
        }
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int readIntField(Object target, String fieldName) throws Exception {
        Object value = getFieldValue(target, fieldName);
        return value instanceof Integer ? (Integer) value : 0;
    }

    private static Object getNotAConstant(Object anchor) throws Exception {
        Class<?> constantClass = loadClass("org.eclipse.jdt.internal.compiler.impl.Constant", anchor);
        Field field = findField(constantClass, "NotAConstant");
        return field != null ? field.get(null) : null;
    }

    private static String getTypeName(Object typeBinding) {
        if (typeBinding == null) {
            return "null";
        }

        try {
            Method getSimpleName = findMethod(typeBinding.getClass(), "getSimpleName");
            if (getSimpleName != null) {
                Object result = getSimpleName.invoke(typeBinding);
                if (result != null) {
                    return result.toString();
                }
            }

            Method getSourceName = findMethod(typeBinding.getClass(), "sourceName");
            if (getSourceName != null) {
                Object result = getSourceName.invoke(typeBinding);
                if (result instanceof char[]) {
                    return new String((char[]) result);
                }
                if (result != null) {
                    return result.toString();
                }
            }

            Method getReadableName = findMethod(typeBinding.getClass(), "getReadableName");
            if (getReadableName != null) {
                Object result = getReadableName.invoke(typeBinding);
                if (result != null) {
                    return result.toString();
                }
            }

            Method readableName = findMethod(typeBinding.getClass(), "readableName");
            if (readableName != null) {
                Object result = readableName.invoke(typeBinding);
                if (result instanceof char[]) {
                    return new String((char[]) result);
                }
                if (result != null) {
                    return result.toString();
                }
            }

            return typeBinding.toString();
        } catch (Exception e) {
            return typeBinding.getClass().getSimpleName();
        }
    }

    private static String getReadableTypeName(Object typeBinding) {
        if (typeBinding == null) {
            return "null";
        }
        try {
            Method getReadableName = findMethod(typeBinding.getClass(), "getReadableName");
            if (getReadableName != null) {
                Object result = getReadableName.invoke(typeBinding);
                if (result instanceof char[]) {
                    return new String((char[]) result);
                }
                if (result != null) {
                    return result.toString();
                }
            }

            Method readableName = findMethod(typeBinding.getClass(), "readableName");
            if (readableName != null) {
                Object result = readableName.invoke(typeBinding);
                if (result instanceof char[]) {
                    return new String((char[]) result);
                }
                if (result != null) {
                    return result.toString();
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return getTypeName(typeBinding);
    }

    private static String getTypeSignatureDebug(Object typeBinding) {
        if (typeBinding == null) {
            return "";
        }
        try {
            Method genericTypeSignature = findMethod(typeBinding.getClass(), "genericTypeSignature");
            if (genericTypeSignature != null) {
                Object result = genericTypeSignature.invoke(typeBinding);
                if (result instanceof char[]) {
                    return new String((char[]) result);
                }
                if (result != null) {
                    return result.toString();
                }
            }

            Method genericSignature = findMethod(typeBinding.getClass(), "genericSignature");
            if (genericSignature != null) {
                Object result = genericSignature.invoke(typeBinding);
                if (result instanceof char[]) {
                    return new String((char[]) result);
                }
                if (result != null) {
                    return result.toString();
                }
            }
        } catch (Exception ignored) {
            // ignore debug helper failure
        }
        return "";
    }

    private static String getQualifiedTypeName(Object typeBinding) {
        if (typeBinding == null) {
            return "";
        }
        try {
            Object compoundName = getFieldValue(typeBinding, "compoundName");
            if (compoundName instanceof char[][]) {
                StringBuilder builder = new StringBuilder();
                for (char[] part : (char[][]) compoundName) {
                    if (part == null || part.length == 0) {
                        continue;
                    }
                    if (builder.length() > 0) {
                        builder.append('.');
                    }
                    builder.append(part);
                }
                if (builder.length() > 0) {
                    return builder.toString();
                }
            }
        } catch (Exception ignored) {
            // fallback below
        }
        return getTypeName(typeBinding);
    }

    private static String toQualifiedName(char[][] compoundName) {
        if (compoundName == null || compoundName.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char[] part : compoundName) {
            if (part == null || part.length == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('.');
            }
            builder.append(part);
        }
        return builder.toString();
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterTypes.length) {
                    return method;
                }
            }
            return null;
        }
    }

    private static Field findField(Class<?> type, String fieldName) {
        if (type == null || fieldName == null) {
            return null;
        }
        Map<String, Field> cachedFields = FIELD_CACHE.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>());
        Field cached = cachedFields.get(fieldName);
        if (cached != null) {
            return cached;
        }
        Set<String> misses = FIELD_MISS_CACHE.computeIfAbsent(type, ignored -> ConcurrentHashMap.newKeySet());
        if (misses.contains(fieldName)) {
            return null;
        }
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                cachedFields.put(fieldName, field);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        misses.add(fieldName);
        return null;
    }

    private static Object invokeMethod(Object target, String name, Object... args) throws Exception {
        Class<?> current = target.getClass();
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                    continue;
                }
                if (!canAccept(method.getParameterTypes(), args)) {
                    continue;
                }
                method.setAccessible(true);
                return method.invoke(target, args);
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object invokeStaticMethod(Class<?> type, String name, Object... args) throws Exception {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                    continue;
                }
                if (!canAccept(method.getParameterTypes(), args)) {
                    continue;
                }
                method.setAccessible(true);
                return method.invoke(null, args);
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean canAccept(Class<?>[] parameterTypes, Object[] args) {
        for (int index = 0; index < parameterTypes.length; index++) {
            Object arg = args[index];
            if (arg == null) {
                if (parameterTypes[index].isPrimitive()) {
                    return false;
                }
                continue;
            }
            if (parameterTypes[index].isPrimitive()) {
                Class<?> wrapper = primitiveWrapper(parameterTypes[index]);
                if (wrapper == null || !wrapper.isInstance(arg)) {
                    return false;
                }
                continue;
            }
            if (!parameterTypes[index].isInstance(arg)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> primitiveWrapper(Class<?> primitive) {
        if (primitive == int.class) return Integer.class;
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == long.class) return Long.class;
        if (primitive == short.class) return Short.class;
        if (primitive == byte.class) return Byte.class;
        if (primitive == char.class) return Character.class;
        if (primitive == float.class) return Float.class;
        if (primitive == double.class) return Double.class;
        return null;
    }

    public static final class Util {
        private static final java.nio.file.Path LOG_PATH = resolveLogPath();
        private static final long MAX_LOG_BYTES = 8L * 1024L * 1024L;

        public static synchronized void log(String msg) {
            try {
                if (java.nio.file.Files.exists(LOG_PATH)
                        && java.nio.file.Files.size(LOG_PATH) >= MAX_LOG_BYTES) {
                    java.nio.file.Files.write(
                            LOG_PATH,
                            "[Zircon] log truncated after reaching 8 MiB\n"
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                            java.nio.file.StandardOpenOption.WRITE
                    );
                }
                java.nio.file.Files.write(
                        LOG_PATH,
                        (msg + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } catch (java.io.IOException ignored) {
            }
        }

        public static boolean isDebugEnabled() {
            return getBooleanProperty("zircon.debug", false);
        }

        public static boolean isTraceEnabled() {
            return getBooleanProperty("zircon.trace", false);
        }

        public static boolean getBooleanProperty(String key, boolean defaultValue) {
            return Boolean.parseBoolean(getProperty(key, Boolean.toString(defaultValue)));
        }

        public static String getProperty(String key, String defaultValue) {
            String value = System.getProperty(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
            if (key != null && key.startsWith("zircon.")) {
                value = System.getProperty("Z" + key.substring(1));
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
            return defaultValue;
        }

        public static String stackTrace(Throwable throwable) {
            if (throwable == null) {
                return "";
            }
            java.io.StringWriter buffer = new java.io.StringWriter();
            java.io.PrintWriter writer = new java.io.PrintWriter(buffer);
            throwable.printStackTrace(writer);
            writer.flush();
            return buffer.toString();
        }

        public static java.nio.file.Path resolveLogPath() {
            String explicit = getProperty("zircon.log.path", "").trim();
            if (!explicit.isEmpty()) {
                return java.nio.file.Paths.get(explicit);
            }
            String tempDir = System.getProperty("java.io.tmpdir", ".");
            return java.nio.file.Paths.get(tempDir, "zircon_vscode_agent.log");
        }
    }
}
