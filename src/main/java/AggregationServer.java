import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.ArrayList;
//import com.distsystems.assignment2.common.*;
import com.google.gson.Gson;

public class AggregationServer {
    private static ServerSocket serverSocket;
    private static ArrayList<RequestBody> updates_list = new ArrayList<>();
    private static int server_lamport_clock = 0;
    private static final Object lamport_lock = new Object();
    private static final Object array_lock = new Object();

    public static void main(String[] args) throws IOException { // creates a server socket, then enters a loop of accepting connections.
        if (args.length != 0){
            serverSocket = new ServerSocket(Integer.parseInt(args[0]));
        }
        else {
            serverSocket = new ServerSocket(4567);
        }
        while (true){
            new ClientHandler(serverSocket.accept()).start(); // spawns a thread to deal with each incoming connection.
        }
    }

    public static class ClientHandler extends Thread {
        private final Socket socket;
        private PrintWriter out;
        private  BufferedReader in;

        public ClientHandler(Socket new_socket){ // initialisation
            System.out.println("thread spawned");
            this.socket = new_socket;
            System.out.println("socket assigned");
        }

        public void run() {
            try {
                System.out.println("Running");
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String input_line;
                Gson gson = new Gson();

                while (true) { // thread will loop forever accepting requests from its content server or get client. will only exit on an exception
                    System.out.println("lamport time: " + server_lamport_clock);
                    input_line = in.readLine();
                    System.out.println("input line: " + input_line);
                    if (Objects.equals(input_line, "PUT /weather.json HTTP/1.1")) { // goes to put handler if the message is a put request
                        handlePut();
                    } else if (input_line.startsWith("GET")) { // goes to get handler if the message is a get request
                        handleGet(input_line);
                    } else { // sends a 400 response to any process that sends it a request other than PUT or GET
                        RequestBody response_object = new RequestBody();
                        response_object.req_lamport_timestamp = iterateClock();
                        String response_body_json = gson.toJson(response_object);
                        out.println("HTTP/1.1 400 Bad Request\r\n\r\n" + response_body_json);
                    }
                }
            }catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void handlePut() throws IOException { // method handles put requests
            System.out.printf("handling PUT\n");
            String input_line;
            Gson gson = new Gson();
            input_line = getRequestBody();
            RequestBody update = gson.fromJson(input_line, RequestBody.class);
            checkClock(update.req_lamport_timestamp);
            if (input_line == null) { // empty put requests get a 500 response
                RequestBody response_object = new RequestBody();
                response_object.req_lamport_timestamp = iterateClock();
                String response_body_json = gson.toJson(response_object);
                out.println("HTTP/1.1 500 Internal Server Error\r\n\r\n" + response_body_json);
            }
            else { // a sensible update with content is handled by calling the pushToArray function. Response is sent.
                pushToArray(update);
                RequestBody response_object = new RequestBody();
                response_object.req_lamport_timestamp = iterateClock();
                String response_body_json = gson.toJson(response_object);
                out.println("HTTP/1.1 200 OK\r\n\r\n" + response_body_json);
            }

        }

        private void handleGet(String request_line) throws IOException { // method handles get requests
            System.out.println("handling GET\n");
            Gson gson = new Gson();
            String request_body = getRequestBody();
            RequestBody request_object = gson.fromJson(request_body, RequestBody.class);
            checkClock(request_object.req_lamport_timestamp);
            String URL = request_line.split(" ")[1]; // extracting the URL so that the code can decide whether to limit its reply to records from a particular station id
            String http_res;
            if (updates_list.isEmpty()){ // if no data is present on server, send 500
                RequestBody response_object = new RequestBody();
                response_object.req_lamport_timestamp = iterateClock();
                String response_body_json = gson.toJson(response_object);
                out.println("HTTP/1.1 500 Internal Server Error\r\n\r\n" + response_body_json);
            }
            else if (!Objects.equals(URL, "/")){ // if there is a path in the request, call the selectiveReadArray method to get a request body filtered by station id

               String target_id = URL.replace("/", "");
               RequestBody outgoing_update = selectiveReadArray(request_object.req_lamport_timestamp, target_id);
               String response_body_json = gson.toJson(outgoing_update);
                http_res = "HTTP/1.1 200 OK\r\nUser-Agent: ATOMClient/1/0\r\nContent-Type: application/json\r\nContent-Length: %d\r\n\r\n" + response_body_json;
                http_res = String.format(http_res, response_body_json.getBytes(StandardCharsets.UTF_8).length);
                out.println(http_res);
            }
            else { // if no station id specified, call readArray, reply with full array limited by the lamport timestamp
                RequestBody outgoing_update = readArray(request_object.req_lamport_timestamp);
                String response_body_json = gson.toJson(outgoing_update);
                http_res = "HTTP/1.1 200 OK\r\nUser-Agent: ATOMClient/1/0\r\nContent-Type: application/json\r\nContent-Length: %d\r\n\r\n" + response_body_json;
                http_res = String.format(http_res, response_body_json.getBytes(StandardCharsets.UTF_8).length);
                out.println(http_res);
            }
        }

        private String getRequestBody() throws IOException { // this method skips past the HTTP headers until it gets to the body (json) of the request/reply. It uses the fact that HTTP requests have a blank line between the headers and body.
            String input_line;
            while (!Objects.equals(input_line = in.readLine(), "")) {
                continue;
            }
            return in.readLine();
        }

        private int iterateClock(){ // this method iterates the clock atomically when a local event should update the clock (i.e. before sending a message) it returns the value of the clock when it's done, so that value can be used in the function without fear of a race condition (accurately timestamping the logical order of the operation depending on that lamport update without being affected by other threads' updates.)
            synchronized (lamport_lock){
                server_lamport_clock += 1;
                return server_lamport_clock;
            }
        }

        private int checkClock(int incoming_clock){ // This method will update the lamport clock in the event of the server receiving an update from a client or content server. it returns the value of the clock when it's done, so that value can be used in the function without fear of a race condition (accurately timestamping the logical order of the operation depending on that lamport update without being affected by other threads' updates.)
            synchronized (lamport_lock){
                server_lamport_clock = Math.max(incoming_clock, server_lamport_clock) + 1;
                return server_lamport_clock;
            }
        }

        private void pushToArray(RequestBody new_element){ // This method pushes new elements to the array that stores the weather data. It uses a synchronized block locked with the object array_lock, which it shares with other methods that operate on this array, to ensure race conditions are avoided.
            synchronized (array_lock){
                updates_list.add(new_element);
                iterateClock();
                if (updates_list.size() > 20){ // the approach I took was to add the new element to the array unconditionally, then check if the array is over the 20-item max and cull the oldest entry if it is.
                    int lowest_clock_score = server_lamport_clock;
                    int lowest_index = 0;
                    for (int i = 0; i < updates_list.size(); i++){
                        RequestBody update = updates_list.get(i);
                        if (update.req_lamport_timestamp < lowest_clock_score){
                            lowest_clock_score = update.req_lamport_timestamp;
                            lowest_index = i;
                        }
                    }
                    updates_list.remove(lowest_index);
                }

                System.out.println("PUSH successful");
                for (RequestBody update : updates_list){
                    System.out.println("update timestamp: " + update.req_lamport_timestamp);
                }
            }
        }

        private RequestBody readArray(int request_timestamp){ // this method is used to read the array and select elements which are older than the GET client's timestamp
            ArrayList<WeatherUpdate> sendable_updates = new ArrayList<>();
            RequestBody res_body = new RequestBody();
            synchronized (array_lock){
                for (RequestBody update : updates_list){
                    if (update.req_lamport_timestamp < request_timestamp) { // this filtering for only content that is "older" than the request's timestamp doesn't make a lot of sense, but I saw a post on piazza that said this was how it should be done.
                        sendable_updates.add(update.weatherUpdate);
                    }
                }
                res_body.weather_updates_list = sendable_updates;
                res_body.req_lamport_timestamp = iterateClock();
            }
            return res_body;
        }

        private RequestBody selectiveReadArray(int request_timestamp, String target_id){ // this method works as above, but also filters elements for a particular station id.
            ArrayList<WeatherUpdate> sendable_updates = new ArrayList<>();
            RequestBody res_body = new RequestBody();
            synchronized (array_lock){
                for (RequestBody update : updates_list){
                    if (Objects.equals(update.weatherUpdate.id, target_id)) {
                        if (update.req_lamport_timestamp < request_timestamp) { // this filtering for only content that is "older" than the request's timestamp doesn't make a lot of sense, but I saw a post on piazza that said this was how it should be done.
                            sendable_updates.add(update.weatherUpdate);
                        }
                    }
                }
                res_body.weather_updates_list = sendable_updates;
                res_body.req_lamport_timestamp = iterateClock();
            }
            return res_body;
        }

    }
}
