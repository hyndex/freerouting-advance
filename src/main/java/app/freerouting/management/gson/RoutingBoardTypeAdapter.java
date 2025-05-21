package app.freerouting.management.gson;

import app.freerouting.board.RoutingBoard;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.*;
import java.util.Base64;

/**
 * Gson type adapter that serializes {@link RoutingBoard} using Java object serialization
 * and encodes the binary data as base64. This allows RoutingBoard instances to be
 * safely written to JSON and restored later.
 */
public class RoutingBoardTypeAdapter extends TypeAdapter<RoutingBoard>
{
  @Override
  public void write(JsonWriter out, RoutingBoard value) throws IOException
  {
    if (value == null)
    {
      out.nullValue();
      return;
    }
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(baos))
    {
      oos.writeObject(value);
    }
    out.value(Base64.getEncoder().encodeToString(baos.toByteArray()));
  }

  @Override
  public RoutingBoard read(JsonReader in) throws IOException
  {
    if (in.peek() == JsonToken.NULL)
    {
      in.nextNull();
      return null;
    }
    byte[] data = Base64.getDecoder().decode(in.nextString());
    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data)))
    {
      return (RoutingBoard) ois.readObject();
    } catch (ClassNotFoundException e)
    {
      throw new IOException(e);
    }
  }
}
