import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
//import com.distsystems.assignment2.common.*;
import com.google.gson.Gson;
import jdk.jshell.spi.ExecutionControl;

public class ContentServer {
    private static Socket socket;
    private static BufferedReader in;
    private static PrintWriter out;
    protected static  int lam_clock = 0;
    protected static int retry_count = 0;

    public static void main(String[] args) throws IOException, ExecutionControl.NotImplementedException, InterruptedException {
        // assuming for now that the input will take the form ip:port and that ip will be localhost
        String[] split_addr = args[0].split(":");
        connectAggregator(split_addr[0], Integer.parseInt(split_addr[1]));

        Gson gson = new Gson();
        while (true){ // main loop. Will send updates to the Aggregation Server forever until an exception occurs.
            ArrayList<WeatherUpdate> updates_list = readFile(args[1]); // grabs the updates from the file provided in command-line args

            for (WeatherUpdate update : updates_list) { // marshals updates and sends them. the file may have multiple updates, but each update gets its own http message.
                RequestBody outgoing_body = new RequestBody();
                outgoing_body.weatherUpdate = update;
                iterateClock();
                outgoing_body.req_lamport_timestamp = lam_clock;

                String update_string = gson.toJson(outgoing_body);

                String http_put_request = "PUT /weather.json HTTP/1.1\r\nUser-Agent: ATOMClient/1/0\r\nContent-Type: application/json\r\nContent-Length: %d\r\n\r\n" + update_string; // http requests/responses are constructed and parsed manually
                http_put_request = String.format(http_put_request, update_string.getBytes(StandardCharsets.UTF_8).length);
                out.println(http_put_request);
                String res = null;
                try {
                    res = in.readLine(); // will try to read the Aggregation server's response. Will assume the put went wrong if no response arrives within 5 seconds
                } catch (SocketTimeoutException e) {
                    retry_count += 1;
                    TimeUnit.SECONDS.sleep( Math.min(retry_count * 5L, 30) ); // retry timer gets longer the more times the retry fails
                    continue;
                }
                System.out.println(res);
                if (res.equals("HTTP/1.1 200 OK")){
                    retry_count = 0; // retry counter reset on 200 response.
                    String response_json = getRequestBody();
                    RequestBody requestBody = gson.fromJson(response_json, RequestBody.class);
                    testClock(requestBody.req_lamport_timestamp);
                    TimeUnit.SECONDS.sleep(5);
                }
                else { // bad responses (any other than 200; 201 and 204 were not implemented) are treated like failures and will cause the content server to wait, however it will not resend the previous message in this case, as that message could be malformed and the source of the bad response
                    String res_body_json = getRequestBody();
                    RequestBody res_body_object = gson.fromJson(res_body_json, RequestBody.class);
                    testClock(res_body_object.req_lamport_timestamp);
                    retry_count += 1;
                    TimeUnit.SECONDS.sleep( Math.min(retry_count * 5L, 30) );
                }
            }
        }
    }

    public static void connectAggregator(String host, int port) throws IOException { // simple method, could be pulled into main function. Just connects the sockets, creates the in and out streams and sets the socket timeout.
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(),true);
        socket.setSoTimeout(500);
    }

    public static ArrayList<WeatherUpdate> readFile(String file_path) throws IOException { // this function reads updates from the file and puts them into an arraylist.
//        System.out.println(java.lang.ClassLoader.getSystemResourceAsStream("weatherdata.txt"));
        BufferedReader file = new BufferedReader(new InputStreamReader(Objects.requireNonNull(ContentServer.class.getClassLoader().getResourceAsStream(file_path))));
        String line;
        ArrayList<WeatherUpdate> updates = new ArrayList<WeatherUpdate>();

        // this monstrosity should parse text files into com.distsystems.assignment2.common.WeatherUpdate objects so long as the source file follows the format exactly.
        while ((line = file.readLine()) != null) {
            WeatherUpdate update = new WeatherUpdate();
            String[] split_line = line.split(":");
            update.id = split_line[1];
            line = file.readLine();
            split_line = line.split(":");
            update.name = split_line[1];
            line = file.readLine();
            split_line = line.split(":");
            update.state = split_line[1];
            line = file.readLine();
            split_line = line.split(":");
            update.time_zone = split_line[1];
            line = file.readLine();
            split_line = line.split(":");
            update.lat = Double.parseDouble(split_line[1]);
            line = file.readLine();
            split_line = line.split(":");
            update.lon = Double.parseDouble(split_line[1]);
            line = file.readLine();
            split_line = line.split(":");
            update.local_date_time = split_line[1];
            line = file.readLine();
            split_line = line.split(":");
            update.local_date_time_full = split_line[1];
            line = file.readLine();
            split_line = line.split(":");
            update.air_temp = Double.parseDouble(split_line[1]);
            line = file.readLine();
            split_line = line.split(":");
            update.apparent_t = Double.parseDouble(split_line[1]);
            line = file.readLine();
            split_line = line.split(":");
            update.cloud = split_line[1];
            line = file.readLine();
            split_line = line.split(":");
            update.dewpt = Double.parseDouble(split_line[1]);
            line = file.readLine();
            split_line = line.split(":");
            update.press = Double.parseDouble(split_line[1]);
            line = file.readLine();
            split_line = line.split(":");
            update.rel_hum = Double.parseDouble(split_line[1]);
            line = file.readLine();
            split_line = line.split(":");
            update.wind_dir = split_line[1];
            line = file.readLine();
            split_line = line.split(":");
            update.wind_spd_kmh = Double.parseDouble(split_line[1]);
            line = file.readLine();
            split_line = line.split(":");
            update.wind_spd_kt = Double.parseDouble(split_line[1]);
//            System.out.println(update);
            updates.add(update);
        }
        return updates;
    }

    private static void testClock(int incoming_clock){ // This function updates the lamport clock when the server receives a message.
        lam_clock = Math.max(incoming_clock, lam_clock) + 1;
    }
    private static void iterateClock(){ // This function iterates the lamport clock whenever an internal server function such as sending a message occurs.
        lam_clock += 1;
    }
    private static String getRequestBody() throws IOException { // this method skips past the HTTP headers until it gets to the body (json) of the request/reply. It uses the fact that HTTP requests have a blank line between the headers and body.
        String input_line;
        while (!Objects.equals(input_line = in.readLine(), "")) {
            continue;
        }
        return in.readLine();
    }
}