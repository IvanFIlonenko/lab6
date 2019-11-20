package lab6;

import akka.actor.AbstractActor;
import akka.japi.pf.ReceiveBuilder;

import java.util.List;

public class Storage extends AbstractActor {
    List<String> serverPorts;

    public Receive createReceive() {
        return ReceiveBuilder.create().match()
    }
}
