package app.freerouting.management.analytics;

import app.freerouting.logger.FRLogger;
import app.freerouting.management.TextManager;
import app.freerouting.management.analytics.dto.*;
import java.util.concurrent.*;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * A client for Google BigQuery's API.
 * Please note, that identifies, tracks, users tables are NOT updated in BigQuery (unlike Segment).
 */
public class BigQueryClient implements AnalyticsClient
{
  private static final String BIGQUERY_PROJECT_ID = "freerouting-analytics";
  private static final String BIGQUERY_DATASET_ID = "freerouting_application";
  private static byte[] BIGQUERY_SERVICE_ACCOUNT_KEY;
  private static BigQuery bigQuery;
  private final String LIBRARY_NAME = "freerouting";
  private final String LIBRARY_VERSION;
  private boolean enabled = true;
  private final BlockingQueue<Payload> queue = new LinkedBlockingQueue<>();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r);
    t.setDaemon(true);
    t.setName("bigquery-flush");
    return t;
  });
  private final int batchSize;

  private final int flushInterval;

  public BigQueryClient(String libraryVersion, String serviceAccountKey)
  {
    this(libraryVersion, serviceAccountKey, 30, 20);
  }

  public BigQueryClient(String libraryVersion, String serviceAccountKey, int flushIntervalSeconds, int batchSize)
  {
    BIGQUERY_SERVICE_ACCOUNT_KEY = serviceAccountKey.getBytes();
    LIBRARY_VERSION = libraryVersion;
    this.flushInterval = flushIntervalSeconds;
    this.batchSize = batchSize;
    // Enable TLS protocols
    System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
    bigQuery = createBigQueryClient();
    scheduler.scheduleAtFixedRate(this::flushQueue, flushInterval, flushInterval, TimeUnit.SECONDS);
  }

  private BigQuery createBigQueryClient()
  {
    try
    {
      InputStream keyStream = new ByteArrayInputStream(BIGQUERY_SERVICE_ACCOUNT_KEY);
      GoogleCredentials credentials = ServiceAccountCredentials
          .fromStream(keyStream)
          .createScoped("https://www.googleapis.com/auth/bigquery");
      credentials.refreshIfExpired();
      // create the BigQuery client builder
      var bigQueryBuilder = BigQueryOptions.newBuilder();
      // set the credentials
      var bigQueryOptions = bigQueryBuilder
          .setCredentials(credentials)
          .build();
      // create the BigQuery client
      return bigQueryOptions.getService();
    } catch (IOException e)
    {
      throw new RuntimeException("Failed to create BigQuery client", e);
    }
  }

  private void enqueuePayload(Payload payload)
  {
    if (!enabled)
    {
      return;
    }

    queue.offer(payload);
    if (queue.size() >= batchSize)
    {
      scheduler.execute(this::flushQueue);
    }
  }

  private void flushQueue()
  {
    if (queue.isEmpty())
    {
      return;
    }

    var batch = new java.util.ArrayList<Payload>();
    queue.drainTo(batch, batchSize);
    Map<String, java.util.List<InsertAllRequest.RowToInsert>> tables = new HashMap<>();

    for (Payload payload : batch)
    {
      try
      {
        Map<String, String> fields = generateFieldsFromPayload(payload);
        String tableName = payload.event
            .toLowerCase()
            .replace(" ", "_")
            .replace("-", "_");

        fields.put("event_text", fields.get("event"));
        fields.remove("event");
        fields.put("event", tableName);

        tables.computeIfAbsent(tableName, k -> new java.util.ArrayList<>())
            .add(InsertAllRequest.RowToInsert.of(fields));
      } catch (Exception e)
      {
        FRLogger.error("Exception preparing analytics payload: " + e.getMessage(), e);
      }
    }

    for (var entry : tables.entrySet())
    {
      try
      {
        TableId tableId = TableId.of(BIGQUERY_PROJECT_ID, BIGQUERY_DATASET_ID, entry.getKey());
        InsertAllRequest.Builder builder = InsertAllRequest.newBuilder(tableId)
            .setRows(entry.getValue());

        InsertAllResponse response = bigQuery.insertAll(builder.build());
        if (response.hasErrors())
        {
          response
              .getInsertErrors()
              .forEach((index, errors) -> FRLogger.error(
                  "Error in BigQueryClient.flushQueue: (" + entry.getKey() + ")" + errors,
                  null));
        }
      } catch (Exception e)
      {
        FRLogger.error("Exception in BigQueryClient.flushQueue: " + e.getMessage(), e);
      }
    }
  }

  private Map<String, String> generateFieldsFromPayload(Payload payload)
  {
    // fields are the fields of the payload class, plus the traits and properties map combined formatted for BigQuery
    Map<String, String> fields = new HashMap<String, String>();

    // payload.traits needs to have a few more fields that are normally set in the SegmentClient
    fields.put("id", "frg-2o0" + TextManager.generateRandomAlphanumericString(25));
    var eventHappenedAt = Instant.now();
    fields.put("received_at", TextManager.convertInstantToString(eventHappenedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC");
    fields.put("sent_at", TextManager.convertInstantToString(eventHappenedAt, "yyyy-MM-dd HH:mm:ss") + " UTC");
    fields.put("original_timestamp", "<nil>");
    fields.put("timestamp", TextManager.convertInstantToString(eventHappenedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC");

    // add the basic fields to the map
    fields.put("user_id", payload.userId);
    fields.put("anonymous_id", payload.anonymousId);
    fields.put("event", payload.event);
    fields.put("context_library_name", payload.context.library.name);
    fields.put("context_library_version", payload.context.library.version);

    // payload.traits needs to have a few more fields that are normally set in the SegmentClient
    var payloadUploadedAt = Instant.now();
    fields.put("loaded_at", TextManager.convertInstantToString(payloadUploadedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC");
    fields.put("uuid_ts", TextManager.convertInstantToString(payloadUploadedAt, "yyyy-MM-dd HH:mm:ss.SSSSSS") + " UTC");

    if ((payload.traits != null) && (!payload.traits.isEmpty()))
    {
      fields.putAll(payload.traits);
    }

    if ((payload.properties != null) && (!payload.properties.isEmpty()))
    {
      fields.putAll(payload.properties);
    }

    return fields;
  }

  public void identify(String userId, String anonymousId, Traits traits) throws IOException
  {
    Payload payload = new Payload();
    payload.userId = userId;
    payload.anonymousId = anonymousId;
    payload.context = new Context();
    payload.context.library = new Library();
    payload.context.library.name = LIBRARY_NAME;
    payload.context.library.version = LIBRARY_VERSION;
    payload.traits = traits;

    // NOTE: we ignore the identify event in BigQuery (because we have the tracked "application started" event instead)
  }

  public void track(String userId, String anonymousId, String event, Properties properties) throws IOException
  {
    Payload payload = new Payload();
    payload.userId = userId;
    payload.anonymousId = anonymousId;
    payload.context = new Context();
    payload.context.library = new Library();
    payload.context.library.name = LIBRARY_NAME;
    payload.context.library.version = LIBRARY_VERSION;
    payload.event = event;
    payload.properties = properties;

    enqueuePayload(payload);
  }

  public void setEnabled(boolean enabled)
  {
    this.enabled = enabled;
  }
}