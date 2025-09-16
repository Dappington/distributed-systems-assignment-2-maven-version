import com.google.gson.Gson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class AggregationServerTest { // This file has tests for the internal (non-integration) tests for this class

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
    void  testLamportClockWhenIncomingTimestampIsGreaterThanLocal(){ // checks that the server will choose a higher incoming clock over a lower local clock
        AggregationServer.ClientHandler server = new AggregationServer.ClientHandler(new Socket());
        int clock_result = server.checkClock(5);
        assertEquals(6, clock_result); // clock is 0 by default, so 5 is greater. max(5, 0) + 1 = 6
    }

    @Test
    void  testLamportClockResetsBetweenTests(){ // makes sure the static clock variable is getting reset between tests
        AggregationServer.ClientHandler server = new AggregationServer.ClientHandler(new Socket());
        int clock_result = server.checkClock(5);
        assertEquals(6, clock_result); // clock is 0 by default, so 5 is greater. max(5, 0) + 1 = 6
    }

    @Test
    void testLamportClockRaceConditions() throws InterruptedException { // spawns a bunch of threads to hammer the lamport clock with every method that can affect it to test for a race condition
        ExecutorService executor = Executors.newFixedThreadPool(1000);
        Socket dummy_socket = null;
        for (int i = 0; i < 1000; i++) {
            executor.submit(() -> {
                AggregationServer.ClientHandler thread = new AggregationServer.ClientHandler(dummy_socket);
                for (int j = 0; j<100;j++){
                    thread.iterateClock();
                    thread.checkClock(0);
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        assertEquals(200000, AggregationServer.server_lamport_clock);

    }

    @Test
    void testMalformedHTTPResponse() throws InterruptedException, IOException { //tests the server's response to a bad request
        AggregationServer server = new AggregationServer();
        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{"4567"});
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        serverThread.start();
        Thread.sleep(500);
        Socket client_socket = new Socket("localhost", 4567);
        PrintWriter out = new PrintWriter(client_socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(client_socket.getInputStream()));

        out.println("GET me pictures of spider-man!");
        String res = in.readLine();
        serverThread.stop();
        assertEquals("HTTP/1.1 400 Bad Request", res);
    }

    @Test
    void testPutResponse() throws InterruptedException, IOException { // tests that the server will accept a put and respond correctly
        AggregationServer server = new AggregationServer();
        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{"4567"});
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
        serverThread.start();
        Thread.sleep(500);
        Socket client_socket = new Socket("localhost", 4567);
        PrintWriter out = new PrintWriter(client_socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(client_socket.getInputStream()));
        Gson gson = new Gson();
        RequestBody dummy_req_body = new RequestBody();
        WeatherUpdate dummy_weather_update = new WeatherUpdate();
        dummy_weather_update.id = "TEST";
        dummy_req_body.req_lamport_timestamp = 0;
        dummy_req_body.weatherUpdate = dummy_weather_update;
        String req_json = gson.toJson(dummy_req_body);
        String http_put_req = "PUT /weather.json HTTP/1.1\r\nUser-Agent: ATOMClient/1/0\r\nContent-Type: application/json\r\nContent-Length: %d\r\n\r\n" + req_json;
        http_put_req = String.format(http_put_req, req_json.getBytes(StandardCharsets.UTF_8).length);
        out.println(http_put_req);
        String res = in.readLine();
        serverThread.stop();
        assertEquals("TEST", AggregationServer.updates_list.get(0).weatherUpdate.id); // push to array successful
        assertTrue(res.startsWith("HTTP/1.1 200 OK"));  //response positive
    }

    @Test
    void testPutResponseWhenNoDataProvided() throws IOException, InterruptedException {
        AggregationServer server = new AggregationServer();
        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{"4567"});
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
        serverThread.start();
        Thread.sleep(500);
        Socket client_socket = new Socket("localhost", 4567);
        PrintWriter out = new PrintWriter(client_socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(client_socket.getInputStream()));
        Gson gson = new Gson();

        out.println("PUT /weather.json HTTP/1.1\r\nUser-Agent: ATOMClient/1/0\r\nContent-Type: application/json\r\nContent-Length: %d\r\n\r\n");
        String res = in.readLine();
        serverThread.stop();
        assertTrue(res.startsWith("HTTP/1.1 500 Internal Server Error")); // appropriate error message as defined in the task description
    }

    @Test
    void  testPutResponseWithMoreThanMaxStoredData() throws InterruptedException, IOException {
        AggregationServer server = new AggregationServer();
        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{"4567"});
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
        serverThread.start();
        Thread.sleep(500);
        Socket client_socket = new Socket("localhost", 4567);
        PrintWriter out = new PrintWriter(client_socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(client_socket.getInputStream()));
        Gson gson = new Gson();

        for (int i = 0; i<20; i++){ // pushes 20 elements to the array
            RequestBody dummy_data = new RequestBody();
            dummy_data.req_lamport_timestamp = i;
            AggregationServer.updates_list.add(dummy_data);
        }

        RequestBody dummy_req_body = new RequestBody();
        dummy_req_body.req_lamport_timestamp = 100;
        String req_json = gson.toJson(dummy_req_body);
        String http_put_req = "PUT /weather.json HTTP/1.1\r\nUser-Agent: ATOMClient/1/0\r\nContent-Type: application/json\r\nContent-Length: %d\r\n\r\n" + req_json;
        http_put_req = String.format(http_put_req, req_json.getBytes(StandardCharsets.UTF_8).length);
        out.println(http_put_req);
        String res = in.readLine();
        serverThread.stop();

        assertTrue(res.startsWith("HTTP/1.1 200 OK"));  //response positive
        assertEquals(20, AggregationServer.updates_list.size()); // ensures the array is still only 20 items

    }

    @Test
    void testGetResponse() throws IOException, InterruptedException {
        AggregationServer server = new AggregationServer();
        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{"4567"});
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
        serverThread.start();
        Thread.sleep(500);
        Socket client_socket = new Socket("localhost", 4567);
        PrintWriter out = new PrintWriter(client_socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(client_socket.getInputStream()));
        Gson gson = new Gson();

        RequestBody dummy_data = new RequestBody(); // add dummy data
        dummy_data.req_lamport_timestamp = 0;
        dummy_data.weatherUpdate = new WeatherUpdate();
        dummy_data.weatherUpdate.id = "TEST";
        AggregationServer.updates_list.add(dummy_data);

        RequestBody request_body_obj = new RequestBody();
        request_body_obj.req_lamport_timestamp = 100; //making sure there will be a response
        String outgoing_req_body = gson.toJson(request_body_obj);
        String outgoing_header = "GET / HTTP/1.1\r\n\r\n";
        String outgoing_req_http = outgoing_header + outgoing_req_body;
        out.println(outgoing_req_http);

        String res_header = in.readLine();
        String input_line;
        while (!Objects.equals(input_line = in.readLine(), "")) {
            continue;
        }
        String res_body = in.readLine();
        serverThread.stop();
        RequestBody res_body_obj = gson.fromJson(res_body, RequestBody.class);
        WeatherUpdate update = res_body_obj.weather_updates_list.get(0);

        assertTrue(res_header.startsWith("HTTP/1.1 200 OK"));  //response positive
        assertTrue("TEST".equals(update.id)); //response contains correct data
    }

    @Test
    void testGetResponseWhenNoDataPresent() throws InterruptedException, IOException {
        AggregationServer server = new AggregationServer();
        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{"4567"});
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
        serverThread.start();
        Thread.sleep(500);
        Socket client_socket = new Socket("localhost", 4567);
        PrintWriter out = new PrintWriter(client_socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(client_socket.getInputStream()));
        Gson gson = new Gson();

        RequestBody request_body_obj = new RequestBody();
        request_body_obj.req_lamport_timestamp = 100;
        String outgoing_req_body = gson.toJson(request_body_obj);
        String outgoing_header = "GET / HTTP/1.1\r\n\r\n";
        String outgoing_req_http = outgoing_header + outgoing_req_body;
        out.println(outgoing_req_http);

        String res_header = in.readLine();
        serverThread.stop();
        assertTrue(res_header.startsWith("HTTP/1.1 500 Internal Server Error"));
    }

    @Test
    void testGetResponseToSelectiveRequest() throws IOException, InterruptedException {
        AggregationServer server = new AggregationServer();
        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{"4567"});
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
        serverThread.start();
        Thread.sleep(500);
        Socket client_socket = new Socket("localhost", 4567);
        PrintWriter out = new PrintWriter(client_socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(client_socket.getInputStream()));
        Gson gson = new Gson();

        RequestBody dummy_data1 = new RequestBody(); // add dummy data
        dummy_data1.req_lamport_timestamp = 0;
        dummy_data1.weatherUpdate = new WeatherUpdate();
        dummy_data1.weatherUpdate.id = "ABC";
        AggregationServer.updates_list.add(dummy_data1);

        RequestBody dummy_data2 = new RequestBody(); // add dummy data
        dummy_data2.req_lamport_timestamp = 0;
        dummy_data2.weatherUpdate = new WeatherUpdate();
        dummy_data2.weatherUpdate.id = "DEF";
        AggregationServer.updates_list.add(dummy_data2);

        RequestBody request_body_obj = new RequestBody();
        request_body_obj.req_lamport_timestamp = 100; //making sure there will be a response
        String outgoing_req_body = gson.toJson(request_body_obj);
        String outgoing_header = "GET /ABC HTTP/1.1\r\n\r\n";
        String outgoing_req_http = outgoing_header + outgoing_req_body;
        out.println(outgoing_req_http);

        String res_header = in.readLine();
        String input_line;
        while (!Objects.equals(input_line = in.readLine(), "")) {
            continue;
        }
        String res_body = in.readLine();
        serverThread.stop();
        RequestBody res_body_obj = gson.fromJson(res_body, RequestBody.class);
        WeatherUpdate update = res_body_obj.weather_updates_list.get(0);

        assertTrue(res_header.startsWith("HTTP/1.1 200 OK"));  //response positive
        assertTrue("ABC".equals(update.id)); //response contains correct data
        assertEquals(1, res_body_obj.weather_updates_list.size()); // response does not contain the second piece of dummy data with the wrong id
    }

    @Test
    void testGetResponseToLowLamportTimeRequest() throws IOException, InterruptedException {
        AggregationServer server = new AggregationServer();
        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{"4567"});
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
        serverThread.start();
        Thread.sleep(500);
        Socket client_socket = new Socket("localhost", 4567);
        PrintWriter out = new PrintWriter(client_socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(client_socket.getInputStream()));
        Gson gson = new Gson();

        RequestBody dummy_data = new RequestBody(); // add dummy data
        dummy_data.req_lamport_timestamp = 100; //make dummy data new
        dummy_data.weatherUpdate = new WeatherUpdate();
        dummy_data.weatherUpdate.id = "TEST";
        AggregationServer.updates_list.add(dummy_data);

        RequestBody request_body_obj = new RequestBody();
        request_body_obj.req_lamport_timestamp = 0; //make request timestamp old
        String outgoing_req_body = gson.toJson(request_body_obj);
        String outgoing_header = "GET / HTTP/1.1\r\n\r\n";
        String outgoing_req_http = outgoing_header + outgoing_req_body;
        out.println(outgoing_req_http);

        String res_header = in.readLine();
        String input_line;
        while (!Objects.equals(input_line = in.readLine(), "")) {
            continue;
        }
        String res_body = in.readLine();
        serverThread.stop();
        RequestBody res_body_obj = gson.fromJson(res_body, RequestBody.class);

        assertEquals(0, res_body_obj.weather_updates_list.size()); //confirm

    }
}
