package app.freerouting.tests;

import app.freerouting.core.BoardFileDetails;
import app.freerouting.gui.FileFormat;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class FrbSaveTest {
  @Test
  void testRepeatedFrbSaveIsIdentical() throws Exception {
    BoardFileDetails details = new BoardFileDetails();
    details.format = FileFormat.FRB;
    details.setFilename("test-output.frb");
    byte[] data = new byte[] {1,2,3,4,5,6,7,8,9,0};
    details.setData(data);

    File temp = File.createTempFile("frbtest", ".frb");
    temp.deleteOnExit();

    details.writeDataToFile(temp);
    byte[] first = Files.readAllBytes(temp.toPath());

    // second save
    details.writeDataToFile(temp);
    byte[] second = Files.readAllBytes(temp.toPath());

    assertArrayEquals(first, second, "FRB save should be stable");
  }
}
