import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
//import com.distsystems.assignment2.common.*;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class GETClient {
    private static Socket socket;
    private static BufferedReader in;
    private static PrintWriter out;
    private static String target_path;
    private static int lam_clock = 0;
    private static int retry_count;

    public static void main(String[] args) throws IOException, InterruptedException {
        Gson gson = new Gson();
        String[] split_addr = args[0].split(":");
        connectAggregator(split_addr[0], Integer.parseInt(split_addr[1]));
        if (args.length == 2){ // sets target path based on the presence of the optional command line argument
            target_path = "/" + args[1];
        }
        else {
            target_path = "/";
        }

        String outgoing_header = "GET %s HTTP/1.1\r\n\r\n";

        outgoing_header = String.format(outgoing_header, target_path);
        RequestBody outgoing_req_body = new RequestBody();

        while (true){ // infinite loop of GET requests. will not halt unless exception is thrown.
            outgoing_req_body.req_lamport_timestamp = iterateClock();
            String outgoing_req_json = gson.toJson(outgoing_req_body);
            String outgoing_req_http = outgoing_header + outgoing_req_json;
            out.println(outgoing_req_http);

            String res_header = null;
            try {
                res_header = in.readLine();
//                System.out.println("res header: " + res_header);
            } catch (SocketTimeoutException e) { // on timeout, waits increasing amounts of time before retry.
                retry_count += 1;
                TimeUnit.SECONDS.sleep( Math.min(retry_count * 5L, 30) );
                continue;
            }
            String input_line;
            if (Objects.equals(res_header, "HTTP/1.1 200 OK")){
                retry_count = 0; // resets retry counter on success
                while (!Objects.equals(input_line = in.readLine(), "")) { // searches for response body
//                    System.out.println(input_line);
                    continue;
                }
                input_line = in.readLine();
                try { // calls printUpdates function to print out the data
                    RequestBody response_body = gson.fromJson(input_line, RequestBody.class);
                    testClock(response_body.req_lamport_timestamp);
                    ArrayList<WeatherUpdate> updates_list = response_body.weather_updates_list;
//                System.out.println(input_line);
                    printUpdates(updates_list);
                } catch (JsonSyntaxException e) {
                    throw new JsonSyntaxException(e);
                }
            }
            else { // on unsuccessful response, waits increasing amounts of time to retry.
                String res_body_json = getRequestBody();
                RequestBody res_body_object = gson.fromJson(res_body_json, RequestBody.class);
                testClock(res_body_object.req_lamport_timestamp);
                retry_count += 1;
                TimeUnit.SECONDS.sleep( Math.min(retry_count * 5L, 30) );
                continue;
            }

            TimeUnit.SECONDS.sleep(5);
        }

    }

    public static void connectAggregator(String host, int port) throws IOException { // simple connection initializer, sets socket timeout to 20 seconds
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(),true);
        socket.setSoTimeout(20000);
    }

    private static int testClock(int incoming_clock){ // updates lamport clock based on responses from the aggregation server
        lam_clock = Math.max(incoming_clock, lam_clock) + 1;
        return lam_clock;
    }
    private static int iterateClock(){ // updates lamport clock based on internal events
        lam_clock += 1;
        return lam_clock;
    }
    private static void printUpdates(ArrayList<WeatherUpdate> updates_list){ // prints a nice formatted output for each update the client receives.
        for (WeatherUpdate update : updates_list){
            System.out.println("Station ID = " + update.id);
            System.out.println("Station Name = " + update.name);
            System.out.println("State of Origin = " + update.state);
            System.out.println("Time Zone = " + update.time_zone);
            System.out.println("Latitude = " + update.lat);
            System.out.println("Longitude = " + update.lon);
            System.out.println("Local Date/Time = " + update.local_date_time);
            System.out.println("Full Local Date/Time = " + update.local_date_time_full);
            System.out.println("Air Temperature = " + update.air_temp);
            System.out.println("Apparent Temperature = " + update.apparent_t);
            System.out.println("Clouds = " + update.cloud);
            System.out.println("Dew Point = " + update.dewpt);
            System.out.println("Pressure = " + update.press);
            System.out.println("Relative Humidity = " + update.rel_hum + "%");
            System.out.println("Wind Direction = " + update.wind_dir);
            System.out.println("Wind Speed = " + update.wind_spd_kmh + "Km/h");
            System.out.println("Wind Speed = " + update.wind_spd_kt + "knots");
            System.out.println("------");
        }
    }

    private static String getRequestBody() throws IOException { // this method skips past the HTTP headers until it gets to the body (json) of the request/reply. It uses the fact that HTTP requests have a blank line between the headers and body.
        String input_line;
        while (!Objects.equals(input_line = in.readLine(), "")) {
            continue;
        }
        return in.readLine();
    }

}
