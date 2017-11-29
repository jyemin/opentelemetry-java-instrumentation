package dd.inst.mongo;

import static org.assertj.core.api.Java6Assertions.assertThat;

import com.datadoghq.agent.integration.DDTracingCommandListener;
import com.datadoghq.trace.DDSpan;
import com.datadoghq.trace.DDTracer;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandStartedEvent;
import io.opentracing.tag.Tags;
import java.util.Arrays;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.Test;

public class MongoClientInstrumentationTest {

  private static ConnectionDescription makeConnection() {
    return new ConnectionDescription(new ServerId(new ClusterId(), new ServerAddress()));
  }

  @Test
  public void mongoSpan() {
    final CommandStartedEvent cmd =
        new CommandStartedEvent(1, makeConnection(), "databasename", "query", new BsonDocument());

    final DDSpan span = new DDTracer().buildSpan("foo").startManual();
    DDTracingCommandListener.decorate(span, cmd);

    assertThat(span.context().getSpanType()).isEqualTo("mongodb");
    assertThat(span.context().getResourceName())
        .isEqualTo(span.context().getTags().get("db.statement"));
  }

  @Test
  public void queryScrubbing() {
    // all "secret" strings should be scrubbed out of these queries
    BsonDocument query1 = new BsonDocument("find", new BsonString("show"));
    query1.put("stuff", new BsonString("secret"));
    BsonDocument query2 = new BsonDocument("insert", new BsonString("table"));
    BsonDocument query2_1 = new BsonDocument("count", new BsonString("show"));
    query2_1.put("id", new BsonString("secret"));
    query2.put("docs", new BsonArray(Arrays.asList(new BsonString("secret"), query2_1)));
    List<BsonDocument> queries = Arrays.asList(query1, query2);
    for (BsonDocument query : queries) {
      final CommandStartedEvent cmd =
          new CommandStartedEvent(1, makeConnection(), "databasename", "query", query);

      final DDSpan span = new DDTracer().buildSpan("foo").startManual();
      DDTracingCommandListener.decorate(span, cmd);

      assertThat(span.getTags().get(Tags.DB_STATEMENT.getKey()))
          .isEqualTo(query.toString().replaceAll("secret", "?"));
    }
  }
}