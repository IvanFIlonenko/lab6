package lab6;

import java.util.List;

public class ServerMessage {
    private List<String> serverPorts;

    public ServerMessage(List<String> serverPorts){
        this.serverPorts = serverPorts;
    }

    public List<String> getServerPorts(){
        return this.serverPorts;
    }
}
