import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;

import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns real library sources into signature-only stubs for the local compile-check harness.
 *
 * <p>Keeps public/protected API (types, methods, constructors, fields), compile-time constant
 * initialisers, enum constants and the annotations that matter for compilation/opt-in checks
 * ({@code @Deprecated}, {@code @UnstableApi}, {@code @RequiresOptIn}, meta-annotations). Every
 * method/constructor body becomes {@code throw new RuntimeException("stub")}; private and
 * package-private members, initialiser blocks, comments and all other annotations are dropped.
 * Imports are pruned to the names still referenced.
 *
 * <p>Usage (run with the add-exports flags in tools/stubgen/regen-media3-stubs.py):
 * {@code java StubGen.java <srcRoot> <outRoot> <frameworkClasspath> <rel/path/A.java>...}
 */
public final class StubGen {
  static final Set<String> KEEP_ANNOTATIONS = Set.of(
      "Deprecated", "UnstableApi", "RequiresOptIn", "Retention", "Target", "Documented",
      "Inherited", "FunctionalInterface", "SafeVarargs", "Repeatable");
  static final Set<String> PRIMITIVES = Set.of(
      "boolean", "byte", "short", "int", "long", "char", "float", "double");
  static final String THROW = "throw new RuntimeException(\"stub\");";

  /** One parsed source file. */
  static final class Unit {
    String rel;
    CompilationUnitTree cu;
    String src;
    String pkg;
    final Map<String, String> singleImports = new LinkedHashMap<>(); // simple -> fqn
    final List<Range> drop = new ArrayList<>(); // comment + stripped annotation ranges
  }

  record Range(int start, int end) {}

  /** Constructor signature as it will appear in the stub. */
  record Ctor(List<String> paramTypes) {}

  /** Model entry for every class declared in the stub set. */
  static final class ClassInfo {
    String fqn;
    Unit unit;
    ClassTree tree;
    List<Ctor> keptCtors = new ArrayList<>();
    boolean noArgAvailable;
    boolean hidden; // not accessible outside its package
    boolean emit;
    boolean supertypeRole; // hidden, but a supertype of an emitted type
  }

  final Map<String, ClassInfo> classes = new HashMap<>();
  final List<Unit> units = new ArrayList<>();
  SourcePositions sp;
  ClassLoader framework;

  public static void main(String[] args) throws Exception {
    Path srcRoot = Paths.get(args[0]);
    Path outRoot = Paths.get(args[1]);
    List<URL> urls = new ArrayList<>();
    for (String p : args[2].split(File.pathSeparator)) {
      if (!p.isEmpty()) urls.add(Paths.get(p).toUri().toURL());
    }
    StubGen g = new StubGen();
    g.framework = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
    List<String> rels = Arrays.asList(args).subList(3, args.length);
    g.parse(srcRoot, rels);
    for (Unit u : g.units) {
      String out = g.emitUnit(u);
      Path dest = outRoot.resolve(u.rel);
      Files.createDirectories(dest.getParent());
      Files.writeString(dest, out, StandardCharsets.UTF_8);
    }
  }

  void parse(Path srcRoot, List<String> rels) throws Exception {
    JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
    StandardJavaFileManager fm = jc.getStandardFileManager(null, null, StandardCharsets.UTF_8);
    List<File> files = new ArrayList<>();
    for (String r : rels) files.add(srcRoot.resolve(r).toFile());
    Iterable<? extends JavaFileObject> fos = fm.getJavaFileObjectsFromFiles(files);
    JavacTask task = (JavacTask) jc.getTask(null, fm, d -> {}, List.of("-proc:none"), null, fos);
    List<CompilationUnitTree> cus = new ArrayList<>();
    for (CompilationUnitTree cu : task.parse()) cus.add(cu);
    sp = Trees.instance(task).getSourcePositions();
    for (CompilationUnitTree cu : cus) {
      Unit u = new Unit();
      u.cu = cu;
      u.src = cu.getSourceFile().getCharContent(true).toString();
      Path abs = Paths.get(cu.getSourceFile().toUri()).toAbsolutePath().normalize();
      u.rel = srcRoot.toAbsolutePath().normalize().relativize(abs).toString();
      u.pkg = cu.getPackageName() == null ? "" : cu.getPackageName().toString();
      for (ImportTree it : cu.getImports()) {
        if (it.isStatic()) continue;
        String q = it.getQualifiedIdentifier().toString();
        if (!q.endsWith(".*")) u.singleImports.put(q.substring(q.lastIndexOf('.') + 1), q);
      }
      collectDrops(u);
      units.add(u);
      for (Tree t : cu.getTypeDecls()) {
        if (t instanceof ClassTree ct) {
          register(u, ct, u.pkg.isEmpty() ? "" : u.pkg + ".", vis(ct.getModifiers(), null) < 3);
        }
      }
    }
    for (ClassInfo ci : classes.values()) computeCtors(ci);
    computeEmission();
  }

