package lab6;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;

import javax.sound.sampled.Port;
import java.util.List;
import java.util.Random;

public class Storage extends AbstractActor {
    List<String> serverPorts;

    public Receive createReceive() {
        return ReceiveBuilder.create().match(ServerMessage.class, msg->{
            serverPorts = msg.getServerPorts();
        }).match(PortRandomizer.class, msg ->{
            Random random = new Random();
            int randomIndex = random.nextInt(serverPorts.size());
            System.out.println(randomIndex);
            while (serverPorts.get(randomIndex).equals(msg.getPort())){
                randomIndex = random.nextInt(serverPorts.size());
            }
            getSender().tell(Integer.parseInt(serverPorts.get(randomIndex)), ActorRef.noSender());
        }).build();
    }
}
