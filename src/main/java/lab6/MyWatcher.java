package lab6;

import akka.actor.ActorRef;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MyWatcher implements Watcher {
    @Override
    public void process(WatchedEvent event) {
        public void process(WatchedEvent event) {
            List<String> servers = new ArrayList<>();
            try{
                servers = zoo.getChildren("/" + SERVERS, true);
            } catch (InterruptedException | KeeperException e) {
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
}
