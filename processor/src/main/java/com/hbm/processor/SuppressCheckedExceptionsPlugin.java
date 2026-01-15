package com.hbm.processor;

import com.sun.source.tree.ClassTree;
import com.sun.source.util.*;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Log;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SuppressCheckedExceptionsPlugin implements Plugin {
    private static final String SUPPRESS_FQN = "com.hbm.interfaces.SuppressCheckedExceptions";
    private final Map<JavaFileObject, List<LongRange>> suppressedSpansByFile = new ConcurrentHashMap<>();
    private final Set<JavaFileObject> scannedFiles = ConcurrentHashMap.newKeySet();

    private static boolean hasMarkerAnnotation(TypeElement typeElement) {
        for (AnnotationMirror mirror : typeElement.getAnnotationMirrors()) {
            Element annotationElement = mirror.getAnnotationType().asElement();
            if (annotationElement instanceof TypeElement annotationType) {
                if (SUPPRESS_FQN.equals(annotationType.getQualifiedName().toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String getName() {
        return "suppress-checked-exceptions";
    }

    @Override
    public void init(JavacTask task, String... args) {
        BasicJavacTask basic = (BasicJavacTask) task;
        Context context = basic.getContext();
        Log log = Log.instance(context);
        log.new DiagnosticHandler() {
            @Override
            public void report(JCDiagnostic diag) {
                if (shouldSuppress(diag)) {
                    return;
                }
                prev.report(diag);
            }
        };

        Trees trees = Trees.instance(task);
        var sourcePositions = trees.getSourcePositions();

        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
                if (e.getKind() != TaskEvent.Kind.ANALYZE) return;

                var cu = e.getCompilationUnit();
                if (cu == null) {
                    TypeElement typeElement = e.getTypeElement();
                    TreePath path = typeElement == null ? null : trees.getPath(typeElement);
                    cu = path == null ? null : path.getCompilationUnit();
                }
                if (cu == null) return;

                var compilationUnit = cu;
                JavaFileObject sourceFile = compilationUnit.getSourceFile();
                if (sourceFile == null || !scannedFiles.add(sourceFile)) return;

                List<LongRange> spans = new ArrayList<>();
                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitClass(ClassTree node, Void p) {
                        long start = sourcePositions.getStartPosition(compilationUnit, node);
                        long end = sourcePositions.getEndPosition(compilationUnit, node);

                        // End pos can be -1 in some edge cases; ignore if so.
                        if (start >= 0 && end >= start) {
                            Element element = trees.getElement(getCurrentPath());
                            boolean annotated = element instanceof TypeElement typeElement && hasMarkerAnnotation(typeElement);
                            spans.add(new LongRange(start, end, annotated));
                        }
                        return super.visitClass(node, p);
                    }
                }.scan(cu, null);

                if (!spans.isEmpty()) {
                    suppressedSpansByFile.put(sourceFile, Collections.synchronizedList(spans));
                }
            }
        });
    }

    private boolean shouldSuppress(JCDiagnostic diag) {
        if (diag == null) return false;
        if (diag.getType() != JCDiagnostic.DiagnosticType.ERROR) return false;

        String code = diag.getCode();
        if (code == null || !code.startsWith("compiler.err.unreported.exception")) return false;

        JavaFileObject src = diag.getSource();
        if (src == null) return false;

        long pos = diag.getStartPosition();
        if (pos < 0) pos = diag.getPosition();
        if (pos < 0) return false;

        List<LongRange> spans = suppressedSpansByFile.get(src);
        if (spans == null || spans.isEmpty()) return false;

        LongRange innermost = null;
        synchronized (spans) {
            for (LongRange r : spans) {
                if (!r.contains(pos)) {
                    continue;
                }
                if (innermost == null || r.length() < innermost.length()) {
                    innermost = r;
                }
            }
        }
        return innermost != null && innermost.annotated();
    }

    private record LongRange(long start, long end, boolean annotated) {
        boolean contains(long pos) {
            return pos >= start && pos <= end;
        }

        long length() {
            return end - start;
        }
    }
}
