package app.freerouting.management.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;

import app.freerouting.board.RoutingBoard;
import app.freerouting.management.gson.RoutingBoardTypeAdapter;

import java.nio.file.Path;
import java.time.Instant;

public class GsonProvider
{
  public static final Gson GSON = new GsonBuilder()
      .setPrettyPrinting()
      .disableHtmlEscaping()
      .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
      .registerTypeAdapter(byte[].class, new ByteArrayToBase64TypeAdapter())
      .registerTypeAdapter(Path.class, new PathTypeAdapter())
      .registerTypeAdapter(app.freerouting.board.RoutingBoard.class, new RoutingBoardTypeAdapter())
      .serializeNulls()
      .setStrictness(Strictness.LENIENT)
      .create();
}