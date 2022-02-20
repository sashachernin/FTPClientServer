/*
 * This class is used to make the sync process between servers easier.
 * It stores the information from the VERSION string.
 * It is easier to store and compare VERSIONs with objects of this class
 */

public class SyncObj {
    private String date;
    private String digest;
    private String lockedby; //ex. 10.0.201.5:5000
    private String location; //ex. serverOne

    public SyncObj(String date, String digest, String lockedby, String location) {
        this.date = date;
        this.digest = digest;
        this.lockedby = lockedby;
        this.location = location;
    }

    public String getDigest() {
        return digest;
    }

    public String getLocation() {
        return location;
    }

    public String getLockedby() {
        return lockedby;
    }

    public String getDate() {
        return date;
    }

}
