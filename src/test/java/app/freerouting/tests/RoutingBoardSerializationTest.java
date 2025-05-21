package app.freerouting.tests;

import app.freerouting.autoroute.NamedAlgorithm;
import app.freerouting.autoroute.NamedAlgorithmType;
import app.freerouting.board.*;
import app.freerouting.core.StoppableThread;
import app.freerouting.management.gson.GsonProvider;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.settings.RouterSettings;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RoutingBoardSerializationTest
{
  private RoutingBoard createBoard()
  {
    Layer[] layers = new Layer[] { new Layer("top", true), new Layer("bottom", true) };
    LayerStructure layerStructure = new LayerStructure(layers);
    ClearanceMatrix cm = ClearanceMatrix.get_default_instance(layerStructure, 0);
    BoardRules rules = new BoardRules(layerStructure, cm);
    IntBox bbox = new IntBox(0, 0, 1000, 1000);
    PolygonShape outline = new PolygonShape(new Polygon(new Point[] {
        new IntPoint(0,0), new IntPoint(1000,0), new IntPoint(1000,1000), new IntPoint(0,1000), new IntPoint(0,0)
    }));
    return new RoutingBoard(bbox, layerStructure, new PolylineShape[] { outline }, BoardRules.default_clearance_class(), rules, new Communication());
  }

  @Test
  void testRoutingBoardGsonRoundTrip() throws Exception
  {
    Gson gson = GsonProvider.GSON;
    RoutingBoard board = createBoard();

    String json = gson.toJson(board);
    RoutingBoard restored = gson.fromJson(json, RoutingBoard.class);

    assertNotNull(restored);
    assertEquals(board.get_layer_count(), restored.get_layer_count());
    assertEquals(board.bounding_box.ll.x, restored.bounding_box.ll.x);
  }

  private static class DummyThread extends StoppableThread
  {
    @Override
    protected void thread_action() {}
  }

  private static class DummyAlgorithm extends NamedAlgorithm
  {
    DummyAlgorithm(RoutingBoard board)
    {
      super(new DummyThread(), board, new RouterSettings());
    }

    @Override
    protected String getId() { return "dummy"; }

    @Override
    protected String getName() { return "dummy"; }

    @Override
    protected String getVersion() { return "1.0"; }

    @Override
    protected String getDescription() { return ""; }

    @Override
    protected NamedAlgorithmType getType() { return NamedAlgorithmType.ROUTER; }
  }

  @Test
  void testAlgorithmSerializationRoundTrip() throws Exception
  {
    Gson gson = GsonProvider.GSON;
    RoutingBoard board = createBoard();
    DummyAlgorithm algo = new DummyAlgorithm(board);

    String json = gson.toJson(algo);
    DummyAlgorithm restored = gson.fromJson(json, DummyAlgorithm.class);

    assertNotNull(restored);
    assertNotNull(restored.board);
    assertEquals(board.get_layer_count(), restored.board.get_layer_count());
  }
}
