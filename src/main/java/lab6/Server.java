package lab6;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import org.apache.zookeeper.*;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;

public class Server extends AllDirectives {
    private static final String SERVERS = "servers";
    private static final String ERROR = "Error: ";
    private static final String URL = "url";
    private static final String COUNT = "count";
    private static int PORT;
    private static ActorRef Storage;
    private static ZooKeeper zoo;
    private static final String ROUTES = "routes";
    private static final String LOCALHOST = "localhost";
    private static final String SERVER_INFO = "\"Server online at http://localhost:";
    private static final String PRESS_RETURN = "/ \nPress RETURN to stop...\"";
    private static Http http;

    private static void createZoo(int port) throws IOException, KeeperException, InterruptedException {
        zoo = new ZooKeeper(
                "127.0.0.1:2181",
                2000,
                new Watcher() {
                    @Override
                    public void process(WatchedEvent event) {
                        List<String> servers = new ArrayList<>();
                        try{
                            servers = zoo.getChildren("/" + SERVERS, true);
                        } catch (InterruptedException | KeeperException  e) {
                            e.printStackTrace();
                        }
                        List<String> serverPorts = new ArrayList<>();
                        for (String s: servers) {
                            byte[] port = new byte[0];
                            try{
                                port = zoo.getData("/" + SERVERS + "/" + s, false, null);
                            } catch (InterruptedException | KeeperException e) {
                                e.printStackTrace();
                            }
                            serverPorts.add(new String(port));
                        }
                        Storage.tell(new ServerMessage(serverPorts), ActorRef.noSender());
                        try {
                            TimeUnit.SECONDS.sleep(3);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        process(event);
                    }
                }
        );
//        zoo.create("/servers","parent".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        zoo.create("/" + SERVERS + "/" + port,
                Integer.toString(port).getBytes(),
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);
        zoo.getChildren("/" + SERVERS, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                    List<String> servers = new ArrayList<>();
                    try{
                        servers = zoo.getChildren("/" + SERVERS, true);
                    } catch (InterruptedException | KeeperException  e) {
                        e.printStackTrace();
                    }
                    List<String> serverPorts = new ArrayList<>();
                    for (String s: servers) {
                        byte[] port = new byte[0];
                        try{
                            port = zoo.getData("/" + SERVERS + "/" + s, false, null);
                        } catch (InterruptedException | KeeperException e) {
                            e.printStackTrace();
                        }
                        serverPorts.add(new String(port));
                    }
                    Storage.tell(new ServerMessage(serverPorts), ActorRef.noSender());
                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                process(event);
            }
        });
    }

    

    public static void main(String[] args) throws InterruptedException, IOException, KeeperException {
        Scanner in = new Scanner(System.in);
        PORT = in.nextInt();
        ActorSystem system = ActorSystem.create(ROUTES);
        Storage = system.actorOf(Props.create(Storage.class));

        createZoo(PORT);

        http = Http.get(system);
        final ActorMaterializer materializer = ActorMaterializer.create(system);

        Server app = new Server();

        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = app.route().flow(system, materializer);
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(
                routeFlow,
                ConnectHttp.toHost(LOCALHOST, PORT),
                materializer
        );

        System.out.println(SERVER_INFO + PORT + PRESS_RETURN);
        System.in.read();

        binding
                .thenCompose(ServerBinding::unbind)
                .thenAccept(unbound -> system.terminate());

    }

    private Route route(){
        return concat(
                get(
                        () -> parameter(URL, url ->
                                parameter(COUNT, count -> {
                                    int countNumber = Integer.parseInt(count);
                                    System.out.println("Request got from " + PORT + "| Count = " + count);
                                    if (countNumber != 0) {
                                        try {
                                            Future<Object> randomPort = Patterns.ask(Storage, new PortRandomizer(Integer.toString(PORT)), 5000);
                                            int reply = (int)Await.result(randomPort, Duration.create(5, TimeUnit.SECONDS));
                                            return complete(requestToServer(reply, url, countNumber).toCompletableFuture().get());
                                        } catch (Exception e){
                                            e.printStackTrace();
                                            return complete(ERROR + e);
                                        }
                                    }
                                    else {
                                        try {
                                            return complete(requestToWebSite(url).toCompletableFuture().get());
                                        } catch (InterruptedException | ExecutionException e) {
                                            e.printStackTrace();
                                            return complete(ERROR + e);
                                        }
                                    }
                                }))
                )
        );
    }

    CompletionStage<HttpResponse> requestToServer(int port, String url, int count){
        try{
            return http.singleRequest(
                    HttpRequest.create("http://localhost:" + port + "/?" + URL + "=" + url + "&" + COUNT + "=" + (count - 1)));
        } catch (Exception e){
            return CompletableFuture.completedFuture(HttpResponse.create().withEntity(ERROR + e));
        }
    }

    CompletionStage<HttpResponse> requestToWebSite(String url){
        try{
            return http.singleRequest(HttpRequest.create(url));
        }catch (Exception e){
            return CompletableFuture.completedFuture(HttpResponse.create().withEntity(ERROR + e));
        }
    }
}
