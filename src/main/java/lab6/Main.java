package lab6;

import akka.NotUsed;
import akka.actor.Actor;
import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.CompletionStage;

public class Main {
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
        zoo.create("/servers/" + Integer.toString(port),
                Integer.toString(port).getBytes(),
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);
    }

    public static void main(String[] args) throws InterruptedException, IOException, KeeperException {
        Scanner in = new Scanner(System.in);
        int PORT = in.nextInt();

        createZoo(PORT);

        ActorSystem system = ActorSystem.create(ROUTES);
        http = Http.get(system);
        final ActorMaterializer materializer = ActorMaterializer.create(system);

        Main app = new Main();

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


    }
}
