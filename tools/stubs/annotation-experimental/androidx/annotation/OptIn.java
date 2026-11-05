// Hand-written stub of androidx.annotation:annotation-experimental 1.3.1 (an `api` dependency of
// media3-common, so it IS on the app's compile classpath).
package androidx.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,
    ElementType.CONSTRUCTOR, ElementType.LOCAL_VARIABLE})
public @interface OptIn {
  Class<? extends Annotation>[] markerClass();
}
