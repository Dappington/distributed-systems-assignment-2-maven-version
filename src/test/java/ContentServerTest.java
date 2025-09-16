import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;

public class ContentServerTest { // This file has tests for the internal (non-integration) tests for this class
    @BeforeEach
    void resetStaticVariables(){ // I didn't really know how static variables worked when I wrote the main code, so this is hacky, but I'm not rewriting my code now.
        ContentServer.lam_clock = 0;
        ContentServer.retry_count = 0;
    }

    @Test
    void testFileParsing() throws IOException {
        ArrayList<WeatherUpdate> return_value = ContentServer.readFile("testweatherdata_1.txt");
        WeatherUpdate update = return_value.get(0);
        assertEquals(13.3, update.air_temp);
        assertEquals("IDS60901", update.id);
        assertEquals("Adelaide (West Terrace /  ngayirdapira)", update.name);
    }
}
