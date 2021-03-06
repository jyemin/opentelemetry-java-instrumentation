/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.sql.Connection;
import java.util.Map;
import java.util.Properties;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class DriverInstrumentation extends Instrumenter.Default {

  public DriverInstrumentation() {
    super("jdbc");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("java.sql.Driver");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("java.sql.Driver"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
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
      packageName + ".JDBCUtils",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        nameStartsWith("connect")
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, Properties.class))
            .and(returns(named("java.sql.Connection"))),
        DriverInstrumentation.class.getName() + "$DriverAdvice");
  }

  public static class DriverAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addDBInfo(
        @Advice.Argument(0) String url,
        @Advice.Argument(1) Properties props,
        @Advice.Return Connection connection) {
      if (connection == null) {
        // Exception was probably thrown.
        return;
      }
      DBInfo dbInfo = JDBCConnectionUrlParser.parse(url, props);
      JDBCMaps.connectionInfo.put(connection, dbInfo);
    }
  }
}