  void register(Unit u, ClassTree ct, String prefix, boolean hidden) {
    ClassInfo ci = new ClassInfo();
    ci.fqn = prefix + ct.getSimpleName();
    ci.unit = u;
    ci.tree = ct;
    ci.hidden = hidden;
    classes.put(ci.fqn, ci);
    for (Tree m : ct.getMembers()) {
      if (m instanceof ClassTree nested) {
        register(u, nested, ci.fqn + ".", hidden || vis(nested.getModifiers(), ct) < 2);
      }
    }
  }

  // ---------------------------------------------------------------- text helpers

  void collectDrops(Unit u) {
    String s = u.src;
    int n = s.length();
    for (int i = 0; i < n; i++) {
      char c = s.charAt(i);
      if (c == '"' || c == '\'') {
        if (c == '"' && s.startsWith("\"\"\"", i)) {
          int e = s.indexOf("\"\"\"", i + 3);
          i = e < 0 ? n : e + 2;
          continue;
        }
        int j = i + 1;
        while (j < n && s.charAt(j) != c) {
          if (s.charAt(j) == '\\') j++;
          j++;
        }
        i = j;
      } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
        int e = s.indexOf('\n', i);
        if (e < 0) e = n;
        u.drop.add(new Range(i, e));
        i = e;
      } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
        int e = s.indexOf("*/", i + 2);
        e = e < 0 ? n : e + 2;
        u.drop.add(new Range(i, e));
        i = e - 1;
      }
    }
    new TreeScanner<Void, Void>() {
      @Override
      public Void visitAnnotation(AnnotationTree a, Void v) {
        String name = a.getAnnotationType().toString();
        name = name.substring(name.lastIndexOf('.') + 1);
        if (!KEEP_ANNOTATIONS.contains(name)) {
          u.drop.add(new Range((int) sp.getStartPosition(u.cu, a), (int) sp.getEndPosition(u.cu, a)));
          return null;
        }
        return super.visitAnnotation(a, v);
      }
    }.scan(u.cu, null);
    u.drop.sort(Comparator.comparingInt(Range::start));
  }

  /** Source text in [start, end) without comments and stripped annotations, whitespace collapsed. */
  String slice(Unit u, long start, long end) {
    StringBuilder b = new StringBuilder();
    int i = (int) start;
    int e = (int) end;
    for (Range r : u.drop) {
      if (r.end() <= i) continue;
      if (r.start() >= e) break;
      if (r.start() > i) b.append(u.src, i, r.start());
      b.append(' ');
      i = Math.max(i, r.end());
    }
    if (i < e) b.append(u.src, i, e);
    return b.toString().replaceAll("\\s+", " ").trim();
  }

  long start(Unit u, Tree t) {
    return sp.getStartPosition(u.cu, t);
  }

  long end(Unit u, Tree t) {
    return sp.getEndPosition(u.cu, t);
  }

  boolean inDrop(Unit u, int pos) {
    for (Range r : u.drop) {
      if (r.start() > pos) return false;
      if (pos < r.end()) return true;
    }
    return false;
  }

  /** Position of the '{' opening a class body. */
  int bodyOpen(Unit u, ClassTree ct) {
    long from = start(u, ct);
    if (!ct.getModifiers().getFlags().isEmpty() || !ct.getModifiers().getAnnotations().isEmpty()) {
      from = Math.max(from, end(u, ct.getModifiers()));
    }
    for (Tree t : ct.getTypeParameters()) from = Math.max(from, end(u, t));
    if (ct.getExtendsClause() != null) from = Math.max(from, end(u, ct.getExtendsClause()));
    for (Tree t : ct.getImplementsClause()) from = Math.max(from, end(u, t));
    int i = (int) from;
    while (i < u.src.length()) {
      if (u.src.charAt(i) == '{' && !inDrop(u, i)) return i;
      i++;
    }
    throw new IllegalStateException("no class body for " + ct.getSimpleName() + " in " + u.rel);
  }

  // ---------------------------------------------------------------- model

  static boolean interfaceLike(ClassTree ct) {
    return ct.getKind() == Tree.Kind.INTERFACE || ct.getKind() == Tree.Kind.ANNOTATION_TYPE;
  }

  /** 0 = private, 1 = package, 2 = protected, 3 = public. */
  static int vis(ModifiersTree m, ClassTree owner) {
    Set<Modifier> f = m.getFlags();
    if (f.contains(Modifier.PRIVATE)) return 0;
    if (f.contains(Modifier.PUBLIC) || (owner != null && interfaceLike(owner))) return 3;
    if (f.contains(Modifier.PROTECTED)) return 2;
    return 1;
  }

  static boolean isCtor(MethodTree m) {
    return m.getName().contentEquals("<init>");
  }

  static boolean isEnumConstant(VariableTree v) {
    return (((JCTree.JCVariableDecl) v).mods.flags & Flags.ENUM) != 0;
  }

  void computeCtors(ClassInfo ci) {
    ClassTree ct = ci.tree;
    if (interfaceLike(ct)) return;
    if (ci.hidden && ct.getKind() != Tree.Kind.ENUM) {
      ci.noArgAvailable = true; // stubbed as a synthetic package-private no-arg constructor
      return;
    }
    boolean anyCtor = false;
    for (Tree m : ct.getMembers()) {
      if (m instanceof MethodTree mt && isCtor(mt)) {
        anyCtor = true;
        if (ct.getKind() == Tree.Kind.ENUM || vis(mt.getModifiers(), ct) >= 2) {
          List<String> ps = new ArrayList<>();
          for (VariableTree p : mt.getParameters()) ps.add(slice(ci.unit, start(ci.unit, p.getType()), end(ci.unit, p.getType())));
          ci.keptCtors.add(new Ctor(ps));
          if (ps.isEmpty()) ci.noArgAvailable = true;
        }
      }
    }
    if (!anyCtor || ci.keptCtors.isEmpty()) ci.noArgAvailable = true; // implicit or synthetic no-arg
  }

  /** Resolves a (possibly qualified, possibly generic) type name as seen from {@code ctx}. */
  String resolve(Unit u, String ctxFqn, String type) {
    String t = type.replaceAll("<.*>", "").replaceAll("\\[\\]", "").trim();
    String first = t.contains(".") ? t.substring(0, t.indexOf('.')) : t;
    String rest = t.contains(".") ? t.substring(t.indexOf('.')) : "";
    // nested in the current class or its outers
    String scope = ctxFqn;
    while (scope != null && !scope.isEmpty()) {
      if (classes.containsKey(scope + "." + first)) return scope + "." + first + rest;
      String inherited = inheritedMember(scope, first, 0);
      if (inherited != null) return inherited + rest;
      int dot = scope.lastIndexOf('.');
      scope = dot < 0 ? null : scope.substring(0, dot);
      if (scope != null && !classes.containsKey(scope)) break;
    }
    if (u.singleImports.containsKey(first)) return u.singleImports.get(first) + rest;
    String samePkg = (u.pkg.isEmpty() ? "" : u.pkg + ".") + first;
    if (classes.containsKey(samePkg)) return samePkg + rest;
    try {
      Class.forName("java.lang." + first, false, framework);
      return "java.lang." + first + rest;
    } catch (Throwable ignored) {
    }
    return t; // assume already fully qualified
  }

  /** Finds member type {@code name} declared in a superclass/superinterface of {@code fqn}. */
  String inheritedMember(String fqn, String name, int depth) {
    ClassInfo ci = classes.get(fqn);
    if (ci == null || depth > 8) return null;
    List<Tree> supers = new ArrayList<>(ci.tree.getImplementsClause());
    if (ci.tree.getExtendsClause() != null) supers.add(0, ci.tree.getExtendsClause());
    String outer = fqn.contains(".") ? fqn.substring(0, fqn.lastIndexOf('.')) : "";
    for (Tree st : supers) {
      String text = slice(ci.unit, start(ci.unit, st), end(ci.unit, st));
      String sup = resolveNoInherit(ci.unit, outer, text);
      if (classes.containsKey(sup + "." + name)) return sup + "." + name;
      String deeper = inheritedMember(sup, name, depth + 1);
      if (deeper != null) return deeper;
    }
    return null;
  }

  String resolveNoInherit(Unit u, String ctxFqn, String type) {
    String t = type.replaceAll("<.*>", "").replaceAll("\\[\\]", "").trim();
    String first = t.contains(".") ? t.substring(0, t.indexOf('.')) : t;
    String rest = t.contains(".") ? t.substring(t.indexOf('.')) : "";
    String scope = ctxFqn;
    while (scope != null && classes.containsKey(scope)) {
      if (classes.containsKey(scope + "." + first)) return scope + "." + first + rest;
      int dot = scope.lastIndexOf('.');
      scope = dot < 0 ? null : scope.substring(0, dot);
    }
    if (u.singleImports.containsKey(first)) return u.singleImports.get(first) + rest;
    String samePkg = (u.pkg.isEmpty() ? "" : u.pkg + ".") + first;
    if (classes.containsKey(samePkg)) return samePkg + rest;
    return t;
  }

  String defaultValue(String type) {
    switch (type.trim()) {
      case "boolean": return "false";
      case "char": return "(char) 0";
      case "byte": return "(byte) 0";
      case "short": return "(short) 0";
      case "int": return "0";
      case "long": return "0L";
      case "float": return "0f";
      case "double": return "0d";
      default: return "null";
    }
  }

  /** Explicit super(...) call needed by stub constructors of {@code ci}, or "". */
  String superCall(ClassInfo ci) {
    ClassTree ct = ci.tree;
    if (ct.getKind() != Tree.Kind.CLASS || ct.getExtendsClause() == null) return "";
    Unit u = ci.unit;
    String ext = slice(u, start(u, ct.getExtendsClause()), end(u, ct.getExtendsClause()));
    String outer = ci.fqn.contains(".") ? ci.fqn.substring(0, ci.fqn.lastIndexOf('.')) : "";
    String superFqn = resolve(u, outer, ext);
    ClassInfo sup = classes.get(superFqn);
    if (sup != null) {
      if (sup.noArgAvailable) return "";
      Ctor best = null;
      for (Ctor c : sup.keptCtors) if (best == null || c.paramTypes().size() < best.paramTypes().size()) best = c;
      int arity = best.paramTypes().size();
      long same = sup.keptCtors.stream().filter(c -> c.paramTypes().size() == arity).count();
      List<String> args = new ArrayList<>();
      String supOuter = sup.fqn.contains(".") ? sup.fqn.substring(0, sup.fqn.lastIndexOf('.')) : "";
      for (String p : best.paramTypes()) {
        String d = defaultValue(p);
        if (d.equals("null") && same > 1) {
          String pt = p.replace("...", "[]");
          boolean array = pt.endsWith("[]");
          String fq = resolve(sup.unit, supOuter, pt);
          d = "(" + fq + (array ? "[]" : "") + ") null";
        }
        args.add(d);
      }
      return "super(" + String.join(", ", args) + "); ";
    }
    try {
      Class<?> c = Class.forName(binaryName(superFqn), false, framework);
      Constructor<?> best = null;
      for (Constructor<?> k : c.getDeclaredConstructors()) {
        int mod = k.getModifiers();
        if (!java.lang.reflect.Modifier.isPublic(mod) && !java.lang.reflect.Modifier.isProtected(mod)) continue;
        if (best == null || k.getParameterCount() < best.getParameterCount()) best = k;
      }
      if (best == null || best.getParameterCount() == 0) return "";
      List<String> args = new ArrayList<>();
      for (Class<?> p : best.getParameterTypes()) {
        String d = defaultValue(p.getName());
        args.add(d.equals("null") ? "(" + p.getCanonicalName() + ") null" : d);
      }
      return "super(" + String.join(", ", args) + "); ";
    } catch (Throwable t) {
      System.err.println("WARN: cannot resolve superclass " + superFqn + " of " + ci.fqn);
      return "";
    }
  }

  String binaryName(String fqn) {
    // turn a.b.Outer.Inner into a.b.Outer$Inner by probing
    String[] parts = fqn.split("\\.");
    for (int i = parts.length - 1; i > 0; i--) {
      StringBuilder b = new StringBuilder(String.join(".", Arrays.copyOfRange(parts, 0, i)));
      for (int j = i; j < parts.length; j++) b.append(j == i ? "." : "$").append(parts[j]);
      try {
        Class.forName(b.toString(), false, framework);
        return b.toString();
      } catch (Throwable ignored) {
      }
    }
    return fqn;
  }

  // ---------------------------------------------------------------- emit

  String emitUnit(Unit u) {
    StringBuilder body = new StringBuilder();
    for (Tree t : u.cu.getTypeDecls()) {
      if (t instanceof ClassTree ct) {
        ClassInfo ci = classes.get((u.pkg.isEmpty() ? "" : u.pkg + ".") + ct.getSimpleName());
        if (ci.emit) emitClass(u, ct, ci, body, "");
      }
    }
    Set<String> tokens = new HashSet<>();
    Matcher m = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*").matcher(body);
    while (m.find()) tokens.add(m.group());
    StringBuilder out = new StringBuilder();
    out.append("// GENERATED by tools/stubgen from androidx/media 1.5.1 sources: signatures only.\n");
    out.append("// Do not edit by hand; re-run tools/stubgen/regen-media3-stubs.py.\n");
    if (!u.pkg.isEmpty()) out.append("package ").append(u.pkg).append(";\n\n");
    for (ImportTree it : u.cu.getImports()) {
      String q = it.getQualifiedIdentifier().toString();
      String last = q.substring(q.lastIndexOf('.') + 1);
      if (last.equals("*") || tokens.contains(last)) {
        out.append("import ").append(it.isStatic() ? "static " : "").append(q).append(";\n");
      }
    }
    out.append('\n').append(body);
    return out.toString();
  }

  /** Names of the types referenced by a class's extends/implements clauses (incl. type args). */
  List<String> supertypeRefs(ClassInfo ci) {
    List<String> refs = new ArrayList<>();
    TreeScanner<Void, Void> sc = new TreeScanner<>() {
      @Override
      public Void visitIdentifier(IdentifierTree id, Void v) {
        refs.add(id.getName().toString());
        return null;
      }

      @Override
      public Void visitMemberSelect(MemberSelectTree ms, Void v) {
        refs.add(ms.toString());
        return null;
      }
    };
    if (ci.tree.getExtendsClause() != null) sc.scan(ci.tree.getExtendsClause(), null);
    for (Tree t : ci.tree.getImplementsClause()) sc.scan(t, null);
    return refs;
  }

  /**
   * Decides which types are emitted: every accessible type, every package-private type that is
   * (transitively) a supertype of an emitted type ("supertype role"), and the enclosing types of
   * emitted types (as bare containers).
   */
  void computeEmission() {
    Deque<ClassInfo> work = new ArrayDeque<>();
    for (ClassInfo ci : classes.values()) {
      if (!ci.hidden) {
        ci.emit = true;
        work.add(ci);
      }
    }
    while (!work.isEmpty()) {
      ClassInfo ci = work.poll();
      String outer = ci.fqn.contains(".") ? ci.fqn.substring(0, ci.fqn.lastIndexOf('.')) : "";
      for (String ref : supertypeRefs(ci)) {
        ClassInfo t = classes.get(resolve(ci.unit, ci.fqn, ref));
        if (t == null) t = classes.get(resolve(ci.unit, outer, ref));
        if (t != null && t.hidden && !t.supertypeRole) {
          t.supertypeRole = true;
          t.emit = true;
          work.add(t);
        }
      }
    }
    for (ClassInfo ci : new ArrayList<>(classes.values())) {
      if (!ci.emit) continue;
      String f = ci.fqn;
      while (f.contains(".")) {
        f = f.substring(0, f.lastIndexOf('.'));
        ClassInfo o = classes.get(f);
        if (o == null) break;
        o.emit = true;
      }
    }
  }

  /** Header text "modifiers kind Name<T>" without the extends/implements clauses. */
  String bareHeader(Unit u, ClassTree ct) {
    String head = slice(u, start(u, ct), bodyOpen(u, ct));
    String kind = switch (ct.getKind()) {
      case INTERFACE -> "interface";
      case ENUM -> "enum";
      case ANNOTATION_TYPE -> "@interface";
      default -> "class";
    };
    Matcher m = Pattern.compile("(^|\\s)" + Pattern.quote(kind) + "\\s+" + ct.getSimpleName() + "\\b").matcher(head);
    if (!m.find()) throw new IllegalStateException("header? " + head);
    int i = m.end();
    if (i < head.length() && head.substring(i).trim().startsWith("<")) {
      int depth = 0;
      for (int j = head.indexOf('<', i); j < head.length(); j++) {
        char c = head.charAt(j);
        if (c == '<') depth++;
        if (c == '>' && --depth == 0) {
          i = j + 1;
          break;
        }
      }
    }
    return head.substring(0, i);
  }

  void emitClass(Unit u, ClassTree ct, ClassInfo ci, StringBuilder out, String ind) {
    boolean containerOnly = ci.hidden && !ci.supertypeRole;
    String header;
    if (containerOnly) {
      header = bareHeader(u, ct);
    } else {
      header = slice(u, start(u, ct), bodyOpen(u, ct));
      if (ci.hidden && ct.getKind() == Tree.Kind.CLASS) {
        // Methods of hidden supertypes are only partially kept; abstract avoids "must implement".
        header = header.replaceFirst("\\bfinal\\s+", "");
        if (!header.matches("(^|.*\\s)abstract\\s.*")) {
          header = header.replaceFirst("(^|\\s)class\\s", "$1abstract class ");
        }
      }
    }
    out.append(ind).append(header).append(" {\n");
    String in = ind + "  ";
    boolean isEnum = ct.getKind() == Tree.Kind.ENUM;
    if (isEnum) {
      List<String> consts = new ArrayList<>();
      for (Tree m : ct.getMembers()) {
        if (m instanceof VariableTree v && isEnumConstant(v)) {
          StringBuilder c = new StringBuilder(v.getName());
          if (v.getInitializer() instanceof NewClassTree nc) {
            if (!nc.getArguments().isEmpty()) {
              List<String> a = new ArrayList<>();
              for (ExpressionTree e : nc.getArguments()) a.add(slice(u, start(u, e), end(u, e)));
              c.append('(').append(String.join(", ", a)).append(')');
            }
            if (nc.getClassBody() != null) {
              StringBuilder cb = new StringBuilder();
              emitMembers(u, nc.getClassBody(), ci.fqn, cb, in + "  ", null);
              c.append(" {\n").append(cb).append(in).append('}');
            }
          }
          consts.add(in + c);
        }
      }
      out.append(String.join(",\n", consts)).append(";\n");
    }
    emitMembers(u, ct, ci.fqn, out, in, ci);
    out.append(ind).append("}\n");
  }

  /**
   * Accessible types keep their public/protected API. Hidden (package-private) types are only
   * kept so public types compile: as bare containers of emitted nested types, or, when they are a
   * supertype of an emitted type, with their public/protected methods and a synthetic no-arg
   * constructor (no fields).
   */
  void emitMembers(Unit u, ClassTree ct, String fqn, StringBuilder out, String in, ClassInfo ci) {
    boolean iface = interfaceLike(ct);
    boolean isEnum = ct.getKind() == Tree.Kind.ENUM;
    boolean hidden = ci != null && ci.hidden;
    boolean containerOnly = hidden && !ci.supertypeRole;
    boolean hasExplicitCtor = false;
    boolean emittedCtor = false;
    for (Tree m : ct.getMembers()) {
      if (m instanceof MethodTree mt && isCtor(mt)) hasExplicitCtor = true;
    }
    String superCall = ci == null || containerOnly ? "" : superCall(ci);
    for (Tree m : ct.getMembers()) {
      if (m instanceof ClassTree nested) {
        ClassInfo nci = classes.get(fqn + "." + nested.getSimpleName());
        if (nci != null && nci.emit) emitClass(u, nested, nci, out, in);
      } else if (m instanceof VariableTree v) {
        if (isEnumConstant(v) || hidden) continue;
        emitField(u, ct, v, out, in, hasExplicitCtor);
      } else if (m instanceof MethodTree mt) {
        if (containerOnly) continue;
        if (isCtor(mt)) {
          if (!hidden && (isEnum || vis(mt.getModifiers(), ct) >= 2)) {
            emittedCtor = true;
            String head = slice(u, start(u, mt), start(u, mt.getBody()));
            out.append(in).append(head).append(" { ").append(isEnum ? "" : superCall).append(THROW).append(" }\n");
          }
          continue;
        }
        int vis = vis(mt.getModifiers(), ct);
        if (vis < 2) continue;
        if (mt.getBody() == null) {
          String s = slice(u, start(u, mt), end(u, mt));
          out.append(in).append(s.endsWith(";") ? s : s + ";").append('\n');
        } else {
          String head = slice(u, start(u, mt), start(u, mt.getBody()));
          out.append(in).append(head).append(" { ").append(THROW).append(" }\n");
        }
      }
    }
    if (ci != null && !iface && !containerOnly && hasExplicitCtor && !emittedCtor) {
      // Only private/package constructors in the original: keep the type non-instantiable from
      // other packages while letting same-package stub subclasses compile.
      out.append(in).append(ct.getSimpleName()).append("() { ").append(superCall).append(THROW).append(" }\n");
    }
  }

  void emitField(Unit u, ClassTree ct, VariableTree v, StringBuilder out, String in, boolean hasExplicitCtor) {
    Set<Modifier> f = v.getModifiers().getFlags();
    boolean iface = interfaceLike(ct);
    boolean isStatic = iface || f.contains(Modifier.STATIC);
    boolean isFinal = iface || f.contains(Modifier.FINAL);
    ExpressionTree init = v.getInitializer();
    int vis = vis(v.getModifiers(), ct);
    boolean constant = init != null && isStatic && isFinal && constantLooking(init)
        && (vis >= 2 || literalOnly(init));
    if (vis < 2 && !constant) return;
    String typeText = slice(u, start(u, v.getType()), end(u, v.getType()));
    String head;
    if (init != null) {
      head = slice(u, start(u, v), start(u, init));
      head = head.replaceAll("=\\s*$", "").trim();
    } else {
      head = slice(u, start(u, v), end(u, v)).replaceAll(";$", "").trim();
    }
    if (constant) {
      out.append(in).append(head).append(" = ").append(slice(u, start(u, init), end(u, init))).append(";\n");
    } else if (isStatic && isFinal) {
      out.append(in).append(head).append(" = ").append(defaultValue(typeText)).append(";\n");
    } else if (isFinal && !hasExplicitCtor) {
      out.append(in).append(head.replaceFirst("\\bfinal\\s+", "")).append(";\n");
    } else {
      out.append(in).append(head).append(";\n");
    }
  }

  static boolean literalOnly(ExpressionTree e) {
    if (e instanceof LiteralTree) return true;
    if (e instanceof ParenthesizedTree p) return literalOnly(p.getExpression());
    if (e instanceof UnaryTree un) return literalOnly(un.getExpression());
    if (e instanceof BinaryTree b) return literalOnly(b.getLeftOperand()) && literalOnly(b.getRightOperand());
    if (e instanceof TypeCastTree tc) return literalOnly(tc.getExpression());
    return false;
  }

  static boolean constantLooking(ExpressionTree e) {
    if (e instanceof LiteralTree) return true;
    if (e instanceof IdentifierTree id) return !id.getName().contentEquals("R");
    if (e instanceof MemberSelectTree ms) return constantLooking(ms.getExpression());
    if (e instanceof ParenthesizedTree p) return constantLooking(p.getExpression());
    if (e instanceof UnaryTree un) return constantLooking(un.getExpression());
    if (e instanceof BinaryTree b) return constantLooking(b.getLeftOperand()) && constantLooking(b.getRightOperand());
    if (e instanceof ConditionalExpressionTree c) {
      return constantLooking(c.getCondition()) && constantLooking(c.getTrueExpression()) && constantLooking(c.getFalseExpression());
    }
    if (e instanceof TypeCastTree tc) return constantLooking(tc.getExpression());
    return false;
  }
}
