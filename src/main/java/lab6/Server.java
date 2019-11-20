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

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class Server extends AllDirectives {
    private static int PORT;
    private static ActorRef Storage;
    private static ZooKeeper zoo;
    private static final String ROUTES = "routes";
    private static final String LOCALHOST = "localhost";
    private static final String SERVER_INFO = "\"Server online at http://localhost:8080/\\nPress RETURN to stop...\"";
    private static Http http;

    private static void createZoo(int port) throws IOException, KeeperException, InterruptedException {
        zoo = new ZooKeeper(
                "127.0.0.1:2181",
                2000,
                a -> {}
        );
        zoo.create("/servers/" + port,
                Integer.toString(port).getBytes(),
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);
        zoo.getChildren("/servers", new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if (event.getType() == Event.EventType.NodeChildrenChanged)
            }
        })
    }

    public static void main(String[] args) throws InterruptedException, IOException, KeeperException {
        Scanner in = new Scanner(System.in);
        PORT = in.nextInt();

        createZoo(PORT);

        ActorSystem system = ActorSystem.create(ROUTES);
        http = Http.get(system);
        Storage = system.actorOf(Props.create(Storage.class));
        final ActorMaterializer materializer = ActorMaterializer.create(system);

        Server app = new Server();

        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = app.route().flow(system, materializer);
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(
                routeFlow,
                ConnectHttp.toHost(LOCALHOST, PORT),
                materializer
        );

        System.out.println(SERVER_INFO);
        System.in.read();

        binding
                .thenCompose(ServerBinding::unbind)
                .thenAccept(unbound -> system.terminate());

    }

    private Route route(){
        return concat(
                get(
                        () -> parameter("url", url ->
                                parameter("count", count -> {
                                    int countNumber = Integer.parseInt(count);
                                    System.out.println("Request got from " + PORT + "count = " + count);
                                    if (countNumber != 0) {
                                        try {
                                            Future<Object> randomPort = CompletableFuture.completedFuture(Patterns.ask(Storage, new PortRandomizer(Integer.toString(PORT)), 5000));
                                            return complete(requestToServer(Integer.parseInt(randomPort.get().toString()), url, countNumber).toCompletableFuture().get());
                                        } catch (InterruptedException| ExecutionException e){
                                            e.printStackTrace();
                                            return complete("Error:" + e);
                                        }
                                    }
                                    try {
                                        return complete(requestToWebSite(url).toCompletableFuture().get());
                                    } catch (InterruptedException | ExecutionException e) {
                                        e.printStackTrace();
                                        return complete("Error:" + e);
                                    }
                                }))
                )
        );
    }

    CompletionStage<HttpResponse> requestToServer(int port, String url, int count){
        try{
            return http.singleRequest(
                    HttpRequest.create("http://localhost:" + port + "/?url=" + url + "&count=" + (count - 1)));
        } catch (Exception e){
            return CompletableFuture.completedFuture(HttpResponse.create().withEntity("Error:" + e));
        }
    }

    CompletionStage<HttpResponse> requestToWebSite(String url){
        try{
            return http.singleRequest(HttpRequest.create(url));
        }catch (Exception e){
            return CompletableFuture.completedFuture(HttpResponse.create().withEntity("Error:" + e));
        }
    }
}
