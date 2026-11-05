import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * javac plugin approximating Android lint's {@code UnsafeOptInUsageError} (annotation-experimental
 * lint, an error that fails {@code lintDebug}): any reference to a declaration that carries a
 * {@code @RequiresOptIn} marker such as media3's {@code @UnstableApi} (on itself or an enclosing
 * class) must sit inside a declaration annotated with that marker or with
 * {@code @OptIn(markerClass = Marker.class)}. Overriding/implementing such a declaration counts as
 * a use. Imports are ignored (lint ignores them too).
 */
public final class OptInCheck implements Plugin {
  static final Set<String> REQUIRES_OPT_IN = Set.of("androidx.annotation.RequiresOptIn", "kotlin.RequiresOptIn");
  static final Set<String> OPT_IN = Set.of("androidx.annotation.OptIn", "kotlin.OptIn");

  @Override
  public String getName() {
    return "AuraOptInCheck";
  }

  @Override
  public void init(JavacTask task, String... args) {
    Trees trees = Trees.instance(task);
    Elements elements = task.getElements();
    Types types = task.getTypes();
    task.addTaskListener(new TaskListener() {
      @Override
      public void finished(TaskEvent e) {
        if (e.getKind() == TaskEvent.Kind.ANALYZE && e.getCompilationUnit() != null) {
          new Scanner(trees, elements, types, e.getCompilationUnit()).scan(e.getCompilationUnit(), null);
        }
      }
    });
  }

  static final class Scanner extends TreePathScanner<Void, Void> {
    final Trees trees;
    final Elements elements;
    final Types types;
    final CompilationUnitTree cu;
    final SourcePositions positions;
    final Set<String> reported = new HashSet<>();

    Scanner(Trees trees, Elements elements, Types types, CompilationUnitTree cu) {
      this.trees = trees;
      this.elements = elements;
      this.types = types;
      this.cu = cu;
      this.positions = trees.getSourcePositions();
    }

    @Override
    public Void visitImport(ImportTree node, Void unused) {
      return null;
    }

    @Override
    public Void visitIdentifier(IdentifierTree node, Void unused) {
      check(node, trees.getElement(getCurrentPath()));
      return super.visitIdentifier(node, unused);
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree node, Void unused) {
      check(node, trees.getElement(getCurrentPath()));
      return super.visitMemberSelect(node, unused);
    }

    @Override
    public Void visitNewClass(NewClassTree node, Void unused) {
      check(node, trees.getElement(getCurrentPath()));
      return super.visitNewClass(node, unused);
    }

    @Override
    public Void visitMemberReference(MemberReferenceTree node, Void unused) {
      check(node, trees.getElement(getCurrentPath()));
      return super.visitMemberReference(node, unused);
    }

    @Override
    public Void visitMethod(MethodTree node, Void unused) {
      Element el = trees.getElement(getCurrentPath());
      if (el instanceof ExecutableElement m && el.getEnclosingElement() instanceof TypeElement owner) {
        for (ExecutableElement sup : overridden(m, owner)) {
          Set<String> need = requiredMarkers(sup);
          if (!need.isEmpty()) report(node, sup, need, "overrides");
        }
      }
      return super.visitMethod(node, unused);
    }

    Set<ExecutableElement> overridden(ExecutableElement m, TypeElement owner) {
      Set<ExecutableElement> out = new LinkedHashSet<>();
      Set<TypeElement> seen = new HashSet<>();
      collectOverridden(m, owner, owner, out, seen);
      return out;
    }

    void collectOverridden(ExecutableElement m, TypeElement owner, TypeElement t, Set<ExecutableElement> out, Set<TypeElement> seen) {
      List<TypeMirror> sups = new java.util.ArrayList<>(t.getInterfaces());
      sups.add(t.getSuperclass());
      for (TypeMirror sm : sups) {
        if (!(sm instanceof DeclaredType dt) || !(dt.asElement() instanceof TypeElement st) || !seen.add(st)) continue;
        for (Element e : st.getEnclosedElements()) {
          if (e instanceof ExecutableElement cand && e.getKind() == ElementKind.METHOD
              && cand.getSimpleName().contentEquals(m.getSimpleName()) && elements.overrides(m, cand, owner)) {
            out.add(cand);
          }
        }
        collectOverridden(m, owner, st, out, seen);
      }
    }

