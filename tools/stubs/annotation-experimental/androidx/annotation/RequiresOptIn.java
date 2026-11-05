// Hand-written stub of androidx.annotation:annotation-experimental 1.3.1.
package androidx.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.ANNOTATION_TYPE)
public @interface RequiresOptIn {
  Level level() default Level.ERROR;

  enum Level {
    WARNING,
    ERROR
  }
}
