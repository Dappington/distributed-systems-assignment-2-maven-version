import jdk.jshell.spi.ExecutionControl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;

public class EndToEndIntegrationTest {

    @BeforeEach
    void resetStaticVariables() { // I didn't really know how static variables worked when I wrote the main code, so this is hacky, but I'm not rewriting my code now.
        AggregationServer.server_lamport_clock = 0;
        AggregationServer.updates_list = new ArrayList<>();
    }

    @AfterEach
    void closeServerSocket() throws IOException {
        AggregationServer.serverSocket.close();
    }

    @Test
    void testSystemSendsDataFromContentServerToClient() throws InterruptedException {
        AggregationServer aggregation_server = new AggregationServer();
        Thread aggregationServerThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{"4567"});
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        aggregationServerThread.start();
        Thread.sleep(500);

        ContentServer content_server = new ContentServer();
        Thread contentServerThread = new Thread(() -> {
            try {
                ContentServer.main(new String[]{"localhost:4567", "testweatherdata_1.txt"});
            } catch (IOException | ExecutionControl.NotImplementedException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        contentServerThread.start();
        Thread.sleep(500);

        GETClient get_client = new GETClient();
        Thread getClientThread = new Thread(() -> {
            try {
                GETClient.main(new String[]{"localhost:4567"});
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        getClientThread.start();
        Thread.sleep(20000);
        assertTrue(GETClient.lam_clock!=0); //it's hard to directly observe this in a junit test but the lam clock being anything other than zero implies data was received. If you want confirmation that the data is received and displayed I recommend checking the console for the sout prints, perhaps running the files manually.
        getClientThread.stop();
        contentServerThread.stop();
        aggregationServerThread.stop();
    }
}