    void check(Tree node, Element el) {
      if (el == null || el.getKind() == ElementKind.PACKAGE) return;
      if (el.getKind() == ElementKind.ANNOTATION_TYPE) return; // @Marker / @OptIn usages themselves
      Set<String> need = requiredMarkers(el);
      if (!need.isEmpty()) report(node, el, need, "uses");
    }

    void report(Tree node, Element target, Set<String> need, String verb) {
      Set<String> allowed = allowedMarkers(getCurrentPath());
      need.removeAll(allowed);
      if (need.isEmpty()) return;
      long pos = positions.getStartPosition(cu, node);
      String marker = need.iterator().next();
      long line = cu.getLineMap().getLineNumber(pos);
      if (!reported.add(line + marker)) return; // one report per line and marker
      String simple = marker.substring(marker.lastIndexOf('.') + 1);
      trees.printMessage(Diagnostic.Kind.ERROR,
          "[UnsafeOptInUsageError] " + verb + " @" + simple + " API " + describe(target)
              + ": add @OptIn(markerClass = " + simple + ".class) (import " + marker
              + ") to the enclosing method/class, or annotate it @" + simple + " (CI lint fails otherwise)",
          node, cu);
    }

    String describe(Element el) {
      Element owner = el.getEnclosingElement();
      if (el instanceof TypeElement te) return te.getQualifiedName().toString();
      String name = el.getKind() == ElementKind.CONSTRUCTOR ? "<init>" : el.getSimpleName().toString();
      return (owner instanceof TypeElement te ? te.getQualifiedName() + "." : "") + name;
    }

    /** Markers required by {@code el}: marker annotations on it or any enclosing type. */
    Set<String> requiredMarkers(Element el) {
      Set<String> out = new LinkedHashSet<>();
      for (Element e = el; e != null && e.getKind() != ElementKind.PACKAGE; e = e.getEnclosingElement()) {
        for (AnnotationMirror am : e.getAnnotationMirrors()) {
          TypeElement at = (TypeElement) am.getAnnotationType().asElement();
          if (isMarker(at)) out.add(at.getQualifiedName().toString());
        }
      }
      return out;
    }

    boolean isMarker(TypeElement annotationType) {
      for (AnnotationMirror meta : annotationType.getAnnotationMirrors()) {
        String n = ((TypeElement) meta.getAnnotationType().asElement()).getQualifiedName().toString();
        if (REQUIRES_OPT_IN.contains(n)) return true;
      }
      return false;
    }

    /** Markers opted into by the declarations enclosing the current usage. */
    Set<String> allowedMarkers(TreePath path) {
      Set<String> out = new HashSet<>();
      for (TreePath p = path; p != null; p = p.getParentPath()) {
        Tree t = p.getLeaf();
        if (!(t instanceof ClassTree || t instanceof MethodTree || t instanceof VariableTree)) continue;
        Element el = trees.getElement(p);
        if (el == null) continue;
        for (AnnotationMirror am : el.getAnnotationMirrors()) {
          TypeElement at = (TypeElement) am.getAnnotationType().asElement();
          String n = at.getQualifiedName().toString();
          if (isMarker(at)) out.add(n);
          if (OPT_IN.contains(n)) {
            am.getElementValues().forEach((k, v) -> {
              if (k.getSimpleName().contentEquals("markerClass")) collectClasses(v, out);
            });
          }
        }
      }
      return out;
    }

    void collectClasses(AnnotationValue v, Set<String> out) {
      Object value = v.getValue();
      if (value instanceof List<?> list) {
        for (Object o : list) collectClasses((AnnotationValue) o, out);
      } else if (value instanceof DeclaredType dt) {
        out.add(((TypeElement) dt.asElement()).getQualifiedName().toString());
      }
    }
  }
}
