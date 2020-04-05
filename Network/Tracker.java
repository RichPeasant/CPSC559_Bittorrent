package Network;

import java.util.ArrayList;

public class Tracker {

    private ArrayList<String> peerList;
    private String fileName;
    private String leader;

    public Tracker(ArrayList<String> peerList, String fileName, String leader) {
        this.peerList = peerList;
        this.fileName = fileName;
        this.leader = leader;
    }

    /**
     * A method to add a new peer to current peerList
     *
     * @param newPeer ip address
     */
    public void addPeerData(String newPeer) {
        this.peerList.add(newPeer);
    }

    /**
     * A method to delete a peer from current peerList
     *
     * @param deletePeer ip address
     */
    public void deletePeerData(String deletePeer) {
        this.peerList.remove(deletePeer);
    }

    /**
     * A method to update leader's ip address
     *
     * @param newLeader ip address
     */
    public void updateLeader(String newLeader) {
        deletePeerData(this.leader);
        this.leader = newLeader;
    }

    public ArrayList<String> getPeerList() {
        return this.peerList;
    }

    /**
     * A method to get the current leader's ip address
     *
     * @return ip address
     */
    public String getLeader() {
        return this.leader;
    }

    public String getFileName() {
        return this.fileName;
    }

}