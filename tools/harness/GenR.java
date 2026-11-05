import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Generates a stand-in {@code R.java} (and {@code BuildConfig.java} when the build enables it) by
 * scanning {@code app/src/main/res}, so the compile-check harness sees exactly the resources that
 * exist right now. Fields are non-final, like AGP 8's default {@code android.nonFinalResIds=true}
 * (so {@code switch} on resource ids fails here just as it does in the real build).
 *
 * <p>Usage: {@code java GenR.java <repoRoot> <outDir>}
 */
public final class GenR {
  static final Set<String> FILE_TYPES = Set.of(
      "anim", "animator", "color", "drawable", "font", "interpolator", "layout", "menu", "mipmap",
      "navigation", "raw", "transition", "xml");
  static final Pattern NEW_ID = Pattern.compile("@\\+id/([A-Za-z0-9_.]+)");

  final Map<String, Set<String>> res = new TreeMap<>();
  final Map<String, List<String>> styleables = new TreeMap<>();
  final List<String> problems = new ArrayList<>();

  public static void main(String[] args) throws Exception {
    Path root = Paths.get(args[0]);
    Path out = Paths.get(args[1]);
    String gradle = read(root.resolve("app/build.gradle"));
    String props = Files.exists(root.resolve("gradle.properties")) ? read(root.resolve("gradle.properties")) : "";
    String pkg = find(gradle, "namespace\\s*=?\\s*['\"]([\\w.]+)['\"]");
    if (pkg == null) pkg = find(read(root.resolve("app/src/main/AndroidManifest.xml")), "package=\"([\\w.]+)\"");
    boolean finalIds = props.matches("(?s).*android\\.nonFinalResIds\\s*=\\s*false.*");
    GenR g = new GenR();
    Path resDir = root.resolve("app/src/main/res");
    if (Files.isDirectory(resDir)) g.scan(resDir);
    Path dir = out.resolve(pkg.replace('.', '/'));
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("R.java"), g.render(pkg, finalIds), StandardCharsets.UTF_8);
    if (gradle.matches("(?s).*buildConfig\\s*=?\\s*true.*")) {
      Files.writeString(dir.resolve("BuildConfig.java"), buildConfig(pkg, gradle), StandardCharsets.UTF_8);
    }
    for (String p : g.problems) System.err.println("GenR: " + p);
    if (!g.problems.isEmpty()) System.exit(2);
  }

  static String read(Path p) throws IOException {
    return Files.readString(p, StandardCharsets.UTF_8);
  }

  static String find(String text, String regex) {
    Matcher m = Pattern.compile(regex).matcher(text);
    return m.find() ? m.group(1) : null;
  }

  void add(String type, String name) {
    res.computeIfAbsent(type, k -> new TreeSet<>()).add(name.replace('.', '_'));
  }

  void scan(Path resDir) throws Exception {
    List<Path> dirs;
    try (Stream<Path> s = Files.list(resDir)) {
      dirs = s.filter(Files::isDirectory).sorted().toList();
    }
    for (Path d : dirs) {
      String type = d.getFileName().toString().split("-")[0];
      List<Path> files;
      try (Stream<Path> s = Files.list(d)) {
        files = s.filter(Files::isRegularFile).sorted().toList();
      }
      for (Path f : files) {
        String file = f.getFileName().toString();
        if (file.startsWith(".")) continue;
        if (type.equals("values")) {
          if (file.endsWith(".xml")) values(f);
          continue;
        }
        if (!FILE_TYPES.contains(type)) {
          problems.add("unknown resource directory " + d.getFileName());
          break;
        }
        String name = file.contains(".") ? file.substring(0, file.indexOf('.')) : file;
        if (!name.matches("[a-z0-9_]+")) problems.add("invalid resource file name (aapt rejects it): " + resDir.relativize(f));
        add(type, name);
        if (file.endsWith(".xml")) {
          Matcher m = NEW_ID.matcher(read(f));
          while (m.find()) add("id", m.group(1));
        }
      }
    }
  }

  void values(Path f) throws Exception {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    Element root;
    try {
      root = dbf.newDocumentBuilder().parse(f.toFile()).getDocumentElement();
    } catch (Exception e) {
      problems.add("cannot parse " + f + ": " + e.getMessage());
      return;
    }
    NodeList kids = root.getChildNodes();
    for (int i = 0; i < kids.getLength(); i++) {
      Node n = kids.item(i);
      if (!(n instanceof Element e)) continue;
      String tag = e.getTagName();
      String name = e.getAttribute("name");
      switch (tag) {
        case "string", "color", "dimen", "bool", "integer", "fraction", "style", "plurals", "drawable", "attr" -> add(tag, name);
        case "array", "string-array", "integer-array" -> add("array", name);
        case "item" -> {
          if (e.getAttribute("type").isEmpty()) problems.add("<item name=\"" + name + "\"> without type in " + f);
          else add(e.getAttribute("type"), name);
        }
        case "declare-styleable" -> {
          List<String> attrs = new ArrayList<>();
          NodeList as = e.getElementsByTagName("attr");
          for (int j = 0; j < as.getLength(); j++) {
            String an = ((Element) as.item(j)).getAttribute("name");
            attrs.add(an);
            if (!an.startsWith("android:")) add("attr", an);
          }
          styleables.put(name.replace('.', '_'), attrs);
        }
        case "eat-comment", "public", "skip", "macro", "overlayable", "java-symbol", "add-resource" -> { }
        default -> problems.add("unknown values element <" + tag + "> in " + f);
      }
      if (name.isEmpty() && !tag.equals("eat-comment")) problems.add("<" + tag + "> without name in " + f);
    }
    // ids declared inline in values files (e.g. styles referencing @+id/) are rare but legal
    Matcher m = NEW_ID.matcher(read(f));
    while (m.find()) add("id", m.group(1));
  }

  String render(String pkg, boolean finalIds) {
    String mod = finalIds ? "public static final " : "public static ";
    StringBuilder b = new StringBuilder();
    b.append("// GENERATED by tools/harness/GenR.java for scripts/compile-check.sh - not the real aapt2 R.\n");
    b.append("package ").append(pkg).append(";\n\n");
    b.append("public final class R {\n");
    int typeIndex = 1;
    for (Map.Entry<String, Set<String>> t : res.entrySet()) {
      b.append("  public static final class ").append(t.getKey()).append(" {\n");
      int i = 0;
      for (String n : t.getValue()) {
        b.append("    ").append(mod).append("int ").append(n)
            .append(String.format(" = 0x7f%02x%04x;%n", typeIndex, i++));
      }
      b.append("  }\n");
      typeIndex++;
    }
    if (!styleables.isEmpty()) {
      b.append("  public static final class styleable {\n");
      for (Map.Entry<String, List<String>> s : styleables.entrySet()) {
        b.append("    ").append(mod).append("int[] ").append(s.getKey()).append(" = new int[")
            .append(s.getValue().size()).append("];\n");
        int i = 0;
        for (String a : s.getValue()) {
          b.append("    ").append(mod).append("int ").append(s.getKey()).append('_')
              .append(a.replace("android:", "android_").replace('.', '_')).append(" = ").append(i++).append(";\n");
        }
      }
      b.append("  }\n");
    }
    b.append("}\n");
    return b.toString();
  }

  static String buildConfig(String pkg, String gradle) {
    String appId = find(gradle, "applicationId\\s*=?\\s*['\"]([\\w.]+)['\"]");
    String code = find(gradle, "versionCode\\s*=?\\s*(\\d+)");
    String name = find(gradle, "versionName\\s*=?\\s*['\"]([^'\"]*)['\"]");
    return "// GENERATED by tools/harness/GenR.java for scripts/compile-check.sh.\n"
        + "package " + pkg + ";\n\npublic final class BuildConfig {\n"
        + "  public static final boolean DEBUG = Boolean.parseBoolean(\"true\");\n"
        + "  public static final String APPLICATION_ID = \"" + (appId == null ? pkg : appId) + "\";\n"
        + "  public static final String BUILD_TYPE = \"debug\";\n"
        + "  public static final int VERSION_CODE = " + (code == null ? "1" : code) + ";\n"
        + "  public static final String VERSION_NAME = \"" + (name == null ? "" : name) + "\";\n}\n";
  }
}
