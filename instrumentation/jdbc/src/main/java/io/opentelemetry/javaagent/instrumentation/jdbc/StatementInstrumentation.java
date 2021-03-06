/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.javaagent.instrumentation.jdbc.JdbcTracer.tracer;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap.Depth;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.sql.Statement;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class StatementInstrumentation extends Instrumenter.Default {

  public StatementInstrumentation() {
    super("jdbc");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("java.sql.Statement");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("java.sql.Statement"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".DataSourceTracer",
      packageName + ".DBInfo",
      packageName + ".DBInfo$Builder",
      packageName + ".JDBCConnectionUrlParser",
      packageName + ".JDBCConnectionUrlParser$1",
      packageName + ".JDBCConnectionUrlParser$2",
      packageName + ".JDBCConnectionUrlParser$3",
      packageName + ".JDBCConnectionUrlParser$4",
      packageName + ".JDBCConnectionUrlParser$5",
      packageName + ".JDBCConnectionUrlParser$6",
      packageName + ".JDBCConnectionUrlParser$7",
      packageName + ".JDBCConnectionUrlParser$8",
      packageName + ".JDBCConnectionUrlParser$9",
      packageName + ".JDBCConnectionUrlParser$10",
      packageName + ".JDBCConnectionUrlParser$11",
      packageName + ".JDBCConnectionUrlParser$12",
      packageName + ".JDBCConnectionUrlParser$13",
      packageName + ".JDBCConnectionUrlParser$14",
      packageName + ".JDBCConnectionUrlParser$15",
      packageName + ".JDBCConnectionUrlParser$16",
      packageName + ".JDBCConnectionUrlParser$17",
      packageName + ".JDBCMaps",
      packageName + ".JdbcTracer",
      packageName + ".JDBCUtils",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        nameStartsWith("execute").and(takesArgument(0, String.class)).and(isPublic()),
        StatementInstrumentation.class.getName() + "$StatementAdvice");
  }

  public static class StatementAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) String sql,
        @Advice.This Statement statement,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelCallDepth") Depth callDepth) {

      callDepth = tracer().getCallDepth();
      if (callDepth.getAndIncrement() == 0) {
        span = tracer().startSpan(statement, sql);
        if (span != null) {
          scope = tracer().startScope(span);
        }
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelCallDepth") Depth callDepth) {
      if (callDepth.decrementAndGet() == 0 && scope != null) {
        scope.close();
        if (throwable == null) {
          tracer().end(span);
        } else {
          tracer().endExceptionally(span, throwable);
        }
      }
    }
  }
}
